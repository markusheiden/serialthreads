package org.serialthreads.agent;

import java.util.ArrayList;
import java.util.List;

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
@SuppressWarnings({ "AnonymousInnerClassWithTooManyMethods", "ReturnOfInnerClass" })
class TransformAnnotationVisitor extends ClassVisitor {
  private static final Logger logger = LoggerFactory.getLogger(TransformAnnotationVisitor.class);

  private static final String TRANSFORM_DESCRIPTOR = Type.getType(Transform.class).getDescriptor();

  /**
   * {@link Transform#transformer() Transformer class}.
   */
  private Type transformer;

  /**
   * {@link Transform#classPrefixes() Prefixes of classes to transform}.
   */
  private final List<String> classPrefixes = new ArrayList<>();

  TransformAnnotationVisitor() {
    super(ASM9);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
    if (!TRANSFORM_DESCRIPTOR.equals(descriptor)) {
      return null;
    }
    logger.debug("Found annotation @Transform.");

    return new AnnotationVisitor(ASM9) {
      @Override
      public void visit(String name, Object value) {
        if ("transformer".equals(name)) {
          if (value == null) {
            throw new IllegalArgumentException("Transformer class not configured in @Transform.");
          }
          transformer = ((Type) value);
        } else {
          throw new IllegalArgumentException("Invalid @Transform value");
        }
      }

      @Override
      public AnnotationVisitor visitArray(String name) {
        if ("classPrefixes".equals(name)) {
          return new AnnotationVisitor(ASM9) {
            @Override
            public void visit(String name, Object value) {
              if (value == null) {
                throw new IllegalArgumentException("Invalid null class prefix");
              }
              classPrefixes.add((String) value);
            }
          };
        } else {
          throw new IllegalArgumentException("Invalid @Transform value");
        }
      }
    };
  }

  /**
   * The {@link Transform} annotation information.
   *
   * @return The (valid) information, or {@code} null if there was no {@link Transform} annotation.
   */
  public TransformAnnotation getTransform() {
    if (transformer == null) {
      return null;
    }

    try {
      @SuppressWarnings("unchecked")
      var transformerClass = (Class<? extends ITransformer>) Class.forName(transformer.getClassName());
      return new TransformAnnotation(transformerClass, classPrefixes.toArray(String[]::new));
    } catch (ClassNotFoundException e) {
      logger.info("Transformer class {} not found.", this.transformer);
      throw new IllegalArgumentException("Transformer not found");
    }
  }
}
