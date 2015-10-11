package org.serialthreads.transformer.strategies;

import java.util.HashSet;
import java.util.Set;

import org.serialthreads.transformer.analyzer.ExtendedFrame;

/**
 * Meta information for an instruction.
 */
public class MetaInfo {
  /**
   * Frame before instruction.
   */
  public final ExtendedFrame frameBefore;

  /**
   * Frame after instruction.
   */
  public final ExtendedFrame frameAfter;

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
   * @param frameBefore Frame before instruction
   * @param frameAfter Frame after instruction
   */
  public MetaInfo(ExtendedFrame frameBefore, ExtendedFrame frameAfter) {
    this.frameBefore = frameBefore;
    this.frameAfter = frameAfter;
  }
}
