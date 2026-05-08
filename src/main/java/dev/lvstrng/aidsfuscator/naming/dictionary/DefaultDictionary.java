package dev.lvstrng.aidsfuscator.naming.dictionary;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.naming.Mappings;
import dev.lvstrng.aidsfuscator.tree.JClass;

/**
 * Dictionary that does aggressive renaming. {@code revert} methods do not have implementations (shouldn't have one either way).
 * @author lvstrng
 */
public class DefaultDictionary implements IDictionary {
    private final Context context;
    private final String dictionary;
    private int classCounter = 0;
    private int resourceCounter = 0;

    public DefaultDictionary(Context context, String dictionary) {
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
        } while (isMethodMapped(owner, result, desc));

        return result;
    }

    @Override
    public String newFieldName(String prefix, JClass owner, String desc) {
        int counter = 0;
        var result = "";

        do {
            result = prefix + newName(counter++);
        } while (isFieldMapped(owner, result, desc));

        return result;
    }

    @Override public String newResourceName(String prefix) {
        var result = "";

        do {
          result = prefix + newName(resourceCounter++);
        } while (isResourceMapped(result));

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
    public String newResourceName() {
        return newResourceName("");
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
        return context.classMap().containsKey(name) || Mappings.CLASS.containsNew(name);
    }

    private boolean isFieldMapped(JClass owner, String name, String desc) {
        var simpleName = String.format("%s %s", name, desc);

        for(var member : owner.tree()) {
            var id = String.format("%s.%s", member, simpleName);
            if(Mappings.FIELD.containsNew(id))
                return true;

            if(member.fields().stream().anyMatch(e -> e.simpleName().equals(simpleName)))
                return true;
        }

        var id = String.format("%s.%s", owner, simpleName);
        if(Mappings.FIELD.containsNew(id))
            return true;

        return owner.fields().stream().anyMatch(e -> e.simpleName().equals(simpleName));
    }

    private boolean isMethodMapped(JClass owner, String name, String desc) {
        var simpleName = String.format("%s%s", name, desc);

        for(var member : owner.tree()) {
            var id = String.format("%s.%s", member, simpleName);
            if(Mappings.METHOD.containsNew(id))
                return true;

            if(member.methods().stream().anyMatch(e -> e.simpleName().equals(simpleName)))
                return true;
        }

        var id = String.format("%s.%s", owner, simpleName);
        if(Mappings.METHOD.containsNew(id))
            return true;

        return owner.methods().stream().anyMatch(e -> e.simpleName().equals(simpleName));
    }

    private boolean isResourceMapped(String name) {
        return context.resourceMap().containsKey(name) || Mappings.RESOURCE.containsNew(name);
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

    @Override
    public void revertResource() {
        // eat five star, do nothing
    }
}
