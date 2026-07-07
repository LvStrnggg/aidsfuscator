package dev.test;

import com.google.gson.Gson;
import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.context.pipeline.postprocess.impl.ArtifactExportProcessor;
import dev.lvstrng.aidsfuscator.context.pipeline.preprocess.impl.ArtifactImportProcessor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class NestedJarPreservationCheck {
    public static void main(String[] args) throws Exception {
        var tempDir = Files.createTempDirectory("aidsfuscator-nested-jars");
        try {
            var inputJar = tempDir.resolve("input.jar");
            var outputJar = tempDir.resolve("output.jar");
            var librariesDir = Files.createDirectory(tempDir.resolve("libs"));

            var nestedJarOne = createJar(entries(
                    "nested/One.class", createClass("nested/One")
            ));
            var nestedJarTwo = createJar(entries(
                    "nested/Two.class", createClass("nested/Two")
            ));

            var modJson = """
                    {
                      \"schemaVersion\": 1,
                      \"id\": \"test-mod\",
                      \"version\": \"1.0.0\",
                      \"entrypoints\": {
                        \"main\": [
                          \"test.mod.Main\"
                        ]
                      },
                      \"jars\": [
                        {
                          \"file\": \"META-INF/jars/lib-one.jar\"
                        },
                        {
                          \"file\": \"META-INF/jars/missing.jar\"
                        }
                      ]
                    }
                    """;

            Files.write(inputJar, createJar(entries(
                    "test/mod/Main.class", createClass("test/mod/Main"),
                    "fabric.mod.json", modJson.getBytes(StandardCharsets.UTF_8),
                    "META-INF/jars/lib-one.jar", nestedJarOne,
                    "META-INF/jars/lib-two.jar", nestedJarTwo,
                    "assets/test/lang/en_us.json", "{}".getBytes(StandardCharsets.UTF_8)
            )));

            var context = Context.newInstance()
                    .in(inputJar.toString())
                    .out(outputJar.toString())
                    .libs(librariesDir.toString());

            new ArtifactImportProcessor().run(context);
            new ArtifactExportProcessor().run(context);

            assertEntry(outputJar, "META-INF/jars/lib-one.jar", nestedJarOne);
            assertEntry(outputJar, "META-INF/jars/lib-two.jar", nestedJarTwo);
            assertJarList(outputJar, List.of(
                    "META-INF/jars/lib-one.jar",
                    "META-INF/jars/lib-two.jar"
            ));
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private static LinkedHashMap<String, byte[]> entries(Object... data) {
        var entries = new LinkedHashMap<String, byte[]>();
        for(int i = 0; i < data.length; i += 2)
            entries.put((String) data[i], (byte[]) data[i + 1]);
        return entries;
    }

    private static byte[] createJar(LinkedHashMap<String, byte[]> entries) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var jar = new JarOutputStream(output)) {
            for(var entry : entries.entrySet()) {
                jar.putNextEntry(new ZipEntry(entry.getKey()));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] createClass(String internalName) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);

        var init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void assertEntry(Path jarPath, String entryName, byte[] expectedBytes) throws IOException {
        try (var jar = new ZipFile(jarPath.toFile())) {
            var entry = jar.getEntry(entryName);
            if(entry == null)
                throw new IllegalStateException("Missing entry: " + entryName);

            var actualBytes = jar.getInputStream(entry).readAllBytes();
            if(!Arrays.equals(actualBytes, expectedBytes))
                throw new IllegalStateException("Unexpected bytes for entry: " + entryName);
        }
    }

    private static void assertJarList(Path jarPath, List<String> expectedPaths) throws IOException {
        try (var jar = new ZipFile(jarPath.toFile())) {
            var entry = jar.getEntry("fabric.mod.json");
            if(entry == null)
                throw new IllegalStateException("Missing entry: fabric.mod.json");

            var json = new Gson().fromJson(new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8), com.google.gson.JsonObject.class);
            var jars = json.getAsJsonArray("jars");
            var paths = new ArrayList<String>();
            for(var jarEntry : jars) {
                if(!jarEntry.isJsonObject())
                    continue;

                var file = jarEntry.getAsJsonObject().get("file");
                if(file != null && file.isJsonPrimitive() && file.getAsJsonPrimitive().isString())
                    paths.add(file.getAsString());
            }

            if(!paths.equals(expectedPaths))
                throw new IllegalStateException("Unexpected jars field: " + paths);
        }
    }

    private static void deleteDirectory(Path path) throws IOException {
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
