package org.serialthreads.transformer.analyzer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.DOUBLE_TYPE;
import static org.objectweb.asm.Type.FLOAT_TYPE;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.LONG_TYPE;
import static org.objectweb.asm.tree.analysis.BasicValue.DOUBLE_VALUE;
import static org.objectweb.asm.tree.analysis.BasicValue.INT_VALUE;
import static org.objectweb.asm.tree.analysis.BasicValue.LONG_VALUE;
import static org.objectweb.asm.tree.analysis.BasicValue.UNINITIALIZED_VALUE;
import static org.serialthreads.transformer.analyzer.ExtendedValueTest.assertEqualsValue;

/**
 * Test for {@link ExtendedFrame}.
 */
public class ExtendedFrameTest {
  /**
   * Test for {@link ExtendedFrame#ExtendedFrame(int, int)}.
   */
  @Test
  public void testConstructor_ii() {
    var frame = new ExtendedFrame(2, 2);

    frame.setLocal(0, UNINITIALIZED_VALUE);
    frame.setLocal(1, INT_VALUE);
    assertEquals(UNINITIALIZED_VALUE, frame.getLocal(0));
    assertEquals(ExtendedValue.valueInLocal(INT_TYPE, 1), frame.getLocal(1));
    assertEquals(2, frame.getLocals());

    frame.push(LONG_VALUE);
    frame.push(DOUBLE_VALUE);
    assertEquals(ExtendedValue.value(LONG_TYPE), frame.getStack(0));
    assertEquals(ExtendedValue.value(DOUBLE_TYPE), frame.getStack(1));
    assertEquals(2, frame.getStackSize());

    try {
      frame.push(INT_VALUE);
      fail("Expected max stack = 2");
    } catch (IndexOutOfBoundsException e) {
      // expected
    }
  }

  /**
   * Test for {@link ExtendedFrame#ExtendedFrame(Frame)}.
   */
  @Test
  public void testConstructor_frame() {
    var src = new ExtendedFrame(2, 2);
    src.setLocal(0, UNINITIALIZED_VALUE);
    src.setLocal(1, INT_VALUE);
    src.push(LONG_VALUE);
    src.push(DOUBLE_VALUE);

    var frame = new ExtendedFrame(src);

    assertEquals(UNINITIALIZED_VALUE, frame.getLocal(0));
    assertEquals(ExtendedValue.valueInLocal(INT_TYPE, 1), frame.getLocal(1));
    assertEquals(2, frame.getLocals());

    assertEquals(ExtendedValue.value(LONG_TYPE), frame.getStack(0));
    assertEquals(ExtendedValue.value(DOUBLE_TYPE), frame.getStack(1));
    assertEquals(2, frame.getStackSize());

    try {
      frame.push(INT_VALUE);
      fail("Expected max stack = 2");
    } catch (IndexOutOfBoundsException e) {
      // expected
    }
  }

  /**
   * Test for {@link ExtendedFrame#execute(AbstractInsnNode, Interpreter)} handling constant values.
   */
  @Test
  public void testExecute_const() throws Exception {
    testExecute_const(new InsnNode(ACONST_NULL), Type.getObjectType("null"), null);
    testExecute_const(new InsnNode(ICONST_M1), INT_TYPE, -1);
    testExecute_const(new InsnNode(ICONST_0), INT_TYPE, 0);
    testExecute_const(new InsnNode(ICONST_1), INT_TYPE, 1);
    testExecute_const(new InsnNode(ICONST_2), INT_TYPE, 2);
    testExecute_const(new InsnNode(ICONST_3), INT_TYPE, 3);
    testExecute_const(new InsnNode(ICONST_4), INT_TYPE, 4);
    testExecute_const(new InsnNode(ICONST_5), INT_TYPE, 5);
    testExecute_const(new InsnNode(LCONST_0), LONG_TYPE, 0L);
    testExecute_const(new InsnNode(LCONST_1), LONG_TYPE, 1L);
    testExecute_const(new InsnNode(FCONST_0), FLOAT_TYPE, 0F);
    testExecute_const(new InsnNode(FCONST_1), FLOAT_TYPE, 1F);
    testExecute_const(new InsnNode(FCONST_2), FLOAT_TYPE, 2F);
    testExecute_const(new InsnNode(DCONST_0), DOUBLE_TYPE, 0D);
    testExecute_const(new InsnNode(DCONST_1), DOUBLE_TYPE, 1D);

    testExecute_const(new IntInsnNode(BIPUSH, 64), INT_TYPE, 64);
    testExecute_const(new IntInsnNode(SIPUSH, 4096), INT_TYPE, 4096);

    testExecute_const(new LdcInsnNode(1000000), INT_TYPE, 1000000);
    testExecute_const(new LdcInsnNode("TEST"), Type.getType(String.class), "TEST");
  }

  private void testExecute_const(AbstractInsnNode instruction, Type type, Object constant) throws Exception {
    var interpreter = new ExtendedVerifier(null, null, null, null, false);

    var frame = new ExtendedFrame(0, 1);
    frame.execute(instruction, interpreter);
    assertEquals(1, frame.getStackSize());
    assertEqualsValue(ExtendedValue.constantValue(type, constant), frame.getStack(0));
  }

  /**
   * Test for {@link ExtendedFrame#execute(AbstractInsnNode, Interpreter)} handling stores to locals.
   */
  @Test
  public void testExecute_store() throws Exception {
    testExecute_store(new VarInsnNode(ISTORE, 0), INT_TYPE);
    testExecute_store(new VarInsnNode(LSTORE, 0), LONG_TYPE);
    testExecute_store(new VarInsnNode(FSTORE, 0), FLOAT_TYPE);
    testExecute_store(new VarInsnNode(DSTORE, 0), DOUBLE_TYPE);
    testExecute_store(new VarInsnNode(ASTORE, 0), Type.getType(Object.class));

    testExecute_store(new IincInsnNode(0, 1), INT_TYPE);
  }

  private void testExecute_store(AbstractInsnNode instruction, Type type) throws Exception {
    var interpreter = new ExtendedVerifier(null, null, null, null, false);

    var frame = new ExtendedFrame(4, 4);
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

  /**
   * Test for {@link ExtendedFrame#setLocal(int, BasicValue)}.
   */
  @Test
  public void testSetLocal() {
    var frame = new ExtendedFrame(1, 0);

    frame.setLocal(0, UNINITIALIZED_VALUE);
    assertEquals(UNINITIALIZED_VALUE, frame.getLocal(0));
    frame.setLocal(0, INT_VALUE);
    assertEqualsValue(ExtendedValue.valueInLocal(INT_TYPE, 0), frame.getLocal(0));
    frame.setLocal(0, ExtendedValue.value(INT_TYPE));
    assertEqualsValue(ExtendedValue.valueInLocal(INT_TYPE, 0), frame.getLocal(0));
    frame.setLocal(0, ExtendedValue.valueInLocal(INT_TYPE, 0));
    assertEqualsValue(ExtendedValue.valueInLocal(INT_TYPE, 0), frame.getLocal(0));
  }

  /**
   * Test for {@link ExtendedFrame#push(BasicValue)}.
   */
  @Test
  public void testPush() {
    var frame = new ExtendedFrame(0, 1);

    frame.push(INT_VALUE);
    assertEqualsValue(ExtendedValue.value(INT_TYPE), frame.getStack(0));
    frame.clearStack();
    frame.push(ExtendedValue.value(INT_TYPE));
    assertEqualsValue(ExtendedValue.value(INT_TYPE), frame.getStack(0));
    frame.clearStack();
    frame.push(ExtendedValue.valueInLocal(INT_TYPE, 0));
    assertEqualsValue(ExtendedValue.valueInLocal(INT_TYPE, 0), frame.getStack(0));
    frame.clearStack();

    try {
      frame.push(UNINITIALIZED_VALUE);
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  /**
   * Test for {@link ExtendedFrame#getLowestNeededLocal(ExtendedValue)}.
   */
  @Test
  public void getLowestNeededLocal() {
    var frame = new ExtendedFrame(5, 0);
    frame.neededLocals.add(2);
    frame.neededLocals.add(3);
    frame.neededLocals.add(4);

    // Local 1: Not needed. Holds the value.
    var value1 = ExtendedValue.valueInLocal(INT_TYPE, 1);
    // Local 2: Needed. Does not hold the value.
    // Local 3: Needed. Holds the value.
    var value3 = ExtendedValue.valueInLocal(INT_TYPE, 3).addLocal(1);
    // Local 4: Needed. Holds the value.
    var value4 = ExtendedValue.valueInLocal(INT_TYPE, 4).addLocal(1).addLocal(3);

    // Local 3 is the lowest local that is needed and holds the value.
    assertEquals(3, frame.getLowestNeededLocal(value4));

    // Local 3 is the lowest local that is needed and holds the value.
    assertEquals(3, frame.getLowestNeededLocal(value3));

    // Local 1 is not needed.
    assertEquals(-1, frame.getLowestNeededLocal(value1));
  }
}
