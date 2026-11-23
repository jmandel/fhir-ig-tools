package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;

/** Port of rouge-4.7.0/lib/rouge/lexers/json.rb */
public class JSON extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("JSON");
  static final Definition DEF = new Definition(null);

  static {
    DEF.state("whitespace", s -> {
      s.rule("\\s+", Text_Whitespace);
    });

    DEF.state("root", s -> {
      s.mixin("whitespace");
      s.rule("{", Punctuation, "object");
      s.rule("\\[", Punctuation, "array");

      s.mixin("name");
      s.mixin("value");

      // These characters may be invalid but syntax correctness is a non-goal
      s.rule("[\\]}]", Punctuation);
    });

    DEF.state("object", s -> {
      s.mixin("whitespace");
      s.mixin("name");
      s.mixin("value");
      s.rule("}", Punctuation, POP);
      s.rule(",", Punctuation);
    });

    DEF.state("name", s -> {
      // equivalent: each unit is either \\. or one [^"\\\n] char and the unit at a position is
      // determined by its first char, so the lazy loop has exactly one candidate end (the first
      // unescaped quote); possessive loops find the same match without recursing per character.
      s.rule(re("(\"(?:\\\\.|[^\"\\\\\\n])*?\")(\\s*)(:)").eq("(\"[^\"\\\\\\n]*+(?:\\\\.[^\"\\\\\\n]*+)*+\")(\\s*)(:)"), (l, m) -> {
        l.groups(Name_Label, Text_Whitespace, Punctuation);
      });
    });

    DEF.state("value", s -> {
      s.mixin("whitespace");
      s.mixin("constants");
      s.rule("\"", Str_Double, "string");
      s.rule("\\[", Punctuation, "array");
      s.rule("{", Punctuation, "object");
    });

    DEF.state("string", s -> {
      s.rule("[^\\\\\"]+", Str_Double);
      s.rule("\\\\.", Str_Escape);
      s.rule("\"", Str_Double, POP);
    });

    DEF.state("array", s -> {
      s.mixin("value");
      s.rule("\\]", Punctuation, POP);
      s.rule(",", Punctuation);
    });

    DEF.state("constants", s -> {
      s.rule("(?:true|false|null)", Keyword_Constant);
      s.rule(re("-?(?:0|[1-9]\\d*)\\.\\d+(?:e[+-]?\\d+)?", "i"), Num_Float);
      s.rule(re("-?(?:0|[1-9]\\d*)(?:e[+-]?\\d+)?", "i"), Num_Integer);
    });
  }

  public JSON(Map<String, ?> opts) {
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
