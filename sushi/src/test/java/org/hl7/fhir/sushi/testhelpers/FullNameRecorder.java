package org.hl7.fhir.sushi.testhelpers;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * Records each finished test's Jest-style fullName (display names of its @Nested classes and of the method,
 * space-joined; the top-level class is left out) to {@code <build dir>/test-fullnames.tsv} as
 * {@code status<TAB>fullName<TAB>class#method}. Surefire's XML only carries method names, so
 * scripts/test-inventory.py --coverage can read this file. Auto-registered (junit-platform.properties +
 * META-INF/services/org.junit.jupiter.api.extension.Extension).
 */
public final class FullNameRecorder implements TestWatcher {
  private static final Path OUT = outFile();

  private static Path outFile() {
    try {
      Path testClasses = Path.of(FullNameRecorder.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      return testClasses.getParent().resolve("test-fullnames.tsv");
    } catch (Exception e) {
      return null;
    }
  }

  @Override public void testSuccessful(ExtensionContext context) {
    write("passed", context);
  }

  @Override public void testFailed(ExtensionContext context, Throwable cause) {
    write("failed", context);
  }

  @Override public void testAborted(ExtensionContext context, Throwable cause) {
    write("skipped", context);
  }

  @Override public void testDisabled(ExtensionContext context, Optional<String> reason) {
    write("skipped", context);
  }

  private static synchronized void write(String status, ExtensionContext context) {
    if (OUT == null) return;
    Deque<String> parts = new ArrayDeque<>();
    for (ExtensionContext c = context; c != null && c.getParent().isPresent(); c = c.getParent().orElse(null)) {
      boolean topLevelClass = c.getTestMethod().isEmpty() && c.getTestClass().map(k -> k.getEnclosingClass() == null)
          .orElse(false);
      if (!topLevelClass) parts.addFirst(c.getDisplayName());
    }
    String where = context.getTestClass().map(Class::getName).orElse("?") + "#"
        + context.getTestMethod().map(Method::getName).orElse("?");
    String line = status + "\t" + String.join(" ", parts) + "\t" + where + "\n";
    try {
      Files.writeString(OUT, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      // best effort
    }
  }
}
