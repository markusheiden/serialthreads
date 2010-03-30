package org.serialthreads.context;

import org.apache.log4j.Logger;

import java.util.Arrays;

/**
 * Used to store the content of a stack frame.
 */
public class StackFrame
{
  private final Logger logger = Logger.getLogger(getClass());

  public static int DEFAULT_FRAME_SIZE = 64;
  public static final int FAST_FRAME_SIZE = 8;

  // double linked list of stack frames
  public final StackFrame previous;
  public StackFrame next;

  public IFrameExecutor executor;

  // Method owner and index of restore code
  public Object owner;
  public int method;

  // Return value of the method
  public Object objectReturn;
  public int intReturn;
  public long longReturn;
  public float floatReturn;
  public double doubleReturn;

  // size of frame
  private int size;

  // stack
  private int stackObjectPtr;
  public Object[] stackObjects;
  private int stackIntPtr;
  public int[] stackInts;
  private int stackLongPtr;
  public long[] stackLongs;
  private int stackFloatPtr;
  public float[] stackFloats;
  private int stackDoublePtr;
  public double[] stackDoubles;

  // fast stack
  public Object stackObject0;
  public Object stackObject1;
  public Object stackObject2;
  public Object stackObject3;
  public Object stackObject4;
  public Object stackObject5;
  public Object stackObject6;
  public Object stackObject7;
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

  // locals
  private int localObjectPtr;
  public Object[] localObjects;
  private int localIntPtr;
  public int[] localInts;
  private int localLongPtr;
  public long[] localLongs;
  private int localFloatPtr;
  public float[] localFloats;
  private int localDoublePtr;
  public double[] localDoubles;

  // fast locals
  public Object localObject0;
  public Object localObject1;
  public Object localObject2;
  public Object localObject3;
  public Object localObject4;
  public Object localObject5;
  public Object localObject6;
  public Object localObject7;
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
   * @param previous previous stack frame for a linked list
   */
  public StackFrame(StackFrame previous, int size)
  {
    if (previous != null)
    {
      previous.next = this;
    }

    this.previous = previous;
    this.next = null;

    executor = null;

    owner = null;
    // has to be -1 for dummy startup restore!
    method = -1;

    this.size = size;

    stackObjectPtr = 0;
    stackObjects = new Object[size];
    stackIntPtr = 0;
    stackInts = new int[size];
    stackLongPtr = 0;
    stackLongs = new long[size];
    stackFloatPtr = 0;
    stackFloats = new float[size];
    stackDoublePtr = 0;
    stackDoubles = new double[size];

    localObjectPtr = 0;
    localObjects = new Object[size];
    localIntPtr = 0;
    localInts = new int[size];
    localLongPtr = 0;
    localLongs = new long[size];
    localFloatPtr = 0;
    localFloats = new float[size];
    localDoublePtr = 0;
    localDoubles = new double[size];
  }

  /**
   * Get owner.
   * Mainly for debugging purposes to be able to do a "not null" check.
   */
  public final Object getOwner()
  {
    assert owner != null : "Check: owner != null";
    return owner;
  }

  /**
   * Get method index.
   * Mainly for debugging purposes to be able to do a ">= 0" check.
   */
  public final int getMethod()
  {
    assert method >= 0 : "Check: method >= 0";
    return method;
  }

  /**
   * Reset frame to empty state.
   */
  public final void reset()
  {
    executor = null;

    owner = null;
    method = -1;

    stackObjectPtr = 0;
    // TODO 2010-03-18 mh: reset fast stack too
    Arrays.fill(stackObjects, null);
    stackIntPtr = 0;
    stackLongPtr = 0;
    stackFloatPtr = 0;
    stackDoublePtr = 0;

    localObjectPtr = 0;
    // TODO 2010-03-18 mh: reset fast locals too
    Arrays.fill(localObjects, null);
    localIntPtr = 0;
    localLongPtr = 0;
    localFloatPtr = 0;
    localDoublePtr = 0;
  }

  //
  // standard interface for capture / restore
  //

  public final void pushStackObject(Object object)
  {
    try
    {
      stackObjects[stackObjectPtr++] = object;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      stackObjects = resize(stackObjects, object);
    }
  }

  public final Object popStackObject()
  {
    final int ptr = --stackObjectPtr;
    Object result = stackObjects[ptr];
    // TODO 2009-10-11 mh: remove deletion of reference for performance reasons?
    stackObjects[ptr] = null;
    return result;
  }

  public final void pushStackInt(int value)
  {
    try
    {
      stackInts[stackIntPtr++] = value;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      stackInts = resize(stackInts, value);
    }
  }

  public final int popStackInt()
  {
    return stackInts[--stackIntPtr];
  }

  public final void pushStackLong(long value)
  {
    try
    {
      stackLongs[stackLongPtr++] = value;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      stackLongs = resize(stackLongs, value);
    }
  }

  public final long popStackLong()
  {
    return stackLongs[--stackLongPtr];
  }

  public final void pushStackFloat(float value)
  {
    try
    {
      stackFloats[stackFloatPtr++] = value;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      stackFloats = resize(stackFloats, value);
    }
  }

  public final float popStackFloat()
  {
    return stackFloats[--stackFloatPtr];
  }

  public final void pushStackDouble(double value)
  {
    try
    {
      stackDoubles[stackDoublePtr++] = value;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      stackDoubles = resize(stackDoubles, value);
    }
  }

  public final double popStackDouble()
  {
    return stackDoubles[--stackDoublePtr];
  }

  public final void pushLocalObject(Object object)
  {
    try
    {
      localObjects[localObjectPtr++] = object;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      localObjects = resize(localObjects, object);
    }
  }

  public final Object popLocalObject()
  {
    final int ptr = --localObjectPtr;
    final Object result = localObjects[ptr];
    // TODO 2009-10-11 mh: remove deletion of reference for performance reasons?
    localObjects[ptr] = null;
    return result;
  }

  public final void pushLocalInt(int value)
  {
    try
    {
      localInts[localIntPtr++] = value;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      localInts = resize(localInts, value);
    }
  }

  public final int popLocalInt()
  {
    return localInts[--localIntPtr];
  }

  public final void pushLocalLong(long value)
  {
    try
    {
      localLongs[localLongPtr++] = value;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      localLongs = resize(localLongs, value);
    }
  }

  public final long popLocalLong()
  {
    return localLongs[--localLongPtr];
  }

  public final void pushLocalFloat(float value)
  {
    try
    {
      localFloats[localFloatPtr++] = value;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      localFloats = resize(localFloats, value);
    }
  }

  public final float popLocalFloat()
  {
    return localFloats[--localFloatPtr];
  }

  public final void pushLocalDouble(double value)
  {
    try
    {
      localDoubles[localDoublePtr++] = value;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      localDoubles = resize(localDoubles, value);
    }
  }

  public final double popLocalDouble()
  {
    return localDoubles[--localDoublePtr];
  }

  //
  // resize support
  //

  protected final Object[] resize(Object[] old, Object object)
  {
    final int oldLength = old.length;
    Object[] result = new Object[oldLength << 1];
    System.arraycopy(old, 0, result, 0, oldLength);
    result[oldLength] = object;
    return result;
  }

  protected final int[] resize(int[] old, int value)
  {
    final int oldLength = old.length;
    int[] result = new int[oldLength << 1];
    System.arraycopy(old, 0, result, 0, oldLength);
    result[oldLength] = value;
    return result;
  }

  protected final long[] resize(long[] old, long value)
  {
    final int oldLength = old.length;
    long[] result = new long[oldLength << 1];
    System.arraycopy(old, 0, result, 0, oldLength);
    result[oldLength] = value;
    return result;
  }

  protected final float[] resize(float[] old, float value)
  {
    final int oldLength = old.length;
    float[] result = new float[oldLength << 1];
    System.arraycopy(old, 0, result, 0, oldLength);
    result[oldLength] = value;
    return result;
  }

  protected final double[] resize(double[] old, double value)
  {
    final int oldLength = old.length;
    double[] result = new double[oldLength << 1];
    System.arraycopy(old, 0, result, 0, oldLength);
    result[oldLength] = value;
    return result;
  }

  //
  // optimized interface for capture / restore
  //

  public final void resize(int max)
  {
    if (max < size)
    {
      return;
    }

    while (size <= max)
    {
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

  //
  // resize support
  //

  protected final Object[] resize(Object[] old)
  {
    Object[] result = new Object[size];
    System.arraycopy(old, 0, result, 0, old.length);
    return result;
  }

  protected final int[] resize(int[] old)
  {
    int[] result = new int[size];
    System.arraycopy(old, 0, result, 0, old.length);
    return result;
  }

  protected final long[] resize(long[] old)
  {
    long[] result = new long[size];
    System.arraycopy(old, 0, result, 0, old.length);
    return result;
  }

  protected final float[] resize(float[] old)
  {
    float[] result = new float[size];
    System.arraycopy(old, 0, result, 0, old.length);
    return result;
  }

  protected final double[] resize(double[] old)
  {
    double[] result = new double[size];
    System.arraycopy(old, 0, result, 0, old.length);
    return result;
  }

  //
  // debug
  //

  /**
   * Check if the frame is completly empty.
   */
  public boolean isEmpty()
  {
    return
      stackObjectPtr == 0 &&
        stackIntPtr == 0 &&
        stackLongPtr == 0 &&
        stackFloatPtr == 0 &&
        stackDoublePtr == 0 &&

        localObjectPtr == 0 &&
        localIntPtr == 0 &&
        localLongPtr == 0 &&
        localFloatPtr == 0 &&
        localDoublePtr == 0;
  }

  public void logSizes()
  {
    logger.debug("Stack objects: " + stackObjectPtr + " / " + stackObjects.length);
    logger.debug("Stack ints:    " + stackIntPtr + " / " + stackInts.length);
    logger.debug("Stack longs:   " + stackLongPtr + " / " + stackLongs.length);
    logger.debug("Stack floats:  " + stackFloatPtr + " / " + stackFloats.length);
    logger.debug("Stack doubles: " + stackDoublePtr + " / " + stackDoubles.length);
    logger.debug("Local objects: " + localObjectPtr + " / " + localObjects.length);
    logger.debug("Local ints:    " + localIntPtr + " / " + localInts.length);
    logger.debug("Local longs:   " + localLongPtr + " / " + localLongs.length);
    logger.debug("Local floats:  " + localFloatPtr + " / " + localFloats.length);
    logger.debug("Local doubles: " + localDoublePtr + " / " + localDoubles.length);
  }
}
