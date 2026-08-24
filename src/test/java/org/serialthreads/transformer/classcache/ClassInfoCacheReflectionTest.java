package org.serialthreads.transformer.classcache;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.serialthreads.Interruptible;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for ClassInfoCacheReflection.
 */
class ClassInfoCacheReflectionTest extends ClassInfoCacheAbstractTest {
  @Override
  protected IClassInfoCache createCache(ClassLoader classLoader) {
    var reflectionCache = new ClassInfoCacheReflection();
    reflectionCache.setClassLoader(classLoader);
    return reflectionCache;
  }

  /**
   * Test that the reflection based scan detects annotations.
   * Uses a class loader which provides no class file resources, so that the scan has to fall back to reflection.
   */
  @Test
  void testIsInterruptible_reflection() {
    ((ClassInfoCacheReflection) cache).setClassLoader(new ResourceHidingClassLoader());

    var className = Type.getType(TestClass.class).getInternalName();
    assertTrue(cache.isInterruptible(className, "interruptible", "()V"));
    assertFalse(cache.isInterruptible(className, "notInterruptible", "()V"));
  }

  /**
   * Test class with an interruptible and a not interruptible method.
   */
  public static class TestClass {
    @Interruptible
    public void interruptible() {
      // just for the annotation
    }

    public void notInterruptible() {
      // just for the missing annotation
    }
  }

  /**
   * Class loader which hides all class file resources to force reflection based scans.
   */
  private static class ResourceHidingClassLoader extends ClassLoader {
    ResourceHidingClassLoader() {
      super(ClassInfoCacheReflectionTest.class.getClassLoader());
    }

    @Override
    public InputStream getResourceAsStream(String name) {
      return null;
    }
  }
}
