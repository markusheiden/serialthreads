package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.serialthreads.context.SerialThread;
import org.serialthreads.context.SerialThreadManager;
import org.serialthreads.context.Stack;
import org.serialthreads.context.StackFrame;

import static org.objectweb.asm.Opcodes.*;
import static org.serialthreads.transformer.code.IntValueCode.push;

/**
 * Base code that is independent from stack frame storage algorithm.
 */
public abstract class AbstractStackCode implements ThreadCode {
  private static final String OBJECT_DESC = Type.getType(Object.class).getDescriptor();
  private static final String MANAGER_NAME = Type.getType(SerialThreadManager.class).getInternalName();
  private static final String THREAD_DESC = Type.getType(SerialThread.class).getDescriptor();
  private static final String THREAD_IMPL_NAME = Type.getType(Stack.class).getInternalName();
  private static final String THREAD_IMPL_DESC = Type.getType(Stack.class).getDescriptor();
  private static final String THREAD = "$$thread$$";
  private static final String FRAME = "$$frame$$";
  private static final String FRAME_IMPL_NAME = Type.getType(StackFrame.class).getInternalName();
  private static final String FRAME_IMPL_DESC = Type.getType(StackFrame.class).getDescriptor();

  //
  // Constructors.
  //

  @Override
  public FieldNode threadField() {
    return new FieldNode(ACC_PRIVATE + ACC_FINAL + ACC_SYNTHETIC, THREAD, THREAD_IMPL_DESC, THREAD_IMPL_DESC, null);
  }

  @Override
  public InsnList initRunThread(String className, int defaultFrameSize, int localThread) {
    InsnList instructions = new InsnList();

    // thread = new Stack(this, defaultFrameSize);
    instructions.add(new TypeInsnNode(NEW, THREAD_IMPL_NAME));
    instructions.add(new InsnNode(DUP));
    instructions.add(new VarInsnNode(ALOAD, 0));
    instructions.add(IntValueCode.push(defaultFrameSize));
    instructions.add(new MethodInsnNode(INVOKESPECIAL, THREAD_IMPL_NAME, "<init>", "(" + OBJECT_DESC + "I)V", false));
    instructions.add(new VarInsnNode(ASTORE, localThread));

    // this.$$thread$$ = thread;
    instructions.add(new VarInsnNode(ALOAD, 0));
    instructions.add(new VarInsnNode(ALOAD, localThread));
    instructions.add(initRunThread(className));

    return instructions;
  }

  @Override
  public InsnList initRunThread(String className) {
    InsnList instructions = new InsnList();
    // this.$$thread$$ = stack;
    instructions.add(new FieldInsnNode(PUTFIELD, className, THREAD, THREAD_IMPL_DESC));
    return instructions;
  }

  @Override
  public InsnList getRunThread(String className, int localThread) {
    InsnList instructions = new InsnList();
    // stack = this.$$thread$$;
    instructions.add(new VarInsnNode(ALOAD, 0));
    instructions.add(new FieldInsnNode(GETFIELD, className, THREAD, THREAD_IMPL_DESC));
    instructions.add(new VarInsnNode(ASTORE, localThread));
    return instructions;
  }

  @Override
  public InsnList getThread(int localThread) {
    InsnList instructions = new InsnList();
    // thread = SerialThreadManager.getThread();
    instructions.add(new MethodInsnNode(INVOKESTATIC, MANAGER_NAME, "getThread", "()" + THREAD_DESC, false));
    instructions.add(new TypeInsnNode(CHECKCAST, THREAD_IMPL_NAME));
    instructions.add(new VarInsnNode(ASTORE, localThread));
    return instructions;
  }

  @Override
  public InsnList pushThread(int localFrame) {
    InsnList instructions = new InsnList();
    // stack = frame.stack;
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "stack", THREAD_IMPL_DESC));
    return instructions;
  }

  @Override
  public FieldNode frameField() {
    return new FieldNode(ACC_PRIVATE + ACC_FINAL + ACC_SYNTHETIC, FRAME, FRAME_IMPL_DESC, FRAME_IMPL_DESC, null);
  }

  @Override
  public InsnList initRunFrame(int localThread, String className) {
    InsnList instructions = new InsnList();
    // this.$$frame$$ = thread.first;
    instructions.add(new VarInsnNode(ALOAD, 0));
    instructions.add(new VarInsnNode(ALOAD, localThread));
    instructions.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "first", FRAME_IMPL_DESC));
    instructions.add(new FieldInsnNode(PUTFIELD, className, FRAME, FRAME_IMPL_DESC));
    return instructions;
  }

  @Override
  public InsnList getRunFrame(String className, int localFrame) {
    InsnList instructions = new InsnList();
    // frame = this.$$frame$$;
    instructions.add(new VarInsnNode(ALOAD, 0));
    instructions.add(new FieldInsnNode(GETFIELD, className, FRAME, FRAME_IMPL_DESC));
    instructions.add(new VarInsnNode(ASTORE, localFrame));
    return instructions;
  }

  //
  // Capture.
  //

  @Override
  public InsnList getNextFrame(int localPreviousFrame, int localFrame, boolean addIfNotPresent) {
    InsnList instructions = new InsnList();

    // frame = previousFrame.next;
    instructions.add(new VarInsnNode(ALOAD, localPreviousFrame));
    instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "next", FRAME_IMPL_DESC));
    if (addIfNotPresent) {
      LabelNode normal = new LabelNode();

      instructions.add(new InsnNode(DUP));
      instructions.add(new JumpInsnNode(IFNONNULL, normal));
      instructions.add(new InsnNode(POP));
      // frame = previousFrame.addFrame();
      instructions.add(new VarInsnNode(ALOAD, localPreviousFrame));
      instructions.add(new MethodInsnNode(INVOKEVIRTUAL, FRAME_IMPL_NAME, "addFrame", "()" + FRAME_IMPL_DESC, false));
      instructions.add(normal);
    }
    instructions.add(new VarInsnNode(ASTORE, localFrame));

    return instructions;
  }

  @Override
  public InsnList setOwner(int localPreviousFrame) {
    InsnList instructions = new InsnList();
    // previousFrame.owner = this;
    instructions.add(new VarInsnNode(ALOAD, localPreviousFrame));
    instructions.add(new VarInsnNode(ALOAD, 0));
    instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
    return instructions;
  }

  @Override
  public InsnList setMethod(int localFrame, int position) {
    InsnList instructions = new InsnList();
    // frame.method = position;
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(push(position));
    instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, "method", "I"));
    return instructions;
  }

  @Override
  public InsnList setSerializing(int localThread, boolean serializing) {
    InsnList instructions = new InsnList();
    // thread.serializing = serializing;
    instructions.add(new VarInsnNode(ALOAD, localThread));
    instructions.add(new InsnNode(serializing ? ICONST_1 : ICONST_0));
    instructions.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    return instructions;
  }

  //
  // Restore.
  //

  @Override
  public InsnList pushSerializing(int localThread) {
    InsnList instructions = new InsnList();
    // stack = thread.serializing;
    instructions.add(new VarInsnNode(ALOAD, localThread));
    instructions.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "serializing", "Z"));
    return instructions;
  }

  @Override
  public InsnList getPreviousFrame(int localThread, int localPreviousFrame) {
    InsnList instructions = new InsnList();
    // previousFrame = thread.frame;
    instructions.add(new VarInsnNode(ALOAD, localThread));
    instructions.add(new FieldInsnNode(GETFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));
    instructions.add(new VarInsnNode(ASTORE, localPreviousFrame));
    return instructions;
  }

  @Override
  public InsnList setFrame(int localThread, int localFrame) {
    InsnList instructions = new InsnList();
    // thread.frame = frame;
    instructions.add(new VarInsnNode(ALOAD, localThread));
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "frame", FRAME_IMPL_DESC));
    return instructions;
  }

  @Override
  public InsnList pushOwner(int localFrame) {
    InsnList instructions = new InsnList();
    // stack = frame.owner;
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "owner", OBJECT_DESC));
    return instructions;
  }

  @Override
  public InsnList pushMethod(int localFrame) {
    InsnList instructions = new InsnList();
    // stack = frame.method;
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(new FieldInsnNode(GETFIELD, FRAME_IMPL_NAME, "method", "I"));
    return instructions;
  }
}
