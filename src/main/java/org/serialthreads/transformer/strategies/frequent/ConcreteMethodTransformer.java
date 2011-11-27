package org.serialthreads.transformer.strategies.frequent;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.LocalVariablesShifter;
import org.serialthreads.transformer.strategies.MethodNeedsNoTransformationException;

import java.util.List;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.serialthreads.transformer.code.MethodCode.firstLocal;

/**
 * Method transformer for concrete methods.
 */
@SuppressWarnings({"UnusedAssignment"})
class ConcreteMethodTransformer extends MethodTransformer
{
  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected ConcreteMethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache)
  {
    super(clazz, method, classInfoCache);
  }

  /**
   * Transform method.
   *
   * @return Transformed method
   * @exception AnalyzerException In case of incorrect byte code of the original method
   * @exception MethodNeedsNoTransformationException In case the method needs no byte code transformation
   */
  public MethodNode transform() throws AnalyzerException, MethodNeedsNoTransformationException
  {
    LocalVariablesShifter.shift(firstLocal(method), 3, method);
    Frame[] frames = analyze();

    List<InsnList> restoreCodes = insertCaptureCode(frames, interruptibleMethodCalls(), false);
    createRestoreHandlerMethod(restoreCodes);
    fixMaxs();

    return method;
  }

  /**
   * Insert frame restoring code at the begin of an interruptible method.
   *
   * @param restoreCodes restore codes for all method calls in the method
   */
  private void createRestoreHandlerMethod(List<InsnList> restoreCodes)
  {
    assert !restoreCodes.isEmpty() : "Precondition: !restoreCodes.isEmpty()";

    if (log.isDebugEnabled())
    {
      log.debug("    Creating restore handler for method");
    }

    int local = firstLocal(method);
    final int localThread = local++;
    final int localPreviousFrame = local++;
    final int localFrame = local++;

    LabelNode normal = new LabelNode();
    LabelNode getThread = new LabelNode();

    // previousFrame = thread.frame;
    InsnList getFrame = new InsnList();
    getFrame.add(new VarInsnNode(ALOAD, localThread));
    getFrame.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));
    getFrame.add(new VarInsnNode(ASTORE, localPreviousFrame));

    InsnList restore = new InsnList();

    // frame = previousFrame.next;
    getFrame.add(new VarInsnNode(ALOAD, localPreviousFrame));
    getFrame.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
    getFrame.add(new InsnNode(DUP));
    getFrame.add(new JumpInsnNode(IFNONNULL, normal));

    getFrame.add(new InsnNode(POP));
    // frame = thread.addFrame(previousFrame);
    getFrame.add(new VarInsnNode(ALOAD, localThread));
    getFrame.add(new VarInsnNode(ALOAD, localPreviousFrame));
    getFrame.add(new MethodInsnNode(INVOKEVIRTUAL, THREAD_IMPL_NAME, "addFrame", "(" + FRAME_IMPL_DESC + ")" + FRAME_IMPL_DESC));

    getFrame.add(normal);
    getFrame.add(new VarInsnNode(ASTORE, localFrame));

    // TODO 2009-11-26 mh: remove me?
    // thread.frame = frame;
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new VarInsnNode(ALOAD, localFrame));
    restore.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));

    // if not serializing "GOTO" normal
    restore.add(new VarInsnNode(ALOAD, localThread));
    restore.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    restore.add(new JumpInsnNode(IFEQ, getThread));

    // else restore code dispatcher
    InsnList getMethod = new InsnList();
    getMethod.add(new VarInsnNode(ALOAD, localFrame));
    getMethod.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "method", "I"));
    restore.add(restoreCodeDispatcher(getMethod, restoreCodes, 0));

    // insert label for normal body of method
    restore.add(getThread);

    // insert generated byte code
    insertMethodGetThreadStartCode(localThread, getFrame, restore);
  }
}
