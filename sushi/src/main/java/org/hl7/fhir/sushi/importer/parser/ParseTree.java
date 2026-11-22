package org.hl7.fhir.sushi.importer.parser;

/** A node of the concrete syntax tree: a rule context or a terminal (token) node. Mirrors ANTLR's ParseTree. */
public interface ParseTree {
  ParseTree getParent();
  /** The concatenated text of all tokens below this node (hidden-channel tokens are not in the tree). */
  String getText();
  int getChildCount();
  ParseTree getChild(int i);
}
