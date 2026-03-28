package dev.lvstrng.aidsfuscator.transform;

import dev.lvstrng.aidsfuscator.transform.impl.data.IntegerEncryptTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.misc.MemberShuffler;
import dev.lvstrng.aidsfuscator.transform.impl.misc.WatermarkTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.rename.ClassRenameTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.rename.FieldRenameTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.rename.MethodRenameTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.salt.MethodSaltTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.strip.LocalVariableNameTransformer;

import java.util.Comparator;
import java.util.List;

/**
 * Aidsfuscator works in a fixed order, because when the user knows no better, we don't want useless issues on the GitHub page.
 */
public final class TransformerOrder {
    private static final List<Transformer> transformers = List.of(
            new FieldRenameTransformer(),
            new MethodRenameTransformer(),
            new ClassRenameTransformer(),

            new LocalVariableNameTransformer(),
            new MethodSaltTransformer(),

            new IntegerEncryptTransformer(),

            new MemberShuffler(),
            new WatermarkTransformer()
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
