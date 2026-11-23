package org.hl7.fhir.jekyll.test.site;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hl7.fhir.jekyll.compare.DomDiff;

/**
 * Checks of org.hl7.fhir.jekyll.compare.DomDiff (the Java form of scripts/domdiff.py): the pairs of domdiff.py's
 * --selftest that hold in its exact (default) mode, a few tokenizer cases, and a tree comparison (missing, extra,
 * text-file and binary rules). Exits 1 on the first failure.
 * usage: java -cp jekyll.jar:test-classes org.hl7.fhir.jekyll.test.site.DomDiffCheck
 */
public final class DomDiffCheck {
  private static int checks;

  public static void main(String[] args) throws Exception {
    String[][] equal = {
        {"<p>a  b</p>", "<p>a\nb</p>"},
        {"<div class=\"x\" id=\"y\">t</div>", "<div id=\"y\" class=\"x\">t</div>"},
        {"<p>&quot;hi&quot; &amp;</p>", "<p>\"hi\" &amp;</p>"},
        {"<ul>\n  <li>x</li>\n</ul>", "<ul><li>x</li></ul>"},
        {"<br/>", "<br>"},
        {"<p>\n x </p>", "<p>x</p>"},
        {"<P CLASS=\"a\">x</P>", "<p class=\"a\">x</p>"},
        {"<p>&#233;&eacute;&#xE9;</p>", "<p>ééé</p>"},
        {"<!--  a   b -->", "<!-- a b -->"},
        {"<!DOCTYPE html>", "<!doctype html>"},
        {"<a title=' t '>x</a>", "<a title=\"t\">x</a>"},
        {"<?xml version=\"1.0\"?><p>x</p>", "<p>x</p>"},
        {"<script>if (a<b) x();</script>", "<script>if (a<b) x();</script>"},
    };
    String[][] different = {
        {"<p>a b</p>", "<p>ab</p>"},
        {"<pre>a  b</pre>", "<pre>a b</pre>"},
        {"<h2 id=\"a\">x</h2>", "<h2 id=\"b\">x</h2>"},
        {"<p><em>a</em> b</p>", "<p><em>a</em>b</p>"},
        {"<ul><li>x</li></ul>", "<ol><li>x</li></ol>"},
        {"<pre class=\"highlight\"><code>{}</code></pre>", "<pre class=\"highlight\"><code>{ }</code></pre>"},
        {"<div class=\"language-json highlighter-rouge\"></div>", "<div class=\"language-json\"></div>"},
        {"<pre class=\"highlight\"><code><span class=\"p\">{</span>}</code></pre>", "<pre class=\"highlight\"><code>{}</code></pre>"},
        {"<script>a  b</script>", "<script>a b</script>"},
        {"<!-- a -->", "<!-- b -->"},
        {"<p>x</p><p>y</p>", "<p>x</p>"},
    };
    for (String[] p : equal) {
      String[] r = compare(p[0], p[1]);
      check(r == null, "expected equal: " + p[0] + " | " + p[1] + " -> " + (r == null ? "" : r[0] + ": " + r[1]));
    }
    for (String[] p : different) {
      check(compare(p[0], p[1]) != null, "expected different: " + p[0] + " | " + p[1]);
    }

    Path root = Files.createTempDirectory("domdiff");
    Path a = root.resolve("a");
    Path b = root.resolve("b");
    write(a.resolve("same.html"), "<p>a  b</p>");
    write(b.resolve("same.html"), "<p>a\nb</p>");
    write(a.resolve("d/diff.html"), "<p>a</p>");
    write(b.resolve("d/diff.html"), "<p>b</p>");
    write(a.resolve("t.json"), "{\n  \"a\": 1   \n}\n\n");
    write(b.resolve("t.json"), "{\n  \"a\": 1\n}");
    write(a.resolve("bin.png"), "x");
    write(b.resolve("bin.png"), "y");
    write(a.resolve("only-a.css"), "x");
    write(b.resolve("only-b.css"), "x");
    DomDiff.Result r = DomDiff.compareTrees(a, b);
    check(r.same == 2 && r.differ.size() == 2 && r.missing.equals(java.util.List.of("only-a.css"))
        && r.extra.equals(java.util.List.of("only-b.css")), "tree: " + r.summary() + " " + r.differ);
    check(r.summary().equals("same=2 differ=2 missing=1 extra=1 pass=40.0%"), "summary line: " + r.summary());
    check(r.differ.get(0).file.equals("bin.png") && r.differ.get(1).file.equals("d/diff.html"), "differ order: " + r.differ);
    System.out.println("DomDiffCheck: " + checks + " checks passed");
  }

  private static String[] compare(String x, String y) throws Exception {
    Path root = Files.createTempDirectory("domdiff-pair");
    write(root.resolve("a/x.html"), x);
    write(root.resolve("b/x.html"), y);
    DomDiff.Result r = DomDiff.compareTrees(root.resolve("a"), root.resolve("b"));
    return r.differ.isEmpty() ? null : new String[] {r.differ.get(0).where, r.differ.get(0).what};
  }

  private static void check(boolean ok, String what) {
    checks++;
    if (!ok) {
      System.out.println("FAIL: " + what);
      System.exit(1);
    }
  }

  private static void write(Path p, String s) throws Exception {
    Files.createDirectories(p.getParent());
    Files.writeString(p, s, StandardCharsets.UTF_8);
  }
}
