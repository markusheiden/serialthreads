package org.serialthreads.transformer.classcache;

import org.junit.jupiter.api.Test;

/**
 * Test for ClassInfoCacheASM.
 */
class ClassInfoCacheReflectionTest extends ClassInfoCacheAbstractTest {
  @Override
  @Test
  void testIsInterruptible() {
    // TODO 2010-01-20 mh: add tests with other start method

    cache = new ClassInfoCacheReflection();
    ((ClassInfoCacheReflection) cache).setClassLoader(getClass().getClassLoader());
  }
}
