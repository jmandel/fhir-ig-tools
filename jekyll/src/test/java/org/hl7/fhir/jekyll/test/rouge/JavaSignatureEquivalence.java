package org.hl7.fhir.jekyll.test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.jekyll.rouge.lexers.Java;
import org.hl7.fhir.jekyll.rouge.lexers.JavaSignaturePrecheck;

/**
 * Proves JavaSignaturePrecheck exact: at every position of every input, "the method-signature
 * regex matches here (StringScanner semantics)" == "the precheck says it may match".
 * Inputs: the given files and the "text" of "java" cases in the given .jsonl files, plus random
 * strings over the rule's alphabet. usage: JavaSignatureEquivalence &lt;randomCount&gt; [files...]
 */
public final class JavaSignatureEquivalence {
  public static void main(String[] args) throws Exception {
    List<String> inputs = new ArrayList<>();
    for (int k = 1; k < args.length; k++) {
      Path p = Path.of(args[k]);
      if (args[k].endsWith(".jsonl")) {
        for (Map<String, Object> c : DiffRunner.readJsonl(p)) {
          if ("java".equals(c.get("lang")) && ((String) c.get("text")).length() < 30000) {
            inputs.add((String) c.get("text"));
          }
        }
      } else {
        inputs.add(Files.readString(p, StandardCharsets.UTF_8));
      }
    }
    int fileInputs = inputs.size();
    Random r = new Random(42);
    String[] pieces = {" ", "  ", "\n", "\t", "\r\n", "\u000b", "\f", "(", ")", "a", "Foo", "x1", "_", "_a", "1", "9x", ".", "a.b",
        "[]", "<T>", "<", ">", "[", "]", "List<String>", "int", "void", "public", "static", "f(", " (", "é", "{", ";", "=",
        "\"", "//", "/*", "*/", "@", "throws", "new", ",", "-"};
    int randomCount = Integer.parseInt(args[0]);
    for (int k = 0; k < randomCount; k++) {
      StringBuilder b = new StringBuilder();
      int n = 1 + r.nextInt(60);
      for (int i = 0; i < n; i++) {
        b.append(pieces[r.nextInt(pieces.length)]);
      }
      inputs.add(b.toString());
    }
    Pattern re = Java.SIGNATURE.pattern();
    long positions = 0, matches = 0, falseNeg = 0, falsePos = 0;
    for (String s : inputs) {
      Matcher m = re.matcher(s);
      m.useAnchoringBounds(true);
      m.useTransparentBounds(false);
      JavaSignaturePrecheck.Info info = JavaSignaturePrecheck.info(s);
      for (int p = 0; p < s.length(); p++) {
        m.region(p, s.length());
        boolean rx = m.lookingAt();
        boolean pre = info.mayMatch(p);
        positions++;
        if (rx) {
          matches++;
        }
        if (rx && !pre) {
          if (falseNeg++ < 5) {
            System.out.println("UNSOUND at " + p + ": " + org.hl7.fhir.jekyll.util.Json.write(s.substring(p, Math.min(s.length(), p + 80))));
          }
        } else if (pre && !rx) {
          if (falsePos++ < 5) {
            System.out.println("INEXACT at " + p + ": " + org.hl7.fhir.jekyll.util.Json.write(s.substring(p, Math.min(s.length(), p + 80))));
          }
        }
      }
    }
    System.out.printf("inputs=%d (%d from files, %d random) positions=%d regex-matches=%d unsound=%d inexact=%d%n",
        inputs.size(), fileInputs, randomCount, positions, matches, falseNeg, falsePos);
    System.exit(falseNeg == 0 && falsePos == 0 ? 0 : 1);
  }
}
