package org.serialthreads.context;

import java.io.Serializable;

/**
 * Stores the state of a serial thread.
 */
public interface SerialThread extends Serializable {
  /**
   * Name of the thread.
   */
  String getName();
}
