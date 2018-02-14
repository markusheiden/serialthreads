package org.serialthreads.context;

import org.serialthreads.Executor;

/**
 * Manages the access to serial threads for transformed classes.
 */
public abstract class SerialThreadManager implements AutoCloseable {
  /**
   * Thread managers.
   */
  private static final ThreadLocal<SerialThreadManager> threadManagers = new ThreadLocal<>();

  /**
   * Currently executing thread.
   */
  private SerialThread currentThread;

  /**
   * Register a thread manager for the current thread.
   *
   * @param manager thread manager
   */
  protected static void registerManager(SerialThreadManager manager) {
    assert threadManagers.get() == null : "Precondition: no manager registered for this thread yet.";

    threadManagers.set(manager);

    assert manager == threadManagers.get() : "Precondition: manager registered successfully.";
  }

  /**
   * Deregister a thread manager from the current thread.
   *
   * @param manager thread manager
   */
  public static void deregisterManager(SerialThreadManager manager) {
    SerialThreadManager registerManager = threadManagers.get();
    assert registerManager == null || registerManager == manager : "Precondition: manager registered for this thread yet.";

    threadManagers.remove();

    assert threadManagers.get() == null : "Precondition: manager deregistered successfully.";
  }

  /**
   * Set the current thread.
   */
  public static void setThread(SerialThread thread) {
    threadManagers.get().currentThread = thread;
  }

  /**
   * Get the current thread.
   */
  public static SerialThread getThread() {
    return threadManagers.get().currentThread;
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
    deregisterManager(this);
  }
}
