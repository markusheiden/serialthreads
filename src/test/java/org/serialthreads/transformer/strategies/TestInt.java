package org.serialthreads.transformer.strategies;

import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

/**
 * Test object for capture and restore locals of type {@link int}.
 */
public class TestInt implements IRunnable {
  public int value0 = -1;
  public int value1 = -1;
  public int value2 = -1;
  public int value3 = -1;
  public int value4 = -1;
  public int value5 = -1;
  public int value6 = -1;
  public int value7 = -1;
  public int value8 = -1;
  public int value9 = -1;

  @Interruptible
  public void run() {
    int local0 = 0;
    int local1 = local0 + 1;
    int local2 = local1 + 1;
    int local3 = local2 + 1;
    int local4 = local3 + 1;
    int local5 = local4 + 1;
    int local6 = local5 + 1;
    int local7 = local6 + 1;
    int local8 = local7 + 1;
    int local9 = local8 + 1;

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
