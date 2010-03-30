package org.serialthreads.transformer.analyzer;

import org.junit.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;

import static org.junit.Assert.fail;
import static org.serialthreads.transformer.analyzer.ExtendedValueTest.assertEqualsValue;

/**
 * Test for FixedVerifier.
 */
public class FixedVerifierTest
{
  /**
   * Test bugfix.
   */
  @Test
  public void testCopyOperation() throws Exception
  {
    FixedVerifier verifier = new FixedVerifier();
    assertEqualsValue(ExtendedValue.value(Type.INT_TYPE), verifier.copyOperation(new VarInsnNode(Opcodes.ILOAD, 1), ExtendedValue.value(Type.INT_TYPE)));
    assertEqualsValue(ExtendedValue.value(Type.INT_TYPE), verifier.copyOperation(new VarInsnNode(Opcodes.ISTORE, 1), ExtendedValue.value(Type.INT_TYPE)));
    assertEqualsValue(ExtendedValue.value(Type.LONG_TYPE), verifier.copyOperation(new VarInsnNode(Opcodes.LLOAD, 1), ExtendedValue.value(Type.LONG_TYPE)));
    assertEqualsValue(ExtendedValue.value(Type.LONG_TYPE), verifier.copyOperation(new VarInsnNode(Opcodes.LSTORE, 1), ExtendedValue.value(Type.LONG_TYPE)));
    assertEqualsValue(ExtendedValue.value(Type.FLOAT_TYPE), verifier.copyOperation(new VarInsnNode(Opcodes.FLOAD, 1), ExtendedValue.value(Type.FLOAT_TYPE)));
    assertEqualsValue(ExtendedValue.value(Type.FLOAT_TYPE), verifier.copyOperation(new VarInsnNode(Opcodes.FSTORE, 1), ExtendedValue.value(Type.FLOAT_TYPE)));
    assertEqualsValue(ExtendedValue.value(Type.DOUBLE_TYPE), verifier.copyOperation(new VarInsnNode(Opcodes.DLOAD, 1), ExtendedValue.value(Type.DOUBLE_TYPE)));
    assertEqualsValue(ExtendedValue.value(Type.DOUBLE_TYPE), verifier.copyOperation(new VarInsnNode(Opcodes.DSTORE, 1), ExtendedValue.value(Type.DOUBLE_TYPE)));

    // test, that remaining cases are handled in SimpleVerifier
    assertEqualsValue(ExtendedValue.value(Type.getType(Number.class)), verifier.copyOperation(new VarInsnNode(Opcodes.ASTORE, 1), ExtendedValue.value(Type.getType(Number.class))));

    try
    {
      verifier.copyOperation(new VarInsnNode(Opcodes.ILOAD, 1), ExtendedValue.value(Type.LONG_TYPE));
      fail("AnalyzerException expected");
    }
    catch (AnalyzerException e)
    {
      // expected
    }
  }
}
