package dev.lvstrng.aidsfuscator.classgen.impl;

import dev.lvstrng.aidsfuscator.classgen.IClassGen;
import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.property.Property;
import dev.lvstrng.aidsfuscator.tree.impl.JClass;
import dev.lvstrng.aidsfuscator.tree.impl.JField;
import dev.lvstrng.aidsfuscator.tree.impl.JMethod;
import dev.lvstrng.aidsfuscator.utils.InsnBuilder;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LabelNode;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * A class generator that generates a salt dispatcher, which, with pairs, strictly specifies `InitializerClass -> ClassGettingInitialized`.
 * @author lvstrng
 */
public class StrictSaltDispatcherClassGenerator implements IClassGen, Opcodes {
    private static final int PRIVATE_STATIC = ACC_PRIVATE | ACC_STATIC;
    private static final int PUBLIC_STATIC = ACC_PUBLIC | ACC_STATIC;
    private static final String MAP_TYPE = "Ljava/util/Map;";
    private static final SecureRandom random = new SecureRandom();

    private JField indyDispatcherClassField;
    private JClass indyClass;
    private JClass clazz;

    private final Map<JClass, Integer> fakeValues = new HashMap<>();

    @Override
    public JClass create(Context context) {
        clazz = context.createClass("java/lang/Object", ACC_PUBLIC);
        var classValues         = clazz.createField(PRIVATE_STATIC, context.dictionary().newFieldName(clazz, MAP_TYPE), MAP_TYPE);
        var initializedValues   = clazz.createField(PRIVATE_STATIC, context.dictionary().newFieldName(clazz, MAP_TYPE), MAP_TYPE);
        indyDispatcherClassField = clazz.createField(PUBLIC_STATIC, context.dictionary().newFieldName(clazz, "Ljava/lang/Class;"), "Ljava/lang/Class;");

        var hasher = generateHasher(context);
        var initializerMethod = addClassValueInit(context, clazz, classValues);
        var retriever = createRetrieverMethod(context, classValues, initializedValues, hasher);
        var retrieverOrder = createOrderedRetrieverMethod(context, classValues, initializedValues, hasher);

        initialize(context, retriever, retrieverOrder);
        initializeFields(classValues, initializedValues, initializerMethod);
        return clazz;
    }

    public void initialize(Context context, JMethod retriever, JMethod retrieverOrder) {
        for(var c : context.classes()) {
            if(!c.hasSalt())
                continue;

            var list = new InsnBuilder();
            if(c.hasFirstInitializerClass()) {
                //MethodHandles.lookup().lookupClass()
                list.label(new LabelNode())
                        .method(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;").addProps(context, Property.IGNORE_REF_OBFUSCATION)
                        .method(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "lookupClass", "()Ljava/lang/Class;").addProps(context, Property.IGNORE_REF_OBFUSCATION)
                        /*._const(clazz.type())*/
                        ._int(fakeValues.get(c)).addProps(context, Property.IGNORE_INTEGER)
                        .method(INVOKESTATIC, clazz.name(), retrieverOrder.name(), retrieverOrder.desc()).addProps(context, Property.IGNORE_REF_OBFUSCATION)
                        .add(c.salt().store());
            } else {
                list.label(new LabelNode())
                        .method(INVOKESTATIC, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;").addProps(context, Property.IGNORE_REF_OBFUSCATION)
                        .method(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "lookupClass", "()Ljava/lang/Class;").addProps(context, Property.IGNORE_REF_OBFUSCATION)
                        /*._const(clazz.type())*/
                        ._int(fakeValues.get(c)).addProps(context, Property.IGNORE_INTEGER)
                        .method(INVOKESTATIC, clazz.name(), retriever.name(), retriever.desc()).addProps(context, Property.IGNORE_REF_OBFUSCATION)
                        .add(c.salt().store());
            }

            var clinit = c.findOrCreateClinit();
            clinit.setSafeInsn(list.result().getLast());
            clinit.insns().insert(list.result());
        }
    }

    private void initializeFields(JField classValues, JField initializedValues, JMethod initializerMethod) {
        var clinit = clazz.findOrCreateClinit();

        var builder = new InsnBuilder();
        builder.label(new LabelNode())
                .type(NEW, "java/util/HashMap")
                .dup()
                .method(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V")
                .field(PUTSTATIC, clazz.name(), classValues.name(), classValues.desc())

                .label(new LabelNode())
                .type(NEW, "java/util/HashMap")
                .dup()
                .method(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V")
                .field(PUTSTATIC, clazz.name(), initializedValues.name(), initializedValues.desc())

                .label(new LabelNode())
                .method(INVOKESTATIC, clazz.name(), initializerMethod.name(), initializerMethod.desc());

        builder.add(new LabelNode())._return();
        clinit.insns().insert(builder.result());
    }

    private JMethod createOrderedRetrieverMethod(Context context, JField classValues, JField initializedValues, JMethod hasher) {
        var desc = "(Ljava/lang/Object;I)I";
        var method = clazz.createMethod(PUBLIC_STATIC, context.dictionary().newMethodName(clazz, desc), desc);

        // ---- LOCALS ----
        var objLocal = method.allocVar(Type.INT_TYPE);
        var nLocal = method.allocVar(Type.INT_TYPE);

        var traces = method.allocVar();
        var traceClassHash = method.allocVar();
        var hash = method.allocVar(Type.INT_TYPE);

        // ---- CODE ----
        var regular = new LabelNode();
        var last = new LabelNode();
        var body = new InsnBuilder(method.insns());
        body.add(new LabelNode())
                .type(NEW, "java/lang/Throwable")
                .dup()
                .method(INVOKESPECIAL, "java/lang/Throwable", "<init>", "()V")
                .method(INVOKEVIRTUAL, "java/lang/Throwable", "getStackTrace", "()[Ljava/lang/StackTraceElement;")
                ._var(ASTORE, traces)

                .label(new LabelNode())
                ._var(ALOAD, traces)
                ._int(2)
                .aaload()
                .method(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;")
                ._const("java")
                .method(INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z")
                .jump(IFEQ, regular)

                ._var(ALOAD, traces)
                ._int(7)
                .aaload()
                .method(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;")
                .method(INVOKESTATIC, clazz.name(), hasher.name(), hasher.desc())
                ._var(LSTORE, traceClassHash)
                ._goto(last)

                // REGULAR
                .label(regular)
                ._var(ALOAD, traces)
                ._int(2)
                .aaload()
                .method(INVOKEVIRTUAL, "java/lang/StackTraceElement", "getClassName", "()Ljava/lang/String;")
                .method(INVOKESTATIC, clazz.name(), hasher.name(), hasher.desc())
                ._var(LSTORE, traceClassHash)

                .label(last)
                .field(GETSTATIC, clazz.name(), classValues.name(), classValues.desc())
                ._var(ALOAD, objLocal)
                .type(CHECKCAST, "java/lang/Class")
                .method(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;")
                .method(INVOKESTATIC, clazz.name(), hasher.name(), hasher.desc())
                .method(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;")
                .method(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;")
                .type(CHECKCAST, "java/lang/Integer")
                .method(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I") // map.get(((Class) obj).getName().hashCode()).intValue()

                ._var(ILOAD, nLocal)
                .ixor() // ^ n

                .field(GETSTATIC, clazz.name(), initializedValues.name(), initializedValues.desc())
                ._var(LLOAD, traceClassHash)
                .method(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;")
                .method(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;")
                .type(CHECKCAST, "java/lang/Integer")
                .method(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I") // map.get(traceHash).intValue()
                .ixor()
                ._var(ISTORE, hash)

                .label(new LabelNode())
                .field(GETSTATIC, clazz.name(), initializedValues.name(), initializedValues.desc())
                ._var(ALOAD, objLocal)
                .type(CHECKCAST, "java/lang/Class")
                .method(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;")
                .method(INVOKESTATIC, clazz.name(), hasher.name(), hasher.desc())
                .method(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;")
                ._var(ILOAD, hash)
                .method(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")
                .method(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                .pop()

                .label(new LabelNode())
                ._var(ILOAD, hash)
                ._ireturn()
        ;

        return method;
    }

    private JMethod createRetrieverMethod(Context context, JField classValues, JField initializedValues, JMethod hasher) {
        var desc = "(Ljava/lang/Object;I)I";
        var method = clazz.createMethod(PUBLIC_STATIC, context.dictionary().newMethodName(clazz, desc), desc);

        // ---- LOCALS ----
        var objLocal = method.allocVar(Type.INT_TYPE);
        var nLocal = method.allocVar(Type.INT_TYPE);

        // ---- CODE ----
        var body = new InsnBuilder(method.insns());
        body.add(new LabelNode())
                .field(GETSTATIC, clazz.name(), classValues.name(), classValues.desc())
                ._var(ALOAD, objLocal)
                .type(CHECKCAST, "java/lang/Class")
                .method(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;")
                .method(INVOKESTATIC, clazz.name(), hasher.name(), hasher.desc())
                .method(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;")
                .method(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;")
                .type(CHECKCAST, "java/lang/Integer")
                .method(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I")
                ._var(ILOAD, nLocal)
                .ixor()
                .dup()
                .method(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")
                ._var(ALOAD, objLocal)
                .type(CHECKCAST, "java/lang/Class")
                .method(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;")
                .method(INVOKESTATIC, clazz.name(), hasher.name(), hasher.desc())
                .method(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;")
                .swap()
                .field(GETSTATIC, clazz.name(), initializedValues.name(), initializedValues.desc())
                .dup_x2()
                .pop()
                .method(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                .pop()
                ._ireturn();

        return method;
    }

    private JMethod addClassValueInit(Context context, JClass clazz, JField classValues) {
        var method = clazz.createMethod(PRIVATE_STATIC, context.dictionary().newMethodName(clazz, "()V"), "()V");
        var body = new InsnBuilder(method.insns());
        body.add(new LabelNode())
                .field(GETSTATIC, clazz.name(), classValues.name(), classValues.desc());

        for(var c : context.classes()) {
            if(!c.hasSalt())
                continue;

            var fakeNum = random.nextInt();
            int num;
            if(c.hasFirstInitializerClass() && c.getFirstInitializerClass().hasSalt()) {
                var initializerClass = c.getFirstInitializerClass();

                num = c.salt().value() ^ fakeNum ^ initializerClass.salt().value();
            } else {
                num = c.salt().value() ^ fakeNum;
            }

            var str = c.name().replace('/', '.');
            body
                    .dup()
                    ._long(hash(str)).method(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;")
                    ._int(num).method(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;")
                    .method(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                    .pop();

            fakeValues.put(c, fakeNum);
        }

        body.pop()._return();
        return method;
    }

    public void setIndyField(JClass indyClass) {
        this.indyClass = indyClass;
        if(clazz == null)
            return;

        var clinit = clazz.findOrCreateClinit();
        var builder = new InsnBuilder();
        if(indyClass != null) {
            builder.label(new LabelNode())
                    ._const(indyClass.type())
                    .field(PUTSTATIC, clazz.name(), indyDispatcherClassField.name(), indyDispatcherClassField.desc());
        }

        clinit.insns().insertBefore(clinit.insns().getLast().getPrevious(), builder.result());
    }

    private JMethod generateHasher(Context context) {
        var desc = "(Ljava/lang/String;)J";
        var method = clazz.createMethod(PRIVATE_STATIC, context.dictionary().newMethodName(clazz, desc), desc);
        // ---- LOCALS ----
        var strVar = method.allocVar();
        var hashVar = method.allocVar(Type.LONG_TYPE);
        var iVar = method.allocVar(Type.INT_TYPE);

        // ---- CODE ----
        var loopLabel = new LabelNode();
        var endLabel = new LabelNode();

        var body = new InsnBuilder(method.insns());
        body.label(new LabelNode())
                ._long(0xcbf29ce484222325L)
                ._var(LSTORE, hashVar)

                .label(new LabelNode())
                ._int(0)
                ._var(ISTORE, iVar)

                .label(loopLabel)
                ._var(ILOAD, iVar)
                ._var(ALOAD, strVar)
                .method(INVOKEVIRTUAL, "java/lang/String", "length", "()I")
                .jump(IF_ICMPGE, endLabel)

                .label(new LabelNode())
                ._var(LLOAD, hashVar)
                ._var(ALOAD, strVar)
                ._var(ILOAD, iVar)
                .method(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C")
                .i2l()
                .lxor()
                ._var(LSTORE, hashVar)

                .label(new LabelNode())
                ._var(LLOAD, hashVar)
                ._const(0x100000001b3L)
                .lmul()
                ._var(LSTORE, hashVar)

                .label(new LabelNode())
                .iinc(iVar, 1)
                ._goto(loopLabel)

                .label(endLabel)
                ._var(LLOAD, hashVar)
                ._lreturn();

        return method;
    }

    // hasher so theres no hash collisions for names like "Aa" and "BB"
    // dubs to chatgpt
    public static long hash(String s) {
        var hash = 0xcbf29ce484222325L;

        for(int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= 0x100000001b3L;
        }

        return hash;
    }
}
