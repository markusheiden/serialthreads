package org.serialthreads.transformer.strategies;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.serialthreads.context.StackFrame;

/**
 * Capture and restore of stack frames.
 */
public interface StackCode {
  /**
   * Get first frame of the stack and store in a local.
   *
   * @param localThread
   *           Number of local containing the thread.
   * @param localFrame
   *           Number of local that will hold the current frame.
   * @return generated code.
   */
  InsnList firstFrame(int localThread, int localFrame);

  /**
   * Get next frame from the current frame.
   * Use {@link StackFrame#addFrame()} to add a new one, if no next frame is present.
   *
   * @param localPreviousFrame
   *           Number of local containing the previous frame .
   * @return generated code.
   */
  InsnList nextFrame(int localPreviousFrame);

  /**
   * Push "this" as owner to the previous frame.
   *
   * @param localPreviousFrame
   *           Number of local containing the previous frame .
   * @return Generated capture code.
   */
  InsnList pushOwner(int localPreviousFrame);

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
   * @return Generated capture code.
   */
  InsnList pushToFrame(MethodNode method, MethodInsnNode methodCall, MetaInfo metaInfo, int localFrame);

  /**
   * Reset method in frame.
   *
   * @param localFrame
   *           number of local containing the current frame.
   * @return generated code.
   */
  InsnList resetMethod(int localFrame);

  /**
   * Push method (index) onto frame.
   *
   * @param position
   *           position of method call.
   * @param localFrame
   *           number of local containing the current frame.
   * @return generated capture code.
   */
  InsnList pushMethod(int position, int localFrame);

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
   * @param localFrame
   *           number of local containing the frame.
   * @return generated restore code.
   */
  InsnList popOwner(int localFrame);

  /**
   * Restore method (index) from frame.
   *
   * @param localFrame
   *           number of local containing the current frame.
   * @return generated restore code.
   */
  InsnList popMethod(int localFrame);

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
