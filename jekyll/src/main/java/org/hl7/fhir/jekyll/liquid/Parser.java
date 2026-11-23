package org.hl7.fhir.jekyll.liquid;

import java.util.List;

/** Port of liquid-4.0.4/lib/liquid/parser.rb (the strict-mode recursive descent parser). */
public class Parser {
  private final List<Lexer.Token> tokens;
  private int p;

  public Parser(String input) {
    this.tokens = new Lexer(input).tokenize();
    this.p = 0;
  }

  public void jump(int point) {
    p = point;
  }

  private Lexer.Token tok(int i) {
    if (i >= tokens.size()) {
      throw RubyError.noMethod("[]", null);
    }
    return tokens.get(i);
  }

  public String consume() {
    Lexer.Token token = tok(p);
    p += 1;
    return token.value;
  }

  public String consume(Lexer.Type type) {
    Lexer.Token token = tok(p);
    if (type != null && token.type != type) {
      throw LiquidException.syntax("Expected " + type + " but found " + token.type);
    }
    p += 1;
    return token.value;
  }

  /** Only consumes the token if it matches the type; returns its contents or null. */
  public String consumeIf(Lexer.Type type) {
    if (p >= tokens.size()) {
      return null;
    }
    Lexer.Token token = tokens.get(p);
    if (token.type != type) {
      return null;
    }
    p += 1;
    return token.value;
  }

  /** Like consumeIf except for an :id token of a certain name. */
  public String idIf(String str) {
    if (p >= tokens.size()) {
      return null;
    }
    Lexer.Token token = tokens.get(p);
    if (token.type != Lexer.Type.id || !token.value.equals(str)) {
      return null;
    }
    p += 1;
    return token.value;
  }

  public boolean look(Lexer.Type type) {
    return look(type, 0);
  }

  public boolean look(Lexer.Type type, int ahead) {
    if (p + ahead >= tokens.size()) {
      return false;
    }
    return tokens.get(p + ahead).type == type;
  }

  public String expression() {
    Lexer.Token token = tok(p);
    if (token.type == Lexer.Type.id) {
      return variableSignature();
    } else if (token.type == Lexer.Type.string || token.type == Lexer.Type.number) {
      return consume();
    } else if (token.type == Lexer.Type.open_round) {
      consume();
      String first = expression();
      consume(Lexer.Type.dotdot);
      String last = expression();
      consume(Lexer.Type.close_round);
      return "(" + first + ".." + last + ")";
    } else {
      throw LiquidException.syntax(token + " is not a valid expression");
    }
  }

  public String argument() {
    StringBuilder str = new StringBuilder();
    // might be a keyword argument (identifier: expression)
    if (look(Lexer.Type.id) && look(Lexer.Type.colon, 1)) {
      str.append(consume()).append(consume()).append(' ');
    }
    str.append(expression());
    return str.toString();
  }

  public String variableSignature() {
    StringBuilder str = new StringBuilder(consume(Lexer.Type.id));
    while (look(Lexer.Type.open_square)) {
      str.append(consume());
      str.append(expression());
      str.append(consume(Lexer.Type.close_square));
    }
    if (look(Lexer.Type.dot)) {
      str.append(consume());
      str.append(variableSignature());
    }
    return str.toString();
  }
}
