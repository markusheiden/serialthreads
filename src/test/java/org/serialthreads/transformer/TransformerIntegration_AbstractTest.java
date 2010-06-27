package org.serialthreads.transformer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.serialthreads.Interruptible;
import org.serialthreads.agent.TransformingClassLoader;
import org.serialthreads.context.IRunnable;
import org.serialthreads.context.SerialThreadManager;
import org.serialthreads.context.SimpleSerialThreadManager;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;

/**
 * Integration test for transformer.
 */
public abstract class TransformerIntegration_AbstractTest
{
  protected IStrategy strategy;

  @Before
  @After
  public void setUp()
  {
    SerialThreadManager.DEBUG = true;
  }

  /**
   * Check that transformation does not alter behaviour.
   */
  @Test
  public void testTransform() throws Exception
  {
    TransformingClassLoader cl = new TransformingClassLoader(strategy);

    Class<? extends IRunnable> clazz = (Class<? extends IRunnable>)
      cl.loadClass(TestInterruptible.class.getName());
    IRunnable test = clazz.newInstance();
    SimpleSerialThreadManager manager = new SimpleSerialThreadManager(test);
    SerialThreadManager.registerManager(manager);
    manager.execute();

    checkResult(test);
  }

  /**
   * Check if the test assumptions are correct by executing without a transformer.
   */
  @Test
  public void testNoTransform() throws Exception
  {
    // disable debug mode to not throw an exception in SerialThreadManager.interrupt()
    SerialThreadManager.DEBUG = false;

    Class<? extends IRunnable> clazz = (Class<? extends IRunnable>)
      getClass().getClassLoader().loadClass(TestInterruptible.class.getName());
    IRunnable test = clazz.newInstance();
    test.run();

    checkResult(test);
  }

  private void checkResult(IRunnable test) throws Exception
  {
    assertFieldEquals(false, test, "z"); // !true
    assertFieldEquals((char) 2, test, "c"); // 1++
    assertFieldEquals((byte) 4, test, "b"); // 3++
    assertFieldEquals((short) 6, test, "s"); // 5++
    assertFieldEquals(8, test, "i"); // 7++
    assertFieldEquals(12L, test, "j"); // 11++

    assertFieldEquals(14.0F, test, "f"); // 13++
    assertFieldEquals(18.0D, test, "d"); // 17++

    assertFieldEquals(50L, test, "test"); // 11++ + 17++ + 19++
  }

  private void assertFieldEquals(Object expected, IRunnable test, String name) throws Exception
  {
    Field field = test.getClass().getField(name);
    assertEquals(expected, field.get(test));
  }

  public static class TestInterruptible implements IRunnable
  {
    public boolean z;
    public char c;
    public byte b;
    public short s;
    public int i;
    public long j;
    public float f;
    public double d;

    public long test;

    @Interruptible
    public void run()
    {
      long l1 = testLong(true, (char) 1, (byte) 3, (short) 5, 7, 11L);
      double d1 = testDouble(13.0F, 17.0D);
      int i1 = testStatic(19);

      test = i1 + l1 + (long) d1;
    }

    @Interruptible
    public long testLong(boolean z, char c, byte b, short s, int i, long j)
    {
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
    public double testDouble(float f, double d)
    {
      f++;
      SerialThreadManager.interrupt();
      this.f = f;
      d++;
      SerialThreadManager.interrupt();
      this.d = d;

      return d;
    }

    @Interruptible
    public static int testStatic(int i)
    {
      i++;
      SerialThreadManager.interrupt();
      return i;
    }
  }
}
