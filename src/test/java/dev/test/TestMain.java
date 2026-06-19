package dev.test;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.exclude.impl.Exclusions;
import dev.lvstrng.aidsfuscator.transform.impl.data.ConstantsFixTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.data.ints.IntegerEncryptTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.data.strings.StringEncryptTransformer;
import dev.lvstrng.aidsfuscator.transform.impl.dynamic.ReferenceObfuscationTransformer;
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
//import dev.test.transform.InjectorTransformer;
import dev.test.transform.MethodInlineTransformer;
import dev.test.transform.MethodParameterObfuscationTransformer;
import dev.test.transform.TestTransformer;
import org.objectweb.asm.Opcodes;

public class TestMain {
    public static void main(String[] args) {
        var context = Context.newInstance()
                .computeFrames()
                .in("flappy.jar")
                .libs("libs")
                .out("out.jar")
                .setAggressiveOverload(true);

        //context.referenceManager().addMethodCandidate("*");
        context.referenceManager().addFieldCandidate("*");
        context.referenceManager().addMethodCandidate("*");

        Exclusions.GLOBAL.addClass("dev/lvstrng/aidsfuscator/api/*");
        context.run(
             //   new InjectorTransformer(),

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
        );
    }
}
