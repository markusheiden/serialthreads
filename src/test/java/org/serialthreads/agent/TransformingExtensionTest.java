package org.serialthreads.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;
import org.serialthreads.context.SimpleSerialThreadManager;
import org.serialthreads.transformer.strategies.frequent4.FrequentInterruptsTransformer4;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Test for {@link TransformingExtension}.
 * <p>
 * Note: All classes of this test are loaded by the {@link TransformingClassLoader},
 * so classes of this project have to be compared by name instead of identity.
 */
@Transform(transformer = FrequentInterruptsTransformer4.class)
class TransformingExtensionTest extends TransformingExtensionAbstractTest {
  /**
   * Value set by {@link #setUp()} to check that all lifecycle methods run on the same transformed instance.
   */
  private int beforeEachValue = 0;

  @BeforeEach
  void setUp() {
    beforeEachValue = 42;
  }

  @AfterEach
  void tearDown() {
    // The after each method runs on the same transformed instance as the before each method.
    assertEquals(42, beforeEachValue);
  }

  /**
   * Test that test methods run on an instance loaded by the transforming class loader.
   */
  @Test
  void testRunsOnTransformedInstance() {
    assertEquals(TransformingClassLoader.class.getName(), getClass().getClassLoader().getClass().getName());
  }

  /**
   * Test that the before each method runs on the same transformed instance as the test method.
   */
  @Test
  void testBeforeEachSharesInstance() {
    assertEquals(42, beforeEachValue);
  }

  /**
   * Test that transformed code executes correctly in a test method.
   */
  @Test
  void testTransformedCodeExecutes() {
    var runnable = new TestRunnable();
    try (var manager = new SimpleSerialThreadManager(runnable)) {
      manager.execute(1);
      assertEquals(1, runnable.value);
      manager.execute(1);
      assertEquals(2, runnable.value);
    }
  }

  /**
   * Test that the argument of a parameterized test is passed to the transformed test method.
   */
  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void testParameterized(int value) {
    assertTrue(value == 1 || value == 2);
    // The before each method has been run on the same transformed instance too.
    assertEquals(42, beforeEachValue);
  }

  /**
   * Test that all arguments of a parameterized test are passed to the transformed test method.
   */
  @ParameterizedTest
  @CsvSource({"1, one", "2, two"})
  void testParameterized_multipleArguments(int number, String name) {
    assertEquals(number == 1 ? "one" : "two", name);
  }

  /**
   * Test that test factory methods run on the transformed instance
   * and the dynamic tests they create execute correctly.
   */
  @TestFactory
  List<DynamicTest> testFactory() {
    return List.of(
      dynamicTest("factory runs on transformed instance", () ->
        assertEquals(TransformingClassLoader.class.getName(), getClass().getClassLoader().getClass().getName())),
      dynamicTest("factory shares instance with before each method", () ->
        assertEquals(42, beforeEachValue)));
  }

  /**
   * Test that each test method runs with its own transforming class loader:
   * Static state does not leak between test methods.
   * @see #testStaticStateIsolation2()
   */
  @Test
  void testStaticStateIsolation1() {
    assertEquals(1, ++StaticCounter.count);
  }

  /**
   * Test that each test method runs with its own transforming class loader:
   * Static state does not leak between test methods.
   * @see #testStaticStateIsolation1()
   */
  @Test
  void testStaticStateIsolation2() {
    assertEquals(1, ++StaticCounter.count);
  }

  /**
   * Holder for static state to check class loader isolation of test methods.
   */
  static class StaticCounter {
    static int count = 0;
  }

  /**
   * Test runnable which gets interrupted once.
   */
  public static class TestRunnable implements IRunnable {
    public int value = 0;

    @Interruptible
    @Override
    public void run() {
      value = 1;

      interrupt();

      value = 2;
    }

    @Interrupt
    void interrupt() {
      // Method call will be redirected to interrupt code.
    }
  }
}

/**
 * Base class to check that inherited test methods are found on the transformed instance.
 */
abstract class TransformingExtensionAbstractTest {
  /**
   * Test that test methods inherited from a super class run on the transformed instance.
   */
  @Test
  void testInheritedRunsOnTransformedInstance() {
    assertEquals(TransformingClassLoader.class.getName(), getClass().getClassLoader().getClass().getName());
  }
}
