package org.serialthreads.transformer.strategies;

import org.objectweb.asm.tree.analysis.Frame;

import java.util.HashSet;
import java.util.Set;

/**
 * Meta information for an instruction.
 */
public class MetaInfo {
  /**
   * Frame before instruction.
   */
  public final Frame frameBefore;

  /**
   * Frame after instruction.
   */
  public final Frame frameAfter;

  /**
   * Tags.
   */
  public final Set<String> tags = new HashSet<>();

  /**
   * Constructor.
   *
   * @param frameBefore Frame before instruction
   * @param frameAfter Frame after instruction
   */
  public MetaInfo(Frame frameBefore, Frame frameAfter) {
    this.frameBefore = frameBefore;
    this.frameAfter = frameAfter;
  }
}
