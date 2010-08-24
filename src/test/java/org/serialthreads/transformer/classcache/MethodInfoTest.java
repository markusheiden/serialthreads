package org.serialthreads.transformer.classcache;

import org.junit.Test;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertNotSame;

/**
 * Test for {@link MethodInfo}.
 */
public class MethodInfoTest
{
  @Test
  public void testDefault()
  {
    MethodInfo info = new MethodInfo("name", "desc", Collections.singleton(Type.INT_TYPE.getDescriptor()));

    assertEquals("namedesc", info.getID());
    assertEquals("name", info.getName());
    assertEquals("desc", info.getDesc());
    assertEquals(1, info.getAnnotations().size());
    assertEquals(Type.INT_TYPE.getDescriptor(), info.getAnnotations().iterator().next());
  }

  @Test
  public void testToString()
  {
    MethodInfo info = new MethodInfo("name", "desc", Collections.singleton(Type.INT_TYPE.getDescriptor()));

    assertEquals("Method info namedesc", info.toString());
  }

  @Test
  public void testHasAnnotation()
  {
    MethodInfo info = new MethodInfo("name", "desc", Collections.singleton(Type.INT_TYPE.getDescriptor()));

    assertTrue(info.hasAnnotation(Type.INT_TYPE));
  }

  @Test(expected = UnsupportedOperationException.class)
  public void testGetAnnotations_immutable()
  {
    MethodInfo info = new MethodInfo("name", "desc", new HashSet<String>(Collections.singleton(Type.INT_TYPE.getDescriptor())));

    info.getAnnotations().add("something");
  }

  @Test
  public void testCopy()
  {
    MethodInfo info = new MethodInfo("name", "desc", Collections.singleton(Type.INT_TYPE.getDescriptor()));
    MethodInfo copy = info.copy();

    assertEquals(info.getID(), copy.getID());
    assertEquals(info.getName(), copy.getName());
    assertEquals(info.getDesc(), copy.getDesc());
    assertEquals(info.getAnnotations(), copy.getAnnotations());
    assertNotSame(info.getAnnotations(), copy.getAnnotations());
  }
}
