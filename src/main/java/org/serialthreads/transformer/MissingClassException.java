package org.serialthreads.transformer;

/**
 * Exception to abort class transformation, because a referenced class has not been found.
 * So it is unknown, whether the referencing class needs to be transformed.
 */
public class MissingClassException extends NotTransformableException {
    public MissingClassException(String message, Throwable cause) {
        super(message, cause);
    }
}
