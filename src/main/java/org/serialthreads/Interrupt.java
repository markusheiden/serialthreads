package org.serialthreads;

import java.lang.annotation.*;

/**
 * Marker annotation for methods representing an interrupt.
 * Calls to methods annotated with this annotations will be replaced by an interrupt.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Interrupt {
}
