package org.hl7.fhir.jekyll.rouge;

import java.util.regex.Matcher;

/**
 * java.util.regex backtracks by recursion (Onigmo, Ruby's engine, uses a heap stack), so a Rouge
 * rule such as <code>"(\\\\|\\"|[^"])*"</code> overflows the thread stack on a long enough match where
 * Ruby does not. When that happens the same match is retried on a helper thread with a very large
 * stack: identical semantics, only slower, and only for pathological inputs.
 */
final class BigStack {
  private BigStack() {}

  static final long STACK = 4L << 30; // reserved, committed only as used

  static boolean lookingAt(Matcher m) {
    boolean[] result = new boolean[1];
    Throwable[] error = new Throwable[1];
    Thread t = new Thread(null, () -> {
      try {
        result[0] = m.lookingAt();
      } catch (Throwable e) {
        error[0] = e;
      }
    }, "rouge-bigstack", STACK);
    t.setDaemon(true);
    t.start();
    try {
      t.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RougeError("interrupted");
    }
    if (error[0] instanceof StackOverflowError) {
      throw new RougeError("regex recursion too deep even with a " + (STACK >> 20) + " MB stack: " + m.pattern());
    }
    if (error[0] instanceof RuntimeException) {
      throw (RuntimeException) error[0];
    }
    if (error[0] != null) {
      throw new RougeError(error[0].toString());
    }
    return result[0];
  }
}
