package org.serialthreads.transformer.strategies;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.serialthreads.context.Stack;

/**
 * Capture and restore of stack frames.
 */
public interface StackFrameCode {
  /**
   * Save current frameAfter after returning from a method call.
   *
   * @param method
   *           Method to capture.
   * @param methodCall
   *           method call to process.
   * @param metaInfo
   *           Meta information about method call.
   * @param localFrame
   *           number of local containing the frameAfter.
   * @return generated capture code.
   */
  InsnList pushToFrame(MethodNode method, MethodInsnNode methodCall, MetaInfo metaInfo, int localFrame);

  /**
   * Push method and owner onto frame.
   *
   * @param position
   *           position of method call.
   * @param containsMoreThanOneMethodCall
   *           contains the method more than one method call?.
   * @param localFrame
   *           number of local containing the current frame.
   * @return generated capture code.
   */
  InsnList pushMethodToFrame(int position, boolean containsMoreThanOneMethodCall, int localFrame);

  /**
   * Push method and owner onto frame with a given method.
   *
   * Currently not used.
   * @see Stack#leaveMethod(Object, int) etc.
   *
   * @param method
   *           Method to capture.
   * @param position
   *           position of method call
   * @param containsMoreThanOneMethodCall
   *           contains the method more than one method call?
   * @param methodName
   *           name of method to store owner and method
   * @param localThread
   *           number of local containing the thread
   * @return generated capture code
   */
  InsnList pushMethodToFrame(MethodNode method, int position, boolean containsMoreThanOneMethodCall, boolean suppressOwner, String methodName, int localThread);

  /**
   * Push owner onto frame.
   *
   * @param method
   *           Method to capture.
   * @param suppressOwner
   *           suppress saving the owner?.
   * @param localPreviousFrame
   *           number of local containing the previous frame or -1 for retrieving it via current frame.
   * @param localFrame
   *           number of local containing the current frame.
   * @return generated capture code.
   */
  InsnList pushOwnerToFrame(MethodNode method, boolean suppressOwner, int localPreviousFrame, int localFrame);

  /**
   * Restore owner.
   *
   * @param methodCall
   *           method call to process.
   * @param metaInfo
   *           Meta information about method call.
   * @param localFrame
   *           number of local containing the frame.
   * @return generated restore code.
   */
  InsnList popOwnerFromFrame(MethodInsnNode methodCall, MetaInfo metaInfo, int localFrame);

  /**
   * Restore current frame before resuming the method call
   *
   * @param method
   *           Method to restore.
   * @param methodCall
   *           method call to process.
   * @param metaInfo
   *           Meta information about method call.
   * @param localFrame
   *           number of local containing the frame.
   * @return generated restore code.
   */
  InsnList popFromFrame(MethodNode method, MethodInsnNode methodCall, MetaInfo metaInfo, int localFrame);
}
