package org.serialthreads.transformer.analyzer;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;
import org.objectweb.asm.tree.analysis.Value;

import java.util.List;

/**
 * Simple verifier which does not check primitive types with ==.
 */
public class FixedVerifier extends SimpleVerifier
{
  /**
   * Constructor.
   * TODO 2010-01-20 mh: use other constructor?
   */
  protected FixedVerifier()
  {
    super(null, null, null, false);
  }

  /**
   * Constructs a new {@link FixedVerifier} to verify a specific class. This
   * class will not be loaded into the JVM since it may be incorrect.
   *
   * @param currentClass the class that is verified.
   * @param currentSuperClass the super class of the class that is verified.
   * @param currentClassInterfaces the interfaces implemented by the class that is verified.
   * @param isInterface if the class that is verified is an interface.
   */
  public FixedVerifier(
    final Type currentClass,
    final Type currentSuperClass,
    final List currentClassInterfaces,
    final boolean isInterface)
  {
    super(currentClass, currentSuperClass, currentClassInterfaces, isInterface);
  }

  /**
   * {@inheritDoc}
   *
   * Bugfix.
   */
  @Override
  public Value copyOperation(AbstractInsnNode insn, Value value) throws AnalyzerException
  {
    Value expected;
    switch (insn.getOpcode())
    {
      case ILOAD:
      case ISTORE:
        expected = BasicValue.INT_VALUE;
        break;
      case FLOAD:
      case FSTORE:
        expected = BasicValue.FLOAT_VALUE;
        break;
      case LLOAD:
      case LSTORE:
        expected = BasicValue.LONG_VALUE;
        break;
      case DLOAD:
      case DSTORE:
        expected = BasicValue.DOUBLE_VALUE;
        break;
      default:
        return super.copyOperation(insn, value);
    }
    // TODO 2009-10-11 mh: BUGFIX: used equals instead of == !
    if (!expected.equals(value))
    {
      throw new AnalyzerException(null, expected, value);
    }

    return value;
  }

  public Value ternaryOperation(
    final AbstractInsnNode insn,
    final Value value1,
    final Value value2,
    final Value value3) throws AnalyzerException
  {
    Value expected1;
    Value expected3;
    switch (insn.getOpcode())
    {
      case IASTORE:
        expected1 = newValue(Type.getType("[I"));
        expected3 = BasicValue.INT_VALUE;
        break;
      case BASTORE:
        if (isSubTypeOf(value1, newValue(Type.getType("[Z"))))
        {
          expected1 = newValue(Type.getType("[Z"));
        }
        else
        {
          expected1 = newValue(Type.getType("[B"));
        }
        expected3 = BasicValue.INT_VALUE;
        break;
      case CASTORE:
        expected1 = newValue(Type.getType("[C"));
        expected3 = BasicValue.INT_VALUE;
        break;
      case SASTORE:
        expected1 = newValue(Type.getType("[S"));
        expected3 = BasicValue.INT_VALUE;
        break;
      case LASTORE:
        expected1 = newValue(Type.getType("[J"));
        expected3 = BasicValue.LONG_VALUE;
        break;
      case FASTORE:
        expected1 = newValue(Type.getType("[F"));
        expected3 = BasicValue.FLOAT_VALUE;
        break;
      case DASTORE:
        expected1 = newValue(Type.getType("[D"));
        expected3 = BasicValue.DOUBLE_VALUE;
        break;
      case AASTORE:
        expected1 = value1;
        expected3 = BasicValue.REFERENCE_VALUE;
        break;
      default:
        throw new Error("Internal error.");
    }
    if (!isSubTypeOf(value1, expected1))
    {
      throw new AnalyzerException("First argument", "a " + expected1 + " array reference", value1);
    }
    else if (!value2.equals(BasicValue.INT_VALUE))
    {
      throw new AnalyzerException("Second argument", BasicValue.INT_VALUE, value2);
    }
    else if (!isSubTypeOf(value3, expected3))
    {
      throw new AnalyzerException("Third argument", expected3, value3);
    }

    return null;
  }
}
