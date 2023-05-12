package org.serialthreads;

import java.lang.annotation.*;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation for tagging method, which are able to execute serial threads.
 * Methods annotated with this annotation are the only methods allowed to call
 * ITransformedRunnable#run().
 */
@Documented
@Retention(RUNTIME)
@Target({METHOD})
public @interface Executor {
}
