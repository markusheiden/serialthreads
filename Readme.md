# Serial threads

Allows to execute many serial threads on one java thread. This is done by capture and restore the call stack.

# TODO

   * Lambdas cannot be interrupted.
   * Exceptions are not handled for interruptible methods.

Introduce paramThread() etc.???
Name locals
--Name labels?
MetaInfo.addTag()?
Check tail call impl. Remove old todos?

Detect interruptible lambdas? -> Change signatures.

Add installer?
