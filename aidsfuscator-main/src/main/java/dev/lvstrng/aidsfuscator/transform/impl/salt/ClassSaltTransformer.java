package dev.lvstrng.aidsfuscator.transform.impl.salt;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.exclude.impl.Exclusions;
import dev.lvstrng.aidsfuscator.property.Property;
import dev.lvstrng.aidsfuscator.salt.impl.ClassSalt;
import dev.lvstrng.aidsfuscator.transform.Setting;
import dev.lvstrng.aidsfuscator.transform.Transformer;
import dev.lvstrng.aidsfuscator.tree.impl.JClass;
import dev.lvstrng.aidsfuscator.tree.impl.JMethod;
import dev.lvstrng.aidsfuscator.utils.ASMUtils;
import dev.lvstrng.aidsfuscator.utils.InsnBuilder;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;

public class ClassSaltTransformer extends Transformer {
    private final Setting<Boolean> unifyAccess = setting("unifyAccess", true);

    public ClassSaltTransformer() {
        super("Class Salting", "classSalting");
    }

    @Override
    public void transform(Context context) {
        for(var clazz : context.classes()) {
            if(clazz.isModule())
                continue;

            if(clazz.isInitOrderForeign())
                continue;

            if(Exclusions.CLASS_SALTING.excluded(clazz))
                continue;

            if(!clazz.isPublic()) {
                if(!unifyAccess.value())
                    continue;

                clazz.removeAccessFlags(ACC_PRIVATE);
                clazz.removeAccessFlags(ACC_PROTECTED);
                clazz.addAccessFlags(ACC_PUBLIC);
            }

            var val = random.nextInt();
            var name = context.dictionary().newFieldName(clazz, "I");

            var field = clazz.createField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, name, "I");
            field.properties().add(Property.SALT_ARTIFACT);
            clazz.setSalt(new ClassSalt(clazz, field, val));

            for(var method : clazz.methods()) {
                if(Exclusions.CLASS_SALTING.excluded(method))
                    continue;

                if(method.hasSalt())
                    continue;

                if(method.isAbstract() || method.isNative() || method.insns().size() == 0)
                    continue;

                if(method.name().equals("<clinit>"))
                    continue;

                var saltLocal = method.allocVar(Type.INT_TYPE);
                var saltValue = random.nextInt();
                prepSalt(context, clazz, method, saltLocal, saltValue);
                obfuscateUnprotectedSalts(context, method, saltLocal, saltValue);
            }

            markChange();
        }

        context.addArtificial(context.saltDispatcher().create(context));
    }

    private void obfuscateUnprotectedSalts(Context context, JMethod method, int saltLocal, int saltValue) {
        var frames = method.frames(context);
        for(var insn : method.insns()) {
            if(!context.properties().get(insn).has(Property.UNPROTECTED_SALT))
                continue;

            if(frames.get(insn).getLocal(saltLocal).isUninitialized())
                continue;

            var list = new InsnList();

            var n = ASMUtils.getInt(insn);
            var mask = method.seed();
            var masked = saltValue & mask;

            list.add(method.salt().load());
            list.add(context.properties().add(ASMUtils.pushInt(mask), Property.IGNORE_INTEGER, Property.IGNORE_FLOW_INTS));
            list.add(new InsnNode(IAND));
            list.add(context.properties().add(ASMUtils.pushInt(masked ^ n), Property.IGNORE_INTEGER));
            list.add(new InsnNode(IXOR));

            method.insns().insertBefore(insn, list);
            method.insns().remove(insn);
        }
    }

    private void prepSalt(Context context, JClass clazz, JMethod method, int saltLocal, int saltValue) {
        var list = new InsnBuilder();
        var mask = method.seed();
        var maskedSalt = clazz.salt().value() & mask;

        list
                .add(clazz.salt().load())
                ._int(mask).addProps(context, Property.IGNORE_INTEGER, Property.IGNORE_FLOW_INTS)
                .iand()
                ._int(maskedSalt ^ saltValue).addProps(context, Property.IGNORE_INTEGER)
                .ixor()
                ._var(ISTORE, saltLocal);

        method.makeSalt(saltValue, saltLocal);
        method.addUnsafeInstructions(list.result());
    }
}
