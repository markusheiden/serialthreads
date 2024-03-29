package org.serialthreads.transformer.debug;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Label;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.util.Textifier;
import org.serialthreads.transformer.analyzer.ExtendedFrame;

import static org.objectweb.asm.Opcodes.ASM9;

/**
 * Extended {@link Textifier} which adds frame infos after each byte code instruction.
 */
class DebugPrinter extends Textifier {
  private int instruction;
  private final ExtendedFrame[] frames;

  public DebugPrinter(ExtendedFrame[] frames) {
    super(ASM9);

    this.instruction = 0;
    this.frames = frames;
  }

  private void addByteCodeIndexWithoutFrame(int lastSize) {
    var index = "000" + instruction;
    index = index.substring(index.length() - 4);

    for (int i = lastSize; i < text.size(); i++) {
      var line = (String) text.get(i);
      text.set(i, index.toUpperCase() + line);
    }

    instruction++;
  }

  private void addByteCodeIndex(int lastSize) {
    var frame = frames[instruction];
    if (frame != null) {
      var frameText = new ArrayList<String>();
      for (int i = 0; i < frame.getLocals(); i++) {
        var local = frame.getLocal(i);
        if (local != BasicValue.UNINITIALIZED_VALUE) {
          var value = local.isReference() ? local.getType().getDescriptor() : local;
          frameText.add(tab3 + "Local: " + i + ": " + value + "\n");
        }
      }
      frameText.add(tab3 + "Needed locals: " + frame.neededLocals + "\n");
      for (int i = 0; i < frame.getStackSize(); i++) {
        var stack = frame.getStack(i);
        var value = stack.isReference() ? stack.getType().getDescriptor() : stack;
        frameText.add(tab3 + "Stack: " + i + ": " + value + "\n");
      }

      text.addAll(lastSize, frameText);
    }

    addByteCodeIndexWithoutFrame(lastSize);
  }

  private void addNoByteCodeIndex(int lastSize) {
    for (int i = lastSize; i < text.size(); i++) {
      var line = (String) text.get(i);
      text.set(i, "----" + line);
    }
  }

  //
  // instructions
  //

  @Override
  public void visitInsn(int opcode) {
    int lastSize = text.size();
    super.visitInsn(opcode);
    addByteCodeIndex(lastSize);
  }

  @Override
  public void visitIntInsn(int opcode, int operand) {
    int lastSize = text.size();
    super.visitIntInsn(opcode, operand);
    addByteCodeIndex(lastSize);
  }

  @Override
  public void visitVarInsn(int opcode, int var) {
    int lastSize = text.size();
    super.visitVarInsn(opcode, var);
    addByteCodeIndex(lastSize);
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    int lastSize = text.size();
    super.visitTypeInsn(opcode, type);
    addByteCodeIndex(lastSize);
  }

  @Override
  public void visitFieldInsn(int opcode, String owner, String name, String desc) {
    int lastSize = text.size();
    super.visitFieldInsn(opcode, owner, name, desc);
    addByteCodeIndex(lastSize);
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
    int lastSize = text.size();
    super.visitMethodInsn(opcode, owner, name, desc, itf);
    addByteCodeIndex(lastSize);
  }

  @Override
  public void visitJumpInsn(int opcode, Label label) {
    int lastSize = text.size();
    super.visitJumpInsn(opcode, label);
    addByteCodeIndex(lastSize);
  }

  @Override
  public void visitLdcInsn(Object cst) {
    int lastSize = text.size();
    super.visitLdcInsn(cst);
    addByteCodeIndex(lastSize);
  }

  @Override
  public void visitIincInsn(int var, int increment) {
    int lastSize = text.size();
    super.visitIincInsn(var, increment);
    addByteCodeIndex(lastSize);
  }

  @Override
  public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
    int lastSize = text.size();
    super.visitTableSwitchInsn(min, max, dflt, labels);
    addByteCodeIndex(lastSize);
  }

  @Override
  public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
    int lastSize = text.size();
    super.visitLookupSwitchInsn(dflt, keys, labels);
    addByteCodeIndex(lastSize);
  }

  @Override
  public void visitMultiANewArrayInsn(String desc, int dims) {
    int lastSize = text.size();
    super.visitMultiANewArrayInsn(desc, dims);
    addByteCodeIndex(lastSize);
  }

  //
  // no instructions, but which asm counts as instructions
  //

  @Override
  public void visitLabel(Label label) {
    int lastSize = text.size();
    super.visitLabel(label);
    addByteCodeIndexWithoutFrame(lastSize);
  }

  @Override
  public void visitLineNumber(int line, Label start) {
    int lastSize = text.size();
    super.visitLineNumber(line, start);
    addByteCodeIndexWithoutFrame(lastSize);
  }

  @Override
  public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
    int lastSize = text.size();
    super.visitFrame(type, nLocal, local, nStack, stack);
    addByteCodeIndexWithoutFrame(lastSize);
  }

  //
  //
  //

  @Override
  public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
    int lastSize = text.size();
    super.visitTryCatchBlock(start, end, handler, type);
    addNoByteCodeIndex(lastSize);
  }

  @Override
  public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
    int lastSize = text.size();
    super.visitLocalVariable(name, desc, signature, start, end, index);
    addNoByteCodeIndex(lastSize);
  }

  @Override
  public void visitMaxs(int maxStack, int maxLocals) {
    int lastSize = text.size();
    super.visitMaxs(maxStack, maxLocals);
    addNoByteCodeIndex(lastSize);
  }
}
