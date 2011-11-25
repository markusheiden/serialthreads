package org.serialthreads.transfomer.context.infrequent;

import org.apache.log4j.Logger;
import org.serialthreads.context.IFrame;
import org.serialthreads.context.SerialThread;

import static org.serialthreads.context.ArrayResizer.resize;

/**
 * Context.
 * Used to store the content of all frames of a thread.
 */
public class DynamicContext extends SerialThread implements IFrame
{
  private final Logger logger = Logger.getLogger(getClass());

  protected static final int maxMethods = 16;

  protected static final int maxStackObjects = 16;
  protected static final int maxStackInts = 8;
  protected static final int maxStackLongs = 4;
  protected static final int maxStackFloats = 4;
  protected static final int maxStackDoubles = 4;
  protected static final int maxLocalObjects = 16;
  protected static final int maxLocalInts = 8;
  protected static final int maxLocalLongs = 4;
  protected static final int maxLocalFloats = 4;
  protected static final int maxLocalDoubles = 4;

  private int ownerPtr;
  private Object[] owners;
  private int methodPtr;
  private int[] methods;

  private int stackObjectPtr;
  private Object[] stackObjects;
  private int stackIntPtr;
  private int[] stackInts;
  private int stackLongPtr;
  private long[] stackLongs;
  private int stackFloatPtr;
  private float[] stackFloats;
  private int stackDoublePtr;
  private double[] stackDoubles;

  private int localObjectPtr;
  private Object[] localObjects;
  private int localIntPtr;
  private int[] localInts;
  private int localLongPtr;
  private long[] localLongs;
  private int localFloatPtr;
  private float[] localFloats;
  private int localDoublePtr;
  private double[] localDoubles;

  /**
   * Constructor.
   *
   * @param name name of the thread
   */
  public DynamicContext(String name)
  {
    super(name);

    ownerPtr = 0;
    owners = new Object[maxMethods];
    methodPtr = 0;
    methods = new int[maxMethods];

    stackObjectPtr = 0;
    stackObjects = new Object[maxStackObjects];
    stackIntPtr = 0;
    stackInts = new int[maxStackInts];
    stackLongPtr = 0;
    stackLongs = new long[maxStackLongs];
    stackFloatPtr = 0;
    stackFloats = new float[maxStackFloats];
    stackDoublePtr = 0;
    stackDoubles = new double[maxStackDoubles];

    localObjectPtr = 0;
    localObjects = new Object[maxLocalObjects];
    localIntPtr = 0;
    localInts = new int[maxLocalInts];
    localLongPtr = 0;
    localLongs = new long[maxLocalLongs];
    localFloatPtr = 0;
    localFloats = new float[maxLocalFloats];
    localDoublePtr = 0;
    localDoubles = new double[maxLocalDoubles];
  }

  public final void pushMethod(Object owner)
  {
    try
    {
      owners[ownerPtr++] = owner;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      owners = resize(owners, owner);
    }
  }

  public final void pushMethod(int method)
  {
    try
    {
      methods[methodPtr++] = method;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      methods = resize(methods, method);
    }
  }

  public final void pushMethod(Object owner, int method)
  {
    try
    {
      owners[ownerPtr++] = owner;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      owners = resize(owners, owner);
    }
    try
    {
      methods[methodPtr++] = method;
    }
    catch (ArrayIndexOutOfBoundsException e)
    {
      methods = resize(methods, method);
    }
  }

  public final Object popOwner()
  {
    return owners[--ownerPtr];
  }

  public final int popMethod()
  {
    return methods[--methodPtr];
  }

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
    final Object result = stackObjects[ptr];
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
  // debug
  //

  /**
   * Check if the context is completly empty.
   */
  public boolean isEmpty()
  {
    return
      ownerPtr == 0 &&
        methodPtr == 0 &&

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
    logger.debug("Owners: " + ownerPtr + " / " + owners.length);
    logger.debug("Methods: " + methodPtr + " / " + methods.length);
    logger.debug("Stack objects: " + stackObjectPtr + " / " + stackObjects.length);
    logger.debug("Stack ints: " + stackIntPtr + " / " + stackInts.length);
    logger.debug("Stack longs: " + stackLongPtr + " / " + stackLongs.length);
    logger.debug("Stack floats: " + stackFloatPtr + " / " + stackFloats.length);
    logger.debug("Stack doubles: " + stackDoublePtr + " / " + stackDoubles.length);
    logger.debug("Local objects: " + localObjectPtr + " / " + localObjects.length);
    logger.debug("Local ints: " + localIntPtr + " / " + localInts.length);
    logger.debug("Local longs: " + localLongPtr + " / " + localLongs.length);
    logger.debug("Local floats: " + localFloatPtr + " / " + localFloats.length);
    logger.debug("Local doubles: " + localDoublePtr + " / " + localDoubles.length);
  }
}
