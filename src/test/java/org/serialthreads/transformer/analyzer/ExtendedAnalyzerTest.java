package org.serialthreads.transformer.analyzer;

import org.junit.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.objectweb.asm.Opcodes.*;

/**
 * Test for {@link ExtendedAnalyzer}.
 */
public class ExtendedAnalyzerTest {
  /**
   * Analyzer instance for testing.
   */
  private ExtendedAnalyzer analyzer = new ExtendedAnalyzer(new ExtendedVerifier(null, null, null, null, false));

  /**
   * Test for {@link ExtendedAnalyzer#newFrame(int, int)}.
   */
  @Test
  public void testNewFrame_ii() {
    ExtendedFrame frame = analyzer.newFrame(2, 2);

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

    try {
      frame.push(BasicValue.INT_VALUE);
      fail("Expected max stack = 2");
    } catch (IndexOutOfBoundsException e) {
      // expected
    }
  }

  /**
   * Test for {@link ExtendedAnalyzer#newFrame(Frame)}.
   */
  @Test
  public void testNewFrame_frame() {
    ExtendedFrame src = new ExtendedFrame(2, 2);
    src.setLocal(0, BasicValue.UNINITIALIZED_VALUE);
    src.setLocal(1, BasicValue.INT_VALUE);
    src.push(BasicValue.LONG_VALUE);
    src.push(BasicValue.DOUBLE_VALUE);

    ExtendedFrame frame = analyzer.newFrame(src);

    assertEquals(BasicValue.UNINITIALIZED_VALUE, frame.getLocal(0));
    assertEquals(ExtendedValue.valueInLocal(Type.INT_TYPE, 1), frame.getLocal(1));
    assertEquals(2, frame.getLocals());

    assertEquals(ExtendedValue.value(Type.LONG_TYPE), frame.getStack(0));
    assertEquals(ExtendedValue.value(Type.DOUBLE_TYPE), frame.getStack(1));
    assertEquals(2, frame.getStackSize());

    try {
      frame.push(BasicValue.INT_VALUE);
      fail("Expected max stack = 2");
    } catch (IndexOutOfBoundsException e) {
      // expected
    }
  }

  /**
   * Test for backward flow analysis with simple byte code.
   */
  @Test
  public void testBackflow_simple() throws Exception {
    MethodNode method = new MethodNode(0, "test", "()I", null, new String[0]);
    method.maxLocals = 4;
    method.maxStack = 1;
    InsnList instructions = method.instructions;

    LabelNode label1 = new LabelNode();
    LabelNode label2 = new LabelNode();

    // 0: define local1, local2 and local3
    instructions.add(new InsnNode(ICONST_1));
    instructions.add(new VarInsnNode(ISTORE, 1));
    instructions.add(new InsnNode(ICONST_1));
    instructions.add(new VarInsnNode(ISTORE, 2));
    instructions.add(new InsnNode(ICONST_1));
    instructions.add(new VarInsnNode(ISTORE, 3));

    instructions.add(new InsnNode(ICONST_0));
    // 7: (pseudo) conditional branch -> at this point local1, local2 and local3 are needed for the remaining code
    instructions.add(new JumpInsnNode(IFEQ, label2));

    // 8: usage of local2 -> at this point just local2 and local1 are needed for the remaining code
    instructions.add(new VarInsnNode(ILOAD, 2));
    instructions.add(new VarInsnNode(ISTORE, 2));
    instructions.add(new JumpInsnNode(GOTO, label1));

    instructions.add(label2);
    // 12: usage of local3 -> at this point just local3 and local1 are needed for the remaining code
    instructions.add(new VarInsnNode(ILOAD, 3));
    instructions.add(new VarInsnNode(ISTORE, 3));

    instructions.add(label1);
    // 15: return local1 -> a this point just local1 is needed for the remaining code
    instructions.add(new VarInsnNode(ILOAD, 1));
    instructions.add(new InsnNode(IRETURN));

    ExtendedFrame[] frames = analyzer.analyze("Test", method);

    // Check that at instruction 15 just local 1 is declared as needed for the remaining code
    assertEquals(set(1), frames[15].neededLocals);
    // Check that at instruction 8 just locals 1 & 2 are declared as needed for the remaining code
    assertEquals(set(1, 2), frames[8].neededLocals);
    // Check that at instruction 12 just locals 1 & 3 are declared as needed for the remaining code
    assertEquals(set(1, 3), frames[12].neededLocals);

    // Check that at instruction 7 (merge point) locals 1, 2 & 3 are declared as needed for the remaining code
    assertEquals(set(1, 2, 3), frames[7].neededLocals);
  }

  /**
   * Test for backward flow analysis with an endless loop.
   */
  @Test
  public void testBackflow_endless() throws Exception {
    MethodNode method = new MethodNode(0, "test", "()I", null, new String[0]);
    method.maxLocals = 3;
    method.maxStack = 1;
    InsnList instructions = method.instructions;

    LabelNode label1 = new LabelNode();

    // 0: define local1 and local2
    instructions.add(new InsnNode(ICONST_1));
    instructions.add(new VarInsnNode(ISTORE, 1));
    instructions.add(new InsnNode(ICONST_1));
    instructions.add(new VarInsnNode(ISTORE, 2));

    instructions.add(label1);
    // 5: usage of local1 -> at this point just local1 is needed for the remaining code
    instructions.add(new VarInsnNode(ILOAD, 1));
    instructions.add(new VarInsnNode(ISTORE, 1));
    instructions.add(new JumpInsnNode(GOTO, label1));

    ExtendedFrame[] frames = analyzer.analyze("Test", method);

    // Check that at instruction 5 locals 1 is declared as needed for the remaining code
    assertEquals(set(1), frames[5].neededLocals);
  }

  /**
   * Create set of Integers.
   *
   * @param ints Integers
   */
  private static Set<Integer> set(Integer... ints) {
    return new HashSet<>(asList(ints));
  }
}
