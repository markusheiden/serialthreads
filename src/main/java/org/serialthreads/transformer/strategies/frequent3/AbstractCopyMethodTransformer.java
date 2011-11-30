package org.serialthreads.transformer.strategies.frequent3;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;
import org.serialthreads.transformer.code.MethodNodeCopier;

import static org.serialthreads.transformer.code.MethodCode.methodName;

/**
 * Method transformer for copies of abstract methods.
 */
class AbstractCopyMethodTransformer extends MethodTransformer
{
  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected AbstractCopyMethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache)
  {
    super(clazz, MethodNodeCopier.copy(method), classInfoCache);
  }

  /**
   * Transform method.
   *
   * @return Transformed method
   * @exception AnalyzerException In case of incorrect byte code of the original method
   */
  public MethodNode transform() throws AnalyzerException
  {
    // create a copy of the method with shortened arguments
    method.name = changeCopyName(method.name, method.desc);
    method.desc = changeCopyDesc(method.desc);
    clazz.methods.add(method);

    if (log.isDebugEnabled())
    {
      log.debug("      Copied abstract method " + methodName(clazz, method));
    }

    return method;
  }
}
