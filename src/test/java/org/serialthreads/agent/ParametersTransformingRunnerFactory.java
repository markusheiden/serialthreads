package org.serialthreads.agent;

import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

/**
 * {@link ParametersRunnerFactory} to be able to use {@link TransformingRunner} with {@llink Parameterized}.
 */
public class ParametersTransformingRunnerFactory implements ParametersRunnerFactory {
  @Override
  public BlockJUnit4ClassRunnerWithParameters createRunnerForTestWithParameters(TestWithParameters test) throws InitializationError {
    return new BlockJUnit4ClassRunnerWithParameters(loadClass(test));
  }

  /**
   * Construct the {@link TransformingClassLoader} and load test class with it.
   *
   * @param test Test class.
   * @return Test class loaded with the {@link TransformingClassLoader}.
   * @throws InitializationError in case of all errors.
   */
  private static TestWithParameters loadClass(TestWithParameters test) throws InitializationError {
    Class<?> clazz = TransformingRunner.loadClass(test.getTestClass().getJavaClass());
    return new TestWithParameters(test.getName(), new TestClass(clazz), test.getParameters());
  }
}
