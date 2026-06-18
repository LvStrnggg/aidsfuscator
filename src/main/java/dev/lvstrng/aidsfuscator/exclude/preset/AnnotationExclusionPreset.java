package dev.lvstrng.aidsfuscator.exclude.preset;

import dev.lvstrng.aidsfuscator.api.ExcludeGlobal;
import dev.lvstrng.aidsfuscator.api.data.ExcludeConstantFix;
import dev.lvstrng.aidsfuscator.api.data.ExcludeIntegerEncryption;
import dev.lvstrng.aidsfuscator.api.data.ExcludeStringEncryption;
import dev.lvstrng.aidsfuscator.api.dynamic.ExcludeReferenceObfuscation;
import dev.lvstrng.aidsfuscator.api.flow.ExcludeFlattening;
import dev.lvstrng.aidsfuscator.api.flow.ExcludeShuffling;
import dev.lvstrng.aidsfuscator.api.optimize.ExcludeTrim;
import dev.lvstrng.aidsfuscator.api.rename.ExcludeClassRename;
import dev.lvstrng.aidsfuscator.api.rename.ExcludeFieldRename;
import dev.lvstrng.aidsfuscator.api.rename.ExcludeMethodRename;
import dev.lvstrng.aidsfuscator.api.salt.ExcludeClassSalting;
import dev.lvstrng.aidsfuscator.api.salt.ExcludeMethodSalting;
import dev.lvstrng.aidsfuscator.api.strip.ExcludeLineNumbers;
import dev.lvstrng.aidsfuscator.api.strip.ExcludeLocalVariableNames;
import dev.lvstrng.aidsfuscator.exclude.IExclusionPreset;
import dev.lvstrng.aidsfuscator.exclude.impl.Exclusions;

/**
 * Default exclusion preset
 */
public class AnnotationExclusionPreset implements IExclusionPreset {
    @Override
    public void load() {
        Exclusions.GLOBAL.addClass("dev/lvstrng/aidsfuscator/api/*");
        Exclusions.GLOBAL.addAnnotation(internal(ExcludeGlobal.class));

        Exclusions.RENAME_CLASS.addAnnotation(internal(ExcludeClassRename.class));
        Exclusions.RENAME_FIELD.addAnnotation(internal(ExcludeFieldRename.class));
        Exclusions.RENAME_METHOD.addAnnotation(internal(ExcludeMethodRename.class));

        Exclusions.LOCAL_NAMES.addAnnotation(internal(ExcludeLocalVariableNames.class));
        Exclusions.LINE_NUMBERS.addAnnotation(internal(ExcludeLineNumbers.class));
        Exclusions.TRIM.addAnnotation(internal(ExcludeTrim.class));

        Exclusions.METHOD_SALTING.addAnnotation(internal(ExcludeMethodSalting.class));
        Exclusions.CLASS_SALTING.addAnnotation(internal(ExcludeClassSalting.class));
        // PARAMETERS

        Exclusions.REFERENCE_OBFUSCATE.addAnnotation(internal(ExcludeReferenceObfuscation.class));
        Exclusions.FIX_CONSTANTS.addAnnotation(internal(ExcludeConstantFix.class));
        Exclusions.INTEGER_ENCRYPTION.addAnnotation(internal(ExcludeIntegerEncryption.class));
        Exclusions.STRING_ENCRYPTION.addAnnotation(internal(ExcludeStringEncryption.class));

        Exclusions.FLOW_FLATTEN.addAnnotation(internal(ExcludeFlattening.class));
        Exclusions.FLOW_SHUFFLE.addAnnotation(internal(ExcludeShuffling.class));
    }
}
