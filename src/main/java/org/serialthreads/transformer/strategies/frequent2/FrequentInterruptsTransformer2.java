package org.serialthreads.transformer.strategies.frequent2;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.context.SimpleSerialThreadManager;
import org.serialthreads.context.StackFrame;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.strategies.AbstractTransformer;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.serialthreads.transformer.code.MethodCode.isAbstract;
import static org.serialthreads.transformer.code.MethodCode.isInterface;
import static org.serialthreads.transformer.code.MethodCode.isRun;

/**
 * Class adapter executing byte code enhancement of all methods.
 * For efficiency all interruptible methods will be copied with a reduced signature.
 * The signature of all interruptible methods will not be changed.
 * This transformation needs a static thread holder, {@link SimpleSerialThreadManager} needs to be used.
 */
public class FrequentInterruptsTransformer2 extends AbstractTransformer {
    /**
     * Strategy name.
     */
    public static final String STRATEGY = "FREQUENT2";

    /**
     * Constructor.
     *
     * @param classInfoCache
     *         Class cache to use.
     */
    public FrequentInterruptsTransformer2(IClassInfoCache classInfoCache) {
        super(classInfoCache, StackFrame.DEFAULT_FRAME_SIZE);
    }

    @Override
    protected @Nullable List<MethodNode> doTransformMethod(ClassNode clazz, MethodNode method) throws AnalyzerException {
        if (isAbstract(method)) {
            // Change signature of abstract methods.
            return singletonList(
                    new CopyMethodTransformer(clazz, method, classInfoCache).transform());
        }

        if (isRun(clazz, method, classInfoCache)) {
            // Take special care of run method.
            return singletonList(
                    new RunMethodTransformer(clazz, method, classInfoCache).transform());
        }

        if (hasNoInterruptibleMethodCalls(method)) {
            // No transformation needed.
            return null;
        }

        // "Standard" transformation of interruptible methods.
        return asList(
                new CopyMethodTransformer(clazz, method, classInfoCache).transform(),
                new OriginalMethodTransformer(clazz, method, classInfoCache).transform());
    }

    @Override
    protected void afterTransformation(ClassNode clazz, List<MethodNode> constructors) {
        if (isInterface(clazz) || implementTransformedRunnable(clazz, constructors, true, true)) {
            return;
        }

        if (!classInfoCache.isInterruptible(requireNonNull(classInfoCache.getSuperClass(clazz.name)))) {
            logger.debug("  Creating stack");
            addThreadField(clazz, false);
        }
    }

    @Override
    public String toString() {
        return "Transformer " + STRATEGY;
    }
}
