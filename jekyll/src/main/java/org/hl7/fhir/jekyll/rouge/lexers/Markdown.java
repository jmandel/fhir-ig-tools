package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.LinkedHashMap;
import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;
import org.hl7.fhir.jekyll.rouge.RougeError;
import org.hl7.fhir.jekyll.rouge.RubyStrings;

/**
 * Port of rouge-4.7.0/lib/rouge/lexers/markdown.rb. Fenced code blocks are delegated to
 * <code>Lexer.find_fancy(lang, code, @options)</code>, so they get whatever lexer Rouge picks; a lexer
 * that is not ported here yields plain Text (see NOTES-rouge.md).
 */
public class Markdown extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("Markdown");
  static final Definition DEF = new Definition(null);

  HTML html;

  HTML html() {
    if (html == null) {
      html = new HTML(options);
    }
    return html;
  }

  static {
    DEF.start(l -> ((Markdown) l).html().reset());

    final R edot = re("\\\\.|[^\\\\\\n]");

    DEF.state("root", s -> {
      // YAML frontmatter
      s.rule(re("\\A(---\\s*\\n.*?\\n?)^(---\\s*$\\n?)", "m"), (l, m) -> l.delegate(YAML.CLASS));

      s.rule("\\\\.", Str_Escape);

      s.rule("^[\\S ]+\\n(?:---*)\\n", Generic_Heading);
      s.rule("^[\\S ]+\\n(?:===*)\\n", Generic_Subheading);

      s.rule("^#(?=[^#]).*?$", Generic_Heading);
      s.rule("^##*.*?$", Generic_Subheading);

      s.rule(re("^([ \\t]*)(`{3,}|~{3,})([^\\n]*\\n)((.*?)(\\n\\1)(\\2))?", "m"), (l, m) -> {
        Markdown md = (Markdown) l;
        String name = RubyStrings.strip(m.get(3));
        Lexer sublexer;
        try {
          sublexer = Lexer.findFancy(name.isEmpty() ? "guess" : name, m.get(5), md.options);
        } catch (RougeError.Ambiguous e) {
          sublexer = e.alternatives.get(0).newLexer(md.options);
        }

        if (sublexer == null) {
          Map<String, Object> o = new LinkedHashMap<>(md.options);
          o.put("token", Str_Backtick);
          sublexer = new PlainText(o);
        }
        sublexer.reset();

        l.token(Text, m.get(1));
        l.token(Punctuation, m.get(2));
        l.token(Name_Label, m.get(3));
        if (m.get(5) != null) {
          l.delegate(sublexer, m.get(5));
        }

        l.token(Text, m.get(6));

        if (m.get(7) != null) {
          l.token(Punctuation, m.get(7));
        } else {
          final Lexer sub = sublexer;
          final String fence = m.get(2);
          l.push(st -> {
            st.rule("^([ \\t]*)(" + fence + ")", (l2, mb) -> {
              l2.pop();
              l2.token(Text, mb.get(1));
              l2.token(Punctuation, mb.get(2));
            });
            st.rule("^.*\\n", (l2, mb) -> {
              l2.delegate(sub, mb.get(1));
            });
          });
        }
      });

      s.rule("\\n\\n((    |\\t).*?\\n|\\n)+", Str_Backtick);

      s.rule("(`+)(?:" + edot + "|\\n)+?\\1", Str_Backtick);

      // various uses of * are in order of precedence

      // line breaks
      s.rule("^(\\s*[*]){3,}\\s*$", Punctuation);
      s.rule("^(\\s*[-]){3,}\\s*$", Punctuation);

      // bulleted lists
      s.rule("^\\s*[*+-](?=\\s)", Punctuation);

      // numbered lists
      s.rule("^\\s*\\d+\\.", Punctuation);

      // blockquotes
      s.rule("^\\s*>.*?$", Generic_Traceback);

      // link references
      // [foo]: bar "baz"
      s.rule(re("^\n"
          + "          (\\s*) # leading whitespace\n"
          + "          (\\[) (" + edot + "+?) (\\]) # the reference\n"
          + "          (\\s*) (:) # colon\n"
          + "        ", "x"), (l, m) -> {
        l.groups(Text, Punctuation, Str_Symbol, Punctuation, Text, Punctuation);

        l.push("title");
        l.push("url");
      });

      // links and images
      s.rule("(!?\\[)(" + edot + "*?|[^\\]]*?)(\\])(?=[\\[(])", (l, m) -> {
        l.groups(Punctuation, Name_Variable, Punctuation);
        l.push("link");
      });

      s.rule("[*]{2}[^* \\n][^*\\n]*[*]{2}", Generic_Strong);
      s.rule("[*]{3}[^* \\n][^*\\n]*[*]{3}", Generic_EmphStrong);
      s.rule("__" + edot + "*?__", Generic_Strong);

      s.rule("[*]" + edot + "*?[*]", Generic_Emph);
      s.rule("_" + edot + "*?_", Generic_Emph);

      // Automatic links
      s.rule("<.*?@.+[.].+>", Name_Variable);
      s.rule("<(https?|mailto|ftp)://" + edot + "*?>", Name_Variable);

      s.rule("[^\\\\`\\[*\\n&<]+", Text);

      // inline html
      s.rule("&\\S*;", (l, m) -> l.delegate(((Markdown) l).html()));
      s.rule("<" + edot + "*?>", (l, m) -> l.delegate(((Markdown) l).html()));
      s.rule("[&<]", Text);

      // An opening square bracket that is not a link
      s.rule("\\[", Text);

      s.rule("\\n", Text);
    });

    DEF.state("link", s -> {
      s.rule("(\\[)(" + edot + "*?)(\\])", (l, m) -> {
        l.groups(Punctuation, Str_Symbol, Punctuation);
        l.pop();
      });

      s.rule("[(]", (l, m) -> {
        l.token(Punctuation);
        l.push("inline_title");
        l.push("inline_url");
      });

      s.rule("[ \\t]+", Text);

      s.rule("", (l, m) -> l.pop());
    });

    DEF.state("url", s -> {
      s.rule("[ \\t]+", Text);

      // the url
      s.rule("(<)(" + edot + "*?)(>)", (l, m) -> {
        l.groups(Name_Tag, Str_Other, Name_Tag);
        l.pop();
      });

      s.rule("\\S+", Str_Other, POP);
    });

    DEF.state("title", s -> {
      s.rule("\"" + edot + "*?\"", Name_Namespace);
      s.rule("'" + edot + "*?'", Name_Namespace);
      s.rule("[(]" + edot + "*?[)]", Name_Namespace);
      s.rule("\\s*(?=[\"'()])", Text);
      s.rule("", (l, m) -> l.pop());
    });

    DEF.state("inline_title", s -> {
      s.rule("[)]", Punctuation, POP);
      s.mixin("title");
    });

    DEF.state("inline_url", s -> {
      s.rule("[^<\\s)]+", Str_Other, POP);
      s.rule(re("\\s+", "m"), Text);
      s.mixin("url");
    });
  }

  public Markdown(Map<String, ?> opts) {
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
