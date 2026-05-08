package dev.lvstrng.aidsfuscator.classgen.impl;

import dev.lvstrng.aidsfuscator.classgen.IClassGen;
import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.property.Property;
import dev.lvstrng.aidsfuscator.tree.JClass;
import dev.lvstrng.aidsfuscator.tree.JField;
import dev.lvstrng.aidsfuscator.tree.JMethod;
import dev.lvstrng.aidsfuscator.tree.JResource;
import dev.lvstrng.aidsfuscator.utils.ASMUtils;
import dev.lvstrng.aidsfuscator.utils.InsnBuilder;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.zip.Deflater;

import static org.objectweb.asm.Opcodes.*;

public class SimpleClassSaltClassGenerator implements IClassGen {
    public JMethod retrieverMethod;
    private String resourcePath;

    @Override
    public JClass create(Context context) {
        var clazz = context.createClass("java/lang/Object", ACC_PUBLIC | ACC_SUPER);
        var fieldName = context.dictionary().newFieldName(clazz, "Ljava/util/Map;");
        var field = clazz.createField(ACC_STATIC, fieldName, "Ljava/util/Map;");

        resourcePath = context.dictionary().newResourceName();

        generateRetrieverMethod(context, field, clazz);
        generateInitializersAndClinit(context, field, clazz);

        return clazz;
    }

    private void generateInitializersAndClinit(Context context, JField mapField, JClass clazz) {
        var saltyClasses = context.classes().stream().filter(JClass::hasSalt).toList();
        var rng = new Random();

        // Build resource bytes + patch each class clinit in one pass
        var entries = new ArrayList<Map.Entry<byte[], Integer>>();
        for (var saltClass : saltyClasses) {
            int key = rng.nextInt();
            entries.add(Map.entry(
                    saltClass.name().getBytes(StandardCharsets.UTF_8),
                    key ^ saltClass.salt().value()
            ));

            var classClinit = saltClass.findOrCreateClinit();
            var list = new InsnList();
            list.add(new LdcInsnNode(saltClass.type()));
            list.add(context.properties().add(ASMUtils.pushInt(key), Property.IGNORE_INTEGER));
            list.add(new MethodInsnNode(INVOKESTATIC,
                    clazz.name(), retrieverMethod.name(), retrieverMethod.desc()));
            list.add(saltClass.salt().store());
            classClinit.setSafeInsn(list.getLast());
            classClinit.insns().insert(list);
        }

        // Write resource
        Collections.shuffle(entries, rng);
        int size = 4 + entries.stream().mapToInt(e -> 4 + e.getKey().length + 4).sum();
        var buf = ByteBuffer.allocate(size);
        buf.putInt(entries.size());
        for (var e : entries) {
            buf.putInt(e.getKey().length);
            buf.put(e.getKey());
            buf.putInt(e.getValue());
        }
        context.addArtificial(new JResource(resourcePath, compress(buf.array()), context));

        var loaderName = context.dictionary().newMethodName(clazz, "()V");
        generateResourceLoader(context, mapField, clazz, loaderName);

        var clinit = clazz.findOrCreateClinit();
        var clinitInsns = new InsnBuilder()
                .type(NEW, "java/util/HashMap")
                .dup()
                .method(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V")
                .field(PUTSTATIC, clazz.name(), mapField.name(), mapField.desc())
                .method(INVOKESTATIC, clazz.name(), loaderName, "()V")
                ._return();
        clinit.insns().insert(clinitInsns.result());
    }

    private void generateResourceLoader(Context context, JField mapField, JClass clazz, String loaderName) {
        var method = clazz.createMethod(ACC_STATIC | ACC_PRIVATE, loaderName, "()V");
        var insns = method.insns();

        // ByteBuffer buf = ByteBuffer.wrap(new InflaterInputStream(new ByteArrayInputStream(ThisClass.class.getClassLoader().getResourceAsStream(path).readAllBytes())).readAllBytes())
        insns.add(new TypeInsnNode(NEW, "java/util/zip/InflaterInputStream"));
        insns.add(new InsnNode(DUP));
        insns.add(new TypeInsnNode(NEW, "java/io/ByteArrayInputStream"));
        insns.add(new InsnNode(DUP));
        insns.add(new LdcInsnNode(Type.getObjectType(clazz.name())));
        insns.add(new MethodInsnNode(INVOKEVIRTUAL,
                "java/lang/Class",
                "getClassLoader",
                "()Ljava/lang/ClassLoader;"));

        insns.add(new LdcInsnNode(resourcePath));
        insns.add(new MethodInsnNode(INVOKEVIRTUAL,
                "java/lang/ClassLoader",
                "getResourceAsStream",
                "(Ljava/lang/String;)Ljava/io/InputStream;"));
        insns.add(new MethodInsnNode(INVOKEVIRTUAL,
                "java/io/InputStream",
                "readAllBytes",
                "()[B"));
        insns.add(new MethodInsnNode(INVOKESPECIAL,
                "java/io/ByteArrayInputStream",
                "<init>",
                "([B)V"));
        insns.add(new MethodInsnNode(INVOKESPECIAL,
                "java/util/zip/InflaterInputStream",
                "<init>",
                "(Ljava/io/InputStream;)V"));
        insns.add(new MethodInsnNode(INVOKEVIRTUAL,
                "java/io/InputStream",
                "readAllBytes",
                "()[B"));
        insns.add(new MethodInsnNode(INVOKESTATIC,
                "java/nio/ByteBuffer",
                "wrap",
                "([B)Ljava/nio/ByteBuffer;"));

        insns.add(new VarInsnNode(ASTORE, 0));

        // int count = buf.getInt();
        insns.add(new VarInsnNode(ALOAD, 0));
        insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/nio/ByteBuffer", "getInt", "()I"));
        insns.add(new VarInsnNode(ISTORE, 1)); // local 1 = count

        // for (int i = 0; i < count; i++)
        var loopStart = new LabelNode();
        var loopEnd = new LabelNode();
        insns.add(new InsnNode(ICONST_0));
        insns.add(new VarInsnNode(ISTORE, 2)); // local 2 = i
        insns.add(loopStart);
        insns.add(new VarInsnNode(ILOAD, 2));
        insns.add(new VarInsnNode(ILOAD, 1));
        insns.add(new JumpInsnNode(IF_ICMPGE, loopEnd));

        // byte[] nameBytes = new byte[buf.getInt()];
        insns.add(new VarInsnNode(ALOAD, 0));
        insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/nio/ByteBuffer", "getInt", "()I"));
        insns.add(new IntInsnNode(NEWARRAY, T_BYTE));
        insns.add(new VarInsnNode(ASTORE, 3)); // local 3 = nameBytes

        // buf.get(nameBytes)
        insns.add(new VarInsnNode(ALOAD, 0));
        insns.add(new VarInsnNode(ALOAD, 3));
        insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/nio/ByteBuffer",
                "get", "([B)Ljava/nio/ByteBuffer;"));
        insns.add(new InsnNode(POP));

        // map.put(new String(nameBytes, UTF_8), Integer.valueOf(buf.getInt()))
        insns.add(new FieldInsnNode(GETSTATIC, clazz.name(), mapField.name(), mapField.desc()));
        insns.add(new TypeInsnNode(NEW, "java/lang/String"));
        insns.add(new InsnNode(DUP));
        insns.add(new VarInsnNode(ALOAD, 3));
        insns.add(new FieldInsnNode(GETSTATIC, "java/nio/charset/StandardCharsets",
                "UTF_8", "Ljava/nio/charset/Charset;"));
        insns.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/String",
                "<init>", "([BLjava/nio/charset/Charset;)V"));
        insns.add(new VarInsnNode(ALOAD, 0));
        insns.add(new MethodInsnNode(INVOKEVIRTUAL, "java/nio/ByteBuffer", "getInt", "()I"));
        insns.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer",
                "valueOf", "(I)Ljava/lang/Integer;"));
        insns.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/Map",
                "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true));
        insns.add(new InsnNode(POP));

        insns.add(new IincInsnNode(2, 1));
        insns.add(new JumpInsnNode(GOTO, loopStart));
        insns.add(loopEnd);
        insns.add(new InsnNode(RETURN));
    }

    private void generateRetrieverMethod(Context context, JField mapField, JClass clazz) {
        var desc = "(Ljava/lang/Object;I)I";
        var name = context.dictionary().newMethodName(clazz, desc);
        retrieverMethod = clazz.createMethod(ACC_PUBLIC | ACC_STATIC, name, desc);

        // ---- LOCALS ----
        var objVar = retrieverMethod.allocVar();
        var keyVar = retrieverMethod.allocVar(Type.INT_TYPE);

        // ---- CODE ----
        var builder = new InsnBuilder(retrieverMethod.insns())
                .field(GETSTATIC, clazz.name(), mapField.name(), mapField.desc())
                ._var(ALOAD, objVar)
                .type(CHECKCAST, "java/lang/Class")
                .method(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;")
                .method(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;")
                .type(CHECKCAST, "java/lang/Integer")
                .method(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I")
                ._var(ILOAD, keyVar)
                .ixor()
                ._ireturn();
    }

    private byte[] compress(byte[] input) {
        var deflater = new Deflater(java.util.zip.Deflater.BEST_COMPRESSION);

        deflater.setInput(input);
        deflater.finish();

        var out = new java.io.ByteArrayOutputStream();
        var buffer = new byte[4096];

        while (!deflater.finished()) {
            int written = deflater.deflate(buffer);
            out.write(buffer, 0, written);
        }

        deflater.end();
        return out.toByteArray();
    }
}