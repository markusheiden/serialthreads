package org.serialthreads.agent;

import org.serialthreads.transformer.*;
import org.serialthreads.transformer.classcache.ClassInfoCacheReflection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import static org.serialthreads.transformer.Strategies.DEFAULT;

/**
 * Byte code enhancement agent.
 * Does the same as {@link TransformingClassLoader}.
 * <p>
 * TODO mh: Do not throw exception in case of transforming problems, because otherwise the vm disables the transformer
 */
public class Agent implements ClassFileTransformer {
  /**
   * Logger.
   */
  private static final Logger logger = LoggerFactory.getLogger(Agent.class);

  /**
   * Information about all classes.
   */
  private final ClassInfoCacheReflection classInfoCache;

  /**
   * Class transformer.
   */
  private final ITransformer transformer;

  /**
   * Creates a new class file transformer.
   *
   * @param strategy strategy to use
   */
  public Agent(IStrategy strategy) {
    classInfoCache = new ClassInfoCacheReflection();
    transformer = strategy.getTransformer(classInfoCache);
  }

  @Override
  public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
    if (loader == null) {
      // No need to transform classes loaded by the bootstrap classloader
      return null;
    }

    boolean failure = true;
    try {
      logger.debug("Transforming class {} ({})", className, classBeingRedefined != null ? "redefining" : "initial");
      classInfoCache.start(loader, className, classfileBuffer);

      var result = transformer.transform(classfileBuffer);
      failure = false;

      logger.info("Successfully transformed class {} ({})", className, classBeingRedefined != null ? "redefining" : "initial");
      classInfoCache.stop(className);

      return result;
    } catch (LoadUntransformedException e) {
      // No need to transform byte code
      failure = false;
      return null;
    } catch (NotTransformableException e) {
      logger.error("Unable to transform {}", className, e);
      failure = false;
      throw e;
    } catch (RuntimeException | Error e) {
      logger.error("Failed to transform {}", className, e);
      failure = false;
      throw e;
    } finally {
      if (failure) {
        logger.error("Failed to transform {}", className);
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
   * Start of agent after main method has been called.
   * Called by vm to start this agent.
   *
   * @param arguments arguments
   * @param inst instrumentation
   */
  public static void agentmain(String arguments, Instrumentation inst) {
    try {
      inst.addTransformer(new Agent(DEFAULT));
    } catch (RuntimeException e) {
      logger.error("Failed to init agent", e);
    }
  }

  @Override
  public String toString() {
    return transformer.toString() + " via agent.";
  }
}
