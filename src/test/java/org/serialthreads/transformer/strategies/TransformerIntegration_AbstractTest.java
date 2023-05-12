package org.serialthreads.transformer.strategies;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.serialthreads.context.IRunnable;
import org.serialthreads.context.SerialThreadManager;
import org.serialthreads.context.SimpleSerialThreadManager;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test for transformer.
 */
public abstract class TransformerIntegration_AbstractTest {
  private SerialThreadManager manager;

  @AfterEach
  void tearDown() {
    if (manager != null) {
      manager.close();
    }
  }

  /**
   * Check that transformation does not alter behaviour.
   */
  @Test
  void testTransform() {
    var test = new TestInterruptible(true);

    manager = new SimpleSerialThreadManager(test);
    manager.execute();

    test.assertExpectedResult();
  }

  /**
   * Check that transformation does not alter behaviour.
   * Tests, that {@link IRunnable#run()} is transformed correctly,
   * if not containing any interruptible method call.
   */
  @Test
  void testRunNo() {
    var test = new TestRunNoInterruptible();

    manager = new SimpleSerialThreadManager(test);
    manager.execute();

    test.assertExpectedResult();
  }

  /**
   * Check that transformation does not alter behaviour.
   * Tests, that {@link IRunnable#run()} is transformed correctly,
   * if just containing one interruptible method call.
   */
  @Test
  void testRunSingle() {
    var test = new TestRunSingleInterruptible();

    manager = new SimpleSerialThreadManager(test);
    manager.execute();

    test.assertExpectedResult();
  }

  /**
   * Check that transformation does not alter behaviour.
   * Tests, that {@link IRunnable#run()} is transformed correctly,
   * if just containing multiple interruptible method calls.
   */
  @Test
  void testRunMulti() {
    var test = new TestRunMultiInterruptible();

    manager = new SimpleSerialThreadManager(test);
    manager.execute();

    test.assertExpectedResult();
  }

  /**
   * Test capture and restore of locals of type {@link int}.
   */
  @Test
  void testLocalStorage_int() throws Exception {
    testLocalStorage(new TestInt(0), Integer::parseInt);
  }

  /**
   * Test capture and restore of locals of type {@link long}.
   */
  @Test
  void testLocalStorage_long() throws Exception {
    testLocalStorage(new TestLong(0), Long::parseLong);
  }

  /**
   * Test capture and restore of locals of type {@link float}.
   */
  @Test
  void testLocalStorage_float() throws Exception {
    testLocalStorage(new TestFloat(0), Float::parseFloat);
  }

  /**
   * Test capture and restore of locals of type {@link double}.
   */
  @Test
  void testLocalStorage_double() throws Exception {
    testLocalStorage(new TestDouble(0), Double::parseDouble);
  }

  /**
   * Test that locals are stored and restored correctly.
   *
   * @param test Test object.
   * @param parser Parser for the primitive type which is tested.
   */
  private void testLocalStorage(IRunnable test, Function<String, Number> parser) throws Exception {
    manager = new SimpleSerialThreadManager(test);
    manager.execute(1);
    for (int i = 0; i < 9; i++) {
      assertEquals(parser.apply("-1"), field(test, "value" + i));
    }
    manager.execute(1);
    for (int i = 0; i < 9; i++) {
      assertEquals(parser.apply("" + i), field(test, "value" + i));
    }
  }

  /**
   * Test that tail calls return the correct value.
   */
  @Test
  void testTailCall() {
    var test = new TestTailCall();
    manager = new SimpleSerialThreadManager(test);
    manager.execute(1);
    assertEquals(-1, test.value);
    manager.execute(1);
    assertEquals(1, test.value);
  }

  /**
   * Test exception handling.
   */
  @Disabled // TODO markus 2018-01-04: Implement exception handling.
  @Test
  void testException() {
    var test = new TestException();
    manager = new SimpleSerialThreadManager(test);
    manager.execute(1);
    assertEquals(-1, test.value);
    manager.execute(1);
    assertEquals(1, test.value);
  }

  //
  // Reflection test support
  //

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
