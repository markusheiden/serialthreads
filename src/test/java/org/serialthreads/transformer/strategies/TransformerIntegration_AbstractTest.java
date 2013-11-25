package org.serialthreads.transformer.strategies;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.TraceClassVisitor;
import org.serialthreads.agent.TransformingClassLoader;
import org.serialthreads.context.IRunnable;
import org.serialthreads.context.SerialThreadManager;
import org.serialthreads.transformer.IStrategy;

import java.io.PrintWriter;

import static org.junit.Assert.assertEquals;

/**
 * Integration test for transformer.
 */
public abstract class TransformerIntegration_AbstractTest {
  /**
   * Strategy under test.
   */
  protected IStrategy strategy;

  private TransformingClassLoader classLoader;

  @Before
  @After
  public void setUp() {
    SerialThreadManager.DEBUG = true;

    classLoader = new TransformingClassLoader(strategy) {
      @Override
      protected ClassVisitor createVisitor(ClassWriter writer) {
        return new TraceClassVisitor(writer, new PrintWriter(System.out));
      }
    };
  }

  /**
   * Check that transformation does not alter behaviour.
   */
  @Test
  public void testTransform() throws Exception {
    create(TestInterruptible.class);
    invoke("runTransformed");
  }

  /**
   * Check if the test assumptions are correct by executing without a transformer.
   */
  @Test
  public void testNoTransform() throws Exception {
    // disable debug mode to not throw an exception in SerialThreadManager.interrupt()
    SerialThreadManager.DEBUG = false;

    Class<?> clazz = getClass().getClassLoader().loadClass(TestInterruptible.class.getName());
    clazz.getMethod("run").invoke(clazz.newInstance());
  }

  @Test
  public void testLocalStorage_int() throws Exception {
    testLocalStorage(TestInt.class, Integer::parseInt);
  }

  @Test
  public void testLocalStorage_long() throws Exception {
    testLocalStorage(TestLong.class, Long::parseLong);
  }

  @Test
  public void testLocalStorage_float() throws Exception {
    testLocalStorage(TestFloat.class, Float::parseFloat);
  }

  @Test
  public void testLocalStorage_double() throws Exception {
    testLocalStorage(TestDouble.class, Double::parseDouble);
  }

  /**
   * Test that local are stored and restored correctly.
   *
   * @param clazz Class of test object
   * @param parser Parser for the primitive type which is tested
   */
  private void testLocalStorage(Class<? extends IRunnable> clazz, Parser parser) throws Exception {
    create(clazz);
    run();
    for (int i = 0; i < 9; i++) {
      assertEquals(parser.parse("-1"), field("value" + i));
    }
    run();
    for (int i = 0; i < 9; i++) {
      assertEquals(parser.parse("" + i), field("value" + i));
    }
  }

  //
  // Reflection test support
  //

  /**
   * SAM interface for a number parser.
   */
  private static interface Parser {
    /**
     * Parse a number from a string.
     *
     * @param value String representation of number
     * @return Number
     */
    public Object parse(String value);
  }

  /**
   * Test object.
   */
  private Object test;

  /**
   * Create and transform test object.
   *
   * @param clazz Clazz of test object
   */
  private void create(Class<? extends IRunnable> clazz) throws Exception {
    test = classLoader.loadClass(clazz.getName()).newInstance();
  }

  /**
   * Run test object once.
   */
  private void run() throws Exception {
    test.getClass().getMethod("run").invoke(test);
  }

  /**
   * Get value of a field of the test object.
   *
   * @param name Name of the field
   * @return Value of the field
   */
  private Object field(String name) throws Exception {
    return test.getClass().getField(name).get(test);
  }

  /**
   * Invoke a method without parameters on the test object.
   *
   * @param name Name of the method
   * @return Value of the field
   */
  private Object invoke(String name) throws Exception {
    return test.getClass().getMethod(name).invoke(test);
  }
}
