package org.serialthreads.transformer.strategies;

import org.serialthreads.Interruptible;

/**
 * Abstract base class for test class for testing transformation of base classes.
 */
abstract class AbstractTestInterruptible {
  @Interruptible
  public abstract double testDouble(float f, double d);
}
