package dev.lvstrng.aidsfuscator.transform.impl.rename;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.exclude.impl.Exclusions;
import dev.lvstrng.aidsfuscator.naming.Mapping;
import dev.lvstrng.aidsfuscator.naming.Mappings;
import dev.lvstrng.aidsfuscator.transform.Setting;
import dev.lvstrng.aidsfuscator.transform.Transformer;

import java.util.ArrayList;
import java.util.Collections;

public class ClassRenameTransformer extends Transformer {
    private final Setting<String> prefix = setting("prefix", "");
    private final Setting<String> mixinPrefix = setting("mixinPrefix", "mixins/");
    private final Setting<Boolean> renameSourceFile = setting("renameSourceFile", true);
    private final Setting<Boolean> randomize = setting("randomize", false);

    public ClassRenameTransformer() {
        super("Rename Classes", "renameClasses");
    }

    @Override
    public void transform(Context context) {
        // ---- RANDOMIZATION ----
        var classes = new ArrayList<>(context.classes());
        if(randomize.value())
            Collections.shuffle(classes);

        // ---- REMAPPING ----
        for(var clazz : classes) {
            if(Exclusions.RENAME_CLASS.excluded(clazz))
                continue;

            var classPrefix = prefix.value();
            if(clazz.isMixin())
                classPrefix = mixinPrefix.value();

            var newClassName = context.dictionary().newClassName(classPrefix.replace('.', '/'));
            Mappings.CLASS.register(clazz.name(), new Mapping(newClassName, newClassName));

            if(renameSourceFile.value())
                clazz.setSourceFile(newClassName + ".java");
            markChange();
        }

        remap(context);
        Mappings.CLASS.clearTemp();
    }
}
