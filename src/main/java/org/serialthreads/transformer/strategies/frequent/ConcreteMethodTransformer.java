package org.serialthreads.transformer.strategies.frequent;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.util.List;

import static org.objectweb.asm.Opcodes.IFEQ;

/**
 * Method transformer for concrete methods.
 */
@SuppressWarnings({"UnusedAssignment"})
class ConcreteMethodTransformer extends MethodTransformer {
  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected ConcreteMethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) {
    super(clazz, method, classInfoCache);
  }

  /**
   * Transform method.
   *
   * @return Transformed method
   * @throws AnalyzerException In case of incorrect byte code of the original method
   */
  public MethodNode transform() throws AnalyzerException {
    shiftLocals();
    nameAddedLocals();
    analyze();

    List<LabelNode> restores = insertCaptureAndRestoreCode(false);
    createRestoreHandlerMethod(restores);
    fixMaxs();

    return method;
  }

  /**
   * Insert frame restoring code at the begin of an interruptible method.
   *
   * @param restores Labels pointing to the generated restore codes for method calls.
   */
  private void createRestoreHandlerMethod(List<LabelNode> restores) {
    assert !restores.isEmpty() : "Precondition: !restores.isEmpty()";

    logger.debug("    Creating restore handler for method");

    final int localThread = localThread();
    final int localPreviousFrame = localPreviousFrame();
    final int localFrame = localFrame();

    LabelNode getThread = new LabelNode();

    InsnList getFrame = new InsnList();
    // previousFrame = thread.frame;
    getFrame.add(threadCode.getPreviousFrame(localThread, localPreviousFrame));
    // frame = previousFrame.next; // etc.
    getFrame.add(threadCode.getNextFrame(localPreviousFrame, localFrame, true));

    // TODO 2009-11-26 mh: remove me?
    InsnList restoreCode = new InsnList();
    // thread.frame = frame;
    restoreCode.add(threadCode.setFrame(localThread, localFrame));

    // if (!thread.serializing) "GOTO" normal.
    restoreCode.add(threadCode.pushSerializing(localThread));
    restoreCode.add(new JumpInsnNode(IFEQ, getThread));

    // else restore code dispatcher
    restoreCode.add(restoreCodeDispatcher(pushMethod(), restores, 0));

    // insert label for normal body of method
    restoreCode.add(getThread);

    // insert generated byte code
    insertMethodGetThreadStartCode(localThread, getFrame, restoreCode);
  }
}
