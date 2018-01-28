
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
  public String toString() {
    return "Transformer " + STRATEGY;
  }

  @Override
  protected List<MethodNode> doTransformMethod(ClassNode clazz, MethodNode method) throws AnalyzerException {
    if (isRun(clazz, method, classInfoCache)) {
      if (isInterface(clazz) || isAbstract(method)) {
        // Do not transform IRunnable.run() itself.
        return null;
      }

      // Take special care of run method.
      return asList(
        new RunMethodTransformer(clazz, method, classInfoCache).transform());
    }

    if (!isAbstract(method) && hasNoInterruptibleMethodCalls(method)) {
      // Copied method not needed, because it will be called never.
      return asList(
        new ConcreteMethodTransformer(clazz, method, classInfoCache).transform());
    }

    // "Standard" transformation of interruptible methods.
    return asList(
      new ConcreteCopyMethodTransformer(clazz, method, classInfoCache).transform(),
      new ConcreteMethodTransformer(clazz, method, classInfoCache).transform());
  }
}
