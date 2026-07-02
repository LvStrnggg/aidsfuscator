package dev.lvstrng.aidsfuscator.transform.impl.dynamic;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.exclude.impl.Exclusions;
import dev.lvstrng.aidsfuscator.transform.Setting;
import dev.lvstrng.aidsfuscator.transform.Transformer;
import dev.lvstrng.aidsfuscator.tree.impl.JClass;
import dev.lvstrng.aidsfuscator.utils.InsnBuilder;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;

public class InvokeDynamicTransformer extends Transformer {
    private static final String bsmDesc = MethodType.methodType(
            CallSite.class,
            MethodHandles.Lookup.class,
            String.class,
            MethodType.class
    ).toMethodDescriptorString();

    private final Setting<Integer> maxPerClass = setting("maxPerClass", 25);
    private final Setting<Integer> chance = setting("chance", 35);

    private final Map<JClass, String> bootstrapNames = new HashMap<>();

    public InvokeDynamicTransformer() {
        super("Invoke Dynamic", "invokeDynamic");
    }

    @Override
    public void transform(Context context) {
        for(var clazz : context.classes()) {
            if(Exclusions.GLOBAL.excluded(clazz))
                continue;

            if(clazz.version() < Opcodes.V1_7)
                continue;

            if(clazz.isInterface() || clazz.isAnnotation())
                continue;

            var touched = 0;
            for(var method : clazz.methods()) {
                if(touched >= maxPerClass.value())
                    break;

                if(cantEditMethod(clazz, method))
                    continue;

                if(Exclusions.INVOKE_DYNAMIC.excluded(method))
                    continue;

                for(var insn : method.insns().toArray()) {
                    if(touched >= maxPerClass.value())
                        break;

                    if(!(insn instanceof MethodInsnNode call))
                        continue;

                    if(call.getOpcode() != Opcodes.INVOKESTATIC && call.getOpcode() != Opcodes.INVOKEVIRTUAL)
                        continue;

                    if(call.owner.startsWith("["))
                        continue;

                    if(call.name.equals("<init>") || call.name.equals("<clinit>"))
                        continue;

                    if(random.nextInt(100) >= chance.value())
                        continue;

                    var bootstrapName = bootstrapNames.computeIfAbsent(clazz, c -> createBootstrap(context, c));

                    var isStatic = call.getOpcode() == Opcodes.INVOKESTATIC;
                    var desc = isStatic ? call.desc : call.desc.replace("(", "(Ljava/lang/Object;");

                    var handle = new Handle(Opcodes.H_INVOKESTATIC, clazz.name(), bootstrapName, bsmDesc, false);
                    var indyName = encode(call.owner, call.name, isStatic);

                    var list = new InsnList();
                    new InsnBuilder(list).indy(indyName, desc, handle);

                    method.insns().insertBefore(call, list);
                    method.insns().remove(call);
                    touched++;
                    markChange();
                }
            }
        }
    }

    private String encode(String owner, String name, boolean isStatic) {
        return (isStatic ? "s$" : "v$") + owner.replace('/', '.') + "$" + name;
    }

    private String createBootstrap(Context context, JClass clazz) {
        var name = context.dictionary().newMethodName(clazz, bsmDesc);
        var method = clazz.createMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, name, bsmDesc);

        var lookupParam = method.allocVar();
        var nameParam = method.allocVar();
        var typeParam = method.allocVar();

        var tag = method.allocVar(Type.INT_TYPE);
        var sepIndex = method.allocVar(Type.INT_TYPE);
        var ownerName = method.allocVar();
        var methodName = method.allocVar();
        var ownerClass = method.allocVar();
        var handle = method.allocVar();

        var isStaticLabel = new LabelNode();
        var afterLabel = new LabelNode();

        var builder = new InsnBuilder(method.insns())
                .label()
                ._var(Opcodes.ALOAD, nameParam)
                ._int(0)
                .method(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C")
                ._int((int) 's')
                .jump(Opcodes.IF_ICMPEQ, isStaticLabel)
                ._int(0)
                ._goto(afterLabel)
                .label(isStaticLabel)
                ._int(1)
                .label(afterLabel)
                ._var(Opcodes.ISTORE, tag)

                .label()
                ._var(Opcodes.ALOAD, nameParam)
                ._const("$")
                ._int(2)
                .method(Opcodes.INVOKEVIRTUAL, "java/lang/String", "indexOf", "(Ljava/lang/String;I)I")
                ._var(Opcodes.ISTORE, sepIndex)

                .label()
                ._var(Opcodes.ALOAD, nameParam)
                ._int(2)
                ._var(Opcodes.ILOAD, sepIndex)
                .method(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;")
                ._var(Opcodes.ASTORE, ownerName)

                .label()
                ._var(Opcodes.ALOAD, ownerName)
                .method(Opcodes.INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;")
                ._var(Opcodes.ASTORE, ownerClass)

                .label()
                ._var(Opcodes.ALOAD, nameParam)
                ._var(Opcodes.ILOAD, sepIndex)
                ._int(1)
                .iadd()
                .method(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;")
                ._var(Opcodes.ASTORE, methodName);

        var staticLabel = new LabelNode();
        var exitLabel = new LabelNode();

        builder
                ._var(Opcodes.ILOAD, tag)
                .jump(Opcodes.IFEQ, staticLabel)

                ._var(Opcodes.ALOAD, lookupParam)
                ._var(Opcodes.ALOAD, ownerClass)
                ._var(Opcodes.ALOAD, methodName)
                ._var(Opcodes.ALOAD, typeParam)
                ._int(0)
                ._int(1)
                .method(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodType", "dropParameterTypes", "(II)Ljava/lang/invoke/MethodType;")
                .method(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;")
                ._var(Opcodes.ASTORE, handle)
                ._goto(exitLabel)

                .label(staticLabel)
                ._var(Opcodes.ALOAD, lookupParam)
                ._var(Opcodes.ALOAD, ownerClass)
                ._var(Opcodes.ALOAD, methodName)
                ._var(Opcodes.ALOAD, typeParam)
                .method(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;")
                ._var(Opcodes.ASTORE, handle)

                .label(exitLabel)
                .type(Opcodes.NEW, "java/lang/invoke/ConstantCallSite")
                .dup()
                ._var(Opcodes.ALOAD, handle)
                .method(Opcodes.INVOKESPECIAL, "java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V")
                ._areturn();

        return name;
    }
}