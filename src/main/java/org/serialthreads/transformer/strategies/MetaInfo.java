package org.serialthreads.transformer.strategies;

import org.serialthreads.transformer.analyzer.ExtendedFrame;

import java.util.HashSet;
import java.util.Set;

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
