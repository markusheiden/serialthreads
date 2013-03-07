package org.serialthreads.transformer.strategies.frequent2;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.context.StackFrame;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.strategies.AbstractTransformer;

import java.util.Arrays;
import java.util.List;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ACC_TRANSIENT;
import static org.serialthreads.transformer.code.MethodCode.isAbstract;
import static org.serialthreads.transformer.code.MethodCode.isInterface;
import static org.serialthreads.transformer.code.MethodCode.isRun;

/**
 * Class adapter executing byte code enhancement of all methods.
 * For efficiency all interruptible methods will be copied with a reduced signature.
 * The signature of all interruptible methods will not be changed.
 * This transformation needs a static thread holder, so SimpleSerialThreadManager has to be used.
 */
public class FrequentInterruptsTransformer2 extends AbstractTransformer
{
  public static final String STRATEGY = "FREQUENT2";

  /**
   * Constructor.
   *
   * @param classInfoCache class cache to use
   */
  public FrequentInterruptsTransformer2(IClassInfoCache classInfoCache)
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
    if (isAbstract(method))
    {
      // change signature of abstract methods
      return Arrays.asList(
        new AbstractCopyMethodTransformer(clazz, method, classInfoCache).transform());
    }

    if (hasNoInterruptibleMethodCalls(method))
    {
      // no transformation needed
      return null;
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

  @Override
  protected void afterTransformation(ClassNode clazz, List<MethodNode> constructors)
  {
    if (isInterface(clazz) || implementTransformedRunnable(clazz, constructors))
    {
      return;
    }

    if (log.isDebugEnabled())
    {
      log.debug("  Creating stack");
    }

    // TODO 2008-09-23 mh: just for classes which contain at least one not static, interruptible method?
    // TODO 2008-09-25 mh: make protected and do not create, when a subclass already has this field?
    clazz.fields.add(new FieldNode(ACC_PRIVATE + ACC_TRANSIENT + ACC_FINAL + ACC_SYNTHETIC, THREAD, THREAD_IMPL_DESC, THREAD_IMPL_DESC, null));
  }
}