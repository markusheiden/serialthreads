package org.serialthreads.context;

/**
 * Support for dynamically resizing arrays.
 */
public class ArrayResizer
{
  public static final Object[] resize(Object[] old, Object object)
  {
    final int oldLength = old.length;
    Object[] result = new Object[oldLength << 1];
    System.arraycopy(old, 0, result, 0, oldLength);
    result[oldLength] = object;
    return result;
  }

  public static final boolean[] resize(boolean[] old, boolean value)
  {
    final int oldLength = old.length;
    boolean[] result = new boolean[oldLength << 1];
    System.arraycopy(old, 0, result, 0, oldLength);
    result[oldLength] = value;
    return result;
  }

  public static final int[] resize(int[] old, int value)
  {
    final int oldLength = old.length;
    int[] result = new int[oldLength << 1];
    System.arraycopy(old, 0, result, 0, oldLength);
    result[oldLength] = value;
    return result;
  }

  public static final long[] resize(long[] old, long value)
  {
    final int oldLength = old.length;
    long[] result = new long[oldLength << 1];
    System.arraycopy(old, 0, result, 0, oldLength);
    result[oldLength] = value;
    return result;
  }

  public static final float[] resize(float[] old, float value)
  {
    final int oldLength = old.length;
    float[] result = new float[oldLength << 1];
    System.arraycopy(old, 0, result, 0, oldLength);
    result[oldLength] = value;
    return result;
  }

  public static final double[] resize(double[] old, double value)
  {
    final int oldLength = old.length;
    double[] result = new double[oldLength << 1];
    System.arraycopy(old, 0, result, 0, oldLength);
    result[oldLength] = value;
    return result;
  }
}
