package org.hl7.fhir.sushi.importer.parser;

/**
 * A token consumed during error recovery, or a token conjured by single-token insertion ("missing X"). Like ANTLR it
 * is a TerminalNode, so the generated accessors (e.g. {@code EQUAL()}) also return error nodes of that type.
 */
public class ErrorNode extends TerminalNode {
  public ErrorNode(Token symbol) {
    super(symbol);
  }
}
