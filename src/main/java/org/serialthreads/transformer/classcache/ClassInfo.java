package org.serialthreads.transformer.classcache;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Type;
import org.serialthreads.Executor;
import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.transformer.NotTransformableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Class info for scanned classes.
 *
 * @param isInterface
 *         The class is an interface.
 * @param type
 *         ASM type representation for this class.
 * @param className
 *         (Internal) name of this class.
 * @param superClassName
 *         (Internal) name of direct super class. {@code null} for {@link Object}.
 * @param classes
 *         All super classes or interfaces this class extends or implements.
 * @param methods
 *         All methods of this class and its super classes.
 * @param interruptible
 *         The class has at least one interruptible method.
 */
public record ClassInfo(
        boolean isInterface,
        Type type,
        String className,
        @Nullable Type superType,
        @Nullable String superClassName,
        Set<String> classes,
        Map<String, MethodInfo> methods,
        boolean interruptible) {
    /**
     * Logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(ClassInfo.class);

    private static final Type TYPE_EXECUTOR = Type.getType(Executor.class);
    private static final Type TYPE_INTERRUPTIBLE = Type.getType(Interruptible.class);
    private static final Type TYPE_INTERRUPT = Type.getType(Interrupt.class);

    /**
     * Constructor.
     *
     * @param isInterface
     *         Is this class an interface?
     * @param className
     *         Internal name of this class.
     * @param superClassName
     *         Internal name of the direct super class.
     * @param methods
     *         Methods directly defined in the class and their interruptible status.
     */
    public ClassInfo(boolean isInterface, String className, @Nullable String superClassName, Map<String, MethodInfo> methods) {
        var allClasses = new HashSet<String>();
        allClasses.add(className);
        var allMethods = new HashMap<>(methods);
        var anyMethodIsInterruptible = methods.values().stream()
                .anyMatch(method -> isInterruptible(method) || isInterrupt(method));
        this(
                isInterface,
                Type.getObjectType(className),
                className,
                superClassName != null ? Type.getObjectType(superClassName) : null,
                superClassName,
                allClasses,
                allMethods,
                anyMethodIsInterruptible);

        for (var method : methods.values()) {
            if (isInterrupt(method) && !method.getDesc().equals("()V")) {
                throw new NotTransformableException(
                        "Interrupt method " + method.getId() + " in class " + className +
                        " must not have parameters nor a return value");
            }
        }
    }

    /**
     * Extends or implements this class the given super class or interface?.
     *
     * @param superClassName
     *         Name of super class to check.
     */
    public boolean hasSuperClass(String superClassName) {
        return classes.contains(superClassName);
    }

    /**
     * Is the given method of this class an executor?.
     *
     * @param methodId
     *         Method ID = name + desc.
     */
    public boolean isExecutor(String methodId) {
        return methodInfo(methodId).hasAnnotation(TYPE_EXECUTOR);
    }

    /**
     * Is the given method of this class interruptible?.
     *
     * @param methodId
     *         Method ID = name + desc.
     */
    public boolean isInterruptible(String methodId) {
        return isInterruptible(methodInfo(methodId));
    }

    private static boolean isInterruptible(MethodInfo method) {
        return method.hasAnnotation(TYPE_INTERRUPTIBLE);
    }

    /**
     * Is the given method of this class an interrupt?.
     *
     * @param methodId
     *         Method ID = name + desc.
     */
    public boolean isInterrupt(String methodId) {
        return isInterrupt(methodInfo(methodId));
    }

    private static boolean isInterrupt(MethodInfo method) {
        return method.hasAnnotation(TYPE_INTERRUPT);
    }

    /**
     * Info for the given method.
     *
     * @param methodId
     *         Method ID = name + desc.
     */
    @Nullable MethodInfo getMethodInfo(String methodId) {
        return methods.get(methodId);
    }

    /**
     * Info for the given method, which has to exist.
     *
     * @param methodId
     *         Method ID = name + desc.
     */
    private MethodInfo methodInfo(String methodId) {
        return requireNonNull(getMethodInfo(methodId), () -> "Method %s of class %s".formatted(methodId, className));
    }

    /**
     * Merge interruptible status of a scanned class (classInfo) into the status of a subclass (this).
     *
     * @param classInfo
     *         Interruptible status of superclass.
     */
    ClassInfo merge(ClassInfo classInfo) {
        logger.debug("Merging interruptible status of class {} into status of class {}", classInfo.className(), className());

        var allClasses = new HashSet<>(classes);
        allClasses.addAll(classInfo.classes());

        var allMethods = new HashMap<>(methods);
        classInfo.methods().forEach((methodId, method) -> {
            if (method.isPrivate() || method.isInitializer()) {
                // Private methods, constructors and class initializers are not inherited.
                return;
            }

            var ownerMethod = getMethodInfo(methodId);
            if (ownerMethod == null) {
                // Copy inherited method info to this class.
                allMethods.put(methodId, method.copy());
            } else if (method.isStatic() || ownerMethod.isStatic() || ownerMethod.isPrivate()) {
                // The method of this class does not override the method of the super class, it just hides it.
            } else if (isInterruptible(method) != isInterruptible(ownerMethod)) {
                throw new NotTransformableException(
                        "Interruptible status of method " + methodId + " in class " + className() +
                        " does not match its definition in the super class or interface " + classInfo.className());
            } else if (isInterrupt(method) != isInterrupt(ownerMethod)) {
                throw new NotTransformableException(
                        "Interrupt status of method " + methodId + " in class " + className() +
                        " does not match its definition in the super class or interface " + classInfo.className());
            }

            // Executor status need not be checked.
        });

        return new ClassInfo(
                isInterface,
                type,
                className,
                superType,
                superClassName,
                allClasses,
                allMethods,
                interruptible || classInfo.interruptible());
    }
}
