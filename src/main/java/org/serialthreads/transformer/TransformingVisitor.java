package org.serialthreads.transformer;

import org.apache.log4j.Logger;
import org.ow2.asm.ClassVisitor;
import org.ow2.asm.tree.ClassNode;
import org.ow2.asm.util.CheckClassAdapter;

/**
 * Visitor executing byte code enhancement of a class.
 */
public class TransformingVisitor extends ClassNode
{
  private final Logger log = Logger.getLogger(getClass());

  protected final ClassVisitor cv;
  protected final ITransformer transformer;

  /**
   * Constructor using a given class loader.
   *
   * @param cv base class visitor
   * @param transformer byte code transformer
   */
  public TransformingVisitor(ClassVisitor cv, ITransformer transformer)
  {
    this.cv = log.isDebugEnabled()? new CheckClassAdapter(cv) : cv;
    this.transformer = transformer;
  }

  public void visitEnd()
  {
    super.visitEnd();

    // transform class
    transformer.transform(this);

    accept(cv);
  }
}
