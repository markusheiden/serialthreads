package org.serialthreads.transformer;

import org.objectweb.asm.tree.ClassNode;

/**
 * Byte code transformer.
 */
public interface ITransformer {
  /**
   * Execute byte code transformation on a class.
   *
   * @param clazz class to be transformed
   */
  void transform(ClassNode clazz);
}
