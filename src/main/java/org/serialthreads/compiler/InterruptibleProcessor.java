package org.serialthreads.compiler;

import org.serialthreads.Interruptible;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import java.util.List;
import java.util.Set;

/**
 * Checks {@link org.serialthreads.Interruptible} annotations.
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class InterruptibleProcessor extends AbstractProcessor
{
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
  {
    for (Element root : roundEnv.getRootElements())
    {
      if (root instanceof TypeElement)
      {
        check((TypeElement) root);
      }
      else
      {
        processingEnv.getMessager().printMessage(Kind.WARNING, root.getSimpleName()  + " is not a type", root);
      }
    }

    return false;
  }

  /**
   * Check a type that its interruptible methods override interruptible methods, if they overrides anything.
   *
   * @param type Type to check
   */
  private void check(TypeElement type)
  {
    for (Element enclosed : type.getEnclosedElements())
    {
      if (enclosed instanceof TypeElement)
      {
        check((TypeElement) enclosed);
      }
      else if (enclosed instanceof ExecutableElement)
      {
        check(type, (ExecutableElement) enclosed);
      }
      else if (!(enclosed instanceof VariableElement))
      {
        processingEnv.getMessager().printMessage(Kind.WARNING, enclosed.getSimpleName()  + " is neither a method nor a field", enclosed);
      }
    }
  }

  /**
   * Check that an interruptible method overrides another interruptible method, if it overrides anything.
   *
   * @param type Class containing the method
   * @param overrider Method to check, whether it overrides something
   */
  private void check(TypeElement type, ExecutableElement overrider)
  {
    Types types = processingEnv.getTypeUtils();
    Elements elements = processingEnv.getElementUtils();

    boolean interruptible = overrider.getAnnotation(Interruptible.class) != null;
    for (TypeMirror superType : types.directSupertypes(type.asType()))
    {
      TypeElement overriddenType = (TypeElement) types.asElement(superType);
      for (Element overridden : elements.getAllMembers(overriddenType))
      {
        if (overridden instanceof ExecutableElement)
        {
          if (elements.overrides(overrider, (ExecutableElement) overridden, type))
          {
            boolean overriddenInterruptible = overridden.getAnnotation(Interruptible.class) != null;

            if (interruptible != overriddenInterruptible)
            {
              processingEnv.getMessager().printMessage(Kind.WARNING,
                type.getQualifiedName() + "#" + overrider.getSimpleName() + " overrides " +
                overriddenType.getQualifiedName() + "#" +  overridden.getSimpleName() + ", " +
                "but their interruptible status does not match",
                overrider);
            }
            break;
          }
        }
      }
    }
  }
}
