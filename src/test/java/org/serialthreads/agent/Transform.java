package org.serialthreads.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.serialthreads.transformer.ITransformer;

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
