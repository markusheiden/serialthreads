package org.serialthreads.agent;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.serialthreads.transformer.ITransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Visitor executing byte code enhancement of a class.
 */
public class TransformingVisitor extends ClassNode {
  /**
   * Logger.
   */
  private final Logger logger = LoggerFactory.getLogger(getClass());

  protected final ClassVisitor cv;
  protected final ITransformer transformer;

  /**
   * Constructor using a given class loader.
   *
   * @param cv base class visitor
   * @param transformer byte code transformer
   */
  public TransformingVisitor(ClassVisitor cv, ITransformer transformer) {
    this.cv = logger.isDebugEnabled() ? new CheckClassAdapter(cv) : cv;
    this.transformer = transformer;
  }

  public void visitEnd() {
    super.visitEnd();

    // transform class
    transformer.transform(this);

    accept(cv);
  }
}
