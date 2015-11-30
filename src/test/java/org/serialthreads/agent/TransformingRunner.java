package org.serialthreads.agent;

import org.junit.runner.Runner;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.serialthreads.transformer.IStrategy;

import java.lang.reflect.Method;

/**
 * {@link Runner} using a {@link TransformingClassLoader} for loading all classes.
 * <p />
 * Expects test class to have a method "String[] getClassPrefixes()" to define the class prefixes.
 */
public class TransformingRunner extends BlockJUnit4ClassRunner {
  /**
   * Constructor.
   *
   * @param clazz Test class.
   * @throws InitializationError in case of all errors.
   */
  public TransformingRunner(Class<?> clazz) throws InitializationError {
    super(loadClass(clazz));
  }

  /**
   * Construct the {@link TransformingClassLoader} and load test class with it.
   *
   * @param clazz Test class.
   * @return Test class loaded with the {@link TransformingClassLoader}.
   * @throws InitializationError in case of all errors.
   */
  static Class<?> loadClass(Class<?> clazz) throws InitializationError {
    TransformingClassLoader classLoader = new TransformingClassLoader(getStrategy(clazz), getClassPrefixes(clazz));
    try {
      return Class.forName(clazz.getName(), true, classLoader);
    } catch (Exception e) {
      throw new InitializationError(e);
    }
  }

  /**
   * Get transforming strategy for test class.
   *
   * @param clazz Test class.
   * @throws InitializationError in case of all errors.
   */
  private static IStrategy getStrategy(Class<?> clazz) throws InitializationError {
    try {
      Method method = clazz.getMethod("getStrategy");
      return (IStrategy) method.invoke(null);
    } catch (Exception e) {
      throw new InitializationError(
        "The test class has to implement a method 'public static IStrategy getStrategy()' returning the transforming strategy. " +
        "Failed to access transforming strategy: " + e.getMessage());
    }
  }

  /**
   * Get class prefixes for test class.
   *
   * @param clazz Test class.
   * @throws InitializationError in case of all errors.
   */
  private static String[] getClassPrefixes(Class<?> clazz) throws InitializationError {
    try {
      Method method = clazz.getMethod("getClassPrefixes");
      return (String[]) method.invoke(null);
    } catch (Exception e) {
      throw new InitializationError(
        "The test class has to implement a method 'public static String[] getClassPrefixes()' returning the class prefixes. " +
        "Failed to access class prefixes: " + e.getMessage());
    }
  }
}