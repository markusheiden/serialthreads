package org.serialthreads.transformer.classcache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.Map;
import java.util.SortedMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for ClassInfoCache.
 */
abstract class ClassInfoCacheAbstractTest {
  protected IClassInfoCache cache;

  @BeforeEach
  void setUp() {
    cache = createCache(getClass().getClassLoader());
  }

  /**
   * Create the cache to test.
   *
   * @param classLoader Class loader to load class files with.
   */
  protected abstract IClassInfoCache createCache(ClassLoader classLoader);

  @Test
  void testIsInterruptible() {
    var mapName = Type.getType(Map.class).getInternalName();
    var sortedMapName = Type.getType(SortedMap.class).getInternalName();
    var objectDesc = Type.getType(Object.class).getDescriptor();
    assertThat(cache.isInterruptible(sortedMapName, "put", "(" + objectDesc + objectDesc + ")" + objectDesc)).isFalse();
    assertThat(cache.isInterruptible(mapName, "put", "(" + objectDesc + objectDesc + ")" + objectDesc)).isFalse();
    assertThat(cache.isInterruptible(sortedMapName, "put", "(" + objectDesc + objectDesc + ")" + objectDesc)).isFalse();
  }
}
