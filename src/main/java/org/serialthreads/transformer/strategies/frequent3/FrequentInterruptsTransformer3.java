package org.serialthreads.transformer.strategies.frequent3;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.context.StackFrame;
import org.serialthreads.transformer.AbstractTransformer;
import org.serialthreads.transformer.LocalVariablesShifter;
import org.serialthreads.transformer.MethodNeedsNoTransformationException;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.util.ArrayList;
import java.util.List;

import static org.serialthreads.transformer.code.MethodCode.firstLocal;
import static org.serialthreads.transformer.code.MethodCode.isAbstract;
import static org.serialthreads.transformer.code.MethodCode.isInterface;
import static org.serialthreads.transformer.code.MethodCode.isRun;

/**
 * Class adapter executing byte code enhancement of all methods.
 * For efficiency all interruptible methods will be copied with a reduced signature.
 * The thread and frame will be added to the signature of all interruptible methods.
 * This transformation needs no static thread holder, SimpleSerialThreadManager2 can be used.
 */
@SuppressWarnings({"UnusedAssignment", "UnusedParameters", "UnusedDeclaration"})
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
    List<MethodNode> result = new ArrayList<>(2);

    if ((isInterface(clazz) || isAbstract(method)) && isRun(clazz, method, classInfoCache))
    {
      // do not transform IRunnable.run() itself
      return result;
    }

    if (isAbstract(method))
    {
      // change signature of abstract methods
      result.add(new AbstractCopyMethodTransformer(clazz, method, classInfoCache).transform());
      result.add(new AbstractMethodTransformer(clazz, method, classInfoCache).transform());
      return result;
    }

    try
    {
      if (isRun(clazz, method, classInfoCache))
      {
        // take special care of run method
        result.add(new RunMethodTransformer(clazz, method, classInfoCache).transform());
        return result;
      }

      // "standard" transformation of interruptible methods
      LocalVariablesShifter.shift(firstLocal(method), 3, method);
      result.add(new ConcreteCopyMethodTransformer(clazz, method, classInfoCache).transform());
      result.add(new ConcreteMethodTransformer(clazz, method, classInfoCache).transform());
      return result;
    }
    catch (MethodNeedsNoTransformationException e)
    {
      // no interruptible calls -> nothing to do
      return result;
    }
  }
}
