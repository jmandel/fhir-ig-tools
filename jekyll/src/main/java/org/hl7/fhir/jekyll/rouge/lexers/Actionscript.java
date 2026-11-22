package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;

/** Port of rouge-4.7.0/lib/rouge/lexers/actionscript.rb */
public class Actionscript extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("Actionscript");
  static final Definition DEF = new Definition(null);

  static final Set<String> KEYWORDS = Words.set(Words.w(
      "for in while do break return continue switch case default"
      + " if else throw try catch finally new delete typeof is"
      + " this with"));

  static final Set<String> DECLARATIONS = Words.set(Words.w("var with function"));

  static final Set<String> RESERVED = Words.set(Words.w(
      "dynamic final internal native public protected private class const"
      + " override static package interface extends implements namespace"
      + " set get import include super flash_proxy object_proxy trace"));

  static final Set<String> CONSTANTS = Words.set(Words.w("true false null NaN Infinity undefined"));

  static final List<String> BUILTINS = List.of(Words.w(
      "void Function Math Class"
      + " Object RegExp decodeURI"
      + " decodeURIComponent encodeURI encodeURIComponent"
      + " eval isFinite isNaN parseFloat parseInt this"));

  static {
    DEF.state("comments_and_whitespace", s -> {
      s.rule("\\s+", Text);
      s.rule("//.*?$", Comment_Single);
      s.rule(re("/\\*.*?\\*/", "m"), Comment_Multiline);
    });

    DEF.state("expr_start", s -> {
      s.mixin("comments_and_whitespace");

      s.rule("/", (l, m) -> {
        l.token(Str_Regex);
        l.gotoState("regex");
      });

      s.rule("[{]", Punctuation, "object");

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
      s.rule("[gim]+", Str_Regex, POP);
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

    final R id = re("[$a-zA-Z_][a-zA-Z0-9_]*");

    DEF.state("root", s -> {
      s.rule(re("\\A\\s*#!.*?\\n", "m"), Comment_Preproc, "statement");
      s.rule("\\n", Text, "statement");
      s.rule("(?<=\\n)(?=\\s|/|<!--)", Text, "expr_start");
      s.mixin("comments_and_whitespace");
      s.rule(re("\\+\\+ | -- | ~ | && | \\|\\| | \\\\(?=\\n) | << | >>>? | ===\n"
          + "               | !== ", "x"),
          Operator, "expr_start");
      s.rule("[:-<>+*%&|\\^/!=]=?", Operator, "expr_start");
      s.rule("[(\\[,]", Punctuation, "expr_start");
      s.rule(";", Punctuation, "statement");
      s.rule("[)\\].]", Punctuation);

      s.rule("[?]", (l, m) -> {
        l.token(Punctuation);
        l.push("ternary");
        l.push("expr_start");
      });

      s.rule("[{}]", Punctuation, "statement");

      s.rule(id, (l, m) -> {
        String v = m.get(0);
        if (KEYWORDS.contains(v)) {
          l.token(Keyword);
          l.push("expr_start");
        } else if (DECLARATIONS.contains(v)) {
          l.token(Keyword_Declaration);
          l.push("expr_start");
        } else if (RESERVED.contains(v)) {
          l.token(Keyword_Reserved);
        } else if (CONSTANTS.contains(v)) {
          l.token(Keyword_Constant);
        } else if (BUILTINS.contains(v)) {
          l.token(Name_Builtin);
        } else {
          l.token(Name_Other);
        }
      });

      s.rule("\\-?[0-9][0-9]*\\.[0-9]+([eE][0-9]+)?[fd]?", Num_Float);
      s.rule("0x[0-9a-fA-F]+", Num_Hex);
      s.rule("\\-?[0-9]+", Num_Integer);
      s.rule("\"(\\\\\\\\|\\\\\"|[^\"])*\"", Str_Double);
      s.rule("'(\\\\\\\\|\\\\'|[^'])*'", Str_Single);
    });

    // braced parts that aren't object literals
    DEF.state("statement", s -> {
      s.rule("(" + id + ")(\\s*)(:)", (l, m) -> {
        l.groups(Name_Label, Text, Punctuation);
      });

      s.rule("[{}]", Punctuation);

      s.mixin("expr_start");
    });

    // object literals
    DEF.state("object", s -> {
      s.mixin("comments_and_whitespace");
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
  }

  public Actionscript(Map<String, ?> opts) {
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
