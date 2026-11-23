package org.serialthreads.context;

/**
 * Interface for frames.
 * Used for contexts which capture Java stack frames.
 */
public interface IFrame {
  /**
   * Push stack object.
   *
   * @param object Object.
   */
  public void pushStackObject(Object object);

  /**
   * Pop stack object.
   */
  public Object popStackObject();

  /**
   * Push stack int value.
   *
   * @param value Value.
   */
  public void pushStackInt(int value);

  /**
   * Pop stack int value.
   */
  public int popStackInt();

  /**
   * Push stack long value.
   *
   * @param value Value.
   */
  public void pushStackLong(long value);

  /**
   * Pop stack long value.
   */
  public long popStackLong();

  /**
   * Push stack float value.
   *
   * @param value Value.
   */
  public void pushStackFloat(float value);

  /**
   * Pop stack float value.
   */
  public float popStackFloat();

  /**
   * Push stack double value.
   *
   * @param value Value.
   */
  public void pushStackDouble(double value);

  /**
   * Pop stack double value.
   */
  public double popStackDouble();

  /**
   * Push local object.
   *
   * @param object Object.
   */
  public void pushLocalObject(Object object);

  /**
   * Pop local object.
   */
  public Object popLocalObject();

  /**
   * Push local int value.
   *
   * @param value Value.
   */
  public void pushLocalInt(int value);

  /**
   * Pop local int value.
   */
  public int popLocalInt();

  /**
   * Push local long value.
   *
   * @param value Value.
   */
  public void pushLocalLong(long value);

  /**
   * Pop local long value.
   */
  public long popLocalLong();

  /**
   * Push local float value.
   *
   * @param value Value.
   */
  public void pushLocalFloat(float value);

  /**
   * Pop local float value.
   */
  public float popLocalFloat();

  /**
   * Push local double value.
   *
   * @param value Value.
   */
  public void pushLocalDouble(double value);

  /**
   * Pop local double value.
   */
  public double popLocalDouble();
}
