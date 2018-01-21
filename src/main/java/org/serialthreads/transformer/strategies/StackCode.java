package org.serialthreads.transformer.strategies;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.serialthreads.context.Stack;
import org.serialthreads.context.StackFrame;

/**
 * Capture and restore of stack frames.
 */
public interface StackCode {
  //
  // run() methods.
  //

  /**
   * Get first frame of the stack and store in a local.
   *
   * @param localThread
   *           Number of local containing the thread.
   * @param localFrame
   *           Number of local that will hold the current frame.
   * @return Generated code.
   */
  InsnList firstFrame(int localThread, int localFrame);

  /**
   * Reset method in frame.
   *
   * @param localFrame
   *           number of local containing the current frame.
   * @return Generated code.
   */
  InsnList resetMethod(int localFrame);

  //
  // Capture.
  //

  /**
   * Get next frame from the current frame.
   * Uses {@link StackFrame#addFrame()} to add a new frame, if no next frame is present.
   *
   * @param localPreviousFrame
   *           Number of local containing the previous frame.
   * @param localFrame
   *           Number of local that will hold the current frame.
   * @return Generated code.
   */
  InsnList nextFrame(int localPreviousFrame, int localFrame);

  /**
   * Push "this" as owner to the previous frame.
   *
   * @param localPreviousFrame
   *           Number of local containing the previous frame .
   * @return Generated code.
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
   * @return Generated code.
   */
  InsnList pushToFrame(MethodNode method, MethodInsnNode methodCall, MetaInfo metaInfo, int localFrame);

  /**
   * Push method (index) onto frame.
   *
   * @param localFrame
   *           number of local containing the current frame.
   * @param position
   *           position of method call.
   * @return Generated code.
   */
  InsnList pushMethod(int localFrame, int position);

  /**
   * Start serializing at interrupt.
   *
   * @param localThread
   *           Number of local containing the thread.
   * @return Generated code.
   */
  InsnList startSerializing(int localThread);

  //
  // Restore.
  //

  /**
   * Get (previous) frame from {@link Stack#frame} and store it to local #localPreviousFrame.
   * <p/>
   * Needed just for transformers not passing a {@link StackFrame} as parameter.
   *
   * @param localThread
   *           Number of local containing the thread.
   * @param localPreviousFrame
   *           Number of local containing the previous frame.
   * @return Generated code.
   */
  InsnList getPreviousFrame(int localThread, int localPreviousFrame);

  /**
   * Set frame to {@link Stack#frame}.
   * <p/>
   * Needed just for transformers not passing a {@link StackFrame} as parameter.
   *
   * @param localThread
   *           Number of local containing the thread.
   * @param localFrame
   *           number of local containing the frame.
   * @return Generated code.
   */
  InsnList setFrame(int localThread, int localFrame);

  /**
   * Stop de-serializing when interrupt location has been reached.
   *
   * @param localThread
   *           Number of local containing the thread.
   * @return Generated code.
   */
  InsnList stopDeserializing(int localThread);

  /**
   * Restore owner from frame.
   *
   * @param localFrame
   *           number of local containing the frame.
   * @return Generated code.
   */
  InsnList popOwner(int localFrame);

  /**
   * Restore method (index) from frame.
   *
   * @param localFrame
   *           number of local containing the current frame.
   * @return Generated code.
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
   * @return Generated code.
   */
  InsnList popFromFrame(MethodNode method, MethodInsnNode methodCall, MetaInfo metaInfo, int localFrame);
}
