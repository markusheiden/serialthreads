package org.serialthreads.transfomer.context.infrequent;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Test for Context.
 */
public class DynamicContextTest
{
  @Test
  public void testPushPop()
  {
    DynamicContext context = new DynamicContext("test");
    for (int i = 0; i <= 10; i++)
    {
      testPushPop(context);
    }
  }

  protected void testPushPop(DynamicContext context)
  {
    // force dynamic increase of size
    for (int i = 1; i <= DynamicContext.maxLocalInts + 2; i++)
    {
      context.pushLocalInt(i);
    }

    // test increase result
    for (int i = DynamicContext.maxLocalInts + 2; i > 0; i--)
    {
      assertEquals(i, context.popLocalInt());
    }
  }
}
