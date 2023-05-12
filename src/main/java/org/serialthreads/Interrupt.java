package org.serialthreads;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marker annotation for methods representing an interrupt.
 * Calls to methods annotated with this annotations will be replaced by an interrupt.
 */
@Documented
@Retention(RUNTIME)
@Target({METHOD})
public @interface Interrupt {
}
