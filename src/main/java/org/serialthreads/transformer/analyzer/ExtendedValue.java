package org.serialthreads.transformer.analyzer;

import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.BasicValue;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static com.github.cowwoc.requirements10.java.DefaultJavaValidators.requireThat;
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
    private final @Nullable Object constant;

    /**
     * The locals this value is held in too.
     */
    private final SortedSet<Integer> locals;

    /**
     * Factory method for a value which is currently not held in a local.
     *
     * @param type
     *         Type of value.
     */
    public static ExtendedValue value(Type type) {
        return new ExtendedValue(type, NOT_CONSTANT, Set.of());
    }

    /**
     * Factory method for a value which is held in a local too.
     *
     * @param type
     *         Type of value.
     * @param local
     *         Number of local.
     */
    public static ExtendedValue valueInLocal(Type type, int local) {
        requireThat(local, "local").isNotNegative();

        return new ExtendedValue(type, NOT_CONSTANT, Set.of(local));
    }

    /**
     * Factory method for a value which is held in locals too.
     *
     * @param type
     *         Type of value.
     * @param locals
     *         Number of locals.
     */
    public static ExtendedValue valueInLocals(Type type, Set<Integer> locals) {
        return new ExtendedValue(type, NOT_CONSTANT, locals);
    }

    /**
     * Factory method for a constant value which is currently not held in a local.
     *
     * @param type
     *         Type of value.
     * @param constant
     *         Constant value.
     */
    public static ExtendedValue constantValue(Type type, @Nullable Object constant) {
        return new ExtendedValue(type, constant, Set.of());
    }

    /**
     * Factory method for a constant value which is held in locals too.
     *
     * @param type
     *         Type of value.
     * @param constant
     *         Constant value.
     * @param locals
     *         Number of locals.
     */
    public static ExtendedValue constantInLocals(Type type, @Nullable Object constant, Set<Integer> locals) {
        return new ExtendedValue(type, constant, locals);
    }

    /**
     * Constructor for values which are held in a local too.
     *
     * @param type
     *         Type of value.
     * @param constant
     *         Constant value this value represents.
     * @param locals
     *         Number of locals.
     */
    private ExtendedValue(Type type, @Nullable Object constant, Set<Integer> locals) {
        super(type);

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
    public @Nullable Object getConstant() {
        requireThat(isConstant(), "isConstant()").isTrue();

        requireThat(constant, "constant").isNotEqualTo(NOT_CONSTANT);
        return constant;
    }

    /**
     * All locals this value is held in too, if any.
     */
    public SortedSet<Integer> getLocals() {
        return locals;
    }

    /**
     * Notifies this value, that a local has been modified.
     *
     * @param local
     *         Local to add.
     */
    public ExtendedValue addLocal(int local) {
        requireThat(local, "local").isNotNegative();

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
     *         The local that has been modified.
     */
    public ExtendedValue removeLocal(int modifiedLocal) {
        requireThat(modifiedLocal, "modifiedLocal").isNotNegative();

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
