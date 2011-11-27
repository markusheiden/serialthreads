package org.serialthreads.transformer;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import static org.serialthreads.transformer.code.MethodCode.isRun;

/**
 * Checks not interruptable method.
 */
public class NotInterruptableMethodChecker extends AbstractMethodTransformer
{
  /**
   * Constructor.
   *
   * @param clazz
   * @param method
   * @param classInfoCache
   */
  protected NotInterruptableMethodChecker(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache)
  {
    super(clazz, method, classInfoCache);
  }

  /**
   * Checks not interruptible method to contain no calls of interruptible methods.
   */
  public void check()
  {
    AbstractInsnNode[] instructions = method.instructions.toArray();
    for (AbstractInsnNode instruction : instructions)
    {
      if (instruction.getType() == AbstractInsnNode.METHOD_INSN)
      {
        MethodInsnNode methodCall = (MethodInsnNode) instruction;

        if (!isInterruptible(methodCall))
        {
          // nothing to check on not interruptible methods
          continue;
        }

        if (!isExecutor(clazz, method))
        {
          throw new NotTransformableException(
            "Not interruptible method " + org.serialthreads.transformer.code.MethodCode.methodName(clazz, method) +
              " calls interruptible method " + org.serialthreads.transformer.code.MethodCode.methodName(methodCall));
        }
        else if (!isRun(methodCall, classInfoCache))
        {
          throw new NotTransformableException(
            "Executor " + org.serialthreads.transformer.code.MethodCode.methodName(clazz, method) +
              " may only call run, but called " + org.serialthreads.transformer.code.MethodCode.methodName(methodCall));
        }
      }
    }
  }

  /**
   * Check, if method is an executor.
   *
   * @param clazz owner of method
   * @param method method
   */
  protected boolean isExecutor(ClassNode clazz, MethodNode method)
  {
    return classInfoCache.isExecutor(clazz, method);
  }

  @Override
  protected void createCaptureCodeForMethod(Frame frameBefore, MethodInsnNode methodCall, Frame frameAfter, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner)
  {
    throw new UnsupportedOperationException("FIXME");
  }

  @Override
  protected InsnList createRestoreCodeForMethod(Frame frameBefore, MethodInsnNode methodCall, Frame frameAfter)
  {
    throw new UnsupportedOperationException("FIXME");
  }
}
