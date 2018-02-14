package org.serialthreads.context;

/**
 * Runnable interface for serial threads after transformation.
 * Do NOT use, except for transformers and serial thread managers!
 */
public interface ITransformedRunnable extends IRunnable {
  /**
   * Get serial thread.
   */
  SerialThread getThread();
}
