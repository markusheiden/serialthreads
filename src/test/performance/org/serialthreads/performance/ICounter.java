package org.serialthreads.performance;

import org.serialthreads.context.IRunnable;

/**
 * Interface for counters.
 */
public interface ICounter extends IRunnable
{
  public boolean isReady();

  public long getCount();
}
