package org.serialthreads.transformer.classcache;

import org.junit.Test;
import org.objectweb.asm.Type;

import java.util.HashSet;

import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * Test for {@link MethodInfo}.
 */
public class MethodInfoTest {
  @Test
  public void testDefault() {
    MethodInfo info = new MethodInfo("name", "desc", singleton(Type.INT_TYPE));

    assertEquals("namedesc", info.getId());
    assertEquals("name", info.getName());
    assertEquals("desc", info.getDesc());
    assertEquals(1, info.getAnnotations().size());
    assertEquals(Type.INT_TYPE, info.getAnnotations().iterator().next());
  }

  @Test
  public void testToString() {
    MethodInfo info = new MethodInfo("name", "desc", singleton(Type.INT_TYPE));

    assertEquals("Method info namedesc", info.toString());
  }

  @Test
  public void testHasAnnotation() {
    MethodInfo info = new MethodInfo("name", "desc", singleton(Type.INT_TYPE));

    assertTrue(info.hasAnnotation(Type.INT_TYPE));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testGetAnnotations_immutable() {
    MethodInfo info = new MethodInfo("name", "desc", new HashSet<>(singleton(Type.INT_TYPE)));

    info.getAnnotations().add(Type.getType(getClass()));
  }

  @Test
  public void testCopy() {
    MethodInfo info = new MethodInfo("name", "desc", singleton(Type.INT_TYPE));
    MethodInfo copy = info.copy();

    assertEquals(info.getId(), copy.getId());
    assertEquals(info.getName(), copy.getName());
    assertEquals(info.getDesc(), copy.getDesc());
    assertEquals(info.getAnnotations(), copy.getAnnotations());
    assertNotSame(info.getAnnotations(), copy.getAnnotations());
  }
}
