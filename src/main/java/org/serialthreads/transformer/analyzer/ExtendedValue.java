package org.serialthreads.transformer.analyzer;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.BasicValue;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSortedSet;
import static java.util.Objects.hash;

/**
 * Extended value.
 * A basic value which - besides the type - can additionally hold,
 * whether the value is "this" or a method parameter.
 */
public final class ExtendedValue extends BasicValue {
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
     * @param type
     *         type of value
     */
    public static ExtendedValue value(Type type) {
        return new ExtendedValue(type, NOT_CONSTANT, emptySet());
    }

    /**
     * Factory method for a value which is hold in a local too.
     *
     * @param type
     *         type of value
     * @param local
     *         number of local
     */
    public static ExtendedValue valueInLocal(Type type, int local) {
        assert type != null : "Precondition: type != null";
        assert local >= 0 : "Precondition: local >= 0";

        return new ExtendedValue(type, NOT_CONSTANT, Set.of(local));
    }

    /**
     * Factory method for a value which is hold in locals too.
     *
     * @param type
     *         type of value
     * @param locals
     *         number of locals
     */
    public static ExtendedValue valueInLocals(Type type, Set<Integer> locals) {
        assert type != null : "Precondition: type != null";
        assert locals != null : "Precondition: locals != null";

        return new ExtendedValue(type, NOT_CONSTANT, locals);
    }

    /**
     * Factory method for a constant value which is currently not hold in a local.
     *
     * @param type
     *         type of value
     * @param constant
     *         constant value
     */
    public static ExtendedValue constantValue(Type type, Object constant) {
        assert type != null : "Precondition: type != null";

        return new ExtendedValue(type, constant, Set.of());
    }

    /**
     * Factory method for a constant value which is hold in locals too.
     *
     * @param type
     *         type of value
     * @param constant
     *         constant value
     * @param locals
     *         number of locals
     */
    public static ExtendedValue constantInLocals(Type type, Object constant, Set<Integer> locals) {
        assert type != null : "Precondition: type != null";
        assert locals != null : "Precondition: locals != null";

        return new ExtendedValue(type, constant, locals);
    }

    /**
     * Constructor for values which are hold in a local too.
     *
     * @param type
     *         type of value
     * @param constant
     *         constant value this value represents
     * @param locals
     *         number of locals
     */
    private ExtendedValue(Type type, Object constant, Set<Integer> locals) {
        super(type);

        assert type != null : "Precondition: type != null";
        assert locals != null : "Precondition: locals != null";

        this.constant = constant;
        this.locals = unmodifiableSortedSet(new TreeSet<>(locals));
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

        assert constant != NOT_CONSTANT : "Postcondition: constant != NOT_CONSTANT";
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
     * @param local
     *         local to add
     */
    public ExtendedValue addLocal(int local) {
        assert local >= 0 : "Precondition: local >= 0";

        if (locals.contains(local)) {
            return this;
        }

        var modifiedLocals = new HashSet<>(locals);
        modifiedLocals.add(local);
        return new ExtendedValue(getType(), constant, modifiedLocals);
    }

    /**
     * Notifies this value, that a local has been modified.
     *
     * @param modifiedLocal
     *         the local that has been modified
     */
    public ExtendedValue removeLocal(int modifiedLocal) {
        assert modifiedLocal >= 0 : "Precondition: modifiedLocal >= 0";

        if (!locals.contains(modifiedLocal)) {
            return this;
        }

        var modifiedLocals = new HashSet<>(locals);
        modifiedLocals.remove(modifiedLocal);
        return new ExtendedValue(getType(), constant, modifiedLocals);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Additionally checks, that the extended attributes are equal too.
     * This is needed, because the analyzer detects changes of merged values via equals().
     * So an extended value never equals a basic value, though a basic value equals an extended value of the same type.
     */
    @Override
    public boolean equals(Object value) {
        return value == this ||
               value instanceof ExtendedValue other &&
               super.equals(other) &&
               Objects.equals(constant, other.constant) &&
               locals.equals(other.locals);
    }

    @Override
    public int hashCode() {
        return hash(getType(), constant, locals);
    }
}
