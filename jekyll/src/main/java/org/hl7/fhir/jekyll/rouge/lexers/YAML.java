package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.ArrayList;
import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;
import org.hl7.fhir.jekyll.rouge.RougeError;
import org.hl7.fhir.jekyll.rouge.RubyStrings;

/** Port of rouge-4.7.0/lib/rouge/lexers/yaml.rb (detect? is in Detectors) */
public class YAML extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("YAML");
  static final Definition DEF = new Definition(null);

  // Documentation: https://yaml.org/spec/1.2/spec.html

  // NB: Tabs are forbidden in YAML, which is why you see things
  // like /[ ]+/.

  ArrayList<Integer> indentStack;
  int nextIndent;
  Integer blockScalarIndent;

  /** reset the indentation levels */
  void resetIndent() {
    indentStack = new ArrayList<>();
    indentStack.add(0);
    nextIndent = 0;
    blockScalarIndent = null;
  }

  int indent() {
    if (indentStack.isEmpty()) {
      throw new RougeError("empty indent stack!");
    }
    return indentStack.get(indentStack.size() - 1);
  }

  boolean dedentP(int level) {
    return level < indent();
  }

  boolean indentP(int level) {
    return level > indent();
  }

  /** Save a possible indentation level; returns [text, err] */
  String[] saveIndent(String match) {
    nextIndent = RubyStrings.size(match);
    if (dedentP(nextIndent)) {
      while (dedentP(nextIndent)) {
        indentStack.remove(indentStack.size() - 1);
      }
      // dedenting to a state not previously indented to is an error
      return new String[] {RubyStrings.slice(match, 0, indent()), RubyStrings.from(match, indent())};
    } else {
      return new String[] {match, ""};
    }
  }

  void continueIndent(String match) {
    nextIndent += RubyStrings.size(match);
  }

  void setIndent(String match, boolean implicit) {
    if (indent() < nextIndent) {
      indentStack.add(nextIndent);
    }

    if (!implicit) {
      nextIndent += RubyStrings.size(match);
    }
  }

  static {
    final R plainScalarStart = re("[^ \\t\\n\\r\\f\\v?:,\\[\\]{}#&*!\\|>'\"%@`]");

    DEF.start(l -> ((YAML) l).resetIndent());

    DEF.state("basic", s -> {
      s.rule("#.*$", Comment_Single);
    });

    DEF.state("root", s -> {
      s.mixin("basic");

      s.rule("\\n+", Text);

      // trailing or pre-comment whitespace
      s.rule("[ ]+(?=#|$)", Text);

      s.rule("^%YAML\\b", (l, m) -> {
        l.token(Name_Tag);
        ((YAML) l).resetIndent();
        l.push("yaml_directive");
      });

      s.rule("^%TAG\\b", (l, m) -> {
        l.token(Name_Tag);
        ((YAML) l).resetIndent();
        l.push("tag_directive");
      });

      // doc-start and doc-end indicators
      s.rule("^(?:---|\\.\\.\\.)(?= |$)", (l, m) -> {
        l.token(Name_Namespace);
        ((YAML) l).resetIndent();
        l.push("block_line");
      });

      // indentation spaces
      s.rule("[ ]*(?!\\s|$)", (l, m) -> {
        String[] te = ((YAML) l).saveIndent(m.get(0));
        l.token(Text, te[0]);
        l.token(Error, te[1]);
        l.push("block_line");
        l.push("indentation");
      });
    });

    DEF.state("indentation", s -> {
      s.rule("\\s*?\\n", (l, m) -> {
        l.token(Text);
        l.pop(2);
      });
      // whitespace preceding block collection indicators
      s.rule("[ ]+(?=[-:?](?:[ ]|$))", (l, m) -> {
        l.token(Text);
        ((YAML) l).continueIndent(m.get(0));
      });

      // block collection indicators
      s.rule("[?:-](?=[ ]|$)", (l, m) -> {
        ((YAML) l).setIndent(m.get(0), false);
        l.token(Punctuation_Indicator);
      });

      // the beginning of a block line
      s.rule("[ ]*", (l, m) -> {
        l.token(Text);
        ((YAML) l).continueIndent(m.get(0));
        l.pop();
      });
    });

    // indented line in the block context
    DEF.state("block_line", s -> {
      // line end
      s.rule("[ ]*(?=#|$)", Text, POP);
      s.rule("[ ]+", Text);
      // tags, anchors, and aliases
      s.mixin("descriptors");
      // block collections and scalars
      s.mixin("block_nodes");
      // flow collections and quoed scalars
      s.mixin("flow_nodes");

      // a plain scalar
      s.rule("(?=" + plainScalarStart + "|[?:-][^ \\t\\n\\r\\f\\v])", (l, m) -> {
        l.token(Name_Variable);
        l.push("plain_scalar_in_block_context");
      });
    });

    DEF.state("descriptors", s -> {
      // a full-form tag
      s.rule("!<[0-9A-Za-z;\\/?:@&=+$,_.!~*'()\\[\\]%-]+>", Keyword_Type);

      // a tag in the form '!', '!suffix' or '!handle!suffix'
      s.rule(re("\n"
          + "          (?:![\\w-]+)? # handle\n"
          + "          !(?:[\\w;/?:@&=+$,.!~*\\'()\\[\\]%-]*) # suffix\n"
          + "        ", "x"), Keyword_Type);

      // an anchor
      s.rule("&[\\p{L}\\p{Nl}\\p{Nd}_-]+", Name_Label);

      // an alias
      s.rule("\\*[\\p{L}\\p{Nl}\\p{Nd}_-]+", Name_Variable);
    });

    DEF.state("block_nodes", s -> {
      // implicit key
      s.rule("([^#,?\\[\\]{}\"'\\n]+)(:)(?=\\s|$)", (l, m) -> {
        l.groups(Name_Attribute, Punctuation_Indicator);
        ((YAML) l).setIndent(m.get(0), true);
      });

      // literal and folded scalars
      s.rule("[\\|>][+-]?", (l, m) -> {
        l.token(Punctuation_Indicator);
        l.push("block_scalar_content");
        l.push("block_scalar_header");
      });
    });

    DEF.state("flow_nodes", s -> {
      s.rule("\\[", Punctuation_Indicator, "flow_sequence");
      s.rule("\\{", Punctuation_Indicator, "flow_mapping");
      s.rule("'", Str_Single, "single_quoted_scalar");
      s.rule("\"", Str_Double, "double_quoted_scalar");
    });

    DEF.state("flow_collection", s -> {
      s.rule(re("\\s+", "m"), Text);
      s.mixin("basic");
      s.rule("[?:,]", Punctuation_Indicator);
      s.mixin("descriptors");
      s.mixin("flow_nodes");

      s.rule("(?=" + plainScalarStart + ")", (l, m) -> {
        l.push("plain_scalar_in_flow_context");
      });
    });

    DEF.state("flow_sequence", s -> {
      s.rule("\\]", Punctuation_Indicator, POP);
      s.mixin("flow_collection");
    });

    DEF.state("flow_mapping", s -> {
      s.rule("\\}", Punctuation_Indicator, POP);
      s.mixin("flow_collection");
    });

    DEF.state("block_scalar_content", s -> {
      s.rule("\\n+", Text);

      // empty lines never dedent, but they might be part of the scalar.
      s.rule("^[ ]+$", (l, m) -> {
        YAML y = (YAML) l;
        String text = m.get(0);
        int indentSize = RubyStrings.size(text);

        int indentMark = y.blockScalarIndent != null ? y.blockScalarIndent : indentSize;

        l.token(Text, RubyStrings.slice(text, 0, indentMark));
        l.token(Name_Constant, RubyStrings.from(text, indentMark));
      });

      // TODO: ^ doesn't actually seem to affect the match at all.
      // Find a way to work around this limitation.
      s.rule("^[ ]*", (l, m) -> {
        YAML y = (YAML) l;
        l.token(Text);

        int indentSize = RubyStrings.size(m.get(0));

        int dedentLevel = y.blockScalarIndent != null ? y.blockScalarIndent : y.indent();
        if (y.blockScalarIndent == null) {
          y.blockScalarIndent = indentSize;
        }

        if (indentSize < dedentLevel) {
          y.saveIndent(m.get(0));
          l.pop();
          l.push("indentation");
        }
      });

      s.rule("[^\\n\\r\\f\\v]+", Str);
    });

    DEF.state("block_scalar_header", s -> {
      // optional indentation indicator and chomping flag, in either order
      s.rule(re("\n"
          + "          (\n"
          + "            ([1-9])[+-]? | [+-]?([1-9])?\n"
          + "          )(?=[ ]|$)\n"
          + "        ", "x"), (l, m) -> {
        YAML y = (YAML) l;
        y.blockScalarIndent = null;
        l.gotoState("ignored_line");
        if (m.get(0).isEmpty()) {
          return; // next
        }

        // NB: m[1] is the outer group (e.g. "-" or "2+"), so String#to_i applies
        String increment = m.get(1) != null ? m.get(1) : m.get(2);
        if (increment != null) {
          y.blockScalarIndent = (int) (y.indent() + RubyStrings.toI(increment));
        }

        l.token(Punctuation_Indicator);
      });
    });

    DEF.state("ignored_line", s -> {
      s.mixin("basic");
      s.rule("[ ]+", Text);
      s.rule("\\n", Text, POP);
    });

    DEF.state("quoted_scalar_whitespaces", s -> {
      // leading and trailing whitespace is ignored
      s.rule("^[ ]+", Text);
      s.rule("[ ]+$", Text);

      s.rule(re("\\n+", "m"), Text);

      s.rule("[ ]+", Name_Variable);
    });

    DEF.state("single_quoted_scalar", s -> {
      s.mixin("quoted_scalar_whitespaces");
      s.rule("\\\\'", Str_Escape);
      s.rule("'", Str, POP);
      s.rule("[^\\s']+", Str);
    });

    DEF.state("double_quoted_scalar", s -> {
      s.rule("\"", Str, POP);
      s.mixin("quoted_scalar_whitespaces");
      // escapes
      s.rule("\\\\[0abt\\tn\\nvfre \"\\\\N_LP]", Str_Escape);
      s.rule("\\\\(?:x[0-9A-Fa-f]{2}|u[0-9A-Fa-f]{4}|U[0-9A-Fa-f]{8})",
          Str_Escape);
      s.rule("[^ \\t\\n\\r\\f\\v\"\\\\]+", Str);
    });

    DEF.state("plain_scalar_in_block_context_new_line", s -> {
      s.rule("^[ ]+\\n", Text);
      s.rule(re("\\n+", "m"), Text);
      s.rule("^(?=---|\\.\\.\\.)", (l, m) -> {
        l.pop(3);
      });

      // dedent detection
      s.rule("^[ ]*", (l, m) -> {
        YAML y = (YAML) l;
        l.token(Text);
        l.pop();

        int indentSize = RubyStrings.size(m.get(0));

        // dedent = end of scalar
        if (indentSize <= y.indent()) {
          l.pop();
          y.saveIndent(m.get(0));
          l.push("indentation");
        }
      });
    });

    DEF.state("plain_scalar_in_block_context", s -> {
      // the : indicator ends a scalar
      s.rule("[ ]*(?=:[ \\n]|:$)", Text, POP);
      s.rule("[ ]*:\\S+", Str);
      s.rule("[ ]+(?=#)", Text, POP);
      s.rule("[ ]+$", Text);
      // check for new documents or dedents at the new line
      s.rule("\\n+", (l, m) -> {
        l.token(Text);
        l.push("plain_scalar_in_block_context_new_line");
      });

      s.rule("[ ]+", Str);
      s.rule("(true|false|null)\\b", Keyword_Constant);
      s.rule("\\d+(?:\\.\\d+)?(?=(\\r?\\n)| +#)", Literal_Number, POP);

      // regular non-whitespace characters
      s.rule("[^\\s:]+", Str);
    });

    DEF.state("plain_scalar_in_flow_context", s -> {
      s.rule("[ ]*(?=[,:?\\[\\]{}])", Text, POP);
      s.rule("[ ]+(?=#)", Text, POP);
      s.rule("^[ ]+", Text);
      s.rule("[ ]+$", Text);
      s.rule("\\n+", Text);
      s.rule("[ ]+", Name_Variable);
      s.rule("[^\\s,:?\\[\\]{}]+", Name_Variable);
    });

    DEF.state("yaml_directive", s -> {
      s.rule("([ ]+)(\\d+\\.\\d+)", (l, m) -> {
        l.groups(Text, Num);
        l.gotoState("ignored_line");
      });
    });

    DEF.state("tag_directive", s -> {
      s.rule(re("\n"
          + "          ([ ]+)(!|![\\w-]*!) # prefix\n"
          + "          ([ ]+)(!|!?[\\w;/?:@&=+$,.!~*'()\\[\\]%-]+) # tag handle\n"
          + "        ", "x"), (l, m) -> {
        l.groups(Text, Keyword_Type, Text, Keyword_Type);
        l.gotoState("ignored_line");
      });
    });
  }

  public YAML(Map<String, ?> opts) {
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
