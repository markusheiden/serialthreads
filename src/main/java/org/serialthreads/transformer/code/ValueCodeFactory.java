package org.serialthreads.transformer.code;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.BasicValue;

import static org.objectweb.asm.Type.ARRAY;
import static org.objectweb.asm.Type.BOOLEAN;
import static org.objectweb.asm.Type.BYTE;
import static org.objectweb.asm.Type.CHAR;
import static org.objectweb.asm.Type.DOUBLE;
import static org.objectweb.asm.Type.FLOAT;
import static org.objectweb.asm.Type.INT;
import static org.objectweb.asm.Type.LONG;
import static org.objectweb.asm.Type.OBJECT;
import static org.objectweb.asm.Type.SHORT;

/**
 * Factory to get value code for a type.
 */
public final class ValueCodeFactory {
  private static final IValueCode OBJECT_CODE = new ReferenceValueCode(Type.getType(Object.class));
  private static final IValueCode INT_CODE = new IntValueCode();
  private static final IValueCode LONG_CODE = new LongValueCode();
  private static final IValueCode FLOAT_CODE = new FloatValueCode();
  private static final IValueCode DOUBLE_CODE = new DoubleValueCode();

  public static final IValueCode[] CODES = {
    OBJECT_CODE, INT_CODE, LONG_CODE, FLOAT_CODE, DOUBLE_CODE
  };

  /**
   * Get code for specific type.
   *
   * @param value basic value
   */
  public static IValueCode code(BasicValue value) {
    assert value.getType() != null : "Precondition: value.getType() != null";

    return code(value.getType());
  }

  /**
   * Get code for specific type.
   *
   * @param type type
   */
  public static IValueCode code(Type type) {
    assert type != null : "Precondition: type != null";

    return switch (type.getSort()) {
      case BOOLEAN, CHAR, BYTE, SHORT, INT -> INT_CODE;
      case LONG -> LONG_CODE;
      case FLOAT -> FLOAT_CODE;
      case DOUBLE -> DOUBLE_CODE;
      case OBJECT, ARRAY -> new ReferenceValueCode(type);
      default -> throw new IllegalArgumentException("Wrong type " + type);
    };
  }
}
