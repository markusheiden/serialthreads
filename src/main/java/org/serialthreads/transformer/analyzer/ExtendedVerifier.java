package org.serialthreads.transformer.analyzer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.objectweb.asm.tree.analysis.BasicValue.UNINITIALIZED_VALUE;
import static org.serialthreads.transformer.analyzer.ExtendedValue.constantInLocals;
import static org.serialthreads.transformer.analyzer.ExtendedValue.constantValue;
import static org.serialthreads.transformer.analyzer.ExtendedValue.value;
import static org.serialthreads.transformer.analyzer.ExtendedValue.valueInLocals;

/**
 * Verifier which can merge extended values.
 */
public class ExtendedVerifier extends SimpleVerifier {
  /**
   * The class that is verified.
   */
  private final Type currentClass;

  /**
   * The super class of the class that is verified.
   */
  private final Type currentSuperClass;

  /**
   * The interfaces implemented by the class that is verified.
   */
  private final List<Type> currentClassInterfaces;

  /**
   * If the class that is verified is an interface.
   */
  private final boolean isInterface;

  /**
   * Class info cache to look up references.
   */
  private final IClassInfoCache classInfoCache;

  /**
   * Constructs a new {@link ExtendedVerifier} to verify a specific class. This
   * class will not be loaded into the JVM since it may be incorrect.
   *
   * @param classInfoCache class info cache.
   * @param currentClass the class that is verified.
   * @param currentSuperClass the super class of the class that is verified.
   * @param currentClassInterfaces the interfaces implemented by the class that is verified.
   * @param isInterface if the class that is verified is an interface.
   */
  public ExtendedVerifier(
    IClassInfoCache classInfoCache,
    Type currentClass,
    Type currentSuperClass,
    List<Type> currentClassInterfaces,
    boolean isInterface) {
    super(currentClass, currentSuperClass, currentClassInterfaces, isInterface);

    this.currentClass = currentClass;
    this.currentSuperClass = currentSuperClass;
    this.currentClassInterfaces = currentClassInterfaces;
    this.isInterface = isInterface;
    this.classInfoCache = classInfoCache;
  }

  @Override
  public BasicValue newValue(Type type) {
    return ExtendedValue.value(type);
  }

  @Override
  public ExtendedValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
    // remove references to local, if required
    switch (insn.getOpcode()) {
      case Opcodes.ACONST_NULL:
        return constantValue(Type.getObjectType("null"), null);
      case Opcodes.ICONST_M1:
        return constantValue(Type.INT_TYPE, -1);
      case Opcodes.ICONST_0:
        return constantValue(Type.INT_TYPE, 0);
      case Opcodes.ICONST_1:
        return constantValue(Type.INT_TYPE, 1);
      case Opcodes.ICONST_2:
        return constantValue(Type.INT_TYPE, 2);
      case Opcodes.ICONST_3:
        return constantValue(Type.INT_TYPE, 3);
      case Opcodes.ICONST_4:
        return constantValue(Type.INT_TYPE, 4);
      case Opcodes.ICONST_5:
        return constantValue(Type.INT_TYPE, 5);
      case Opcodes.LCONST_0:
        return constantValue(Type.LONG_TYPE, 0L);
      case Opcodes.LCONST_1:
        return constantValue(Type.LONG_TYPE, 1L);
      case Opcodes.FCONST_0:
        return constantValue(Type.FLOAT_TYPE, 0F);
      case Opcodes.FCONST_1:
        return constantValue(Type.FLOAT_TYPE, 1F);
      case Opcodes.FCONST_2:
        return constantValue(Type.FLOAT_TYPE, 2F);
      case Opcodes.DCONST_0:
        return constantValue(Type.DOUBLE_TYPE, 0D);
      case Opcodes.DCONST_1:
        return constantValue(Type.DOUBLE_TYPE, 1D);
      case Opcodes.BIPUSH:
      case Opcodes.SIPUSH:
        return constantValue(Type.INT_TYPE, ((IntInsnNode) insn).operand);
      case Opcodes.LDC:
        return constantValue(super.newOperation(insn).getType(), ((LdcInsnNode) insn).cst);
      default:
        // TODO 2018-02-20: Support remaining opcodes too!?!
        return extendedValue(super.newOperation(insn));
    }
  }

  /**
   * Convert values to {@link ExtendedValue}.
   */
  private ExtendedValue extendedValue(BasicValue value) throws IndexOutOfBoundsException {
    if (value.equals(BasicValue.UNINITIALIZED_VALUE)) {
      // no uninitialized values on the stack are allowed
      throw new IllegalArgumentException("Stack values have to be initialized");
    } else if (value instanceof ExtendedValue) {
      // the value already has been converted to an extended value -> copy value
      return (ExtendedValue) value;
    } else {
      // convert the value to an extended value -> new value
      return value(value.getType());
    }
  }

  @Override
  public BasicValue merge(BasicValue v, BasicValue w) {
    assert v != null : "Precondition: v != null";
    assert w != null : "Precondition: w != null";

    BasicValue result = super.merge(v, w);
    if (result.equals(UNINITIALIZED_VALUE)) {
      // return uninitialized value
      return result;
    }

    assert !v.equals(UNINITIALIZED_VALUE) : "Check: !v.equals(UNINITIALIZED_VALUE)";
    assert !w.equals(UNINITIALIZED_VALUE) : "Check: !w.equals(UNINITIALIZED_VALUE)";

    final ExtendedValue ev = (ExtendedValue) v;
    final ExtendedValue ew = (ExtendedValue) w;
    if (ev != result || !ev.equalsValue(ew)) {
      // the type has been changed -> create new value with merged constant and locals or
      // the type has not been changed, but the constant or the locals have to be merged

      boolean isConstant = ev.isConstant() && ew.isConstant() &&
        (ev.getConstant() == ew.getConstant() || ev.getConstant() != null && ev.getConstant().equals(ew.getConstant()));

      Set<Integer> mergedLocals = new HashSet<>(ev.getLocals());
      mergedLocals.retainAll(ew.getLocals());

      return isConstant ?
        constantInLocals(result.getType(), ev.getConstant(), mergedLocals) :
        valueInLocals(result.getType(), mergedLocals);
    }

    // the value and its locals have not been changed
    return result;
  }

  @Override
  protected boolean isInterface(final Type t) {
    if (t.equals(currentClass)) {
      return isInterface;
    }
    return classInfoCache.isInterface(t.getInternalName());
  }

  @Override
  protected Type getSuperClass(Type t) {
    if (t.equals(currentClass)) {
      return currentSuperClass;
    }

    return classInfoCache.getSuperClass(t.getInternalName());
  }

  @Override
  @SuppressWarnings("unchecked")
  protected boolean isAssignableFrom(final Type t, final Type u) {
    if (t.equals(u)) {
      return true;
    }
    if (t.equals(currentClass)) {
      return getSuperClass(u) != null && isAssignableFrom(t, getSuperClass(u));
    }
    if (u.equals(currentClass)) {
      if (isAssignableFrom(t, currentSuperClass)) {
        return true;
      }
      if (currentClassInterfaces != null) {
        for (Type v : currentClassInterfaces) {
          if (isAssignableFrom(t, v)) {
            return true;
          }
        }
      }
      return false;
    }
    return classInfoCache.hasSuperClass(u.getInternalName(), t.getInternalName());
  }

  @Override
  protected Class getClass(Type t) {
    throw new UnsupportedOperationException("Classes should be loaded via the class info cache");
  }
}
