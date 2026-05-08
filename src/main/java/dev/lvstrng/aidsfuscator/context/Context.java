package dev.lvstrng.aidsfuscator.context;

import dev.lvstrng.aidsfuscator.context.order.ClassInitOrderHandler;
import dev.lvstrng.aidsfuscator.analysis.ref.ReferenceGraph;
import dev.lvstrng.aidsfuscator.context.asm.HierarchyClassWriter;
import dev.lvstrng.aidsfuscator.context.exception.MissingMemberException;
import dev.lvstrng.aidsfuscator.context.exception.MissingWorkspaceItemException;
import dev.lvstrng.aidsfuscator.context.hierarchy.IHierarchy;
import dev.lvstrng.aidsfuscator.context.hierarchy.SimpleHierarchy;
import dev.lvstrng.aidsfuscator.context.library.LibraryLoader;
import dev.lvstrng.aidsfuscator.context.resource.ResourceHandler;
import dev.lvstrng.aidsfuscator.exclude.Exclusions;
import dev.lvstrng.aidsfuscator.log.Logger;
import dev.lvstrng.aidsfuscator.naming.dictionary.DefaultDictionary;
import dev.lvstrng.aidsfuscator.naming.dictionary.IDictionary;
import dev.lvstrng.aidsfuscator.property.GlobalPropertyContainer;
import dev.lvstrng.aidsfuscator.reference.ReferenceManager;
import dev.lvstrng.aidsfuscator.transform.Transformer;
import dev.lvstrng.aidsfuscator.tree.*;
import dev.lvstrng.aidsfuscator.utils.ClassUtils;
import dev.lvstrng.aidsfuscator.utils.Utils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The obfuscator context "the core". This class is responsible for reading input JAR, transforming read classes, exporting output, handling global exclusions,
 * and contains utils that can be used by the transformers, like for instance the reference graph or dictionary.
 * @author lvstrng
 */
public class Context {
    private String input, output, libPath, javaPath;
    private final Map<String, JClass> classes, artificials, libraries, excluded;
    private final Map<String, JResource> resources, artificialResources;
    private int writerFlags;
    private int version;
    private boolean computeFrames;
    private String dictionaryString;
    private String watermark;

    private final ResourceHandler resourceHandler;
    private final LibraryLoader libraryLoader;
    private final IHierarchy hierarchy;
    private final ReferenceGraph referenceGraph;
    private final GlobalPropertyContainer propertyContainer;
    private final ClassInitOrderHandler initOrder;
    private final ReferenceManager referenceManager;
    private IDictionary dictionary;

    private final List<Transformer> transformers;

    private Context() {
        this.dictionaryString = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        this.watermark = ""; // empty by default => no watermark
        this.classes = new HashMap<>();
        this.artificials = new HashMap<>();
        this.libraries = new HashMap<>();
        this.excluded = new HashMap<>();
        this.resources = new HashMap<>();
        this.artificialResources = new HashMap<>();
        this.transformers = new ArrayList<>();

        this.resourceHandler    = new ResourceHandler(this);
        this.hierarchy          = new SimpleHierarchy(this);
        this.libraryLoader      = new LibraryLoader(this, javaPath);
        this.referenceGraph     = new ReferenceGraph(this);
        this.propertyContainer  = new GlobalPropertyContainer();
        this.initOrder          = new ClassInitOrderHandler(this);
        this.referenceManager   = new ReferenceManager(this);

        this.writerFlags = ClassWriter.COMPUTE_MAXS;
    }

    // ---- INITIALIZE OBFUSCATOR ----
    public Context initialize() {
        if (!computeFrames) {
            Logger.warn("------------------------------------------------");
            Logger.warn("You've disabled frame computation, you will not receive any support. Enable it in config with computeFrames");
            Logger.warn("------------------------------------------------");
        }
        this.dictionary         = new DefaultDictionary(this, dictionaryString);

        Logger.info("Loading libraries...");
        this.libraryLoader().loadLibraries(libPath);

        Logger.info("Reading input JAR...");
        this.readJar();

        Logger.info("Building hierarchy...");
        this.hierarchy.build(); // build hierarchy
        return this;
    }

    private void readJar() {
        var file = new File(input);
        if(!file.exists())
            throw new IllegalArgumentException("Input file `" + input + "` does not exist");

        // ---- LOAD JAR CLASSES ----
        try (var zip = new ZipFile(file)) {
            for(var entry : zip.stream().toList()) {
                var name = entry.getName();
                var is = zip.getInputStream(entry);
                var bytes = is.readAllBytes();

                // if class, add new class
                if(name.endsWith(".class")) {
                    var clazz = new JClass(ClassUtils.readClass(bytes));
                    version = Math.max(clazz.version(), version);

                    // if excluded, add to excluded class list
                    if(Exclusions.GLOBAL.excluded(clazz)) {
                        clazz.setLibrary();
                        addExcluded(clazz);
                        continue;
                    }

                    add(clazz);
                    continue;
                }

                // jars in a jar are "fat jars"
                if(name.endsWith(".jar")) {
                    libraryLoader.parseJar(bytes);
                    continue;
                }

                // add resource
                var resource = new JResource(name, bytes, this);
                resources.put(name, resource);
                resourceHandler.add(resource);
            }
        } catch (IOException _) {}
    }

    public Context transform(Transformer... transformers) {
        this.transformers.addAll(Arrays.asList(transformers));

        for(var transformer : transformers) {
            Logger.info("Running '%s'", transformer.name());
            transformer.transform(this);
            Logger.success("Completed running '%s' with %s changes", transformer.name(), transformer.changes());
            Logger.info("");
        }

        return this;
    }

    public Context transform() {
        for(var transformer : transformers) {
            Logger.info("Running '%s'", transformer.name());
            transformer.transform(this);
            Logger.success("Completed running '%s' with %s changes", transformer.name(), transformer.changes());
            Logger.info("");
        }
        return this;
    }

    @SuppressWarnings("all")
    public Context exportJar() {
        Logger.info("Exporting JAR...");

        var outputFile = new File(output);
        try (var jos = new JarOutputStream(new FileOutputStream(outputFile))) {
            var classes = new ArrayList<>(jarClasses());
            classes.addAll(artificials().values());

            for(var clazz : classes) {
                var writer = new HierarchyClassWriter(this);
                try {
                    clazz.core().accept(writer);
                } catch (Exception e) {
                    Logger.error("Error writing class %s", clazz.name());
                    e.printStackTrace();
                }

                jos.putNextEntry(new ZipEntry(clazz.name() + ".class"));
                jos.write(writer.toByteArray());
                jos.closeEntry();
            }

            resourceHandler().handle(jos);
        } catch (IOException e) {
            Logger.error("Error writing output JAR: %s", e);
        }

        Logger.success("Exported JAR successfully!");
        Logger.success("%s (%skb) -> %s (%skb)",
                input, Utils.bytesToKB(new File(input).length()),
                output, Utils.bytesToKB(outputFile.length())
        );
        return this;
    }

    // -----------------
    // ----   MISC  ----
    // -----------------

    public List<Transformer> transformers() {
        return transformers;
    }

    public IDictionary dictionary() {
        return dictionary;
    }

    public ResourceHandler resourceHandler() {
        return resourceHandler;
    }

    public IHierarchy hierarchy() {
        return hierarchy;
    }

    public LibraryLoader libraryLoader() {
        return libraryLoader;
    }

    public ReferenceGraph referenceGraph() {
        return referenceGraph;
    }

    public GlobalPropertyContainer properties() {
        return propertyContainer;
    }

    public ClassInitOrderHandler initOrder() {
        return initOrder;
    }

    public ReferenceManager referenceManager() {
        return referenceManager;
    }

    public int writerFlags() {
        return writerFlags;
    }

    public static String readWorkspaceString(String item) {
        try {
            return Files.readString(getFromWorkspace(item).toPath());
        } catch (IOException _) {
            var e = new MissingWorkspaceItemException(item);
            Logger.error(e.getMessage());
            throw e;
        }
    }

    public static File getFromWorkspace(String item) {
        return new File("workspace/" + item);
    }

    public String in() {
        return input;
    }

    public String out() {
        return output;
    }

    public String libs() {
        return libPath;
    }

    // -------------------
    // ---- RESOURCES ----
    // -------------------

    public JResource createResource() {
        return new JResource(dictionary.newResourceName(), this);
    }

    public Map<String, JResource> resourceMap() {
        return resources;
    }

    public Map<String, JResource> artificialResources() {
        return artificialResources;
    }

    public void addArtificial(JResource resource) {
        artificialResources.put(resource.getName(), resource);
    }

    // -------------------
    // ---- CLASSES ------
    // -------------------

    public JClass createClass(String superName, int access) {
        var _node = new ClassNode();
        _node.name = dictionary.newClassName();
        _node.superName = superName;
        _node.access = access;
        _node.version = version;

        return new JClass(_node);
    }

    public JClass forName(String name) {
        var clazz = classes.get(name);
        if(clazz == null) clazz = libraries.get(name);
        if(clazz == null) clazz = excluded.get(name);
        if(clazz == null) clazz = artificials.get(name);

        if(clazz == null)
            throw new MissingMemberException(name);

        return clazz;
    }

    public void add(JClass clazz) {
        classes.put(clazz.name(), clazz);
    }

    public void addExcluded(JClass clazz) {
        excluded.put(clazz.name(), clazz);
    }

    public void addArtificial(JClass clazz) {
        artificials.put(clazz.name(), clazz);
    }

    public void addLibrary(JClass clazz) {
        libraries.put(clazz.name(), clazz);
    }

    public List<JClass> jarClasses() {
        var list = new ArrayList<>(classes.values().stream().toList());
        list.addAll(excluded.values());
        return list;
    }

    public List<JClass> classes() {
        return classes.values().stream().toList();
    }

    public Map<String, JClass> classMap() {
        return classes;
    }

    public Map<String, JClass> libraries() {
        return libraries;
    }

    public Map<String, JClass> artificials() {
        return artificials;
    }

    public Map<String, JClass> excluded() {
        return excluded;
    }

    public boolean doesComputeFrames() {
        return computeFrames;
    }

    public int version() {
        return version;
    }

    public String dictionaryString() {
        return dictionaryString;
    }

    public String watermark() {
        return watermark;
    }

    // -----------------
    // ---- BUILDER ----
    // -----------------

    public static Context newInstance() {
        return new Context();
    }

    public Context in(String input) {
        this.input = input;
        return this;
    }

    public Context out(String output) {
        this.output = output;
        return this;
    }

    public Context libs(String libPath) {
        this.libPath = libPath;
        return this;
    }

    public Context javaPath(String javaPath) {
        this.javaPath = javaPath;
        return this;
    }

    public Context computeFrames() {
        this.computeFrames = true;
        this.writerFlags |= ClassWriter.COMPUTE_FRAMES;
        return this;
    }

    public Context setDictionary(String str) {
        this.dictionaryString = str;
        return this;
    }

    public Context setWatermark(String str) {
        this.watermark = str;
        return this;
    }
}
