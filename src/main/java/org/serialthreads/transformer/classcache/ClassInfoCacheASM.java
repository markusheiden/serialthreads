package org.serialthreads.transformer.classcache;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Deque;

/**
 * Check and caches which methods are marked as interruptible.
 */
public class ClassInfoCacheASM extends AbstractClassInfoCache
{
  private final ClassLoader _classLoader;

  /**
   * Constructor.
   *
   * @param classLoader class loader for loading classes.
   */
  public ClassInfoCacheASM(ClassLoader classLoader)
  {
    assert classLoader != null : "Precondition: classLoader != null";

    _classLoader = classLoader;
  }

  /**
   * Scan a given class for interruptible methods.
   * Does NOT do a deep scan!
   *
   * @param className name of class to scan
   * @param toProcess further classes to scan, will be filled with super class and interfaces of scanned class
   */
  @Override
  protected ClassInfo scan(String className, Deque<String> toProcess) throws IOException
  {
    if (logger.isDebugEnabled())
    {
      logger.debug("Scanning class " + className);
    }

    InputStream classFile = _classLoader.getResourceAsStream(className + ".class");
    return scan(read(new ClassReader(classFile)), toProcess);
  }
}
