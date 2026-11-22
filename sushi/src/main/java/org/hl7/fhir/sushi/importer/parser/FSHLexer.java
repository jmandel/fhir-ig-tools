package org.hl7.fhir.sushi.importer.parser;

import static org.hl7.fhir.sushi.importer.parser.FSHParser.*;
import static org.hl7.fhir.sushi.importer.parser.LexPattern.*;

import java.util.ArrayList;
import java.util.List;

// port of sushi/antlr/src/main/antlr/FSHLexer.g4 (SUSHI 3.21.1)
/**
 * The FSH lexer: FSHLexer.g4's rules in grammar order, in its four modes. Each rule below transcribes the grammar's
 * rule (fragment rules inlined), so the two can be compared line by line.
 */
final class FSHLexer extends LexerBase {
  static final int DEFAULT_MODE = 0, RULESET_OR_INSERT = 1, PARAM_RULESET_OR_INSERT = 2, LIST_OF_CONTEXTS = 3,
      LIST_OF_CODES = 4;

  // FRAGMENTS
  /** fragment WS: [ \t\r\n\f\u00A0]; */
  static final String WS_CHARS = " \t\r\n\f\u00A0";
  static final Rx WS = set(chars(WS_CHARS));
  /** fragment NONWS: ~[ \t\r\n\f\u00A0]; */
  static final Rx NONWS = set(notChars(WS_CHARS));
  /** fragment NONWS_STR: ~[ \t\r\n\f\u00A0\\"]; */
  static final Rx NONWS_STR = set(notChars(WS_CHARS + "\\\""));
  /** fragment RSNONWS: ~[ \t\r\n\f\u00A0(]; */
  static final Rx RSNONWS = set(notChars(WS_CHARS + "("));
  static final Rx DIGIT = set(range('0', '9'));

  /** keyword with a colon: 'Alias' WS* ':' */
  private static Rx kw(String word) {
    return seq(lit(word), star(WS), lit(":"));
  }

  /** '(' WS* 'example' WS* ')' */
  private static Rx paren(String word) {
    return seq(lit("("), star(WS), lit(word), star(WS), lit(")"));
  }

  // PATTERNS (token rules also used inside other rules)
  /** LINE_COMMENT: '//' ~[\r\n]* [\r\n]; */
  static final Rx LINE_COMMENT_RX = seq(lit("//"), star(set(notChars("\r\n"))), set(chars("\r\n")));
  /** STRING: '"' (~[\\"] | '\\u' | '\\r' | '\\n' | '\\t' | '\\"' | '\\\\')* '"'; */
  static final Rx STRING_RX = seq(lit("\""),
      star(alt(set(notChars("\\\"")), lit("\\u"), lit("\\r"), lit("\\n"), lit("\\t"), lit("\\\""), lit("\\\\"))),
      lit("\""));
  /** SEQUENCE: NONWS+; */
  static final Rx SEQUENCE_RX = plus(NONWS);
  /** CONCEPT_STRING: '"' (NONWS_STR | '\\"' | '\\\\')+ (WS (NONWS_STR | '\\"' | '\\\\')+)* '"'; */
  static final Rx CONCEPT_STRING_RX = seq(lit("\""), plus(alt(NONWS_STR, lit("\\\""), lit("\\\\"))),
      star(seq(WS, plus(alt(NONWS_STR, lit("\\\""), lit("\\\\"))))), lit("\""));
  /** CODE: SEQUENCE? '#' (SEQUENCE | CONCEPT_STRING); */
  static final Rx CODE_RX = seq(opt(SEQUENCE_RX), lit("#"), alt(SEQUENCE_RX, CONCEPT_STRING_RX));
  /**
   * TIME: [0-9][0-9](':'[0-9][0-9](':'[0-9][0-9]('.'[0-9]+)?)?)?('Z' | ('+' | '-')[0-9][0-9]':'[0-9][0-9])?;
   */
  static final Rx TIME_RX = seq(DIGIT, DIGIT,
      opt(seq(lit(":"), DIGIT, DIGIT, opt(seq(lit(":"), DIGIT, DIGIT, opt(seq(lit("."), plus(DIGIT))))))),
      opt(alt(lit("Z"), seq(set(chars("+-")), DIGIT, DIGIT, lit(":"), DIGIT, DIGIT))));
  /** (WS 'or' WS+ SEQUENCE WS*)* ... for Reference/CodeableReference */
  private static Rx refList(String word) {
    return seq(lit(word), star(WS), lit("("), star(WS), SEQUENCE_RX, star(WS),
        star(seq(WS, lit("or"), plus(WS), SEQUENCE_RX, star(WS))), lit(")"));
  }

  /** MULTILINE_STRING: '"""' .*? ('""' | '"')? '"""'; (non-greedy: see class comment) */
  static LexPattern.Match multilineString(String s, int pos) {
    if (!s.startsWith("\"\"\"", pos)) return LexPattern.NONE;
    // The non-greedy loop stops at the first '"""' after the opening one; the optional '""' | '"' lets the
    // token take up to two more quotes of that run (ANTLR drops the configurations that passed through the loop
    // once the plain '"""' alternative accepts, keeping only those that entered ('""' | '"')? at the same place).
    int j = s.indexOf("\"\"\"", pos + 3);
    if (j < 0) return new LexPattern.Match(-1, s.length() - pos);
    int q = 0;
    while (j + q < s.length() && s.charAt(j + q) == '"' && q < 5) q++;
    int len = j - pos + q;
    return new LexPattern.Match(len, len);
  }

  /** BLOCK_COMMENT: '/*' .*? '*&#47;' -> skip; (non-greedy: ends at the first '*&#47;') */
  static LexPattern.Match blockComment(String s, int pos) {
    if (!s.startsWith("/*", pos)) return LexPattern.NONE;
    int j = s.indexOf("*/", pos + 2);
    if (j < 0) return new LexPattern.Match(-1, s.length() - pos);
    return new LexPattern.Match(j + 2 - pos, j + 2 - pos);
  }

  private static final Rule[][] MODES = new Rule[5][];

  static {
    List<Rule> d = new ArrayList<>();
    // KEYWORDS
    d.add(Rule.of(KW_ALIAS, kw("Alias")));
    d.add(Rule.of(KW_PROFILE, kw("Profile")));
    d.add(Rule.of(KW_EXTENSION, kw("Extension")));
    d.add(Rule.of(KW_INSTANCE, kw("Instance")));
    d.add(Rule.of(KW_INSTANCEOF, kw("InstanceOf")));
    d.add(Rule.of(KW_INVARIANT, kw("Invariant")));
    d.add(Rule.of(KW_VALUESET, kw("ValueSet")));
    d.add(Rule.of(KW_CODESYSTEM, kw("CodeSystem")));
    d.add(Rule.of(KW_RULESET, kw("RuleSet")).push(RULESET_OR_INSERT));
    d.add(Rule.of(KW_MAPPING, kw("Mapping")));
    d.add(Rule.of(KW_LOGICAL, kw("Logical")));
    d.add(Rule.of(KW_RESOURCE, kw("Resource")));
    d.add(Rule.of(KW_PARENT, kw("Parent")));
    d.add(Rule.of(KW_ID, kw("Id")));
    d.add(Rule.of(KW_TITLE, kw("Title")));
    d.add(Rule.of(KW_DESCRIPTION, kw("Description")));
    d.add(Rule.of(KW_EXPRESSION, kw("Expression")));
    d.add(Rule.of(KW_XPATH, kw("XPath")));
    d.add(Rule.of(KW_SEVERITY, kw("Severity")));
    d.add(Rule.of(KW_USAGE, kw("Usage")));
    d.add(Rule.of(KW_SOURCE, kw("Source")));
    d.add(Rule.of(KW_TARGET, kw("Target")));
    d.add(Rule.of(KW_CONTEXT, kw("Context")).push(LIST_OF_CONTEXTS));
    d.add(Rule.of(KW_CHARACTERISTICS, kw("Characteristics")).push(LIST_OF_CODES));
    d.add(Rule.of(KW_MOD, lit("?!")));
    d.add(Rule.of(KW_MS, lit("MS")));
    d.add(Rule.of(KW_SU, lit("SU")));
    d.add(Rule.of(KW_TU, lit("TU")));
    d.add(Rule.of(KW_NORMATIVE, lit("N")));
    d.add(Rule.of(KW_DRAFT, lit("D")));
    d.add(Rule.of(KW_FROM, lit("from")));
    d.add(Rule.of(KW_EXAMPLE, paren("example")));
    d.add(Rule.of(KW_PREFERRED, paren("preferred")));
    d.add(Rule.of(KW_EXTENSIBLE, paren("extensible")));
    d.add(Rule.of(KW_REQUIRED, paren("required")));
    d.add(Rule.of(KW_CONTAINS, lit("contains")));
    d.add(Rule.of(KW_NAMED, lit("named")));
    d.add(Rule.of(KW_AND, lit("and")));
    d.add(Rule.of(KW_ONLY, lit("only")));
    d.add(Rule.of(KW_OR, lit("or")));
    d.add(Rule.of(KW_OBEYS, lit("obeys")));
    d.add(Rule.of(KW_TRUE, lit("true")));
    d.add(Rule.of(KW_FALSE, lit("false")));
    d.add(Rule.of(KW_INCLUDE, lit("include")));
    d.add(Rule.of(KW_EXCLUDE, lit("exclude")));
    d.add(Rule.of(KW_CODES, lit("codes")));
    d.add(Rule.of(KW_WHERE, lit("where")));
    d.add(Rule.of(KW_VSREFERENCE, lit("valueset")));
    d.add(Rule.of(KW_SYSTEM, lit("system")));
    d.add(Rule.of(KW_EXACTLY, paren("exactly")));
    d.add(Rule.of(KW_INSERT, lit("insert")).push(RULESET_OR_INSERT));
    d.add(Rule.of(KW_CONTENTREFERENCE, lit("contentReference")));
    // SYMBOLS
    d.add(Rule.of(EQUAL, lit("=")));
    // STAR: ([\r\n] | LINE_COMMENT) WS* '*' [ \u00A0];
    d.add(Rule.of(STAR, seq(alt(set(chars("\r\n")), LINE_COMMENT_RX), star(WS), lit("*"), set(chars(" \u00A0")))));
    d.add(Rule.of(COLON, lit(":")));
    d.add(Rule.of(COMMA, lit(",")));
    d.add(Rule.of(ARROW, lit("->")));
    // PATTERNS
    d.add(Rule.of(STRING, STRING_RX));
    d.add(new Rule(MULTILINE_STRING, FSHLexer::multilineString, LexerBase.Action.NONE, -1, 0));
    // NUMBER: [+\-]? [0-9]+('.' [0-9]+)? ([eE] [+\-]? [0-9]+)?;
    d.add(Rule.of(NUMBER, seq(opt(set(chars("+-"))), plus(DIGIT), opt(seq(lit("."), plus(DIGIT))),
        opt(seq(set(chars("eE")), opt(set(chars("+-"))), plus(DIGIT))))));
    // UNIT: '\'' (~[\\'])* '\'';
    d.add(Rule.of(UNIT, seq(lit("'"), star(set(notChars("\\'"))), lit("'"))));
    d.add(Rule.of(CODE, CODE_RX));
    d.add(Rule.of(CONCEPT_STRING, CONCEPT_STRING_RX));
    // DATETIME: [0-9][0-9][0-9][0-9]('-'[0-9][0-9]('-'[0-9][0-9]('T' TIME)?)?)?;
    d.add(Rule.of(DATETIME, seq(DIGIT, DIGIT, DIGIT, DIGIT,
        opt(seq(lit("-"), DIGIT, DIGIT, opt(seq(lit("-"), DIGIT, DIGIT, opt(seq(lit("T"), TIME_RX)))))))));
    d.add(Rule.of(TIME, TIME_RX));
    // CARD: ([0-9]+)? '..' ([0-9]+ | '*')?;
    d.add(Rule.of(CARD, seq(opt(plus(DIGIT)), lit(".."), opt(alt(plus(DIGIT), lit("*"))))));
    // REFERENCE: 'Reference' WS* '(' WS* SEQUENCE WS* (WS 'or' WS+ SEQUENCE WS*)* ')';
    d.add(Rule.of(REFERENCE, refList("Reference")));
    // CODEABLE_REFERENCE: 'CodeableReference' WS* '(' WS* SEQUENCE WS* (WS 'or' WS+ SEQUENCE WS*)* ')';
    d.add(Rule.of(CODEABLE_REFERENCE, refList("CodeableReference")));
    // CANONICAL: 'Canonical' WS* '(' WS* SEQUENCE ('|' SEQUENCE)? WS* (WS 'or' WS+ SEQUENCE ('|' SEQUENCE)? WS*)* ')';
    d.add(Rule.of(CANONICAL, seq(lit("Canonical"), star(WS), lit("("), star(WS), SEQUENCE_RX,
        opt(seq(lit("|"), SEQUENCE_RX)), star(WS),
        star(seq(WS, lit("or"), plus(WS), SEQUENCE_RX, opt(seq(lit("|"), SEQUENCE_RX)), star(WS))), lit(")"))));
    // CARET_SEQUENCE: '^' NONWS+;
    d.add(Rule.of(CARET_SEQUENCE, seq(lit("^"), plus(NONWS))));
    // REGEX: '/' ('\\/' | ~[*/\r\n])('\\/' | ~[/\r\n])* '/';
    d.add(Rule.of(REGEX, seq(lit("/"), alt(lit("\\/"), set(notChars("*/\r\n"))),
        star(alt(lit("\\/"), set(notChars("/\r\n")))), lit("/"))));
    d.add(new Rule(BLOCK_COMMENT, FSHLexer::blockComment, LexerBase.Action.SKIP, -1, 0));
    d.add(Rule.of(SEQUENCE, SEQUENCE_RX));
    // IGNORED TOKENS
    d.add(Rule.of(WHITESPACE, WS).hidden());
    d.add(Rule.of(LINE_COMMENT, LINE_COMMENT_RX).skip());
    MODES[DEFAULT_MODE] = d.toArray(Rule[]::new);

    // mode RULESET_OR_INSERT;
    MODES[RULESET_OR_INSERT] = new Rule[] {
      // PARAM_RULESET_REFERENCE: WS* RSNONWS+ WS* '(' -> pushMode(PARAM_RULESET_OR_INSERT);
      Rule.of(PARAM_RULESET_REFERENCE, seq(star(WS), plus(RSNONWS), star(WS), lit("("))).push(PARAM_RULESET_OR_INSERT),
      // RULESET_REFERENCE: WS* RSNONWS+ -> popMode;
      Rule.of(RULESET_REFERENCE, seq(star(WS), plus(RSNONWS))).pop(1),
    };

    // mode PARAM_RULESET_OR_INSERT;
    // ( ~[\]] | (']'~[\]]) | (']]' WS* ~[,) \t\r\n\f\u00A0]) )+
    Rx bracketed = seq(star(WS), lit("[["), plus(alt(set(notChars("]")), seq(lit("]"), set(notChars("]"))),
        seq(lit("]]"), star(WS), set(notChars(",)" + WS_CHARS))))), lit("]]"), star(WS));
    // WS* ('\\)' | '\\,' | '\\\\' | ~[),])* WS*
    Rx plain = seq(star(WS), star(alt(lit("\\)"), lit("\\,"), lit("\\\\"), set(notChars("),")))), star(WS));
    MODES[PARAM_RULESET_OR_INSERT] = new Rule[] {
      Rule.of(BRACKETED_PARAM, seq(bracketed, lit(","))),
      Rule.of(LAST_BRACKETED_PARAM, seq(bracketed, lit(")"))).pop(2),
      Rule.of(PLAIN_PARAM, seq(plain, lit(","))),
      Rule.of(LAST_PLAIN_PARAM, seq(plain, lit(")"))).pop(2),
    };

    // mode LIST_OF_CONTEXTS;
    MODES[LIST_OF_CONTEXTS] = new Rule[] {
      // QUOTED_CONTEXT: STRING WS* ',';
      Rule.of(QUOTED_CONTEXT, seq(STRING_RX, star(WS), lit(","))),
      // LAST_QUOTED_CONTEXT: STRING -> popMode;
      Rule.of(LAST_QUOTED_CONTEXT, STRING_RX).pop(1),
      // UNQUOTED_CONTEXT: (SEQUENCE | CODE) WS* ',';
      Rule.of(UNQUOTED_CONTEXT, seq(alt(SEQUENCE_RX, CODE_RX), star(WS), lit(","))),
      // LAST_UNQUOTED_CONTEXT: (SEQUENCE | CODE) -> popMode;
      Rule.of(LAST_UNQUOTED_CONTEXT, alt(SEQUENCE_RX, CODE_RX)).pop(1),
      // CONTEXT_WHITESPACE: WS -> channel(HIDDEN);
      Rule.of(CONTEXT_WHITESPACE, WS).hidden(),
    };

    // mode LIST_OF_CODES;
    MODES[LIST_OF_CODES] = new Rule[] {
      // CODE_ITEM: CODE WS* ',';
      Rule.of(CODE_ITEM, seq(CODE_RX, star(WS), lit(","))),
      // LAST_CODE_ITEM: CODE -> popMode;
      Rule.of(LAST_CODE_ITEM, CODE_RX).pop(1),
      // CODE_LIST_WHITESPACE: WS -> channel(HIDDEN);
      Rule.of(CODE_LIST_WHITESPACE, WS).hidden(),
    };
  }

  FSHLexer(InputText input, ErrorListener listener) {
    super(input, listener);
  }

  @Override Rule[] rules(int mode) {
    return MODES[mode];
  }
}
