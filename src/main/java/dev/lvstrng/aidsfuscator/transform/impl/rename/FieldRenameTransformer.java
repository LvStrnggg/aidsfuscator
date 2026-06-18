package dev.lvstrng.aidsfuscator.transform.impl.rename;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.exclude.impl.Exclusions;
import dev.lvstrng.aidsfuscator.log.Logger;
import dev.lvstrng.aidsfuscator.naming.Mapping;
import dev.lvstrng.aidsfuscator.naming.Mappings;
import dev.lvstrng.aidsfuscator.transform.Setting;
import dev.lvstrng.aidsfuscator.transform.Transformer;
import dev.lvstrng.aidsfuscator.tree.impl.JClass;
import dev.lvstrng.aidsfuscator.tree.impl.JField;
import dev.lvstrng.aidsfuscator.utils.MemberUtils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class FieldRenameTransformer extends Transformer {
    private final Setting<Boolean>  preserveRecordNames = setting("preserveRecordNames", true);
    private final Setting<Boolean>  keepSerializableNames = setting("keepSerializableNames", true);
    private final Setting<String>   prefix = setting("prefix", "");
    private final Setting<Boolean>  shuffle = setting("shuffle", false);

    public FieldRenameTransformer() {
        super("Rename Fields", "renameFields");
    }

    @Override
    public void transform(Context context) {
        for(var clazz : context.classes()) {
            mapFields(context, clazz);
        }

        remap(context);
        if(!preserveRecordNames.value())
            transformRecordMethods(context);
        Mappings.FIELD.clearTemp();
    }

    /**
     * Updates invokedynamic instructions in records from ObjectMethods.bootstrap to have the new remapped field names. Issue link: (<a href="https://github.com/LvStrnggg/aidsfuscator/issues/25">...</a>)
     * @param context obfuscator context
     * @author lvstrng
     */
    private void transformRecordMethods(Context context) {
        if(!context.hasClass("java/lang/runtime/ObjectMethods")) {
            Logger.warn("Couldn't find java.lang.runtime.ObjectMethods class in obfuscator context, skipping preserveRecordNames");
            return;
        }

        var objectMethodsClass = context.forName("java/lang/runtime/ObjectMethods");
        var bootstrapMethod = objectMethodsClass.findMethod(
                "bootstrap",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;"
        ).orElse(null);

        if(bootstrapMethod == null) {
            Logger.warn("Failed to find `bootstrap` method in `java.lang.runtime.ObjectMethods` class, report this bug to the github page.");
            return;
        }

        // look for bootstrap method refs
        var graph = context.referenceGraph().build();
        var bootstrapRefs = graph.refs(bootstrapMethod);

        for(var ref : bootstrapRefs) {
            if(!(ref.insn() instanceof InvokeDynamicInsnNode indy))
                continue;

            var callerClass = ref.callerClass();
            if(!callerClass.isRecord() || indy.bsmArgs.length < 2)
                continue;

            // string with old names, kys
            var namesArg = indy.bsmArgs[1];
            if(!(namesArg instanceof String))
                continue;

            var newNames = new StringBuilder();
            for(int i = 2; i < indy.bsmArgs.length; i++) {
                var arg = indy.bsmArgs[i];
                if(!(arg instanceof Handle handle))
                    continue;

                newNames.append(handle.getName());
                if (i < indy.bsmArgs.length - 1) {
                    newNames.append(";");
                }
            }

            indy.bsmArgs[1] = newNames.toString();
        }
    }

    private void mapFields(Context context, JClass clazz) {
        if(shuffle.value()) {
            var seed = random.nextLong();

            Collections.shuffle(clazz.core().fields, new Random(seed));
            Collections.shuffle(clazz.fields(), new Random(seed));
        }

        for(var field : clazz.fields()) {
            var impactedClasses = impactedClasses(context, clazz, field);
            if(skipHierarchy(field, impactedClasses))
                continue;

            var name = findOrGenerateName(context, clazz, impactedClasses, field);
            for(var member : impactedClasses) {
                var opt = member.findField(field.name(), field.desc());
                if(opt.isPresent()) {
                    var f = opt.get();
                    f.setMappedName(name);
                }

                var oldId = MemberUtils.fullField(member, field);
                var newId = MemberUtils.fullField(member.name(), name, field.desc());
                Mappings.FIELD.register(oldId, new Mapping(newId, name));

                markChange();
            }
        }
    }

    private String findOrGenerateName(Context context, JClass clazz, Set<JClass> impactedClasses, JField field) {
        for(var member : impactedClasses) {
            var id = MemberUtils.fullField(member, field);

            if(Mappings.FIELD.containsOld(id))
                return Mappings.FIELD.retrieve(id).value();
        }

        return context.dictionary().newFieldName(prefix.value(), clazz, field.desc());
    }

    private boolean skipHierarchy(JField field, Set<JClass> impactedClass) {
        if(field.owner().isLibField(field))
            return true;

        for(var member : impactedClass) {
            var opt = member.findField(field.name(), field.desc());
            if(opt.isPresent())
                field = opt.get();

            if(Exclusions.RENAME_FIELD.excluded(member))
                return true;

            if(Exclusions.RENAME_FIELD.excluded(member, field))
                return true;

            if(keepSerializableNames.value() && member.tree().stream().anyMatch(e -> e.name().equals("java/io/Serializable")))
                return true;
        }

        return false;
    }

    private Set<JClass> impactedClasses(Context context, JClass clazz, JField field) {
        var classes = new HashSet<>(clazz.children());
        classes.add(clazz);

        for(var parent : clazz.parents()) {
            if(!parent.hasFieldInTree(context, field))
                continue;

            classes.add(parent);
        }

        return classes;
    }
}
