package org.serialthreads.transformer.analyzer;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.BasicValue;

import java.util.*;

/**
 * Extended value.
 * A basic value which - besides the type - can additionally hold,
 * whether the value is "this" or a method parameter.
 */
public class ExtendedValue extends BasicValue {
  /**
   * Constant for not constant values.
   */
  private static final Object NOT_CONSTANT = new Object() {
    @Override
    public String toString() {
      return "NOT_CONSTANT";
    }
  };

  /**
   * Constant value.
   */
  private final Object constant;

  /**
   * The locals this value is hold in too.
   */
  private final SortedSet<Integer> locals;

  /**
   * Factory method for a value which is currently not hold in a local.
   *
   * @param type type of value
   */
  public static ExtendedValue value(Type type) {
    return new ExtendedValue(type, NOT_CONSTANT, Collections.<Integer>emptySet());
  }

  /**
   * Factory method for a value which is hold in a local too.
   *
   * @param type type of value
   * @param local number of local
   */
  public static ExtendedValue valueInLocal(Type type, int local) {
    assert type != null : "Precondition: type != null";
    assert local >= 0 : "Precondition: local >= 0";

    return new ExtendedValue(type, NOT_CONSTANT, Collections.singleton(local));
  }

  /**
   * Factory method for a value which is hold in locals too.
   *
   * @param type type of value
   * @param locals number of locals
   */
  public static ExtendedValue valueInLocals(Type type, Set<Integer> locals) {
    assert type != null : "Precondition: type != null";
    assert locals != null : "Precondition: locals != null";

    return new ExtendedValue(type, NOT_CONSTANT, locals);
  }

  /**
   * Factory method for a constant value which is currently not hold in a local.
   *
   * @param type type of value
   * @param constant constant value
   */
  public static ExtendedValue constantValue(Type type, Object constant) {
    assert type != null : "Precondition: type != null";

    return new ExtendedValue(type, constant, Collections.<Integer>emptySet());
  }

  /**
   * Factory method for a constant value which is hold in locals too.
   *
   * @param type type of value
   * @param constant constant value
   * @param locals number of locals
   */
  public static ExtendedValue constantInLocals(Type type, Object constant, Set<Integer> locals) {
    assert type != null : "Precondition: type != null";
    assert locals != null : "Precondition: locals != null";

    return new ExtendedValue(type, constant, locals);
  }

  /**
   * Constructor for values which are hold in a local too.
   *
   * @param type type of value
   * @param constant constant value this value represents
   * @param locals number of locals
   */
  private ExtendedValue(Type type, Object constant, Set<Integer> locals) {
    super(type);

    assert locals != null : "Precondition: locals != null";

    this.constant = constant;
    this.locals = Collections.unmodifiableSortedSet(new TreeSet<>(locals));
  }

  /**
   * Is the value constant?.
   */
  public boolean isConstant() {
    return constant != NOT_CONSTANT;
  }

  /**
   * Get constant value.
   */
  public Object getConstant() {
    assert isConstant() : "Precondition: isConstant()";

    assert constant != NOT_CONSTANT : "Precondition: constant != NOT_CONSTANT";
    return constant;
  }

  /**
   * All locals this value is hold in too, if any.
   */
  public SortedSet<Integer> getLocals() {
    return locals;
  }

  /**
   * Notifies this value, that a local has been modified.
   *
   * @param local local to add
   */
  public ExtendedValue addLocal(int local) {
    assert local >= 0 : "Precondition: local >= 0";

    if (locals.contains(local)) {
      return this;
    }

    Set<Integer> modifiedLocals = new HashSet<>(locals);
    modifiedLocals.add(local);
    return new ExtendedValue(getType(), constant, modifiedLocals);
  }

  /**
   * Notifies this value, that a local has been modified.
   *
   * @param modifiedLocal the local that has been modified
   */
  public ExtendedValue removeLocal(int modifiedLocal) {
    assert modifiedLocal >= 0 : "Precondition: modifiedLocal >= 0";

    if (!locals.contains(modifiedLocal)) {
      return this;
    }

    Set<Integer> modifiedLocals = new HashSet<>(locals);
    modifiedLocals.remove(modifiedLocal);
    return new ExtendedValue(getType(), constant, modifiedLocals);
  }

  /**
   * equals() replacement which additionally checks, that the extended attributes are equal too.
   *
   * @param value value to compare against
   */
  public boolean equalsValue(ExtendedValue value) {
    assert value != null : "Precondition: value != null";

    return equals(value) && equalsConstant(value) && equalsLocals(value);
  }

  /**
   * Check that the constant value is the same, if any.
   *
   * @param value value to compare against
   */
  private boolean equalsConstant(ExtendedValue value) {
    assert value != null : "Precondition: value != null";

    return constant == value.constant ||
      constant != null && constant.equals(value.constant);
  }

  /**
   * Check that the locals are identical.
   *
   * @param value value to compare against
   */
  private boolean equalsLocals(ExtendedValue value) {
    assert value != null : "Precondition: value != null";

    return locals.equals(value.locals);
  }
}
