package org.serialthreads.transformer.classcache;

import org.junit.Before;

/**
 * Test for ClassInfoCacheASM.
 */
public class ClassInfoCacheASMTest extends ClassInfoCacheAbstractTest
{
  @Before
  public void setUp()
  {
    cache = new ClassInfoCacheASM(ClassInfoCacheASMTest.class.getClassLoader());
  }
}
