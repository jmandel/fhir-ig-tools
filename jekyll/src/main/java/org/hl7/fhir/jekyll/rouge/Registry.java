package org.hl7.fhir.jekyll.rouge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Rouge::Lexer's class-level registry (<code>registry</code>, <code>all</code>) for Rouge 4.7.0, built
 * from {@link LexerData}, plus the constructors of the ported lexers (org.hl7.fhir.jekyll.rouge.lexers).
 */
final class Registry {
  private Registry() {}

  /** Lexer.all (registry.values.uniq, in load order) */
  static final List<LexerClass> ALL;
  /** Lexer.registry: name (tag or alias) -> class */
  static final Map<String, LexerClass> BY_NAME;
  static final Map<String, LexerClass> BY_CLASS;

  static {
    Map<String, LexerClass> byClass = new HashMap<>();
    List<LexerClass> all = new ArrayList<>();
    for (Object[] a : LexerData.ALL) {
      String cls = (String) a[0];
      LexerClass lc = new LexerClass(cls, (String) a[1], List.of((String[]) a[2]), List.of((String[]) a[3]),
          List.of((String[]) a[4]), (Boolean) a[5], (String) a[6], Detectors.DETECT.get(cls));
      byClass.put(cls, lc);
      all.add(lc);
    }
    Map<String, LexerClass> byName = new LinkedHashMap<>();
    for (String[] r : LexerData.REGISTRY) {
      byName.put(r[0], byClass.get(r[1]));
    }
    Map<String, Function<Map<String, Object>, Lexer>> ported = new HashMap<>();
    org.hl7.fhir.jekyll.rouge.lexers.Ported.register(ported);
    for (Map.Entry<String, Function<Map<String, Object>, Lexer>> e : ported.entrySet()) {
      LexerClass lc = byClass.get(e.getKey());
      if (lc == null) {
        throw new IllegalStateException("no Rouge lexer class " + e.getKey());
      }
      lc.factory = e.getValue();
    }
    ALL = Collections.unmodifiableList(all);
    BY_NAME = Collections.unmodifiableMap(byName);
    BY_CLASS = Collections.unmodifiableMap(byClass);
  }
}
