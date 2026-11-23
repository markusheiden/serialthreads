package org.serialthreads;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marker annotation for methods representing an interrupt.
 * Calls to methods annotated with this annotation will be replaced by an interrupt.
 */
@Documented
@Retention(RUNTIME)
@Target({ METHOD })
public @interface Interrupt {
}
