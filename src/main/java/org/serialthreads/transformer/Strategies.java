package org.serialthreads.transformer;

import org.serialthreads.transformer.strategies.frequent.FrequentInterruptsTransformer;
import org.serialthreads.transformer.strategies.frequent2.FrequentInterruptsTransformer2;
import org.serialthreads.transformer.strategies.frequent3.FrequentInterruptsTransformer4;
import org.serialthreads.transformer.strategies.frequent4.FrequentInterruptsTransformer3;

/**
 * Available transformation strategies.
 */
public class Strategies {
  /**
   * Strategy for frequent interrupts.
   */
  public static final IStrategy FREQUENT = FrequentInterruptsTransformer::new;

  /**
   * Strategy for frequent interrupts which bloats the code more than FREQUENT but is slightly faster.
   */
  public static final IStrategy FREQUENT2 = FrequentInterruptsTransformer2::new;

  /**
   * Strategy for frequent interrupts which bloats the code more than FREQUENT2 but is faster.
   */
  public static final IStrategy FREQUENT3 = FrequentInterruptsTransformer3::new;

  /**
   * Strategy for frequent interrupts which bloats the code less than FREQUENT3.
   */
  public static final IStrategy FREQUENT4 = FrequentInterruptsTransformer4::new;

  /**
   * Default strategy.
   */
  public static final IStrategy DEFAULT = FREQUENT4;
}
