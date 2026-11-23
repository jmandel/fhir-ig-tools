package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;

/** Port of rouge-4.7.0/lib/rouge/lexers/json5.rb */
public class JSON5 extends JSON {
  public static final LexerClass CLASS = Lexer.byClassName("JSON5");
  static final Definition DEF = new Definition(JSON.DEF);

  static {
    DEF.append("whitespace", s -> {
      s.rule("//.*$", Comment);

      // comments are non-nesting, so a single regex will do
      s.rule(re("/[*].*?[*]/", "m"), Comment);
    });

    DEF.prepend("name", s -> {
      s.rule(Javascript.idRegex(), Name_Label);
      s.rule("\".*?\"", Name_Label);
    });

    // comments can appear between keys and :, so we have to
    // manage our states a little more carefully
    DEF.append("object", s -> {
      s.rule(":", (l, m) -> {
        l.token(Punctuation);
        l.gotoState("object_value");
      });
    });

    DEF.state("object_value", s -> {
      s.mixin("value");
      s.rule(",", (l, m) -> {
        l.token(Punctuation);
        l.gotoState("object");
      });

      s.rule("}", Punctuation, POP);
    });

    DEF.append("value", s -> {
      s.rule("'", Str_Single, "sstring");
    });

    DEF.state("sstring", s -> {
      s.rule("[^\\\\']+", Str_Single);
      s.rule(re("\\\\.", "m"), Str_Escape);
      s.rule("'", Str_Single, POP);
    });

    // can escape newlines
    DEF.append("string", s -> {
      s.rule(re("\\\\.", "m"), Str_Escape);
    });

    // override: numbers are very different in json5
    DEF.state("constants", s -> {
      s.rule("\\b(?:true|false|null)\\b", Keyword_Constant);
      s.rule("[+-]?\\b(?:Infinity|NaN)\\b", Keyword_Constant);
      s.rule(re("[+-]?0x\\h+", "i"), Num_Hex);

      s.rule(re("[+-.]?[0-9]+[.]?[0-9]?([eE][-]?[0-9]+)?", "i"), Num_Float);
      s.rule("[+-]?\\d+e[+-]?\\d+", Num_Integer);
      s.rule(re("[+-]?(?:0|[1-9]\\d*)(?:e[+-]?\\d+)?", "i"), Num_Integer);
    });
  }

  public JSON5(Map<String, ?> opts) {
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
