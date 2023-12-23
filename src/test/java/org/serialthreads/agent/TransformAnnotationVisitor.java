package org.serialthreads.agent;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;
import org.serialthreads.transformer.ITransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.objectweb.asm.Opcodes.ASM9;

/**
 * Visitor which checks all methods for the presence of the {@link Transform} annotation.
 */
public class TransformAnnotationVisitor extends ClassVisitor {
  private static final Logger logger = LoggerFactory.getLogger(TransformAnnotationVisitor.class);

  private static final String TRANSFORM_DESCRIPTOR = Type.getType(Transform.class).getDescriptor();
  private static final String[] NO_CLASS_PREFIXES = {};

  /**
   * Transformer class.
   */
  private Type transformer = null;

  /**
   * Prefixes of classes to transform. "org.serialthreads." will always be added.
   */
  private String[] classPrefixes = NO_CLASS_PREFIXES;

  public TransformAnnotationVisitor() {
    super(ASM9);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
    logger.info("Found annotation {}.", descriptor);
    if (!TRANSFORM_DESCRIPTOR.equals(descriptor)) {
      return null;
    }
    logger.info("Found annotation @Transform.");

    return new AnnotationVisitor(ASM9) {
      @Override
      public void visit(String name, Object value) {
        switch (name) {
          case "transformer" -> transformer = ((Type) value);
          case "classPrefixes" -> classPrefixes = (String[]) value;
          default -> throw new IllegalArgumentException("Invalid @Transform value");
        }
      }
    };
  }

  public TransformAnnotation getTransform() {
    if (transformer == null) {
      return null;
    }

    try {
      @SuppressWarnings("unchecked")
      var transformerClass = (Class<? extends ITransformer>) Class.forName(transformer.getClassName());
      return new TransformAnnotation(transformerClass, classPrefixes);
    } catch (ClassNotFoundException e) {
      logger.info("Transformer class {} not found.", this.transformer);
      throw new IllegalArgumentException("Transformer not found");
    }
  }
}
