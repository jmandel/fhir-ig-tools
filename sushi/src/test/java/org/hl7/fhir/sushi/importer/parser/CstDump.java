package org.hl7.fhir.sushi.importer.parser;

import java.util.List;

/**
 * Canonical text dump of a parse (tokens, tree, errors) for differential comparison between parser implementations
 * (hand-written, ANTLR Java oracle, and the JS runtime via scripts/wp1/js-dump.js, which prints the same format).
 */
public final class CstDump {
  private CstDump() {}

  public static String esc(String s) {
    if (s == null) return "null";
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      switch (c) {
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        case '\'' -> sb.append("\\'");
        default -> {
          if (c < 0x20 || c > 0x7e) sb.append(String.format("\\u%04x", (int) c));
          else sb.append(c);
        }
      }
    }
    return sb.toString();
  }

  public static String token(Token t) {
    if (t == null) return "null";
    return t.getTokenIndex() + ":" + t.getType() + ":" + t.getChannel() + ":" + t.getStartIndex() + "-" + t.getStopIndex()
        + ":" + t.getLine() + "." + t.getCharPositionInLine() + "'" + esc(t.getText()) + "'";
  }

  public static String tokens(List<Token> tokens) {
    StringBuilder sb = new StringBuilder();
    for (Token t : tokens) sb.append(token(t)).append('\n');
    return sb.toString();
  }

  public static String tree(ParseTree t, String[] ruleNames) {
    StringBuilder sb = new StringBuilder();
    tree(t, ruleNames, 0, sb);
    return sb.toString();
  }

  private static void tree(ParseTree t, String[] ruleNames, int depth, StringBuilder sb) {
    sb.append("  ".repeat(depth));
    if (t instanceof ErrorNode e) {
      sb.append("!ERR ").append(token(e.getSymbol())).append('\n');
    } else if (t instanceof TerminalNode n) {
      sb.append(token(n.getSymbol())).append('\n');
    } else {
      ParserRuleContext c = (ParserRuleContext) t;
      sb.append('(').append(ruleNames[c.getRuleIndex()]).append(" start=").append(idx(c.start))
          .append(" stop=").append(idx(c.stop));
      if (c.exception != null) {
        sb.append(" exc=").append(c.exception.getKind()).append('@').append(idx(c.exception.getOffendingToken()));
      }
      sb.append('\n');
      for (int i = 0; i < c.getChildCount(); i++) tree(c.getChild(i), ruleNames, depth + 1, sb);
    }
  }

  private static String idx(Token t) {
    return t == null ? "null" : t.getTokenIndex() < 0 ? "conj(" + token(t) + ")" : String.valueOf(t.getTokenIndex());
  }

  /** An error listener that records errors in the dump format. */
  public static final class Recorder implements ErrorListener {
    public final StringBuilder out = new StringBuilder();
    public int count;

    @Override public void syntaxError(Recognizer r, Token t, int line, int column, String msg) {
      count++;
      out.append(line).append(':').append(column).append(' ').append(t == null ? "lexer" : idx(t)).append(' ')
          .append(esc(msg)).append('\n');
    }
  }
}
