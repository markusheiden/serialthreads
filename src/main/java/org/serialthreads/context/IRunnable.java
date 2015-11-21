package org.serialthreads.context;

import org.serialthreads.Interruptible;

/**
 * Runnable interface for serial threads.
 * <p/>
 * TODO 2010-02-03 mh: move to org.serialthreads?
 */
public interface IRunnable {
  /**
   * Run serial thread.
   *
   * @throws ThreadFinishedException if thread terminates
   */
  @Interruptible
  void run() throws ThreadFinishedException;
}
