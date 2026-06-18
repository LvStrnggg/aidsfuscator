package dev.lvstrng.aidsfuscator.context;

import dev.lvstrng.aidsfuscator.analysis.ref.ReferenceGraph;
import dev.lvstrng.aidsfuscator.classgen.impl.StrictSaltDispatcherClassGenerator;
import dev.lvstrng.aidsfuscator.context.exception.MissingMemberException;
import dev.lvstrng.aidsfuscator.context.exception.MissingWorkspaceItemException;
import dev.lvstrng.aidsfuscator.context.hierarchy.IHierarchy;
import dev.lvstrng.aidsfuscator.context.hierarchy.SimpleHierarchy;
import dev.lvstrng.aidsfuscator.context.library.LibraryLoader;
import dev.lvstrng.aidsfuscator.context.order.ClassInitOrderHandler;
import dev.lvstrng.aidsfuscator.context.pipeline.IPass;
import dev.lvstrng.aidsfuscator.context.pipeline.obfuscation.ObfuscationPass;
import dev.lvstrng.aidsfuscator.context.pipeline.postprocess.PostProcessorPass;
import dev.lvstrng.aidsfuscator.context.pipeline.preprocess.PreProcessorPass;
import dev.lvstrng.aidsfuscator.context.resource.ResourceHandler;
import dev.lvstrng.aidsfuscator.exclude.ExclusionPresetLoader;
import dev.lvstrng.aidsfuscator.file.impl.initOrder.ClassInitOrderLoader;
import dev.lvstrng.aidsfuscator.log.Logger;
import dev.lvstrng.aidsfuscator.naming.dictionary.IDictionary;
import dev.lvstrng.aidsfuscator.property.GlobalPropertyContainer;
import dev.lvstrng.aidsfuscator.reference.ReferenceManager;
import dev.lvstrng.aidsfuscator.transform.Transformer;
import dev.lvstrng.aidsfuscator.tree.impl.JClass;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Supplier;

/**
 * The obfuscator context "the core". This class is responsible for reading input JAR, transforming read classes, exporting output, handling global exclusions,
 * and contains utils that can be used by the transformers, like for instance the reference graph or dictionary.
 * @author lvstrng
 */
public class Context {
    private String input, output, libPath, javaPath;
    private final Map<String, JClass> classes, artificials, libraries, excluded;
    private int writerFlags;
    private int version;
    private boolean computeFrames, aggressiveOverload;
    private String dictionaryString;
    private String watermark;

    private final LibraryLoader libraryLoader;
    private final ExclusionPresetLoader presetLoader;
    private final ClassInitOrderLoader initOrderLoader;
    private final ReferenceGraph referenceGraph;
    private final GlobalPropertyContainer propertyContainer;
    private final ReferenceManager referenceManager;
    private final StrictSaltDispatcherClassGenerator saltDispatcherGen;
    private final ResourceHandler resourceHandler;
    private final ClassInitOrderHandler initOrder;

    private final IHierarchy hierarchy;
    private IDictionary dictionary;

    private final List<Transformer> transformers;
    private static final List<Supplier<IPass>> pipeline = List.of(
            PreProcessorPass::new,
            ObfuscationPass::new,
            PostProcessorPass::new
    );

    private Context() {
        this.dictionaryString = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        this.watermark = ""; // empty by default => no watermark
        this.classes = new HashMap<>();
        this.artificials = new HashMap<>();
        this.libraries = new HashMap<>();
        this.excluded = new HashMap<>();
        this.transformers = new ArrayList<>();

        this.presetLoader       = new ExclusionPresetLoader();
        this.resourceHandler    = new ResourceHandler(this);
        this.hierarchy          = new SimpleHierarchy(this);
        this.libraryLoader      = new LibraryLoader(this);
        this.referenceGraph     = new ReferenceGraph(this);
        this.propertyContainer  = new GlobalPropertyContainer();
        this.initOrder          = new ClassInitOrderHandler(this);
        this.referenceManager   = new ReferenceManager(this);
        this.saltDispatcherGen  = new StrictSaltDispatcherClassGenerator();
        this.initOrderLoader    = new ClassInitOrderLoader(this, "");

        this.writerFlags = ClassWriter.COMPUTE_MAXS;
    }

    public Context run(Transformer... transformers) {
        this.transformers.addAll(Arrays.asList(transformers));
        return run();
    }

    public Context run() {
        pipeline.forEach(e -> e.get().run(this));
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

    public void setDictionary(IDictionary dictionary) {
        this.dictionary = dictionary;
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

    public StrictSaltDispatcherClassGenerator saltDispatcher() {
        return saltDispatcherGen;
    }

    public ExclusionPresetLoader presetLoader() {
        return presetLoader;
    }

    public ClassInitOrderLoader initOrderLoader() {
        return initOrderLoader;
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

    // -----------------
    // ---- CLASSES ----
    // -----------------

    public boolean hasClass(String internal) {
        return hasJarClass(internal) || hasLibClass(internal) || hasArtificial(internal);
    }

    public boolean hasLibClass(String internal) {
        return libraries.containsKey(internal);
    }

    public boolean hasJarClass(String internal) {
        return classes.containsKey(internal) || excluded.containsKey(internal);
    }

    public boolean hasArtificial(String internal) {
        return artificials.containsKey(internal);
    }

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

    public void setVersion(int version) {
        this.version = version;
    }

    public String dictionaryString() {
        return dictionaryString;
    }

    public String watermark() {
        return watermark;
    }

    public String javaPath() {
        return javaPath;
    }

    public boolean aggressiveOverload() {
        return aggressiveOverload;
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

    public Context setAggressiveOverload(boolean aggressiveOverload) {
        this.aggressiveOverload = aggressiveOverload;
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
