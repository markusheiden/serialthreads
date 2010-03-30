package org.serialthreads.context;

import org.serialthreads.Executor;
import org.serialthreads.Interrupt;

/**
 * Manages the access to serial threads for transformed classes.
 */
public abstract class SerialThreadManager
{
  public static boolean DEBUG = true;

  private static final ThreadLocal<SerialThreadManager> threadManagers =
    new ThreadLocal<SerialThreadManager>();

  protected SerialThread currentThread;

  /**
   * Register a thread manager for the current thread.
   *
   * @param manager thread manager
   */
  public static void registerManager(SerialThreadManager manager)
  {
    assert threadManagers.get() == null : "Precondition: no manager registered for this thread yet";

    threadManagers.set(manager);

    assert manager == threadManagers.get() : "Precondition: manager registered successfully";
  }

  /**
   * Get the current thread.
   */
  public static SerialThread getThread()
  {
    return threadManagers.get().currentThread;
  }

  /**
   * Interrupt current thread.
   * This is a dummy method which calls will be eliminated by the byte code transformation!
   */
  @Interrupt
  public static void interrupt()
  {
    if (DEBUG)
    {
      throw new IllegalThreadStateException("Byte code transformation failed");
    }
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
  public abstract void execute(int interrupts);
}
