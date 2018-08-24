package org.serialthreads.transformer.code;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test for {@link MethodCode}.
 */
public class MethodCodeTest {
  /**
   * Test for {@link MethodCode}.
   */
  @Test
  public void testEscapeForMethodName() throws Exception {
    assertEquals("org_serialthreads_Test_test__Lorg_serialthreads_Test__V",
      MethodCode.escapeForMethodName("org/serialthreads/Test/test([Lorg/serialthreads/Test;)V"));
  }
}