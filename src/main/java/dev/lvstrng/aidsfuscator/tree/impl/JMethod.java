package dev.lvstrng.aidsfuscator.tree.impl;

import dev.lvstrng.aidsfuscator.analysis.flow.graph.Block;
import dev.lvstrng.aidsfuscator.analysis.flow.graph.ControlFlowGraph;
import dev.lvstrng.aidsfuscator.analysis.interpreter.SimpleAnalyzer;
import dev.lvstrng.aidsfuscator.analysis.interpreter.SimpleFrame;
import dev.lvstrng.aidsfuscator.analysis.interpreter.SimpleInterpreter;
import dev.lvstrng.aidsfuscator.analysis.interpreter.SimpleValue;
import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.log.Logger;
import dev.lvstrng.aidsfuscator.property.PropertyContainer;
import dev.lvstrng.aidsfuscator.salt.ISaltable;
import dev.lvstrng.aidsfuscator.salt.impl.MethodSalt;
import dev.lvstrng.aidsfuscator.tree.IAccessFlags;
import dev.lvstrng.aidsfuscator.tree.IAnnotatable;
import dev.lvstrng.aidsfuscator.tree.IHierarchical;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;

import java.security.SecureRandom;
import java.util.*;

/**
 * A MethodNode wrapper for easier use.
 */
public class JMethod implements IAccessFlags, ISaltable<MethodSalt>, IHierarchical<JMethod>, IAnnotatable {
    private static final SecureRandom random = new SecureRandom();
    private JClass owner;
    private MethodNode core;
    private final PropertyContainer properties;
    private MethodSalt salt;
    private int seed;

    private boolean library;
    private final String originalName, originalDesc;
    private String mappedName;

    private Set<JMethod> parents, children;
    private AbstractInsnNode safeInsn;

    public JMethod(MethodNode core) {
        this.properties = new PropertyContainer();
        this.library = false;

        this.originalName = core.name;
        this.originalDesc = core.desc;
        this.seed = random.nextInt();

        this.setCore(core);
    }

    public void insertSafe(InsnList list) {
        if(safeInsn == null) {
            insns().insert(list);
        } else {
            insns().insert(safeInsn, list);
        }
    }

    public AbstractInsnNode safeInsn() {
        return safeInsn;
    }

    public AbstractInsnNode setSafeInsn(AbstractInsnNode insn) {
        this.safeInsn = insn;
        return safeInsn;
    }

    public String originalName() {
        return originalName;
    }

    public String originalDesc() {
        return originalDesc;
    }

    public int allocParameter(Type type) {
        int argSize = Arrays.stream(args()).mapToInt(Type::getSize).sum();
        int spot = isStatic() ? argSize : argSize + 1;

        if (localVariables() != null) {
            for (var lv : localVariables()) {
                if (lv.index >= spot)
                    lv.index += type.getSize();
            }
        }

        for (var insn : insns()) {
            if (insn instanceof VarInsnNode v) {
                if (v.var >= spot)
                    v.var += type.getSize();
            } else if (insn instanceof IincInsnNode v) {
                if (v.var >= spot)
                    v.var += type.getSize();
            }
        }

        if(signature() != null) {
            core.signature = signature().replace(")", type.getDescriptor() + ")");
        }
        core.desc = desc().replace(")", type.getDescriptor() + ")");
        core.maxLocals += type.getSize();
        return spot;
    }

    public Type returnType() {
        return Type.getReturnType(desc());
    }

    public Type[] args() {
        return Type.getArgumentTypes(desc());
    }

    public int allocVar() {
        return allocVar(Type.getObjectType("java/lang/Object"));
    }

    public int allocVar(Type type) {
        var slot = maxLocals();
        setMaxLocals(maxLocals() + type.getSize());
        return slot;
    }

    public int maxLocals() {
        return core.maxLocals;
    }

    public void setMaxLocals(int maxLocals) {
        core.maxLocals = maxLocals;
    }

    public int maxStack() {
        return core.maxStack;
    }

    public void makeSalt(int value, int local) {
        this.salt = new MethodSalt(value, local);
    }

    @Override
    public boolean hasSalt() {
        return salt != null;
    }

    @Override
    public MethodSalt salt() {
        return salt;
    }

    public int seed() {
        return seed;
    }

    @Override
    public boolean canSalt(Block block) {
        if(!hasSalt()) return false;
        return block.isInitialized(salt.local());
    }

    @Override
    public boolean canSalt(Frame<SimpleValue> frame) {
        if(frame == null) return false;
        if(!hasSalt()) return false;

        return !frame.getLocal(salt.local()).isUninitialized();
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

    public void setCore(MethodNode core) {
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

    public boolean isSpecial() {
        return name().startsWith("<");
    }

    @Override
    public Set<JMethod> parents() {
        return parents;
    }

    @Override
    public Set<JMethod> children() {
        return children;
    }

    @Override
    public boolean isNonHierarchical() {
        return isPrivate() || isStatic() || isSpecial();
    }

    public MethodNode core() {
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

    public List<TryCatchBlockNode> traps() {
        if(core.tryCatchBlocks == null)
            core.tryCatchBlocks = new ArrayList<>();

        return core.tryCatchBlocks;
    }

    public List<LocalVariableNode> localVariables() {
        if(core.localVariables == null)
            core.localVariables = new ArrayList<>();

        return core.localVariables;
    }

    public InsnList insns() {
        return core.instructions;
    }

    public ControlFlowGraph createFlowGraph(Context context) {
        return new ControlFlowGraph(context, this).build();
    }

    public Map<AbstractInsnNode, SimpleFrame> frames(Context context) {
        try {
            var frameArr = new SimpleAnalyzer(new SimpleInterpreter(context, this)).analyzeAndComputeMaxs(owner.name(), core);
            var frames = new HashMap<AbstractInsnNode, SimpleFrame>();

            for(int i = 0; i < insns().size(); i++) {
                var insn = insns().get(i);
                var frame = frameArr[i];
                if(frame == null)
                    continue;

                frames.put(insn, SimpleFrame.of(frame));
            }

            return frames;
        } catch (AnalyzerException e) {
            Logger.error("Error analyzing frames in (%s): %s", fullOriginalName(), e.getLocalizedMessage());
            return null;
        }
    }

    public String mappedName() {
        return mappedName;
    }

    public void setMappedName(String mappedName) {
        this.mappedName = mappedName;
    }

    public String simpleName() {
        return "%s%s".formatted(name(), desc());
    }

    public String simpleOriginalName() {
        return "%s%s".formatted(originalName, originalDesc);
    }

    public String fullOriginalName() {
        return "%s.%s".formatted(owner.originalName(), simpleOriginalName());
    }

    public String fullName() {
        return "%s.%s".formatted(owner, simpleName());
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
