package org.serialthreads.transformer.code;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link MethodCode}.
 */
class MethodCodeTest {
  /**
   * Test for {@link MethodCode}.
   */
  @Test
  void testEscapeForMethodName() throws Exception {
    assertThat(MethodCode.escapeForMethodName("org/serialthreads/Test/test([Lorg/serialthreads/Test;)V"))
      .isEqualTo("org_serialthreads_Test_test__Lorg_serialthreads_Test__V");
  }
}