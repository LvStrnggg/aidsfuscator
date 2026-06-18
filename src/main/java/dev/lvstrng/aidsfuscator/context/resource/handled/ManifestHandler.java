package dev.lvstrng.aidsfuscator.context.resource.handled;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.context.resource.HandledResource;
import dev.lvstrng.aidsfuscator.naming.Mappings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

public class ManifestHandler implements HandledResource {
    @Override
    public void handle(Context context, JarOutputStream jos, String name, byte[] bytes) throws IOException {
        try {
            var is = new ByteArrayInputStream(bytes);
            var manifest = new Manifest(is);

            var attributes = manifest.getMainAttributes();
            var main = attributes.getValue("Main-Class");
            if(main != null){
                var newName = Mappings.CLASS.retrieve(
                        main.replace('.', '/')
                ).value().replace('/', '.');
                attributes.put(new Attributes.Name("Main-Class"), newName);
            }
            jos.putNextEntry(new ZipEntry(name));
            manifest.write(jos);
            jos.closeEntry();
        } catch (Exception e) {
            throw new RuntimeException("An error occurred trying to handle the manifest file.", e);
        }
    }
}
