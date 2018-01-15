package org.serialthreads.transformer.strategies;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

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
   * Push owner onto frame.
   *
   * @param method
   *           Method to capture.
   * @param methodCall
   *           method call to process.
   * @param metaInfo
   *           Meta information about method call.
   * @param localPreviousFrame
   *           number of local containing the previous frame or -1 for retrieving it via current frame.
   * @param localFrame
   *           number of local containing the current frame.
   * @return generated capture code.
   */
  InsnList pushOwnerToFrame(MethodNode method, MethodInsnNode methodCall, MetaInfo metaInfo, int localPreviousFrame, int localFrame);

  /**
   * Start serializing at interrupt.
   */
  InsnList startSerializing(int localThread);

  /**
   * Stop de-serializing when interrupt location has been reached.
   */
  InsnList stopDeserializing(int localThread);

  /**
   * Restore owner from frame.
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
   * Restore method from frame.
   *
   * @param localFrame
   *           number of local containing the current frame.
   * @return generated restore code.
   */
  InsnList popMethodFromFrame(int localFrame);

  /**
   * Restore current frame before resuming the method call.
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
