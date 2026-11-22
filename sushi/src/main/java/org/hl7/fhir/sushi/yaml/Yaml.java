package org.hl7.fhir.sushi.yaml;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.JsNumber;
import org.hl7.fhir.sushi.js.JsObject;

/**
 * {@code YAML.parse(src)} of the npm package yaml@1.10 (eemeli/yaml 1.10.3, the version SUSHI 3.21.1 ships) with its
 * default options: YAML 1.2 core schema, {@code mapAsMap: false}, {@code merge: false}, {@code maxAliasCount: 100}.
 * Values are the shared JS value model ({@code js} package): mappings {@link JsObject}, sequences {@link List}, numbers
 * {@link Double}, booleans, strings, null.
 *
 * <p>The syntax layer is the libyaml 0.2.5 event parser shared with the Jekyll shim ({@link Parser}); this class is
 * the composer yaml@1.10 would be on top of it: core-schema scalar resolution (plain scalars only; quoted and block
 * scalars are strings; {@code yes/no/on/off} and timestamps stay strings; {@code 0o} octal, {@code 0x} hex,
 * {@code .inf/.nan}), explicit tags resolved like yaml 1.10 ({@code !!int x} is "x", unknown tags fall back to str
 * with a warning), anchors/aliases (an alias is the same JS object; circular references allowed), keys stringified
 * like yaml's {@code stringifyKey}, and yaml 1.10's semantic errors with its wording: duplicate keys, unknown aliases,
 * multiple documents, excessive aliases.
 *
 * <p>Where libyaml rejects input, the message is libyaml's problem text mapped to yaml 1.10's wording where the
 * error classes correspond ({@link #syntaxMessage}); libyaml and yaml 1.10 do not accept exactly the same malformed
 * inputs (WP-3 mutation report).
 */
public final class Yaml {
  private Yaml() {}

  static final String TAG_PREFIX = "tag:yaml.org,2002:";
  static final String STR = TAG_PREFIX + "str", MAP = TAG_PREFIX + "map", SEQ = TAG_PREFIX + "seq";

  /** yaml@1.10 {@code YAML.parse(src)}: the first document's JS value; throws {@link YamlError}. */
  public static Object parse(String src) {
    return parse(src, null);
  }

  /**
   * As {@link #parse(String)}; yaml 1.10's warnings (printed by node as process warnings on stderr, not part of
   * SUSHI's log) are added to {@code warnings} when it is not null.
   */
  public static Object parse(String src, List<String> warnings) {
    Composer c = new Composer(warnings);
    return c.compose(src, src.getBytes(StandardCharsets.UTF_8));
  }

  // ---------------------------------------------------------------- composer

  /** A resolved node (yaml's Scalar / YAMLMap / YAMLSeq / Alias), kept for duplicate-key and alias-count checks. */
  private static final class Node {
    final boolean collection;
    /** Scalar.value (own `value` property); collections and aliases have none */
    final boolean hasValue;
    final Object value;
    /** for aliases: the anchored node */
    final Node aliasOf;
    final String aliasName;
    /** child nodes in document order (pairs: key then value) */
    final List<Node> children;
    /** the JS value this node converts to */
    Object js;
    /** a collection key stringifies as flow YAML: kept to render it */
    String yamlType;

    private Node(boolean collection, boolean hasValue, Object value, Node aliasOf, String aliasName) {
      this.collection = collection;
      this.hasValue = hasValue;
      this.value = value;
      this.aliasOf = aliasOf;
      this.aliasName = aliasName;
      this.children = collection ? new ArrayList<>() : null;
    }

    static Node scalar(Object v) {
      Node n = new Node(false, true, v, null, null);
      n.js = v;
      return n;
    }

    static Node collection(Object js, String type) {
      Node n = new Node(true, false, null, null, null);
      n.js = js;
      n.yamlType = type;
      return n;
    }

    static Node alias(Node src, String name) {
      Node n = new Node(false, false, null, src, name);
      n.js = src == null ? null : src.js;
      return n;
    }
  }

  /** An open collection being composed. */
  private static final class Frame {
    final Node node;
    final boolean map;
    /** map: the pending key node (null until the key is complete); keyDone distinguishes a null key */
    Node key;
    boolean keyDone;
    /** map: keys in order, for the duplicate check at MAPPING_END */
    final List<Node> keys = new ArrayList<>();

    Frame(Node node, boolean map) {
      this.node = node;
      this.map = map;
    }
  }

  private static final class Composer {
    private final List<String> warnings;
    private final List<YamlError> errors = new ArrayList<>();
    private final List<Frame> stack = new ArrayList<>();
    private final Map<String, Node> anchors = new HashMap<>();
    /** every anchored node (TS ctx.anchors also keeps renamed previous anchors) */
    private final IdentityHashMap<Node, int[]> anchorCounts = new IdentityHashMap<>();
    /** alias nodes in document (= toJSON) order */
    private final List<Node> aliases = new ArrayList<>();
    private Node root;
    private boolean rootDone;
    private int documents;
    private boolean warnedCollectionKey;

    Composer(List<String> warnings) {
      this.warnings = warnings;
    }

    private String src;

    Object compose(String src, byte[] input) {
      this.src = src;
      Parser parser = new Parser(input, Reader.ANY_ENCODING);
      parser.yaml110 = true;
      try {
        while (true) {
          Event e = parser.parse();
          if (e == null || e.type == Event.Type.STREAM_END) break;
          handle(e);
        }
      } catch (LibyamlError e) {
        // yaml 1.10 collects errors in resolution order and throws the first: errors of nodes completed before
        // the syntax error come first; the syntax error itself next.
        if (documents > 1) throw multipleDocuments();
        if (!errors.isEmpty()) throw errors.get(0);
        throw YamlError.syntax(syntaxMessage(e, src));
      }
      if (documents > 1) throw multipleDocuments();
      if (!errors.isEmpty()) throw errors.get(0);
      countAliases();
      return root == null ? null : root.js;
    }

    private static YamlError multipleDocuments() {
      return YamlError.semantic("Source contains multiple documents; please use YAML.parseAllDocuments()");
    }

    private void warn(String msg) {
      if (warnings != null) warnings.add(msg);
    }

    private void handle(Event e) {
      switch (e.type) {
        case DOCUMENT_START -> documents++;
        case DOCUMENT_END -> {
          // yaml 1.10 starts another (empty) document for any content after an explicit "...", comments included
          if (!e.implicit && documents == 1) {
            int after = charIndex(src, e.endMark.index);
            if (!src.substring(Math.min(after, src.length())).isBlank()) documents++;
          }
        }
        case SCALAR -> {
          if (e.style == Token.SINGLE_QUOTED_SCALAR_STYLE || e.style == Token.DOUBLE_QUOTED_SCALAR_STYLE) {
            // yaml 1.10: a comment right after a quoted value ('x'#c) is an error; libyaml takes it as a comment
            int after = charIndex(src, e.endMark.index);
            if (after < src.length() && src.charAt(after) == '#') {
              errors.add(YamlError.semantic("Comments must be separated from other tokens by white space characters"));
            }
          }
          add(scalar(e), e);
        }
        case ALIAS -> {
          String name = cstr(e.anchor);
          Node src = anchors.get(name);
          if (src == null) errors.add(YamlError.reference("Aliased anchor not found: " + name));
          Node n = Node.alias(src, name);
          if (src != null) aliases.add(n);
          add(n, null);
        }
        case SEQUENCE_START, MAPPING_START -> {
          boolean map = e.type == Event.Type.MAPPING_START;
          String type = (e.style == Event.FLOW_STYLE ? "FLOW_" : "") + (map ? "MAP" : "SEQ");
          String tag = cstr(e.tag);
          Node n;
          String expected = map ? MAP : SEQ;
          if (tag == null || tag.equals("!") || tag.equals(expected)) {
            n = Node.collection(map ? new JsObject() : new ArrayList<Object>(), type);
          } else if (tag.equals(map ? SEQ : MAP)) {
            // resolveSeq / resolveMap on the wrong collection type: error, node resolves to null
            errors.add(YamlError.syntax("A " + type + " node cannot be resolved as a " + (map ? "sequence" : "mapping")));
            n = Node.collection(map ? new JsObject() : new ArrayList<Object>(), type);
            n.js = null;
          } else {
            // scalar tags on a collection resolve its (empty) string value; unknown tags fall back to map/seq
            Object v = scalarTagOnCollection(tag, expected);
            if (v == FALLBACK) {
              n = Node.collection(map ? new JsObject() : new ArrayList<Object>(), type);
            } else {
              n = Node.collection(map ? new JsObject() : new ArrayList<Object>(), type);
              n.js = v;
            }
          }
          registerAnchor(e, n);
          stack.add(new Frame(n, map));
        }
        case SEQUENCE_END, MAPPING_END -> {
          Frame f = stack.remove(stack.size() - 1);
          if (f.map) checkDuplicateKeys(f);
          add(f.node, null);
        }
        default -> {}
      }
    }

    private void registerAnchor(Event e, Node n) {
      String anchor = cstr(e.anchor);
      if (anchor != null) {
        anchors.put(anchor, n);
        anchorCounts.put(n, new int[] {1, 0});
      }
    }

    /** Adds a completed node to the open collection (or makes it the document root). */
    private void add(Node n, Event scalarEvent) {
      if (scalarEvent != null) registerAnchor(scalarEvent, n);
      if (stack.isEmpty()) {
        if (!rootDone) {
          root = n;
          rootDone = true;
        }
        return;
      }
      Frame f = stack.get(stack.size() - 1);
      f.node.children.add(n);
      if (!f.map) {
        if (f.node.js instanceof List<?>) {
          @SuppressWarnings("unchecked") List<Object> l = (List<Object>) f.node.js;
          l.add(n.js);
        }
        return;
      }
      if (!f.keyDone) {
        f.key = emptyKey(n) ? null : n;
        f.keyDone = true;
        f.keys.add(f.key);
        return;
      }
      if (f.node.js instanceof JsObject m) {
        String k = stringifyKey(f.key);
        // JS: map[k] = v, or defineProperty when k is already present: the first position is kept
        m.put(k, n.js);
      }
      f.key = null;
      f.keyDone = false;
    }

    /** yaml 1.10 has no key node for an empty key (`: v`, `? ` without content). */
    private static boolean emptyKey(Node n) {
      return false;
    }

    private void checkDuplicateKeys(Frame f) {
      List<Node> items = f.keys;
      for (int i = 0; i < items.size(); i++) {
        Node iKey = items.get(i);
        for (int j = i + 1; j < items.size(); j++) {
          Node jKey = items.get(j);
          if (iKey == jKey || (iKey != null && jKey != null && iKey.hasValue && jKey.hasValue
              && strictEquals(iKey.value, jKey.value))) {
            errors.add(YamlError.semantic("Map keys must be unique; \"" + nodeToString(iKey) + "\" is repeated"));
            break;
          }
        }
      }
    }

    /** JS === on scalar values (NaN !== NaN, -0 === 0). */
    private static boolean strictEquals(Object a, Object b) {
      if (a == null || b == null) return a == b;
      if (a instanceof Double x && b instanceof Double y) return x.doubleValue() == y.doubleValue();
      return a.equals(b);
    }

    /** `${node}` in the duplicate-key message: String(scalar.value). */
    private static String nodeToString(Node n) {
      if (n == null) return "null";
      if (n.hasValue) return Js.toStr(n.value);
      return "[object Object]";
    }

    /** yaml 1.10 stringifyKey(key, toJSON(key)). */
    private String stringifyKey(Node key) {
      if (key == null) return "";
      Object jsKey = key.js;
      if (jsKey == null) return "";
      if (!(jsKey instanceof Map<?, ?>) && !(jsKey instanceof List<?>)) return Js.toStr(jsKey);
      if (!warnedCollectionKey) {
        warnedCollectionKey = true;
        warn("Keys with collection values will be stringified as YAML due to JS Object restrictions. "
            + "Use mapAsMap: true to avoid this.");
      }
      if (key.aliasOf != null) return "*" + key.aliasName;
      return FlowYaml.stringify(key);
    }

    // ---------------------------------------------------------------- scalars

    private Node scalar(Event e) {
      String value = new String(e.value, StandardCharsets.UTF_8);
      String tag = cstr(e.tag);
      boolean plain = e.style == Token.PLAIN_SCALAR_STYLE;
      if (tag == null) {
        return Node.scalar(plain ? resolvePlain(value) : value);
      }
      if (tag.equals("!")) return Node.scalar(value);
      if (tag.equals(MAP) || tag.equals(SEQ)) {
        String type = switch (e.style) {
          case Token.SINGLE_QUOTED_SCALAR_STYLE -> "QUOTE_SINGLE";
          case Token.DOUBLE_QUOTED_SCALAR_STYLE -> "QUOTE_DOUBLE";
          case Token.LITERAL_SCALAR_STYLE -> "BLOCK_LITERAL";
          case Token.FOLDED_SCALAR_STYLE -> "BLOCK_FOLDED";
          default -> "PLAIN";
        };
        errors.add(YamlError.syntax(
            "A " + type + " node cannot be resolved as a " + (tag.equals(MAP) ? "mapping" : "sequence")));
        return Node.scalar(null);
      }
      Object v = resolveTagged(tag, value);
      if (v == FALLBACK) {
        warn("The tag " + tag + " is unavailable, falling back to " + STR);
        return Node.scalar(value);
      }
      return Node.scalar(v);
    }

    /** Marker: the tag is not in the core schema (yaml falls back to str / map / seq with a warning). */
    private static final Object FALLBACK = new Object();

    private Object scalarTagOnCollection(String tag, String fallback) {
      Object v = resolveTagged(tag, "");
      if (v == FALLBACK) warn("The tag " + tag + " is unavailable, falling back to " + fallback);
      return v;
    }

    /** resolveByTagName for an explicit tag on a scalar string. */
    private static Object resolveTagged(String tag, String str) {
      if (tag.equals(STR)) return str;
      List<Rule> rules = new ArrayList<>();
      for (Rule r : CORE) {
        if (r.tag.equals(tag)) rules.add(r);
      }
      if (rules.isEmpty()) return FALLBACK;
      for (Rule r : rules) {
        Matcher m = r.test.matcher(str);
        if (m.matches()) return r.resolve(str, m);
      }
      return str;
    }

    // ---------------------------------------------------------------- aliases

    /** Document.toJSON's alias accounting (maxAliasCount 100), in toJSON order. */
    private void countAliases() {
      for (Node a : aliases) {
        int[] anchor = anchorCounts.get(a.aliasOf);
        if (anchor == null) continue;
        anchor[0] += 1;
        if (anchor[1] == 0) anchor[1] = aliasCount(a.aliasOf);
        if ((long) anchor[0] * anchor[1] > 100) {
          throw YamlError.reference("Excessive alias count indicates a resource exhaustion attack");
        }
      }
    }

    /** getAliasCount(node, anchors) */
    private int aliasCount(Node n) {
      if (n.aliasOf != null) {
        int[] anchor = anchorCounts.get(n.aliasOf);
        return anchor == null ? 0 : anchor[0] * anchor[1];
      }
      if (n.collection) {
        int count = 0;
        for (Node c : n.children) {
          int k = aliasCount(c);
          if (k > count) count = k;
        }
        return count;
      }
      return 1;
    }
  }

  // ---------------------------------------------------------------- core schema

  private abstract static class Rule {
    final String tag;
    final Pattern test;

    Rule(String tag, String test, int flags) {
      this.tag = TAG_PREFIX + tag;
      this.test = Pattern.compile(test, flags);
    }

    abstract Object resolve(String str, Matcher m);
  }

  private static Rule rule(String tag, String test, int flags, java.util.function.BiFunction<String, Matcher, Object> f) {
    return new Rule(tag, test, flags) {
      @Override Object resolve(String str, Matcher m) {
        return f.apply(str, m);
      }
    };
  }

  /** yaml 1.10 core schema tags with a test, in schema order (nullObj .. floatObj). */
  private static final List<Rule> CORE = List.of(
      rule("null", "(?:~|[Nn]ull|NULL)?", 0, (s, m) -> null),
      rule("bool", "(?:[Tt]rue|TRUE|[Ff]alse|FALSE)", 0, (s, m) -> s.charAt(0) == 't' || s.charAt(0) == 'T'),
      rule("int", "0o([0-7]+)", 0, (s, m) -> new BigInteger(m.group(1), 8).doubleValue()),
      rule("int", "[-+]?[0-9]+", 0, (s, m) -> parseIntDecimal(s)),
      rule("int", "0x([0-9a-fA-F]+)", 0, (s, m) -> new BigInteger(m.group(1), 16).doubleValue()),
      rule("float", "(?:[-+]?\\.inf|(\\.nan))", Pattern.CASE_INSENSITIVE,
          (s, m) -> m.group(1) != null ? Double.NaN
              : s.charAt(0) == '-' ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY),
      rule("float", "[-+]?(?:\\.[0-9]+|[0-9]+(?:\\.[0-9]*)?)[eE][-+]?[0-9]+", 0, (s, m) -> Double.parseDouble(s)),
      rule("float", "[-+]?(?:\\.([0-9]+)|[0-9]+\\.([0-9]*))", 0, (s, m) -> Double.parseDouble(s)));

  /** parseInt(str, 10) on /^[-+]?[0-9]+$/: correctly rounded, "-0" is -0. */
  private static Double parseIntDecimal(String s) {
    return Double.parseDouble(s);
  }

  /** resolveScalar(str, schema.tags) for an untagged plain scalar. */
  static Object resolvePlain(String str) {
    for (Rule r : CORE) {
      Matcher m = r.test.matcher(str);
      if (m.matches()) return r.resolve(str, m);
    }
    return str;
  }

  static String cstr(byte[] b) {
    if (b == null) return null;
    int n = 0;
    while (n < b.length && b[n] != 0) n++;
    return new String(b, 0, n, StandardCharsets.UTF_8);
  }

  // ---------------------------------------------------------------- libyaml errors

  /**
   * The message for a libyaml error. yaml 1.10 words its syntax errors differently and detects them at different
   * places; the libyaml problems with a clear yaml 1.10 counterpart are mapped to its wording (chosen with the WP-3
   * mutation set: the yaml 1.10 message TS gives for the same malformed input most often), the rest keep libyaml's
   * problem/context text.
   */
  static String syntaxMessage(LibyamlError e, String src) {
    String p = e.problem == null ? "" : e.problem;
    String c = e.context == null ? "" : e.context;
    String libyaml = e.context == null ? p : p + " " + e.context;
    int at = charIndex(src, e.problemMark.index);
    char ch = at >= 0 && at < src.length() ? src.charAt(at) : '\0';
    String line = lineAt(src, at);
    int col = at - lineStart(src, at);
    // node properties libyaml rejects as a parse error, yaml 1.10 as a semantic one
    String props = line.replaceFirst("(^|[ \\t])#.*$", "");
    if (props.matches(".*(^|[\\s:\\-\\[{,])&[^\\s,\\[\\]{}]+[ \\t]+&.*")) return "A node can have at most one anchor";
    if (props.matches(".*(^|[\\s:\\-\\[{,])[&!][^\\s,\\[\\]{}]*[ \\t]+\\*.*")) {
      return "An alias node must not specify any properties";
    }
    switch (p) {
      case "found character that cannot start any token" -> {
        if (ch == '\t') return tabMessage(src, at);
        if (ch == '@' || ch == '`') return "Plain value cannot start with reserved character " + ch;
        return "All collection items must start at the same column";
      }
      case "found a tab character that violates indentation" -> {
        return tabMessage(src, at);
      }
      case "did not find expected key" -> {
        // a "- " item at the column of the mapping's keys
        int lineCol = col - (line.length() - line.stripLeading().length());
        if (line.stripLeading().startsWith("- ") && lineCol == 0 && previousSiblingIsKey(src, at, col)) {
          return "A collection cannot be both a mapping and a sequence";
        }
        return "All collection items must start at the same column";
      }
      case "did not find expected '-' indicator", "did not find expected comment or line break" -> {
        return "All collection items must start at the same column";
      }
      case "mapping values are not allowed in this context" -> {
        // yaml 1.10 reads the line as the continuation of the previous line's plain value
        return previousLineOpensBlock(src, at) ? "All collection items must start at the same column"
            : "Nested mappings are not allowed in compact mappings";
      }
      case "could not find expected ':'" -> {
        int k = charIndex(src, e.contextMark.index);
        String key = k >= 0 && k <= at && at <= src.length() ? src.substring(k, at) : "";
        if (key.length() > 1024) {
          return "The \"" + key.substring(0, 8) + "..." + key.substring(key.length() - 8) + "\" key is too long";
        }
        return "Implicit map keys need to be followed by map values";
      }
      case "did not find expected <document start>" -> {
        return "Document contains trailing content not separated by a ... or --- line";
      }
      case "found unexpected end of stream" -> {
        if (c.equals("while scanning a quoted scalar")) {
          int q = charIndex(src, e.contextMark.index);
          return q >= 0 && q < src.length() && src.charAt(q) == '\'' ? "Missing closing 'quote"
              : "Missing closing \"quote";
        }
        return libyaml;
      }
      case "found undefined tag handle" -> {
        int end = at + 1;
        while (end < src.length() && src.charAt(end) != '!' && !Character.isWhitespace(src.charAt(end))) end++;
        String handle = end < src.length() && src.charAt(end) == '!' ? src.substring(at, end + 1) : "!";
        return "The " + handle + " tag handle is non-default and was not declared.";
      }
      case "block sequence entries are not allowed in this context" -> {
        return "Sequence items must not have preceding content on the same line";
      }
      case "found unknown escape character" -> {
        int b = src.lastIndexOf('\\', at);
        return "Invalid escape sequence " + (b >= 0 && b + 2 <= src.length() ? src.substring(b, b + 2) : "\\");
      }
      case "did not find expected node content" -> {
        if (c.equals("while parsing a flow node") && ch == ',') return "Flow sequence contains an unexpected ,";
        return libyaml;
      }
      default -> {
        if (p.startsWith("did not find expected ',' or ")) return "Insufficient indentation in flow collection";
        return libyaml;
      }
    }
  }

  /**
   * A tab-indented line: yaml 1.10 sees it as the continuation of the previous line's plain value ("Plain value
   * cannot start with a tab character") unless the previous line opened a block ("key:" with no value).
   */
  private static String tabMessage(String src, int at) {
    return previousLineOpensBlock(src, at) ? "All collection items must start at the same column"
        : "Plain value cannot start with a tab character";
  }

  /** Whether the last non-blank, non-comment line before {@code at}'s line ends with ':' (comments stripped). */
  private static boolean previousLineOpensBlock(String src, int at) {
    int i = lineStart(src, at);
    while (i > 0) {
      int end = i - 1;
      if (end > 0 && src.charAt(end) == '\n' && src.charAt(end - 1) == '\r') end--;
      int start = lineStart(src, end);
      String l = src.substring(start, end).replaceFirst("(^|[ \\t])#.*$", "").strip();
      if (!l.isEmpty()) return l.endsWith(":");
      i = start;
    }
    return false;
  }

  /** Whether the closest earlier non-blank, non-comment line indented exactly {@code col} is a "key:" entry. */
  private static boolean previousSiblingIsKey(String src, int at, int col) {
    int i = lineStart(src, at);
    while (i > 0) {
      int end = i - 1;
      if (end > 0 && src.charAt(end) == '\n' && src.charAt(end - 1) == '\r') end--;
      int start = lineStart(src, end);
      String l = src.substring(start, end);
      String content = l.strip();
      int indent = l.length() - l.stripLeading().length();
      if (!content.isEmpty() && !content.startsWith("#")) {
        if (indent == col) return !content.startsWith("- ") && content.replaceFirst("(^|[ \\t])#.*$", "").contains(":");
        if (indent < col) return false;
      }
      i = start;
    }
    return false;
  }

  /** The UTF-16 index of a libyaml mark index (characters = code points), clamped to the source. */
  private static int charIndex(String src, long codePoints) {
    if (codePoints <= 0) return 0;
    long n = Math.min(codePoints, src.codePointCount(0, src.length()));
    return src.offsetByCodePoints(0, (int) n);
  }

  private static int lineStart(String src, int at) {
    int i = Math.min(at, src.length());
    while (i > 0 && src.charAt(i - 1) != '\n' && src.charAt(i - 1) != '\r') i--;
    return i;
  }

  private static String lineAt(String src, int at) {
    int s = lineStart(src, at);
    int e = Math.max(s, Math.min(at, src.length()));
    while (e < src.length() && src.charAt(e) != '\n' && src.charAt(e) != '\r') e++;
    return src.substring(s, e);
  }

  /** Flow-YAML rendering of a collection key (yaml's stringifyKey with inStringifyKey), for simple contents. */
  static final class FlowYaml {
    private FlowYaml() {}

    static String stringify(Node n) {
      StringBuilder sb = new StringBuilder();
      write(sb, n);
      return sb.toString();
    }

    private static void write(StringBuilder sb, Node n) {
      if (n == null) {
        sb.append("null");
      } else if (n.aliasOf != null) {
        sb.append('*').append(n.aliasName);
      } else if (!n.collection) {
        scalar(sb, n.value);
      } else if (n.yamlType.endsWith("MAP")) {
        if (n.children.isEmpty()) {
          sb.append("{}");
          return;
        }
        sb.append("{ ");
        for (int i = 0; i + 1 < n.children.size(); i += 2) {
          if (i > 0) sb.append(", ");
          write(sb, n.children.get(i));
          sb.append(": ");
          write(sb, n.children.get(i + 1));
        }
        sb.append(" }");
      } else {
        if (n.children.isEmpty()) {
          sb.append("[]");
          return;
        }
        sb.append("[ ");
        for (int i = 0; i < n.children.size(); i++) {
          if (i > 0) sb.append(", ");
          write(sb, n.children.get(i));
        }
        sb.append(" ]");
      }
    }

    private static void scalar(StringBuilder sb, Object v) {
      if (v == null) sb.append("null");
      else if (v instanceof Double d) sb.append(JsNumber.toString(d));
      else sb.append(Js.toStr(v));
    }
  }
}
