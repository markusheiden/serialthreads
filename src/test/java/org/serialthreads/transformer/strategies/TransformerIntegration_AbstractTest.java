package org.serialthreads.transformer.strategies;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.serialthreads.context.IRunnable;
import org.serialthreads.context.SerialThreadManager;
import org.serialthreads.context.SimpleSerialThreadManager;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

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
      assertThat(field(test, "value" + i)).isEqualTo(parser.apply("-1"));
    }
    manager.execute(1);
    for (int i = 0; i < 9; i++) {
      assertThat(field(test, "value" + i)).isEqualTo(parser.apply("" + i));
    }
  }

  /**
   * Test that a finished serial thread is reset, so that it can be restarted.
   */
  @Test
  void testRestart() {
    var test = new TestRestart();

    manager = new SimpleSerialThreadManager(test);
    // Run until the interrupt.
    manager.execute(1);
    assertThat(test.runs).isEqualTo(1);
    assertThat(test.value).isEqualTo(1);
    // Finish the thread: it gets reset to be able to run again.
    manager.execute(1);
    assertThat(test.runs).isEqualTo(1);
    assertThat(test.value).isEqualTo(2);
    // Restart the thread: it runs from the beginning until the interrupt again.
    manager.execute(1);
    assertThat(test.runs).isEqualTo(2);
    assertThat(test.value).isEqualTo(1);
  }

  /**
   * Test that classes whose frames need the computation of a common super class transform correctly.
   */
  @Test
  void testTypeMerge() {
    var test = new TestTypeMerge();

    manager = new SimpleSerialThreadManager(test);
    manager.execute(1);
    assertThat(test.value).isEmpty();
    manager.execute(1);
    assertThat(test.value).isEqualTo("a");
  }

  /**
   * Test capture and restore of operand stack values of type {@link long}.
   */
  @Test
  void testStackStorage_long() {
    var test = new TestStackLong();

    manager = new SimpleSerialThreadManager(test);
    manager.execute(1);
    assertThat(test.value).isEqualTo(-1);
    assertThat(test.valueDeep).isEqualTo(-1);
    manager.execute(1);
    assertThat(test.value).isEqualTo(42);
    assertThat(test.valueDeep).isEqualTo(-1);
    manager.execute(1);
    assertThat(test.valueDeep).isEqualTo(122);
  }

  /**
   * Test capture and restore of operand stack values of type {@link double}.
   */
  @Test
  void testStackStorage_double() {
    var test = new TestStackDouble();

    manager = new SimpleSerialThreadManager(test);
    manager.execute(1);
    assertThat(test.value).isEqualTo(-1);
    assertThat(test.valueDeep).isEqualTo(-1);
    manager.execute(1);
    assertThat(test.value).isEqualTo(42.25);
    assertThat(test.valueDeep).isEqualTo(-1);
    manager.execute(1);
    assertThat(test.valueDeep).isEqualTo(126.25);
  }

  /**
   * Test that tail calls return the correct value.
   */
  @Test
  void testTailCall() {
    var test = new TestTailCall();
    manager = new SimpleSerialThreadManager(test);
    manager.execute(1);
    assertThat(test.value).isEqualTo(-1);
    manager.execute(1);
    assertThat(test.value).isEqualTo(1);
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
    assertThat(test.value).isEqualTo(-1);
    manager.execute(1);
    assertThat(test.value).isEqualTo(1);
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
