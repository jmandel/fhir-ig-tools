package org.hl7.fhir.sushi.importer.parser;

/** A token in the tree. Mirrors ANTLR's TerminalNode(Impl). */
public class TerminalNode implements ParseTree {
  public final Token symbol;
  public ParserRuleContext parent;

  public TerminalNode(Token symbol) {
    this.symbol = symbol;
  }

  public Token getSymbol() { return symbol; }
  @Override public ParserRuleContext getParent() { return parent; }
  @Override public String getText() { return symbol.getText(); }
  @Override public int getChildCount() { return 0; }
  @Override public ParseTree getChild(int i) { return null; }
  @Override public String toString() { return symbol.getType() == Token.EOF ? "<EOF>" : symbol.getText(); }
}
