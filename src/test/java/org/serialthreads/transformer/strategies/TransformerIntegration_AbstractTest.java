package org.serialthreads.transformer.strategies;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.serialthreads.context.IRunnable;
import org.serialthreads.context.SerialThreadManager;
import org.serialthreads.context.SimpleSerialThreadManager;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;

/**
 * Integration test for transformer.
 */
public abstract class TransformerIntegration_AbstractTest {
  @Before
  @After
  public void setUp() {
    // Enable debug mode, to detect byte code transformation failures.
    SerialThreadManager.DEBUG = true;
  }

  /**
   * Check that transformation does not alter behaviour.
   */
  @Test
  public void testTransform() {
    SimpleSerialThreadManager manager = new SimpleSerialThreadManager(new TestInterruptible());
    SerialThreadManager.registerManager(manager);
    manager.execute();
  }

  @Test
  public void testLocalStorage_int() throws Exception {
    testLocalStorage(new TestInt(), Integer::parseInt);
  }

  @Test
  public void testLocalStorage_long() throws Exception {
    testLocalStorage(new TestLong(), Long::parseLong);
  }

  @Test
  public void testLocalStorage_float() throws Exception {
    testLocalStorage(new TestFloat(), Float::parseFloat);
  }

  @Test
  public void testLocalStorage_double() throws Exception {
    testLocalStorage(new TestDouble(), Double::parseDouble);
  }

  /**
   * Test that locals are stored and restored correctly.
   *
   * @param test Test object.
   * @param parser Parser for the primitive type which is tested.
   */
  private void testLocalStorage(IRunnable test, Function<String, Number> parser) throws Exception {
    run(test);
    for (int i = 0; i < 9; i++) {
      assertEquals(parser.apply("-1"), field(test, "value" + i));
    }
    run(test);
    for (int i = 0; i < 9; i++) {
      assertEquals(parser.apply("" + i), field(test, "value" + i));
    }
  }

  /**
   * Test that tail calls return the correct value.
   */
  @Test
  public void testTailCall() throws Exception {
    TestTailCall test = new TestTailCall();
    run(test);
    assertEquals(-1, test.value);
    run(test);
    assertEquals(1, test.value);
  }

  //
  // Reflection test support
  //

  /**
   * Run test object once (until the next interrupt).
   */
  private void run(IRunnable test) throws Exception {
    // Do NOT call "run()" directly, to avoid transformation.
    test.getClass().getMethod("run").invoke(test);
  }

  /**
   * Get value of a field of the test object.
   *
   * @param test Test object.
   * @param name Name of the field.
   * @return Value of the field.
   */
  private Object field(IRunnable test, String name) throws Exception {
    return test.getClass().getField(name).get(test);
  }
}
