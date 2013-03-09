package org.serialthreads.agent;

import org.apache.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.serialthreads.transformer.*;
import org.serialthreads.transformer.classcache.ClassInfoCacheReflection;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import static org.objectweb.asm.ClassReader.SKIP_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;

/**
 * Byte code enhancement agent.
 * Does the same as {@link TransformingClassLoader}.
 * <p/>
 * TODO mh: Do not throw exception in case of transforming problems, because otherwise the vm disables the transformer
 */
public class Agent implements ClassFileTransformer {
  private static final Logger _logger = Logger.getLogger(Agent.class);

  private final ClassInfoCacheReflection _classInfoCache;
  private final ITransformer _transformer;

  /**
   * Creates a new class file transformer.
   *
   * @param strategy strategy to use
   */
  public Agent(IStrategy strategy) {
    _classInfoCache = new ClassInfoCacheReflection();
    _transformer = strategy.getTransformer(_classInfoCache);
  }

  @Override
  public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
    if (loader == null) {
      // No need to transform classes loaded by the bootstrap classloader
      return null;
    }

    boolean failure = true;
    try {
      _logger.debug("Transforming class " + className + (classBeingRedefined != null ? " (redefining)" : " (initial)"));
      _classInfoCache.start(loader, className, classfileBuffer);

      ClassReader reader = new ClassReader(classfileBuffer);
      ClassWriter writer = new ClassWriter(COMPUTE_MAXS + COMPUTE_FRAMES);
      ClassVisitor visitor = new TransformingVisitor(writer, _transformer);
      reader.accept(visitor, SKIP_FRAMES);
      byte[] result = writer.toByteArray();
      failure = false;

      _logger.info("Successfully transformed class " + className + (classBeingRedefined != null ? " (redefining)" : " (initial)"));
      _classInfoCache.stop(className);

      return result;
    } catch (LoadUntransformedException e) {
      // No need to transform byte code
      failure = false;
      return null;
    } catch (NotTransformableException e) {
      _logger.error("Unable to transform " + className, e);
      failure = false;
      throw e;
    } catch (RuntimeException | Error e) {
      _logger.error("Failed to transform " + className, e);
      failure = false;
      throw e;
    } finally {
      if (failure) {
        _logger.fatal("Failed to transform " + className);
      }
    }
  }

  /**
   * Start of agent before main method have been called.
   * Called by vm to start this agent.
   *
   * @param arguments arguments
   * @param inst instrumentation
   */
  public static void premain(String arguments, Instrumentation inst) {
    agentmain(arguments, inst);
  }

  /**
   * Start of agent after main methods has been called.
   * Called by vm to start this agent.
   *
   * @param arguments arguments
   * @param inst instrumentation
   */
  public static void agentmain(String arguments, Instrumentation inst) {
    try {
      inst.addTransformer(new Agent(Strategies.DEFAULT));
    } catch (RuntimeException e) {
      _logger.fatal("Failed to init agent", e);
    }
  }
}
