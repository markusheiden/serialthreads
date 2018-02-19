package org.serialthreads.transformer.classcache;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Interface for class info cache.
 */
public interface IClassInfoCache {
  /**
   * Is the class an interface.
   *
   * @param clazz class
   */
  boolean isInterface(String clazz);

  /**
   * Get direct super class of this class.
   *
   * @param clazz internal name of class
   */
  Type getSuperClass(String clazz);

  /**
   * Extends / implements this class the given super class or interface.
   *
   * @param clazz internal name of class
   * @param superClass super class to check class for
   */
  boolean hasSuperClass(String clazz, String superClass);

  /**
   * Check if the method is an executor.
   *
   * @param clazz owner of method
   * @param method method
   */
  boolean isExecutor(ClassNode clazz, MethodNode method);

  /**
   * Check if the method is interruptible.
   * This includes interrupt methods.
   *
   * @param owner owner of method
   * @param method method
   */
  boolean isInterruptible(ClassNode owner, MethodNode method);

  /**
   * Check if the called method is interruptible.
   * This includes interrupt methods.
   *
   * @param method method call
   */
  boolean isInterruptible(MethodInsnNode method);

  /**
   * Check if the method is interruptible.
   * This includes interrupt methods.
   *
   * @param method method call
   */
  boolean isInterruptible(Handle method);

  /**
   * Check if method is interruptible.
   * This includes interrupt methods.
   *
   * @param owner owner of method
   * @param name name of method
   * @param desc descriptor of method
   */
  boolean isInterruptible(String owner, String name, String desc);

  /**
   * Check if the called method is an interrupt.
   *
   * @param method method call
   */
  boolean isInterrupt(MethodInsnNode method);

  /**
   * Has the class at least one interruptible method?.
   */
  boolean isInterruptible(Type type);
}
