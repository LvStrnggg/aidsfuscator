package dev.test;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.transform.impl.data.strings.StringEncryptTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.flow.ControlFlowFlatteningTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.flow.ControlFlowShufflingTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.rename.ClassRenameTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.rename.FieldRenameTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.rename.MethodRenameTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.salt.MethodSaltTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.salt.SimpleClassSaltTransformer;
import dev.test.transform.ClassSaltTransformer;
import dev.test.transform.MethodParameterObfuscationTransformer;

public class TestMain {
    public static void main(String[] args) {
        var context = Context.newInstance()
                .computeFrames()
                .in("eval.jar")
                .libs("libs/")
                .out("out.jar")
                .initialize();

        //context.referenceManager().addMethodCandidate("*");
        //context.referenceManager().addFieldCandidate("*");

        context.transform(
                new MethodSaltTransformer(),
                new SimpleClassSaltTransformer(),
                new StringEncryptTransformer(),
                new MethodParameterObfuscationTransformer()
        ).exportJar();
    }
}
