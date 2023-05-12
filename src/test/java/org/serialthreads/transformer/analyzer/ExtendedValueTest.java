package org.serialthreads.transformer.analyzer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.Value;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Type.INT_TYPE;

/**
 * Test for {@link ExtendedValue}.
 */
class ExtendedValueTest {
  @Test
  void testValue() {
    var value = ExtendedValue.value(INT_TYPE);
    assertEquals(INT_TYPE, value.getType());
    assertFalse(value.isConstant());
    assertThat(value.getLocals()).isEmpty();
  }

  @Test
  void testValueInLocal() {
    var value = ExtendedValue.valueInLocal(INT_TYPE, 1);
    assertEquals(INT_TYPE, value.getType());
    assertFalse(value.isConstant());
    assertThat(value.getLocals()).containsExactly(1);
  }

  @Test
  void testValueInLocals() {
    var value = ExtendedValue.valueInLocal(INT_TYPE, 1).addLocal(2);
    assertEquals(INT_TYPE, value.getType());
    assertFalse(value.isConstant());
    assertThat(value.getLocals()).containsExactly(1, 2);
  }

  @Test
  void testConstantValue() {
    var value = ExtendedValue.constantValue(INT_TYPE, 1);
    assertEquals(INT_TYPE, value.getType());
    assertTrue(value.isConstant());
    assertEquals(1, value.getConstant());
    assertThat(value.getLocals()).isEmpty();
  }

  @Test
  void testConstantInLocals() {
    var value = ExtendedValue.constantInLocals(INT_TYPE, 1, Set.of(1, 2));
    assertEquals(INT_TYPE, value.getType());
    assertTrue(value.isConstant());
    assertEquals(1, value.getConstant());
    assertThat(value.getLocals()).containsExactly(1, 2);
  }

  @Test
  void testAddLocal() {
    var value = ExtendedValue.value(INT_TYPE);
    var local1 = ExtendedValue.valueInLocal(INT_TYPE, 1);
    assertEqualsValue(local1, value.addLocal(1));
  }

  @Test
  void testRemoveLocal() {
    var value = ExtendedValue.value(INT_TYPE);
    var local1 = ExtendedValue.valueInLocal(INT_TYPE, 1);
    assertEqualsValue(value, local1.removeLocal(1));
  }

  @Test
  void testEqualsValue() {
    var const1Local1A = ExtendedValue.constantValue(INT_TYPE, 1).addLocal(1);
    var const1Local1B = ExtendedValue.constantValue(INT_TYPE, 1).addLocal(1);
    assertEqualsValue(const1Local1A, const1Local1B);

    var const1Local12 = ExtendedValue.constantValue(INT_TYPE, 1).addLocal(1).addLocal(2);
    assertFalse(const1Local1A.equalsValue(const1Local12));
    assertFalse(const1Local12.equalsValue(const1Local1A));

    var const2Local1 = ExtendedValue.constantValue(INT_TYPE, 2).addLocal(1);
    assertFalse(const1Local1A.equalsValue(const2Local1));
    assertFalse(const2Local1.equalsValue(const1Local1A));

    var local1 = ExtendedValue.value(INT_TYPE).addLocal(1);
    assertFalse(const1Local1A.equalsValue(local1));
    assertFalse(local1.equalsValue(const1Local1A));
  }

  /**
   * Assert that an extended value is not the same as the expected value,
   * but is "equalsValue".
   *
   * @param expected expected value
   * @param value value
   */
  static void assertEqualsValue(ExtendedValue expected, Value value) {
    assertTrue(value instanceof ExtendedValue, "expected ExtendedValue but was: <" + value.getClass().getName() + ">");
    var ev = (ExtendedValue) value;
    assertNotSame(expected, ev);
    if (expected.isConstant()) {
      assertTrue(ev.isConstant(), "expected a constant value: <" + expected.getConstant() + "> but was: none");
      assertEquals(expected.getConstant(), ev.getConstant(),
              "expected constant: <" + expected.getConstant() + "> but was: <" + ev.getConstant() + ">");
    } else {
      assertFalse(ev.isConstant(), "expected no constant value");
    }
    assertEquals(expected.getLocals(), ev.getLocals(),
            "expected locals: <" + expected.getLocals() + "> but was: <" + ev.getLocals() + ">");
    assertTrue(expected.equalsValue((ExtendedValue) value));
  }
}
