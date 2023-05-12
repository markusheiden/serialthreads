package org.serialthreads.transformer.strategies;

import org.objectweb.asm.tree.analysis.BasicValue;
import org.serialthreads.transformer.NotTransformableException;

import static org.objectweb.asm.tree.analysis.BasicValue.DOUBLE_VALUE;
import static org.objectweb.asm.tree.analysis.BasicValue.FLOAT_VALUE;
import static org.objectweb.asm.tree.analysis.BasicValue.INT_VALUE;
import static org.objectweb.asm.tree.analysis.BasicValue.LONG_VALUE;

/**
 * Used to calculate maxs sizes.
 */
public final class Maxs {
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
    } else if (INT_VALUE.equals(value)) {
      maxStackInts++;
    } else if (LONG_VALUE.equals(value)) {
      maxStackLongs++;
    } else if (FLOAT_VALUE.equals(value)) {
      maxStackFloats++;
    } else if (DOUBLE_VALUE.equals(value)) {
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
    } else if (INT_VALUE.equals(value)) {
      maxLocalInts++;
    } else if (LONG_VALUE.equals(value)) {
      maxLocalLongs++;
    } else if (FLOAT_VALUE.equals(value)) {
      maxLocalFloats++;
    } else if (DOUBLE_VALUE.equals(value)) {
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
