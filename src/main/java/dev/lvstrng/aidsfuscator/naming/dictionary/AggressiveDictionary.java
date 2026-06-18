package dev.lvstrng.aidsfuscator.naming.dictionary;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.naming.Mappings;
import dev.lvstrng.aidsfuscator.tree.impl.JClass;

/**
 * Dictionary that does aggressive renaming. {@code revert} methods do not have implementations (shouldn't have one either way).
 * @author lvstrng
 */
public class AggressiveDictionary implements IDictionary {
    private final Context context;
    private final String dictionary;
    private int classCounter = 0;

    public AggressiveDictionary(Context context, String dictionary) {
        this.context = context;
        this.dictionary = dictionary;
    }

    @Override
    public String newClassName(String prefix) {
        var result = "";

        do {
            result = prefix + newName(classCounter++);
        } while (isClassMapped(result));

        return result;
    }

    @Override
    public String newMethodName(String prefix, JClass owner, String desc) {
        int counter = 0;
        var result = "";

        do {
            result = prefix + newName(counter++);
        } while (owner.isMethodMappedExact(result, desc));

        return result;
    }

    @Override
    public String newFieldName(String prefix, JClass owner, String desc) {
        int counter = 0;
        var result = "";

        do {
            result = prefix + newName(counter++);
        } while (owner.isFieldMappedExact(result, desc));

        return result;
    }

    @Override
    public String newClassName() {
        return newClassName("");
    }

    @Override
    public String newMethodName(JClass owner, String desc) {
        return newMethodName("", owner, desc);
    }

    @Override
    public String newFieldName(JClass owner, String desc) {
        return newFieldName("", owner, desc);
    }

    @Override
    public String newName(int count) {
        int base = dictionary.length();
        var result = new StringBuilder();

        for (int i = count; i >= 0; i = (i / base) - 1) {
            int idx = i % base;
            result.append(dictionary.charAt(idx));
        }

        return result.reverse().toString();
    }

    private boolean isClassMapped(String name) {
        return context.hasJarClass(name) || Mappings.CLASS.containsNew(name);
    }

    @Override
    public void revertClass() {
        // do nothing
    }

    @Override
    public void revertMethod() {
        // do nothing
    }

    @Override
    public void revertField() {
        // do nothing
    }
}
