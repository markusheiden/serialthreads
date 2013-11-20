package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * Code for object values.
 */
public class ReferenceValueCode extends AbstractValueCode {
  private final Type concreteType;

  public ReferenceValueCode(Type type) {
    super(Type.getType(Object.class), "Object", ALOAD, ASTORE, AALOAD, AASTORE, ACONST_NULL, ARETURN);

    this.concreteType = type;
  }

  @Override
  public InsnList popReturnValue(int localThread) {
    InsnList instructions = super.popReturnValue(localThread);
    instructions.add(new VarInsnNode(ALOAD, localThread));
    instructions.add(pushNull());
    instructions.add(new FieldInsnNode(PUTFIELD, THREAD_IMPL_NAME, "returnObject", type.getDescriptor()));

    return instructions;
  }

  @Override
  protected InsnList cast() {
    InsnList instructions = new InsnList();
    instructions.add(new TypeInsnNode(CHECKCAST, concreteType.getInternalName()));

    return instructions;
  }

  @Override
  protected InsnList clear(String name, int localFrame) {
    // TODO 2009-10-11 mh: remove deletion of reference for performance reasons?
    InsnList instructions = new InsnList();
    instructions.add(new VarInsnNode(ALOAD, localFrame));
    instructions.add(pushNull());
    instructions.add(new FieldInsnNode(PUTFIELD, FRAME_IMPL_NAME, name, type.getDescriptor()));

    return instructions;
  }

  @Override
  protected InsnList beforePop() {
    InsnList instructions = new InsnList();
    instructions.add(new InsnNode(DUP));

    return instructions;
  }

  @Override
  protected InsnList afterPop(int i) {
    InsnList instructions = new InsnList();
    instructions.add(new InsnNode(SWAP));
    instructions.add(IntValueCode.push(i));
    instructions.add(pushNull());
    instructions.add(new InsnNode(astore));

    return instructions;
  }

  @Override
  public AbstractInsnNode push(Object value) {
    if (value == null) {
      return new InsnNode(ACONST_NULL);
    }

    return new LdcInsnNode(value);
  }

  @Override
  public boolean isCompatibleWith(Type type) {
    assert type != null : "Precondition: type != null2";

    return super.isCompatibleWith(type) && type.getInternalName().equals(type.getInternalName());
  }

  @Override
  public boolean isResponsibleFor(Type type) {
    return type != null && (Type.OBJECT == type.getSort() || Type.ARRAY == type.getSort());
  }
}
