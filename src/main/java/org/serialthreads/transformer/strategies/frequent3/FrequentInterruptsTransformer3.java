
package org.serialthreads.transformer.strategies.frequent3;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.context.SerialThreadExecutor;
import org.serialthreads.context.StackFrame;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.strategies.AbstractTransformer;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.serialthreads.transformer.code.MethodCode.isAbstract;
import static org.serialthreads.transformer.code.MethodCode.isInterface;
import static org.serialthreads.transformer.code.MethodCode.isRun;

/**
 * Class adapter executing byte code enhancement of all methods.
 * For efficiency all interruptible methods will be copied with a reduced signature.
 * The thread and frame will be added to the signature of all interruptible methods.
 * This transformation needs no static thread holder, {@link SerialThreadExecutor} can be used.
 */
public class FrequentInterruptsTransformer3 extends AbstractTransformer {
  /**
   * Strategy name.
   */
  public static final String STRATEGY = "FREQUENT3";

  /**
   * Constructor.
   *
   * @param classInfoCache class cache to use
   */
  public FrequentInterruptsTransformer3(IClassInfoCache classInfoCache) {
    super(classInfoCache, StackFrame.DEFAULT_FRAME_SIZE);
  }

  @Override
  protected List<MethodNode> doTransformMethod(ClassNode clazz, MethodNode method) throws AnalyzerException {
    if (isRun(clazz, method, classInfoCache)) {
      return doTransformRun(clazz, method);
    }

    if (!isAbstract(method) && hasNoInterruptibleMethodCalls(method)) {
      // Copied method not needed, because it will be called never.
      // Original method needs to be transformed though, because callers don't known about this fact.
      return singletonList(
        new OriginalMethodTransformer(clazz, method, classInfoCache).transform());
    }

    // "Standard" transformation of interruptible methods.
    return asList(
      new CopyMethodTransformer(clazz, method, classInfoCache).transform(),
      new OriginalMethodTransformer(clazz, method, classInfoCache).transform());
  }

  /**
   * Execute byte code transformation on run method.
   *
   * @param clazz class to transform
   * @param method method node to transform
   * @return transformed methods
   */
  private List<MethodNode> doTransformRun(ClassNode clazz, MethodNode method) throws AnalyzerException {
    if (isInterface(clazz) || isAbstract(method)) {
      // Do not transform IRunnable.run() itself.
      return null;
    }

    // Take special care of run method.
    return singletonList(
      new RunMethodTransformer(clazz, method, classInfoCache).transform());
  }

  @Override
  public String toString() {
    return "Transformer " + STRATEGY;
  }
}
