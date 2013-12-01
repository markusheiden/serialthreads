package org.serialthreads.transformer.analyzer;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.util.*;

import static org.serialthreads.transformer.code.MethodCode.isLoad;
import static org.serialthreads.transformer.code.MethodCode.isReturn;

/**
 * Extended analyzer.
 * Supports detection of "this" and method parameters in the locals and on the stack.
 * Always uses a verifier as interpreter.
 */
public class ExtendedAnalyzer extends Analyzer<BasicValue> {
  /**
   * Backward flow of instructions.
   * To instruction index -> From instruction index.
   */
  private final Map<Integer, NavigableSet<Integer>> backflow = new HashMap<>();

  /**
   * Analyze a method to compute its frames.
   *
   * @param clazz owner of method
   * @param method method
   * @param classInfoCache class info cache
   * @return frames
   * @exception AnalyzerException In case of incorrect byte code of the original method
   */
  public static Frame[] analyze(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) throws AnalyzerException {
    Type classType = Type.getObjectType(clazz.name);
    Type superClassType = Type.getObjectType(clazz.superName);
    List<Type> interfaceTypes = new ArrayList<>(clazz.interfaces.size());
    for (String interfaceName : clazz.interfaces) {
      interfaceTypes.add(Type.getObjectType(interfaceName));
    }
    boolean isInterface = (clazz.access & ACC_INTERFACE) != 0;

    return new ExtendedAnalyzer(classInfoCache, classType, superClassType, interfaceTypes, isInterface).analyze(clazz.name, method);
  }

  /**
   * Constructs a new {@link ExtendedAnalyzer} to analyze a specific class.
   * This class will not be loaded into the JVM since it may be incorrect.
   *
   * @param classInfoCache class info cache
   * @param currentClass the class that is analyzed.
   * @param currentSuperClass the super class of the class that is analyzed.
   * @param currentClassInterfaces the interfaces implemented by the class that is analyzed.
   * @param isInterface if the class that is analyzed is an interface.
   */
  public ExtendedAnalyzer(
    IClassInfoCache classInfoCache,
    Type currentClass,
    Type currentSuperClass,
    List<Type> currentClassInterfaces,
    boolean isInterface) {
    super(new ExtendedVerifier(classInfoCache, currentClass, currentSuperClass, currentClassInterfaces, isInterface));
  }

  @Override
  public Frame<BasicValue>[] analyze(String owner, MethodNode m) throws AnalyzerException {
    Frame<BasicValue>[] result = super.analyze(owner, m);

    InsnList instructions = m.instructions;

    SortedSet<Integer> startingPoints = new TreeSet<>();
    for (int i = 0; i < instructions.size(); i++) {
      AbstractInsnNode instruction = instructions.get(i);
      if (isReturn(instruction) || instruction.getOpcode() == ATHROW) {
        startingPoints.add(i);
      }
    }

    while (!startingPoints.isEmpty()) {
      traceBack(m, startingPoints, result);
    }

    return result;
  }

  private void traceBack(MethodNode m, SortedSet<Integer> startingPoints, Frame<BasicValue>[] frames) {
    Integer index = startingPoints.last();
    startingPoints.remove(index);
    ExtendedFrame firstFrame = (ExtendedFrame) frames[index];
    if (firstFrame == null) {
      return;
    }

    InsnList instructions = m.instructions;

    while (true) {
      AbstractInsnNode instruction = instructions.get(index);
      ExtendedFrame frame = (ExtendedFrame) frames[index];

      if (isLoad(instruction)) {
        frame.neededLocals.add(((VarInsnNode) instruction).var);
      }

      NavigableSet<Integer> froms = backflow.get(index);
      if (froms == null || froms.isEmpty()) {
        // no predecessors at all -> we are finished with the current starting point
        return;
      }

      // Update needed locals of all other predecessors
      for (Integer from : froms) {
        ExtendedFrame fromFrame = (ExtendedFrame) frames[from];
        boolean modified = fromFrame.neededLocals.addAll(frame.neededLocals);
        if (modified) {
          startingPoints.add(from);
        }
      }

      index = froms.lower(index);
      if (index == null) {
        // no direct predecessor -> we are finished with the current starting point
        return;
      }
      startingPoints.remove(index);
    }
  }

  @Override
  protected void newControlFlowEdge(int from, int to) {
    NavigableSet<Integer> froms = backflow.get(to);
    if (froms == null) {
      froms = new TreeSet<>();
      backflow.put(to, froms);
    }
    froms.add(from);
  }

  @Override
  protected ExtendedFrame newFrame(int nLocals, int nStack) {
    return new ExtendedFrame(nLocals, nStack);
  }

  @Override
  protected ExtendedFrame newFrame(Frame<? extends BasicValue> src) {
    return new ExtendedFrame(src);
  }
}
