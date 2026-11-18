package dev.lvstrng.aidsfuscator.utils;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.property.Property;
import dev.lvstrng.aidsfuscator.tree.impl.JMethod;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;

import java.util.Random;

import static org.objectweb.asm.Opcodes.*;

public class InsnBuilder {
    private final Random random = new Random();
    private final InsnList list;

    public InsnBuilder(InsnList list) {
        this.list = list;
    }

    private InsnBuilder(JMethod method) {
        this(method.insns());
    }

    public InsnBuilder() {
        this(new InsnList());
    }

    public InsnList result() {
        return list;
    }

    public InsnBuilder insertRandomly(InsnList toAdd) {
        var randomInsn = list.get(random.nextInt(list.size()));
        list.insert(randomInsn, toAdd);
        return this;
    }

    public InsnBuilder insertBeforeRandomly(InsnList toAdd) {
        var randomInsn = list.get(random.nextInt(list.size()));
        list.insertBefore(randomInsn, toAdd);
        return this;
    }

    public InsnBuilder insertRandomly(InsnBuilder builder) {
        return insertRandomly(builder.result());
    }

    public InsnBuilder insertBeforeRandomly(InsnBuilder builder) {
        return insertBeforeRandomly(builder.result());
    }

    public InsnBuilder add(AbstractInsnNode node) {
        list.add(node);
        return this;
    }

    public InsnBuilder add(InsnList list) {
        this.list.add(list);
        return this;
    }

    public InsnBuilder add(InsnBuilder builder) {
        return add(builder.result());
    }

    public InsnBuilder _int(int n) {
        list.add(ASMUtils.pushInt(n));
        return this;
    }

    public InsnBuilder _long(long n) {
        list.add(ASMUtils.pushLong(n));
        return this;
    }

    public InsnBuilder _double(double n) {
        list.add(ASMUtils.pushDouble(n));
        return this;
    }

    public InsnBuilder _float(float n) {
        list.add(ASMUtils.pushFloat(n));
        return this;
    }

    public InsnBuilder label(LabelNode label) {
        list.add(label);
        return this;
    }

    public InsnBuilder label() {
        list.add(new LabelNode());
        return this;
    }

    public InsnBuilder _goto(LabelNode label) {
        list.add(new JumpInsnNode(GOTO, label));
        return this;
    }

    public InsnBuilder jump(int op, LabelNode label) {
        list.add(new JumpInsnNode(op, label));
        return this;
    }

    private static final int STRING_CHUNK_SIZE = 16384;

    /**
     * Append string load.
     * @author a114
     */
    private InsnBuilder appendStringLoad(String str) {
        if (str.length() <= STRING_CHUNK_SIZE) {
            list.add(new LdcInsnNode(str));
            return this;
        }
        list.add(new TypeInsnNode(NEW, "java/lang/StringBuilder"));
        list.add(new InsnNode(DUP));
        list.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V"));

        for (int i = 0; i < str.length(); i += STRING_CHUNK_SIZE) {
            String chunk = str.substring(i, Math.min(i + STRING_CHUNK_SIZE, str.length()));
            list.add(new LdcInsnNode(chunk));
            list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
        }
        list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;"));
        return this;
    }

    public InsnBuilder _const(Object value) {
        if (value instanceof String str) {
            return appendStringLoad(str);
        }
        if (value instanceof Integer $int) {
            return _int($int);
        }
        if (value instanceof Long $long) {
            return _long($long);
        }
        if (value instanceof Float $float) {
            return _float($float);
        }
        if (value instanceof Double $double) {
            return _double($double);
        }
        else {
            list.add(new LdcInsnNode(value));
            return this;
        }
    }

    public InsnBuilder method(int op, String owner, String name, String desc) {
        list.add(new MethodInsnNode(op, owner, name, desc));
        return this;
    }

    public InsnBuilder method(int op, String owner, String name, String desc, boolean itf) {
        list.add(new MethodInsnNode(op, owner, name, desc, itf));
        return this;
    }

    public InsnBuilder field(int op, String owner, String name, String desc) {
        list.add(new FieldInsnNode(op, owner, name, desc));
        return this;
    }

    public InsnBuilder iinc(int idx, int incr) {
        list.add(new IincInsnNode(idx, incr));
        return this;
    }

    public InsnBuilder anewarray(String type) {
        list.add(new TypeInsnNode(ANEWARRAY, type));
        return this;
    }

    public InsnBuilder newarray(int type) {
        list.add(new IntInsnNode(NEWARRAY, type));
        return this;
    }

    public InsnBuilder _var(int op, int idx) {
        list.add(new VarInsnNode(op, idx));
        return this;
    }

    public InsnBuilder indy(String name, String desc, Handle handle, Object... args) {
        list.add(new InvokeDynamicInsnNode(
                name, desc,
                handle, args
        ));
        return this;
    }

    public InsnBuilder ixor() {
        list.add(new InsnNode(IXOR));
        return this;
    }

    public InsnBuilder lxor() {
        list.add(new InsnNode(LXOR));
        return this;
    }

    public InsnBuilder iand() {
        list.add(new InsnNode(IAND));
        return this;
    }

    public InsnBuilder land() {
        list.add(new InsnNode(LAND));
        return this;
    }

    public InsnBuilder ior() {
        list.add(new InsnNode(IOR));
        return this;
    }

    public InsnBuilder lor() {
        list.add(new InsnNode(LOR));
        return this;
    }

    public InsnBuilder iadd() {
        list.add(new InsnNode(IADD));
        return this;
    }

    public InsnBuilder ladd() {
        list.add(new InsnNode(LADD));
        return this;
    }

    public InsnBuilder isub() {
        list.add(new InsnNode(ISUB));
        return this;
    }

    public InsnBuilder lsub() {
        list.add(new InsnNode(LSUB));
        return this;
    }

    public InsnBuilder ishr() {
        list.add(new InsnNode(ISHR));
        return this;
    }

    public InsnBuilder lshr() {
        list.add(new InsnNode(LSHR));
        return this;
    }

    public InsnBuilder ishl() {
        list.add(new InsnNode(ISHL));
        return this;
    }

    public InsnBuilder lshl() {
        list.add(new InsnNode(LSHL));
        return this;
    }

    public InsnBuilder iushr() {
        list.add(new InsnNode(IUSHR));
        return this;
    }

    public InsnBuilder lushr() {
        list.add(new InsnNode(LUSHR));
        return this;
    }

    public InsnBuilder dup() {
        list.add(new InsnNode(DUP));
        return this;
    }

    public InsnBuilder dup_x1() {
        list.add(new InsnNode(DUP_X1));
        return this;
    }

    public InsnBuilder dup_x2() {
        list.add(new InsnNode(DUP_X2));
        return this;
    }

    public InsnBuilder dup2() {
        list.add(new InsnNode(DUP2));
        return this;
    }

    public InsnBuilder dup2_x1() {
        list.add(new InsnNode(DUP2_X1));
        return this;
    }
    public InsnBuilder dup2_x2() {
        list.add(new InsnNode(DUP2_X2));
        return this;
    }

    public InsnBuilder swap() {
        list.add(new InsnNode(SWAP));
        return this;
    }

    public InsnBuilder pop() {
        list.add(new InsnNode(POP));
        return this;
    }

    public InsnBuilder pop2() {
        list.add(new InsnNode(POP2));
        return this;
    }

    public InsnBuilder imul() {
        list.add(new InsnNode(IMUL));
        return this;
    }

    public InsnBuilder idiv() {
        list.add(new InsnNode(IDIV));
        return this;
    }

    public InsnBuilder lmul() {
        list.add(new InsnNode(LMUL));
        return this;
    }

    public InsnBuilder ldiv() {
        list.add(new InsnNode(LDIV));
        return this;
    }

    public InsnBuilder ineg() {
        list.add(new InsnNode(INEG));
        return this;
    }

    public InsnBuilder lneg() {
        list.add(new InsnNode(LNEG));
        return this;
    }

    public InsnBuilder fneg() {
        list.add(new InsnNode(FNEG));
        return this;
    }

    public InsnBuilder dneg() {
        list.add(new InsnNode(DNEG));
        return this;
    }

    public InsnBuilder irem() {
        list.add(new InsnNode(IREM));
        return this;
    }

    public InsnBuilder lrem() {
        list.add(new InsnNode(LREM));
        return this;
    }

    public InsnBuilder frem() {
        list.add(new InsnNode(FREM));
        return this;
    }

    public InsnBuilder drem() {
        list.add(new InsnNode(DREM));
        return this;
    }

    public InsnBuilder dcmp(boolean l) {
        list.add(new InsnNode(l ? DCMPL : DCMPG));
        return this;
    }

    public InsnBuilder fcmp(boolean l) {
        list.add(new InsnNode(l ? FCMPL : FCMPG));
        return this;
    }

    public InsnBuilder type(int op, String s) {
        list.add(new TypeInsnNode(op, s));
        return this;
    }

    public InsnBuilder iaload() {
        list.add(new InsnNode(IALOAD));
        return this;
    }

    public InsnBuilder caload() {
        list.add(new InsnNode(CALOAD));
        return this;
    }

    public InsnBuilder baload() {
        list.add(new InsnNode(BALOAD));
        return this;
    }

    public InsnBuilder saload() {
        list.add(new InsnNode(SALOAD));
        return this;
    }

    public InsnBuilder aaload() {
        list.add(new InsnNode(AALOAD));
        return this;
    }

    public InsnBuilder faload() {
        list.add(new InsnNode(FALOAD));
        return this;
    }

    public InsnBuilder daload() {
        list.add(new InsnNode(DALOAD));
        return this;
    }

    public InsnBuilder laload() {
        list.add(new InsnNode(LALOAD));
        return this;
    }

    public InsnBuilder iastore() {
        list.add(new InsnNode(IASTORE));
        return this;
    }

    public InsnBuilder castore() {
        list.add(new InsnNode(CASTORE));
        return this;
    }

    public InsnBuilder bastore() {
        list.add(new InsnNode(BASTORE));
        return this;
    }

    public InsnBuilder sastore() {
        list.add(new InsnNode(SASTORE));
        return this;
    }

    public InsnBuilder aastore() {
        list.add(new InsnNode(AASTORE));
        return this;
    }

    public InsnBuilder fastore() {
        list.add(new InsnNode(FASTORE));
        return this;
    }

    public InsnBuilder dastore() {
        list.add(new InsnNode(DASTORE));
        return this;
    }

    public InsnBuilder lastore() {
        list.add(new InsnNode(LASTORE));
        return this;
    }

    public InsnBuilder lcmp() {
        list.add(new InsnNode(LCMP));
        return this;
    }

    public InsnBuilder lookupswitch(LookupSwitchInsnNode sw) {
        list.add(sw);
        return this;
    }

    public InsnBuilder tableswitch(TableSwitchInsnNode sw) {
        list.add(sw);
        return this;
    }

    public InsnBuilder lookupswitch(LabelNode dflt, LabelNode[] routes, int[] keys) {
        return lookupswitch(new LookupSwitchInsnNode(dflt, keys, routes));
    }

    public InsnBuilder tableswitch(LabelNode dflt, int min, int max, LabelNode[] routes) {
        return tableswitch(new TableSwitchInsnNode(min, max, dflt, routes));
    }

    public InsnBuilder arraylength() {
        list.add(new InsnNode(ARRAYLENGTH));
        return this;
    }

    public InsnBuilder multianewarray(int dims, String type) {
        list.add(new MultiANewArrayInsnNode(type, dims));
        return this;
    }

    public InsnBuilder _return() {
        list.add(new InsnNode(RETURN));
        return this;
    }

    public InsnBuilder _areturn() {
        list.add(new InsnNode(ARETURN));
        return this;
    }

    public InsnBuilder _lreturn() {
        list.add(new InsnNode(LRETURN));
        return this;
    }

    public InsnBuilder _ireturn() {
        list.add(new InsnNode(IRETURN));
        return this;
    }

    public InsnBuilder _freturn() {
        list.add(new InsnNode(FRETURN));
        return this;
    }

    public InsnBuilder _dreturn() {
        list.add(new InsnNode(DRETURN));
        return this;
    }

    public InsnBuilder _null() {
        list.add(new InsnNode(ACONST_NULL));
        return this;
    }

    public InsnBuilder athrow() {
        list.add(new InsnNode(ATHROW));
        return this;
    }

    public InsnBuilder i2s() {
        list.add(new InsnNode(I2S));
        return this;
    }

    public InsnBuilder i2b() {
        list.add(new InsnNode(I2B));
        return this;
    }

    public InsnBuilder i2c() {
        list.add(new InsnNode(I2C));
        return this;
    }

    public InsnBuilder i2d() {
        list.add(new InsnNode(I2D));
        return this;
    }

    public InsnBuilder i2f() {
        list.add(new InsnNode(I2F));
        return this;
    }

    public InsnBuilder i2l() {
        list.add(new InsnNode(I2L));
        return this;
    }

    public InsnBuilder l2i() {
        list.add(new InsnNode(L2I));
        return this;
    }

    public InsnBuilder l2d() {
        list.add(new InsnNode(L2D));
        return this;
    }

    public InsnBuilder l2f() {
        list.add(new InsnNode(L2F));
        return this;
    }

    public InsnBuilder d2l() {
        list.add(new InsnNode(D2L));
        return this;
    }

    public InsnBuilder d2f() {
        list.add(new InsnNode(D2F));
        return this;
    }

    public InsnBuilder d2i() {
        list.add(new InsnNode(D2I));
        return this;
    }

    public InsnBuilder f2l() {
        list.add(new InsnNode(F2L));
        return this;
    }

    public InsnBuilder f2i() {
        list.add(new InsnNode(F2I));
        return this;
    }

    public InsnBuilder f2d() {
        list.add(new InsnNode(F2D));
        return this;
    }

    public InsnBuilder addProps(Context context, Property... properties) {
        context.properties().add(list.getLast(), properties);
        return this;
    }
}
