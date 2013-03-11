package org.serialthreads.transformer.strategies;

import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;
import org.serialthreads.context.SerialThreadManager;
import org.serialthreads.context.SimpleSerialThreadManager;

import static org.junit.Assert.assertEquals;

/**
 * Test runnable for transformer integration tests.
 */
public class TestInterruptible extends AbstractTestInterruptible implements ITestInterruptible, IRunnable {
  public boolean z;
  public char c;
  public byte b;
  public short s;
  public int i;
  public long j;
  public float f;
  public double d;

  public long test;

  /**
   * Execute transformed runnable via serial thread manager.
   */
  public void runTransformed() {
    SimpleSerialThreadManager manager = new SimpleSerialThreadManager(this);
    SerialThreadManager.registerManager(manager);
    manager.execute();
  }

  /**
   * Execute runnable.
   */
  @Interruptible
  public void run() {
    long l1 = testLong(true, (char) 1, (byte) 3, (short) 5, 7, 11L);
    double d1 = testDouble(13.0F, 17.0D);
    int i1 = testStatic(19);

    test = i1 + l1 + (long) d1;

    notInterruptible();

    checkResult();
  }

  @Interruptible
  public long testLong(boolean z, char c, byte b, short s, int i, long j) {
    z = !z;
    SerialThreadManager.interrupt();
    this.z = z;
    c++;
    SerialThreadManager.interrupt();
    this.c = c;
    b++;
    SerialThreadManager.interrupt();
    this.b = b;
    s++;
    SerialThreadManager.interrupt();
    this.s = s;
    i++;
    SerialThreadManager.interrupt();
    this.i = i;
    j++;
    SerialThreadManager.interrupt();
    this.j = j;

    return j;
  }

  @Interruptible
  public double testDouble(float f, double d) {
    f++;
    SerialThreadManager.interrupt();
    this.f = f;
    d++;
    SerialThreadManager.interrupt();
    this.d = d;

    return d;
  }

  @Interruptible
  public static int testStatic(int i) {
    i++;
    SerialThreadManager.interrupt();
    i += 3;
    SerialThreadManager.interrupt();
    return i;
  }

  @Interruptible
  public static void notInterruptible() {
    System.out.println("Not interruptible method has been called");
  }

  //
  // Check results of execution ("self test")
  //

  private void checkResult() {
    assertEquals(false, z); // !true
    assertEquals((char) 2, c); // 1++
    assertEquals((byte) 4, b); // 3++
    assertEquals((short) 6, s); // 5++
    assertEquals(8, i); // 7++ + 3
    assertEquals(12L, j); // 11++

    assertEquals(14.0F, f, 0.1); // 13++
    assertEquals(18.0D, d, 0.1); // 17++

    assertEquals(53L, test); // 11++ + 3 + 17++ + 19++
  }
}
