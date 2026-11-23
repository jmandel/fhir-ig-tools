package org.hl7.fhir.sushi.importer.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * A rule node of the concrete syntax tree. Mirrors ANTLR's ParserRuleContext (public fields {@code start},
 * {@code stop}, {@code children}, {@code exception}; the typed accessors of the generated contexts are built on
 * {@link #getToken}/{@link #getRuleContext}). The node classes themselves are nested in {@link FSHParser} and
 * {@link MiniFSHParser} with ANTLR's names ({@code FSHParser.SdRuleContext}, ...).
 */
public abstract class ParserRuleContext implements ParseTree {
  public ParserRuleContext parent;
  /** The ATN state that invoked this rule (-1 for the start rule); kept for parity with ANTLR, not used by SUSHI. */
  public int invokingState;
  public List<ParseTree> children;
  /** First token of the rule (TS {@code ctx.start}). */
  public Token start;
  /** Last token of the rule (TS {@code ctx.stop}) = the token before the next one at rule exit; may precede start. */
  public Token stop;
  /** The syntax error this rule caught and recovered from, if any. */
  public RecognitionException exception;

  protected ParserRuleContext(ParserRuleContext parent, int invokingState) {
    this.parent = parent;
    this.invokingState = invokingState;
  }

  public abstract int getRuleIndex();

  @Override public ParserRuleContext getParent() { return parent; }
  public Token getStart() { return start; }
  public Token getStop() { return stop; }

  @Override public int getChildCount() { return children == null ? 0 : children.size(); }
  @Override public ParseTree getChild(int i) {
    return children == null || i < 0 || i >= children.size() ? null : children.get(i);
  }

  public <T extends ParseTree> T addChild(T child) {
    if (children == null) children = new ArrayList<>();
    children.add(child);
    return child;
  }

  public TerminalNode addTokenNode(Token t) {
    TerminalNode n = new TerminalNode(t);
    n.parent = this;
    return addChild(n);
  }

  public ErrorNode addErrorNode(Token t) {
    ErrorNode n = new ErrorNode(t);
    n.parent = this;
    return addChild(n);
  }

  /** Removes the last child (used when a rule invocation is replaced, as in ANTLR's enterOuterAlt). */
  public void removeLastChild() {
    if (children != null && !children.isEmpty()) children.remove(children.size() - 1);
  }

  @Override public String getText() {
    if (children == null || children.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    for (ParseTree c : children) sb.append(c.getText());
    return sb.toString();
  }

  /** The i-th child terminal (incl. error nodes) with this token type, or null. */
  public TerminalNode getToken(int ttype, int i) {
    if (children == null || i < 0) return null;
    int j = -1;
    for (ParseTree o : children) {
      if (o instanceof TerminalNode t && t.symbol.getType() == ttype && ++j == i) return t;
    }
    return null;
  }

  public List<TerminalNode> getTokens(int ttype) {
    List<TerminalNode> out = new ArrayList<>();
    if (children != null) {
      for (ParseTree o : children) {
        if (o instanceof TerminalNode t && t.symbol.getType() == ttype) out.add(t);
      }
    }
    return out;
  }

  public <T extends ParserRuleContext> T getRuleContext(Class<? extends T> type, int i) {
    if (children == null || i < 0) return null;
    int j = -1;
    for (ParseTree o : children) {
      if (type.isInstance(o) && ++j == i) return type.cast(o);
    }
    return null;
  }

  public <T extends ParserRuleContext> List<T> getRuleContexts(Class<? extends T> type) {
    List<T> out = new ArrayList<>();
    if (children != null) {
      for (ParseTree o : children) {
        if (type.isInstance(o)) out.add(type.cast(o));
      }
    }
    return out;
  }

  @Override public String toString() { return getClass().getSimpleName() + ":" + getText(); }
}
