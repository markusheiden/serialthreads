package org.serialthreads.transformer.strategies.frequent;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.context.SimpleSerialThreadManager;
import org.serialthreads.context.StackFrame;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.strategies.AbstractTransformer;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.serialthreads.transformer.code.MethodCode.isAbstract;
import static org.serialthreads.transformer.code.MethodCode.isInterface;
import static org.serialthreads.transformer.code.MethodCode.isRun;

/**
 * Class adapter executing byte code enhancement of all methods.
 * The signature of all interruptible methods will not be changed.
 * This transformation needs a static thread holder, {@link SimpleSerialThreadManager} needs to be used.
 */
public class FrequentInterruptsTransformer extends AbstractTransformer {
    /**
     * Strategy name.
     */
    public static final String STRATEGY = "FREQUENT";

    /**
     * Constructor.
     *
     * @param classInfoCache
     *         Class cache to use.
     */
    public FrequentInterruptsTransformer(IClassInfoCache classInfoCache) {
        super(classInfoCache, StackFrame.DEFAULT_FRAME_SIZE);
    }

    @Override
    protected @Nullable List<MethodNode> doTransformMethod(ClassNode clazz, MethodNode method) throws AnalyzerException {
        if (isAbstract(method)) {
            // Abstract methods need no transformation.
            return null;
        }

        if (isRun(clazz, method, classInfoCache)) {
            // Take special care of run method.
            return List.of(
                    new RunMethodTransformer(clazz, method, classInfoCache).transform());
        }

        if (hasNoInterruptibleMethodCalls(method)) {
            // No transformation needed.
            return null;
        }

        // "Standard" transformation of interruptible methods.
        return List.of(
                new OriginalMethodTransformer(clazz, method, classInfoCache).transform());
    }

    @Override
    protected void afterTransformation(ClassNode clazz, List<MethodNode> constructors) {
        if (isInterface(clazz) || implementTransformedRunnable(clazz, constructors, true, true)) {
            return;
        }

        if (!classInfoCache.isInterruptible(requireNonNull(classInfoCache.getSuperClass(clazz.name)))) {
            logger.debug("  Creating stack for {}", clazz.name);
            addThreadField(clazz, false);
        }
    }

    @Override
    public String toString() {
        return "Transformer " + STRATEGY;
    }
}
