package org.hl7.fhir.jekyll.compare;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.hl7.fhir.jekyll.kramdown.Entities;

/**
 * Normalized-DOM comparison of two site trees: the Java form of {@code scripts/domdiff.py} (TRACKER D1), the rule the
 * shim's corpus results are measured with, so that a caller without Python (the publisher's shadow mode) can compare
 * Jekyll's output with the shim's in process.
 *
 * <p>Rules, as domdiff.py (exact mode, its default):
 * <ul>
 * <li>File sets must match. Byte-identical files are equal. {@code .html}/{@code .htm} files are parsed into a tree:
 * character references decoded, attribute names lower-cased and values stripped and compared as a map, text runs of
 * whitespace collapsed to one space and whitespace-only text dropped (except inside {@code pre}, {@code textarea},
 * {@code script}, {@code style}), leading/trailing space of a child list dropped, comments compared with collapsed
 * whitespace, declarations lower-cased, processing instructions ignored.</li>
 * <li>Rendered text files ({@code .js .css .json .xml .txt .md .svg .ttl .csv}) are compared line by line modulo
 * trailing whitespace; anything else must be byte-identical.</li>
 * </ul>
 * The HTML tokenizer follows Python's {@code html.parser} closely enough for this (tag/attribute syntax,
 * {@code script}/{@code style} raw text, comments, declarations, numeric and named references; named references are
 * those kramdown knows). Both trees go through the same parser, so a tokenizer corner case can only hide a difference
 * that is also invisible after normalization, never invent one.
 *
 * <p>CLI: {@code java -cp jekyll.jar org.hl7.fhir.jekyll.compare.DomDiff <expected-dir> <actual-dir> [--max-report N]}
 * prints domdiff.py's summary line ({@code same= differ= missing= extra= pass=}) and the first differences; exit 1 if
 * anything differs.
 */
public final class DomDiff {
  private DomDiff() {}

  static final Set<String> VOID = Set.of("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
      "param", "source", "track", "wbr");
  static final Set<String> VERBATIM = Set.of("pre", "textarea", "script", "style");
  static final Set<String> HTML_EXT = Set.of(".html", ".htm");
  static final Set<String> TEXT_EXT = Set.of(".js", ".css", ".json", ".xml", ".txt", ".md", ".svg", ".ttl", ".csv");
  private static final Pattern WS = Pattern.compile("[ \\t\\n\\r\\f\\u000B\\u001C-\\u001F\\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+");

  /** One difference: the file (relative path), where in it, and what. */
  public static final class Difference {
    public final String file;
    public final String where;
    public final String what;

    Difference(String file, String where, String what) {
      this.file = file;
      this.where = where;
      this.what = what;
    }

    @Override
    public String toString() {
      return file + " " + where + ": " + what;
    }
  }

  /** The comparison of two trees. */
  public static final class Result {
    public int same;
    public final List<String> missing = new ArrayList<>();
    public final List<String> extra = new ArrayList<>();
    public final List<Difference> differ = new ArrayList<>();

    public boolean identical() {
      return missing.isEmpty() && extra.isEmpty() && differ.isEmpty();
    }

    /** domdiff.py's summary line. */
    public String summary() {
      int total = same + differ.size() + missing.size();
      return String.format(Locale.ROOT, "same=%d differ=%d missing=%d extra=%d pass=%.1f%%", same, differ.size(),
          missing.size(), extra.size(), total == 0 ? 100.0 : same * 100.0 / total);
    }
  }

  /** Compares the tree {@code actual} with {@code expected} (both directories). */
  public static Result compareTrees(Path expected, Path actual) throws IOException {
    Set<String> fe = walk(expected);
    Set<String> fa = walk(actual);
    Result r = new Result();
    for (String f : fe) {
      if (!fa.contains(f)) {
        r.missing.add(f);
      }
    }
    for (String f : fa) {
      if (!fe.contains(f)) {
        r.extra.add(f);
      }
    }
    for (String f : fe) {
      if (!fa.contains(f)) {
        continue;
      }
      String[] d = compareFiles(expected.resolve(f), actual.resolve(f));
      if (d == null) {
        r.same++;
      } else {
        r.differ.add(new Difference(f, d[0], d[1]));
      }
    }
    return r;
  }

  private static Set<String> walk(Path dir) throws IOException {
    Set<String> out = new TreeSet<>();
    if (!Files.isDirectory(dir)) {
      return out;
    }
    try (Stream<Path> s = Files.walk(dir)) {
      s.filter(Files::isRegularFile).forEach(p -> out.add(dir.relativize(p).toString().replace('\\', '/')));
    }
    return out;
  }

  /** null if equal, else {where, what}. */
  static String[] compareFiles(Path a, Path b) throws IOException {
    byte[] ra = Files.readAllBytes(a);
    byte[] rb = Files.readAllBytes(b);
    if (Arrays.equals(ra, rb)) {
      return null;
    }
    String name = a.getFileName().toString();
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    if (HTML_EXT.contains(ext)) {
      return compareHtml(new String(ra, StandardCharsets.UTF_8), new String(rb, StandardCharsets.UTF_8));
    }
    if (TEXT_EXT.contains(ext)) {
      List<String> la = lines(new String(ra, StandardCharsets.UTF_8));
      List<String> lb = lines(new String(rb, StandardCharsets.UTF_8));
      if (la.equals(lb)) {
        return null;
      }
      for (int i = 0; i < Math.min(la.size(), lb.size()); i++) {
        if (!la.get(i).equals(lb.get(i))) {
          return new String[] {"line " + (i + 1), repr(la.get(i)) + " vs " + repr(lb.get(i))};
        }
      }
      return new String[] {"line " + (Math.min(la.size(), lb.size()) + 1), "line count " + la.size() + " vs " + lb.size()};
    }
    return new String[] {"", "binary differs (" + ra.length + " vs " + rb.length + " bytes)"};
  }

  /** Python: [l.rstrip() for l in s.rstrip().splitlines()] */
  private static List<String> lines(String s) {
    List<String> out = new ArrayList<>();
    String t = rstrip(s);
    if (t.isEmpty()) {
      return out;
    }
    for (String l : t.split("\r\n|[\n\r\u000B\f\u001C\u001D\u001E\u0085\u2028\u2029]", -1)) {
      out.add(rstrip(l));
    }
    return out;
  }

  private static String rstrip(String s) {
    int e = s.length();
    while (e > 0 && isPyWs(s.charAt(e - 1))) {
      e--;
    }
    return s.substring(0, e);
  }

  private static String lstrip(String s) {
    int b = 0;
    while (b < s.length() && isPyWs(s.charAt(b))) {
      b++;
    }
    return s.substring(b);
  }

  /** Python str.strip() */
  static String strip(String s) {
    return lstrip(rstrip(s));
  }

  private static boolean isPyWs(char c) {
    return Character.isWhitespace(c) || c == '\u00A0' || c == '\u2007' || c == '\u202F' || c == '\u0085'
        || (c >= '\u001C' && c <= '\u001F');
  }

  static String[] compareHtml(String a, String b) {
    return compare(parse(a), parse(b), "", false);
  }

  // ---------------------------------------------------------------- tree

  /** An element; children are Node, String (text) or Mark (comment/declaration). */
  static final class Node {
    final String tag;
    final Map<String, String> attrs;
    final List<Object> children = new ArrayList<>();
    final int line;

    Node(String tag, Map<String, String> attrs, int line) {
      this.tag = tag;
      this.attrs = attrs;
      this.line = line;
    }
  }

  /** ('#comment', text) or ('#decl', text). */
  static final class Mark {
    final String kind;
    final String text;

    Mark(String kind, String text) {
      this.kind = kind;
      this.text = text;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Mark && ((Mark) o).kind.equals(kind) && ((Mark) o).text.equals(text);
    }

    @Override
    public int hashCode() {
      return Objects.hash(kind, text);
    }

    @Override
    public String toString() {
      return "('" + kind + "', " + repr(text) + ")";
    }
  }

  static Node parse(String html) {
    return new TreeBuilder(html).build();
  }

  /** A tokenizer in the manner of Python's html.parser (convert_charrefs=True), feeding domdiff.py's TreeBuilder. */
  static final class TreeBuilder {
    private final String s;
    private final int n;
    private int i;
    private final Node root = new Node("#root", new LinkedHashMap<>(), 0);
    private final List<Node> stack = new ArrayList<>(List.of(root));
    private final StringBuilder pending = new StringBuilder(); // text not yet handed to handleData (refs decoded together)

    TreeBuilder(String s) {
      this.s = s;
      this.n = s.length();
    }

    private int linePos;
    private int lineNo = 1;

    /** Line of position i (getpos()[0]); positions only move forwards. */
    private int line() {
      for (; linePos < i && linePos < n; linePos++) {
        if (s.charAt(linePos) == '\n') {
          lineNo++;
        }
      }
      return lineNo;
    }

    Node build() {
      while (i < n) {
        int lt = s.indexOf('<', i);
        if (lt < 0) {
          pending.append(s, i, n);
          i = n;
          break;
        }
        pending.append(s, i, lt);
        i = lt;
        if (!markup()) {
          pending.append('<');
          i++;
        }
      }
      flush();
      return root;
    }

    private void flush() {
      if (pending.length() > 0) {
        handleData(unescape(pending.toString()));
        pending.setLength(0);
      }
    }

    /** At '<': handles a tag, comment, declaration or PI and returns true, or false if it is text. */
    private boolean markup() {
      if (s.startsWith("<!--", i)) {
        int end = s.indexOf("-->", i + 4);
        if (end < 0) {
          return false;
        }
        flush();
        handleComment(s.substring(i + 4, end));
        i = end + 3;
        return true;
      }
      if (s.startsWith("<![", i)) {
        int end = s.indexOf("]]>", i + 3);
        int gt = end < 0 ? s.indexOf('>', i) : end + 2;
        if (gt < 0) {
          return false;
        }
        flush();
        String body = end < 0 ? s.substring(i + 3, gt) : s.substring(i + 3, end);
        stack.get(stack.size() - 1).children.add(new Mark("#decl", strip(body)));
        i = gt + 1;
        return true;
      }
      if (s.startsWith("<!", i)) {
        int gt = s.indexOf('>', i + 2);
        if (gt < 0) {
          return false;
        }
        flush();
        String body = s.substring(i + 2, gt);
        if (body.regionMatches(true, 0, "doctype", 0, 7)) {
          stack.get(stack.size() - 1).children.add(new Mark("#decl", strip(body).toLowerCase(Locale.ROOT)));
        } else {
          handleComment(body); // bogus comment (Python: parse_bogus_comment -> handle_comment)
        }
        i = gt + 1;
        return true;
      }
      if (s.startsWith("<?", i)) {
        int gt = s.indexOf('>', i + 2);
        if (gt < 0) {
          return false;
        }
        flush(); // processing instruction: domdiff.py's builder ignores it
        i = gt + 1;
        return true;
      }
      if (s.startsWith("</", i)) {
        int j = i + 2;
        if (j < n && Character.isLetter(s.charAt(j))) {
          int k = j;
          while (k < n && !Character.isWhitespace(s.charAt(k)) && s.charAt(k) != '>' && s.charAt(k) != '/') {
            k++;
          }
          int gt = s.indexOf('>', k);
          if (gt < 0) {
            return false;
          }
          flush();
          handleEndtag(s.substring(j, k).toLowerCase(Locale.ROOT));
          i = gt + 1;
          return true;
        }
        int gt = s.indexOf('>', j);
        if (gt < 0) {
          return false;
        }
        flush();
        if (gt > j) {
          handleComment(s.substring(j, gt)); // </ followed by a non-letter: bogus comment
        }
        i = gt + 1;
        return true;
      }
      if (i + 1 < n && Character.isLetter(s.charAt(i + 1))) {
        return startTag();
      }
      return false;
    }

    private boolean startTag() {
      int j = i + 1;
      int k = j;
      while (k < n && !Character.isWhitespace(s.charAt(k)) && s.charAt(k) != '>' && s.charAt(k) != '/') {
        k++;
      }
      String tag = s.substring(j, k).toLowerCase(Locale.ROOT);
      Map<String, String> attrs = new TreeMap<>();
      int p = k;
      boolean selfClosing = false;
      while (true) {
        while (p < n && (Character.isWhitespace(s.charAt(p)) || s.charAt(p) == '/')) {
          if (s.charAt(p) == '/' && p + 1 < n && s.charAt(p + 1) == '>') {
            selfClosing = true;
          }
          p++;
        }
        if (p >= n) {
          return false; // unterminated tag: text (Python keeps it as data at the end)
        }
        if (s.charAt(p) == '>') {
          break;
        }
        int a = p;
        while (p < n && !Character.isWhitespace(s.charAt(p)) && s.charAt(p) != '>' && s.charAt(p) != '=' && !(s.charAt(p) == '/' && p + 1 < n && s.charAt(p + 1) == '>')) {
          p++;
        }
        String name = s.substring(a, p).toLowerCase(Locale.ROOT);
        int q = p;
        while (q < n && Character.isWhitespace(s.charAt(q))) {
          q++;
        }
        String value = null;
        if (q < n && s.charAt(q) == '=') {
          q++;
          while (q < n && Character.isWhitespace(s.charAt(q))) {
            q++;
          }
          if (q < n && (s.charAt(q) == '"' || s.charAt(q) == '\'')) {
            int e = s.indexOf(s.charAt(q), q + 1);
            if (e < 0) {
              return false;
            }
            value = s.substring(q + 1, e);
            p = e + 1;
          } else {
            int e = q;
            while (e < n && !Character.isWhitespace(s.charAt(e)) && s.charAt(e) != '>') {
              e++;
            }
            value = s.substring(q, e);
            p = e;
          }
        }
        if (!name.isEmpty() && !attrs.containsKey(name)) {
          attrs.put(name, value == null ? "" : strip(unescape(value)));
        }
      }
      flush();
      int line = line();
      i = p + 1;
      Node node = new Node(tag, attrs, line);
      stack.get(stack.size() - 1).children.add(node);
      if (selfClosing) {
        return true; // handle_startendtag: never pushed
      }
      if (!VOID.contains(tag)) {
        stack.add(node);
      }
      if (tag.equals("script") || tag.equals("style")) { // raw text up to </tag
        int end = indexOfIgnoreCase(s, "</" + tag, i);
        String raw = end < 0 ? s.substring(i) : s.substring(i, end);
        if (!raw.isEmpty()) {
          handleData(raw);
        }
        i = end < 0 ? n : end;
      }
      return true;
    }

    private static int indexOfIgnoreCase(String s, String what, int from) {
      for (int k = from; k + what.length() <= s.length(); k++) {
        if (s.regionMatches(true, k, what, 0, what.length())) {
          return k;
        }
      }
      return -1;
    }

    private boolean verbatim() {
      for (Node x : stack) {
        if (VERBATIM.contains(x.tag)) {
          return true;
        }
      }
      return false;
    }

    private void handleEndtag(String tag) {
      if (VOID.contains(tag)) {
        return;
      }
      for (int k = stack.size() - 1; k > 0; k--) { // tolerant: close up to the matching open tag
        if (stack.get(k).tag.equals(tag)) {
          while (stack.size() > k) {
            stack.remove(stack.size() - 1);
          }
          return;
        }
      }
    }

    private void handleData(String data) {
      boolean vb = verbatim();
      if (!vb) {
        data = WS.matcher(data).replaceAll(" ");
        if (data.equals(" ") || data.isEmpty()) {
          return;
        }
      }
      List<Object> kids = stack.get(stack.size() - 1).children;
      if (!kids.isEmpty() && kids.get(kids.size() - 1) instanceof String) {
        String t = kids.get(kids.size() - 1) + data;
        kids.set(kids.size() - 1, vb ? t : WS.matcher(t).replaceAll(" "));
      } else {
        kids.add(data);
      }
    }

    private void handleComment(String data) {
      stack.get(stack.size() - 1).children.add(new Mark("#comment", strip(WS.matcher(data).replaceAll(" "))));
    }
  }

  /** html.unescape for numeric references and the named references kramdown knows. */
  static String unescape(String s) {
    if (s.indexOf('&') < 0) {
      return s;
    }
    StringBuilder b = new StringBuilder(s.length());
    int n = s.length();
    for (int i = 0; i < n; i++) {
      char c = s.charAt(i);
      if (c != '&') {
        b.append(c);
        continue;
      }
      int j = i + 1;
      if (j < n && s.charAt(j) == '#') {
        int k = j + 1;
        boolean hex = k < n && (s.charAt(k) == 'x' || s.charAt(k) == 'X');
        if (hex) {
          k++;
        }
        int st = k;
        while (k < n && (hex ? Character.digit(s.charAt(k), 16) >= 0 : Character.isDigit(s.charAt(k)))) {
          k++;
        }
        if (k > st) {
          long cp;
          try {
            cp = Long.parseLong(s.substring(st, Math.min(k, st + 8)), hex ? 16 : 10);
          } catch (NumberFormatException e) {
            cp = 0xFFFD;
          }
          if (cp == 0 || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
            cp = 0xFFFD;
          }
          b.appendCodePoint((int) cp);
          i = (k < n && s.charAt(k) == ';') ? k : k - 1;
          continue;
        }
      } else {
        int k = j;
        while (k < n && Character.isLetterOrDigit(s.charAt(k)) && k - j < 40) {
          k++;
        }
        if (k > j && k < n && s.charAt(k) == ';') {
          String name = s.substring(j, k);
          String ch = named(name);
          if (ch != null) {
            b.append(ch);
            i = k;
            continue;
          }
        }
      }
      b.append(c);
    }
    return b.toString();
  }

  private static String named(String name) {
    try {
      return Entities.entity(name).chr();
    } catch (RuntimeException e) {
      return null;
    }
  }

  // ---------------------------------------------------------------- comparison

  private static List<Object> normChildren(List<Object> children) {
    List<Object> out = new ArrayList<>(children);
    if (!out.isEmpty() && out.get(0) instanceof String) {
      String t = lstrip((String) out.get(0));
      if (t.isEmpty()) {
        out.remove(0);
      } else {
        out.set(0, t);
      }
    }
    if (!out.isEmpty() && out.get(out.size() - 1) instanceof String) {
      String t = rstrip((String) out.get(out.size() - 1));
      if (t.isEmpty()) {
        out.remove(out.size() - 1);
      } else {
        out.set(out.size() - 1, t);
      }
    }
    return out;
  }

  static String[] compare(Object a, Object b, String path, boolean verbatim) {
    if (a instanceof String || b instanceof String) {
      return Objects.equals(a, b) ? null : new String[] {path, "text differs: " + repr(short_(a)) + " vs " + repr(short_(b))};
    }
    if (a instanceof Mark || b instanceof Mark) {
      return Objects.equals(a, b) ? null : new String[] {path, short_(a) + " vs " + short_(b)};
    }
    Node x = (Node) a;
    Node y = (Node) b;
    if (!x.tag.equals(y.tag)) {
      return new String[] {path, "tag <" + x.tag + "> vs <" + y.tag + ">"};
    }
    String here = path + "/" + x.tag;
    if (!x.attrs.equals(y.attrs)) {
      List<String> diffs = new ArrayList<>();
      Set<String> keys = new TreeSet<>(x.attrs.keySet());
      keys.addAll(y.attrs.keySet());
      for (String k : keys) {
        if (!Objects.equals(x.attrs.get(k), y.attrs.get(k)) && diffs.size() < 3) {
          diffs.add(k + ": " + repr(x.attrs.get(k)) + " vs " + repr(y.attrs.get(k)));
        }
      }
      return new String[] {here + "@L" + x.line, "attrs " + String.join("; ", diffs)};
    }
    boolean vb = verbatim || VERBATIM.contains(x.tag);
    List<Object> ac = vb ? x.children : normChildren(x.children);
    List<Object> bc = vb ? y.children : normChildren(y.children);
    for (int k = 0; k < Math.min(ac.size(), bc.size()); k++) {
      String[] r = compare(ac.get(k), bc.get(k), here + "[" + k + "]", vb);
      if (r != null) {
        return r;
      }
    }
    if (ac.size() != bc.size()) {
      Object extra = (ac.size() > bc.size() ? ac : bc).get(Math.min(ac.size(), bc.size()));
      return new String[] {here + "@L" + x.line, "child count " + ac.size() + " vs " + bc.size() + "; first extra: " + repr(short_(extra))};
    }
    return null;
  }

  private static String short_(Object o) {
    String s;
    if (o instanceof Node) {
      Node x = (Node) o;
      s = x.attrs.isEmpty() ? "<" + x.tag + ">" : "<" + x.tag + " " + x.attrs + ">";
    } else {
      s = String.valueOf(o);
    }
    return s.length() <= 90 ? s : s.substring(0, 90) + "…";
  }

  private static String repr(String s) {
    return s == null ? "None" : "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
        .replace("\t", "\\t") + "'";
  }

  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      System.err.println("usage: DomDiff <expected-dir> <actual-dir> [--max-report N]");
      System.exit(2);
    }
    int max = 25;
    for (int k = 2; k + 1 < args.length; k++) {
      if (args[k].equals("--max-report")) {
        max = Integer.parseInt(args[k + 1]);
      }
    }
    Result r = compareTrees(Path.of(args[0]), Path.of(args[1]));
    System.out.println(r.summary());
    if (!r.missing.isEmpty()) {
      System.out.println("  missing: " + String.join(", ", r.missing.subList(0, Math.min(10, r.missing.size()))) + (r.missing.size() > 10 ? " …" : ""));
    }
    if (!r.extra.isEmpty()) {
      System.out.println("  extra: " + String.join(", ", r.extra.subList(0, Math.min(10, r.extra.size()))) + (r.extra.size() > 10 ? " …" : ""));
    }
    for (Difference d : r.differ.subList(0, Math.min(max, r.differ.size()))) {
      System.out.println("  DIFF " + d);
    }
    System.exit(r.identical() ? 0 : 1);
  }
}
