package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;

/** Port of rouge-4.7.0/lib/rouge/lexers/json_doc.rb */
public class JSONDOC extends JSON {
  public static final LexerClass CLASS = Lexer.byClassName("JSONDOC");
  static final Definition DEF = new Definition(JSON.DEF);

  static {
    DEF.prepend("name", s -> {
      s.rule("([$\\w]+)(\\s*)(:)", (l, m) -> {
        l.groups(Name_Attribute, Text, Punctuation);
      });
    });

    DEF.prepend("value", s -> {
      s.rule("/[*].*?[*]/", Comment);
      s.rule("//.*?$", Comment_Single);
      s.rule("(\\.\\.\\.)", Comment_Single);
    });
  }

  public JSONDOC(Map<String, ?> opts) {
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
