package org.serialthreads.context;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Used to store the content of a stack frame.
 */
public final class StackFrame implements Serializable {
    public static int DEFAULT_FRAME_SIZE = 64;
    public static final int FAST_FRAME_SIZE = 8;

    public Stack stack;
    // Double linked list of stack frames.
    public final @Nullable StackFrame previous;
    public @Nullable StackFrame next;

    // Method owner and index of restore code.
    public @Nullable Object owner;
    public int method;

    // Size of frame.
    private int size;

    // Stack.
    public Object[] stackObjects;
    public int[] stackInts;
    public long[] stackLongs;
    public float[] stackFloats;
    public double[] stackDoubles;

    // Fast stack.
    public @Nullable Object stackObject0;
    public @Nullable Object stackObject1;
    public @Nullable Object stackObject2;
    public @Nullable Object stackObject3;
    public @Nullable Object stackObject4;
    public @Nullable Object stackObject5;
    public @Nullable Object stackObject6;
    public @Nullable Object stackObject7;
    public int stackInt0;
    public int stackInt1;
    public int stackInt2;
    public int stackInt3;
    public int stackInt4;
    public int stackInt5;
    public int stackInt6;
    public int stackInt7;
    public long stackLong0;
    public long stackLong1;
    public long stackLong2;
    public long stackLong3;
    public long stackLong4;
    public long stackLong5;
    public long stackLong6;
    public long stackLong7;
    public float stackFloat0;
    public float stackFloat1;
    public float stackFloat2;
    public float stackFloat3;
    public float stackFloat4;
    public float stackFloat5;
    public float stackFloat6;
    public float stackFloat7;
    public double stackDouble0;
    public double stackDouble1;
    public double stackDouble2;
    public double stackDouble3;
    public double stackDouble4;
    public double stackDouble5;
    public double stackDouble6;
    public double stackDouble7;

    // Locals.
    public Object[] localObjects;
    public int[] localInts;
    public long[] localLongs;
    public float[] localFloats;
    public double[] localDoubles;

    // Fast locals.
    public @Nullable Object localObject0;
    public @Nullable Object localObject1;
    public @Nullable Object localObject2;
    public @Nullable Object localObject3;
    public @Nullable Object localObject4;
    public @Nullable Object localObject5;
    public @Nullable Object localObject6;
    public @Nullable Object localObject7;
    public int localInt0;
    public int localInt1;
    public int localInt2;
    public int localInt3;
    public int localInt4;
    public int localInt5;
    public int localInt6;
    public int localInt7;
    public long localLong0;
    public long localLong1;
    public long localLong2;
    public long localLong3;
    public long localLong4;
    public long localLong5;
    public long localLong6;
    public long localLong7;
    public float localFloat0;
    public float localFloat1;
    public float localFloat2;
    public float localFloat3;
    public float localFloat4;
    public float localFloat5;
    public float localFloat6;
    public float localFloat7;
    public double localDouble0;
    public double localDouble1;
    public double localDouble2;
    public double localDouble3;
    public double localDouble4;
    public double localDouble5;
    public double localDouble6;
    public double localDouble7;

    /**
     * Constructor.
     *
     * @param previous
     *         Previous stack frame for a linked list.
     * @param size
     *         Maximum size of the frame.
     */
    public StackFrame(Stack stack, @Nullable StackFrame previous, int size) {
        this.stack = stack;

        if (previous != null) {
            previous.next = this;
        }

        this.previous = previous;
        this.next = null;

        owner = null;
        // Has to be -1 for dummy startup restore!
        method = -1;

        this.size = size;

        stackObjects = new Object[size];
        stackInts = new int[size];
        stackLongs = new long[size];
        stackFloats = new float[size];
        stackDoubles = new double[size];

        localObjects = new Object[size];
        localInts = new int[size];
        localLongs = new long[size];
        localFloats = new float[size];
        localDoubles = new double[size];
    }

    /**
     * Increase the stack by one frame.
     *
     * @return Added frame.
     */
    public StackFrame addFrame() {
        return new StackFrame(stack, this, size);
    }

    /**
     * Reset frame to empty state.
     */
    public void reset() {
        owner = null;
        method = -1;

        // Just the object slots need to be cleared to avoid memory leaks.
        // Stale primitive values are always written before being read again.
        stackObject0 = null;
        stackObject1 = null;
        stackObject2 = null;
        stackObject3 = null;
        stackObject4 = null;
        stackObject5 = null;
        stackObject6 = null;
        stackObject7 = null;
        Arrays.fill(stackObjects, null);

        localObject0 = null;
        localObject1 = null;
        localObject2 = null;
        localObject3 = null;
        localObject4 = null;
        localObject5 = null;
        localObject6 = null;
        localObject7 = null;
        Arrays.fill(localObjects, null);
    }

    //
    // Resize support.
    //

    /**
     * Grow all arrays, so that they can hold at least max + 1 elements.
     * Never shrinks arrays.
     *
     * @param max
     *         Highest needed index.
     */
    public void resize(int max) {
        if (max < size) {
            return;
        }

        while (size <= max) {
            size <<= 1;
        }

        stackObjects = resize(stackObjects);
        stackInts = resize(stackInts);
        stackLongs = resize(stackLongs);
        stackFloats = resize(stackFloats);
        stackDoubles = resize(stackDoubles);
        localObjects = resize(localObjects);
        localInts = resize(localInts);
        localLongs = resize(localLongs);
        localFloats = resize(localFloats);
        localDoubles = resize(localDoubles);
    }

    private Object[] resize(Object[] old) {
        if (old.length >= size) {
            // The array already has grown to at least the needed size.
            return old;
        }

        final var result = new Object[size];
        System.arraycopy(old, 0, result, 0, old.length);
        return result;
    }

    private int[] resize(int[] old) {
        if (old.length >= size) {
            // The array already has grown to at least the needed size.
            return old;
        }

        final var result = new int[size];
        System.arraycopy(old, 0, result, 0, old.length);
        return result;
    }

    private long[] resize(long[] old) {
        if (old.length >= size) {
            // The array already has grown to at least the needed size.
            return old;
        }

        final var result = new long[size];
        System.arraycopy(old, 0, result, 0, old.length);
        return result;
    }

    private float[] resize(float[] old) {
        if (old.length >= size) {
            // The array already has grown to at least the needed size.
            return old;
        }

        final var result = new float[size];
        System.arraycopy(old, 0, result, 0, old.length);
        return result;
    }

    private double[] resize(double[] old) {
        if (old.length >= size) {
            // The array already has grown to at least the needed size.
            return old;
        }

        final var result = new double[size];
        System.arraycopy(old, 0, result, 0, old.length);
        return result;
    }
}
