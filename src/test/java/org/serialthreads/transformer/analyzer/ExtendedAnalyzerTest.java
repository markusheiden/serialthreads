package org.serialthreads.transformer.analyzer;

import org.junit.Test;
import org.ow2.asm.Type;
import org.ow2.asm.tree.analysis.BasicValue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Test for ExtendedAnalyzer.
 */
public class ExtendedAnalyzerTest
{
  @Test
  public void testNewFrame_ii()
  {
    ExtendedAnalyzer analyzer = new ExtendedAnalyzer(null, null, null, null, false);

    ExtendedFrame frame = analyzer.newFrame(2, 2);

    frame.setLocal(0, BasicValue.UNINITIALIZED_VALUE);
    frame.setLocal(1, BasicValue.INT_VALUE);
    assertEquals(BasicValue.UNINITIALIZED_VALUE, frame.getLocal(0));
    assertEquals(ExtendedValue.valueInLocal(Type.INT_TYPE, 1), frame.getLocal(1));
    assertEquals(2, frame.getLocals());

    frame.push(BasicValue.LONG_VALUE);
    frame.push(BasicValue.DOUBLE_VALUE);
    assertEquals(ExtendedValue.value(Type.LONG_TYPE), frame.getStack(0));
    assertEquals(ExtendedValue.value(Type.DOUBLE_TYPE), frame.getStack(1));
    assertEquals(2, frame.getStackSize());

    try
    {
      frame.push(BasicValue.INT_VALUE);
      fail("Expected max stack = 2");
    }
    catch (IndexOutOfBoundsException e)
    {
      // expected
    }
  }

  @Test
  public void testNewFrame_frame()
  {
    ExtendedAnalyzer analyzer = new ExtendedAnalyzer(null, null, null, null, false);

    ExtendedFrame src = new ExtendedFrame(2, 2);
    src.setLocal(0, BasicValue.UNINITIALIZED_VALUE);
    src.setLocal(1, BasicValue.INT_VALUE);
    src.push(BasicValue.LONG_VALUE);
    src.push(BasicValue.DOUBLE_VALUE);

    ExtendedFrame frame = analyzer.newFrame(src);

    assertEquals(BasicValue.UNINITIALIZED_VALUE, frame.getLocal(0));
    assertEquals(ExtendedValue.valueInLocal(Type.INT_TYPE, 1), frame.getLocal(1));
    assertEquals(2, frame.getLocals());

    assertEquals(ExtendedValue.value(Type.LONG_TYPE), frame.getStack(0));
    assertEquals(ExtendedValue.value(Type.DOUBLE_TYPE), frame.getStack(1));
    assertEquals(2, frame.getStackSize());

    try
    {
      frame.push(BasicValue.INT_VALUE);
      fail("Expected max stack = 2");
    }
    catch (IndexOutOfBoundsException e)
    {
      // expected
    }
  }
}
