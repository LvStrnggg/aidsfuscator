package dev.lvstrng.aidsfuscator.transform.impl.shuffle;

import dev.lvstrng.aidsfuscator.analysis.ref.ReferenceGraph;
import dev.lvstrng.aidsfuscator.analysis.ref.nodes.MethodReference;
import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.exclude.impl.Exclusions;
import dev.lvstrng.aidsfuscator.transform.Transformer;
import dev.lvstrng.aidsfuscator.tree.impl.JClass;
import dev.lvstrng.aidsfuscator.tree.impl.JMethod;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

public class MethodParameterShuffleTransformer extends Transformer {

    public MethodParameterShuffleTransformer() {
        super("Method Parameter Shuffling", "parameterShuffle");
    }

    @Override
    public void transform(Context context) {
        var graph = context.referenceGraph().build();
        var shuffleMap = new HashMap<JMethod, int[]>();

        for (var clazz : context.classes()) {
            for (var method : clazz.methods()) {
                if (shouldSkip(context, graph, clazz, method))
                    continue;

                var args = Type.getArgumentTypes(method.desc());
                if (args.length < 2)
                    continue;

                var order = shuffleOrder(args.length);
                if (isIdentity(order))
                    continue;

                shuffleMap.put(method, order);
            }
        }

        for (var entry : shuffleMap.entrySet()) {
            var method = entry.getKey();
            var order  = entry.getValue();

            rewriteMethod(method, order);

            var refs = graph.refs(method);
            for (var ref : refs) {
                if (ref.cantEdit())
                    continue;
                rewriteCallsite(ref, order);
            }

            markChange();
        }
    }

    private void rewriteMethod(JMethod method, int[] order) {
        var args      = Type.getArgumentTypes(method.desc());
        var isStatic  = method.isStatic();
        var thisOffset = isStatic ? 0 : 1;

        var oldSlots = new int[args.length];
        int slot = thisOffset;
        for (int i = 0; i < args.length; i++) {
            oldSlots[i] = slot;
            slot += args[i].getSize();
        }

        var newArgs = new Type[args.length];
        for (int i = 0; i < order.length; i++) {
            newArgs[i] = args[order[i]];
        }

        var newSlots = new int[args.length];
        slot = thisOffset;
        for (int i = 0; i < newArgs.length; i++) {
            newSlots[i] = slot;
            slot += newArgs[i].getSize();
        }

        var oldToNew = new int[method.maxLocals()];
        Arrays.fill(oldToNew, -1);

        if (!isStatic)
            oldToNew[0] = 0;

        for (int i = 0; i < args.length; i++) {
            oldToNew[oldSlots[i]] = newSlots[order[i]];
            if (args[i].getSize() == 2 && oldSlots[i] + 1 < oldToNew.length)
                oldToNew[oldSlots[i] + 1] = newSlots[order[i]] + 1;
        }

        int firstBodySlot = thisOffset + totalSize(args);
        int firstNewBodySlot = thisOffset + totalSize(newArgs);

        for (int i = firstBodySlot; i < oldToNew.length; i++) {
            oldToNew[i] = firstNewBodySlot + (i - firstBodySlot);
        }

        for (var insn : method.insns()) {
            if (insn instanceof VarInsnNode v && v.var < oldToNew.length && oldToNew[v.var] != -1) {
                v.var = oldToNew[v.var];
            } else if (insn instanceof IincInsnNode inc && inc.var < oldToNew.length && oldToNew[inc.var] != -1) {
                inc.var = oldToNew[inc.var];
            }
        }

        if (method.core().localVariables != null) {
            for (var lv : method.core().localVariables) {
                if (lv.index < oldToNew.length && oldToNew[lv.index] != -1)
                    lv.index = oldToNew[lv.index];
            }
        }

        method.core().desc = buildDesc(newArgs, method.returnType());
        if (method.core().signature != null)
            method.core().signature = null;
    }

    private void rewriteCallsite(MethodReference ref, int[] order) {
        var call   = (MethodInsnNode) ref.insn();
        var caller = ref.caller();

        var args      = Type.getArgumentTypes(call.desc);
        var isStatic  = call.getOpcode() == INVOKESTATIC;
        var newArgs   = new Type[args.length];

        for (int i = 0; i < order.length; i++) {
            newArgs[i] = args[order[i]];
        }

        var totalSlots = totalSize(args);
        var storeBase  = caller.allocVar(Type.getObjectType("java/lang/Object"));

        var storeSlots = new int[args.length];
        int s = storeBase;
        for (int i = args.length - 1; i >= 0; i--) {
            storeSlots[i] = s;
            s += args[i].getSize();
        }
        caller.core().maxLocals = Math.max(caller.core().maxLocals, s);

        var list = new InsnList();

        for (int i = args.length - 1; i >= 0; i--) {
            list.add(new VarInsnNode(args[i].getOpcode(ISTORE), storeSlots[i]));
        }

        for (int i = 0; i < order.length; i++) {
            int src = order[i];
            list.add(new VarInsnNode(args[src].getOpcode(ILOAD), storeSlots[src]));
        }

        caller.insns().insertBefore(call, list);
        call.desc = buildDesc(newArgs, Type.getReturnType(call.desc));
    }

    private boolean shouldSkip(Context context, ReferenceGraph graph, JClass clazz, JMethod method) {
        if (cantEditMethod(clazz, method, true))
            return true;

        if (clazz.isLibMethod(method))
            return true;

        if (Exclusions.PARAMETER_OBFUSCATE.excluded(clazz))
            return true;

        if (Exclusions.PARAMETER_OBFUSCATE.excluded(method))
            return true;

        for (var impacted : impactedClasses(context, clazz, method)) {
            if (Exclusions.PARAMETER_OBFUSCATE.excluded(impacted))
                return true;
        }

        var refs = graph.refs(method);
        if (refs.stream().anyMatch(MethodReference::cantEdit))
            return true;

        return false;
    }

    private Set<JClass> impactedClasses(Context context, JClass clazz, JMethod method) {
        var classes = new HashSet<>(clazz.children());
        classes.add(clazz);

        for (var parent : clazz.tree()) {
            if (!parent.hasMethodInTree(context, method))
                continue;

            classes.add(parent);
            classes.addAll(parent.children());
        }

        return classes;
    }

    private int[] shuffleOrder(int length) {
        var order = new int[length];
        for (int i = 0; i < length; i++) order[i] = i;

        for (int i = length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }

        return order;
    }

    private boolean isIdentity(int[] order) {
        for (int i = 0; i < order.length; i++) {
            if (order[i] != i) return false;
        }
        return true;
    }

    private int totalSize(Type[] types) {
        int size = 0;
        for (var t : types) size += t.getSize();
        return size;
    }

    private String buildDesc(Type[] args, Type ret) {
        var sb = new StringBuilder("(");
        for (var t : args) sb.append(t.getDescriptor());
        sb.append(")").append(ret.getDescriptor());
        return sb.toString();
    }
}
