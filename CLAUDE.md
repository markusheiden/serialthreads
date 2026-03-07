# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```shell
./gradlew build                                          # build and test
./gradlew test                                          # run tests
./gradlew test --tests "org.serialthreads.SomeTest"     # run a single test class
./gradlew publishToMavenLocal                           # publish to ~/.m2
./gradlew wrapper                                       # update wrapper from libs.versions.toml
```

Note: `tasks.test` has `ignoreFailures = true`, so test failures do not fail the build.

The `performanceTest` source set (`src/test/performance/`) has no associated Gradle task and cannot be run via Gradle.

## Architecture

This library enables **cooperative multitasking** (many "serial threads" on one OS thread) by capturing and restoring Java call stacks via bytecode transformation at load time.

### User-facing API (`org.serialthreads`)

- `@Interruptible` — marks a method whose execution can be suspended (call stack captured)
- `@Interrupt` — marks a method whose call site is replaced with an actual interrupt point
- `@Executor` — marks executor methods (entry points that drive the serial threads)
- `IRunnable` — interface implemented by serial thread code (like `Runnable`)
- `SerialThreadManager` / `SimpleSerialThreadManager` — manage and run a set of serial threads cooperatively

### Transformation pipeline

Bytecode transformation is triggered at class load time by either:
- **`Agent`** (`org.serialthreads.agent`) — a JVM `-javaagent` that hooks into instrumentation; registered as `Premain-Class` and `Agent-Class` in the jar manifest
- `ITransformer` / `AbstractTransformer` — core ASM-based transformer; reads class bytes, detects interruptible methods via `IClassInfoCache`, rewrites bytecode

`IClassInfoCache` has two implementations:
- `ClassInfoCacheReflection` — used by the agent (live class hierarchy via reflection)
- `ClassInfoCacheASM` — used in tests (class hierarchy from raw bytecode)

### Strategies (`org.serialthreads.transformer.strategies`)

Four strategies trade off between code size and performance; `FREQUENT4` is the default. Each lives in its own subpackage and provides:
- `OriginalMethodTransformer` — rewrites the original method to save state on interrupt
- `CopyMethodTransformer` — generates a copy of the method used to restore state
- `RunMethodTransformer` — transforms the top-level `run()` method
- `MethodTransformer` — shared method-level transformation logic

### Runtime (`org.serialthreads.context`)

- `Stack` / `StackFrame` — data structures that hold captured call stack state at runtime; `StackFrame` stores locals and operand stack values for one frame
- `SerialThread` — represents one serial thread's execution state (its `Stack`)
- `SerialThreadExecutor` — runs serial threads round-robin

### Bytecode utilities (`org.serialthreads.transformer`)

- `transformer/analyzer/` — extended ASM dataflow analyzer (`ExtendedAnalyzer`, `ExtendedFrame`) that tracks which locals are in use at each interrupt point
- `transformer/code/` — code generation helpers: `IValueCode` / concrete implementations per JVM type (int, long, float, double, reference), `CompactingStackCode` for stack save/restore sequences, `MethodCode` for method-level utilities
- `transformer/classcache/` — class metadata visitors and cache implementations

### Known limitations

- Lambdas cannot be interrupted
- Exceptions are not handled for interruptible methods
