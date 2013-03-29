package org.serialthreads.context;

import java.io.Serializable;

/**
 * Stores the state of a serial thread.
 */
public class SerialThread implements Serializable {
  /**
   * Is the thread in the capturing or restoring phase?.
   */
  public boolean serializing = false;

  /**
   * The name of the thread.
   */
  private final String name;

  /**
   * Constructor.
   *
   * @param name name of the thread
   */
  public SerialThread(String name) {
    this.name = name;
  }

  /**
   * Name of the thread.
   */
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }
}
