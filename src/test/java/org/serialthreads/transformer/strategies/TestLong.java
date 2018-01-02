package org.serialthreads.transformer.strategies;

import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

/**
 * Test object for capture and restore locals of type {@link long}.
 */
public class TestLong implements IRunnable {
  public long value0 = -1;
  public long value1 = -1;
  public long value2 = -1;
  public long value3 = -1;
  public long value4 = -1;
  public long value5 = -1;
  public long value6 = -1;
  public long value7 = -1;
  public long value8 = -1;
  public long value9 = -1;

  @Interruptible
  public void run() {
    long local0 = 0;
    long local1 = local0 + 1;
    long local2 = local1 + 1;
    long local3 = local2 + 1;
    long local4 = local3 + 1;
    long local5 = local4 + 1;
    long local6 = local5 + 1;
    long local7 = local6 + 1;
    long local8 = local7 + 1;
    long local9 = local8 + 1;

    interrupt();

    value0 = local0;
    value1 = local1;
    value2 = local2;
    value3 = local3;
    value4 = local4;
    value5 = local5;
    value6 = local6;
    value7 = local7;
    value8 = local8;
    value9 = local9;

    interrupt();
  }

  @Interrupt
  private void interrupt() {
    // method call will be redirected to interrupt code
  }
}
