package org.serialthreads.transformer;

import org.junit.Test;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Test to reproduce lambda creation via {@link LambdaMetafactory}.
 */
public class LambdaTest {
  @Test
  public void test() throws Throwable {
    CallSite callSite = LambdaMetafactory.metafactory(
      MethodHandles.lookup(),
      "execute",
      MethodType.methodType(Opcode.class, LambdaTest.class),
      MethodType.methodType(void.class),
      MethodHandles.lookup().findVirtual(LambdaTest.class, "opcode00", MethodType.methodType(void.class)),
      MethodType.methodType(void.class));

    Object o = callSite.getTarget().invoke(new LambdaTest());

    System.out.println(o.getClass());
    System.out.println(o instanceof Opcode);
    ((Opcode) o).execute();
  }

  private void opcode00() {
    System.out.println("Opcode");
  }

  private static interface Opcode {
    public void execute();
  }

  /*
  Problem:
  java.lang.ClassCastException: de.heiden.jem.models.c64.components.cpu.CPU6510 cannot be cast to de.heiden.jem.models.c64.components.cpu.CPU6510$Opcode
	at de.heiden.jem.models.c64.components.cpu.CPU6510.execute$$__V$$(CPU6510.java)
	at de.heiden.jem.models.c64.components.cpu.CPU6510.run(CPU6510.java)

  // access flags 0x4
  protected execute$$__V$$(Lorg/serialthreads/context/Stack;Lorg/serialthreads/context/StackFrame;)V
  @Lorg/serialthreads/Interruptible;()
    ALOAD 2
    GETFIELD org/serialthreads/context/StackFrame.next : Lorg/serialthreads/context/StackFrame;
    ASTORE 3
    ALOAD 3
    GETFIELD org/serialthreads/context/StackFrame.method : I
    TABLESWITCH
      0: L0
      1: L1
      default: L2
   L2
    NEW java/lang/IllegalThreadStateException
    DUP
    LDC "Invalid method pointer"
    INVOKESPECIAL java/lang/IllegalThreadStateException.<init> (Ljava/lang/String;)V
    ATHROW
   L1
    ALOAD 3
    GETFIELD org/serialthreads/context/StackFrame.owner : Ljava/lang/Object; <-- Ist CPU, weil Methode in CPU definiert und nur per Lamda gewrapt...
    CHECKCAST de/heiden/jem/models/c64/components/cpu/CPU6510$Opcode
    ALOAD 1
    ALOAD 3
    INVOKEINTERFACE de/heiden/jem/models/c64/components/cpu/CPU6510$Opcode.execute$$__V$$ (Lorg/serialthreads/context/Stack;Lorg/serialthreads/context/StackFrame;)V


   */
}
