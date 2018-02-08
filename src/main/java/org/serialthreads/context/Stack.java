package org.serialthreads.context;

/**
 * Stack for all frames of a thread.
 */
public final class Stack implements SerialThread {
  /**
   * Name.
   */
  private final String name;

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

  /**
   * Is the thread in the capturing or restoring phase?.
   */
  public boolean serializing;

  /**
   * Return value of the last executed method: Object.
   */
  public Object returnObject;

  /**
   * Return value of the last executed method: int.
   */
  public int returnInt;

  /**
   * Return value of the last executed method: long.
   */
  public long returnLong;

  /**
   * Return value of the last executed method: float.
   */
  public float returnFloat;

  /**
   * Return value of the last executed method: double.
   */
  public double returnDouble;

  /**
   * Constructor.
   *
   * @param runnable Runnable.
   * @param frameSize Default size of frames
   */
  public Stack(Object runnable, int frameSize) {
    this.name = runnable.getClass().getSimpleName();
    this.frameSize = frameSize;
    first = new StackFrame(this, null, frameSize);
    frame = first;

    serializing = false;
    returnObject = null;
    returnInt = 0;
    returnLong = 0;
    returnFloat = 0;
    returnDouble = 0;
  }

  @Override
  public String getName() {
    return name;
  }

  /**
   * Increase the stack by one frame.
   *
   * @param lastFrame Last frame of the stack
   * @return Added frame
   */
  public StackFrame addFrame(StackFrame lastFrame) {
    return new StackFrame(this, lastFrame, frameSize);
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
   * @param owner Owner of the called method == this in the called method. for the frame one level above
   * @param method Index of method which will be left
   */
  public final void leaveMethod(Object owner, int method) {
    frame.method = method;
    frame.previous.owner = owner;
  }

  /**
   * Leave non-static method, and the caller contains exactly one interruptible method call.
   *
   * @param owner Owner of the called method == this in the called method. for the frame one level above
   */
  public final void leaveMethod(Object owner) {
    frame.previous.owner = owner;
  }

  /**
   * Leave static method, and the caller contains more than one interruptible method call.
   *
   * @param method Index of method which will be left
   */
  public final void leaveMethod(int method) {
    frame.method = method;
  }

  /**
   * Resets all frames below this.
   * Needed after an exception has been thrown to clean up the stack.
   *
   * @param resetTo Frame to reset to
   */
  public final void resetTo(StackFrame resetTo) {
    for (StackFrame frame = resetTo.next; frame != null; frame = frame.next) {
      frame.reset();
    }
  }

  /**
   * Resets the complete stack.
   */
  public final void reset() {
    resetTo(first);
  }

  @Override
  public String toString() {
    return name;
  }
}
