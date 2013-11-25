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
    Class<?> clazz = classLoader.loadClass(TestInterruptible.class.getName());
    clazz.getMethod("runTransformed").invoke(clazz.newInstance());
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
  public void testLocalStorage_Integer() throws Exception {
    create(TestInteger.class);
    run();
    for (int i = 0; i < 9; i++) {
      assertEquals(-1, field("value" + i));
    }
    run();
    for (int i = 0; i < 9; i++) {
      assertEquals(i, field("value" + i));
    }
  }

  //
  // Reflection test support
  //

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
}
