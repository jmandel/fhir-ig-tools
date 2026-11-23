/**
 * The FSH and MiniFSH parsers (port of SUSHI 3.21.1's ANTLR grammars FSHLexer.g4/FSH.g4/MiniFSH*.g4), producing a
 * concrete syntax tree shaped like ANTLR's generated {@code FSHParser.*Context} classes.
 *
 * <p>Design (D6). SUSHI's observable behaviour on bad input -- the error messages ("mismatched input 'x' expecting
 * {...}", "no viable alternative at input ..."), their order and positions, and what is recovered after an error --
 * is defined by ANTLR's runtime algorithms on the grammar's ATN. So the parser is written in three layers:
 * <ul>
 * <li>{@link org.hl7.fhir.sushi.importer.parser.FSHLexer}/{@code MiniFSHLexer}: hand-written longest-match lexers,
 *     each FSHLexer.g4 rule transcribed as a small regex ({@code LexPattern}), plus the two non-greedy rules coded
 *     directly; ANTLR's lexer error recovery in {@code LexerBase}.</li>
 * <li>{@code FshGrammar}: FSH.g4/MiniFSH.g4 rule by rule in a small Java DSL ({@code Grammar}); {@code Atn} builds
 *     the ATN exactly as the ANTLR tool does (state numbering included, checked by AtnEquivalenceTest).</li>
 * <li>{@code ParserEngine}: recursive descent over the grammar following ANTLR's generated-code templates, with the
 *     JS runtime's DefaultErrorStrategy; {@code Prediction}: the JS runtime's ALL(*) for the 15 non-LL(1) decisions.
 *     JS-runtime quirks reproduced: {@code JsIntervalSet} (complement), "&lt;missing null&gt;" token text, lazy
 *     token fetching (lexer errors interleave with parser errors), stop = LT(-1).</li>
 * </ul>
 * To change the grammar: edit FSHLexer/FshGrammar like the .g4, regenerate the CST classes
 * ({@code scripts/wp1/gen-cst.py} from the ANTLR test-scope output), run AtnEquivalenceTest and the differential
 * gate ({@code scripts/wp1/gate.sh}; ground truth from SUSHI's JS parser via {@code scripts/wp1/js-oracle.sh},
 * fuzz cases via {@code scripts/wp1/fuzz.sh}).
 */
package org.hl7.fhir.sushi.importer.parser;
