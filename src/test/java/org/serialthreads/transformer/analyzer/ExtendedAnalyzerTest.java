package org.serialthreads.transformer.analyzer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Frame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Type.DOUBLE_TYPE;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.LONG_TYPE;
import static org.objectweb.asm.tree.analysis.BasicValue.DOUBLE_VALUE;
import static org.objectweb.asm.tree.analysis.BasicValue.INT_VALUE;
import static org.objectweb.asm.tree.analysis.BasicValue.LONG_VALUE;
import static org.objectweb.asm.tree.analysis.BasicValue.UNINITIALIZED_VALUE;

/**
 * Test for {@link ExtendedAnalyzer}.
 */
class ExtendedAnalyzerTest {
  /**
   * Analyzer instance for testing.
   */
  private ExtendedAnalyzer analyzer = new ExtendedAnalyzer(new ExtendedVerifier(null, null, null, null, false));

  /**
   * Test for {@link ExtendedAnalyzer#newFrame(int, int)}.
   */
  @Test
  void testNewFrame_ii() {
    var frame = analyzer.newFrame(2, 2);

    frame.setLocal(0, UNINITIALIZED_VALUE);
    frame.setLocal(1, INT_VALUE);
    assertThat(frame.getLocal(0)).isEqualTo(UNINITIALIZED_VALUE);
    assertThat(frame.getLocal(1)).isEqualTo(ExtendedValue.valueInLocal(INT_TYPE, 1));
    assertThat(frame.getLocals()).isEqualTo(2);

    frame.push(LONG_VALUE);
    frame.push(DOUBLE_VALUE);
    assertThat(frame.getStack(0)).isEqualTo(ExtendedValue.value(LONG_TYPE));
    assertThat(frame.getStack(1)).isEqualTo(ExtendedValue.value(DOUBLE_TYPE));
    assertThat(frame.getStackSize()).isEqualTo(2);

    assertThatExceptionOfType(IndexOutOfBoundsException.class)
      .as("Expected max stack = 2")
      .isThrownBy(() -> frame.push(INT_VALUE));
  }

  /**
   * Test for {@link ExtendedAnalyzer#newFrame(Frame)}.
   */
  @Test
  void testNewFrame_frame() {
    var src = new ExtendedFrame(2, 2);
    src.setLocal(0, UNINITIALIZED_VALUE);
    src.setLocal(1, INT_VALUE);
    src.push(LONG_VALUE);
    src.push(DOUBLE_VALUE);

    var frame = analyzer.newFrame(src);

    assertThat(frame.getLocal(0)).isEqualTo(UNINITIALIZED_VALUE);
    assertThat(frame.getLocal(1)).isEqualTo(ExtendedValue.valueInLocal(INT_TYPE, 1));
    assertThat(frame.getLocals()).isEqualTo(2);

    assertThat(frame.getStack(0)).isEqualTo(ExtendedValue.value(LONG_TYPE));
    assertThat(frame.getStack(1)).isEqualTo(ExtendedValue.value(DOUBLE_TYPE));
    assertThat(frame.getStackSize()).isEqualTo(2);

    assertThatExceptionOfType(IndexOutOfBoundsException.class)
      .as("Expected max stack = 2")
      .isThrownBy(() -> frame.push(INT_VALUE));
  }

  /**
   * Test for backward flow analysis with simple byte code.
   */
  @Test
  void testBackflow_simple() throws Exception {
    var method = new MethodNode(0, "test", "()I", null, new String[0]);
    method.maxLocals = 4;
    method.maxStack = 1;
    var instructions = method.instructions;

    var label1 = new LabelNode();
    var label2 = new LabelNode();

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

    var frames = analyzer.analyze("Test", method);

    // Check that at instruction 15 just local 1 is declared as needed for the remaining code
    assertThat(frames[15].neededLocals).containsOnly(1);
    // Check that at instruction 8 just locals 1 & 2 are declared as needed for the remaining code
    assertThat(frames[8].neededLocals).containsOnly(1, 2);
    // Check that at instruction 12 just locals 1 & 3 are declared as needed for the remaining code
    assertThat(frames[12].neededLocals).containsOnly(1, 3);

    // Check that at instruction 7 (merge point) locals 1, 2 & 3 are declared as needed for the remaining code
    assertThat(frames[7].neededLocals).containsOnly(1, 2, 3);
  }

  /**
   * Test for backward flow analysis with an endless loop.
   */
  @Test
  void testBackflow_endless() throws Exception {
    var method = new MethodNode(0, "test", "()I", null, new String[0]);
    method.maxLocals = 3;
    method.maxStack = 1;
    var instructions = method.instructions;

    var label1 = new LabelNode();

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

    var frames = analyzer.analyze("Test", method);

    // Check that at instruction 5 locals 1 is declared as needed for the remaining code
    assertThat(frames[5].neededLocals).containsOnly(1);
  }

  /**
   * Test for backward flow analysis that ensures that locals are just considered as needed the shortest possible range.
   */
  @Test
  void testBackflow_minimumNeededLocals() throws Exception {
    var method = new MethodNode(0, "test", "()I", null, new String[0]);
    method.maxLocals = 2;
    method.maxStack = 1;
    var instructions = method.instructions;

    instructions.add(new InsnNode(ICONST_1));
    instructions.add(new VarInsnNode(ISTORE, 1));
    instructions.add(new VarInsnNode(ILOAD, 1));
    instructions.add(new InsnNode(IRETURN));

    var frames = analyzer.analyze("Test", method);

    // Before ICONST_1.
    assertThat(frames[0].neededLocals).isEmpty();
    // Before ISTORE 1: Local 1 is overwritten -> Local 1 is not needed here and before.
    assertThat(frames[1].neededLocals).isEmpty();
    // Before ILOAD 1: Local 1 is used -> Local 1 is needed.
    assertThat(frames[2].neededLocals).containsOnly(1);
    // Before IRETURN.
    assertThat(frames[3].neededLocals).isEmpty();
  }
}
