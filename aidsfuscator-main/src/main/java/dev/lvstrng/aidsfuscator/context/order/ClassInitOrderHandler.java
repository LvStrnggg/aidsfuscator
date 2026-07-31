package dev.lvstrng.aidsfuscator.context.order;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.log.Logger;
import dev.lvstrng.aidsfuscator.transform.impl.salt.ClassSaltTransformer;
import dev.lvstrng.aidsfuscator.tree.impl.JClass;
import dev.lvstrng.aidsfuscator.utils.Pair;

import java.util.HashSet;
import java.util.Set;

/**
 * Handles class initialization order used further in obfuscation by class salting.
 * @see ClassSaltTransformer
 * @author lvstrng
 */
public class ClassInitOrderHandler {
    private final Context context;
    private final Set<Pair<JClass, JClass>> pairs; // before -> after

    public ClassInitOrderHandler(Context context) {
        this.context = context;
        this.pairs = new HashSet<>();
    }

    public void add(String before, String after) {
        before = before.replace('.', '/');
        after = after.replace('.', '/');

        final String finalBefore = before;
        var firstClass = context.jarClasses().stream().filter(e -> e.name().equals(finalBefore)).findFirst().orElse(null);

        final String finalAfter = after;
        var secondClass = context.jarClasses().stream().filter(e -> e.name().equals(finalAfter)).findFirst().orElse(null);

        if(firstClass == null && secondClass == null) {
            Logger.warn("Skipping initOrder statement for pair (%s -> %s): neither class is found or is in JAR classes list", before, after);
            return;
        }

        if(firstClass == null) {
            Logger.warn("Class %s in initOrder pair (%s -> %s) isn't in JAR classes list; skipping class salting for %s to avoid initialization order issues", before, before, after, after);
            secondClass.setInitOrderForeign(true);
            return;
        }

        if(secondClass == null) {
            Logger.warn("Class %s in initOrder pair (%s -> %s) isn't in JAR classes list; skipping class salting for %s to avoid initialization order issues", after, before, after, before);
            firstClass.setInitOrderForeign(true);
            return;
        }

        secondClass.setFirstInitializerClass(firstClass);
        firstClass.initializes().add(secondClass);
        pairs.add(new Pair<>(firstClass, secondClass));
    }

    public Set<Pair<JClass, JClass>> pairs() {
        return pairs;
    }
}
