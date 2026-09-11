package org.serialthreads.agent;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.serialthreads.Interrupt;
import org.serialthreads.Interruptible;
import org.serialthreads.context.IRunnable;
import org.serialthreads.context.SimpleSerialThreadManager;
import org.serialthreads.transformer.NotTransformableException;
import org.serialthreads.transformer.classcache.ClassInfoCacheReflection;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.instrument.IllegalClassFormatException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.V17;
import static org.serialthreads.transformer.Strategies.DEFAULT;

/**
 * Integration test for {@link Agent}.
 * <p>
 * Simulates the JVM instrumentation via {@link AgentSimulatingClassLoader}:
 * Each loaded class is passed through {@link Agent#transform(ClassLoader, String, Class, java.security.ProtectionDomain, byte[])}
 * like an instrumented JVM would do.
 * This exercises all kinds of class loading implemented in {@link ClassInfoCacheReflection}:
 * <ul>
 *   <li>Direct ASM scan of the passed byte code ({@code addClassInfo}) for each class the agent transforms.</li>
 *   <li>Class file based ASM scan ({@code scanClassFile}) for referenced classes whose class file resource is available,
 *       e.g. {@code java/lang/Object}.</li>
 *   <li>Reflection based scan ({@code scanReflection}) for referenced classes whose class file resource is not available,
 *       enforced by hiding the class file resource of {@link HiddenBase}.</li>
 * </ul>
 */
class AgentTest {
  private static final String HIDDEN_BASE = HiddenBase.class.getName().replace('.', '/');
  private static final String TEST_RUNNABLE = TestRunnable.class.getName().replace('.', '/');
  private static final String TEST_MAIN = TestMain.class.getName().replace('.', '/');

  /**
   * Test that the agent transforms classes loaded via all kinds of class loading of {@link ClassInfoCacheReflection}
   * and that the transformed code executes correctly.
   */
  @Test
  void testTransformAndRun() throws Exception {
    var loader = new AgentSimulatingClassLoader(HIDDEN_BASE + ".class");

    // Run interruptible code which has been transformed by the agent.
    var mainClass = loader.loadClass(TestMain.class.getName());
    var result = mainClass.getMethod("run").invoke(null);

    // The runnable has been interrupted once per execute(1): value == 1 after the first, value == 2 after the second.
    assertThat(result).isEqualTo(12);

    // The interruptible runnable has been transformed by the agent (direct ASM scan of its byte code).
    assertThat(loader.transformed).containsEntry(TEST_RUNNABLE, true);
    // The not interruptible main class has been loaded unchanged (agent returned null due to LoadUntransformedException).
    assertThat(loader.transformed).containsEntry(TEST_MAIN, false);

    // Class file based ASM scan: java/lang/Object has no byte code passed to the agent,
    // so the cache scanned its class hierarchy via its class file resource.
    assertThat(loader.requestedResources).contains("java/lang/Object.class");

    // Reflection based scan: The class file resource of HiddenBase has been requested but was hidden,
    // so the cache had to fall back to reflection.
    // The successful transformation of TestRunnable (see above) proves that the reflection scan succeeded,
    // because it is the only remaining way to scan the super class HiddenBase.
    assertThat(loader.requestedResources).contains(HIDDEN_BASE + ".class");
    assertThat(loader.loadClass(HiddenBase.class.getName()).getClassLoader())
      .isNotSameAs(loader);
    assertThat(loader.loadClass(TestRunnable.class.getName()).getClassLoader())
      .isSameAs(loader);
  }

  /**
   * Test that classes loaded by the bootstrap class loader are not transformed.
   */
  @Test
  void testBootstrapClassesAreNotTransformed() throws IllegalClassFormatException {
    var agent = new Agent(DEFAULT);

    assertThat(agent.transform(null, "java/lang/Object", null, null, new byte[0])).isNull();
  }

  /**
   * Test that transformation fails, if a referenced class can neither be scanned via its class file resource
   * (because there is none) nor via reflection (because the class does not exist).
   */
  @Test
  void testClassNotFound() {
    var agent = new Agent(DEFAULT);

    // Generate a class extending a not existing class.
    var writer = new ClassWriter(0);
    writer.visit(V17, ACC_PUBLIC, "org/serialthreads/agent/Broken", null, "org/serialthreads/agent/DoesNotExist", null);
    writer.visitEnd();
    var byteCode = writer.toByteArray();

    assertThatThrownBy(() -> agent.transform(getClass().getClassLoader(), "org/serialthreads/agent/Broken", null, null, byteCode))
      .isInstanceOf(NotTransformableException.class);
  }

  /**
   * Class loader which simulates the JVM instrumentation:
   * All classes of this project are passed through the agent and defined with the (maybe transformed) byte code.
   * Class file resources may be hidden to force reflection based scans of the class info cache.
   */
  private static final class AgentSimulatingClassLoader extends ClassLoader {
    private final Agent agent = new Agent(DEFAULT);
    private final Set<String> hiddenResources;

    /**
     * All requested class file resources.
     */
    final Set<String> requestedResources = ConcurrentHashMap.newKeySet();

    /**
     * Internal class name -> has the agent transformed the class?.
     */
    final Map<String, Boolean> transformed = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param hiddenResources class file resources to hide, e.g. {@code "org/example/Test.class"}
     */
    AgentSimulatingClassLoader(String... hiddenResources) {
      super(AgentTest.class.getClassLoader());
      this.hiddenResources = Set.of(hiddenResources);
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      var result = findLoadedClass(name);
      if (result == null) {
        result = name.startsWith("org.serialthreads.") ? defineTransformed(name) : null;
        if (result == null) {
          // Use the default behaviour for all other classes and for classes without byte code.
          return super.loadClass(name, resolve);
        }
      }

      if (resolve) {
        resolveClass(result);
      }

      return result;
    }

    /**
     * Load the byte code of the class, pass it through the agent and define the resulting class,
     * like an instrumented JVM would do.
     *
     * @param name binary name of class
     * @return defined class or {@code null}, if the byte code of the class is not available
     */
    private Class<?> defineTransformed(String name) {
      var internalName = name.replace('.', '/');
      var byteCode = loadByteCode(internalName);
      if (byteCode == null) {
        return null;
      }

      try {
        var transformedByteCode = agent.transform(this, internalName, null, null, byteCode);
        transformed.put(internalName, transformedByteCode != null);
        // The agent returns null, if the class needs no transformation -> define the original byte code.
        var definedByteCode = transformedByteCode != null ? transformedByteCode : byteCode;
        return defineClass(name, definedByteCode, 0, definedByteCode.length);
      } catch (IllegalClassFormatException e) {
        throw new IllegalStateException("Invalid class " + name, e);
      }
    }

    /**
     * Load byte code of the given class.
     *
     * @param internalName internal name of class
     * @return byte code or {@code null}, if the class file resource is not available
     */
    private byte[] loadByteCode(String internalName) {
      try (var classFile = getResourceAsStream(internalName + ".class")) {
        return classFile != null ? classFile.readAllBytes() : null;
      } catch (IOException e) {
        throw new UncheckedIOException("Class file for " + internalName + " could not be read", e);
      }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
      requestedResources.add(name);
      if (hiddenResources.contains(name)) {
        return null;
      }
      return super.getResourceAsStream(name);
    }
  }

  /**
   * Super class of {@link TestRunnable} whose class file resource is hidden,
   * so that the class info cache has to scan it via reflection.
   */
  public static class HiddenBase {
    public int value = 0;
  }

  /**
   * Test runnable which gets interrupted once.
   */
  public static class TestRunnable extends HiddenBase implements IRunnable {
    @Interruptible
    @Override
    public void run() {
      value = 1;

      interrupt();

      value = 2;
    }

    @Interrupt
    void interrupt() {
      // Method call will be redirected to interrupt code.
    }
  }

  /**
   * Entry point executing transformed code.
   * Loaded by the {@link AgentSimulatingClassLoader}, so that all referenced classes are transformed by the agent.
   */
  public static class TestMain {
    /**
     * Run {@link TestRunnable} with one interrupt per execute.
     *
     * @return value of the runnable after the first execute * 10 + value after the second execute
     */
    public static int run() {
      var runnable = new TestRunnable();
      try (var manager = new SimpleSerialThreadManager(runnable)) {
        manager.execute(1);
        var afterFirst = runnable.value;
        manager.execute(1);
        return afterFirst * 10 + runnable.value;
      }
    }
  }
}
