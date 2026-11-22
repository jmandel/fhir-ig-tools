package org.hl7.fhir.jekyll.liquid;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Port of liquid-4.0.4/lib/liquid/lexer.rb (StringScanner based). */
public class Lexer {
  public enum Type {
    comparison, string, number, id, dotdot, pipe, dot, colon, comma, open_square, close_square, open_round, close_round, question, dash, end_of_string
  }

  /** A token: Ruby {@code [type, value]}. */
  public static final class Token {
    public final Type type;
    public final String value;

    Token(Type type, String value) {
      this.type = type;
      this.value = value;
    }

    /** Ruby {@code Array#to_s} of the token, e.g. {@code [:pipe, "|"]}. */
    @Override
    public String toString() {
      return value == null ? "[:" + type + "]" : "[:" + type + ", " + Values.inspect(value) + "]";
    }
  }

  private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][\\w-]*\\??", Pattern.UNIX_LINES);
  private static final Pattern SINGLE_STRING_LITERAL = Pattern.compile("'[^\\']*'", Pattern.UNIX_LINES);
  private static final Pattern DOUBLE_STRING_LITERAL = Pattern.compile("\"[^\\\"]*\"", Pattern.UNIX_LINES);
  private static final Pattern NUMBER_LITERAL = Pattern.compile("-?\\d+(\\.\\d+)?", Pattern.UNIX_LINES);
  private static final Pattern DOTDOT = Pattern.compile("\\.\\.", Pattern.UNIX_LINES);
  private static final Pattern COMPARISON_OPERATOR = Pattern.compile("==|!=|<>|<=?|>=?|contains(?=\\s)", Pattern.UNIX_LINES);
  private static final Pattern WHITESPACE_OR_NOTHING = Pattern.compile("\\s*", Pattern.UNIX_LINES);

  private final String input;
  private final Matcher m;
  private int pos;

  public Lexer(String input) {
    this.input = input;
    this.m = WHITESPACE_OR_NOTHING.matcher(input);
    this.m.useTransparentBounds(true);
    this.m.useAnchoringBounds(false);
  }

  private String scan(Pattern p) {
    m.usePattern(p);
    m.region(pos, input.length());
    if (m.lookingAt()) {
      pos = m.end();
      return m.group();
    }
    return null;
  }

  private static Type special(char c) {
    switch (c) {
      case '|': return Type.pipe;
      case '.': return Type.dot;
      case ':': return Type.colon;
      case ',': return Type.comma;
      case '[': return Type.open_square;
      case ']': return Type.close_square;
      case '(': return Type.open_round;
      case ')': return Type.close_round;
      case '?': return Type.question;
      case '-': return Type.dash;
      default: return null;
    }
  }

  public List<Token> tokenize() {
    List<Token> output = new ArrayList<>();
    while (pos < input.length()) {
      scan(WHITESPACE_OR_NOTHING);
      if (pos >= input.length()) {
        break;
      }
      String t;
      Token tok;
      if ((t = scan(COMPARISON_OPERATOR)) != null) {
        tok = new Token(Type.comparison, t);
      } else if ((t = scan(SINGLE_STRING_LITERAL)) != null) {
        tok = new Token(Type.string, t);
      } else if ((t = scan(DOUBLE_STRING_LITERAL)) != null) {
        tok = new Token(Type.string, t);
      } else if ((t = scan(NUMBER_LITERAL)) != null) {
        tok = new Token(Type.number, t);
      } else if ((t = scan(IDENTIFIER)) != null) {
        tok = new Token(Type.id, t);
      } else if ((t = scan(DOTDOT)) != null) {
        tok = new Token(Type.dotdot, t);
      } else {
        int cp = input.codePointAt(pos);
        String c = new String(Character.toChars(cp));
        pos += c.length();
        Type s = cp < 128 ? special((char) cp) : null;
        if (s != null) {
          tok = new Token(s, c);
        } else {
          throw LiquidException.syntax("Unexpected character " + c);
        }
      }
      output.add(tok);
    }
    output.add(new Token(Type.end_of_string, null));
    return output;
  }
}
