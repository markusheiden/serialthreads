package org.serialthreads.context;

import org.serialthreads.Executor;

/**
 * Sandbox for single frame execution.
 */
public class SFESandbox implements IRunnable, IFrameExecutor
{
  private final Stack $$thread$$ = new Stack("test", 10);

  @Executor
  public void execute()
  {
    // loop until a chain finishes
    Stack thread = null;
    thread.first.executor = new InitialFrameExecutor();
    try
    {
      //noinspection InfiniteLoopStatement
      while (true)
      {
        // thread = thread.next;
        executeFrame(thread);
      }
    }
    catch (ThreadFinishedException e)
    {
      // expected: execution finished normally due to the end of a serial thread
    }
  }

  public void executeFrame(Stack thread)
  {
    StackFrame frame = thread.frame;
    do
    {
      frame.executor.executeFrame(thread, frame);
      if (thread.serializing)
      {
        // thread has been interrupted again, so exit run() for now
        return;
      }
      frame = frame.previous;
    } while (frame != null);
    // TODO 2010-02-02 mh: rework of stack chain needed to hold frame != null ending condition?

    thread.reset();
    throw new ThreadFinishedException(thread.toString());
  }

  public void run()
  {
    Stack thread = $$thread$$;
    StackFrame frame = thread.frame;
    do
    {
      frame.executor.executeFrame(thread, frame);
      if (thread.serializing)
      {
        // thread has been interrupted again, so exit run() for now
        return;
      }
      frame = frame.previous;
    } while (frame != null);
    // TODO 2010-02-02 mh: reworked of stack chain needed to hold frame != null ending condition?

    throw new ThreadFinishedException(toString());
  }

  @Override
  public void executeFrame(Stack thread, StackFrame frame)
  {
    ((TargetClass) frame.owner).copiedMethod(thread, frame);
  }

  private static class TargetClass
  {
    public void copiedMethod(Stack thread, StackFrame frame)
    {
    }
  }
}
