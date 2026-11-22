package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.List;
import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;
import org.hl7.fhir.jekyll.rouge.RougeError;
import org.hl7.fhir.jekyll.rouge.RubyStrings;

/** Port of rouge-4.7.0/lib/rouge/lexers/http.rb */
public class HTTP extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("HTTP");
  static final Definition DEF = new Definition(null);

  static final List<String> HTTP_METHODS = List.of("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "TRACE", "PATCH", "QUERY");

  String contentType;
  /** @content_lexer: a Lexer instance (content option) or a LexerClass (guessed) */
  Object contentLexer;

  Object contentLexer() {
    if (contentLexer == null) {
      Lexer opt = lexerOption("content", null);
      contentLexer = opt != null ? opt : guessContentLexer();
    }
    return contentLexer;
  }

  Object guessContentLexer() {
    if (contentType == null) {
      return PlainText.CLASS;
    }
    try {
      return Lexer.guessByMimetype(contentType);
    } catch (RougeError.Ambiguous e) {
      // `rescue Lexer::AmbiguousGuess` names a constant that does not exist
      throw new RougeError("NameError: uninitialized constant Rouge::Lexer::AmbiguousGuess");
    }
  }

  static {
    DEF.start(l -> ((HTTP) l).contentType = "text/plain");

    DEF.state("root", s -> {
      // request
      s.rule(re("\n"
          + "          (" + String.join("|", HTTP_METHODS) + ")([ ]+) # method\n"
          + "          ([^ ]+)([ ]+)                          # path\n"
          + "          (HTTPS?)(/)(\\d(?:\\.\\d)?)(\\r?\\n|$)      # http version\n"
          + "        ", "x"), (l, m) -> {
        l.groups(
            Name_Function, Text,
            Name_Namespace, Text,
            Keyword, Operator, Num, Text);

        l.push("headers");
      });

      // response
      s.rule(re("\n"
          + "          (HTTPS?)(/)(\\d(?:\\.\\d)?)([ ]+) # http version\n"
          + "          (\\d{3})([ ]+)?                 # status\n"
          + "          ([^\\r\\n]*)?(\\r?\\n|$)           # status message\n"
          + "        ", "x"), (l, m) -> {
        l.groups(
            Keyword, Operator, Num, Text,
            Num, Text,
            Name_Exception, Text);
        l.push("headers");
      });
    });

    DEF.state("headers", s -> {
      s.rule("([^\\s:]+)( *)(:)( *)([^\\r\\n]+)(\\r?\\n|$)", (l, m) -> {
        String key = m.get(1);
        String value = m.get(5);
        if (RubyStrings.casecmp(RubyStrings.strip(key), "content-type") == 0) {
          List<String> parts = RubyStrings.split(value, ";");
          if (parts.isEmpty()) {
            throw new RougeError("NoMethodError: undefined method `downcase' for nil:NilClass");
          }
          ((HTTP) l).contentType = RubyStrings.downcase(parts.get(0));
        }

        l.groups(Name_Attribute, Text, Punctuation, Text, Str, Text);
      });

      s.rule("([^\\r\\n]+)(\\r?\\n|$)", (l, m) -> {
        l.groups(Str, Text);
      });

      s.rule("\\r?\\n", Text, "content");
    });

    DEF.state("content", s -> {
      s.rule(re(".+", "m"), (l, m) -> {
        Object c = ((HTTP) l).contentLexer();
        if (c instanceof Lexer) {
          l.delegate((Lexer) c);
        } else {
          l.delegate((LexerClass) c);
        }
      });
    });
  }

  public HTTP(Map<String, ?> opts) {
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
