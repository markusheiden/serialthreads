package org.serialthreads.compiler;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * Checks {@link org.serialthreads.Interruptible} annotation.
 */
@SupportedAnnotationTypes("org.serialthreads.Interruptible")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class InterruptibleProcessor extends AbstractProcessor
{
  @Override
  public boolean process(Set<? extends TypeElement> typeElements, RoundEnvironment roundEnvironment)
  {
    if (typeElements != null)
    {
      for (TypeElement typeElement : typeElements)
      {
        System.out.println(typeElement.getClass().getName());
      }
    }

    return true;
  }
}
