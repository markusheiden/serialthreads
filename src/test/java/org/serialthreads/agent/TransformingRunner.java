package org.serialthreads.agent;

import org.junit.runner.Runner;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;
import org.serialthreads.transformer.IStrategy;
import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.classcache.IClassInfoCache;

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
    Transform annotation = clazz.getAnnotation(Transform.class);
    if (annotation == null) {
      throw new InitializationError("Missing @Transform at test.");
    }
    if (annotation.transformer() == null) {
      throw new InitializationError("Transformer class not configured in @Transform.");
    }
    if (annotation.classPrefixes() == null) {
      throw new InitializationError("Class prefixes not configured in @Transform.");
    }

    try {
      Class<? extends ITransformer> transformer = annotation.transformer();
      String[] classPrefixes = annotation.classPrefixes();
      TransformingClassLoader classLoader = new TransformingClassLoader(new Strategy(transformer), classPrefixes);
      if (annotation.trace()) {
        classLoader.trace();
      }
      return Class.forName(clazz.getName(), true, classLoader);
    } catch (Exception e) {
      throw new InitializationError(e);
    }
  }

  /**
   * Transformation strategy.
   */
  private static class Strategy implements IStrategy {
    /**
     * Transformer class.
     */
    private final Class<? extends ITransformer> transformerClass;

    /**
     * Constructor.
     *
     * @param transformerClass Transformer class.
     */
    public Strategy(Class<? extends ITransformer> transformerClass) {
      this.transformerClass = transformerClass;
    }

    @Override
    public ITransformer getTransformer(IClassInfoCache classInfoCache) {
      try {
        return transformerClass.getConstructor(IClassInfoCache.class).newInstance(classInfoCache);
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid transformer specified.", e);
      }
    }
  }
}