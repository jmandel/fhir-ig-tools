package org.hl7.fhir.jekyll.test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.Tok;

/**
 * Debug helper: lexes one input and prints the tokens (or the error with stack frames).
 * usage: RougeDebug &lt;lang&gt; &lt;file&gt; | RougeDebug --case &lt;cases.jsonl&gt; &lt;id&gt;
 */
public final class RougeDebug {
  public static void main(String[] args) throws Exception {
    String lang, text;
    if (args[0].equals("--case")) {
      Map<String, Object> c = null;
      for (Map<String, Object> m : DiffRunner.readJsonl(Path.of(args[1]))) {
        if (m.get("id").equals(args[2])) {
          c = m;
        }
      }
      lang = (String) c.get("lang");
      text = (String) c.get("text");
    } else {
      lang = args[0];
      text = Files.readString(Path.of(args[1]), StandardCharsets.UTF_8);
    }
    long t0 = System.nanoTime();
    try {
      Lexer l = Lexer.findFancy(lang, text);
      List<Tok> toks = l.lex(text);
      System.err.printf("%s: %d tokens, %d chars, %.1f ms%n", l.tag(), toks.size(), text.length(), (System.nanoTime() - t0) / 1e6);
      if (args.length < 4) {
        for (Tok t : toks.subList(0, Math.min(toks.size(), 200))) {
          System.out.println(t.token().qualname + " " + org.hl7.fhir.jekyll.util.Json.write(t.val()));
        }
      }
    } catch (Throwable e) {
      StackTraceElement[] st = e.getStackTrace();
      System.out.println(e + " depth=" + st.length);
      java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
      for (StackTraceElement s : st) {
        counts.merge(s.getClassName() + "." + s.getMethodName(), 1, Integer::sum);
      }
      counts.entrySet().stream().limit(25).forEach(en -> System.out.println("  " + en.getValue() + " " + en.getKey()));
      for (int k = st.length - 1; k >= Math.max(0, st.length - 30); k--) {
        System.out.println("  at " + st[k]);
      }
    }
  }
}
