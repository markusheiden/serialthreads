package org.serialthreads.transformer.classcache;

/**
 * Test for ClassInfoCacheASM.
 */
class ClassInfoCacheASMTest extends ClassInfoCacheAbstractTest {
  @Override
  protected IClassInfoCache createCache(ClassLoader classLoader) {
    return new ClassInfoCacheASM(classLoader);
  }
}
