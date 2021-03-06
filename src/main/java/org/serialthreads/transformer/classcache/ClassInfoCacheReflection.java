package org.serialthreads.transformer.classcache;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.serialthreads.transformer.NotTransformableException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.stream;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

/**
 * Checks and caches which methods are marked as interruptible.
 */
public class ClassInfoCacheReflection extends AbstractClassInfoCache {
  /**
   * Class loader to load class files.
   */
  private ClassLoader classLoader;

  /**
   * Classes with their visitors.
   */
  private final Map<String, ClassInfoVisitor> classes = new HashMap<>();

  /**
   * Start processing for a given class.
   *
   * @param classLoader class loader to use
   * @param className internal name of class
   * @param byteCode byte code of class
   */
  public void start(ClassLoader classLoader, String className, byte[] byteCode) {
    assert classLoader != null : "Precondition: classLoader != null";
    assert className != null : "Precondition: className != null";
    assert byteCode != null : "Precondition: byteCode != null";

    this.classLoader = classLoader;
    classes.put(className, read(new ClassReader(byteCode)));
  }

  /**
   * Stop processing of a given class.
   *
   * @param className internal name of class
   */
  public void stop(String className) {
    classLoader = null;
    classes.remove(className);
  }

  /**
   * Set class loader for tests.
   *
   * @param classLoader class loader to use
   */
  protected void setClassLoader(ClassLoader classLoader) {
    assert classLoader != null : "Precondition: classLoader != null";

    this.classLoader = classLoader;
  }

  @Override
  protected ClassInfo scan(String className, Deque<String> toProcess) throws IOException {
    logger.debug("Scanning class {}", className);

    // remove class info visitor, because we scan a class at max once
    ClassInfoVisitor classInfoVisitor = classes.remove(className);
    if (classInfoVisitor != null) {
      // scan not yet loaded class with asm to avoid circular class loading
      logger.debug("  Direct ASM scan of {}", className);
      return scan(classInfoVisitor, toProcess);
    }

    InputStream classFile = classLoader.getResourceAsStream(className + ".class");
    if (classFile != null) {
      logger.debug("  Class file based ASM scan of {}", className);
      return scan(read(new ClassReader(classFile)), toProcess);
    }

    logger.error("  Reflection scan of {}", className);
    return scanReflection(classLoader, className, toProcess);
  }

  /**
   * Scan a given class for interruptible methods.
   * Does NOT do a deep scan!
   * Uses reflection.
   *
   * @param classLoader class loader to use
   * @param className name of class to scan
   * @param toProcess further classes to scan, will be filled with super class and interfaces of scanned class
   */
  private ClassInfo scanReflection(ClassLoader classLoader, String className, Deque<String> toProcess) {
    try {
      // scan all other classes via reflection, because not all other classes have class files
      Class<?> clazz = Class.forName(className.replace('/', '.'), false, classLoader);

      String superClassName = null;
      if (clazz.getSuperclass() != null) {
        superClassName = clazz.getSuperclass().getName().replace('.', '/');
      }
      Map<String, MethodInfo> methodInfos = new HashMap<>();
      Method[] methods = clazz.getDeclaredMethods();
      for (Method method : methods) {
        String name = method.getName();
        String desc = Type.getMethodDescriptor(method);
        Set<Type> annotations = stream(method.getAnnotations())
          .map(Annotation::getClass)
          .map(Type::getType)
          .collect(toSet());

        MethodInfo info = new MethodInfo(name, desc, annotations);
        methodInfos.put(info.getId(), info);
      }

      Constructor<?>[] constructors = clazz.getDeclaredConstructors();
      for (Constructor<?> constructor : constructors) {
        String name = "<init>";
        String desc = Type.getConstructorDescriptor(constructor);
        Set<Type> annotations = emptySet();

        MethodInfo info = new MethodInfo(name, desc, annotations);
        methodInfos.put(info.getId(), info);
      }

      String name = "<clinit>";
      String desc = "()V";
      Set<Type> annotations = emptySet();

      MethodInfo info = new MethodInfo(name, desc, annotations);
      methodInfos.put(info.getId(), info);

      ClassInfo classInfo = new ClassInfo(clazz.isInterface(), className, superClassName, methodInfos);
      if (clazz.getSuperclass() != null) {
        toProcess.addFirst(clazz.getSuperclass().getName().replace('.', '/'));
      }
      for (Class<?> superInterface : clazz.getInterfaces()) {
        toProcess.add(superInterface.getName().replace('.', '/'));
      }

      return classInfo;
    } catch (ClassNotFoundException e) {
      throw new NotTransformableException("Class not found", e);
    }
  }
}
