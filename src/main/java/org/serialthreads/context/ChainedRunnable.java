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

    ChainedRunnable[] result = new ChainedRunnable[count];
    result[0] = new ChainedRunnable(runnables[0]);
    for (int i = 1; i < count; i++) {
      ChainedRunnable chain = new ChainedRunnable(runnables[i]);
      result[i] = chain;
      result[i - 1].next = chain;
    }
    result[count - 1].next = result[0];

    return result;
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
