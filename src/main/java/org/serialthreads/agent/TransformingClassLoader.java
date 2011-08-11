package org.serialthreads.agent;

import org.apache.log4j.Logger;
import org.ow2.asm.ClassReader;
import org.ow2.asm.ClassVisitor;
import org.ow2.asm.ClassWriter;
import org.ow2.asm.util.TraceClassVisitor;
import org.serialthreads.transformer.IStrategy;
import org.serialthreads.transformer.ITransformer;
import org.serialthreads.transformer.LoadUntransformedException;
import org.serialthreads.transformer.NotTransformableException;
import org.serialthreads.transformer.TransformingVisitor;
import org.serialthreads.transformer.classcache.ClassInfoCacheASM;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.ow2.asm.ClassReader.SKIP_DEBUG;
import static org.ow2.asm.ClassReader.SKIP_FRAMES;
import static org.ow2.asm.ClassWriter.COMPUTE_FRAMES;
import static org.ow2.asm.ClassWriter.COMPUTE_MAXS;

/**
 * ClassLoader which applies a transformer to the loaded classes.
 */
public class TransformingClassLoader extends ClassLoader
{
  private final Logger logger = Logger.getLogger(getClass());

  private final String[] classPrefixes;
  private final ITransformer transformer;

  /**
   * Constructor using the system class loader as parent.
   *
   * @param strategy transformation strategy
   * @param classPrefixes prefixes of class to transform
   */
  public TransformingClassLoader(IStrategy strategy, String... classPrefixes)
  {
    this(Thread.currentThread().getContextClassLoader(), strategy, classPrefixes);
  }

  /**
   * Constructor using a given class loader as parent.
   *
   * @param parent parent class loader
   * @param strategy transformation strategy
   * @param classPrefixes prefixes of class to transform
   */
  public TransformingClassLoader(ClassLoader parent, IStrategy strategy, String... classPrefixes)
  {
    super(parent);

    this.classPrefixes = new String[1 + classPrefixes.length];
    this.classPrefixes[0] = "org.serialthreads.";
    System.arraycopy(classPrefixes, 0, this.classPrefixes, 1, classPrefixes.length);
    this.transformer = strategy.getTransformer(new ClassInfoCacheASM(this));
  }

  /**
   * {@inheritDoc}
   * <p/>
   * Tries to transform class first, if possible.
   */
  protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
  {
    Class<?> result = findLoadedClass(name);
    if (result == null)
    {
      byte[] byteCode = null;

      try
      {
        if (!shouldBeTransformed(name))
        {
          // use default behaviour for all classes not directly marked as interruptible
          return super.loadClass(name, resolve);
        }

        byteCode = loadByteCode(name);
        if (byteCode == null)
        {
          // use default behaviour for all classes that could not be loaded directly
          return super.loadClass(name, resolve);
        }

        byteCode = transform(byteCode);
//        FileCopyUtils.copy(byteCode, new File("./" + name + ".class"));
      }
      catch (LoadUntransformedException e)
      {
        // load class, because class should use this classloader to load referenced classes
      }
      catch (NotTransformableException e)
      {
        throw new ClassNotFoundException("Class " + name + " has transformation errors", e);
      }
      catch (IOException e)
      {
        throw new ClassNotFoundException("Class file for " + name + " could not be read", e);
      }

      result = defineClass(name, byteCode, 0, byteCode.length);
    }

    if (resolve)
    {
      resolveClass(result);
    }

    return result;
  }

  /**
   * Load byte code of the given class.
   *
   * @param name name of class
   * @return byte code or null, if class file does not exist
   * @exception IOException in case of IO exception while reading the class file
   */
  private byte[] loadByteCode(String name) throws IOException
  {
    // class content
    InputStream in = getResourceAsStream(name.replace('.', '/') + ".class");
    if (in == null)
    {
      return null;
    }

    // byte buffer
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    // try to load the byte code
    try
    {
      byte[] buffer = new byte[16384];
      for (int bytesRead; (bytesRead = in.read(buffer)) != -1; )
      {
        out.write(buffer, 0, bytesRead);
      }
      out.flush();
      return out.toByteArray();
    }
    finally
    {
      try
      {
        in.close();
      }
      catch (IOException e)
      {
        logger.warn("Unable to close InputStream", e);
      }
      try
      {
        out.close();
      }
      catch (IOException e)
      {
        logger.warn("Unable to close OutputStream", e);
      }
    }
  }

  /**
   * Transform class.
   *
   * @param byteCode byte code of class
   * @return modified byte code of class
   */
  protected byte[] transform(byte[] byteCode) throws NotTransformableException
  {
    ClassReader reader = new ClassReader(byteCode);
    ClassWriter writer = new ClassWriter(COMPUTE_MAXS + COMPUTE_FRAMES);
    ClassVisitor visitor = new TransformingVisitor(createVisitor(writer), transformer);

    reader.accept(visitor, 0);
    byte[] result = writer.toByteArray();

    if (logger.isDebugEnabled())
    {
      StringWriter output = new StringWriter(4096);
      new ClassReader(result).accept(new TraceClassVisitor(new PrintWriter(output)), SKIP_FRAMES + SKIP_DEBUG);
      logger.debug("Byte code:\n" + output);
    }

    return result;
  }

  /**
   * Should this class be transformed.
   *
   * @param className fully qualified name of class
   */
  protected boolean shouldBeTransformed(String className)
  {
    for (String classPrefix : classPrefixes)
    {
      if (className.startsWith(classPrefix))
      {
        return true;
      }
    }

    return false;
  }

  /**
   * Create byte code writer.
   *
   * @param writer byte code writer
   */
  protected ClassVisitor createVisitor(ClassWriter writer)
  {
    return writer;
  }
}
