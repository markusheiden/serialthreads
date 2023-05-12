package org.serialthreads.transformer.classcache;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.Map;
import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Test for ClassInfoCache.
 */
abstract class ClassInfoCacheAbstractTest {
  protected IClassInfoCache cache;

  @Test
  void testIsInterruptible() {
    var mapName = Type.getType(Map.class).getInternalName();
    var sortedMapName = Type.getType(SortedMap.class).getInternalName();
    var objectDesc = Type.getType(Object.class).getDescriptor();
    assertFalse(cache.isInterruptible(sortedMapName, "put", "(" + objectDesc + objectDesc + ")" + objectDesc));
    assertFalse(cache.isInterruptible(mapName, "put", "(" + objectDesc + objectDesc + ")" + objectDesc));
    assertFalse(cache.isInterruptible(sortedMapName, "put", "(" + objectDesc + objectDesc + ")" + objectDesc));
  }
}
