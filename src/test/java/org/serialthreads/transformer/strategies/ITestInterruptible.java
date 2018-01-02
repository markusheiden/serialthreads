package org.serialthreads.transformer.strategies;

import org.serialthreads.Interruptible;

/**
 * Interface for test class for testing transformation of interfaces.
 */
public interface ITestInterruptible {
  @Interruptible
  public long testLong(boolean z, char c, byte b, short s, int i, long j);
}
