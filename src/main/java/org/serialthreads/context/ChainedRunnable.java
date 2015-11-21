package org.serialthreads.context;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import org.serialthreads.Executor;

/**
 * Linked list element for runnables.
 */
public class ChainedRunnable {
  public final ITransformedRunnable runnable;
  public final SerialThread thread;
  public ChainedRunnable next;

  /**
   * Create linked chain array.
   *
   * @param runnables Runnables
   */
  public static ChainedRunnable[] chain(IRunnable... runnables) {
    return chain(Arrays.asList(runnables));
  }

  /**
   * Create linked chain array.
   *
   * @param runnables Runnables
   */
  public static ChainedRunnable[] chain(Collection<? extends IRunnable> runnables) {
    int count = runnables.size();

    ChainedRunnable[] result = new ChainedRunnable[count];
    Iterator<? extends IRunnable> iterator = runnables.iterator();
    result[0] = new ChainedRunnable(iterator.next());
    for (int i = 1; i < count; i++) {
      ChainedRunnable chain = new ChainedRunnable(iterator.next());
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

  /**
   * Constructor.
   */
  protected ChainedRunnable() {
    this.runnable = null;
    this.thread = null;
  }

  /**
   * Run chained runnable.
   *
   * @return Next in chain
   */
  @Executor
  public ChainedRunnable run() {
    runnable.run();
    return next;
  }
}
