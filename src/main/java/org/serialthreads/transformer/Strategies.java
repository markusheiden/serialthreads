package org.serialthreads.transformer;

import org.serialthreads.transformer.classcache.IClassInfoCache;

/**
 * Available tranformation strategies.
 */
public class Strategies
{
  /**
   * Strategy for frequent interrupts.
   */
  public static IStrategy FREQUENT = new IStrategy()
  {
    @Override
    public ITransformer getTransformer(IClassInfoCache classInfoCache)
    {
      return new FrequentInterruptsTransformer(classInfoCache);
    }

    @Override
    public String toString()
    {
      return "Transformation strategy " + FrequentInterruptsTransformer.STRATEGY;
    }
  };

  /**
   * Strategy for frequent interrupts which bloats the code more than FREQUENT but is slightly faster.
   */
  public static IStrategy FREQUENT2 = new IStrategy()
  {
    @Override
    public ITransformer getTransformer(IClassInfoCache classInfoCache)
    {
      return new FrequentInterruptsTransformer2(classInfoCache);
    }

    @Override
    public String toString()
    {
      return "Transformation strategy " + FrequentInterruptsTransformer2.STRATEGY;
    }
  };

  /**
   * Strategy for frequent interrupts which bloats the code more than FREQUENT but is slightly faster.
   */
  public static IStrategy FREQUENT3 = new IStrategy()
  {
    @Override
    public ITransformer getTransformer(IClassInfoCache classInfoCache)
    {
      return new FrequentInterruptsTransformer3(classInfoCache);
    }

    @Override
    public String toString()
    {
      return "Transformation strategy " + FrequentInterruptsTransformer3.STRATEGY;
    }
  };

  /**
   * Strategy for infrequent interrupts.
   */
  public static IStrategy DEFAULT = FREQUENT3;
}
