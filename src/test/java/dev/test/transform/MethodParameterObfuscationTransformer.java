package dev.test.transform;

import dev.lvstrng.aidsfuscator.analysis.ref.MethodCallNode;
import dev.lvstrng.aidsfuscator.analysis.ref.ReferenceGraph;
import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.property.Property;
import dev.lvstrng.aidsfuscator.transform.Transformer;
import dev.lvstrng.aidsfuscator.tree.JClass;
import dev.lvstrng.aidsfuscator.tree.JMethod;
import dev.lvstrng.aidsfuscator.utils.ASMUtils;
import dev.lvstrng.aidsfuscator.utils.InsnBuilder;
import dev.lvstrng.aidsfuscator.utils.MemberUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;


public class MethodParameterObfuscationTransformer extends Transformer {
    public MethodParameterObfuscationTransformer() {
        super("Obfuscate Method Parameters", "methodParameterObfuscate");
    }

    @Override
    public void transform(Context context) {
        var graph = context.referenceGraph().build();
        var methods = new HashMap<JMethod, String>(); // method -> old desc

        for(var clazz : context.classes()) {
            for(var method : clazz.methods()) {
                registerMethod(graph, clazz, method, methods);
            }
        }

        var modified = new HashSet<AbstractInsnNode>();
        for(var entry : methods.entrySet()) {
            var method = entry.getKey();
            var desc = entry.getValue();

            var args = Type.getArgumentTypes(desc);
            var refs = new LinkedHashSet<MethodCallNode>();
            refs.addAll(graph.refs(method));

            for(var node : refs) {
                var call = (MethodInsnNode) node.insn();
                if(!modified.add(call))
                    continue;

                var list = new InsnBuilder()
                        .add(context.properties().add(ASMUtils.pushInt(args.length), Property.IGNORE_INTEGER))
                        .anewarray("java/lang/Object");

                for(int i = args.length - 1; i >= 0; i--) {
                    var arg = args[i];

                    if(arg.getSize() == 2) {
                        list.dup_x2().dup_x2().pop();
                    } else list.dup_x1().swap();

                    ASMUtils.box(list.result(), arg);
                    list
                            .add(context.properties().add(ASMUtils.pushInt(i), Property.IGNORE_INTEGER))
                            .swap()
                            .aastore();
                }

                node.caller().insns().insertBefore(call, list.result());
                call.desc = node.method().desc();
            }

            if(method.hasSalt())
                method.salt().updateVar(method.salt().local() + 1);

            if(method.isAbstract() || method.insns().size() == 0)
                continue;

            var array = method.isVirtual() ? 1 : 0;
            var current = array + 1;
            var builder = new InsnBuilder();

            builder._var(ALOAD, array);
            for(int i = 0; i < args.length; i++) {
                var arg = args[i];

                builder
                        .dup()
                        .add(context.properties().add(ASMUtils.pushInt(i), Property.IGNORE_INTEGER))
                        .aaload();
                ASMUtils.unbox(builder.result(), arg);
                builder._var(arg.getOpcode(ISTORE), current);

                current += arg.getSize();
            }
            builder.add(method.setSafeInsn(new InsnNode(POP)));

            for(var insn : method.insns()) {
                switch (insn) {
                    case VarInsnNode v -> {
                        if(method.isVirtual() && v.var == 0)
                            continue;

                        v.var++;
                    }
                    case IincInsnNode v -> v.var++;
                    default -> {}
                }
            }

            method.insns().insert(builder.result());
            method.core().access &= ~ACC_VARARGS;
            method.core().maxLocals++;
            markChange();
        }
    }

    private void registerMethod(ReferenceGraph graph, JClass clazz, JMethod method, Map<JMethod, String> methods) {
        if(this.cantEditMethod(clazz, method))
            return;

        if(this.isIgnoredSynthetic(method))
            return;

        var hierarchy = this.methodFamily(method);
        if(this.shouldSkipHierarchy(graph, hierarchy))
            return;

        if(hierarchy.stream().anyMatch(methods::containsKey))
            return;

        for(var member : hierarchy) {
            var newDesc = "([Ljava/lang/Object;)" + member.returnType().getDescriptor();
            if(this.hasDuplicateSignature(hierarchy, member.name(), newDesc))
                return;
        }

        for(var member : hierarchy) {
            if(member.isLibrary())
                continue;

            if(methods.containsKey(member))
                continue;

            methods.put(member, member.desc());
            member.core().desc = "([Ljava/lang/Object;)" + member.returnType().getDescriptor();
            member.core().signature = null;
        }
    }

    private Set<JMethod> methodFamily(JMethod method) {
        var family = new LinkedHashSet<JMethod>();
        family.add(method);
        family.addAll(method.tree());

        var classes = new LinkedHashSet<JClass>();
        for(var member : family) {
            classes.add(member.owner());
            classes.addAll(member.owner().tree());
        }

        for(var clazz : classes)
            for(var candidate : clazz.methods())
                if(method.name().equals(candidate.name()) &&
                        Arrays.equals(Type.getArgumentTypes(method.desc()), Type.getArgumentTypes(candidate.desc())))
                    family.add(candidate);

        return family;
    }

    private boolean shouldSkipHierarchy(ReferenceGraph graph, Set<JMethod> hierarchy) {
        for(var member : hierarchy) {
            if(member.isLibrary())
                return true;

            if(this.cantEditMethod(member.owner(), member))
                return true;

            if(this.isIgnoredSynthetic(member))
                return true;

            var refs = graph.refs(member);
            if(refs.stream().anyMatch(e -> !e.canEdit()))
                return true;
        }

        return false;
    }

    private boolean hasDuplicateSignature( Set<JMethod> hierarchy, String name, String desc) {
        var simpleName = MemberUtils.methodDesc(name, desc);
        var classes = new LinkedHashSet<JClass>();
        for(var member : hierarchy) {
            var owner = member.owner();
            if(owner.isLibrary())
                continue;

            classes.add(owner);
            for(var related : owner.tree()) {
                if(related.isLibrary())
                    continue;

                classes.add(related);
            }
        }

        for(var clazz : classes) {
            if(this.hasDuplicateSignatureInClass(clazz, hierarchy, simpleName))
                return true;

            for(var related : clazz.tree()) {
                if(related.isLibrary())
                    continue;

                if(this.hasDuplicateSignatureInClass(related, hierarchy, simpleName))
                    return true;
            }
        }

        return false;
    }

    private boolean hasDuplicateSignatureInClass(JClass clazz, Set<JMethod> hierarchy, String simpleName) {
        for(var method : clazz.methods()) {
            if(hierarchy.contains(method))
                continue;

            if(method.simpleName().equals(simpleName))
                return true;
        }

        return false;
    }

    private boolean isIgnoredSynthetic(JMethod method) {
        return (method.access() & ACC_SYNTHETIC) != 0 && !method.isBridge();
    }
}