package org.serialthreads.compiler;

import org.apache.log4j.Logger;
import org.serialthreads.Interruptible;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
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
        processingEnv.getMessager().printMessage(Kind.WARNING, element.getSimpleName() + " is interruptible", element);
      }
    }

    return true;
  }
}
