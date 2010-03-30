package org.serialthreads.transformer.classcache;

import org.junit.Test;

/**
 * Test for ClassInfoCacheASM.
 */
public class ClassInfoCacheASMTest extends ClassInfoCacheAbstractTest
{
  @Test
  public void testIsInterruptible()
  {
    ClassInfoCacheASM cache = new ClassInfoCacheASM(ClassInfoCacheASMTest.class.getClassLoader());
    testIsInterruptible(cache);
  }
}
