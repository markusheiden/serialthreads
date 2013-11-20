package org.serialthreads.context;

import java.io.Serializable;

/**
 * Stack for all frames of a thread.
 */
public class Stack extends SerialThread implements Serializable {
  /**
   * Initial size of the stack.
   */
  protected static final int MAX_LEVELS = 256;

  /**
   * Default frame size.
   */
  private final int frameSize;

  /**
   * Frame for the first method.
   */
  public final StackFrame first;

  /**
   * The currently active frame.
   */
  public StackFrame frame;

  // Return value of the last executed method
  public Object returnObject;
  public int returnInt;
  public long returnLong;
  public float returnFloat;
  public double returnDouble;

  /**
   * Constructor.
   *
   * @param name name of the thread
   * @param frameSize size of frames
   */
  public Stack(String name, int frameSize) {
    super(name);

    this.frameSize = frameSize;
    first = new StackFrame(null, frameSize);
    frame = first;

    returnObject = null;
  }

  /**
   * Increase the stack by one frame.
   *
   * @param lastFrame last frame of the stack
   * @return Added frame
   */
  public StackFrame addFrame(StackFrame lastFrame) {
    return new StackFrame(lastFrame, frameSize);
  }

  /**
   * Enter a method.
   *
   * @return the frame for the method
   */
  public final StackFrame enterMethod() {
    return frame = frame.next;
  }

  /**
   * Enter the first method (IRunnable.run()).
   * Resets the current frame to the first.
   *
   * @return the frame for the method
   */
  public final StackFrame enterFirstMethod() {
    return frame = first;
  }

  /**
   * Leave non-static method, and the caller contains more than one interruptible method call.
   *
   * @param owner owner of the called method == this in the called method. for the frame one level above
   * @param method index of method which will be left
   */
  public final void leaveMethod(Object owner, int method) {
    frame.method = method;
    frame.previous.owner = owner;
  }

  /**
   * Leave non-static method, and the caller contains exactly one interruptible method call.
   *
   * @param owner owner of the called method == this in the called method. for the frame one level above
   */
  public final void leaveMethod(Object owner) {
    frame.previous.owner = owner;
  }

  /**
   * Leave static method, and the caller contains more than one interruptible method call.
   *
   * @param method index of method which will be left
   */
  public final void leaveMethod(int method) {
    frame.method = method;
  }

  /**
   * Resets all frames below this.
   * Needed after an exception has been thrown to clean up the stack.
   *
   * @param resetTo frame to reset to
   */
  public final void resetTo(StackFrame resetTo) {
    StackFrame frame = resetTo.next;
    while (frame != null) {
      frame.reset();
      frame = frame.next;
    }
  }

  /**
   * Resets the complete stack.
   */
  public final void reset() {
    resetTo(first);
  }
}
