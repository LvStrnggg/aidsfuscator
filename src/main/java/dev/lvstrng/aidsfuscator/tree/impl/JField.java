package dev.lvstrng.aidsfuscator.tree.impl;

import dev.lvstrng.aidsfuscator.property.PropertyContainer;
import dev.lvstrng.aidsfuscator.tree.IAccessFlags;
import dev.lvstrng.aidsfuscator.tree.IAnnotatable;
import dev.lvstrng.aidsfuscator.tree.IHierarchical;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A FieldNode wrapper for easier use.
 */
public class JField implements IAccessFlags, IHierarchical<JField>, IAnnotatable {
    private JClass owner;
    private FieldNode core;
    private final PropertyContainer properties;

    private boolean library;
    private final String originalName, originalDesc;
    private String mappedName;

    private Set<JField> parents, children;

    public JField(FieldNode core) {
        this.properties = new PropertyContainer();
        this.library = false;
        this.originalName = core.name;
        this.originalDesc = core.desc;

        this.setCore(core);
    }

    public String originalName() {
        return originalName;
    }

    public String originalDesc() {
        return originalDesc;
    }

    public PropertyContainer properties() {
        return properties;
    }

    public void setLibrary() {
        this.library = true;
    }

    public boolean isLibrary() {
        return library;
    }

    public void setCore(FieldNode core) {
        this.core = core;
        setMappedName(core.name);

        this.parents = new HashSet<>();
        this.children = new HashSet<>();
    }

    public void setOwner(JClass owner) {
        this.owner = owner;
    }

    public JClass owner() {
        return owner;
    }

    @Override
    public Set<JField> parents() {
        return parents;
    }

    @Override
    public Set<JField> children() {
        return children;
    }

    public FieldNode core() {
        return core;
    }

    @Override
    public int access() {
        return core.access;
    }

    @Override
    public void setAccess(int flags) {
        core.access = flags;
    }

    public String name() {
        return core.name;
    }

    public String desc() {
        return core.desc;
    }

    public String signature() {
        return core.signature;
    }

    public Object value() {
        return core.value;
    }

    public void setValue(Object value) {
        core.value = value;
    }

    public String mappedName() {
        return mappedName;
    }

    public void setMappedName(String mappedName) {
        this.mappedName = mappedName;
    }

    public String simpleName() {
        return "%s %s".formatted(name(), desc());
    }

    public String simpleOriginalName() {
        return "%s %s".formatted(originalName, originalDesc);
    }

    public String fullOriginalName() {
        return "%s.%s".formatted(owner.originalName(), simpleOriginalName());
    }

    public String fullName() {
        return "%s.%s".formatted(owner, simpleName());
    }

    @Override
    public boolean isNonHierarchical() {
        return isPrivate() || isStatic();
    }

    @Override
    public String toString() {
        return fullName();
    }

    @Override
    public List<AnnotationNode> annotations() {
        var list = new ArrayList<AnnotationNode>();
        if(core.visibleAnnotations != null)
            list.addAll(core.visibleAnnotations);

        if(core.invisibleAnnotations != null)
            list.addAll(core.invisibleAnnotations);
        return list;
    }

    @Override
    public void removeAnnotation(String annotation) {
        if(core.visibleAnnotations != null)
            core.visibleAnnotations.removeIf(e -> e.desc.equals(annotation));
        if(core.invisibleAnnotations != null)
            core.invisibleAnnotations.removeIf(e -> e.desc.equals(annotation));
    }
}
