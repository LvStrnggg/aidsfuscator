package dev.lvstrng.aidsfuscator.context.resource.handled;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.context.resource.HandledResource;
import dev.lvstrng.aidsfuscator.log.Logger;
import dev.lvstrng.aidsfuscator.naming.Mappings;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class FabricModJsonHandler implements HandledResource {
    private final List<String> entrypointTypes = List.of(
            "main", "client"
    );

    @Override
    public void handle(Context context, JarOutputStream jos, String name, byte[] bytes) throws IOException {
        var gson = new Gson();
        var modJson = gson.fromJson(new String(bytes), JsonObject.class);

        var entrypoints = modJson.getAsJsonObject("entrypoints");
        if(entrypoints == null) {
            jos.putNextEntry(new ZipEntry(name));
            jos.write(bytes);
            jos.closeEntry();
            Logger.warn("Ignoring `" + name + "`, no entry points found");
            return;
        }

        for(var type : entrypointTypes) {
            var p = entrypoints.get(type);
            if(p == null)
                continue;

            var entrypoint = p.getAsJsonArray();
            if(entrypoint == null)
                continue;

            for(int i = 0; i < entrypoint.size(); i++) {
                var className = entrypoint.get(i).getAsString().replace('.', '/');
                var newName = Mappings.CLASS.retrieve(className).value().replace('/', '.');
                entrypoint.set(i, new JsonPrimitive(newName));
            }
        }

        syncNestedJars(context, modJson);

        jos.putNextEntry(new ZipEntry(name));
        jos.write(gson.toJson(modJson).getBytes());
        jos.closeEntry();
    }

    private void syncNestedJars(Context context, JsonObject modJson) {
        var jarEntries = modJson.getAsJsonArray("jars");
        if(jarEntries == null)
            return;

        var actualPaths = new LinkedHashSet<>(context.resourceHandler().nestedJarPaths());
        var synced = new JsonArray();

        for(var element : jarEntries) {
            if(!element.isJsonObject()) {
                synced.add(element);
                continue;
            }

            var jar = element.getAsJsonObject();
            var file = jar.get("file");
            if(file == null || !file.isJsonPrimitive() || !file.getAsJsonPrimitive().isString()) {
                synced.add(element);
                continue;
            }

            var path = file.getAsString();
            if(actualPaths.remove(path))
                synced.add(element);
        }

        for(var path : actualPaths) {
            var jar = new JsonObject();
            jar.add("file", new JsonPrimitive(path));
            synced.add(jar);
        }

        modJson.add("jars", synced);
    }
}
