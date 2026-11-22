package org.hl7.fhir.jekyll.rouge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Port of rouge-4.7.0/lib/rouge/token.rb. A token type is a node in the Pygments-compatible token
 * tree (qualname "Name.Tag", shortname "nt"). The constants live in {@link Tokens}.
 */
public final class Token {
  private static final Map<String, Token> CACHE = new ConcurrentHashMap<>();

  public final String name;
  public final Token parent;
  public final String shortname;
  public final String qualname;

  private Token(Token parent, String name, String shortname) {
    this.parent = parent;
    this.name = name;
    this.shortname = shortname;
    this.qualname = parent == null ? name : parent.qualname + "." + name;
    CACHE.put(qualname, this); // register!
  }

  static Token make(Token parent, String name, String shortname) {
    return new Token(parent, name, shortname);
  }

  /** Token[qualname] */
  public static Token get(String qualname) {
    Tokens.Text.getClass(); // make sure the tree is initialized
    return CACHE.get(qualname);
  }

  /** Token.matches?(other): other is this token or a sub-token of it. */
  public boolean matches(Token other) {
    for (Token t = other; t != null; t = t.parent) {
      if (t == this) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return "<Token " + qualname + ">";
  }
}
