package org.serialthreads;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for tagging method, which are able to execute serial threads.
 * Methods annotated with this annotation are the only methods allowed to call
 * ITransformedRunnable#run().
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Executor
{
}
