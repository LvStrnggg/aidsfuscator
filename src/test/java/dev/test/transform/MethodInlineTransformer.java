package dev.test.transform;

import dev.lvstrng.aidsfuscator.analysis.ref.nodes.MethodReference;
import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.log.Logger;
import dev.lvstrng.aidsfuscator.transform.Setting;
import dev.lvstrng.aidsfuscator.transform.Transformer;
import dev.lvstrng.aidsfuscator.tree.impl.JMethod;
import dev.lvstrng.aidsfuscator.utils.ASMUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class MethodInlineTransformer extends Transformer {
    private final Setting<Boolean> keepMethod = setting("keepMethod", false);

    public MethodInlineTransformer() {
        super("Method Inlining", "methodInline");
        setExperimental();
    }

    @Override
    public void transform(Context context) {
        context.referenceGraph().build();

        for(var clazz : context.classes()) {
            var toRemove = new HashSet<JMethod>();

            for(var method : clazz.methods()) {
                if(!canInline(context, method))
                    continue;

                for(var ref : context.referenceGraph().refs(method)) {
                    var caller = ref.caller();
                    var copy = copyBody(context, caller, method);

                    caller.insns().insertBefore(ref.insn(), copy);
                    caller.insns().remove(ref.insn());
                    markChange();
                }

                if(!keepMethod.value()) {
                    toRemove.add(method);
                    continue;
                }

                context.referenceGraph().build();
            }

            toRemove.forEach(e -> e.owner().remove(e));
        }
    }

    private InsnList copyBody(Context context, JMethod caller, JMethod method) {
        var labels = new HashMap<LabelNode, LabelNode>();
        for(var insn : method.insns()) {
            if(!(insn instanceof LabelNode lbl))
                continue;

            labels.put(lbl, new LabelNode());
        }

        var exitLabel = new LabelNode();
        var startIndex = caller.maxLocals();
        var args = method.args();
        var currIdx = startIndex + Arrays.stream(args).mapToInt(Type::getSize).sum() + (method.isVirtual() ? 1 : 0);

        // args
        var body = new InsnList();
        for(int i = args.length - 1; i >= 0; i--) {
            var arg = args[i];
            body.add(new VarInsnNode(arg.getOpcode(ISTORE), currIdx -= arg.getSize()));
        }

        if(method.isVirtual())
            body.add(new VarInsnNode(ASTORE, --currIdx));

        // real body
        for(var insn : method.insns()) {
            if(ASMUtils.isReturn(insn)) {
                body.add(new JumpInsnNode(GOTO, exitLabel));
                continue;
            }

            var cloned = insn.clone(labels);
            if(cloned instanceof VarInsnNode v) {
                v.var += startIndex;
            } else if(cloned instanceof IincInsnNode iinc) {
                iinc.var += startIndex;
            }

            body.add(cloned);
        }

        // traps
        for(var trap : method.traps()) {
            var tcb = new TryCatchBlockNode(
                    labels.get(trap.start),
                    labels.get(trap.end),
                    labels.get(trap.handler),
                    trap.type
            );

            caller.traps().add(tcb);
        }

        body.add(exitLabel);
        caller.setMaxLocals(caller.maxLocals() + method.maxLocals());

        // if not in same class
        if(!method.owner().equals(caller.owner())) {
            for(var field : method.owner().fields()) {
                field.removeAccessFlags(ACC_PRIVATE);
                field.addAccessFlags(ACC_PUBLIC);
            }

            for(var m : method.owner().methods()) {
                m.removeAccessFlags(ACC_PRIVATE);
                m.addAccessFlags(ACC_PUBLIC);
            }
        }

        return body;
    }

    private boolean canInline(Context context, JMethod method) {
        /*if(!method.isAnnotatedBy("aidsfuscator/api/InlineMethod")) // comment out for testing purposes
            return false;*/

        if(!method.isNonHierarchical()) // let's not mess with methods with a hierarchy
            return false;

        var inRefs = context.referenceGraph().refs(method);
        if(inRefs.stream().anyMatch(MethodReference::cantEdit)) // just can't edit
            return false;

        if(inRefs.stream().anyMatch(e -> e.caller().equals(method))) // recursion check
            return false;

        if(cantEditMethod(method.owner(), method))
            return false;

        if(!method.properties().properties().isEmpty())
            return false;

        var outRefs = context.referenceGraph().methodRefsIn(method);
        if(outRefs.stream().anyMatch(e -> e.method().owner().name().contains("/reflect/"))) {
            Logger.warn("Method %s will not be inlined: calling reflection methods",  method);
            return false;
        }

        return true;
    }
}
