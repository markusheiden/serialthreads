package org.serialthreads.transformer;

/**
 * Byte code transformer.
 */
public interface ITransformer {
  /**
   * Trace generated byte code to {@link System#out}.
   */
  void trace();

  /**
   * Execute byte code transformation on a class.
   *
   * @param byteCode byte code of class to be transformed
   */
  byte[] transform(byte[] byteCode) throws NotTransformableException;
}
