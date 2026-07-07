package dev.lvstrng.aidsfuscator.context.pipeline.preprocess.impl;

import dev.lvstrng.aidsfuscator.context.Context;
import dev.lvstrng.aidsfuscator.context.pipeline.IProcessor;
import dev.lvstrng.aidsfuscator.exclude.impl.Exclusions;
import dev.lvstrng.aidsfuscator.log.Logger;
import dev.lvstrng.aidsfuscator.tree.impl.JClass;
import dev.lvstrng.aidsfuscator.utils.ClassUtils;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

public class ArtifactImportProcessor implements IProcessor {
    @Override
    public void run(Context context) {
        Logger.info("Reading input JAR...");
        var file = new File(context.in());
        if(!file.exists())
            throw new IllegalArgumentException("Input file `" + context.in() + "` does not exist");

        // ---- LOAD JAR CLASSES ----
        context.presetLoader().loadAll();
        try (var zip = new ZipFile(file)) {
            for(var entry : zip.stream().toList()) {
                if(entry.isDirectory())
                    continue;

                var name = entry.getName();
                var is = zip.getInputStream(entry);
                var bytes = is.readAllBytes();

                // if class, add new class
                if(name.endsWith(".class")) {
                    var clazz = new JClass(ClassUtils.readClass(bytes));
                    context.setVersion(Math.max(clazz.version(), context.version()));

                    // if excluded, add to excluded class list
                    if(Exclusions.GLOBAL.excluded(clazz)) {
                        clazz.setLibrary();
                        context.addExcluded(clazz);
                        continue;
                    }

                    context.add(clazz);
                    continue;
                }

                // jars in a jar are "fat jars"
                if(name.endsWith(".jar")) {
                    context.libraryLoader().parseJar(bytes);
                    if(context.resourceHandler().isNestedJar(name))
                        context.resourceHandler().add(name, bytes);
                    continue;
                }

                // add resource
                context.resourceHandler().add(name, bytes);
            }
        } catch (IOException _) {}
    }
}
