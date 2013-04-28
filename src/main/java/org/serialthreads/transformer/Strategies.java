package org.serialthreads.transformer;

import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.strategies.frequent.FrequentInterruptsTransformer;
import org.serialthreads.transformer.strategies.frequent2.FrequentInterruptsTransformer2;
import org.serialthreads.transformer.strategies.frequent3.FrequentInterruptsTransformer3;
import org.serialthreads.transformer.strategies.singleframe.SingleFrameExecutionTransformer;

/**
 * Available transformation strategies.
 */
public class Strategies {
  /**
   * Strategy for frequent interrupts.
   */
  public static final IStrategy FREQUENT = new IStrategy() {
    @Override
    public ITransformer getTransformer(IClassInfoCache classInfoCache) {
      return new FrequentInterruptsTransformer(classInfoCache);
    }

    @Override
    public String toString() {
      return "Transformation strategy " + FrequentInterruptsTransformer.STRATEGY;
    }
  };

  /**
   * Strategy for frequent interrupts which bloats the code more than FREQUENT but is slightly faster.
   */
  public static final IStrategy FREQUENT2 = new IStrategy() {
    @Override
    public ITransformer getTransformer(IClassInfoCache classInfoCache) {
      return new FrequentInterruptsTransformer2(classInfoCache);
    }

    @Override
    public String toString() {
      return "Transformation strategy " + FrequentInterruptsTransformer2.STRATEGY;
    }
  };

  /**
   * Strategy for frequent interrupts which bloats the code more than FREQUENT but is slightly faster.
   */
  public static final IStrategy FREQUENT3 = new IStrategy() {
    @Override
    public ITransformer getTransformer(IClassInfoCache classInfoCache) {
      return new FrequentInterruptsTransformer3(classInfoCache);
    }

    @Override
    public String toString() {
      return "Transformation strategy " + FrequentInterruptsTransformer3.STRATEGY;
    }
  };

  /**
   * Strategy for frequent interrupts which bloats the code more than FREQUENT but is slightly faster.
   */
  public static final IStrategy SINGLE_FRAME_EXECUTION = new IStrategy() {
    @Override
    public ITransformer getTransformer(IClassInfoCache classInfoCache) {
      return new SingleFrameExecutionTransformer(classInfoCache);
    }

    @Override
    public String toString() {
      return "Transformation strategy " + SingleFrameExecutionTransformer.STRATEGY;
    }
  };

  /**
   * Strategy for infrequent interrupts.
   */
  public static final IStrategy DEFAULT = FREQUENT3;
}
