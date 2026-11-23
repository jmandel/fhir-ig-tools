package org.hl7.fhir.sushi.importer.parser;

import static org.hl7.fhir.sushi.importer.parser.LexPattern.*;
import static org.hl7.fhir.sushi.importer.parser.MiniFSHParser.*;

// port of sushi/antlr/src/main/antlr/MiniFSHLexer.g4 (SUSHI 3.21.1)
/** The MiniFSH lexer (one mode); same fragments and rule definitions as FSHLexer where the grammars share them. */
final class MiniFSHLexer extends LexerBase {
  private static final Rule[] RULES = {
    // STAR: ([\r\n] | LINE_COMMENT) WS* '*' [ \u00A0];
    Rule.of(STAR, seq(alt(set(chars("\r\n")), FSHLexer.LINE_COMMENT_RX), star(FSHLexer.WS), lit("*"),
        set(chars(" \u00A0")))),
    Rule.of(STRING, FSHLexer.STRING_RX),
    new Rule(MULTILINE_STRING, FSHLexer::multilineString, LexerBase.Action.NONE, -1, 0),
    Rule.of(SEQUENCE, FSHLexer.SEQUENCE_RX),
    Rule.of(WHITESPACE, FSHLexer.WS).hidden(),
    Rule.of(LINE_COMMENT, FSHLexer.LINE_COMMENT_RX).skip(),
  };

  MiniFSHLexer(InputText input, ErrorListener listener) {
    super(input, listener);
  }

  @Override Rule[] rules(int mode) {
    return RULES;
  }
}
