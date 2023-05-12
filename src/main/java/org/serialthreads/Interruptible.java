package org.serialthreads;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marker annotation for interruptible methods.
 */
@Documented
@Retention(RUNTIME)
@Target({METHOD})
public @interface Interruptible {
}
