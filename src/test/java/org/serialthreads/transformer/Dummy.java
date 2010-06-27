package org.serialthreads.transformer;

import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;
import org.serialthreads.context.SerialThreadManager;

/**
 * Dummy target for transformers.
 */
public class Dummy implements IRunnable
{
  @Override
  @Interruptible
  public void run()
  {
    int a = 1;
    int b = 2;
    int c = 3;
    int d = 4;
    a = subInterruptible(a, b, c, d);
    System.out.println(a);
  }

  @Interruptible
  private int subInterruptible(int a, int b, int c, int d)
  {
    a = a + b + c + d;
    SerialThreadManager.interrupt();
    return a;
  }
}
