package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;

/**
 * Port of rouge-4.7.0/lib/rouge/lexers/javascript.rb (detect? is in Detectors). The class methods
 * keywords/declarations/reserved/constants/builtins are instance methods here so that Typescript
 * can extend them (Ruby: <code>self.class.keywords</code> + TypescriptCommon).
 */
public class Javascript extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("Javascript");
  static final Definition DEF = new Definition(null);

  static final Set<String> KEYWORDS = Set.of(
      "async", "await", "break", "case", "catch", "continue", "debugger", "default", "delete",
      "do", "else", "export", "finally", "from", "for", "if", "import", "in", "instanceof", "new", "of",
      "return", "super", "switch", "this", "throw", "try", "typeof", "void", "while", "yield");

  static final Set<String> DECLARATIONS = Set.of(
      "var", "let", "const", "with", "function", "class",
      "extends", "constructor", "get", "set", "static");

  static final Set<String> RESERVED = Set.of(
      "enum", "implements", "interface",
      "package", "private", "protected", "public");

  static final Set<String> CONSTANTS = Set.of("true", "false", "null", "NaN", "Infinity", "undefined");

  static final List<String> BUILTINS = List.of(
      "Array", "Boolean", "Date", "Error", "Function", "Math", "netscape",
      "Number", "Object", "Packages", "RegExp", "String", "sun", "decodeURI",
      "decodeURIComponent", "encodeURI", "encodeURIComponent",
      "Error", "eval", "isFinite", "isNaN", "parseFloat", "parseInt",
      "document", "window", "navigator", "self", "global",
      "Promise", "Set", "Map", "WeakSet", "WeakMap", "Symbol", "Proxy", "Reflect",
      "Int8Array", "Uint8Array", "Uint8ClampedArray",
      "Int16Array", "Uint16Array", "Uint16ClampedArray",
      "Int32Array", "Uint32Array", "Uint32ClampedArray",
      "Float32Array", "Float64Array", "DataView", "ArrayBuffer");

  public Set<String> keywords() {
    return KEYWORDS;
  }

  public Set<String> declarations() {
    return DECLARATIONS;
  }

  public Set<String> reserved() {
    return RESERVED;
  }

  public Set<String> constants() {
    return CONSTANTS;
  }

  public List<String> builtins() {
    return BUILTINS;
  }

  private static final R ID = re("[\\p{L}\\p{Nl}$_][\\p{Word}]*", "i");

  /** self.id_regex: /[\p{L}\p{Nl}$_][\p{Word}]*&#47;io */
  public static R idRegex() {
    return ID;
  }

  static {
    final R id = idRegex();

    DEF.state("multiline_comment", s -> {
      s.rule("[*]/", Comment_Multiline, POP);
      s.rule("[^*/]+", Comment_Multiline);
      s.rule("[*/]", Comment_Multiline);
    });

    DEF.state("comments_and_whitespace", s -> {
      s.rule("\\s+", Text);
      s.rule("<!--", Comment); // really...?
      s.rule("//.*?$", Comment_Single);
      s.rule("/[*]", Comment_Multiline, "multiline_comment");
    });

    DEF.state("expr_start", s -> {
      s.mixin("comments_and_whitespace");

      s.rule("/", (l, m) -> {
        l.token(Str_Regex);
        l.gotoState("regex");
      });

      s.rule("[{]", (l, m) -> {
        l.token(Punctuation);
        l.gotoState("object");
      });

      s.rule("", Text, POP);
    });

    DEF.state("regex", s -> {
      s.rule("/", (l, m) -> {
        l.token(Str_Regex);
        l.gotoState("regex_end");
      });

      s.rule("[^/]\\n", Error, POP);

      s.rule("\\n", Error, POP);
      s.rule("\\[\\^", Str_Escape, "regex_group");
      s.rule("\\[", Str_Escape, "regex_group");
      s.rule("\\\\.", Str_Escape);
      s.rule("[(][?][:=<!]", Str_Escape);
      s.rule("[{][\\d,]+[}]", Str_Escape);
      s.rule("[()?]", Str_Escape);
      s.rule(".", Str_Regex);
    });

    DEF.state("regex_end", s -> {
      s.rule("[gimuy]+", Str_Regex, POP);
      s.rule("", (l, m) -> l.pop());
    });

    DEF.state("regex_group", s -> {
      // specially highlight / in a group to indicate that it doesn't
      // close the regex
      s.rule("/", Str_Escape);

      s.rule("[^/]\\n", (l, m) -> {
        l.token(Error);
        l.pop(2);
      });

      s.rule("\\]", Str_Escape, POP);
      s.rule("\\\\.", Str_Escape);
      s.rule(".", Str_Regex);
    });

    DEF.state("bad_regex", s -> {
      s.rule("[^\\n]+", Error, POP);
    });

    DEF.state("root", s -> {
      s.rule(re("\\A\\s*#!.*?\\n", "m"), Comment_Preproc, "statement");
      s.rule("(?<=\\n)(?=\\s|/|<!--)", Text, "expr_start");
      s.mixin("comments_and_whitespace");
      s.rule(re("\\+\\+ | -- | ~ | \\?\\?=? | && | \\|\\| | \\\\(?=\\n) | << | >>>? | ===\n"
          + "               | !== ", "x"),
          Operator, "expr_start");
      s.rule("[-<>+*%&|\\^/!=]=?", Operator, "expr_start");
      s.rule("[(\\[,]", Punctuation, "expr_start");
      s.rule(";", Punctuation, "statement");
      s.rule("[)\\].]", Punctuation);

      s.rule("`", (l, m) -> {
        l.token(Str_Double);
        l.push("template_string");
      });

      // special case for the safe navigation operator ?.
      // so that we don't start detecting a ternary expr
      s.rule("[?][.]", Punctuation);

      s.rule("[?]", (l, m) -> {
        l.token(Punctuation);
        l.push("ternary");
        l.push("expr_start");
      });

      s.rule("(\\@)(\\w+)?", (l, m) -> {
        l.groups(Punctuation, Name_Decorator);
        l.push("expr_start");
      });

      s.rule("(class)((?:\\s|\\\\\\s)+)", (l, m) -> {
        l.groups(Keyword_Declaration, Text);
        l.push("classname");
      });

      s.rule(re("([\\p{Nl}$_]*\\p{Lu}[\\p{Word}]*)[ \\t]*(?=(\\(.*\\)))", "m"), Name_Class);

      s.rule("(function)((?:\\s|\\\\\\s)+)(" + id + ")", (l, m) -> {
        l.groups(Keyword_Declaration, Text, Name_Function);
      });

      s.rule("function(?=(\\(.*\\)))", Keyword_Declaration); // For anonymous functions

      s.rule(re("(#?" + id + ")[ \\t]*(?=(\\(.*\\)))", "m"), (l, m) -> {
        if (((Javascript) l).keywords().contains(m.get(1))) {
          // "if" in "if (...)" or "switch" in "switch (...)" are recognized as keywords.
          l.token(Keyword);
        } else {
          l.token(Name_Function);
        }
      });

      s.rule("[{}]", Punctuation, "statement");

      s.rule("#?" + id, (l, m) -> {
        Javascript js = (Javascript) l;
        String v = m.get(0);
        if (js.keywords().contains(v)) {
          l.token(Keyword);
          l.push("expr_start");
        } else if (js.declarations().contains(v)) {
          l.token(Keyword_Declaration);
          l.push("expr_start");
        } else if (js.reserved().contains(v)) {
          l.token(Keyword_Reserved);
        } else if (js.constants().contains(v)) {
          l.token(Keyword_Constant);
        } else if (js.builtins().contains(v)) {
          l.token(Name_Builtin);
        } else {
          l.token(Name_Other);
        }
      });

      s.rule("[0-9][0-9]*\\.[0-9]+([eE][0-9]+)?[fd]?", Num_Float);
      s.rule(re("0x[0-9a-fA-F]+", "i"), Num_Hex);
      s.rule(re("0o[0-7][0-7_]*", "i"), Num_Oct);
      s.rule(re("0b[01][01_]*", "i"), Num_Bin);
      s.rule("[0-9]+", Num_Integer);

      s.rule("\"", Str_Delimiter, "dq");
      s.rule("'", Str_Delimiter, "sq");
      s.rule(":", Punctuation);
    });

    DEF.state("dq", s -> {
      s.rule("\\\\[\\\\nrt\"]?", Str_Escape);
      s.rule("[^\\\\\"]+", Str_Double);
      s.rule("\"", Str_Delimiter, POP);
    });

    DEF.state("sq", s -> {
      s.rule("\\\\[\\\\nrt']?", Str_Escape);
      s.rule("[^\\\\']+", Str_Single);
      s.rule("'", Str_Delimiter, POP);
    });

    DEF.state("classname", s -> {
      s.rule("(" + id + ")((?:\\s|\\\\\\s)+)(extends)((?:\\s|\\\\\\s)+)", (l, m) -> {
        l.groups(Name_Class, Text, Keyword_Declaration, Text);
      });

      s.rule(id, Name_Class, POP);
    });

    // braced parts that aren't object literals
    DEF.state("statement", s -> {
      s.rule("case\\b", (l, m) -> {
        l.token(Keyword);
        l.gotoState("expr_start");
      });

      s.rule("(" + id + ")(\\s*)(:)", (l, m) -> {
        l.groups(Name_Label, Text, Punctuation);
      });

      s.mixin("expr_start");
    });

    // object literals
    DEF.state("object", s -> {
      s.mixin("comments_and_whitespace");

      s.rule("[{]", (l, m) -> {
        l.token(Punctuation);
        l.push();
      });

      s.rule("[}]", (l, m) -> {
        l.token(Punctuation);
        l.gotoState("statement");
      });

      s.rule("(" + id + ")(\\s*)(:)", (l, m) -> {
        l.groups(Name_Attribute, Text, Punctuation);
        l.push("expr_start");
      });

      s.rule(":", Punctuation);
      s.mixin("root");
    });

    // ternary expressions, where <id>: is not a label!
    DEF.state("ternary", s -> {
      s.rule(":", (l, m) -> {
        l.token(Punctuation);
        l.gotoState("expr_start");
      });

      s.mixin("root");
    });

    // template strings
    DEF.state("template_string", s -> {
      s.rule("[$]{", Punctuation, "template_string_expr");
      s.rule("`", Str_Double, POP);
      s.rule("\\\\[$`\\\\]", Str_Escape);
      s.rule("[^$`\\\\]+", Str_Double);
      s.rule("[\\\\$]", Str_Double);
    });

    DEF.state("template_string_expr", s -> {
      s.rule("}", Punctuation, POP);
      s.mixin("root");
    });
  }

  public Javascript(Map<String, ?> opts) {
    super(opts);
  }

  @Override
  protected Definition definition() {
    return DEF;
  }

  @Override
  public LexerClass lexerClass() {
    return CLASS;
  }
}
