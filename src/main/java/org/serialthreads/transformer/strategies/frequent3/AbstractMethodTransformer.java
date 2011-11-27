package org.serialthreads.transformer.strategies.frequent3;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.serialthreads.transformer.classcache.IClassInfoCache;

/**
 * Method transformer for abstract methods.
 */
class AbstractMethodTransformer extends MethodTransformer
{
  /**
   * Constructor.
   *
   * @param clazz class to transform
   * @param method method to transform
   * @param classInfoCache class cache to use
   */
  protected AbstractMethodTransformer(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache)
  {
    super(clazz, method, classInfoCache);
  }

  /**
   * Transform method.
   *
   * @return Transformed method
   * @exception AnalyzerException In case of incorrect byte code of the original method
   */
  public MethodNode transform() throws AnalyzerException
  {
    // add thread and previousFrame arguments to the original method
    method.desc = changeDesc(method.desc);

    return method;
  }
}
