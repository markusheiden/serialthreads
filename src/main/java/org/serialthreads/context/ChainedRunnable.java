package org.serialthreads.context;

/**
 * Linked list element for runnables.
 */
public final class ChainedRunnable {
  public final ITransformedRunnable runnable;
  public final SerialThread thread;
  public ChainedRunnable next;

  /**
   * Create linked chain array.
   *
   * @param runnables Runnables
   */
  public static ChainedRunnable[] chain(IRunnable... runnables) {
    int count = runnables.length;

    // create and chain serial chains for all runnables
    ChainedRunnable[] chains = new ChainedRunnable[count];
    chains[0] = new ChainedRunnable(runnables[0]);
    for (int i = 1; i < count; i++) {
      ChainedRunnable chain = new ChainedRunnable(runnables[i]);
      chains[i] = chain;
      chains[i - 1].next = chain;
    }
    chains[count - 1].next = chains[0];

    return chains;
  }

  /**
   * Constructor.
   *
   * @param runnable task to execute
   */
  private ChainedRunnable(IRunnable runnable) {
    this.runnable = (ITransformedRunnable) runnable;
    this.thread = this.runnable.getThread();

    // thread.first.method == -1 -> dummy restore -> start normal execution of run()
  }
}
