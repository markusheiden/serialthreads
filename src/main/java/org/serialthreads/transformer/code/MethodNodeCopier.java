package org.serialthreads.transformer.code;

import org.objectweb.asm.Label;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Copies method nodes.
 */
public class MethodNodeCopier
{
  /**
   * Copy method without instructions.
   *
   * @param method method
   * @return copied method
   */
  @SuppressWarnings({"UnusedDeclaration"})
  public static MethodNode copyEmpty(MethodNode method)
  {
    String[] exceptions = method.exceptions.toArray(new String[method.exceptions.size()]);
    return new MethodNode(method.access, method.name, method.desc, method.signature, exceptions);
  }

  /**
   * Copy method with instructions.
   * Takes care that labels are correctly cloned.
   *
   * @param method method
   * @return copied method
   */
  public static MethodNode copy(MethodNode method)
  {
    String[] exceptions = method.exceptions.toArray(new String[method.exceptions.size()]);
    MethodNode result = new MethodNode(method.access, method.name, method.desc, method.signature, exceptions)
    {
      /**
       * Label remapping.
       * Old label -> new label.
       */
      private final Map<Label, Label> labels = new HashMap<>();

      @Override
      protected LabelNode getLabelNode(Label label)
      {
        Label newLabel = labels.get(label);
        if (newLabel == null)
        {
          newLabel = new Label();
          labels.put(label, newLabel);
        }

        return super.getLabelNode(newLabel);
      }
    };
    method.accept(result);

    return result;
  }
}
