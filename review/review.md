# Code review: serialthreads

**Date:** 2026-08-24
**Scope:** All of `src/main` (72 files, ~9,300 lines), with emphasis on the transformers
(`org.serialthreads.transformer.*`). Reviewed for **correctness** and **conciseness**.
Findings were verified against the actual source; the ASM-related claims were checked
against the ASM 9.x sources.

---

## Summary

The core transformation machinery is in better shape than its age suggests: the frame
chaining, the `method == -1` startup sentinel, the position-index agreement between
original-method capture and copy-method dispatch, and the backward `neededLocals`
dataflow in `ExtendedAnalyzer` all check out under close tracing. The code is clean and
well-commented where it matters.

The problems cluster in four places:

1. **Untested edges of the bytecode model.** Capturing a `long`/`double` operand-stack
   value emits illegal bytecode (H1); locals capture keys "skip local 0" on the *callee's*
   staticness instead of the caller's (H2); tail-call detection reasons by instruction
   adjacency instead of control flow (H3). All are silent on the happy path the test
   suite covers and corrupt data or fail verification just outside it.
2. **A broken analyzer contract.** `ExtendedValue` never overrides `equals()`, so ASM's
   fixpoint merge silently discards the very annotations (`locals`, `constant`) the whole
   compacting-capture optimization depends on (H4).
3. **Concurrency.** The class-info caches and the agent share mutable state across what
   the JVM guarantees to be concurrent and re-entrant class loading (H6).
4. **Dead weight.** Roughly 800+ lines of dead code (all of `Maxs`, ~230 lines of unused
   `StackFrame` API, dead `Stack` navigation methods, dead value-code methods) and
   near-total duplication between the four strategy packages (~500–700 duplicated lines),
   which doubles or quadruples the surface on which every fix must land.

Severity legend: **High** = produces wrong behavior or invalid bytecode on realistic
input; **Medium** = wrong on plausible input, resource issue, or missing validation;
**Low** = latent, debug-only, or cosmetic.

---

## Correctness — High

### H1. Capturing a long/double stack value generates invalid bytecode (`SWAP` on category-2)

**Status: FIXED (2026-08-24).** `pushStackFast`/`pushStackSlow` now use `DUP_X2; POP` for
category-2 values, mirroring `pushReturnValue`. Covered by new integration tests
(`TestStackLong`/`TestStackDouble`, fast and slow storage) which fail without the fix in
all four strategies. Note: the operand-stack value must not be a compile-time constant —
javac inlines constant variables (including final instance fields with constant
initializers), and the analyzer's constant tracking skips capture for them.

`transformer/code/AbstractValueCode.java:158-176` (`pushStackFast`, `pushStackSlow`)

```java
instructions.add(new VarInsnNode(ALOAD, localFrame));
instructions.add(new InsnNode(SWAP));   // value may be long/double → illegal
instructions.add(new FieldInsnNode(PUTFIELD, ...));
```

`pushStack` is called with the value to save on top of the operand stack. The JVM spec
forbids `SWAP` unless both operands are category 1, and `LongValueCode`/`DoubleValueCode`
do **not** override `pushStack`. Any interruptible call with a non-constant,
non-local-mirrored `long`/`double` beneath it on the operand stack — e.g.
`long r = this.f + interruptibleLong();` or `foo(a * b, interruptibleCall())` with
`long a, b` — produces a `VerifyError` at load time. The path is reachable:
`CompactingStackCode.saveStack` dispatches such values to `pushStack`, and
`stackIndexes` explicitly computes indexes for `LONG`/`DOUBLE` stack values.

Tellingly, `pushReturnValue()` in the same class (lines 339–353) handles category 2
correctly with `DUP_X2; POP`, and the restore direction (`popStackFast/Slow`) is fine —
only capture is broken. The tests (e.g. `TestLong`) exercise category-2 *locals*, never
stack captures, which is why this lies dormant.

**Fix:** use the `DUP_X2`/`POP` pattern (or store to a temp local) for `size == 2`,
mirroring `pushReturnValue`.

### H2. Local 0 skipped based on the *callee's* staticness, not the transformed method's

`transformer/code/CompactingStackCode.java:78-82` (`saveLocals`) and `:126-131`
(`restoreLocals`); related: `transformer/code/MethodCode.java:75-89` (`isSelfCall`) and
`strategies/AbstractMethodTransformer.java:311-343` (`setOwner`/`pushOwner`)

```java
var isMethodNotStatic = isNotStatic(methodCall);   // tests the CALLEE (INVOKESTATIC)
// Do not store local 0 for non-static methods, because it always contains "this".
for (int local = isMethodNotStatic ? 1 : 0, ...
```

The locals being captured belong to the **containing** (transformed) method, but the
predicate tests the **called** method's opcode. When a *static* interruptible method
calls an *instance* interruptible method, local 0 of the static caller holds its first
real parameter, yet the loop starts at 1 — local 0 is never captured. On resume the
copy/original is re-entered with dummy arguments, so local 0 comes back as 0/null:
silent data corruption or NPE. `ExtendedFrame.getLowestNeededLocal` confirms local 0
can be a needed local, and nothing upstream excludes static callers.

The same confusion is systemic:

- `MethodCode.isSelfCall` treats "owner value lives in local 0" as a self call without
  checking whether the *transformed* method is static. In a static interruptible method
  `m(Foo o)` calling `o.bar()`, `o` sits in local 0 → `setOwner` suppresses saving the
  owner and `pushOwner` emits `ALOAD 0`. On restore, frequent re-invokes with null dummy
  arguments (NPE), and in a frequent2/3/4 static copy local 0 is the `Stack`/`StackFrame`
  parameter (`VerifyError`).
- The inverse direction (instance caller, static callee) merely wastes a slot saving
  `this` redundantly — harmless only because capture and restore share the same wrong
  predicate.

It works in the existing tests only because there `interrupt()` is static and all
callers are instance methods.

**Fix:** `CompactingStackCode` never receives the transformed `MethodNode`, so it
*cannot* compute this correctly today — pass the containing method's access flags (or
the method itself) into `captureFrame`/`restoreFrame`, and give `isSelfCall` the same
information.

### H3. Tail-call return replacement corrupts merged returns (frequent3/frequent4)

`strategies/frequent3/MethodTransformer.java:146-169` (`replaceReturns`), same at
`frequent4/MethodTransformer.java:142-161`; tagging at
`strategies/AbstractMethodTransformer.java:151-154`

`TAG_TAIL_CALL` is assigned by *list adjacency* (`isReturn(nextInstruction(methodCall))`),
and `replaceReturns` applies tail-call treatment based on the return's *list predecessor*
(`previousInstruction(returnInstruction)`). Neither considers control flow. For
`return c ? foo() : bar();` javac emits a single shared `IRETURN`
(`foo(); GOTO L; ... bar(); L: IRETURN`). `bar` is tail-tagged, so the shared return is
replaced *without* `pushReturnValue` — but the `foo` path also reaches `L`. After foo's
capture code, the `normal:` path executes `popReturnValue(localThread)`, which for
reference types reads **and clears** `thread.returnObject`
(`AbstractValueCode.popReturnValue()`: `pushNull(); PUTFIELD`). foo's value is then
discarded by the `ICONST_0; IRETURN`, and the enclosing caller pops
`thread.returnObject == null`. Any interruptible reference-returning method whose merged
return has one interruptible-call inflow (ternary, switch expression) silently returns
`null`. Primitives survive only by accident (primitive `popReturnValue` doesn't clear
the field).

**Fix:** tag tail calls using the analyzer's control-flow information (successor frames),
not list adjacency — or verify at replacement time that *all* predecessors of the return
are tail-tagged calls.

### H4. `ExtendedValue` breaks ASM's `equals()` merge contract — annotations silently discarded

`transformer/analyzer/ExtendedValue.java` (whole class), interacting with
`ExtendedVerifier.java:85-98`

ASM's `Frame.merge` decides whether a merged value must be stored via `equals()`:

```java
V v = interpreter.merge(values[i], frame.values[i]);
if (!v.equals(values[i])) { values[i] = v; changed = true; }
```

`ExtendedValue` inherits type-only equality from `BasicValue`. `ExtendedVerifier.merge`
correctly computes the intersection of `locals` and drops non-matching constants — but
when the type is unchanged, the new value `equals()` the old one, so `Frame.merge`
neither stores it nor iterates: the first-seen (larger) locals set / stale constant
survives at every control-flow join. The class even contains `equalsValue()`
("equals() replacement which additionally checks the extended attributes") — but ASM
calls `equals()`, which nobody overrode.

This is not cosmetic: `CompactingStackCode.saveStack/restoreStack` uses
`getLowestNeededLocal` to *skip saving* a stack slot and reload it from a local instead.
With a stale "value is also in local N" annotation that only holds on one inbound path,
the restored frame contains a wrong value on the other path.

**Fix:** override `equals`/`hashCode` to include `constant` and `locals`; delete
`equalsValue`.

### H5. Reflection scan uses `Annotation::getClass` — annotations are never detected

**Status: FIXED (2026-08-24).** Changed to `Annotation::annotationType`. Covered by a new
test in `ClassInfoCacheReflectionTest` that forces the reflection scan via a class loader
hiding all class-file resources; the test fails without the fix. The test class also no
longer overrides (and thereby disables) the inherited cache test. Correction to the
original finding: the bug did *not* repeat for constructors — the constructor loop uses
`emptySet()`, which is fine since the serialthreads annotations have `@Target({METHOD})`.

`transformer/classcache/ClassInfoCacheReflection.java:117-120`

```java
var annotations = stream(method.getAnnotations())
  .map(Annotation::getClass)   // JDK proxy class, e.g. jdk.proxy2.$Proxy8
  .map(Type::getType)
```

`getClass()` on an annotation instance returns the proxy class, not the annotation type,
so `@Interruptible`/`@Interrupt`/`@Executor` are invisible on every reflection-scanned
class (the fallback path when no class-file resource is available). Interruptible methods
inherited from such a class are treated as plain methods; if an ASM-scanned subclass
overrides an annotated method of a reflection-scanned superclass, `ClassInfo.merge`
throws a spurious `NotTransformableException` ("Interruptible status ... does not match
its definition in the super class") because the two scans disagree.

**Fix:** `.map(Annotation::annotationType)`.

### H6. Class-info caches and Agent are not safe for concurrent/re-entrant class loading

`agent/Agent.java:48-83`, `transformer/classcache/ClassInfoCacheReflection.java:28-59`,
`transformer/classcache/AbstractClassInfoCache.java:39-46, 176-185`

The JVM invokes `ClassFileTransformer.transform` concurrently (parallel-capable class
loaders) and re-entrantly. The caches use unsynchronized `HashMap`/`TreeMap` plus a
single shared mutable `classLoader` field:

- **Re-entrancy on one thread:** `scanReflection` calls `Class.forName(..., false,
  classLoader)`, which loads the class and re-enters `Agent.transform` → the inner
  `start()` overwrites `classLoader`, the inner `stop()` sets it to `null`
  (`ClassInfoCacheReflection.java:57`) — the *outer* transform then NPEs at
  `classLoader.getResourceAsStream(...)` on its next superclass scan.
- **Concurrency:** unsynchronized map mutation from multiple threads (corruption,
  infinite loops); `classLoader` is whichever `start()` ran last. The cache is also
  keyed by class name only, so identically-named classes from different loaders collide.

Fails intermittently in any multithreaded application run with the agent.

### H7. `Stack.reset()` never resets the first frame — restarting a finished thread is broken

**Status: FIXED (2026-08-24).** `reset()` now resets the first frame too, points `frame`
back to `first`, and clears `serializing` and the return-value registers — restoring the
post-constructor state. Covered by a new `testRestart` integration test (run → finish →
restart) which fails in all four strategies without the fix.

`context/Stack.java:146-157`

```java
public void resetTo(StackFrame resetTo) {
  for (StackFrame frame = resetTo.next; frame != null; frame = frame.next) { frame.reset(); }
}
public void reset() { resetTo(first); }
```

`resetTo` starts at `resetTo.next`, so `reset()` leaves `first.method`, `first.owner`
and `first`'s locals/stack populated, and never resets `this.frame` or `serializing` —
despite the javadoc "Resets the complete stack". The generated `run()` dispatcher treats
`method == -1` as "fresh start" (`StackFrame` constructor: "has to be -1 for dummy
startup restore!"). `SimpleSerialThreadManager.execute()` / `SerialThreadExecutor.execute()`
call `reset()` on the finished thread precisely so it can be re-run — but on the next
`run()` the dispatcher reads the stale `first.method >= 0` and jumps into a restore label
with an emptied frame (null owner, cleared locals).

**Fix:** also reset `resetTo` itself (or `first.reset(); frame = first;` in `reset()`),
and reset `serializing`.

---

## Correctness — Medium

### M1. Executor validation is unreachable — `@Executor` methods are never checked

`strategies/AbstractTransformer.java:181-187` (with 142-145, 222-226)

`check()` contains the rule "an executor may only call run", but `check()` runs only when
`transformMethod()` returned null — and `transformMethod()` returns
`singletonList(method)` for executors ("bypass check()"). So inside `check()`,
`isExecutor` is always false: the executor rule is dead code (consistent with the stale
`// TODO 2009-12-11 mh: check executor`). An executor calling an interruptible non-run
method transforms fine and breaks at runtime. Side effect: the untransformed executor
lands in `allTransformedMethods`, so a class whose only special method is an executor
never gets the `LoadUntransformedException` fast path.

### M2. Constructors bypass the interruptible-call check entirely

`strategies/AbstractTransformer.java:129-154`

`transform(ClassNode)` splits `<init>` methods out before the method loop; they are
neither transformed nor passed through `check()`. A constructor calling an interruptible
method — exactly what `check()` exists to reject for normal methods — passes silently at
transform time and fails at runtime (changed callee descriptor → `NoSuchMethodError`, or
runs without capture support and continues with dummy values).

### M3. `new ClassWriter(COMPUTE_FRAMES)` without a `getCommonSuperClass` override

`strategies/AbstractTransformer.java:109`

No `ClassWriter` subclass exists in the project. The default `getCommonSuperClass`
resolves types via `Class.forName` on the ClassWriter's own loader. The generated restore
dispatchers jump into the middle of methods, so frame merging at those targets can require
common-superclass computation over *application* classes. Inside the agent or
`TransformingClassLoader` this (a) loads application classes mid-transform
(`ClassCircularityError` risk, classes loaded untransformed or by the wrong loader) and
(b) fails with `TypeNotPresentException` when the transformed class lives in a child
loader invisible to the serialthreads loader. Classic ASM pitfall — override
`getCommonSuperClass` to use the defining loader (or the class-info cache).

### M4. Interruptible calls to `run()` generate invalid bytecode instead of a clear error (frequent3/4)

`frequent3/MethodTransformer.java:133-139`, `frequent4/MethodTransformer.java:126-131`

`IRunnable.run()` is `@Interruptible`, so a `run()` call inside an interruptible method
gets standard capture code (an `IFEQ` expecting a boolean result) — but the descriptor
exclusion (`!isRun(...)`) leaves it `()V`, so `IFEQ` operates on an empty stack; the
restore path would additionally call `run$$__V$$(...)`, which is never generated.
The result is an opaque verifier/frames error. frequent2 has the inverse problem: it
renames *every* interruptible call (`copyMethodCall`,
`frequent2/MethodTransformer.java:254-260`) with no `isRun` exclusion at all, so a nested
`run()` call yields `NoSuchMethodError` on the first resume through that site. Either
support the pattern or raise `NotTransformableException` during transform.

### M5. Interrupt-call replacement leaves the owner push on the stack (frequent/frequent2)

`frequent/MethodTransformer.java:99-131`, `frequent2/MethodTransformer.java:122-154`

`replace(methodCall, instructions)` removes only the `INVOKE*` node. For *instance*
interrupt methods, the preceding `ALOAD 0` remains on the stack through the capture path.
Worse, `captureFrame` saves the stack against `metaInfo.frameAfter`, which excludes the
replaced call's owner/arguments — if the operand stack is ever non-empty at an interrupt
call, `saveStack` would save the leftover owner in place of the real top-of-stack value.
Works today only because `@Interrupt` methods are void and called as standalone
statements. Delete the owner/argument-push instructions of the replaced call, or validate
that interrupt methods are static and parameterless.

### M6. `fixMaxs` under-allocates one slot for long/double return values (frequent/frequent2)

**Status: FIXED (2026-08-24).** The transformer now tracks the maximum size of the
return values actually stored in the return-value local (`maxReturnValueSize`) and
`fixMaxs` reserves exactly that many slots. Found in practice: the new H1 tests failed
reanalysis in frequent/frequent2 with "Trying to set an inexistant local variable"
until this was fixed.

`frequent/MethodTransformer.java:176, 245-249`; `frequent2/MethodTransformer.java:197, 266-274`

`localReturnValue = method.maxLocals` and `fixMaxs()` does `maxLocals += 1` — but a
`long`/`double` return stored there occupies two slots. The emitted class is rescued by
`COMPUTE_FRAMES` (implies `COMPUTE_MAXS`), but `reanalyzeMethods`
(`AbstractTransformer.java:199-210`, active with debug logging) analyzes with the stated
`maxLocals` and fails with `AnalyzerException`. Should be sized from the return type.

### M7. `ExtendedVerifier.isAssignableFrom` drops interface handling

`transformer/analyzer/ExtendedVerifier.java:126-128`

For `t.equals(currentClass)` the code walks only the superclass chain of `u`; ASM's own
`SimpleVerifier` additionally short-circuits interfaces
(`if (isInterface) return type2.getSort() == OBJECT || ARRAY`). When the class being
analyzed is an interface (relevant: interruptible `default` methods are transformed) and
a value of an implementing class merges with a value of the interface type, the recursion
never finds the interface and returns false — the merge degrades toward `Object` or fails
where `SimpleVerifier` would succeed.

### M8. Unclosed `InputStream`s from `getResourceAsStream`

`transformer/classcache/ClassInfoCacheASM.java:40-41`,
`ClassInfoCacheReflection.java:84-88`

ASM's `ClassReader(InputStream)` does not close the stream, and neither does this code —
one leaked jar/file stream per scanned class (including all transitively scanned
superclasses). Wrap in try-with-resources. `ClassInfoCacheASM` also lacks a null check —
a missing resource surfaces as ASM's bare `IOException("Class not found")` without the
class name.

### M9. Method-info merge conflates non-virtual methods across the hierarchy

`transformer/classcache/ClassInfo.java:180-196`

`merge` copies **all** superclass methods (private, static, constructors —
`ClassInfoVisitor.visitMethod` ignores access flags) into the subclass map keyed by
`name + desc`, then throws on annotation mismatch. Private/static methods don't
override, yet an unrelated same-signature pair (e.g. a private `@Interruptible step()V`
in the superclass, an unannotated private `step()V` in the subclass) fails the whole
transformation with a false-positive `NotTransformableException`.

### M10. Agent error handling: exceptions swallowed by the JVM, `stop()` skipped

`agent/Agent.java:57-83`

- Per the `ClassFileTransformer` contract, exceptions thrown from `transform` are caught
  and ignored by the JVM and the class loads *untransformed* — so `throw e` for
  `NotTransformableException` doesn't fail the load; the app fails obscurely later. The
  class javadoc TODO admits this is unresolved.
- `classInfoCache.stop(className)` runs only on the success path; on
  `LoadUntransformedException` (very common) and error paths, entries and the retained
  `classLoader` reference accumulate for the life of the agent.

### M11. `StackFrame` size bookkeeping desyncs on growth; `reset()` leaks fast-slot references

`context/StackFrame.java:255-261, 387-393, 431-460` and `:229-249`

- `resize(Object[] old, Object object)` doubles the array but never updates `size`; a
  later `resize(int max)` computes from stale `size` and `System.arraycopy(old, 0,
  result, 0, old.length)` throws `ArrayIndexOutOfBoundsException`. Currently only dead
  public API (see C2), but a trap.
- `reset()` nulls only the array parts; `stackObject0..7`/`localObject0..7` keep their
  references (generated capture code *does* store references there). After a thread
  finishes with captured state, up to 16 object references per frame stay reachable,
  preventing GC. The 15-year-old TODOs ("reset fast stack too") acknowledge it.

### M12. `TransformingExtension` drops test-method arguments

`src/testFixtures/.../agent/TransformingExtension.java:74-89`

`method.invoke(instance)` ignores `invocationContext.getArguments()`, so any
`@ParameterizedTest` routed through the transforming extension fails with
`IllegalArgumentException: wrong number of arguments`.

### M13. `DebugPrinter` misses `visitInvokeDynamicInsn` — debug output desyncs after any indy

`transformer/debug/DebugPrinter.java:74-156`

Every instruction visitor bumps the instruction counter except `visitInvokeDynamicInsn`.
With Java 9+ string concatenation and lambdas compiling to `invokedynamic`, every printed
frame/index after the first indy is attached to the wrong instruction. Debug-only.

---

## Correctness — Low

- **L1.** `strategies/AbstractMethodTransformer.java:135-156` — `analyze()` stores
  analyzer frames directly; unreachable interruptible calls (possible with non-javac
  bytecode) get `MetaInfo` with null frames → NPE in `CompactingStackCode` instead of a
  `NotTransformableException`.
- **L2.** `strategies/AbstractMethodTransformer.java:182-189, 248` — the documented
  "may return null" contract of `createCaptureAndRestoreCode` is honored by no strategy
  (all return a — sometimes dangling, never-inserted — label), the single-restore path
  would emit `GOTO null` if it ever were, and the multi-restore `replaceAll` null-guard
  is dead. Make the contract "never null", fix the javadoc, drop the guard. Note
  `replaceAll` also mutates the caller's list in place.
- **L3.** `strategies/AbstractTransformer.java:289-380` — constructor injection edge
  cases: `int localThread = 1` clobbers the debug variable table for slot 1; `this(...)`
  delegation initializes `$$thread$$`/`$$frame$$` twice (two `Stack` allocations per
  instance); the `ITRANSFORMED_RUNNABLE_NAME` guard checks only *direct* interfaces, so
  a transformed runnable extending a transformed runnable gets shadowing fields and a
  duplicate `getThread()` (user-declared `getThread()` → `ClassFormatError`).
- **L4.** `strategies/AbstractTransformer.java:67, 83-86` — mutable non-volatile
  `check` flag on a transformer shared across concurrent class loads; verification also
  depends on the log level at construction time.
- **L5.** `context/SimpleSerialThreadManager.java:50,58` /
  `SerialThreadExecutor.java:55,63` — `int loops = interrupts * chains.length` can
  overflow negative; `do { } while (--loops != 0)` then runs ~2^32 iterations.
- **L6.** `context/SimpleSerialThreadManager.java:39,65` — `execute()` calls `close()`
  (clearing the static ThreadLocal) on first thread finish although the manager is
  designed for repeated invocation, and clobbers a thread set by a different manager on
  the same OS thread. Inconsistent with `SerialThreadExecutor`, which never closes.
- **L7.** `context/ChainedRunnable.java:36` — empty input throws raw
  `ArrayIndexOutOfBoundsException`; the callers' guards are `assert`s (inactive without
  `-ea`).
- **L8.** `agent/TransformingClassLoader.java:27-33, 58, 86` — not registered as
  parallel-capable; whole-method `synchronized loadClass` instead of
  `getClassLoadingLock(name)`; `defineClass` without `ProtectionDomain`/package
  definition; javadoc says "system class loader as parent" but the code uses the context
  classloader.
- **L9.** `compiler/InterruptibleProcessor.java:20, 67, 72` — pinned to `RELEASE_20`
  (toolchain is Java 26) and findings emitted only as `Kind.NOTE`, so the check can
  neither fail nor visibly warn.
- **L10.** `transformer/classcache/ClassInfo.java:101-130` — `isInterruptible`/`isInterrupt`/
  `isExecutor` NPE for unknown method ids (reachable via signature-polymorphic
  `MethodHandle.invoke` call sites) instead of a diagnostic exception.
- **L11.** `transformer/classcache/AbstractClassInfoCache.java:176-216` — if the initial
  `scan` throws, the error message reads "Referenced class null not found" (`className`
  still null); `process` and its only caller both `put` the same entry.
- **L12.** `transformer/analyzer/ExtendedFrame.java:83-118` — `removeLocalFromStack`
  doesn't mirror ASM's two-slot invalidation on `LSTORE`/`DSTORE` (stale "also in local
  var±1" annotations). Latent — no failing case constructed — but the asymmetry with
  `Frame.execute` is real and cheap to close.
- **L13.** `frequent3/OriginalMethodTransformer.java:64-67` (same in frequent4) —
  `previousFrame.owner` is written on every invocation even for methods that can never
  capture (`hasNoInterruptibleMethodCalls` path). Pure per-call overhead on the hot path
  of the jem emulator.

---

## Conciseness

### Duplication (the biggest maintainability cost)

- **C1 (medium). frequent3 vs frequent4 are ~90 % identical.**
  `FrequentInterruptsTransformer3` vs `4`: byte-identical apart from the digit.
  `OriginalMethodTransformer`: identical except one method name. `RunMethodTransformer`:
  identical except one `getRunThread` line. `MethodTransformer` (~420 lines): differs
  only in `localThread` presence, descriptor suffixes and thread access via parameter vs
  `frame.stack`. `needsFrame()`, `isTailCall(...)`, `methodReturn(...)`,
  `changeCopyName(...)`, `fixMaxs()` are verbatim copies using only base-class state —
  they belong in `AbstractMethodTransformer`. Neither javadoc states how strategy 4
  differs from 3.
- **C2 (medium). frequent vs frequent2 likewise.**
  `createCaptureAndRestoreCodeForInterrupt` is byte-for-byte identical;
  `...ForMethod` differs in ~5 lines (resume invocation); the two
  `RunMethodTransformer`s differ in one `setFrame` line; the restore-handler prologue and
  `afterTransformation` are duplicated verbatim (frequent2's log line 72 even lost the
  class-name argument). A shared intermediate base with one hook ("emit the resume
  invocation") would eliminate ~200 lines here and ~500 across all four packages, and
  make fixes for H1–H3, M5, M6 single-site.
- **C3 (low).** `SimpleSerialThreadManager` and `SerialThreadExecutor` `execute()` bodies
  are near-verbatim duplicates (differing in the `close()` call, see L6).
- **C4 (low).** `AbstractTransformer` and `AbstractMethodTransformer` both hardwire
  `protected final ThreadCode threadCode = new CompactingStackCode();` — duplicated
  choice, and the `ThreadCode` abstraction cannot actually be swapped per strategy.

### Dead code (verified: no callers in main, tests, or generated bytecode)

- **C5 (medium).** `strategies/Maxs.java` — the entire class (85 lines).
- **C6 (medium).** `context/StackFrame.java` — ~230 lines of unused API: the whole
  "standard interface for capture/restore" (`pushStackObject` … `popLocalDouble`), both
  `resize` families, `isEmpty`, `logSizes`, `getOwner`, `getMethod`, the never-read
  `last` field, and `methodHandle` + `METHOD_TYPE` (leftovers of the unimplemented
  method-handle design in `concept.txt`). Generated code only uses `addFrame`,
  `getThread`, `setThread` and direct field access. Removing this reveals the real,
  much smaller contract between generated code and runtime.
- **C7 (low).** `context/Stack.java:88-138` — `addFrame`, `enterMethod`,
  `enterFirstMethod`, all three `leaveMethod` overloads: no callers anywhere.
- **C8 (low).** `transformer/code/` — `MethodNodeCopier.copyEmpty` (also lossier than
  `copy`); `IValueCode.move`/`AbstractValueCode.move`; the whole
  `MethodCode.isCompatible` → `isCompatibleWith` chain (~40 lines across four files).
- **C9 (low).** `strategies/AbstractMethodTransformer.java:376-406` —
  `insertLabelBefore`/`insertLabelAfter` have zero callers; related, `searchLabel`
  (431-439) tests the start node itself first, so label reuse never triggers for real
  instructions. `AbstractTransformer.logDebug(Frame)` likewise uncalled. The blanket
  class-level `@SuppressWarnings("UnusedDeclaration")` on most transformer classes is
  what lets dead code accumulate unnoticed — consider removing the suppressions.
- **C10 (low).** `frequent3/CopyMethodTransformer.java:101-106` — the
  `localPreviousFrame` copy block is dead (nothing in frequent3 copy-method paths reads
  it; return values go to `thread.returnXXX`, so the 2018 TODO's rationale is false;
  frequent4's parallel handler omits the block). The `suppressOwner` parameter of
  `createCaptureAndRestoreCode` is likewise dead in frequent3/frequent4.
- **C11 (low).** `agent/Agent.java:54, 78-82` — the `failure` flag: every path sets it
  to `false` before exiting, so the `finally` log is unreachable.
- **C12 (low).** `context/ChainedRunnable.java` — protected no-arg constructor (no
  subclasses), `run()` (executors inline `chain.runnable.run()`), never-read `thread`
  field, unused `Iterator` import.

### Simplification / hygiene

- **C13 (medium).** `AbstractValueCode.baseType` is always identical to `type` (the only
  reference subclass passes `Object` as `type` and keeps the concrete type separately) —
  the field can be deleted. It currently masks an inconsistency in `popReturnValue()`:
  `GETFIELD` uses `baseType.getDescriptor()`, the clearing `PUTFIELD` uses
  `type.getDescriptor()` — same string today, a trap for future edits.
- **C14 (low).** `MetaInfo.tags` is `Set<Object>` but only ever holds the `String`
  constants declared in the same class → `Set<String>`; the constants are also
  interleaved between fields, hurting readability.
- **C15 (low).** `AbstractTransformer.java:103-116` — the transformed bytecode is
  debug-logged twice with the identical message; `transformConstructor`/`createGetThread`
  set maxs manually although `COMPUTE_FRAMES` recomputes them; `createGetThread` stores
  to local 1 and reloads where `GETFIELD`/`ARETURN` suffices.
- **C16 (low).** `CompactingStackCode.stackIndexes` (196-209) assigns frame slots to
  *every* stack value including constants/local-mirrored ones that are never stored —
  consistent, but burns the 8 fast fields on holes.
- **C17 (low).** `AbstractStackCode.java:39, 110` — field descriptor passed as the
  generic-signature argument of `FieldNode` (should be null); writes a pointless
  `Signature` attribute into every transformed class.
- **C18 (low).** `LocalVariablesShifter` — doc says "shift to the left" while `remap`
  shifts up; garbled `@param shift`; redundant `getType() == VAR_INSN &&
  instanceof VarInsnNode` checks; doesn't remap local-variable *type annotations*
  (`visible/invisibleLocalVariableAnnotations` keep stale indexes — wrong metadata, not
  verify-fatal).
- **C19 (low).** `MethodNodeCopier.copy` — double label indirection via `Label.info`;
  mapping `Label → LabelNode` directly is simpler (the override itself is necessary and
  correct).
- **C20 (low).** Stale docs/typos worth a sweep: `IValueCode.pushStack/popStack` javadoc
  describes an older calling convention ("frame ... already on top of the stack" — false,
  and actively misleading next to H1); `changeCopyDesc(String desc)` ignores its
  parameter in frequent3/4 and frequent4's javadoc still claims it inserts *thread and*
  frame; `FrequentInterruptsTransformer2` class javadoc is self-contradictory;
  `pushOwner` javadoc says "Restore owner."; "needs to transformation", "may not not
  implement"; TODOs from 2009/2010/2013/2018 that document known bugs (M11, M1) or
  answered questions (`MethodCode.java:278`) should be resolved or deleted;
  `ClassInfoCacheReflection.classes` shadows the superclass cache's name with different
  semantics (pending visitors vs finished infos); unused imports in
  `ClassInfoCacheASM`, `ClassInfoCacheReflection`, `ExtendedFrame`, `ClassInfoVisitor`;
  wildcard import in `frequent4/CopyMethodTransformer`; `DebugPrinter`'s
  `index.toUpperCase()` is a no-op on digits; `StackFrame.DEFAULT_FRAME_SIZE` is a
  mutable `public static` non-final; `TransformingClassLoader.loadByteCode` hand-rolls
  what `InputStream.readAllBytes()` + try-with-resources does in two lines (plus a
  commented-out debug line at :76).
- **C21 (low).** Analyzer/classcache micro-cleanups: `MethodInfo.copy()` is pointless
  (deeply immutable class); `ClassInfo.merge` runs `interruptible |= ...` inside the
  per-method loop; `ExtendedVerifier.merge` hand-rolls `Objects.equals`;
  `ExtendedAnalyzer` duplicates array-copy logic in `getFrames()`/`analyze()`;
  `ExtendedFrame.removeLocalFromStack` copies the whole frame though only the stack
  needs snapshotting.

---

## Recommended priorities

1. **Fix the analyzer contract (H4) and the reflection scan (H5)** — small, mechanical,
   high impact, easy to unit-test.
2. **Fix the category-2 stack capture (H1) and the caller-staticness confusion (H2)**,
   adding targeted transformation tests: static interruptible callers, long/double
   values on the operand stack across an interrupt, merged returns (ternary) for
   reference-returning methods (H3). These are exactly the untested regions where all
   the high-severity bugs live.
3. **Fix `Stack.reset()` (H7)** if restarting finished threads is a supported use case
   (jem relies on the managers).
4. **Decide on the concurrency story (H6, M10, L4, L8)**: either document
   single-threaded class loading as a hard requirement or synchronize the caches and
   make the agent/loader parallel-capable.
5. **Collapse the strategy duplication (C1/C2)** before further fixes — most remaining
   corrections become single-site.
6. **Delete the dead weight (C5–C12)** — roughly 800 lines; it materially clarifies the
   real contract between generated code and the runtime.
7. Consider deleting the `frequent`/`frequent2` (and possibly one of `frequent3`/
   `frequent4`) strategies outright if only one is used in practice — that alone removes
   more code than every other suggestion combined.
