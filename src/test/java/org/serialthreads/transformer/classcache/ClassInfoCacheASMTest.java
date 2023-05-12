package org.serialthreads.transformer.classcache;

import org.junit.jupiter.api.BeforeEach;

/**
 * Test for ClassInfoCacheASM.
 */
class ClassInfoCacheASMTest extends ClassInfoCacheAbstractTest {
  @BeforeEach
  void setUp() {
    cache = new ClassInfoCacheASM(ClassInfoCacheASMTest.class.getClassLoader());
  }
}
