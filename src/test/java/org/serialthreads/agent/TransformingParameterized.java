package org.serialthreads.agent;

import org.junit.runners.Parameterized;

/**
 * Used to combined {@link TransformingRunner} with {@llink Parameterized}.
 */
public class TransformingParameterized extends Parameterized {
  /**
   * Only called reflectively. Do not use programmatically.
   *
   * @param klass
   */
  public TransformingParameterized(Class<?> klass) throws Throwable {
    super(TransformingRunner.loadClass(klass));
  }
}
