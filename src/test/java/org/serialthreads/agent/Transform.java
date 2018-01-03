package org.serialthreads.agent;

import org.serialthreads.transformer.ITransformer;

import java.lang.annotation.*;

/**
 * Transformation parameters.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Transform {
  /**
   * Transformer class.
   */
  Class<? extends ITransformer> transformer();

  /**
   * Prefixes of classes to transform. "org.serialthreads." will be always added.
   */
  String[] classPrefixes() default {};
}
