package org.serialthreads.transformer.classcache;

import org.junit.Before;
import org.junit.Test;

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
