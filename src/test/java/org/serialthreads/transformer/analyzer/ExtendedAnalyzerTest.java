package org.serialthreads.transformer.analyzer;

import org.junit.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.objectweb.asm.Opcodes.*;

/**
 * Test for {@link ExtendedAnalyzer}.
 */
public class ExtendedAnalyzerTest {
  @Test
  public void testNewFrame_ii() {
    ExtendedAnalyzer analyzer = new ExtendedAnalyzer(null, null, null, null, false);

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

  @Test
  public void testNewFrame_frame() {
    ExtendedAnalyzer analyzer = new ExtendedAnalyzer(null, null, null, null, false);

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

  @Test
  public void testBackflow_simple() throws Exception {
    MethodNode method = new MethodNode(0, "test", "()I", null, new String[0]);
    method.maxLocals = 3;
    method.maxStack = 1;

    InsnList instructions = method.instructions;
    // 0: local1 = 1
    instructions.add(new InsnNode(ICONST_1));
    instructions.add(new VarInsnNode(ISTORE, 1));
    // 2: local2 = 1
    instructions.add(new InsnNode(ICONST_1));
    instructions.add(new VarInsnNode(ISTORE, 2));

    // 4: return local1 -> a this point just local1 is needed for the remaining code
    instructions.add(new VarInsnNode(ILOAD, 1));
    instructions.add(new InsnNode(IRETURN));

    ExtendedAnalyzer analyzer = new ExtendedAnalyzer(null, null, null, null, false);
    Frame[] frames = analyzer.analyze("test", method);

    // Check that at instruction 4 just local 1 is declared as needed for the remaining code
    ExtendedFrame frame4 = (ExtendedFrame) frames[4];
    assertEquals(Collections.singleton(1), frame4.neededLocals);
  }
}
