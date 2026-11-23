package org.serialthreads.context;

import org.serialthreads.Executor;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;

/**
 * Simple implementation of a thread manager.
 * Expects that getThread() is never called!
 * Expects that IRunnables are transformed to ITransformedRunnables!
 */
public class FrameExecutionSerialThreadManager extends SerialThreadManager {
  private final ChainedRunnable[] chains;
  private ChainedRunnable lastExecuted;

  /**
   * Constructor.
   *
   * @param runnables Runnables.
   */
  public FrameExecutionSerialThreadManager(IRunnable... runnables) {
    requireThat(runnables, "runnables").isNotEmpty();

    chains = ChainedRunnable.chain(runnables);
    lastExecuted = chains[chains.length - 1];

    // Not used in this manager!
    currentThread = null;
  }


  /**
   * Simple serial execution of all runnables.
   */
  @Executor
  public void execute() {
    // Loop until a chain finishes.
    ChainedRunnable chain = lastExecuted;

    // Loop until a chain finishes.
    ((Stack) chain.thread).first.executor = new InitialFrameExecutor();
    try {
      //noinspection InfiniteLoopStatement
      while (true) {
        chain = chain.next;
        executeFrame((Stack) chain.thread);
      }
    } catch (ThreadFinishedException e) {
      // Expected: execution finished normally due to the end of a serial thread.
      // TODO 2009-12-09 mh: Avoid cast.
      ((Stack) chain.thread).reset();
    }

    lastExecuted = chain;
  }

  /**
   * Simple serial execution of all runnables for a given number of interrupts.
   */
  @Executor
  public void execute(int interrupts) {
    requireThat(interrupts, "interrupts").isPositive();

    int loops = interrupts * chains.length;

    // Loop until a chain finishes.
    ChainedRunnable chain = lastExecuted;

    // Loop until a chain finishes.
    ((Stack) chain.thread).first.executor = new InitialFrameExecutor();
    try {
      do {
        chain = chain.next;
        executeFrame((Stack) chain.thread);
      } while (--loops != 0);
    } catch (ThreadFinishedException e) {
      // Expected: execution finished normally due to the end of a serial thread.
      // TODO 2009-12-09 mh: Avoid cast.
      ((Stack) chain.thread).reset();
    }

    lastExecuted = chain;
  }

  private void executeFrame(Stack thread) {
    StackFrame frame = thread.frame;
    do {
      frame.executor.executeFrame(thread, frame);
      if (thread.serializing) {
        // Thread has been interrupted again, so exit run() for now.
        return;
      }
      frame = frame.previous;
    } while (frame != null);
    // TODO 2010-02-02 mh: Rework of stack chain needed to hold frame != null ending condition?

    throw new ThreadFinishedException(thread.toString());
  }
}
