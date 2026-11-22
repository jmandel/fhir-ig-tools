package org.hl7.fhir.jekyll.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.hl7.fhir.jekyll.util.Json;

/**
 * Generic differential test runner: reads oracle cases + expected outputs (JSONL), renders each
 * case with a Java implementation, and compares exactly.
 *
 * usage: DiffRunner &lt;cases.jsonl&gt; &lt;expected.jsonl&gt; [--filter substr] [--show N] [--report out.jsonl] [--d7] [--known file]
 * <p>
 * <code>--known file</code>: ids expected to differ (first token of each line; lines starting with <code>#</code>
 * give the reason; blank lines ignored). They count as <code>known</code>, are not shown, and don't fail the run; a known id that
 * passes is reported as <code>fixed</code> so the list can be trimmed. Any other failure still fails the run.
 *
 * <code>--no-d7</code> (or env SHIM_NO_D7=1) disables the D7 rule (Rouge token spans stripped from
 * highlighted code before comparing), so outputs must be byte-identical including the spans.
 *
 * Implementations register per case "type" in {@link #IMPLS}; each maps a case object to a result
 * map with "output" (String) or "error" (String), mirroring oracle.rb. For "error" results only the
 * presence of an error is compared, not the message text.
 */
public final class DiffRunner {
  public static final Map<String, Function<Map<String, Object>, Map<String, Object>>> IMPLS = new LinkedHashMap<>();

  static {
    // Implementations self-register via reflection so this runner compiles before they exist.
    for (String cls : new String[] {"org.hl7.fhir.jekyll.test.LiquidCaseRunner", "org.hl7.fhir.jekyll.test.KramdownCaseRunner",
        "org.hl7.fhir.jekyll.test.SiteCaseRunner", "org.hl7.fhir.jekyll.test.YamlCaseRunner",
        "org.hl7.fhir.jekyll.test.RougeCaseRunner"}) {
      try {
        Class.forName(cls);
      } catch (ClassNotFoundException e) {
        // not built yet
      }
    }
  }

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> readJsonl(Path p) throws IOException {
    List<Map<String, Object>> out = new ArrayList<>();
    for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
      if (!line.isBlank()) {
        out.add((Map<String, Object>) Json.parse(line));
      }
    }
    return out;
  }

  public static void main(String[] args) throws IOException {
    Path cases = Path.of(args[0]);
    Path expected = Path.of(args[1]);
    String filter = null;
    int show = 10;
    Path report = null;
    java.util.Set<String> known = new java.util.HashSet<>();
    // Exact comparison by default (D7 reversed 2026-11-19: Rouge spans are reproduced). --d7 / SHIM_D7=1
    // re-enables the old text-only rule for highlighted code (diagnostics only).
    boolean noD7 = !"1".equals(System.getenv("SHIM_D7"));
    for (int k = 2; k < args.length; k++) {
      switch (args[k]) {
        case "--filter": filter = args[++k]; break;
        case "--show": show = Integer.parseInt(args[++k]); break;
        case "--report": report = Path.of(args[++k]); break;
        case "--no-d7": noD7 = true; break;
        case "--d7": noD7 = false; break;
        case "--known":
          for (String line : Files.readAllLines(Path.of(args[++k]), StandardCharsets.UTF_8)) {
            String t = line.trim(); // ids may contain '#', so comments are whole lines only
            if (!t.isEmpty() && !t.startsWith("#")) {
              known.add(t.split("\\s+", 2)[0]);
            }
          }
          break;
        default: throw new IllegalArgumentException(args[k]);
      }
    }
    Map<String, Map<String, Object>> exp = new LinkedHashMap<>();
    for (Map<String, Object> e : readJsonl(expected)) {
      exp.put((String) e.get("id"), e);
    }
    int pass = 0, fail = 0, crash = 0, shown = 0, knownFail = 0;
    java.util.List<String> fixed = new java.util.ArrayList<>();
    StringBuilder rep = new StringBuilder();
    for (Map<String, Object> c : readJsonl(cases)) {
      String id = (String) c.get("id");
      if (filter != null && !id.contains(filter)) {
        continue;
      }
      Function<Map<String, Object>, Map<String, Object>> impl = IMPLS.get((String) c.get("type"));
      if (impl == null) {
        throw new IllegalStateException("no implementation registered for type " + c.get("type"));
      }
      Map<String, Object> want = exp.get(id);
      Map<String, Object> got;
      try {
        got = impl.apply(c);
      } catch (RuntimeException | StackOverflowError ex) {
        got = Map.of("error", "CRASH " + ex);
        crash++;
      }
      boolean ok;
      if (want.containsKey("error")) {
        ok = got.containsKey("error");
      } else {
        String w = String.valueOf(want.get("output")), g = String.valueOf(got.get("output"));
        ok = !got.containsKey("error") && (noD7 ? w.equals(g) : stripRougeSpans(w).equals(stripRougeSpans(g)));
      }
      if (ok) {
        pass++;
        if (known.contains(id)) {
          fixed.add(id);
        }
      } else if (known.contains(id)) {
        knownFail++;
      } else {
        fail++;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("want", want.containsKey("error") ? "ERROR " + want.get("error") : want.get("output"));
        r.put("got", got.containsKey("error") ? "ERROR " + got.get("error") : got.get("output"));
        rep.append(Json.write(r)).append('\n');
        if (shown++ < show) {
          System.out.println("FAIL " + id);
          System.out.println("  want: " + abbreviate(String.valueOf(r.get("want"))));
          System.out.println("  got:  " + abbreviate(String.valueOf(r.get("got"))));
          System.out.println("  first difference at char " + firstDiff(String.valueOf(r.get("want")), String.valueOf(r.get("got"))));
        }
      }
    }
    if (report != null) {
      Files.writeString(report, rep.toString(), StandardCharsets.UTF_8);
    }
    for (String f : fixed) {
      System.out.println("FIXED (listed as known, now passes) " + f);
    }
    System.out.printf(java.util.Locale.ROOT, "pass=%d fail=%d (crashes=%d) total=%d%s%n", pass, fail, crash, pass + fail + knownFail,
        known.isEmpty() ? "" : " known=" + knownFail + (fixed.isEmpty() ? "" : " fixed=" + fixed.size()));
    System.exit(fail == 0 ? 0 : 1);
  }

  // kramdown fenced code: <pre class="highlight"><code>…; Jekyll {% highlight %}: <figure class="highlight"><pre><code class="language-x" data-lang="x">…
  private static final java.util.regex.Pattern HIGHLIGHT_CODE = java.util.regex.Pattern.compile(
      "(<pre class=\"highlight\"><code>|<figure class=\"highlight\"><pre><code [^>]*data-lang=\"[^\"]*\">)(.*?)(</code></pre>)",
      java.util.regex.Pattern.DOTALL);

  /** Decision D7: Rouge token spans are not reproduced; compare highlighted code by text only. */
  // kramdown inline codespans highlighted by Rouge: <code class="language-x highlighter-rouge">…</code>
  private static final java.util.regex.Pattern INLINE_HIGHLIGHT = java.util.regex.Pattern.compile(
      "(<code class=\"[^\"]*highlighter-rouge[^\"]*\">)(.*?)(</code>)", java.util.regex.Pattern.DOTALL);

  static String stripRougeSpans(String html) {
    html = strip(INLINE_HIGHLIGHT, html);
    return strip(HIGHLIGHT_CODE, html);
  }

  private static String strip(java.util.regex.Pattern p, String html) {
    java.util.regex.Matcher m = p.matcher(html);
    StringBuilder b = new StringBuilder();
    while (m.find()) {
      String inner = m.group(2).replaceAll("<span class=\"[^\"]*\">", "").replace("</span>", "");
      m.appendReplacement(b, java.util.regex.Matcher.quoteReplacement(m.group(1) + inner + m.group(3)));
    }
    m.appendTail(b);
    return b.toString();
  }

  static String abbreviate(String s) {
    s = s.replace("\n", "\\n");
    return s.length() > 400 ? s.substring(0, 400) + "…" : s;
  }

  static String firstDiff(String a, String b) {
    int n = Math.min(a.length(), b.length());
    for (int k = 0; k < n; k++) {
      if (a.charAt(k) != b.charAt(k)) {
        int from = Math.max(0, k - 40);
        return k + ": want …" + abbreviate(a.substring(from, Math.min(a.length(), k + 60))) + " | got …"
            + abbreviate(b.substring(from, Math.min(b.length(), k + 60)));
      }
    }
    return n + " (length " + a.length() + " vs " + b.length() + ")";
  }
}
