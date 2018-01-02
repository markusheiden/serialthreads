package org.serialthreads.transformer.strategies;

import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

/**
 * Test object for capture and restore locals of type {@link float}.
 */
public class TestFloat implements IRunnable {
  public float value0 = -1;
  public float value1 = -1;
  public float value2 = -1;
  public float value3 = -1;
  public float value4 = -1;
  public float value5 = -1;
  public float value6 = -1;
  public float value7 = -1;
  public float value8 = -1;
  public float value9 = -1;

  @Interruptible
  public void run() {
    float local0 = 0;
    float local1 = local0 + 1;
    float local2 = local1 + 1;
    float local3 = local2 + 1;
    float local4 = local3 + 1;
    float local5 = local4 + 1;
    float local6 = local5 + 1;
    float local7 = local6 + 1;
    float local8 = local7 + 1;
    float local9 = local8 + 1;

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
