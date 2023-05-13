package org.serialthreads.agent;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.objectweb.asm.Opcodes.ASM9;

/**
 * Visitor which checks all methods for the presence of the @Interruptible annotation.
 */
public class TransformAnnotationVisitor extends ClassVisitor {
  private static final Logger logger = LoggerFactory.getLogger(TransformAnnotationVisitor.class);

  private Transform transform;

  public TransformAnnotationVisitor() {
    super(ASM9);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
    logger.info("Found annotation {}.", descriptor);
    return super.visitAnnotation(descriptor, visible);
  }

  public Transform getTransform() {
    return transform;
  }
}
