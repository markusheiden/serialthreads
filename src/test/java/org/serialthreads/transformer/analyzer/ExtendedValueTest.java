package org.serialthreads.transformer.analyzer;

import org.junit.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.Value;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * Test for ExtendedValue.
 */
public class ExtendedValueTest
{
  @Test
  public void testValue()
  {
    ExtendedValue value = ExtendedValue.value(Type.INT_TYPE);
    assertEquals(Type.INT_TYPE, value.getType());
    assertFalse(value.isConstant());
    assertFalse(value.isHoldInLocal());
  }

  @Test
  public void testValueInLocal()
  {
    Set<Integer> locals1 = new HashSet<>(Arrays.asList(1));

    ExtendedValue value = ExtendedValue.valueInLocal(Type.INT_TYPE, 1);
    assertEquals(Type.INT_TYPE, value.getType());
    assertFalse(value.isConstant());
    assertTrue(value.isHoldInLocal());
    assertEquals(1, value.getLowestLocal());
    assertEquals(locals1, value.getLocals());
  }

  @Test
  public void testValueInLocals()
  {
    Set<Integer> locals12 = new HashSet<>(Arrays.asList(1, 2));

    ExtendedValue value = ExtendedValue.valueInLocal(Type.INT_TYPE, 1).addLocal(2);
    assertEquals(Type.INT_TYPE, value.getType());
    assertFalse(value.isConstant());
    assertTrue(value.isHoldInLocal());
    assertTrue(locals12.contains(value.getLowestLocal()));
    assertEquals(locals12, value.getLocals());
  }

  @Test
  public void testConstantValue()
  {
    ExtendedValue value = ExtendedValue.constantValue(Type.INT_TYPE, 1);
    assertEquals(Type.INT_TYPE, value.getType());
    assertTrue(value.isConstant());
    assertEquals(1, value.getConstant());
    assertFalse(value.isHoldInLocal());
  }

  @Test
  public void testConstantInLocals()
  {
    Set<Integer> locals12 = new HashSet<>(Arrays.asList(1, 2));

    ExtendedValue value = ExtendedValue.constantInLocals(Type.INT_TYPE, 1, locals12);
    assertEquals(Type.INT_TYPE, value.getType());
    assertTrue(value.isConstant());
    assertEquals(1, value.getConstant());
    assertTrue(value.isHoldInLocal());
    assertTrue(locals12.contains(value.getLowestLocal()));
    assertEquals(locals12, value.getLocals());
  }

  @Test
  public void testAddLocal()
  {
    ExtendedValue value = ExtendedValue.value(Type.INT_TYPE);
    ExtendedValue local1 = ExtendedValue.valueInLocal(Type.INT_TYPE, 1);
    assertEqualsValue(local1, value.addLocal(1));
  }

  @Test
  public void testRemoveLocal()
  {
    ExtendedValue value = ExtendedValue.value(Type.INT_TYPE);
    ExtendedValue local1 = ExtendedValue.valueInLocal(Type.INT_TYPE, 1);
    assertEqualsValue(value, local1.removeLocal(1));
  }

  @Test
  public void testEqualsValue()
  {
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
  public static void assertEqualsValue(ExtendedValue expected, Value value)
  {
    assertTrue("expected ExtendedValue but was: <" + value.getClass().getName() + ">", value instanceof ExtendedValue);
    ExtendedValue ev = (ExtendedValue) value;
    assertNotSame(expected, ev);
    if (expected.isConstant())
    {
      assertTrue("expected a constant value: <" + expected.getConstant() + "> but was: none", ev.isConstant());
      assertEquals("expected constant: <" + expected.getConstant() + "> but was: <" + ev.getConstant() + ">",
        expected.getConstant(), ev.getConstant());
    }
    else
    {
      assertFalse("expected no constant value", ev.isConstant());
    }
    if (expected.isHoldInLocal())
    {
      assertTrue("expected value which is hold in a local", ev.isHoldInLocal());
      assertEquals("expected locals: <" + expected.getLocals() + "> but was: <" + ev.getLocals() + ">",
        expected.getLocals(), ev.getLocals());
    }
    else
    {
      assertFalse("expected value which is not hold in any local", ev.isHoldInLocal());
    }
    assertTrue(expected.equalsValue((ExtendedValue) value));
  }
}
