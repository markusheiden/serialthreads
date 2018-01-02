package org.serialthreads.transformer.strategies;

import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

/**
 * Test object for capture and restore locals of type {@link double}.
 */
public class TestDouble implements IRunnable {
  private final double init;
  public double value0 = -1;
  public double value1 = -1;
  public double value2 = -1;
  public double value3 = -1;
  public double value4 = -1;
  public double value5 = -1;
  public double value6 = -1;
  public double value7 = -1;
  public double value8 = -1;
  public double value9 = -1;

  public TestDouble(double init) {
    this.init = init;
  }

  @Interruptible
  public void run() {
    double local0 = init;
    double local1 = local0 + 1;
    double local2 = local1 + 1;
    double local3 = local2 + 1;
    double local4 = local3 + 1;
    double local5 = local4 + 1;
    double local6 = local5 + 1;
    double local7 = local6 + 1;
    double local8 = local7 + 1;
    double local9 = local8 + 1;

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
