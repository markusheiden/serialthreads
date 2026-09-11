package org.serialthreads.transformer.classcache;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.serialthreads.transformer.NotTransformableException;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Arrays.stream;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

/**
 * Checks and caches which methods are marked as interruptible.
 */
public class ClassInfoCacheReflection extends AbstractClassInfoCache {
  /**
   * Classes with their visitors.
   */
  private final Map<String, ClassInfoVisitor> classVisitors = new ConcurrentHashMap<>();

  /**
   * Cosntructor.
   */
  public ClassInfoCacheReflection(ClassLoader classLoader) {
    super(classLoader);
  }

  /**
   * Start processing for a given class.
   *
   * @param className internal name of class
   * @param byteCode byte code of class
   */
  public void start(String className, byte[] byteCode) {
    assert className != null : "Precondition: className != null";
    assert byteCode != null : "Precondition: byteCode != null";

    classVisitors.put(className, read(new ClassReader(byteCode)));
  }

  /**
   * Stop processing of a given class.
   *
   * @param className internal name of class
   */
  public void stop(String className) {
    classVisitors.remove(className);
  }

  @Override
  protected ClassInfo scan(String className, Deque<String> toProcess) throws IOException {
    logger.debug("Scanning class {}", className);

    // Remove class info visitor, because we scan a class at max once.
    var classInfoVisitor = classVisitors.remove(className);
    if (classInfoVisitor != null) {
      // Scan not yet loaded class with asm to avoid circular class loading.
      logger.debug("  Direct ASM scan of {}", className);
      return scan(classInfoVisitor, toProcess);
    }

    try (var classFile = classLoader.getResourceAsStream(className + ".class")) {
      if (classFile != null) {
        logger.debug("  Class file based ASM scan of {}", className);
        return scan(read(new ClassReader(classFile)), toProcess);
      }
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
      // Scan all other classes via reflection, because not all other classes have class files.
      var clazz = Class.forName(className.replace('/', '.'), false, classLoader);

      String superClassName = null;
      if (clazz.getSuperclass() != null) {
        superClassName = clazz.getSuperclass().getName().replace('.', '/');
      }
      var methodInfos = new HashMap<String, MethodInfo>();
      var methods = clazz.getDeclaredMethods();
      for (var method : methods) {
        var name = method.getName();
        var desc = Type.getMethodDescriptor(method);
        var annotations = stream(method.getAnnotations())
          .map(Annotation::annotationType)
          .map(Type::getType)
          .collect(toSet());

        var info = new MethodInfo(name, desc, annotations);
        methodInfos.put(info.getId(), info);
      }

      var constructors = clazz.getDeclaredConstructors();
      for (var constructor : constructors) {
        var name = "<init>";
        var desc = Type.getConstructorDescriptor(constructor);
        Set<Type> annotations = emptySet();

        var info = new MethodInfo(name, desc, annotations);
        methodInfos.put(info.getId(), info);
      }

      var name = "<clinit>";
      var desc = "()V";
      Set<Type> annotations = emptySet();

      var info = new MethodInfo(name, desc, annotations);
      methodInfos.put(info.getId(), info);

      var classInfo = new ClassInfo(clazz.isInterface(), className, superClassName, methodInfos);
      if (clazz.getSuperclass() != null) {
        toProcess.addFirst(clazz.getSuperclass().getName().replace('.', '/'));
      }
      for (var superInterface : clazz.getInterfaces()) {
        toProcess.add(superInterface.getName().replace('.', '/'));
      }

      return classInfo;
    } catch (ClassNotFoundException e) {
      throw new NotTransformableException("Class not found", e);
    }
  }
}
