package org.serialthreads.transformer.classcache;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.objectweb.asm.Type;
import org.serialthreads.Executor;
import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.transformer.NotTransformableException;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Class info for scanned classes.
 */
public class ClassInfo {
  private final Log logger = LogFactory.getLog(getClass());

  private static final Type TYPE_EXECUTOR = Type.getType(Executor.class);
  private static final Type TYPE_INTERRUPTIBLE = Type.getType(Interruptible.class);
  private static final Type TYPE_INTERRUPT = Type.getType(Interrupt.class);

  private final boolean isInterface;
  private final Type type;
  private final String className;
  private final String superClassName;
  private final Set<String> superClasses;
  private final Map<String, MethodInfo> methods;

  /**
   * Constructor.
   *
   * @param isInterface Is this class an interface?
   * @param className Internal name of this class
   * @param superClassName Internal name of the direct super class
   * @param methods Methods directly defined in the class and their interruptible status
   */
  public ClassInfo(boolean isInterface, String className, String superClassName, Map<String, MethodInfo> methods) {
    this.isInterface = isInterface;
    this.type = Type.getObjectType(className);
    this.className = className;
    this.superClassName = superClassName;
    this.superClasses = new TreeSet<>();
    this.methods = new TreeMap<>(methods);

    superClasses.add(className);

    for (MethodInfo method : methods.values()) {
      if (method.hasAnnotation(TYPE_INTERRUPT) && !method.getDesc().equals("()V")) {
        throw new NotTransformableException(
          "Interrupt method " + method.getID() + " in class " + className +
            " must not have parameters nor a return value");
      }
    }
  }

  /**
   * Is the class an interface?.
   */
  public boolean isInterface() {
    return isInterface;
  }

  /**
   * Get ASM type representation fpr this class.
   */
  public Type getType() {
    return type;
  }

  /**
   * (Internal) name of this class.
   */
  public String getClassName() {
    return className;
  }

  /**
   * (Internal) name of direct super class.
   *
   * @return internal of super or null for java/lang/Object
   */
  public String getSuperClassName() {
    return superClassName;
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
  public final boolean isInterrupt(String methodId) {
    return getMethodInfo(methodId).hasAnnotation(TYPE_INTERRUPT);
  }

  /**
   * Info for the given method.
   *
   * @param methodId method ID = name + desc
   */
  protected MethodInfo getMethodInfo(String methodId) {
    return methods.get(methodId);
  }

  /**
   * All methods IDs of this class.
   */
  protected Set<String> getMethods() {
    return methods.keySet();
  }

  /**
   * Extends or implements this class the given super class or interface?.
   *
   * @param superClassName name of super class to check
   */
  public boolean hasSuperClass(String superClassName) {
    return superClasses.contains(superClassName);
  }

  /**
   * Add a super class or interface this class extends or implements.
   *
   * @param superClassName name of super class
   */
  protected void addSuperClass(String superClassName) {
    superClasses.add(superClassName);
  }

  /**
   * All super classes or interfaces this class extends or implements.
   */
  public Set<String> getSuperClasses() {
    return superClasses;
  }

  /**
   * Merge interruptible status of a scanned class (classInfo) into the status of a subclass (this).
   *
   * @param classInfo interruptible status of superclass
   */
  protected void merge(ClassInfo classInfo) {
    if (logger.isDebugEnabled()) {
      logger.debug("Merging interruptible status of class " + classInfo.getClassName() + " into status of class " + getClassName());
    }

    superClasses.addAll(classInfo.getSuperClasses());
    for (String methodId : classInfo.getMethods()) {
      MethodInfo method = classInfo.getMethodInfo(methodId);
      MethodInfo ownerMethod = getMethodInfo(methodId);
      if (ownerMethod == null) {
        // copy inherited method info to this class
        ownerMethod = method.copy();
        methods.put(methodId, ownerMethod);
      } else if (method.hasAnnotation(TYPE_INTERRUPTIBLE) != ownerMethod.hasAnnotation(TYPE_INTERRUPTIBLE)) {
        throw new NotTransformableException(
          "Interruptible status of method " + methodId + " in class " + getClassName() +
            " does not match its definition in the super class or interface " + classInfo.getClassName());
      } else if (method.hasAnnotation(TYPE_INTERRUPT) != ownerMethod.hasAnnotation(TYPE_INTERRUPT)) {
        throw new NotTransformableException(
          "Interrupt status of method " + methodId + " in class " + getClassName() +
            " does not match its definition in the super class or interface " + classInfo.getClassName());
      }

      // executor status need not be checked
    }
  }
}
