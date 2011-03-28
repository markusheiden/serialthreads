package org.serialthreads.context;

/**
 * The exception the run method throws if a thread is finished.
 */
public class ThreadFinishedException extends RuntimeException
{
  /**
   * Constructor.
   *
   * @param name name of the thread
   */
  public ThreadFinishedException(String name)
  {
    super("Thread " + name + " finished");
  }
}
