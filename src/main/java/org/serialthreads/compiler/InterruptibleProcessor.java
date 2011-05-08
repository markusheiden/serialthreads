package org.serialthreads.compiler;

import org.apache.log4j.Logger;
import org.serialthreads.Interruptible;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import java.util.List;
import java.util.Set;

/**
 * Checks {@link org.serialthreads.Interruptible} annotations.
 */
@SupportedAnnotationTypes("org.serialthreads.Interruptible")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class InterruptibleProcessor extends AbstractProcessor
{
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
  {
    for (TypeElement annotation : annotations)
    {
      for (Element element : roundEnv.getElementsAnnotatedWith(annotation))
      {
        // It had to be a method, because the targets of @Interruptible can only be methods,
        // but to be sure we are checking per instanceof here.
        if (element instanceof ExecutableElement)
        {
          check((ExecutableElement) element);
        }
      }
    }

    return true;
  }

  /**
   * Check if interruptible method overrides an interruptible method, if it overrides something.
   *
   * @param overrider Method to check, whether it overrides something
   */
  private void check(ExecutableElement overrider)
  {
    Types types = processingEnv.getTypeUtils();
    Elements elements = processingEnv.getElementUtils();

    TypeElement type = (TypeElement) overrider.getEnclosingElement();

    processingEnv.getMessager().printMessage(Kind.WARNING,
      type.getQualifiedName() + ":" + overrider.getSimpleName() + " is interruptible", overrider);

    List<? extends TypeMirror> superTypes = types.directSupertypes(type.asType());
    for (TypeMirror superType : superTypes)
    {
      TypeElement superElement = (TypeElement) types.asElement(superType);
      List<? extends Element> members = elements.getAllMembers(superElement);
      for (Element overridden : members)
      {
        if (overridden instanceof ExecutableElement && elements.overrides(overrider, (ExecutableElement) overridden, type))
        {
          try
          {
            processingEnv.getMessager().printMessage(Kind.WARNING,
              type.getQualifiedName() + ":" + overrider.getSimpleName() + "/" +
              superElement.getQualifiedName() + ":" +  overridden.getSimpleName() +
              " -> " + elements.overrides(overrider, (ExecutableElement) overridden, type),
              overrider);
          }
          catch (Exception e)
          {
            processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage(), overrider);
          }
        }
      }
    }
  }
}
