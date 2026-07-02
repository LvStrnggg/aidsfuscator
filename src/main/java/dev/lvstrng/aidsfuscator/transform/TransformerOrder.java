package dev.lvstrng.aidsfuscator.transform;

import dev.lvstrng.aidsfuscator.transform.impl.data.ConstantsFixTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.data.ints.encryption.IntegerEncryptTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.data.strings.StringEncryptTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.dynamic.ReferenceObfuscationTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.dynamic.InvokeDynamicTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.flow.ControlFlowFlatteningTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.flow.ControlFlowShufflingTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.optimize.DeadCodeCleanTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.optimize.TrimTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.rename.ClassRenameTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.rename.FieldRenameTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.rename.MethodRenameTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.salt.ClassSaltTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.salt.MethodSaltTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.strip.LineNumberTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.strip.LocalVariableNameTransformer;

import java.util.Comparator;
import java.util.List;

/**
 * Aidsfuscator works in a fixed order, because when the user knows no better, we don't want useless issues on the GitHub page.
 */
public final class TransformerOrder {
    /*
     * WARN LVSTRNG BEFORE CHANGING ORDER OR ADDING NEW TRANSFORMS.
     */
    private static final List<Transformer> transformers = List.of(
            new TrimTransformer(),

            new FieldRenameTransformer(),
            new MethodRenameTransformer(),
            new ClassRenameTransformer(),

            new LocalVariableNameTransformer(),
            new LineNumberTransformer(),
            new MethodSaltTransformer(),
            new ClassSaltTransformer(),

            new ConstantsFixTransformer(),
            new IntegerEncryptTransformer(),
            new StringEncryptTransformer(),

            new ControlFlowFlatteningTransformer(),
            new ControlFlowShufflingTransformer(),
            new DeadCodeCleanTransformer(),
            new ReferenceObfuscationTransformer()
            new InvokeDynamicTransformer()
    );

    @SuppressWarnings("unchecked")
    public <T> T get(Class<Transformer> clazz) {
        return (T) transformers.stream()
                .filter(clazz::isInstance)
                .findFirst().orElse(null);
    }

    public List<Transformer> sorted(List<Transformer> transformers) {
        transformers.sort(Comparator.comparingInt(TransformerOrder.transformers::indexOf));
        return transformers;
    }

    public static List<Transformer> transformers() {
        return transformers;
    }
}
