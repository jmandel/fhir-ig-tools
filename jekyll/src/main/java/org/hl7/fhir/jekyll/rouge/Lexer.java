package org.hl7.fhir.jekyll.rouge;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Port of rouge-4.7.0/lib/rouge/lexer.rb (Rouge::Lexer): the class-level registry API
 * (<code>find</code>, <code>lookup_fancy</code>, <code>find_fancy</code>, <code>guess</code>), option
 * handling, and the token consolidation done by <code>continue_lex</code>.
 */
public abstract class Lexer implements Tokens {
  /** @options (string keys) */
  protected final Map<String, Object> options = new LinkedHashMap<>();

  protected Lexer(Map<String, ?> opts) {
    if (opts != null) {
      for (Map.Entry<String, ?> e : opts.entrySet()) {
        options.put(String.valueOf(e.getKey()), e.getValue());
      }
    }
  }

  /** self.class */
  public abstract LexerClass lexerClass();

  /** delegated to Lexer.tag */
  public String tag() {
    return lexerClass().tag;
  }

  public Map<String, Object> options() {
    return options;
  }

  // ------------------------------------------------------------------ class methods

  /** Lexer.find(name): registry[name.to_s] */
  public static LexerClass find(String name) {
    return Registry.BY_NAME.get(name);
  }

  /** Lexer.all */
  public static List<LexerClass> all() {
    return Registry.ALL;
  }

  public static LexerClass byClassName(String cls) {
    return Registry.BY_CLASS.get(cls);
  }

  /** The pair returned by Lexer.lookup_fancy (the class may be null). */
  public record Fancy(LexerClass lexerClass, Map<String, Object> options) {}

  /** Lexer.lookup_fancy(str, code=nil, default_options={}) */
  public static Fancy lookupFancy(String str, String code, Map<String, Object> defaultOptions) {
    if (str != null && !str.contains("?") && !str.equals("guess")) {
      return new Fancy(find(str), defaultOptions);
    }
    String name;
    String opts;
    if (str != null) {
      int q = str.indexOf('?');
      name = q < 0 ? str : str.substring(0, q);
      opts = q < 0 ? null : str.substring(q + 1);
    } else {
      name = null;
      opts = "";
    }
    // parse the options hash from a cgi-style string
    Map<String, List<String>> cgi = new LinkedHashMap<>();
    for (String[] kv : decodeWwwForm(opts == null ? "" : opts)) {
      cgi.computeIfAbsent(kv[0], k -> new ArrayList<>()).add(kv[1]);
    }
    Map<String, Object> merged = new LinkedHashMap<>(defaultOptions);
    for (Map.Entry<String, List<String>> e : cgi.entrySet()) {
      List<String> vals = e.getValue();
      merged.put(e.getKey(), vals.size() == 1 ? vals.get(0) : vals);
    }
    LexerClass cls;
    if (name == null || name.equals("guess")) {
      Object mt = merged.get("mimetype");
      cls = guess(mt == null ? null : mt.toString(), null, code);
    } else {
      cls = find(name);
    }
    return new Fancy(cls, merged);
  }

  /** Lexer.find_fancy(str, code=nil, default_options={}) */
  public static Lexer findFancy(String str, String code, Map<String, Object> defaultOptions) {
    Fancy f = lookupFancy(str, code, defaultOptions);
    return f.lexerClass() == null ? null : f.lexerClass().newLexer(f.options());
  }

  public static Lexer findFancy(String str, String code) {
    return findFancy(str, code, Map.of());
  }

  /** Lexer.guesses(info): mimetype / filename / source guessers over Lexer.all. */
  public static List<LexerClass> guesses(String mimetype, String filename, String source) {
    List<Guessers.Guesser> gs = new ArrayList<>();
    if (mimetype != null) {
      gs.add(new Guessers.Mimetype(mimetype));
    }
    if (filename != null) {
      throw new UnsupportedOperationException("filename guessing is not ported (unused by kramdown/Jekyll)");
    }
    if (source != null) {
      gs.add(new Guessers.Modeline(source));
      gs.add(new Guessers.Source(source));
    }
    return Guessers.guess(gs, all());
  }

  /** Lexer.guess(info): PlainText when nothing matches, raises Guesser::Ambiguous when several do. */
  public static LexerClass guess(String mimetype, String filename, String source) {
    List<LexerClass> lexers = guesses(mimetype, filename, source);
    if (lexers.isEmpty()) {
      return byClassName("PlainText");
    }
    if (lexers.size() == 1) {
      return lexers.get(0);
    }
    throw new RougeError.Ambiguous(lexers);
  }

  public static LexerClass guessByMimetype(String mt) {
    return guess(mt, null, null);
  }

  /** URI.decode_www_form (Ruby 3.2 uri/common.rb), as used by lookup_fancy. */
  static List<String[]> decodeWwwForm(String str) {
    List<String[]> ary = new ArrayList<>();
    for (int k = 0; k < str.length(); k++) {
      if (str.charAt(k) >= 128) {
        throw new RougeError("ArgumentError: the input of URI.decode_www_form must be ASCII only string");
      }
    }
    if (str.isEmpty()) {
      return ary;
    }
    int start = 0;
    while (start < str.length()) {
      int amp = str.indexOf('&', start);
      String piece = amp < 0 ? str.substring(start) : str.substring(start, amp);
      start = amp < 0 ? str.length() : amp + 1;
      int eq = piece.indexOf('=');
      String key = eq < 0 ? piece : piece.substring(0, eq);
      String val = eq < 0 ? "" : piece.substring(eq + 1);
      ary.add(new String[] {decodeComponent(key), decodeComponent(val)});
    }
    return ary;
  }

  private static String decodeComponent(String s) {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    for (int k = 0; k < s.length(); k++) {
      char c = s.charAt(k);
      if (c == '+') {
        b.write(' ');
      } else if (c == '%' && k + 2 < s.length() && hex(s.charAt(k + 1)) >= 0 && hex(s.charAt(k + 2)) >= 0) {
        b.write(hex(s.charAt(k + 1)) * 16 + hex(s.charAt(k + 2)));
        k += 2;
      } else {
        b.write(c);
      }
    }
    // force_encoding(UTF-8).scrub!
    return new String(b.toByteArray(), StandardCharsets.UTF_8);
  }

  private static int hex(char c) {
    return c < 128 ? Character.digit(c, 16) : -1;
  }

  // ------------------------------------------------------------------ option casts

  protected boolean asBool(Object val) {
    if (val == null || Boolean.FALSE.equals(val) || "0".equals(val) || "false".equals(val) || "off".equals(val)
        || (val instanceof Number && ((Number) val).longValue() == 0 && !(val instanceof Double))) {
      return false;
    }
    if (val instanceof List) {
      List<?> l = (List<?>) val;
      return l.isEmpty() || asBool(l.get(l.size() - 1));
    }
    return true;
  }

  protected String asString(Object val) {
    if (val instanceof List) {
      List<?> l = (List<?>) val;
      return asString(l.isEmpty() ? null : l.get(l.size() - 1));
    }
    return val == null || Boolean.FALSE.equals(val) ? null : String.valueOf(val);
  }

  protected List<String> asList(Object val) {
    List<String> out = new ArrayList<>();
    if (val instanceof List) {
      for (Object v : (List<?>) val) {
        out.addAll(asList(v));
      }
    } else if (val instanceof String) {
      out.addAll(RubyStrings.split((String) val, ","));
    }
    return out;
  }

  protected Lexer asLexer(Object val) {
    if (val instanceof List) {
      List<?> l = (List<?>) val;
      return asLexer(l.isEmpty() ? null : l.get(l.size() - 1));
    }
    if (val instanceof LexerClass) {
      return ((LexerClass) val).newLexer(options);
    }
    if (val instanceof Lexer) {
      return (Lexer) val;
    }
    if (val instanceof String) {
      LexerClass c = find((String) val);
      return c == null ? null : c.newLexer(options);
    }
    return null;
  }

  protected Token asToken(Object val) {
    if (val instanceof List) {
      List<?> l = (List<?>) val;
      return asToken(l.isEmpty() ? null : l.get(l.size() - 1));
    }
    if (val instanceof Token) {
      return (Token) val;
    }
    return val instanceof String ? Token.get((String) val) : null;
  }

  protected boolean boolOption(String name, Supplier<Boolean> dflt) {
    if (options.containsKey(name)) {
      return asBool(options.get(name));
    }
    return dflt != null && dflt.get();
  }

  private Object deleteOption(String name, Supplier<Object> dflt) {
    if (options.containsKey(name)) {
      return options.remove(name);
    }
    return dflt == null ? null : dflt.get();
  }

  protected String stringOption(String name, Supplier<Object> dflt) {
    return asString(deleteOption(name, dflt));
  }

  protected Lexer lexerOption(String name, Supplier<Object> dflt) {
    return asLexer(deleteOption(name, dflt));
  }

  protected List<String> listOption(String name, Supplier<Object> dflt) {
    return asList(deleteOption(name, dflt));
  }

  protected Token tokenOption(String name, Supplier<Object> dflt) {
    return asToken(deleteOption(name, dflt));
  }

  // ------------------------------------------------------------------ lexing

  /** @abstract reset!: called before each fresh lex. */
  public void reset() {}

  /** lex(string) { |tok, val| }: reset!, then continue_lex. */
  public void lex(String string, Tok.Sink b) {
    reset();
    continueLex(string, b);
  }

  /** lex(string).to_a */
  public List<Tok> lex(String string) {
    List<Tok> out = new ArrayList<>();
    lex(string, (t, v) -> out.add(new Tok(t, v)));
    return out;
  }

  /** continue_lex: stream tokens, consolidating consecutive tokens of the same type. */
  public void continueLex(String string, Tok.Sink b) {
    final Token[] lastToken = {null};
    final StringBuilder lastVal = new StringBuilder();
    streamTokens(string, (tok, val) -> {
      if (val.isEmpty()) {
        return;
      }
      if (tok == lastToken[0]) {
        lastVal.append(val);
        return;
      }
      if (lastToken[0] != null) {
        b.token(lastToken[0], lastVal.toString());
      }
      lastToken[0] = tok;
      lastVal.setLength(0);
      lastVal.append(val);
    });
    if (lastToken[0] != null) {
      b.token(lastToken[0], lastVal.toString());
    }
  }

  /** @abstract stream_tokens(stream) { |tok, val| } */
  protected abstract void streamTokens(String string, Tok.Sink b);

  @Override
  public String toString() {
    return "#<" + lexerClass() + ">";
  }
}
