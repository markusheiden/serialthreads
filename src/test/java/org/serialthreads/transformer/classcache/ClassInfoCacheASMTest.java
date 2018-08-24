package org.serialthreads.transformer.classcache;

import org.junit.jupiter.api.BeforeEach;

/**
 * Test for ClassInfoCacheASM.
 */
public class ClassInfoCacheASMTest extends ClassInfoCacheAbstractTest {
  @BeforeEach
  public void setUp() {
    cache = new ClassInfoCacheASM(ClassInfoCacheASMTest.class.getClassLoader());
  }
}
