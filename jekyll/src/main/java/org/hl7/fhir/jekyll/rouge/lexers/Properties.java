package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.Map;
import java.util.Set;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;
import org.hl7.fhir.jekyll.rouge.RubyStrings;

/** Port of rouge-4.7.0/lib/rouge/lexers/properties.rb */
public class Properties extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("Properties");
  static final Definition DEF = new Definition(null);

  static {
    final R identifier = re("[\\w.-]+");

    DEF.state("basic", s -> {
      s.rule("[!#].*?\\n", Comment);
      s.rule("\\s+", Text);
      s.rule("\\\\\\n", Str_Escape);
    });

    DEF.state("root", s -> {
      s.mixin("basic");

      s.rule("(" + identifier + ")(\\s*)([=:])", (l, m) -> {
        l.groups(Name_Property, Text, Punctuation);
        l.push("value");
      });
    });

    DEF.state("value", s -> {
      s.rule("\\n", Text, POP);
      s.mixin("basic");
      s.rule("\"", Str, "dq");
      s.rule("'.*?'", Str);
      s.mixin("esc_str");
      s.rule("[^\\\\\\n]+", Str);
    });

    DEF.state("dq", s -> {
      s.rule("\"", Str, POP);
      s.mixin("esc_str");
      s.rule(re("[^\\\\\"]+", "m"), Str);
    });

    DEF.state("esc_str", s -> {
      s.rule("\\\\u[0-9]{4}", Str_Escape);
      s.rule(re("\\\\.", "m"), Str_Escape);
    });
  }

  public Properties(Map<String, ?> opts) {
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
