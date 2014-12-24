package org.serialthreads.transformer.code;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test for {@link MethodCode}.
 */
public class MethodCodeTest {
  /**
   * Test for {@link MethodCode}.
   */
  @Test
  public void testEscapeForMethodName() throws Exception {
    assertEquals("Lorg_serialthreads_Test_", MethodCode.escapeForMethodName("Lorg/serialthreads/Test;"));
  }
}