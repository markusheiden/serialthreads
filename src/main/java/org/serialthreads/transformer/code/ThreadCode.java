package org.serialthreads.transformer.code;

import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.serialthreads.context.Stack;
import org.serialthreads.context.StackFrame;
import org.serialthreads.transformer.strategies.MetaInfo;

/**
 * Capture and restore of stack frames.
 */
public interface ThreadCode {
  //
  // Constructors.
  //

  /**
   * Create field for holding the associated thread.
   */
  FieldNode threadField();

  /**
   * Set "this.$$thread$$".
   *
   * @param className
   *           Class of this.
   * @return Generated code.
   */
  InsnList setThread(String className);

  /**
   * Push "this.$$thread$$" onto stack.
   *
   * @param className
   *           Class of this.
   * @return Generated code.
   */
  InsnList pushThread(String className);

  /**
   * Push "frame.stack" onto stack.
   *
   * @param localFrame
   *           Local with frame.
   * @return Generated code.
   */
  InsnList pushThread(int localFrame);

  /**
   * Push new {@link Stack} and onto stack.
   *
   * @param defaultFrameSize
   *           Default size of frames.
   * @return Generated code.
   */
  InsnList pushNewStack(int defaultFrameSize);

  //
  // Capture.
  //

  /**
   * Get {@link StackFrame#next} from the current frame and store it in a local.
   * Uses {@link StackFrame#addFrame()} to add a new frame, if no next frame is present.
   *
   * @param localPreviousFrame
   *           Number of local containing the previous frame.
   * @param localFrame
   *           Number of local that will hold the current frame.
   * @param addIfNotPresent
   *           Check if next frame is present and add a new one if not?.
   * @return Generated code.
   */
  InsnList getNextFrame(int localPreviousFrame, int localFrame, boolean addIfNotPresent);

  /**
   * Set "this" as {@link StackFrame#owner} into the previous frame.
   *
   * @param localPreviousFrame
   *           Number of local containing the previous frame .
   * @return Generated code.
   */
  InsnList setOwner(int localPreviousFrame);

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
  InsnList captureFrame(MethodNode method, MethodInsnNode methodCall, MetaInfo metaInfo, int localFrame);

  /**
   * Set position as {@link StackFrame#method}.
   *
   * @param localFrame
   *           number of local containing the current frame.
   * @param position
   *           position of method call.
   * @return Generated code.
   */
  InsnList setMethod(int localFrame, int position);

  /**
   * Set {@link Stack#serializing}.
   *
   * @param localThread
   *           Number of local containing the thread.
   * @param serializing
   *           Start serializing at interrupt (true) or
   *           Stop de-serializing when interrupt location has been reached (false).
   * @return Generated code.
   */
  InsnList setSerializing(int localThread, boolean serializing);

  //
  // Restore.
  //

  /**
   * Push {@link Stack#serializing} onto stack.
   *
   * @param localThread
   *           Number of local containing the thread.
   * @return Generated code.
   */
  InsnList pushSerializing(int localThread);

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
   * Push {@link StackFrame#owner} onto stack.
   *
   * @param localFrame
   *           number of local containing the frame.
   * @return Generated code.
   */
  InsnList pushOwner(int localFrame);

  /**
   * Push {@link StackFrame#method} onto stack.
   *
   * @param localFrame
   *           number of local containing the current frame.
   * @return Generated code.
   */
  InsnList pushMethod(int localFrame);

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
  InsnList restoreFrame(MethodNode method, MethodInsnNode methodCall, MetaInfo metaInfo, int localFrame);
}
