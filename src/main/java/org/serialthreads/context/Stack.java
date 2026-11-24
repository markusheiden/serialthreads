package org.serialthreads.context;

import org.jspecify.annotations.Nullable;

/**
 * Stack for all frames of a thread.
 */
public final class Stack implements SerialThread {
    /**
     * Name.
     */
    private final String name;

    /**
     * The first frame.
     */
    public StackFrame first;

    /**
     * The currently active frame.
     */
    public StackFrame frame;

    /**
     * Is the thread in the capturing or restoring phase?.
     */
    public boolean serializing;

    /**
     * Return value of the last executed method: Object.
     */
    public @Nullable Object returnObject;

    /**
     * Return value of the last executed method: int.
     */
    public int returnInt;

    /**
     * Return value of the last executed method: long.
     */
    public long returnLong;

    /**
     * Return value of the last executed method: float.
     */
    public float returnFloat;

    /**
     * Return value of the last executed method: double.
     */
    public double returnDouble;

    /**
     * Constructor.
     *
     * @param runnable
     *         Runnable.
     * @param frameSize
     *         Default size of frames.
     */
    public Stack(Object runnable, int frameSize) {
        this.name = runnable.getClass().getSimpleName();
        this.first = new StackFrame(this, null, frameSize);
        this.frame = first;

        serializing = false;
        returnObject = null;
        returnInt = 0;
        returnLong = 0;
        returnFloat = 0;
        returnDouble = 0;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Resets all frames below this.
     * Needed after an exception has been thrown to clean up the stack.
     *
     * @param resetTo
     *         Frame to reset to.
     */
    public void resetTo(StackFrame resetTo) {
        for (var frame = resetTo.next; frame != null; frame = frame.next) {
            frame.reset();
        }
    }

    /**
     * Resets the complete stack, so that the thread can be started again.
     */
    public void reset() {
        // Reset the first frame too, so that the run() method starts from the beginning again.
        first.reset();
        resetTo(first);
        frame = first;

        serializing = false;
        returnObject = null;
        returnInt = 0;
        returnLong = 0;
        returnFloat = 0;
        returnDouble = 0;
    }

    @Override
    public String toString() {
        return name;
    }
}
