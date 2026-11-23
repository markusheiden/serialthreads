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
     * Add class info for the given class.
     *
     * @param className
     *         Internal name of class.
     * @param byteCode
     *         Byte code of class.
     */
    void addClassInfo(String className, byte[] byteCode);

    /**
     * Is the class an interface.
     *
     * @param clazz
     *         Class.
     */
    boolean isInterface(String clazz);

    /**
     * Get direct super class of this class.
     *
     * @param clazz
     *         Internal name of class.
     */
    Type getSuperClass(String clazz);

    /**
     * Extends / implements this class the given super class or interface.
     *
     * @param clazz
     *         Internal name of class.
     * @param superClass
     *         Super class to check class for.
     */
    boolean hasSuperClass(String clazz, String superClass);

    /**
     * Check if the method is an executor.
     *
     * @param clazz
     *         Owner of method.
     * @param method
     *         Method.
     */
    boolean isExecutor(ClassNode clazz, MethodNode method);

    /**
     * Check if the method is interruptible.
     * This includes interrupt methods.
     *
     * @param owner
     *         Owner of method.
     * @param method
     *         Method.
     */
    boolean isInterruptible(ClassNode owner, MethodNode method);

    /**
     * Check if the called method is interruptible.
     * This includes interrupt methods.
     *
     * @param method
     *         Method call.
     */
    boolean isInterruptible(MethodInsnNode method);

    /**
     * Check if the method is interruptible.
     * This includes interrupt methods.
     *
     * @param method
     *         Method call.
     */
    boolean isInterruptible(Handle method);

    /**
     * Check if method is interruptible.
     * This includes interrupt methods.
     *
     * @param owner
     *         Owner of method.
     * @param name
     *         Name of method.
     * @param desc
     *         Descriptor of method.
     */
    boolean isInterruptible(String owner, String name, String desc);

    /**
     * Check if the called method is an interrupt.
     *
     * @param method
     *         Method call.
     */
    boolean isInterrupt(MethodInsnNode method);

    /**
     * Has the class at least one interruptible method?.
     */
    boolean isInterruptible(Type type);
}
