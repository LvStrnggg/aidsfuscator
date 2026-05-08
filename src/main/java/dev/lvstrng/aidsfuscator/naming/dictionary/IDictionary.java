package dev.lvstrng.aidsfuscator.naming.dictionary;

import dev.lvstrng.aidsfuscator.tree.JClass;

public interface IDictionary {
    String newClassName(String prefix);
    String newMethodName(String prefix, JClass owner, String desc);
    String newFieldName(String prefix, JClass owner, String desc);
    String newResourceName(String prefix);

    String newClassName();
    String newMethodName(JClass owner, String desc);
    String newFieldName(JClass owner, String desc);
    String newResourceName();
    String newName(int count);

    void revertClass();
    void revertMethod();
    void revertField();
    void revertResource();
}
