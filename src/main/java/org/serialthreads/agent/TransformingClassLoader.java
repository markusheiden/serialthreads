package org.serialthreads.agent;

import org.serialthreads.transformer.IStrategy;
import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.LoadUntransformedException;
import org.serialthreads.transformer.NotTransformableException;
import org.serialthreads.transformer.classcache.ClassInfoCacheASM;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.security.cert.Certificate;

/**
 * ClassLoader which applies a transformer to the loaded classes.
 */
public class TransformingClassLoader extends SecureClassLoader {
  static {
    // Classes are loaded with a lock per class name instead of a lock for the whole class loader.
    registerAsParallelCapable();
  }

  private final String[] classPrefixes;
  private final ITransformer transformer;

  /**
   * Constructor using the context class loader of the current thread as parent.
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
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    if (!shouldBeTransformed(name)) {
      // Use the default behaviour for all classes not directly marked as interruptible.
      return super.loadClass(name, resolve);
    }

    synchronized (getClassLoadingLock(name)) {
      var result = findLoadedClass(name);
      if (result == null) {
        var resourceName = name.replace('.', '/') + ".class";
        var resource = getResource(resourceName);
        if (resource == null) {
          // use default behaviour for all classes that could not be loaded directly
          return super.loadClass(name, resolve);
        }

        try {
          var byteCode = loadByteCode(resource);
          try {
            byteCode = transformer.transform(byteCode);
          } catch (LoadUntransformedException e) {
            // load class, because class should use this classloader to load referenced classes
          }

          result = defineClass(name, byteCode, 0, byteCode.length, codeSource(resource, resourceName));
        } catch (NotTransformableException e) {
          throw new ClassNotFoundException("Class " + name + " has transformation errors", e);
        } catch (IOException e) {
          throw new ClassNotFoundException("Class file for " + name + " could not be read", e);
        }
      }

      if (resolve) {
        resolveClass(result);
      }

      return result;
    }
  }

  /**
   * Load byte code of the given class.
   *
   * @param resource class file
   * @return byte code
   * @throws IOException in case of IO exception while reading the class file
   */
  private byte[] loadByteCode(URL resource) throws IOException {
    try (var in = resource.openStream()) {
      return in.readAllBytes();
    }
  }

  /**
   * Code source of a class: The location of its class file without the name of the class file.
   *
   * @param resource class file
   * @param resourceName name of class file relative to its code source
   * @throws IOException if the location is no valid URL
   */
  private CodeSource codeSource(URL resource, String resourceName) throws IOException {
    var url = resource.toExternalForm();
    var location = url.substring(0, url.length() - resourceName.length());
    return new CodeSource(URI.create(location).toURL(), (Certificate[]) null);
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
