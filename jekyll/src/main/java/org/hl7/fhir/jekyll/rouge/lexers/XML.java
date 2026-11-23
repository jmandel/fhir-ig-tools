package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;

/** Port of rouge-4.7.0/lib/rouge/lexers/xml.rb (detect? is in Detectors) */
public class XML extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("XML");
  static final Definition DEF = new Definition(null);

  static {
    DEF.state("root", s -> {
      s.rule("[^<&]+", Text);
      s.rule("&\\S*?;", Name_Entity);
      s.rule("<!\\[CDATA\\[.*?\\]\\]\\>", Comment_Preproc);
      s.rule("<!--", Comment, "comment");
      s.rule("<\\?.*?\\?>", Comment_Preproc);
      s.rule("<![^>]*>", Comment_Preproc);

      // open tags
      s.rule(re("<\\s*[\\p{L}:_][\\p{Word}\\p{Cf}:.·-]*", "m"), Name_Tag, "tag");

      // self-closing tags
      s.rule(re("<\\s*/\\s*[\\p{L}:_][\\p{Word}\\p{Cf}:.·-]*\\s*>", "m"), Name_Tag);
    });

    DEF.state("comment", s -> {
      s.rule(re("[^-]+", "m"), Comment);
      s.rule("-->", Comment, POP);
      s.rule("-", Comment);
    });

    DEF.state("tag", s -> {
      s.rule(re("\\s+", "m"), Text);
      s.rule(re("[\\p{L}:_][\\p{Word}\\p{Cf}:.·-]*\\s*=", "m"), Name_Attribute, "attr");
      s.rule("/?\\s*>", Name_Tag, POP);
    });

    DEF.state("attr", s -> {
      s.rule(re("\\s+", "m"), Text);
      s.rule(re("\".*?\"|'.*?'|[^\\s>]+", "m"), Str, POP);
    });
  }

  public XML(Map<String, ?> opts) {
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
