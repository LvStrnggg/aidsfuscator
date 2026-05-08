package dev.lvstrng.aidsfuscator.context.resource;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.context.resource.handled.FabricModJsonHandler;
import dev.lvstrng.aidsfuscator.context.resource.handled.ManifestHandler;
import dev.lvstrng.aidsfuscator.tree.JResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Class responsible for writing resources after obfuscation.
 * @author lvstrng
 */
public class ResourceHandler {
    private final Context context;
    private final List<JResource> resources = new ArrayList<>();

    private final Map<String, Supplier<HandledResource>> handledResources = Map.of(
            "MANIFEST.MF", ManifestHandler::new,
            "fabric.mod.json", FabricModJsonHandler::new
    );

    public ResourceHandler(Context context) {
        this.context = context;
    }

    public void handle(JarOutputStream jos) throws IOException {
        var resourcesList = new ArrayList<>(resources);
        resourcesList.addAll(context.artificialResources().values());
        for(var resource : resourcesList) {
            var name = resource.getName();
            var bytes = resource.getData();

            var handled = false;
            for(var k : handledResources.keySet()) {
                if(name.endsWith(k)) {
                    handledResources.get(k).get().handle(context, jos, name, bytes);

                    handled = true;
                    break;
                }
            }

            if(handled)
                continue;

            jos.putNextEntry(new ZipEntry(name));
            jos.write(bytes);
            jos.closeEntry();
        }
    }

    public void add(JResource resource) {
        resources.add(resource);
    }

    public List<JResource> resources() {
        return resources;
    }
}
