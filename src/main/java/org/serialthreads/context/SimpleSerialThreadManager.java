package org.serialthreads.context;

import org.serialthreads.Executor;

/**
 * Simple implementation of a thread manager.
 */
public class SimpleSerialThreadManager extends SerialThreadManager {
  private final ChainedRunnable[] chains;
  private ChainedRunnable lastExecuted;

  /**
   * Constructor.
   *
   * @param runnables runnables
   */
  public SimpleSerialThreadManager(IRunnable... runnables) {
    assert runnables.length > 0 : "Precondition: runnables.length > 0";

    chains = ChainedRunnable.chain(runnables);
    lastExecuted = chains[chains.length - 1];

    registerManager(this);
  }

  /**
   * Simple serial execution of all runnables.
   */
  @Override
  @Executor
  public void execute() {
    // loop until a chain finishes
    ChainedRunnable chain = lastExecuted;
    try {
      //noinspection InfiniteLoopStatement
      for (;;) {
        chain = chain.next;
        currentThread = chain.thread;
        chain.runnable.run();
      }
    } catch (ThreadFinishedException e) {
      // expected: execution finished normally due to the end of a serial thread
      // TODO 2009-12-09 mh: Avoid cast
      ((Stack) chain.runnable.getThread()).reset();
    }

    lastExecuted = chain;
  }

  /**
   * Simple serial execution of all runnables for a given number of interrupts.
   */
  @Override
  @Executor
  public void execute(int interrupts) {
    assert interrupts > 0 : "Precondition: interrupts > 0";

    int loops = interrupts * chains.length;

    // loop until a chain finishes
    ChainedRunnable chain = lastExecuted;
    try {
      do {
        chain = chain.next;
        currentThread = chain.thread;
        chain.runnable.run();
      } while (--loops != 0);

      // execution finished, because the given number of interrupts have been processed
    } catch (ThreadFinishedException e) {
      // expected: execution finished normally due to the end of a serial thread
      // TODO 2009-12-09 mh: Avoid cast
      ((Stack) chain.runnable.getThread()).reset();
    }

    lastExecuted = chain;
  }
}
