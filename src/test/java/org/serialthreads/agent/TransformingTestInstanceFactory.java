package org.serialthreads.agent;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstantiationException;
import org.serialthreads.transformer.IStrategy;
import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.lang.reflect.Constructor;

/**
 * {@link TestInstanceFactory} using a {@link TransformingClassLoader} for loading all classes.
 * <p>
 * Expects test class to have a method "String[] getClassPrefixes()" to define the class prefixes.
 */
public class TransformingTestInstanceFactory implements TestInstanceFactory {
  /**
   * Construct the {@link TransformingClassLoader} and load test class with it.
   *
   * @return Test class loaded with the {@link TransformingClassLoader}.
   * @throws TestInstantiationException in case of all errors.
   */
  @Override
  public Object createTestInstance(TestInstanceFactoryContext factoryContext, ExtensionContext extensionContext) throws TestInstantiationException {
    Class<?> clazz = factoryContext.getTestClass();
    Transform annotation = clazz.getAnnotation(Transform.class);
    if (annotation == null) {
      throw new TestInstantiationException("Missing @Transform at test.");
    }
    if (annotation.transformer() == null) {
      throw new TestInstantiationException("Transformer class not configured in @Transform.");
    }
    if (annotation.classPrefixes() == null) {
      throw new TestInstantiationException("Class prefixes not configured in @Transform.");
    }

    try {
      TransformingClassLoader cl = new TransformingClassLoader(new Strategy(annotation.transformer()), annotation.classPrefixes());
      Class<?> testClass = cl.loadClass(clazz.getName(), true);
      Constructor<?> constructor = testClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (Exception e) {
      throw new TestInstantiationException("Failed to create test instance.", e);
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