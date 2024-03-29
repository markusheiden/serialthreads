package org.serialthreads.transformer.analyzer;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.serialthreads.transformer.classcache.IClassInfoCache;

import java.util.*;

import static org.serialthreads.transformer.code.MethodCode.*;

/**
 * Extended analyzer.
 * Supports detection of "this" and method parameters in the locals and on the stack.
 * Always uses a verifier as interpreter.
 */
public class ExtendedAnalyzer extends Analyzer<BasicValue> {
  /**
   * Opcodes considered as starting points.
   * Jump instructions have to considered too, because of methods with an endless loop (-> no return).
   */
  private static final Set<Integer> STARTING_POINT_OPCODES = new TreeSet<>(Arrays.asList(
    IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN,
    ATHROW,
    GOTO,
    IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
    IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
    IF_ACMPEQ, IF_ACMPNE
  ));

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
   * @throws AnalyzerException In case of incorrect byte code of the original method
   */
  public static ExtendedFrame[] analyze(ClassNode clazz, MethodNode method, IClassInfoCache classInfoCache) throws AnalyzerException {
    return create(clazz, classInfoCache).analyze(clazz.name, method);
  }

  /**
   * Constructs a new {@link ExtendedAnalyzer} to analyze a specific class.
   * This class will not be loaded into the JVM since it may be incorrect.
   *
   * @param clazz owner of method
   * @param classInfoCache class info cache
   */
  public static ExtendedAnalyzer create(ClassNode clazz, IClassInfoCache classInfoCache) {
    var classType = Type.getObjectType(clazz.name);
    var superClassType = Type.getObjectType(clazz.superName);
    var interfaceTypes = new ArrayList<Type>(clazz.interfaces.size());
    for (var interfaceName : clazz.interfaces) {
      interfaceTypes.add(Type.getObjectType(interfaceName));
    }
    boolean isInterface = isInterface(clazz);

    return new ExtendedAnalyzer(new ExtendedVerifier(classInfoCache, classType, superClassType, interfaceTypes, isInterface));
  }

  /**
   * Constructs a new {@link ExtendedAnalyzer}.
   *
   * @param verifier Verifier
   */
  public ExtendedAnalyzer(ExtendedVerifier verifier) {
    super(verifier);
  }

  @Override
  public ExtendedFrame[] getFrames() {
    var frames = super.getFrames();
    var result = new ExtendedFrame[frames.length];
    System.arraycopy(frames, 0, result, 0, frames.length);
    return result;
  }

  @Override
  public ExtendedFrame[] analyze(String owner, MethodNode m) throws AnalyzerException {
    var frames = super.analyze(owner, m);
    var result = new ExtendedFrame[frames.length];
    System.arraycopy(frames, 0, result, 0, frames.length);

    var instructions = m.instructions;

    var startingPoints = new TreeSet<Integer>();
    for (int i = 0; i < instructions.size(); i++) {
      var instruction = instructions.get(i);
      if (STARTING_POINT_OPCODES.contains(instruction.getOpcode())) {
        startingPoints.add(i);
      }
    }

    while (!startingPoints.isEmpty()) {
      traceBack(m, startingPoints, result);
    }

    return result;
  }

  /**
   * Traces from the last starting point to one of its predecessors, until there is no more predecessor.
   * Updates the needed locals on its way.
   *
   * @param method Method
   * @param startingPoints Starting points
   * @param frames Frames
   */
  private void traceBack(MethodNode method, SortedSet<Integer> startingPoints, ExtendedFrame[] frames) {
    // Use last starting point
    var index = startingPoints.last();
    startingPoints.remove(index);
    var firstFrame = frames[index];
    if (firstFrame == null) {
      // No frame -> unreachable code -> no need to compute
      return;
    }

    var instructions = method.instructions;
    //noinspection InfiniteLoopStatement
    while (true) {
      var instruction = instructions.get(index);
      var frameBefore = frames[index];

      if (isLoad(instruction)) {
        frameBefore.neededLocals.add(((VarInsnNode) instruction).var);
      }
      else if (isStore(instruction)) {
        frameBefore.neededLocals.remove(((VarInsnNode) instruction).var);
      }

      var froms = backflow.get(index);
      if (froms == null || froms.isEmpty()) {
        // no predecessors at all -> we are finished with the current starting point
        return;
      }

      // Update needed locals of all other predecessors
      for (var from : froms) {
        var fromFrame = frames[from];
        boolean modified = fromFrame.neededLocals.addAll(frameBefore.neededLocals);
        if (modified) {
          startingPoints.add(from);
        }
      }

      // move backwards to nearest predecessor
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
    backflow
      .computeIfAbsent(to, k -> new TreeSet<>())
      .add(from);
  }

  @Override
  protected boolean newControlFlowExceptionEdge(int from, int to) {
    newControlFlowEdge(from, to);
    return true;
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
