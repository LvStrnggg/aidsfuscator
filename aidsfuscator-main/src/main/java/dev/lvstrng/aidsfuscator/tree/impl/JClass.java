package dev.lvstrng.aidsfuscator.tree.impl;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.property.PropertyContainer;
import dev.lvstrng.aidsfuscator.salt.ISaltable;
import dev.lvstrng.aidsfuscator.salt.impl.ClassSalt;
import dev.lvstrng.aidsfuscator.tree.IAccessFlags;
import dev.lvstrng.aidsfuscator.tree.IAnnotatable;
import dev.lvstrng.aidsfuscator.tree.IHierarchical;
import dev.lvstrng.aidsfuscator.utils.MemberUtils;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.function.Predicate;

/**
 * A ClassNode wrapper for easier use.
 */
@SuppressWarnings("all")
public class JClass implements IAccessFlags, ISaltable<ClassSalt>, IHierarchical<JClass>, IAnnotatable {
    private ClassNode core;
    private final PropertyContainer properties;

    private final String originalName;
    private boolean library;

    private Set<JClass> parents, children;
    private final List<JField> fields;
    private final List<JMethod> methods;

    private ClassSalt salt;

    private JClass initializerClass; // class that initializes
    private final List<JClass> initializes; // initialized these classes

    private boolean initOrderForeign;

    public JClass(ClassNode core) {
        this.properties = new PropertyContainer();
        this.library = false;
        this.originalName = core.name;

        this.fields = new ArrayList<>();
        this.methods = new ArrayList<>();
        this.initializes= new ArrayList<>();

        this.setCore(core);

        core.methods.forEach(this::add);
        core.fields.forEach(this::add);
    }

    public boolean isMethodMappedExact(String name, String desc) {
        return isMethodMappedExact(name, desc, tree());
    }

    /**
     * Scans the class tree to see if theres a method with the specified name AND descriptor
     * @param name name to check for
     * @param desc descriptor to check for
     */
    public boolean isMethodMappedExact(String name, String desc, Collection<JClass> classes) {
        if(methods().stream().anyMatch(e -> e.mappedName().equals(name) && e.desc().equals(desc)))
            return true;

        for(var member : classes) {
            if(member.methods().stream().anyMatch(e -> e.mappedName().equals(name) && e.desc().equals(desc)))
                return true;
        }
        return methods().stream().filter(e -> e.mappedName().equals(name)).filter(e -> e.desc().equals(desc)).findAny().isPresent();
    }

    /**
     * Scans the class tree to see if theres a field with the specified name AND descriptor
     * @param name name to check for
     * @param desc descriptor to check for
     */
    public boolean isFieldMappedExact(String name, String desc) {
        return isFieldMappedExact(name, desc, tree());
    }

    public boolean isFieldMappedExact(String name, String desc, Collection<JClass> classes) {
        if(tree().stream().anyMatch(e -> e.name().equals("java/io/Serializable"))) // serializable check
            return isFieldMapped(name, desc, classes);

        if(fields().stream().anyMatch(e -> e.mappedName().equals(name) && e.desc().equals(desc)))
            return true;

        for(var member : classes) {
            if(member.fields().stream().anyMatch(e -> e.mappedName().equals(name) && e.desc().equals(desc)))
                return true;
        }

        return fields().stream().filter(e -> e.mappedName().equals(name)).filter(e -> e.desc().equals(desc)).findAny().isPresent();
    }

    /**
     * Scans the class tree to see if theres a method with the specified name (no descriptor checking)
     * @param name name to check for
     */
    public boolean isMethodMapped(String name, String desc) {
        return isMethodMapped(name, desc, tree());
    }

    public boolean isMethodMapped(String name, String desc, Collection<JClass> classes) {
        if(methods().stream().anyMatch(e -> e.mappedName().equals(name)))
            return true;

        for(var member : classes) {
            if(member.methods().stream().anyMatch(e -> e.mappedName().equals(name)))
                return true;
        }

        return methods().stream().filter(e -> e.mappedName().equals(name)).filter(e -> e.desc().equals(desc)).findFirst().isPresent();
    }

    public boolean isFieldMapped(String name, String desc) {
        return isFieldMapped(name, desc, tree());
    }

    /**
     * Scans the class tree to see if theres a field with the specified name (no descriptor checking)
     * @param name name to check for
     */
    public boolean isFieldMapped(String name, String desc, Collection<JClass> classes) {
        if(fields().stream().anyMatch(e -> e.mappedName().equals(name)))
            return true;

        for(var member : classes) {
            if(member.fields().stream().anyMatch(e -> e.mappedName().equals(name)))
                return true;
        }

        return false;
    }

    public boolean isMixin() {
        return isAnnotatedBy("org/spongepowered/asm/mixin/Mixin");
    }

    public void setFirstInitializerClass(JClass clazz) {
        this.initializerClass = clazz;
    }

    public JClass getFirstInitializerClass() {
        return initializerClass;
    }

    public boolean hasFirstInitializerClass() {
        return initializerClass != null;
    }

    public List<JClass> initializes() {
        return initializes;
    }

    public boolean isInitOrderForeign() {
        return initOrderForeign;
    }

    public void setInitOrderForeign(boolean initOrderForeign) {
        this.initOrderForeign = initOrderForeign;
    }

    @Override
    public boolean hasSalt() {
        return salt != null;
    }

    @Override
    public ClassSalt salt() {
        return salt;
    }

    public void setSalt(ClassSalt salt) {
        this.salt = salt;
    }

    public int version() {
        return core.version;
    }

    public String originalName() {
        return originalName;
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

    public boolean isAnnotatedBy(String annotation) {
        return MemberUtils.hasAnnotation(core.visibleAnnotations, annotation) ||
                MemberUtils.hasAnnotation(core.invisibleAnnotations, annotation);
    }

    @Override
    public void removeAnnotation(String annotation) {
        if(core.visibleAnnotations != null)
            core.visibleAnnotations.removeIf(e -> e.desc.equals(annotation));
        if(core.invisibleAnnotations != null)
            core.invisibleAnnotations.removeIf(e -> e.desc.equals(annotation));
    }

    public void setLibrary() {
        this.library = true;
    }

    public boolean isLibrary() {
        return library;
    }

    public boolean isEnum() {
        return core.superName != null && core.superName.equals("java/lang/Enum");
    }

    @Override
    public boolean isRecord() {
        return core.superName != null && core.superName.equals("java/lang/Record");
    }

    public PropertyContainer properties() {
        return properties;
    }

    public boolean isAssignableFrom(JClass clazz) {
        if(this == clazz)
            return true;
        return clazz.parents.contains(this);
    }

    public boolean isLibMethod(JMethod method) {
        if(method.owner().isLibrary())
            return true;

        for(var member : tree()) {
            if(!member.isLibrary())
                continue;

            var found = member.methods.stream()
                    .filter(e -> e.name().equals(method.name()))
                    .anyMatch(e -> e.desc().equals(method.desc()));

            if(found)
                return true;
        }

        return false;
    }

    public boolean isLibField(JField field) {
        if(field.owner().isLibrary())
            return true;

        for(var parent : tree()) {
            if(!parent.isLibrary())
                continue;

            var found = parent.fields.stream()
                    .filter(e -> e.name().equals(field.name()))
                    .filter(e -> e.desc().equals(field.desc())).findAny();

            if(found.isPresent())
                return true;
        }

        return false;
    }

    public JMethod findOrCreateClinit() {
        var opt = findMethod("<clinit>", "()V");
        if(opt.isPresent())
            return opt.get();

        var node = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        node.instructions.add(new InsnNode(Opcodes.RETURN));
        return add(node);
    }

    public boolean hasMethodInTree(Context context, String name, String desc) {
        return findMethodFull(context, name, desc) != null;
    }

    public boolean hasMethodInTree(Context context, JMethod method) {
        return hasMethodInTree(context, method.name(), method.desc());
    }

    public boolean hasFieldInTree(Context context, String name, String desc) {
        return findFieldFull(context, name, desc) != null;
    }

    public boolean hasFieldInTree(Context context, JField field) {
        return hasFieldInTree(context, field.name(), field.desc());
    }

    public JMethod findMethodFull(Context context, String name, String desc) {
        var method = findMethod(name, desc).orElse(null);
        if(method != null)
            return method;

        if(parents.isEmpty())
            context.hierarchy().build(this);

        for(var parent : parents) {
            method = parent.findMethod(name, desc).orElse(null);
            if(method == null) continue;

            return method;
        }

        return method;
    }

    public JField findFieldFull(Context context, String name, String desc) {
        var field = findField(name, desc).orElse(null);
        if(field != null)
            return field;

        if(parents.isEmpty())
            context.hierarchy().build(this);

        for(var parent : parents) {
            field = parent.findField(name, desc).orElse(null);
            if(field == null) continue;

            return field;
        }

        return field;
    }

    public Optional<JMethod> findMethod(String name, String desc) {
        return findMethod(name, desc, _ -> true);
    }

    public Optional<JField> findField(String name, String desc) {
        return findField(name, desc, _ -> true);
    }

    public Optional<JMethod> findMethod(String name, String desc, Predicate<JMethod> predicate) {
        return methods.stream()
                .filter(predicate)
                .filter(e -> e.name().equals(name))
                .filter(e -> e.desc().equals(desc))
                .findAny();
    }

    public Optional<JField> findField(String name, String desc, Predicate<JField> predicate) {
        return fields.stream()
                .filter(predicate)
                .filter(e -> e.name().equals(name))
                .filter(e -> e.desc().equals(desc))
                .findAny();
    }

    public void setCore(ClassNode core) {
        this.core = core;

        this.parents = new HashSet<>();
        this.children = new HashSet<>();
    }

    public void accept(ClassVisitor visitor, ClassNode remapped) {
        core.accept(visitor);

        // ---- refresh member cores ----
        for(int i = 0; i < methods.size(); i++) {
            methods.get(i).setCore(remapped.methods.get(i));
        }

        for(int i = 0; i < fields.size(); i++) {
            fields.get(i).setCore(remapped.fields.get(i));
        }
    }

    public void remove(JMethod method) {
        methods.remove(method);
        core.methods.remove(method.core());
    }

    public void remove(JField field) {
        fields.remove(field);
        core.fields.remove(field.core());
    }

    public JMethod createMethod(int access, String name, String desc) {
        return add(new MethodNode(access, name, desc, null, null));
    }

    public JField createField(int access, String name, String desc, Object value) {
        return add(new FieldNode(access, name, desc, null, value));
    }

    public JField createField(int access, String name, String desc) {
        return createField(access, name, desc, null);
    }

    public JMethod add(MethodNode method) {
        return add(new JMethod(method));
    }

    public JField add(FieldNode field) {
        return add(new JField(field));
    }

    public JMethod add(JMethod method) {
        methods.add(method);
        if(!core.methods.contains(method.core()))
            core.methods.add(method.core());

        if(library)
            method.setLibrary();
        method.setOwner(this);
        return method;
    }

    public JField add(JField field) {
        fields.add(field);
        if(!core.fields.contains(field.core()))
            core.fields.add(field.core());

        if(library)
            field.setLibrary();
        field.setOwner(this);
        return field;
    }

    @Override
    public Set<JClass> parents() {
        return parents;
    }

    @Override
    public Set<JClass> children() {
        return children;
    }

    public List<JMethod> methods() {
        return methods;
    }

    public List<JField> fields() {
        return fields;
    }

    public Type type() {
        return Type.getObjectType(name());
    }

    public ClassNode core() {
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

    public String signature() {
        return core.signature;
    }

    public String sourceFile() {
        return core.sourceFile;
    }

    public String sourceDebug() {
        return core.sourceDebug;
    }

    public void setSourceFile(String sourceFile) {
        core.sourceFile = sourceFile;
    }

    public void setSourceDebug(String sourceDebug) {
        core.sourceDebug = sourceDebug;
    }

    public String superName() {
        return core.superName;
    }

    public List<String> interfaces() {
        if(core.interfaces == null)
            core.interfaces = new ArrayList<>();

        return core.interfaces;
    }

    @Override
    public String toString() {
        return name();
    }
}
