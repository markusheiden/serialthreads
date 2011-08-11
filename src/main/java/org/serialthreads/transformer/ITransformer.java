package org.serialthreads.transformer;

import org.ow2.asm.tree.ClassNode;

/**
 * Byte code transformer.
 */
public interface ITransformer
{
  /**
   * Execute byte code transformation on a class.
   *
   * @param clazz class to be transformed
   */
  public void transform(ClassNode clazz);
}
