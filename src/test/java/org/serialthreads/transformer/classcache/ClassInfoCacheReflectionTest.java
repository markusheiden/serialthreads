package org.serialthreads.transformer.classcache;

import org.junit.Test;

/**
 * Test for ClassInfoCacheASM.
 */
public class ClassInfoCacheReflectionTest extends ClassInfoCacheAbstractTest
{
  @Test
  public void testIsInterruptible()
  {
    // TODO 2010-01-20 mh: add tests with other start method

    ClassInfoCacheReflection cache = new ClassInfoCacheReflection();
    cache.setClassLoader(ClassInfoCacheReflectionTest.class.getClassLoader());
    testIsInterruptible(cache);
  }
}
