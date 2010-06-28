package org.serialthreads.context;

import org.serialthreads.Executor;

/**
 * Simple implementation of a thread manager.
 * Expects that getThread() is never called!
 * Expects that IRunnables are transformed to ITransformedRunnables!
 */
public class FrameExecutionSerialThreadManager extends SerialThreadManager
{
  private final ChainedRunnable[] chains;
  private ChainedRunnable lastExecuted;

  /**
   * Constructor.
   *
   * @param runnables runnables
   */
  public FrameExecutionSerialThreadManager(IRunnable... runnables)
  {
    assert runnables.length > 0 : "Precondition: runnables.length > 0";

    chains = ChainedRunnable.chain(runnables);
    lastExecuted = chains[chains.length - 1];

    // not used in this manager!
    currentThread = null;
  }


  /**
   * Simple serial execution of all runnables.
   */
  @Executor
  public void execute()
  {
    // loop until a chain finishes
    ChainedRunnable chain = lastExecuted;

    // loop until a chain finishes
    ((Stack) chain.thread).first.executor = new InitialFrameExecutor();
    try
    {
      //noinspection InfiniteLoopStatement
      while (true)
      {
        chain = chain.next;
        executeFrame((Stack) chain.thread);
      }
    }
    catch (ThreadFinishedException e)
    {
      // expected: execution finished normally due to the end of a serial thread
      // TODO 2009-12-09 mh: Avoid cast
      ((Stack) chain.thread).reset();
    }

    lastExecuted = chain;
  }

  /**
   * Simple serial execution of all runnables for a given number of interrupts.
   */
  @Executor
  public void execute(int interrupts)
  {
    assert interrupts > 0 : "Precondition: interrupts > 0";

    int loops = interrupts * chains.length;

    // loop until a chain finishes
    ChainedRunnable chain = lastExecuted;

    // loop until a chain finishes
    ((Stack) chain.thread).first.executor = new InitialFrameExecutor();
    try
    {
      do
      {
        chain = chain.next;
        executeFrame((Stack) chain.thread);
      } while (--loops != 0);
    }
    catch (ThreadFinishedException e)
    {
      // expected: execution finished normally due to the end of a serial thread
      // TODO 2009-12-09 mh: Avoid cast
      ((Stack) chain.thread).reset();
    }

    lastExecuted = chain;
  }

  private void executeFrame(Stack thread)
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

    throw new ThreadFinishedException(thread.toString());
  }
}
