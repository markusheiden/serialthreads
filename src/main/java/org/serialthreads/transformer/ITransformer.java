package org.serialthreads.transformer;

import org.serialthreads.transformer.classcache.IClassInfoCache;

/**
 * Byte code transformer.
 */
public interface ITransformer {
  /**
   * Class infos.
   */
  IClassInfoCache getClassInfoCache();

  /**
   * Execute byte code transformation on a class.
   *
   * @param byteCode Byte code of class to be transformed.
   */
  byte[] transform(byte[] byteCode) throws NotTransformableException;
}
