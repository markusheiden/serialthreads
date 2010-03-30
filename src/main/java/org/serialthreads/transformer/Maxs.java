package org.serialthreads.transformer;

import org.objectweb.asm.tree.analysis.BasicValue;

/**
 * Used to calculate maxs sizes.
 */
public class Maxs {
  public int maxStackObjects;
  public int maxStackInts;
  public int maxStackLongs;
  public int maxStackFloats;
  public int maxStackDoubles;
  public int maxLocalObjects;
  public int maxLocalInts;
  public int maxLocalLongs;
  public int maxLocalFloats;
  public int maxLocalDoubles;

  /**
   * Add a new stack element.
   *
   * @param value type of the stack element
   */
  public void addStack(BasicValue value) {
    if (value.isReference()) {
      maxStackObjects++;
    } else if (BasicValue.INT_VALUE.equals(value)) {
      maxStackInts++;
    } else if (BasicValue.LONG_VALUE.equals(value)) {
      maxStackLongs++;
    } else if (BasicValue.FLOAT_VALUE.equals(value)) {
      maxStackFloats++;
    } else if (BasicValue.DOUBLE_VALUE.equals(value)) {
      maxStackDoubles++;
    } else {
      throw new NotTransformableException("Unknown type " + value + " in stack detected");
    }
  }

  /**
   * Add a new local variable.
   *
   * @param value type of the local variable
   */
  public void addLocal(BasicValue value) {
    if (value.isReference()) {
      maxLocalObjects++;
    } else if (BasicValue.INT_VALUE.equals(value)) {
      maxLocalInts++;
    } else if (BasicValue.LONG_VALUE.equals(value)) {
      maxLocalLongs++;
    } else if (BasicValue.FLOAT_VALUE.equals(value)) {
      maxLocalFloats++;
    } else if (BasicValue.DOUBLE_VALUE.equals(value)) {
      maxLocalDoubles++;
    } else {
      throw new NotTransformableException("Unknown type " + value + " in local variable detected");
    }
  }

  /**
   * Maximum of two maxs.
   *
   * @param add maxs to consolidate from
   */
  public void consolidate(Maxs add) {
    maxStackObjects = Math.max(maxStackObjects, add.maxStackObjects);
    maxStackInts = Math.max(maxStackInts, add.maxStackInts);
    maxStackLongs = Math.max(maxStackLongs, add.maxStackLongs);
    maxStackFloats = Math.max(maxStackFloats, add.maxStackFloats);
    maxStackDoubles = Math.max(maxStackDoubles, add.maxStackDoubles);
    maxLocalObjects = Math.max(maxLocalObjects, add.maxLocalObjects);
    maxLocalInts = Math.max(maxLocalInts, add.maxLocalInts);
    maxLocalLongs = Math.max(maxLocalLongs, add.maxLocalLongs);
    maxLocalFloats = Math.max(maxLocalFloats, add.maxLocalFloats);
    maxLocalDoubles = Math.max(maxLocalDoubles, add.maxLocalDoubles);
  }
}
