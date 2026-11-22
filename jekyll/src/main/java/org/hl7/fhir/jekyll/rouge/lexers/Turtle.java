package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.Map;
import java.util.Set;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;
import org.hl7.fhir.jekyll.rouge.RubyStrings;

/** Port of rouge-4.7.0/lib/rouge/lexers/turtle.rb */
public class Turtle extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("Turtle");
  static final Definition DEF = new Definition(null);

  static {
    DEF.state("root", s -> {
      s.rule("@base\\b", Keyword_Declaration);
      s.rule("@prefix\\b", Keyword_Declaration);
      s.rule("true\\b", Keyword_Constant);
      s.rule("false\\b", Keyword_Constant);

      s.rule(re("\"\"\".*?\"\"\"", "m"), Literal_String);
      s.rule("\"([^\"\\\\]|\\\\.)*\"", Literal_String);
      s.rule(re("'''.*?'''", "m"), Literal_String);
      s.rule("'([^'\\\\]|\\\\.)*'", Literal_String);

      s.rule("#.*$", Comment_Single);

      s.rule("@[^\\s,.;]+", Name_Attribute);

      s.rule("[+-]?[0-9]+\\.[0-9]*E[+-]?[0-9]+", Literal_Number_Float);
      s.rule("[+-]?\\.[0-9]+E[+-]?[0-9]+", Literal_Number_Float);
      s.rule("[+-]?[0-9]+E[+-]?[0-9]+", Literal_Number_Float);

      s.rule("[+-]?[0-9]*\\.[0-9]+?", Literal_Number_Float);

      s.rule("[+-]?[0-9]+", Literal_Number_Integer);

      s.rule("\\.", Punctuation);
      s.rule(",", Punctuation);
      s.rule(";", Punctuation);
      s.rule("\\(", Punctuation);
      s.rule("\\)", Punctuation);
      s.rule("\\{", Punctuation);
      s.rule("\\}", Punctuation);
      s.rule("\\[", Punctuation);
      s.rule("\\]", Punctuation);
      s.rule("\\^\\^", Punctuation);

      s.rule("<[^>]*>", Name_Label);

      s.rule(re("base\\b", "i"), Keyword_Declaration);
      s.rule(re("prefix\\b", "i"), Keyword_Declaration);
      s.rule("GRAPH\\b", Keyword);
      s.rule("a\\b", Keyword);

      s.rule("\\s+", Text_Whitespace);

      s.rule("[^:;<>#\\@\"\\(\\).\\[\\]\\{\\} ]*:", Name_Namespace);
      s.rule("[^:;<>#\\@\"\\(\\).\\[\\]\\{\\} ]+", Name);
    });
  }

  public Turtle(Map<String, ?> opts) {
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
