package org.serialthreads.context;

import org.serialthreads.Executor;

/**
 * Manages the access to serial threads for transformed classes.
 */
public abstract class SerialThreadManager implements AutoCloseable {
  /**
   * Thread managers.
   */
  private static final ThreadLocal<SerialThread> threads = new ThreadLocal<>();

  /**
   * Set the current thread.
   */
  public static void setThread(SerialThread thread) {
    threads.set(thread);
  }

  /**
   * Get the current thread.
   */
  public static SerialThread getThread() {
    return threads.get();
  }

  /**
   * Execute all serial threads.
   */
  @Executor
  public abstract void execute();

  /**
   * Execute all serial threads for a given number of interrupts.
   *
   * @param interrupts number interrupts to execute each serial thread
   */
  @Executor
  public abstract void execute(int interrupts);

  @Override
  public void close() {
    threads.remove();
  }
}
