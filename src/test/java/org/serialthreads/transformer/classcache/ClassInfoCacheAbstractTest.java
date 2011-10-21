package org.serialthreads.transformer.classcache;

import org.junit.Test;
import org.objectweb.asm.Type;

import java.util.Map;
import java.util.SortedMap;

import static org.junit.Assert.assertFalse;

/**
 * Test for ClassInfoCache.
 */
public abstract class ClassInfoCacheAbstractTest
{
  protected IClassInfoCache cache;

  @Test
  public void testIsInterruptible()
  {
    String mapName = Type.getType(Map.class).getInternalName();
    String sortedMapName = Type.getType(SortedMap.class).getInternalName();
    String objectDesc = Type.getType(Object.class).getDescriptor();
    assertFalse(cache.isInterruptible(sortedMapName, "put", "(" + objectDesc + objectDesc + ")" + objectDesc));
    assertFalse(cache.isInterruptible(mapName, "put", "(" + objectDesc + objectDesc + ")" + objectDesc));
    assertFalse(cache.isInterruptible(sortedMapName, "put", "(" + objectDesc + objectDesc + ")" + objectDesc));
  }
}
