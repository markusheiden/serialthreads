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

import static org.assertj.core.api.Assertions.assertThat;
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
    assertThat(beforeEachValue).isEqualTo(42);
  }

  /**
   * Test that test methods run on an instance loaded by the transforming class loader.
   */
  @Test
  void testRunsOnTransformedInstance() {
    assertThat(getClass().getClassLoader().getClass().getName()).isEqualTo(TransformingClassLoader.class.getName());
  }

  /**
   * Test that the before each method runs on the same transformed instance as the test method.
   */
  @Test
  void testBeforeEachSharesInstance() {
    assertThat(beforeEachValue).isEqualTo(42);
  }

  /**
   * Test that transformed code executes correctly in a test method.
   */
  @Test
  void testTransformedCodeExecutes() {
    var runnable = new TestRunnable();
    try (var manager = new SimpleSerialThreadManager(runnable)) {
      manager.execute(1);
      assertThat(runnable.value).isEqualTo(1);
      manager.execute(1);
      assertThat(runnable.value).isEqualTo(2);
    }
  }

  /**
   * Test that the argument of a parameterized test is passed to the transformed test method.
   */
  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void testParameterized(int value) {
    assertThat(value).isIn(1, 2);
    // The before each method has been run on the same transformed instance too.
    assertThat(beforeEachValue).isEqualTo(42);
  }

  /**
   * Test that all arguments of a parameterized test are passed to the transformed test method.
   */
  @ParameterizedTest
  @CsvSource({"1, one", "2, two"})
  void testParameterized_multipleArguments(int number, String name) {
    assertThat(name).isEqualTo(number == 1 ? "one" : "two");
  }

  /**
   * Test that test factory methods run on the transformed instance
   * and the dynamic tests they create execute correctly.
   */
  @TestFactory
  List<DynamicTest> testFactory() {
    return List.of(
      dynamicTest("factory runs on transformed instance", () ->
        assertThat(getClass().getClassLoader().getClass().getName()).isEqualTo(TransformingClassLoader.class.getName())),
      dynamicTest("factory shares instance with before each method", () ->
        assertThat(beforeEachValue).isEqualTo(42)));
  }

  /**
   * Test that each test method runs with its own transforming class loader:
   * Static state does not leak between test methods.
   * @see #testStaticStateIsolation2()
   */
  @Test
  void testStaticStateIsolation1() {
    assertThat(++StaticCounter.count).isEqualTo(1);
  }

  /**
   * Test that each test method runs with its own transforming class loader:
   * Static state does not leak between test methods.
   * @see #testStaticStateIsolation1()
   */
  @Test
  void testStaticStateIsolation2() {
    assertThat(++StaticCounter.count).isEqualTo(1);
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
    assertThat(getClass().getClassLoader().getClass().getName()).isEqualTo(TransformingClassLoader.class.getName());
  }
}
