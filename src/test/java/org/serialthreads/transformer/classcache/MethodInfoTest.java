package org.serialthreads.transformer.classcache;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.HashSet;

import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Type.INT_TYPE;

/**
 * Test for {@link MethodInfo}.
 */
class MethodInfoTest {
  @Test
  void testDefault() {
    var info = new MethodInfo("name", "desc", singleton(INT_TYPE));

    assertEquals("namedesc", info.getId());
    assertEquals("name", info.getName());
    assertEquals("desc", info.getDesc());
    assertEquals(1, info.getAnnotations().size());
    assertEquals(INT_TYPE, info.getAnnotations().iterator().next());
  }

  @Test
  void testToString() {
    var info = new MethodInfo("name", "desc", singleton(INT_TYPE));

    assertEquals("Method info namedesc", info.toString());
  }

  @Test
  void testHasAnnotation() {
    var info = new MethodInfo("name", "desc", singleton(INT_TYPE));

    assertTrue(info.hasAnnotation(INT_TYPE));
  }

  @Test
  void testGetAnnotations_immutable() {
    var info = new MethodInfo("name", "desc", new HashSet<>(singleton(INT_TYPE)));

    assertThrows(UnsupportedOperationException.class, () -> info.getAnnotations().add(Type.getType(getClass())));
  }

  @Test
  void testCopy() {
    var info = new MethodInfo("name", "desc", singleton(INT_TYPE));
    var copy = info.copy();

    assertEquals(info.getId(), copy.getId());
    assertEquals(info.getName(), copy.getName());
    assertEquals(info.getDesc(), copy.getDesc());
    assertEquals(info.getAnnotations(), copy.getAnnotations());
  }
}
