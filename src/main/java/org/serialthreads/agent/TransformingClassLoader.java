package org.serialthreads.agent;

import org.serialthreads.transformer.IStrategy;
import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.LoadUntransformedException;
import org.serialthreads.transformer.NotTransformableException;
import org.serialthreads.transformer.classcache.ClassInfoCacheASM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * ClassLoader which applies a transformer to the loaded classes.
 */
public class TransformingClassLoader extends ClassLoader {
  /**
   * Logger.
   */
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final String[] classPrefixes;
  private final ITransformer transformer;

  /**
   * Constructor using the system class loader as parent.
   *
   * @param strategy transformation strategy
   * @param classPrefixes prefixes of class to transform
   */
  public TransformingClassLoader(IStrategy strategy, String... classPrefixes) {
    this(Thread.currentThread().getContextClassLoader(), strategy, classPrefixes);
  }

  /**
   * Constructor using a given class loader as parent.
   *
   * @param parent parent class loader
   * @param strategy transformation strategy
   * @param classPrefixes prefixes of class to transform
   */
  public TransformingClassLoader(ClassLoader parent, IStrategy strategy, String... classPrefixes) {
    super("serialthreads", parent);

    this.classPrefixes = new String[1 + classPrefixes.length];
    this.classPrefixes[0] = "org.serialthreads.";
    System.arraycopy(classPrefixes, 0, this.classPrefixes, 1, classPrefixes.length);
    this.transformer = strategy.getTransformer(new ClassInfoCacheASM(this));
  }

  /**
   * {@inheritDoc}
   * <p>
   * Tries to transform class first, if possible.
   */
  @Override
  protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    var result = findLoadedClass(name);
    if (result == null) {
      byte[] byteCode = null;

      try {
        if (!shouldBeTransformed(name)) {
          // Use the default behaviour for all classes not directly marked as interruptible.
          return super.loadClass(name, resolve);
        }

        byteCode = loadByteCode(name);
        if (byteCode == null) {
          // use default behaviour for all classes that could not be loaded directly
          return super.loadClass(name, resolve);
        }

        byteCode = transformer.transform(byteCode);
//        FileCopyUtils.copy(byteCode, new File("./" + name + ".class"));

      } catch (LoadUntransformedException e) {
        // load class, because class should use this classloader to load referenced classes
      } catch (NotTransformableException e) {
        throw new ClassNotFoundException("Class " + name + " has transformation errors", e);
      } catch (IOException e) {
        throw new ClassNotFoundException("Class file for " + name + " could not be read", e);
      }

      result = defineClass(name, byteCode, 0, byteCode.length);
    }

    if (resolve) {
      resolveClass(result);
    }

    return result;
  }

  /**
   * Load byte code of the given class.
   *
   * @param name name of class
   * @return byte code or null, if class file does not exist
   * @throws IOException in case of IO exception while reading the class file
   */
  private byte[] loadByteCode(String name) throws IOException {
    // class content
    var in = getResourceAsStream(name.replace('.', '/') + ".class");
    if (in == null) {
      return null;
    }

    // byte buffer
    var out = new ByteArrayOutputStream();

    // try to load the byte code
    try {
      var buffer = new byte[16384];
      for (int bytesRead; (bytesRead = in.read(buffer)) != -1; ) {
        out.write(buffer, 0, bytesRead);
      }
      out.flush();
      return out.toByteArray();
    } finally {
      try {
        in.close();
      } catch (IOException e) {
        logger.warn("Unable to close InputStream", e);
      }
      try {
        out.close();
      } catch (IOException e) {
        logger.warn("Unable to close OutputStream", e);
      }
    }
  }

  /**
   * Should this class be transformed.
   *
   * @param className fully qualified name of class
   */
  protected boolean shouldBeTransformed(String className) {
    for (var classPrefix : classPrefixes) {
      if (className.startsWith(classPrefix)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public String toString() {
    return transformer.toString() + " via class loader.";
  }
}
