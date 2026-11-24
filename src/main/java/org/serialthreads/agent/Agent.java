package org.serialthreads.agent;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.serialthreads.transformer.IStrategy;
import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.LoadUntransformedException;
import org.serialthreads.transformer.MissingClassException;
import org.serialthreads.transformer.NotTransformableException;
import org.serialthreads.transformer.classcache.ClassInfoCacheReflection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.serialthreads.transformer.Strategies.DEFAULT;

/**
 * Byte code enhancement agent.
 * Does the same as {@link TransformingClassLoader}.
 * <p>
 * The JVM ignores exceptions thrown by {@link #transform} and loads the class untransformed.
 * So this agent never throws exceptions, but decides itself how to proceed, see {@link #transform}.
 */
public class Agent implements ClassFileTransformer {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(Agent.class);

    /**
     * Byte code which is no valid class file.
     * Makes the JVM fail to load a class with a {@link ClassFormatError}.
     * It must not be empty, because the JVM ignores empty byte code.
     */
    private static final byte[] INVALID_BYTE_CODE = new byte[4];

    /**
     * Strategy.
     */
    private final IStrategy strategy;

    /**
     * Transformers per class loader.
     */
    private final Map<ClassLoader, ITransformer> transformers = new ConcurrentHashMap<>();

    /**
     * Creates a new class file transformer.
     *
     * @param strategy
     *         Strategy to use.
     */
    public Agent(IStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Classes violating the rules for interruptible code are replaced by invalid byte code,
     * so the JVM fails to load them with a {@link ClassFormatError}.
     * Otherwise, they would be loaded untransformed and fail obscurely when executed.
     * Classes which could not be analyzed, e.g. because they reference missing (optional) classes,
     * are loaded untransformed, because this agent sees all classes, not just the ones containing interruptible code.
     * <p>
     * The JVM passes no class name for classes defined without one, e.g. by {@link ClassLoader#defineClass} with a
     * {@code null} name. In this case the class name is taken from the byte code.
     */
    @Override
    public byte @Nullable [] transform(
            @Nullable ClassLoader loader,
            @Nullable String className,
            @Nullable Class<?> classBeingRedefined,
            @Nullable ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        if (loader == null) {
            // No need to transform classes loaded by the bootstrap classloader.
            return null;
        }

        // Used for logging only, as long as the class name has not been determined.
        var name = className != null ? className : "<unnamed>";
        try {
            if (className == null) {
                name = new ClassReader(classfileBuffer).getClassName();
            }
            var mode = classBeingRedefined != null ? "redefining" : "initial";

            logger.debug("Transforming class {} ({})", name, mode);
            var transformer = transformers.computeIfAbsent(loader, this::getTransformer);
            transformer.getClassInfoCache().addClassInfo(name, classfileBuffer);
            var transformed = transformer.transform(classfileBuffer);

            logger.info("Successfully transformed class {} ({})", name, mode);
            return transformed;
        } catch (LoadUntransformedException e) {
            // No need to transform byte code.
            return null;
        } catch (MissingClassException e) {
            logger.warn("Unable to analyze {}, loading it untransformed: {}", name, e.getMessage());
            return null;
        } catch (NotTransformableException e) {
            logger.error("Unable to transform {}, failing its load", name, e);
            return INVALID_BYTE_CODE;
        } catch (RuntimeException | Error e) {
            logger.error("Failed to transform {}, loading it untransformed", name, e);
            return null;
        }
    }

    /**
     * Start of agent before main method has been called.
     * Called by VM to start this agent.
     *
     * @param arguments
     *         Arguments.
     * @param inst
     *         Instrumentation.
     */
    public static void premain(String arguments, Instrumentation inst) {
        agentmain(arguments, inst);
    }

    /**
     * Start of agent after main method has been called.
     * Called by VM to start this agent.
     *
     * @param arguments
     *         Arguments.
     * @param inst
     *         Instrumentation.
     */
    public static void agentmain(String arguments, Instrumentation inst) {
        try {
            inst.addTransformer(new Agent(DEFAULT));
        } catch (RuntimeException e) {
            logger.error("Failed to init agent", e);
        }
    }

    @Override
    public String toString() {
        return getTransformer(getClass().getClassLoader()).toString() + " via agent.";
    }

    private ITransformer getTransformer(ClassLoader classLoader) {
        return strategy.getTransformer(new ClassInfoCacheReflection(classLoader));
    }
}
