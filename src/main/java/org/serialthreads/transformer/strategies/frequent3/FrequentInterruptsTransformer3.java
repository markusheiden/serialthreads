package org.serialthreads.transformer.strategies.frequent3;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.context.StackFrame;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.strategies.AbstractTransformer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.serialthreads.transformer.code.MethodCode.isAbstract;
import static org.serialthreads.transformer.code.MethodCode.isInterface;
import static org.serialthreads.transformer.code.MethodCode.isRun;

/**
 * Class adapter executing byte code enhancement of all methods.
 * For efficiency all interruptible methods will be copied with a reduced signature.
 * The thread and frame will be added to the signature of all interruptible methods.
 * This transformation needs no static thread holder, SimpleSerialThreadManager2 can be used.
 */
public class FrequentInterruptsTransformer3 extends AbstractTransformer
{
  public static final String STRATEGY = "FREQUENT3";

  /**
   * Constructor.
   *
   * @param classInfoCache class cache to use
   */
  public FrequentInterruptsTransformer3(IClassInfoCache classInfoCache)
  {
    super(classInfoCache, StackFrame.DEFAULT_FRAME_SIZE);
  }

  @Override
  public String toString()
  {
    return "Transformer " + STRATEGY;
  }

  @Override
  protected List<MethodNode> doTransformMethod(ClassNode clazz, MethodNode method) throws AnalyzerException
  {
    if ((isInterface(clazz) || isAbstract(method)) && isRun(clazz, method, classInfoCache))
    {
      // do not transform IRunnable.run() itself
      return Collections.emptyList();
    }

    if (isAbstract(method))
    {
      // change signature of abstract methods
      return Arrays.asList(
        new AbstractCopyMethodTransformer(clazz, method, classInfoCache).transform(),
        new AbstractMethodTransformer(clazz, method, classInfoCache).transform());
    }

    if (hasNoInterruptibleMethodCalls(method))
    {
      // no transformation needed
      return Collections.emptyList();
    }

    if (isRun(clazz, method, classInfoCache))
    {
      // take special care of run method
      return Arrays.asList(
        new RunMethodTransformer(clazz, method, classInfoCache).transform());
    }

    // "standard" transformation of interruptible methods
    return Arrays.asList(
      new ConcreteCopyMethodTransformer(clazz, method, classInfoCache).transform(),
      new ConcreteMethodTransformer(clazz, method, classInfoCache).transform());
  }
}
