package org.serialthreads.transformer.strategies;

import org.junit.Ignore;
import org.junit.Test;
import org.serialthreads.context.IRunnable;
import org.serialthreads.context.SimpleSerialThreadManager;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;

/**
 * Integration test for transformer.
 */
public abstract class TransformerIntegration_AbstractTest {
  /**
   * Check that transformation does not alter behaviour.
   */
  @Test
  public void testTransform() {
    TestInterruptible test = new TestInterruptible(true);

    SimpleSerialThreadManager manager = new SimpleSerialThreadManager(test);
    manager.execute();

    test.assertExpectedResult();
  }

  /**
   * Check that transformation does not alter behaviour.
   * Tests, that {@link IRunnable#run()} is transformed correctly,
   * if not containing any interruptible method call.
   */
  @Ignore
  @Test
  public void testRunNo() {
    TestRunNoInterruptible test = new TestRunNoInterruptible();

    SimpleSerialThreadManager manager = new SimpleSerialThreadManager(test);
    manager.execute();

    test.assertExpectedResult();
  }

  /**
   * Check that transformation does not alter behaviour.
   * Tests, that {@link IRunnable#run()} is transformed correctly,
   * if just containing one interruptible method call.
   */
  @Ignore
  @Test
  public void testRunSingle() {
    TestRunSingleInterruptible test = new TestRunSingleInterruptible();

    SimpleSerialThreadManager manager = new SimpleSerialThreadManager(test);
    manager.execute();

    test.assertExpectedResult();
  }

  /**
   * Check that transformation does not alter behaviour.
   * Tests, that {@link IRunnable#run()} is transformed correctly,
   * if just containing multiple interruptible method calls.
   */
  @Test
  public void testRunMulti() {
    TestRunMultiInterruptible test = new TestRunMultiInterruptible();

    SimpleSerialThreadManager manager = new SimpleSerialThreadManager(test);
    manager.execute();

    test.assertExpectedResult();
  }

  /**
   * Test capture and restore of locals of type {@link int}.
   */
  @Test
  public void testLocalStorage_int() throws Exception {
    testLocalStorage(new TestInt(0), Integer::parseInt);
  }

  /**
   * Test capture and restore of locals of type {@link long}.
   */
  @Test
  public void testLocalStorage_long() throws Exception {
    testLocalStorage(new TestLong(0), Long::parseLong);
  }

  /**
   * Test capture and restore of locals of type {@link float}.
   */
  @Test
  public void testLocalStorage_float() throws Exception {
    testLocalStorage(new TestFloat(0), Float::parseFloat);
  }

  /**
   * Test capture and restore of locals of type {@link double}.
   */
  @Test
  public void testLocalStorage_double() throws Exception {
    testLocalStorage(new TestDouble(0), Double::parseDouble);
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

  /**
   * Test exception handling.
   */
  @Ignore // TODO markus 2018-01-04: Implement exception handling.
  @Test
  public void testException() throws Exception {
    TestException test = new TestException();
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
