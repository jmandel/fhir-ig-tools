package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Ruby <code>Set.new %w(...)</code> (duplicates allowed). */
final class Words {
  private Words() {}

  static Set<String> set(String... words) {
    return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(words)));
  }

  /** %w(...) given as one whitespace-separated string */
  static String[] w(String words) {
    String t = words.strip();
    return t.isEmpty() ? new String[0] : t.split("\\s+");
  }
}
