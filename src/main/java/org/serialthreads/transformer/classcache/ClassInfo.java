package org.serialthreads.transformer.classcache;

import org.objectweb.asm.Type;
import org.serialthreads.Executor;
import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.transformer.NotTransformableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Class info for scanned classes.
 *
 * @param isInterface The class is an interface.
 * @param type ASM type representation fpr this class.
 * @param className (Internal) name of this class.
 * @param superClassName (Internal) name of direct super class. {@code null} for {@link Object}.
 * @param classes All super classes or interfaces this class extends or implements.
 * @param methods All methods of this class and its super classes.
 * @param interruptible The class has at least one interruptible method.
 */
public record ClassInfo(
        boolean isInterface,
        Type type,
        String className,
        Type superType,
        String superClassName,
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
   * @param isInterface Is this class an interface?
   * @param className Internal name of this class
   * @param superClassName Internal name of the direct super class
   * @param methods Methods directly defined in the class and their interruptible status
   */
  public ClassInfo(boolean isInterface, String className, String superClassName, Map<String, MethodInfo> methods) {
    var allClasses = new TreeSet<String>();
    allClasses.add(className);
    var allMethods = new TreeMap<>(methods);
    var anyMethodIsInterruptible = methods.values().stream()
            .anyMatch(method -> method.hasAnnotation(TYPE_INTERRUPT) || method.hasAnnotation(TYPE_INTERRUPTIBLE));
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
      if (method.hasAnnotation(TYPE_INTERRUPT) && !method.getDesc().equals("()V")) {
        throw new NotTransformableException(
          "Interrupt method " + method.getId() + " in class " + className +
            " must not have parameters nor a return value");
      }
    }
  }

  /**
   * Is the given method of this class an executor?.
   *
   * @param methodId method ID = name + desc
   */
  public boolean isExecutor(String methodId) {
    return getMethodInfo(methodId).hasAnnotation(TYPE_EXECUTOR);
  }

  /**
   * Is the given method of this class interruptible?.
   *
   * @param methodId method ID = name + desc
   */
  public boolean isInterruptible(String methodId) {
    return getMethodInfo(methodId).hasAnnotation(TYPE_INTERRUPTIBLE);
  }

  /**
   * Is the given method of this class an interrupt?.
   *
   * @param methodId method ID = name + desc
   */
  public boolean isInterrupt(String methodId) {
    return getMethodInfo(methodId).hasAnnotation(TYPE_INTERRUPT);
  }

  /**
   * Info for the given method.
   *
   * @param methodId method ID = name + desc
   */
  MethodInfo getMethodInfo(String methodId) {
    return methods.get(methodId);
  }

  /**
   * Extends or implements this class the given super class or interface?.
   *
   * @param superClassName name of super class to check
   */
  public boolean hasSuperClass(String superClassName) {
    return classes.contains(superClassName);
  }

  /**
   * Merge interruptible status of a scanned class (classInfo) into the status of a subclass (this).
   *
   * @param classInfo interruptible status of superclass
   */
  ClassInfo merge(ClassInfo classInfo) {
    logger.debug("Merging interruptible status of class {} into status of class {}", classInfo.className(), className());

    var allClasses = new TreeSet<>(classes);
    allClasses.addAll(classInfo.classes());

    var allMethods = new TreeMap<>(methods);
    classInfo.methods().forEach((methodId, method) -> {
      var ownerMethod = getMethodInfo(methodId);
      if (ownerMethod == null) {
        // Copy inherited method info to this class.
        ownerMethod = method.copy();
        allMethods.put(methodId, ownerMethod);
      } else if (method.hasAnnotation(TYPE_INTERRUPTIBLE) != ownerMethod.hasAnnotation(TYPE_INTERRUPTIBLE)) {
        throw new NotTransformableException(
          "Interruptible status of method " + methodId + " in class " + className() +
            " does not match its definition in the super class or interface " + classInfo.className());
      } else if (method.hasAnnotation(TYPE_INTERRUPT) != ownerMethod.hasAnnotation(TYPE_INTERRUPT)) {
        throw new NotTransformableException(
          "Interrupt status of method " + methodId + " in class " + className() +
            " does not match its definition in the super class or interface " + classInfo.className());
      }

      // executor status need not be checked
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
