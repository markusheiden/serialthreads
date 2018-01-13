package org.serialthreads.transformer.analyzer;

import org.junit.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.Value;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * Test for ExtendedValue.
 */
public class ExtendedValueTest {
  @Test
  public void testValue() {
    ExtendedValue value = ExtendedValue.value(Type.INT_TYPE);
    assertEquals(Type.INT_TYPE, value.getType());
    assertFalse(value.isConstant());
    assertThat(value.getLocals()).isEmpty();
  }

  @Test
  public void testValueInLocal() {
    ExtendedValue value = ExtendedValue.valueInLocal(Type.INT_TYPE, 1);
    assertEquals(Type.INT_TYPE, value.getType());
    assertFalse(value.isConstant());
    assertThat(value.getLocals()).containsExactly(1);
  }

  @Test
  public void testValueInLocals() {
    ExtendedValue value = ExtendedValue.valueInLocal(Type.INT_TYPE, 1).addLocal(2);
    assertEquals(Type.INT_TYPE, value.getType());
    assertFalse(value.isConstant());
    assertThat(value.getLocals()).containsExactly(1, 2);
  }

  @Test
  public void testConstantValue() {
    ExtendedValue value = ExtendedValue.constantValue(Type.INT_TYPE, 1);
    assertEquals(Type.INT_TYPE, value.getType());
    assertTrue(value.isConstant());
    assertEquals(1, value.getConstant());
    assertThat(value.getLocals()).isEmpty();
  }

  @Test
  public void testConstantInLocals() {
    ExtendedValue value = ExtendedValue.constantInLocals(Type.INT_TYPE, 1, Set.of(1, 2));
    assertEquals(Type.INT_TYPE, value.getType());
    assertTrue(value.isConstant());
    assertEquals(1, value.getConstant());
    assertThat(value.getLocals()).containsExactly(1, 2);
  }

  @Test
  public void testAddLocal() {
    ExtendedValue value = ExtendedValue.value(Type.INT_TYPE);
    ExtendedValue local1 = ExtendedValue.valueInLocal(Type.INT_TYPE, 1);
    assertEqualsValue(local1, value.addLocal(1));
  }

  @Test
  public void testRemoveLocal() {
    ExtendedValue value = ExtendedValue.value(Type.INT_TYPE);
    ExtendedValue local1 = ExtendedValue.valueInLocal(Type.INT_TYPE, 1);
    assertEqualsValue(value, local1.removeLocal(1));
  }

  @Test
  public void testEqualsValue() {
    ExtendedValue const1Local1A = ExtendedValue.constantValue(Type.INT_TYPE, 1).addLocal(1);
    ExtendedValue const1Local1B = ExtendedValue.constantValue(Type.INT_TYPE, 1).addLocal(1);
    assertEqualsValue(const1Local1A, const1Local1B);

    ExtendedValue const1Local12 = ExtendedValue.constantValue(Type.INT_TYPE, 1).addLocal(1).addLocal(2);
    assertFalse(const1Local1A.equalsValue(const1Local12));
    assertFalse(const1Local12.equalsValue(const1Local1A));

    ExtendedValue const2Local1 = ExtendedValue.constantValue(Type.INT_TYPE, 2).addLocal(1);
    assertFalse(const1Local1A.equalsValue(const2Local1));
    assertFalse(const2Local1.equalsValue(const1Local1A));

    ExtendedValue local1 = ExtendedValue.value(Type.INT_TYPE).addLocal(1);
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
  public static void assertEqualsValue(ExtendedValue expected, Value value) {
    assertTrue("expected ExtendedValue but was: <" + value.getClass().getName() + ">", value instanceof ExtendedValue);
    ExtendedValue ev = (ExtendedValue) value;
    assertNotSame(expected, ev);
    if (expected.isConstant()) {
      assertTrue("expected a constant value: <" + expected.getConstant() + "> but was: none", ev.isConstant());
      assertEquals("expected constant: <" + expected.getConstant() + "> but was: <" + ev.getConstant() + ">",
        expected.getConstant(), ev.getConstant());
    } else {
      assertFalse("expected no constant value", ev.isConstant());
    }
    assertEquals("expected locals: <" + expected.getLocals() + "> but was: <" + ev.getLocals() + ">",
      expected.getLocals(), ev.getLocals());
    assertTrue(expected.equalsValue((ExtendedValue) value));
  }
}
