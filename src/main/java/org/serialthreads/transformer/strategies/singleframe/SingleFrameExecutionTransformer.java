package org.serialthreads.transformer.strategies.singleframe;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.context.StackFrame;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.strategies.AbstractTransformer;
import org.serialthreads.transformer.strategies.LocalVariablesShifter;
import org.serialthreads.transformer.strategies.MethodNeedsNoTransformationException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.serialthreads.transformer.code.MethodCode.firstLocal;
import static org.serialthreads.transformer.code.MethodCode.isAbstract;
import static org.serialthreads.transformer.code.MethodCode.isInterface;
import static org.serialthreads.transformer.code.MethodCode.isRun;

/**
 * Transformer implementing serial threads by executing single frames.
 */
public class SingleFrameExecutionTransformer extends AbstractTransformer
{
  public static final String STRATEGY = "SINGLE_FRAME_EXECUTION";

//  private static final String METHOD_HANDLE_NAME = Type.getType(MethodHandle.class).getInternalName();
//  private static final String METHOD_HANDLE_DESC = Type.getType(MethodHandle.class).getDescriptor();
//  private static final String LOOKUP_NAME = Type.getType(Lookup.class).getInternalName();
//  private static final String LOOKUP_DESC = Type.getType(Lookup.class).getDescriptor();

//    clazz.fields.add(new FieldNode(ASM4, ACC_PRIVATE, changeCopyName(method.name, method.desc), METHOD_HANDLE_DESC, METHOD_HANDLE_NAME, null));

  /**
   * Constructor.
   *
   * @param classInfoCache class cache to use
   */
  public SingleFrameExecutionTransformer(IClassInfoCache classInfoCache)
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

    try
    {
      if (isRun(clazz, method, classInfoCache))
      {
        // take special care of run method
        return Arrays.asList(
          new RunMethodTransformer(clazz, method, classInfoCache).transform());
      }

      // "standard" transformation of interruptible methods
      // TODO 2011-11-27 mh: Move shift into method transformer
      LocalVariablesShifter.shift(firstLocal(method), 3, method);
      return Arrays.asList(
        new ConcreteCopyMethodTransformer(clazz, method, classInfoCache).transform(),
        new ConcreteMethodTransformer(clazz, method, classInfoCache).transform());
    }
    catch (MethodNeedsNoTransformationException e)
    {
      // no interruptible calls -> nothing to do
      return Collections.emptyList();
    }
  }
}
