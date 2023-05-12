package org.serialthreads.transformer.strategies;

import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test runnable for transformer integration tests.
 * <p>
 * Extends base class to test base class transformation.
 * Implements interface to test interface transformation.
 */
public class TestInterruptible extends AbstractTestInterruptible implements ITestInterruptible, IRunnable {
  /**
   * Check, if byte code transformation was successful?.
   */
  private static boolean checkTransformation = true;

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
   * Constructor.
   *
   * @param checkTransformation Check, if byte code transformation was successful?.
   */
  public TestInterruptible(boolean checkTransformation) {
    this.checkTransformation = checkTransformation;
  }

  /**
   * Execute runnable.
   */
  @Override
  @Interruptible
  public void run() {
    long l1 = testLong(true, (char) 1, (byte) 3, (short) 5, 7, 11L);
    double d1 = testDouble(13.0F, 17.0D);
    int i1 = testStatic(19);

    test = i1 + l1 + (long) d1;

    notInterruptible();
  }

  /**
   * Test interrupt of method and capture and restore of integer locals.
   */
  @Override
  @Interruptible
  public long testLong(boolean z, char c, byte b, short s, int i, long j) {
    z = !z;
    interrupt();
    this.z = z;
    c++;
    interrupt();
    this.c = c;
    b++;
    interrupt();
    this.b = b;
    s++;
    interrupt();
    this.s = s;
    i++;
    interrupt();
    this.i = i;
    j++;
    interrupt();
    this.j = j;

    return j;
  }

  /**
   * Test interrupt of method and capture and restore of floating point locals.
   */
  @Override
  @Interruptible
  public double testDouble(float f, double d) {
    f++;
    interrupt();
    this.f = f;
    d++;
    interrupt();
    this.d = d;

    return d;
  }

  /**
   * Test interrupt of static method.
   */
  @Interruptible
  public static int testStatic(int i) {
    i++;
    interrupt();
    i += 3;
    interrupt();
    return i;
  }

  /**
   * Test interruptible static method without interrupt.
   */
  @Interruptible
  public static void notInterruptible() {
    System.out.println("Not interruptible method has been called");
  }

  /**
   * Interrupt current thread.
   * This is a dummy method which calls will be eliminated by the byte code transformation!
   */
  @Interrupt
  public static void interrupt() {
    if (checkTransformation) {
      throw new IllegalThreadStateException("Byte code transformation failed");
    }
  }

  //
  // Check results of execution ("self test")
  //

  /**
   * Check expected results of calling {@link TestInterruptible#run()} once.
   */
  public void assertExpectedResult() {
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
