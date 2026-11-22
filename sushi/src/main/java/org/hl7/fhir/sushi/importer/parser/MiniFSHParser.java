package org.hl7.fhir.sushi.importer.parser;

import java.util.List;

// port of sushi/antlr/src/main/antlr/MiniFSH.g4 (SUSHI 3.21.1): CST node classes + parse entry point
/**
 * MiniFSH parser (used by MiniFSHImporter for RuleSet parameter substitution): token types, rule indices and CST node
 * classes shaped like the ANTLR-generated {@code MiniFSHParser.*Context}.
 */
public final class MiniFSHParser {
  private MiniFSHParser() {}

  /**
   * TS MiniFSHImporter.transformRuleSet's parse: SUSHI installs no error listener there, so ANTLR's default
   * ConsoleErrorListener prints {@code line L:C msg} to stderr ({@link ErrorListener#CONSOLE}).
   */
  public static RuleSetContext parseRuleSet(String input) {
    return parseRuleSet(input, ErrorListener.CONSOLE);
  }

  public static RuleSetContext parseRuleSet(String input, ErrorListener listener) {
    return Parsers.backend().parseMiniFsh(input, listener);
  }

  // ---- BEGIN GENERATED CST (scripts/wp1/gen-cst.py from the ANTLR 4.13.2 Java target; do not edit by hand) ----
  public static final int EOF = Token.EOF;
  public static final int
    STAR=1, STRING=2, MULTILINE_STRING=3, SEQUENCE=4, WHITESPACE=5, LINE_COMMENT=6;
  public static final int
    RULE_ruleSet = 0, RULE_someRule = 1, RULE_rulePart = 2;
  public static final String[] ruleNames = {
    "ruleSet", "someRule", "rulePart"
  };
  /** ANTLR literal names by token type (JS runtime: {@code recognizer.literalNames}). */
  public static final String[] literalNames = {

  };
  /** ANTLR symbolic names by token type (JS runtime: {@code recognizer.symbolicNames}). */
  public static final String[] symbolicNames = {
    null, "STAR", "STRING", "MULTILINE_STRING", "SEQUENCE", "WHITESPACE",
    "LINE_COMMENT"
  };

  public static class RuleSetContext extends ParserRuleContext {
    public TerminalNode EOF() { return getToken(MiniFSHParser.EOF, 0); }
    public List<SomeRuleContext> someRule() {
      return getRuleContexts(SomeRuleContext.class);
    }
    public SomeRuleContext someRule(int i) {
      return getRuleContext(SomeRuleContext.class,i);
    }
    public RuleSetContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_ruleSet; }
  }

  public static class SomeRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(MiniFSHParser.STAR, 0); }
    public List<RulePartContext> rulePart() {
      return getRuleContexts(RulePartContext.class);
    }
    public RulePartContext rulePart(int i) {
      return getRuleContext(RulePartContext.class,i);
    }
    public SomeRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_someRule; }
  }

  public static class RulePartContext extends ParserRuleContext {
    public TerminalNode SEQUENCE() { return getToken(MiniFSHParser.SEQUENCE, 0); }
    public TerminalNode STRING() { return getToken(MiniFSHParser.STRING, 0); }
    public TerminalNode MULTILINE_STRING() { return getToken(MiniFSHParser.MULTILINE_STRING, 0); }
    public RulePartContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_rulePart; }
  }

  /** Creates the empty context object for a rule index (used by the parser and the test adapter). */
  public static ParserRuleContext newContext(int ruleIndex, ParserRuleContext parent, int invokingState) {
    return switch (ruleIndex) {
      case RULE_ruleSet -> new RuleSetContext(parent, invokingState);
      case RULE_someRule -> new SomeRuleContext(parent, invokingState);
      case RULE_rulePart -> new RulePartContext(parent, invokingState);
      default -> throw new IllegalArgumentException("rule " + ruleIndex);
    };
  }
  // ---- END GENERATED CST ----
}
