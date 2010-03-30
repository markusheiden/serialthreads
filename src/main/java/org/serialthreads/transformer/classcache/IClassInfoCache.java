package org.serialthreads.transformer.classcache;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Interface for class info cache.
 */
public interface IClassInfoCache
{
  /**
   * Is the class an interface.
   *
   * @param clazz class
   */
  public boolean isInterface(String clazz);

  /**
   * Get direct super class of this class.
   *
   * @param clazz internal name of class
   */
  public Type getSuperClass(String clazz);

  /**
   * Extends / implements this class the given super class or interface.
   *
   * @param clazz internal name of class
   * @param superClass super class to check class for
   */
  public boolean hasSuperClass(String clazz, String superClass);

  /**
   * Check if the method is an executor.
   *
   * @param clazz owner of method
   * @param method method
   */
  public boolean isExecutor(ClassNode clazz, MethodNode method);

  /**
   * Check if method is interruptible.
   *
   * @param owner owner of method
   * @param method method
   */
  public boolean isInterruptible(ClassNode owner, MethodNode method);

  /**
   * Check if the called method is interruptible.
   *
   * @param method method call
   */
  public boolean isInterruptible(MethodInsnNode method);

  /**
   * Check if method is interruptible.
   *
   * @param owner owner of method
   * @param name name of method
   * @param desc descriptor of method
   */
  public boolean isInterruptible(String owner, String name, String desc);

  /**
   * Check if the called method is an interrupt.
   *
   * @param method method call
   */
  public boolean isInterrupt(MethodInsnNode method);
}
