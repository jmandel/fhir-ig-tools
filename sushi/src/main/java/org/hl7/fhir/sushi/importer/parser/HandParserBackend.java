package org.hl7.fhir.sushi.importer.parser;

/** The hand-written parser (production backend). */
final class HandParserBackend implements ParserBackend {
  static final ParserEngine.Compiled FSH = new ParserEngine.Compiled(FshGrammar.FSH);
  static final ParserEngine.Compiled MINI = new ParserEngine.Compiled(FshGrammar.MINI);

  @Override public FSHParser.DocContext parseDoc(String input, ErrorListener listener) {
    return parseDocWithTokens(input, listener).tree();
  }

  @Override public MiniFSHParser.RuleSetContext parseMiniFsh(String input, ErrorListener listener) {
    return parseMiniWithTokens(input, listener).tree();
  }

  record Result<T extends ParserRuleContext>(T tree, java.util.List<Token> tokens) {}

  static Result<FSHParser.DocContext> parseDocWithTokens(String input, ErrorListener listener) {
    InputText text = new InputText(input);
    CommonTokenStream tokens = new CommonTokenStream(new FSHLexer(text, listener));
    ParserEngine p = new ParserEngine(FSH, tokens, listener, FSHParser::newContext);
    FSHParser.DocContext doc = (FSHParser.DocContext) p.parse(FSHParser.RULE_doc);
    return new Result<>(doc, tokens.getTokens());
  }

  static Result<MiniFSHParser.RuleSetContext> parseMiniWithTokens(String input, ErrorListener listener) {
    InputText text = new InputText(input);
    CommonTokenStream tokens = new CommonTokenStream(new MiniFSHLexer(text, listener));
    ParserEngine p = new ParserEngine(MINI, tokens, listener, MiniFSHParser::newContext);
    MiniFSHParser.RuleSetContext rs = (MiniFSHParser.RuleSetContext) p.parse(MiniFSHParser.RULE_ruleSet);
    return new Result<>(rs, tokens.getTokens());
  }
}
