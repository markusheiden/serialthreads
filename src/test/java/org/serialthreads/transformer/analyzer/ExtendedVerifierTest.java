package org.serialthreads.transformer.analyzer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.serialthreads.transformer.classcache.ClassInfoCacheASM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.tree.analysis.BasicValue.UNINITIALIZED_VALUE;
import static org.serialthreads.transformer.analyzer.ExtendedValue.constantValue;
import static org.serialthreads.transformer.analyzer.ExtendedValue.value;
import static org.serialthreads.transformer.analyzer.ExtendedValue.valueInLocal;
import static org.serialthreads.transformer.analyzer.ExtendedValueTest.assertEqualsValue;

/**
 * Test for ExtendedVerifier.
 */
class ExtendedVerifierTest {
  @Test
  void testMerge() {
    var verifier = new ExtendedVerifier(
      new ClassInfoCacheASM(getClass().getClassLoader()), null, null, null, false);

    var local1Int = valueInLocal(INT_TYPE, 1);
    var local12Int = valueInLocal(INT_TYPE, 1).addLocal(2);
    var const1Int = constantValue(INT_TYPE, 1);

    // test handling of uninitialized value
    assertThat(verifier.merge(UNINITIALIZED_VALUE, UNINITIALIZED_VALUE)).isEqualTo(UNINITIALIZED_VALUE);
    assertThat(verifier.merge(UNINITIALIZED_VALUE, valueInLocal(INT_TYPE, 1))).isEqualTo(UNINITIALIZED_VALUE);
    assertThat(verifier.merge(local1Int, UNINITIALIZED_VALUE)).isEqualTo(UNINITIALIZED_VALUE);

    // test same values
    assertThat(verifier.merge(local1Int, valueInLocal(INT_TYPE, 1))).isSameAs(local1Int);
    assertThat(verifier.merge(const1Int, constantValue(INT_TYPE, 1))).isSameAs(const1Int);

    // test compatible merge locals only
    assertEqualsValue(local1Int, verifier.merge(local1Int, local12Int));
    assertEqualsValue(local1Int, verifier.merge(local12Int, local1Int));

    // test compatible merge with constant
    var const1local1Int = constantValue(INT_TYPE, 1).addLocal(1);
    var const1local12Int = constantValue(INT_TYPE, 1).addLocal(1).addLocal(2);
    assertEqualsValue(const1local1Int, verifier.merge(const1local1Int, const1local12Int));
    assertEqualsValue(const1local1Int, verifier.merge(const1local12Int, const1local1Int));

    // test nothing in common
    assertEqualsValue(value(INT_TYPE), verifier.merge(local1Int, const1Int));

    // only simple test for merging of types, because SimpleVerifier should have been tested too
    var numberClass = value(Type.getType(Number.class));
    var integerClass = value(Type.getType(Integer.class)); // Integer extends Number
    assertEqualsValue(numberClass, verifier.merge(numberClass, integerClass));
  }
}
