package org.serialthreads.transformer.strategies;

import org.jspecify.annotations.Nullable;
import org.serialthreads.transformer.analyzer.ExtendedFrame;

import java.util.HashSet;
import java.util.Set;

/**
 * Meta information for an instruction.
 */
public class MetaInfo {
    /**
     * Frame before instruction, null for unreachable instructions.
     */
    public final @Nullable ExtendedFrame frameBefore;

    /**
     * Frame after instruction, null for unreachable instructions and after the last instruction.
     */
    public final @Nullable ExtendedFrame frameAfter;

    /**
     * Tag for interruptible methods.
     */
    public static final String TAG_INTERRUPTIBLE = "INTERRUPTIBLE";

    /**
     * Tag for interrupt methods.
     */
    public static final String TAG_INTERRUPT = "INTERRUPT";

    /**
     * Tag for tail calls.
     */
    public static final String TAG_TAIL_CALL = "TAIL_CALL";

    /**
     * Tags.
     */
    public final Set<Object> tags = new HashSet<>();

    /**
     * Constructor.
     *
     * @param frameBefore
     *         Frame before instruction.
     * @param frameAfter
     *         Frame after instruction.
     */
    public MetaInfo(@Nullable ExtendedFrame frameBefore, @Nullable ExtendedFrame frameAfter) {
        this.frameBefore = frameBefore;
        this.frameAfter = frameAfter;
    }
}
