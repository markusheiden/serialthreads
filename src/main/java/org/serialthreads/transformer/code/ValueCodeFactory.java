package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.BasicValue;

/**
 * Factory to get value code for a type.
 */
public class ValueCodeFactory
{
  private static final IValueCode INT_CODE = new IntValueCode();
  private static final IValueCode LONG_CODE = new LongValueCode();
  private static final IValueCode FLOAT_CODE = new FloatValueCode();
  private static final IValueCode DOUBLE_CODE = new DoubleValueCode();

  public static final IValueCode[] CODES = {
    new ReferenceValueCode(Type.getType(Object.class)), INT_CODE, LONG_CODE, FLOAT_CODE, DOUBLE_CODE
  };

  /**
   * Get code for specific type.
   *
   * @param value basic value
   */
  public static IValueCode code(BasicValue value)
  {
    assert value.getType() != null : "Precondition: value.getType() != null";

    return code(value.getType());
  }

  /**
   * Get code for specific type.
   *
   * @param type type
   */
  public static IValueCode code(Type type)
  {
    assert type != null : "Precondition: type != null";

    switch (type.getSort())
    {
      case Type.BOOLEAN:
      case Type.CHAR:
      case Type.BYTE:
      case Type.SHORT:
      case Type.INT:
        return INT_CODE;
      case Type.LONG:
        return LONG_CODE;
      case Type.FLOAT:
        return FLOAT_CODE;
      case Type.DOUBLE:
        return DOUBLE_CODE;
      case Type.OBJECT:
      case Type.ARRAY:
        return new ReferenceValueCode(type);
      default:
        throw new IllegalArgumentException("Wrong type " + type);
    }
  }
}
