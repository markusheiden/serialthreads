package org.serialthreads.transformer.strategies.frequent3;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.MethodNodeCopier;

import java.util.Set;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.serialthreads.transformer.code.MethodCode.firstLocal;
import static org.serialthreads.transformer.code.MethodCode.isNotStatic;
import static org.serialthreads.transformer.code.MethodCode.isNotVoid;
import static org.serialthreads.transformer.code.MethodCode.isRun;
import static org.serialthreads.transformer.code.MethodCode.isSelfCall;
import static org.serialthreads.transformer.code.MethodCode.methodName;
import static org.serialthreads.transformer.code.MethodCode.returnInstructions;
import static org.serialthreads.transformer.code.ValueCodeFactory.code;

/**
 * Base class for method transformers of {@link FrequentInterruptsTransformer3}.
 */
@SuppressWarnings({"UnusedAssignment", "UnusedDeclaration"})
abstract class MethodTransformer extends org.serialthreads.transformer.strategies.AbstractMethodTransformer
{
  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected MethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache)
  {
    super(clazz, method, classInfoCache);
  }

  /**
   * Copies a method and adds it to the class.
   * Its arguments will be shortened by the caller later on.
   *
   * @param clazz class to transform
   * @param method method to transform
   */
  protected static MethodNode copyMethod(ClassNode clazz, MethodNode method)
  {
    MethodNode copy = MethodNodeCopier.copy(method);
    copy.name = changeCopyName(method.name, method.desc);

    clazz.methods.add(copy);

    return copy;
  }

  /**
   * Change the name of a copied method.
   * Computes an unique name based on the name and the descriptor.
   *
   * @param name name of method
   * @param desc parameters
   * @return changed name
   */
  private static String changeCopyName(String name, String desc)
  {
    return name + "$$" + desc.replaceAll("[()\\[/;]", "_") + "$$";
  }

  /**
   * Change the parameters of a copied method to (Thread, Frame).
   *
   * @param desc parameters
   * @return changed parameters
   */
  protected String changeCopyDesc(String desc)
  {
    return "(" + THREAD_IMPL_DESC + FRAME_IMPL_DESC + ")" + Type.VOID_TYPE;
  }

  /**
   * Change parameters of a method.
   * Inserts thread and frame as additional parameters at the end.
   *
   * @param desc parameters
   * @return changed parameters
   */
  protected String changeDesc(String desc)
  {
    int index = desc.indexOf(")");
    return desc.substring(0, index) + THREAD_IMPL_DESC + FRAME_IMPL_DESC + ")" + Type.VOID_TYPE;
  }

  /**
   * Additionally push thread and previous frame arguments onto the stack for all interruptible method calls.
   * Alters the method descriptor to reflect these changes.
   *
   * @param methodCalls interruptible method calls
   */
  protected void addThreadAndFrame(Set<MethodInsnNode> methodCalls)
  {
    InsnList instructions = method.instructions;

    int local = firstLocal(method);
    final int localThread = local++; // param thread
    final int localPreviousFrame = local++; // param previousFrame
    final int localFrame = local++;

    for (MethodInsnNode methodCall : methodCalls)
    {
      if (!isRun(methodCall, classInfoCache) && !classInfoCache.isInterrupt(methodCall))
      {
        instructions.insertBefore(methodCall, new VarInsnNode(ALOAD, localThread));
        instructions.insertBefore(methodCall, new VarInsnNode(ALOAD, localFrame));
        methodCall.desc = changeDesc(methodCall.desc);
      }
    }
  }

  /**
   * Replace returns with void returns.
   * The return value will be captured in the previous frame.
   */
  protected void voidReturns()
  {
    Type returnType = Type.getReturnType(method.desc);
    if (returnType.getSort() == Type.VOID)
    {
      // Method already has return type void
      return;
    }

    InsnList instructions = method.instructions;

    int local = firstLocal(method);
    final int localThread = local++; // param thread
    final int localPreviousFrame = local++; // param previousFrame
    final int localFrame = local++;

    for (AbstractInsnNode returnInstruction : returnInstructions(method))
    {
      instructions.insert(returnInstruction, code(returnType).pushReturnValue(localPreviousFrame));
      instructions.remove(returnInstruction);
    }
  }

  //
  // Capture code inserted after method calls
  //

  @Override
  protected InsnList dummyReturn()
  {
    // Always use void returns, because all methods have been change to use void returns
    InsnList result = new InsnList();
    result.add(new InsnNode(RETURN));
    return result;
  }

  @Override
  protected void createCaptureCodeForMethod(Frame frameBefore, MethodInsnNode methodCall, Frame frameAfter, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner)
  {
    if (log.isDebugEnabled())
    {
      log.debug("      Creating capture code for method call to " + methodName(methodCall));
    }

    int local = firstLocal(method);
    final int localThread = local++; // param thread
    final int localPreviousFrame = local++; // param previousFrame
    final int localFrame = local++;

    LabelNode normal = new LabelNode();

    InsnList capture = new InsnList();

    // if not serializing "GOTO" normal
    capture.add(new VarInsnNode(ALOAD, localThread));
    capture.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    capture.add(new JumpInsnNode(IFEQ, normal));

    // capture frame and return early
    capture.add(pushToFrame(methodCall, frameAfter, localFrame));
    capture.add(pushMethodToFrame(position, containsMoreThanOneMethodCall, suppressOwner, localPreviousFrame, localFrame));
    capture.add(new InsnNode(RETURN));

    // normal execution
    capture.add(normal);
    // restore return value of call, if any
    if (isNotVoid(methodCall))
    {
      capture.add(code(Type.getReturnType(methodCall.desc)).popReturnValue(localFrame));
    }

    // insert capture code
    method.instructions.insert(methodCall, capture);
  }

  @Override
  protected InsnList popReturnValue(MethodInsnNode methodCall)
  {
    // There is no return value, because all method have been change to void returns
    return new InsnList();
  }

  //
  // Restore code to be able to resume a method call
  //

  @Override
  protected InsnList createRestoreCodeForMethod(Frame frameBefore, MethodInsnNode methodCall, Frame frameAfter)
  {
    if (log.isDebugEnabled())
    {
      log.debug("      Creating restore code for method call to " + methodName(methodCall));
    }

    MethodInsnNode clonedCall = copyMethodCall(methodCall);

    int local = firstLocal(method);
    final int localThread = local++; // param thread
    final int localPreviousFrame = local++; // param previousFrame
    final int localFrame = local++;
    final int localReturnValue = method.maxLocals;

    // label "normal" points the code directly after the method call
    LabelNode normal = new LabelNode();
    method.instructions.insert(methodCall, normal);
    LabelNode restoreFrame = new LabelNode();

    InsnList restore = new InsnList();

    // call interrupted method
    if (isSelfCall(methodCall, frameBefore))
    {
      // self call: owner == this
      restore.add(new VarInsnNode(ALOAD, 0));
    }
    else if (isNotStatic(clonedCall))
    {
      // get owner
      restore.add(new VarInsnNode(ALOAD, localFrame));
      restore.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
      restore.add(new TypeInsnNode(CHECKCAST, clonedCall.owner));
    }

    // jump to cloned method call with thread and frame as arguments
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new VarInsnNode(ALOAD, localFrame));
    restore.add(clonedCall);

    // if not serializing "GOTO" normal, but restore the frame first
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    restore.add(new JumpInsnNode(IFEQ, restoreFrame));

    // early return, the frame already has been captured
    restore.add(new InsnNode(RETURN));

    // restore frame to be able to resume normal execution of method
    restore.add(restoreFrame);

    // restore stack "under" the returned value, if any
    restore.add(popFromFrame(methodCall, frameAfter, localFrame));
    if (isNotVoid(methodCall))
    {
      restore.add(code(Type.getReturnType(methodCall.desc)).popReturnValue(localFrame));
    }
    restore.add(new JumpInsnNode(GOTO, normal));

    return restore;
  }

  /**
   * Copies method call and changes the signature.
   *
   * @param methodCall method call
   */
  private MethodInsnNode copyMethodCall(MethodInsnNode methodCall)
  {
    MethodInsnNode result = (MethodInsnNode) methodCall.clone(null);
    result.name = changeCopyName(methodCall.name, methodCall.desc);
    result.desc = changeCopyDesc(methodCall.desc);

    return result;
  }

  //
  //
  //

  /**
   * Fix maxs of method.
   * These have not to be exact (but may not be too small!), because it is just for debugging purposes.
   */
  protected void fixMaxs()
  {
    method.maxLocals += 1;
    // TODO 2009-10-11 mh: recalculate minimum maxs
    method.maxStack = Math.max(method.maxStack + 2, 5);
  }
}
