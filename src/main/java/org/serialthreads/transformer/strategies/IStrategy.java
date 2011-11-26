package org.serialthreads.transformer.strategies;

import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.classcache.IClassInfoCache;

/**
 * Transformation strategy.
 */
public interface IStrategy
{
  /**
   * Create a new transformer.
   *
   * @param classInfoCache ClassInfoCache
   */
  public ITransformer getTransformer(IClassInfoCache classInfoCache);
}
