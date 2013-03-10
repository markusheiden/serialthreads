package org.serialthreads;

import java.lang.annotation.*;

/**
 * Marker annotation for interruptible methods.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Interruptible {
}
