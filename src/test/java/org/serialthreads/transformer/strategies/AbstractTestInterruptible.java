package org.serialthreads.transformer.strategies;

import org.serialthreads.Interruptible;

/**
 * Abstract base class for test class.
 */
public abstract class AbstractTestInterruptible {
  @Interruptible
  public abstract double testDouble(float f, double d);
}
