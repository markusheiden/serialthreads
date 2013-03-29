package org.serialthreads.transformer;

/**
 * Exception to abort class transformation of classes which need no transformation,
 * but should be loaded by the transforming classloader.
 */
public class LoadUntransformedException extends RuntimeException {
  public LoadUntransformedException(String name) {
    super("Class " + name + " needs no transformation");
  }
}