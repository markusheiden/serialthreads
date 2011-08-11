package org.serialthreads.transformer.analyzer;

import org.junit.Test;
import org.ow2.asm.Type;
import org.serialthreads.transformer.classcache.ClassInfoCacheASM;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.ow2.asm.tree.analysis.BasicValue.UNINITIALIZED_VALUE;
import static org.serialthreads.transformer.analyzer.ExtendedValue.constantValue;
import static org.serialthreads.transformer.analyzer.ExtendedValue.value;
import static org.serialthreads.transformer.analyzer.ExtendedValue.valueInLocal;
import static org.serialthreads.transformer.analyzer.ExtendedValueTest.assertEqualsValue;

/**
 * Test for ExtendedVerifier.
 */
public class ExtendedVerifierTest
{
  @Test
  public void testMerge() throws Exception
  {
    ExtendedVerifier verifier = new ExtendedVerifier(
      new ClassInfoCacheASM(getClass().getClassLoader()), null, null, null, false);

    ExtendedValue local1Int = valueInLocal(Type.INT_TYPE, 1);
    ExtendedValue local12Int = valueInLocal(Type.INT_TYPE, 1).addLocal(2);
    ExtendedValue const1Int = constantValue(Type.INT_TYPE, 1);

    // test handling of uninitialized value
    assertEquals(UNINITIALIZED_VALUE, verifier.merge(UNINITIALIZED_VALUE, UNINITIALIZED_VALUE));
    assertEquals(UNINITIALIZED_VALUE, verifier.merge(UNINITIALIZED_VALUE, valueInLocal(Type.INT_TYPE, 1)));
    assertEquals(UNINITIALIZED_VALUE, verifier.merge(local1Int, UNINITIALIZED_VALUE));

    // test same values
    assertSame(local1Int, verifier.merge(local1Int, valueInLocal(Type.INT_TYPE, 1)));
    assertSame(const1Int, verifier.merge(const1Int, constantValue(Type.INT_TYPE, 1)));

    // test compatible merge locals only
    assertEqualsValue(local1Int, verifier.merge(local1Int, local12Int));
    assertEqualsValue(local1Int, verifier.merge(local12Int, local1Int));

    // test compatible merge with constant
    ExtendedValue const1local1Int = constantValue(Type.INT_TYPE, 1).addLocal(1);
    ExtendedValue const1local12Int = constantValue(Type.INT_TYPE, 1).addLocal(1).addLocal(2);
    assertEqualsValue(const1local1Int, verifier.merge(const1local1Int, const1local12Int));
    assertEqualsValue(const1local1Int, verifier.merge(const1local12Int, const1local1Int));

    // test nothing in common
    assertEqualsValue(value(Type.INT_TYPE), verifier.merge(local1Int, const1Int));

    // only simple test for merging of types, because SimpleVerifier should have been tested too
    ExtendedValue numberClass = value(Type.getType(Number.class));
    ExtendedValue integerClass = value(Type.getType(Integer.class)); // Integer extends Number
    assertEqualsValue(numberClass, verifier.merge(numberClass, integerClass));
  }
}
