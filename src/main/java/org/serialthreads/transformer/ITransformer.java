package org.serialthreads.transformer;

import org.objectweb.asm.tree.ClassNode;

/**
 * Byte code transformer.
 */
public interface ITransformer
{
  /**
   * Execute byte code transformation on a class.
   */
  public void transform(ClassNode clazz);
}
