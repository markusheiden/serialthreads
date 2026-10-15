package org.serialthreads.agent;

import org.junit.jupiter.api.extension.ExtendWith;
import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.strategies.frequent3.FrequentInterruptsTransformer3;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Transformation parameters.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(TransformingExtension.class)
public @interface Transform {
  /**
   * Transformer class.
   */
  Class<? extends ITransformer> transformer() default FrequentInterruptsTransformer3.class;

  /**
   * Prefixes of classes to transform. "org.serialthreads." will always be added.
   */
  String[] classPrefixes() default {};
}
