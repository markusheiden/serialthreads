package org.serialthreads.context;

/**
 * Stack for all frames of a thread.
 */
public class Stack extends SerialThread
{
  /**
   * Initial size of the stack.
   */
  protected static final int MAX_LEVELS = 256;

  /**
   * The currently active frame.
   */
  public StackFrame frame;

  /**
   * Frame for the first method.
   */
  public final StackFrame first;

  /**
   * Constructor.
   *
   * @param name name of the thread
   * @param size size of frames
   */
  public Stack(String name, int size)
  {
    super(name);

    first = new StackFrame(null, size);
    fillStack(first, size);
    frame = first;
  }

  private void fillStack(StackFrame last, int size)
  {
    StackFrame previous = last;
    for (int i = 0; i < MAX_LEVELS; i++)
    {
      previous = new StackFrame(previous, size);
    }
  }

  /**
   * Enter a method.
   *
   * @return the frame for the method
   */
  public final StackFrame enterMethod()
  {
    return frame = frame.next;
  }

  /**
   * Enter the first method (IRunnable.run()).
   * Resets the current frame to the first.
   *
   * @return the frame for the method
   */
  public final StackFrame enterFirstMethod()
  {
    return frame = first;
  }

  /**
   * Leave non-static method, and the caller contains more than one interruptible method call.
   *
   * @param owner owner of the called method == this in the called method. for the frame one level above
   * @param method index of method which will be left
   */
  public final void leaveMethod(Object owner, int method)
  {
    frame.method = method;
    frame.previous.owner = owner;
  }

  /**
   * Leave non-static method, and the caller contains exactly one interruptible method call.
   *
   * @param owner owner of the called method == this in the called method. for the frame one level above
   */
  public final void leaveMethod(Object owner)
  {
    frame.previous.owner = owner;
  }

  /**
   * Leave static method, and the caller contains more than one interruptible method call.
   *
   * @param method index of method which will be left
   */
  public final void leaveMethod(int method)
  {
    frame.method = method;
  }

  /**
   * Resets all frames below this.
   * Needed after an exception has been thrown to clean up the stack.
   *
   * @param resetTo frame to reset to
   */
  public final void resetTo(StackFrame resetTo)
  {
    StackFrame frame = resetTo.next;
    while (frame != null)
    {
      frame.reset();
      frame = frame.next;
    }
  }

  /**
   * Resets the complete stack.
   */
  public final void reset()
  {
    resetTo(first);
  }
}
