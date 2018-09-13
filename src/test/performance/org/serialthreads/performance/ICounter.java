package org.serialthreads.performance;

import org.serialthreads.context.IRunnable;

/**
 * Interface for counters.
 */
public interface ICounter extends IRunnable {
  boolean isReady();

  long getCount();
}
