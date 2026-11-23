package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;

/** Port of rouge-4.7.0/lib/rouge/lexers/html.rb (detect? is in Detectors) */
public class HTML extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("HTML");
  static final Definition DEF = new Definition(null);

  Javascript javascript;
  CSS css;
  Lexer lang;

  static {
    DEF.start(l -> {
      HTML h = (HTML) l;
      h.javascript = new Javascript(h.options);
      h.css = new CSS(h.options);
    });

    DEF.state("root", s -> {
      s.rule(re("[^<&]+", "m"), Text);
      s.rule("&\\S*?;", Name_Entity);
      s.rule(re("<!DOCTYPE .*?>", "im"), Comment_Preproc);
      s.rule(re("<!\\[CDATA\\[.*?\\]\\]>", "m"), Comment_Preproc);
      s.rule("<!--", Comment, "comment");
      s.rule(re("<\\?.*?\\?>", "m"), Comment_Preproc); // php? really?

      s.rule(re("<\\s*script\\s*", "m"), (l, m) -> {
        HTML h = (HTML) l;
        l.token(Name_Tag);
        h.javascript.reset();
        l.push("script_content");
        l.push("tag");
      });

      s.rule(re("<\\s*style\\s*", "m"), (l, m) -> {
        HTML h = (HTML) l;
        l.token(Name_Tag);
        h.css.reset();
        h.lang = h.css;
        l.push("style_content");
        l.push("tag");
      });

      s.rule("</", Name_Tag, "tag_end");
      s.rule("<", Name_Tag, "tag_start");

      s.rule("<\\s*[\\p{L}:_-][\\p{Word}\\p{Cf}:.·-]*", Name_Tag, "tag"); // opening tags
      s.rule("<\\s*/\\s*[\\p{L}:_-][\\p{Word}\\p{Cf}:.·-]*\\s*>", Name_Tag); // closing tags
    });

    DEF.state("tag_end", s -> {
      s.mixin("tag_end_end");
      s.rule("[\\p{L}:_-][\\p{Word}\\p{Cf}:.·-]*", (l, m) -> {
        l.token(Name_Tag);
        l.gotoState("tag_end_end");
      });
    });

    DEF.state("tag_end_end", s -> {
      s.rule("\\s+", Text);
      s.rule(">", Name_Tag, POP);
    });

    DEF.state("tag_start", s -> {
      s.rule("\\s+", Text);

      s.rule("[\\p{L}:_-][\\p{Word}\\p{Cf}:.·-]*", (l, m) -> {
        l.token(Name_Tag);
        l.gotoState("tag");
      });

      s.rule("", (l, m) -> l.gotoState("tag"));
    });

    DEF.state("comment", s -> {
      s.rule("[^-]+", Comment);
      s.rule("-->", Comment, POP);
      s.rule("-", Comment);
    });

    DEF.state("tag", s -> {
      s.rule(re("\\s+", "m"), Text);
      s.rule(re("[\\p{L}:_\\[\\]()*.-][\\p{Word}\\p{Cf}:.·\\[\\]()*-]*\\s*=\\s*", "m"), Name_Attribute, "attr");
      s.rule("[\\p{L}:_*#-][\\p{Word}\\p{Cf}:.·*#-]*", Name_Attribute);
      s.rule(re("/?\\s*>", "m"), Name_Tag, POP);
    });

    DEF.state("attr", s -> {
      // TODO: are backslash escapes valid here?
      s.rule("\"", (l, m) -> {
        l.token(Str);
        l.gotoState("dq");
      });

      s.rule("'", (l, m) -> {
        l.token(Str);
        l.gotoState("sq");
      });

      s.rule("[^\\s>]+", Str, POP);
    });

    DEF.state("dq", s -> {
      s.rule("\"", Str, POP);
      s.rule("[^\"]+", Str);
    });

    DEF.state("sq", s -> {
      s.rule("'", Str, POP);
      s.rule("[^']+", Str);
    });

    DEF.state("script_content", s -> {
      s.rule("[^<]+", (l, m) -> l.delegate(((HTML) l).javascript));

      s.rule(re("<\\s*/\\s*script\\s*>", "m"), Name_Tag, POP);

      s.rule("<", (l, m) -> l.delegate(((HTML) l).javascript));
    });

    DEF.state("style_content", s -> {
      s.rule("[^<]+", (l, m) -> l.delegate(((HTML) l).lang));

      s.rule(re("<\\s*/\\s*style\\s*>", "m"), Name_Tag, POP);

      s.rule("<", (l, m) -> l.delegate(((HTML) l).lang));
    });
  }

  public HTML(Map<String, ?> opts) {
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
