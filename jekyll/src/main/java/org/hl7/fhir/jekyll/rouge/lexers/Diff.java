package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.Map;
import java.util.Set;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;
import org.hl7.fhir.jekyll.rouge.RubyStrings;

/** Port of rouge-4.7.0/lib/rouge/lexers/diff.rb */
public class Diff extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("Diff");
  static final Definition DEF = new Definition(null);

  static {
    DEF.state("root", s -> {
      s.rule("^ .*$\\n?", Text);
      s.rule("^---$\\n?", Punctuation);

      s.rule(re("\n"
          + "          (^\\++.*$\\n?) |\n"
          + "          (^>+[ \\t]+.*$\\n?) |\n"
          + "          (^>+$\\n?)\n"
          + "        ", "x"), Generic_Inserted);

      s.rule(re("\n"
          + "          (^-+.*$\\n?) |\n"
          + "          (^<+[ \\t]+.*$\\n?) |\n"
          + "          (^<+$\\n?)\n"
          + "        ", "x"), Generic_Deleted);

      s.rule("^!.*$\\n?", Generic_Strong);
      s.rule("^([Ii]ndex|diff).*$\\n?", Generic_Heading);
      s.rule("^(@@[^@]*@@)([^\\n]*\\n)", (l, m) -> {
        l.groups(Punctuation, Text);
      });
      s.rule("^\\w.*$\\n?", Punctuation);
      s.rule("^=.*$\\n?", Generic_Heading);
      s.rule(".+$\\n?", Text);
    });
  }

  public Diff(Map<String, ?> opts) {
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
