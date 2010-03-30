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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Check and caches which methods are marked as interruptible.
 */
public class ClassInfoCacheReflection extends AbstractClassInfoCache
{
  private ClassLoader _classLoader;
  private final Map<String, ClassLoader> _classLoaders = new HashMap<String, ClassLoader>();
  private final Map<String, ClassInfoVisitor> _classes = new HashMap<String, ClassInfoVisitor>();

  /**
   * Start processing for a given class.
   *
   * @param classLoader class loader to use
   * @param className internal name of class
   * @param byteCode byte code of class
   */
  public void start(ClassLoader classLoader, String className, byte[] byteCode)
  {
    assert classLoader != null : "Precondition: classLoader != null";
    assert className != null : "Precondition: className != null";
    assert byteCode != null : "Precondition: byteCode != null";

    _classLoader = classLoader;
    _classLoaders.put(className, classLoader);
    _classes.put(className, read(new ClassReader(byteCode)));
  }

  /**
   * Stop processing of a given class.
   *
   * @param className internal name of class
   */
  public void stop(String className)
  {
    _classLoader = null;
    _classLoaders.remove(className);
    _classes.remove(className);
  }

  /**
   * Set class loader for tests.
   *
   * @param classLoader class loader to use
   */
  protected void setClassLoader(ClassLoader classLoader)
  {
    assert classLoader != null : "Precondition: classLoader != null";

    _classLoader = classLoader;
  }

  @Override
  protected ClassInfo scan(String className, Deque<String> toProcess) throws IOException
  {
    if (logger.isDebugEnabled())
    {
      logger.debug("Scanning class " + className);
    }

    // remove class info visitor, because we scan a class at max once
    ClassInfoVisitor classInfoVisitor = _classes.remove(className);
    if (classInfoVisitor != null)
    {
      // scan not yet loaded class with asm to avoid class loading circles
      logger.debug("  Direct ASM scan of " + className);
      return scan(classInfoVisitor, toProcess);
    }

    InputStream classFile = _classLoader.getResourceAsStream(className + ".class");
    if (classFile != null)
    {
      logger.debug("  Class file based ASM scan of " + className);
      return scan(read(new ClassReader(classFile)), toProcess);
    }

    logger.error("  Reflection scan of " + className);
    return scanReflection(_classLoader, className, toProcess);
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
  private ClassInfo scanReflection(ClassLoader classLoader, String className, Deque<String> toProcess)
  {
    try
    {
      // scan all other classes via reflection, because not all other classes have class files
      Class<?> clazz = Class.forName(className.replace('/', '.'), false, classLoader);

      String superClassName = null;
      if (clazz.getSuperclass() != null)
      {
        clazz.getSuperclass().getName().replace('.', '/');
      }
      Map<String, MethodInfo> methodInfos = new HashMap<String, MethodInfo>();
      Method[] methods = clazz.getDeclaredMethods();
      for (Method method : methods)
      {
        String name = method.getName();
        String desc = Type.getMethodDescriptor(method);
        Set<String> annotations = new HashSet<String>();
        for (Annotation annotation : method.getAnnotations())
        {
          annotations.add(Type.getDescriptor(annotation.getClass()));
        }

        MethodInfo info = new MethodInfo(name, desc, annotations);
        methodInfos.put(info.getID(), info);
      }

      Constructor<?>[] constructors = clazz.getDeclaredConstructors();
      for (Constructor<?> constructor : constructors)
      {
        String name = "<init>";
        String desc = Type.getConstructorDescriptor(constructor);
        Set<String> annotations = new HashSet<String>();

        MethodInfo info = new MethodInfo(name, desc, annotations);
        methodInfos.put(info.getID(), info);
      }

      String name = "<clinit>";
      String desc = "()V";
      Set<String> annotations = new HashSet<String>();

      MethodInfo info = new MethodInfo(name, desc, annotations);
      methodInfos.put(info.getID(), info);

      ClassInfo classInfo = new ClassInfo(clazz.isInterface(), className, superClassName, methodInfos);
      if (clazz.getSuperclass() != null)
      {
        toProcess.addFirst(clazz.getSuperclass().getName().replace('.', '/'));
      }
      for (Class<?> superInterface : clazz.getInterfaces())
      {
        toProcess.add(superInterface.getName().replace('.', '/'));
      }

      return classInfo;
    }
    catch (ClassNotFoundException e)
    {
      throw new NotTransformableException("Class not found", e);
    }
  }
}
