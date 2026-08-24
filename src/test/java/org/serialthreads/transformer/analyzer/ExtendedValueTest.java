package org.serialthreads.transformer.analyzer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.analysis.Value;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Type.INT_TYPE;

/**
 * Test for {@link ExtendedValue}.
 */
class ExtendedValueTest {
  @Test
  void testValue() {
    var value = ExtendedValue.value(INT_TYPE);
    assertThat(value.getType()).isEqualTo(INT_TYPE);
    assertThat(value.isConstant()).isFalse();
    assertThat(value.getLocals()).isEmpty();
  }

  @Test
  void testValueInLocal() {
    var value = ExtendedValue.valueInLocal(INT_TYPE, 1);
    assertThat(value.getType()).isEqualTo(INT_TYPE);
    assertThat(value.isConstant()).isFalse();
    assertThat(value.getLocals()).containsOnly(1);
  }

  @Test
  void testValueInLocals() {
    var value = ExtendedValue.valueInLocal(INT_TYPE, 1).addLocal(2);
    assertThat(value.getType()).isEqualTo(INT_TYPE);
    assertThat(value.isConstant()).isFalse();
    assertThat(value.getLocals()).containsOnly(1, 2);
  }

  @Test
  void testConstantValue() {
    var value = ExtendedValue.constantValue(INT_TYPE, 1);
    assertThat(value.getType()).isEqualTo(INT_TYPE);
    assertThat(value.isConstant()).isTrue();
    assertThat(value.getConstant()).isEqualTo(1);
    assertThat(value.getLocals()).isEmpty();
  }

  @Test
  void testConstantInLocals() {
    var value = ExtendedValue.constantInLocals(INT_TYPE, 1, Set.of(1, 2));
    assertThat(value.getType()).isEqualTo(INT_TYPE);
    assertThat(value.isConstant()).isTrue();
    assertThat(value.getConstant()).isEqualTo(1);
    assertThat(value.getLocals()).containsOnly(1, 2);
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
    assertThat(const1Local1A.equalsValue(const1Local12)).isFalse();
    assertThat(const1Local12.equalsValue(const1Local1A)).isFalse();

    var const2Local1 = ExtendedValue.constantValue(INT_TYPE, 2).addLocal(1);
    assertThat(const1Local1A.equalsValue(const2Local1)).isFalse();
    assertThat(const2Local1.equalsValue(const1Local1A)).isFalse();

    var local1 = ExtendedValue.value(INT_TYPE).addLocal(1);
    assertThat(const1Local1A.equalsValue(local1)).isFalse();
    assertThat(local1.equalsValue(const1Local1A)).isFalse();
  }

  /**
   * Assert that an extended value is not the same as the expected value,
   * but is "equalsValue".
   *
   * @param expected expected value
   * @param value value
   */
  static void assertEqualsValue(ExtendedValue expected, Value value) {
    assertThat(value).isInstanceOf(ExtendedValue.class);
    var ev = (ExtendedValue) value;
    assertThat(ev).isNotSameAs(expected);
    if (expected.isConstant()) {
      assertThat(ev.isConstant()).as("constant value expected").isTrue();
      assertThat(ev.getConstant()).as("constant").isEqualTo(expected.getConstant());
    } else {
      assertThat(ev.isConstant()).as("no constant value expected").isFalse();
    }
    assertThat(ev.getLocals()).as("locals").isEqualTo(expected.getLocals());
    assertThat(expected.equalsValue(ev)).isTrue();
  }
}
