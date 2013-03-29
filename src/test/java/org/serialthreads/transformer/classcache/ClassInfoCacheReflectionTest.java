package org.serialthreads.transformer.classcache;

import org.junit.Test;

/**
 * Test for ClassInfoCacheASM.
 */
public class ClassInfoCacheReflectionTest extends ClassInfoCacheAbstractTest {
  @Override
  @Test
  public void testIsInterruptible() {
    // TODO 2010-01-20 mh: add tests with other start method

    cache = new ClassInfoCacheReflection();
    ((ClassInfoCacheReflection) cache).setClassLoader(getClass().getClassLoader());
  }
}
