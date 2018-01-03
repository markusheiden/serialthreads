package org.serialthreads.transformer;

import org.serialthreads.transformer.classcache.IClassInfoCache;

/**
 * Transformation strategy.
 */
public interface IStrategy {
  /**
   * Create a new transformer.
   *
   * @param classInfoCache ClassInfoCache
   */
  ITransformer getTransformer(IClassInfoCache classInfoCache);
}
