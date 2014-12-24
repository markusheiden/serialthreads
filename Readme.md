# Serial threads

Allows to execute many serial threads on one java thread. This is done by capture and restore the call stack.

# TODO

   * Lambdas cannot be interrupted.

Name locals
No string concat in log statements
Check instruction iterations
Name labels?
Avoid toString() in log statements
MetaInfo.addTag()?
Check tail call impl. Remove old todos?
MethodCode.escapDesc() -> desc.replaceAll("[()\\[/;]", "_").

Detect interruptible lambdas? -> Change signatures.

Add installer?
