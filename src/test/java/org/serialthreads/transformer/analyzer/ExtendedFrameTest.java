package org.serialthreads.transformer.analyzer;

import org.junit.Test;
import org.ow2.asm.Opcodes;
import org.ow2.asm.Type;
import org.ow2.asm.tree.AbstractInsnNode;
import org.ow2.asm.tree.IincInsnNode;
import org.ow2.asm.tree.InsnNode;
import org.ow2.asm.tree.IntInsnNode;
import org.ow2.asm.tree.LdcInsnNode;
import org.ow2.asm.tree.VarInsnNode;
import org.ow2.asm.tree.analysis.BasicValue;
import org.ow2.asm.tree.analysis.Interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.serialthreads.transformer.analyzer.ExtendedValueTest.assertEqualsValue;

/**
 * Test for ExtendedFrame.
 */
public class ExtendedFrameTest
{
  @Test
  public void testConstructor_ii()
  {
    ExtendedFrame frame = new ExtendedFrame(2, 2);

    frame.setLocal(0, BasicValue.UNINITIALIZED_VALUE);
    frame.setLocal(1, BasicValue.INT_VALUE);
    assertEquals(BasicValue.UNINITIALIZED_VALUE, frame.getLocal(0));
    assertEquals(ExtendedValue.valueInLocal(Type.INT_TYPE, 1), frame.getLocal(1));
    assertEquals(2, frame.getLocals());

    frame.push(BasicValue.LONG_VALUE);
    frame.push(BasicValue.DOUBLE_VALUE);
    assertEquals(ExtendedValue.value(Type.LONG_TYPE), frame.getStack(0));
    assertEquals(ExtendedValue.value(Type.DOUBLE_TYPE), frame.getStack(1));
    assertEquals(2, frame.getStackSize());

    try
    {
      frame.push(BasicValue.INT_VALUE);
      fail("Expected max stack = 2");
    }
    catch (IndexOutOfBoundsException e)
    {
      // expected
    }
  }

  @Test
  public void testConstructor_frame()
  {
    ExtendedFrame src = new ExtendedFrame(2, 2);
    src.setLocal(0, BasicValue.UNINITIALIZED_VALUE);
    src.setLocal(1, BasicValue.INT_VALUE);
    src.push(BasicValue.LONG_VALUE);
    src.push(BasicValue.DOUBLE_VALUE);

    ExtendedFrame frame = new ExtendedFrame(src);

    assertEquals(BasicValue.UNINITIALIZED_VALUE, frame.getLocal(0));
    assertEquals(ExtendedValue.valueInLocal(Type.INT_TYPE, 1), frame.getLocal(1));
    assertEquals(2, frame.getLocals());

    assertEquals(ExtendedValue.value(Type.LONG_TYPE), frame.getStack(0));
    assertEquals(ExtendedValue.value(Type.DOUBLE_TYPE), frame.getStack(1));
    assertEquals(2, frame.getStackSize());

    try
    {
      frame.push(BasicValue.INT_VALUE);
      fail("Expected max stack = 2");
    }
    catch (IndexOutOfBoundsException e)
    {
      // expected
    }
  }

  @Test
  public void testExecute_const() throws Exception
  {
    testExecute_const(new InsnNode(Opcodes.ACONST_NULL), Type.getObjectType("null"), null);
    testExecute_const(new InsnNode(Opcodes.ICONST_M1), Type.INT_TYPE, -1);
    testExecute_const(new InsnNode(Opcodes.ICONST_0), Type.INT_TYPE, 0);
    testExecute_const(new InsnNode(Opcodes.ICONST_1), Type.INT_TYPE, 1);
    testExecute_const(new InsnNode(Opcodes.ICONST_2), Type.INT_TYPE, 2);
    testExecute_const(new InsnNode(Opcodes.ICONST_3), Type.INT_TYPE, 3);
    testExecute_const(new InsnNode(Opcodes.ICONST_4), Type.INT_TYPE, 4);
    testExecute_const(new InsnNode(Opcodes.ICONST_5), Type.INT_TYPE, 5);
    testExecute_const(new InsnNode(Opcodes.LCONST_0), Type.LONG_TYPE, 0L);
    testExecute_const(new InsnNode(Opcodes.LCONST_1), Type.LONG_TYPE, 1L);
    testExecute_const(new InsnNode(Opcodes.FCONST_0), Type.FLOAT_TYPE, 0F);
    testExecute_const(new InsnNode(Opcodes.FCONST_1), Type.FLOAT_TYPE, 1F);
    testExecute_const(new InsnNode(Opcodes.DCONST_0), Type.DOUBLE_TYPE, 0D);
    testExecute_const(new InsnNode(Opcodes.DCONST_1), Type.DOUBLE_TYPE, 1D);

    testExecute_const(new IntInsnNode(Opcodes.BIPUSH, 64), Type.INT_TYPE, 64);
    testExecute_const(new IntInsnNode(Opcodes.SIPUSH, 4096), Type.INT_TYPE, 4096);

    testExecute_const(new LdcInsnNode(1000000), Type.INT_TYPE, 1000000);
    testExecute_const(new LdcInsnNode("TEST"), Type.getType(String.class), "TEST");
  }

  private void testExecute_const(AbstractInsnNode instruction, Type type, Object constant) throws Exception
  {
    Interpreter interpreter = new ExtendedVerifier(null, null, null, null, false);

    ExtendedFrame frame = new ExtendedFrame(0, 1);
    frame.execute(instruction, interpreter);
    assertEquals(1, frame.getStackSize());
    assertEqualsValue(ExtendedValue.constantValue(type, constant), frame.getStack(0));
  }

  @Test
  public void testExecute_store() throws Exception
  {
    testExecute_store(new VarInsnNode(Opcodes.ISTORE, 0), Type.INT_TYPE);
    testExecute_store(new VarInsnNode(Opcodes.LSTORE, 0), Type.LONG_TYPE);
    testExecute_store(new VarInsnNode(Opcodes.FSTORE, 0), Type.FLOAT_TYPE);
    testExecute_store(new VarInsnNode(Opcodes.DSTORE, 0), Type.DOUBLE_TYPE);
    testExecute_store(new VarInsnNode(Opcodes.ASTORE, 0), Type.getType(Object.class));

    testExecute_store(new IincInsnNode(0, 1), Type.INT_TYPE);
  }

  private void testExecute_store(AbstractInsnNode instruction, Type type) throws Exception
  {
    Interpreter interpreter = new ExtendedVerifier(null, null, null, null, false);

    ExtendedFrame frame = new ExtendedFrame(4, 4);
    // value which will be changed
    frame.setLocal(0, ExtendedValue.value(type));
    // local which locals will be updated
    frame.setLocal(type.getSize(), ExtendedValue.value(type).addLocal(0).addLocal(type.getSize()));
    // stack which locals will be updated
    frame.push(ExtendedValue.value(type).addLocal(0).addLocal(type.getSize()));
    // stack which may be changed
    frame.push(ExtendedValue.value(type));
    frame.execute(instruction, interpreter);

    assertEqualsValue(ExtendedValue.value(type).addLocal(type.getSize()), frame.getLocal(type.getSize()));
    assertEqualsValue(ExtendedValue.value(type).addLocal(type.getSize()), frame.getStack(0));
  }

  @Test
  public void testSetLocal()
  {
    ExtendedFrame frame = new ExtendedFrame(1, 0);

    frame.setLocal(0, BasicValue.UNINITIALIZED_VALUE);
    assertEquals(BasicValue.UNINITIALIZED_VALUE, frame.getLocal(0));
    frame.setLocal(0, BasicValue.INT_VALUE);
    assertEqualsValue(ExtendedValue.valueInLocal(Type.INT_TYPE, 0), frame.getLocal(0));
    frame.setLocal(0, ExtendedValue.value(Type.INT_TYPE));
    assertEqualsValue(ExtendedValue.valueInLocal(Type.INT_TYPE, 0), frame.getLocal(0));
    frame.setLocal(0, ExtendedValue.valueInLocal(Type.INT_TYPE, 0));
    assertEqualsValue(ExtendedValue.valueInLocal(Type.INT_TYPE, 0), frame.getLocal(0));
  }

  @Test
  public void testPush()
  {
    ExtendedFrame frame = new ExtendedFrame(0, 1);

    frame.push(BasicValue.INT_VALUE);
    assertEqualsValue(ExtendedValue.value(Type.INT_TYPE), frame.getStack(0));
    frame.clearStack();
    frame.push(ExtendedValue.value(Type.INT_TYPE));
    assertEqualsValue(ExtendedValue.value(Type.INT_TYPE), frame.getStack(0));
    frame.clearStack();
    frame.push(ExtendedValue.valueInLocal(Type.INT_TYPE, 0));
    assertEqualsValue(ExtendedValue.valueInLocal(Type.INT_TYPE, 0), frame.getStack(0));
    frame.clearStack();

    try
    {
      frame.push(BasicValue.UNINITIALIZED_VALUE);
      fail("IllegalArgumentException expected");
    }
    catch (IllegalArgumentException e)
    {
      // expected
    }
  }
}
