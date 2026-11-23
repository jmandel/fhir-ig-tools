package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.Map;
import java.util.Set;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;
import org.hl7.fhir.jekyll.rouge.RubyStrings;

/** Port of rouge-4.7.0/lib/rouge/lexers/sparql.rb */
public class SPARQL extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("SPARQL");
  static final Definition DEF = new Definition(null);

  static final Set<String> BUILTINS = Words.set(Words.w("ABS AVG BNODE BOUND CEIL COALESCE CONCAT CONTAINS COUNT DATATYPE DAY ENCODE_FOR_URI FLOOR GROUP_CONCAT HOURS IF IRI isBLANK isIRI isLITERAL isNUMERIC isURI LANG LANGMATCHES LCASE MAX MD5 MIN MINUTES MONTH NOW RAND REGEX REPLACE ROUND SAMETERM SAMPLE SECONDS SEPARATOR SHA1 SHA256 SHA384 SHA512 STR STRAFTER STRBEFORE STRDT STRENDS STRLANG STRLEN STRSTARTS STRUUID SUBSTR SUM TIMEZONE TZ UCASE URI UUID YEAR"));

  static final Set<String> KEYWORDS = Words.set(Words.w("ADD ALL AS ASC ASK BASE BIND BINDINGS BY CLEAR CONSTRUCT COPY CREATE DATA DEFAULT DELETE DESC DESCRIBE DISTINCT DROP EXISTS FILTER FROM GRAPH GROUP BY HAVING IN INSERT LIMIT LOAD MINUS MOVE NAMED NOT OFFSET OPTIONAL ORDER PREFIX SELECT REDUCED SERVICE SILENT TO UNDEF UNION USING VALUES WHERE WITH"));

  static {
    DEF.state("root", s -> {
      s.rule(re("\\s+", "m"), Text_Whitespace);
      s.rule("#.*", Comment_Single);

      s.rule("\"\"\"", Str_Double, "string_double_literal");
      s.rule("\"", Str_Double, "string_double");
      s.rule("'''", Str_Single, "string_single_literal");
      s.rule("'", Str_Single, "string_single");

      s.rule("[$?][[:word:]]+", Name_Variable);
      s.rule("([[:word:]-]*)(:)([[:word:]-]+)?", (l, m) -> {
        l.token(Name_Namespace, m.get(1));
        l.token(Operator, m.get(2));
        l.token(Str_Symbol, m.get(3));
      });
      s.rule("<[^>]*>", Name_Namespace);
      s.rule(re("true|false", "i"), Keyword_Constant);
      s.rule("a\\b", Keyword);

      s.rule(re("[A-Z][[:word:]]+\\b", "i"), (l, m) -> {
        String u = RubyStrings.upcase(m.get(0));
        if (BUILTINS.contains(u)) {
          l.token(Name_Builtin);
        } else if (KEYWORDS.contains(u)) {
          l.token(Keyword);
        } else {
          l.token(Error);
        }
      });

      s.rule(re("[+\\-]?(?:\\d+\\.\\d*|\\.\\d+)(?:[e][+\\-]?[0-9]+)?", "i"), Num_Float);
      s.rule("[+\\-]?\\d+", Num_Integer);
      s.rule("[\\]\\[(){}.,;=]", Punctuation);
      s.rule("[/?*+=!<>]|&&|\\|\\||\\^\\^", Operator);
    });

    DEF.state("string_double_common", s -> {
      s.mixin("string_escapes");
      s.rule("\\\\", Str_Double);
      s.rule("[^\"\\\\]+", Str_Double);
    });

    DEF.state("string_double", s -> {
      s.rule("\"", (l, m) -> {
        l.token(Str_Double);
        l.gotoState("string_end");
      });
      s.mixin("string_double_common");
    });

    DEF.state("string_double_literal", s -> {
      s.rule("\"\"\"", (l, m) -> {
        l.token(Str_Double);
        l.gotoState("string_end");
      });
      s.rule("\"", Str_Double);
      s.mixin("string_double_common");
    });

    DEF.state("string_single_common", s -> {
      s.mixin("string_escapes");
      s.rule("\\\\", Str_Single);
      s.rule("[^'\\\\]+", Str_Single);
    });

    DEF.state("string_single", s -> {
      s.rule("'", (l, m) -> {
        l.token(Str_Single);
        l.gotoState("string_end");
      });
      s.mixin("string_single_common");
    });

    DEF.state("string_single_literal", s -> {
      s.rule("'''", (l, m) -> {
        l.token(Str_Single);
        l.gotoState("string_end");
      });
      s.rule("'", Str_Single);
      s.mixin("string_single_common");
    });

    DEF.state("string_escapes", s -> {
      s.rule("\\\\[tbnrf\"'\\\\]", Str_Escape);
      s.rule("\\\\u\\h{4}", Str_Escape);
      s.rule("\\\\U\\h{8}", Str_Escape);
    });

    DEF.state("string_end", s -> {
      s.rule("(@)([a-zA-Z]+(?:-[a-zA-Z0-9]+)*)", (l, m) -> {
        l.groups(Operator, Name_Property);
      });
      s.rule("\\^\\^", Operator);
      s.rule("", (l, m) -> l.pop());
    });
  }

  public SPARQL(Map<String, ?> opts) {
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
