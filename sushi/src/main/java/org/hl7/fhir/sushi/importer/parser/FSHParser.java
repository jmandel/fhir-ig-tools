package org.hl7.fhir.sushi.importer.parser;

import java.util.List;

// port of sushi/antlr/src/main/antlr/FSH.g4 (SUSHI 3.21.1): CST node classes + parse entry point
/**
 * FSH parser: token types, rule indices and the concrete-syntax-tree node classes, named and shaped exactly like the
 * ANTLR-generated {@code FSHParser.*Context} classes (same rule names, same accessors: {@code ctx.name()},
 * {@code ctx.sdRule()} list / {@code ctx.sdRule(i)}, {@code ctx.STAR()}, ...), so the importer reads like SUSHI's
 * FSHImporter.ts. Parse with {@link #parseDoc}.
 */
public final class FSHParser {
  private FSHParser() {}

  /**
   * TS FSHImporter.parseDoc: lexes and parses a whole FSH document; syntax errors (lexer and parser) go to the
   * listener with ANTLR's message text, in ANTLR's order; the returned tree contains the recovered parse.
   */
  public static DocContext parseDoc(String input, ErrorListener listener) {
    return Parsers.backend().parseDoc(input, listener);
  }

  /** Display name of a token type as in ANTLR messages: literal name, else symbolic name. */
  public static String displayName(int type) {
    if (type == Token.EOF) return "<EOF>";
    if (type == Token.EPSILON) return "<EPSILON>";
    if (type > 0 && type < literalNames.length && literalNames[type] != null) return literalNames[type];
    if (type > 0 && type < symbolicNames.length && symbolicNames[type] != null) return symbolicNames[type];
    return String.valueOf(type);
  }

  static List<String> ruleNameList() { return List.of(ruleNames); }

  // ---- BEGIN GENERATED CST (scripts/wp1/gen-cst.py from the ANTLR 4.13.2 Java target; do not edit by hand) ----
  public static final int EOF = Token.EOF;
  public static final int
    KW_ALIAS=1, KW_PROFILE=2, KW_EXTENSION=3, KW_INSTANCE=4, KW_INSTANCEOF=5,
    KW_INVARIANT=6, KW_VALUESET=7, KW_CODESYSTEM=8, KW_RULESET=9, KW_MAPPING=10,
    KW_LOGICAL=11, KW_RESOURCE=12, KW_PARENT=13, KW_ID=14, KW_TITLE=15, KW_DESCRIPTION=16,
    KW_EXPRESSION=17, KW_XPATH=18, KW_SEVERITY=19, KW_USAGE=20, KW_SOURCE=21,
    KW_TARGET=22, KW_CONTEXT=23, KW_CHARACTERISTICS=24, KW_MOD=25, KW_MS=26,
    KW_SU=27, KW_TU=28, KW_NORMATIVE=29, KW_DRAFT=30, KW_FROM=31, KW_EXAMPLE=32,
    KW_PREFERRED=33, KW_EXTENSIBLE=34, KW_REQUIRED=35, KW_CONTAINS=36, KW_NAMED=37,
    KW_AND=38, KW_ONLY=39, KW_OR=40, KW_OBEYS=41, KW_TRUE=42, KW_FALSE=43,
    KW_INCLUDE=44, KW_EXCLUDE=45, KW_CODES=46, KW_WHERE=47, KW_VSREFERENCE=48,
    KW_SYSTEM=49, KW_EXACTLY=50, KW_INSERT=51, KW_CONTENTREFERENCE=52, EQUAL=53,
    STAR=54, COLON=55, COMMA=56, ARROW=57, STRING=58, MULTILINE_STRING=59,
    NUMBER=60, UNIT=61, CODE=62, CONCEPT_STRING=63, DATETIME=64, TIME=65,
    CARD=66, REFERENCE=67, CODEABLE_REFERENCE=68, CANONICAL=69, CARET_SEQUENCE=70,
    REGEX=71, BLOCK_COMMENT=72, SEQUENCE=73, WHITESPACE=74, LINE_COMMENT=75,
    PARAM_RULESET_REFERENCE=76, RULESET_REFERENCE=77, BRACKETED_PARAM=78,
    LAST_BRACKETED_PARAM=79, PLAIN_PARAM=80, LAST_PLAIN_PARAM=81, QUOTED_CONTEXT=82,
    LAST_QUOTED_CONTEXT=83, UNQUOTED_CONTEXT=84, LAST_UNQUOTED_CONTEXT=85,
    CONTEXT_WHITESPACE=86, CODE_ITEM=87, LAST_CODE_ITEM=88, CODE_LIST_WHITESPACE=89;
  public static final int
    RULE_doc = 0, RULE_entity = 1, RULE_alias = 2, RULE_profile = 3, RULE_extension = 4,
    RULE_logical = 5, RULE_resource = 6, RULE_sdMetadata = 7, RULE_sdRule = 8,
    RULE_lrRule = 9, RULE_instance = 10, RULE_instanceMetadata = 11, RULE_instanceRule = 12,
    RULE_invariant = 13, RULE_invariantMetadata = 14, RULE_invariantRule = 15,
    RULE_valueSet = 16, RULE_vsMetadata = 17, RULE_vsRule = 18, RULE_codeSystem = 19,
    RULE_csMetadata = 20, RULE_csRule = 21, RULE_ruleSet = 22, RULE_ruleSetRule = 23,
    RULE_paramRuleSet = 24, RULE_paramRuleSetRef = 25, RULE_parameter = 26,
    RULE_lastParameter = 27, RULE_paramRuleSetContent = 28, RULE_mapping = 29,
    RULE_mappingMetadata = 30, RULE_mappingEntityRule = 31, RULE_parent = 32,
    RULE_id = 33, RULE_title = 34, RULE_description = 35, RULE_expression = 36,
    RULE_xpath = 37, RULE_severity = 38, RULE_instanceOf = 39, RULE_usage = 40,
    RULE_source = 41, RULE_target = 42, RULE_context = 43, RULE_contextItem = 44,
    RULE_lastContextItem = 45, RULE_characteristics = 46, RULE_cardRule = 47,
    RULE_flagRule = 48, RULE_valueSetRule = 49, RULE_fixedValueRule = 50,
    RULE_containsRule = 51, RULE_onlyRule = 52, RULE_obeysRule = 53, RULE_caretValueRule = 54,
    RULE_codeCaretValueRule = 55, RULE_mappingRule = 56, RULE_insertRule = 57,
    RULE_codeInsertRule = 58, RULE_addCRElementRule = 59, RULE_addElementRule = 60,
    RULE_pathRule = 61, RULE_vsComponent = 62, RULE_vsConceptComponent = 63,
    RULE_vsFilterComponent = 64, RULE_vsComponentFrom = 65, RULE_vsFromSystem = 66,
    RULE_vsFromValueset = 67, RULE_vsFilterList = 68, RULE_vsFilterDefinition = 69,
    RULE_vsFilterOperator = 70, RULE_vsFilterValue = 71, RULE_name = 72, RULE_path = 73,
    RULE_caretPath = 74, RULE_flag = 75, RULE_strength = 76, RULE_value = 77,
    RULE_item = 78, RULE_code = 79, RULE_concept = 80, RULE_quantity = 81,
    RULE_ratio = 82, RULE_reference = 83, RULE_referenceType = 84, RULE_codeableReferenceType = 85,
    RULE_canonical = 86, RULE_ratioPart = 87, RULE_bool = 88, RULE_targetType = 89,
    RULE_mostAlphaKeywords = 90;
  public static final String[] ruleNames = {
    "doc", "entity", "alias", "profile", "extension", "logical", "resource",
    "sdMetadata", "sdRule", "lrRule", "instance", "instanceMetadata", "instanceRule",
    "invariant", "invariantMetadata", "invariantRule", "valueSet", "vsMetadata",
    "vsRule", "codeSystem", "csMetadata", "csRule", "ruleSet", "ruleSetRule",
    "paramRuleSet", "paramRuleSetRef", "parameter", "lastParameter", "paramRuleSetContent",
    "mapping", "mappingMetadata", "mappingEntityRule", "parent", "id", "title",
    "description", "expression", "xpath", "severity", "instanceOf", "usage",
    "source", "target", "context", "contextItem", "lastContextItem", "characteristics",
    "cardRule", "flagRule", "valueSetRule", "fixedValueRule", "containsRule",
    "onlyRule", "obeysRule", "caretValueRule", "codeCaretValueRule", "mappingRule",
    "insertRule", "codeInsertRule", "addCRElementRule", "addElementRule",
    "pathRule", "vsComponent", "vsConceptComponent", "vsFilterComponent",
    "vsComponentFrom", "vsFromSystem", "vsFromValueset", "vsFilterList",
    "vsFilterDefinition", "vsFilterOperator", "vsFilterValue", "name", "path",
    "caretPath", "flag", "strength", "value", "item", "code", "concept",
    "quantity", "ratio", "reference", "referenceType", "codeableReferenceType",
    "canonical", "ratioPart", "bool", "targetType", "mostAlphaKeywords"
  };
  /** ANTLR literal names by token type (JS runtime: {@code recognizer.literalNames}). */
  public static final String[] literalNames = {
    null, null, null, null, null, null, null, null, null, null, null, null,
    null, null, null, null, null, null, null, null, null, null, null, null,
    null, "'?!'", "'MS'", "'SU'", "'TU'", "'N'", "'D'", "'from'", null, null,
    null, null, "'contains'", "'named'", "'and'", "'only'", "'or'", "'obeys'",
    "'true'", "'false'", "'include'", "'exclude'", "'codes'", "'where'",
    "'valueset'", "'system'", null, "'insert'", "'contentReference'", "'='",
    null, "':'", "','", "'->'"
  };
  /** ANTLR symbolic names by token type (JS runtime: {@code recognizer.symbolicNames}). */
  public static final String[] symbolicNames = {
    null, "KW_ALIAS", "KW_PROFILE", "KW_EXTENSION", "KW_INSTANCE", "KW_INSTANCEOF",
    "KW_INVARIANT", "KW_VALUESET", "KW_CODESYSTEM", "KW_RULESET", "KW_MAPPING",
    "KW_LOGICAL", "KW_RESOURCE", "KW_PARENT", "KW_ID", "KW_TITLE", "KW_DESCRIPTION",
    "KW_EXPRESSION", "KW_XPATH", "KW_SEVERITY", "KW_USAGE", "KW_SOURCE",
    "KW_TARGET", "KW_CONTEXT", "KW_CHARACTERISTICS", "KW_MOD", "KW_MS", "KW_SU",
    "KW_TU", "KW_NORMATIVE", "KW_DRAFT", "KW_FROM", "KW_EXAMPLE", "KW_PREFERRED",
    "KW_EXTENSIBLE", "KW_REQUIRED", "KW_CONTAINS", "KW_NAMED", "KW_AND",
    "KW_ONLY", "KW_OR", "KW_OBEYS", "KW_TRUE", "KW_FALSE", "KW_INCLUDE",
    "KW_EXCLUDE", "KW_CODES", "KW_WHERE", "KW_VSREFERENCE", "KW_SYSTEM",
    "KW_EXACTLY", "KW_INSERT", "KW_CONTENTREFERENCE", "EQUAL", "STAR", "COLON",
    "COMMA", "ARROW", "STRING", "MULTILINE_STRING", "NUMBER", "UNIT", "CODE",
    "CONCEPT_STRING", "DATETIME", "TIME", "CARD", "REFERENCE", "CODEABLE_REFERENCE",
    "CANONICAL", "CARET_SEQUENCE", "REGEX", "BLOCK_COMMENT", "SEQUENCE",
    "WHITESPACE", "LINE_COMMENT", "PARAM_RULESET_REFERENCE", "RULESET_REFERENCE",
    "BRACKETED_PARAM", "LAST_BRACKETED_PARAM", "PLAIN_PARAM", "LAST_PLAIN_PARAM",
    "QUOTED_CONTEXT", "LAST_QUOTED_CONTEXT", "UNQUOTED_CONTEXT", "LAST_UNQUOTED_CONTEXT",
    "CONTEXT_WHITESPACE", "CODE_ITEM", "LAST_CODE_ITEM", "CODE_LIST_WHITESPACE"
  };

  public static class DocContext extends ParserRuleContext {
    public TerminalNode EOF() { return getToken(FSHParser.EOF, 0); }
    public List<EntityContext> entity() {
      return getRuleContexts(EntityContext.class);
    }
    public EntityContext entity(int i) {
      return getRuleContext(EntityContext.class,i);
    }
    public DocContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_doc; }
  }

  public static class EntityContext extends ParserRuleContext {
    public AliasContext alias() {
      return getRuleContext(AliasContext.class,0);
    }
    public ProfileContext profile() {
      return getRuleContext(ProfileContext.class,0);
    }
    public ExtensionContext extension() {
      return getRuleContext(ExtensionContext.class,0);
    }
    public InvariantContext invariant() {
      return getRuleContext(InvariantContext.class,0);
    }
    public InstanceContext instance() {
      return getRuleContext(InstanceContext.class,0);
    }
    public ValueSetContext valueSet() {
      return getRuleContext(ValueSetContext.class,0);
    }
    public CodeSystemContext codeSystem() {
      return getRuleContext(CodeSystemContext.class,0);
    }
    public RuleSetContext ruleSet() {
      return getRuleContext(RuleSetContext.class,0);
    }
    public ParamRuleSetContext paramRuleSet() {
      return getRuleContext(ParamRuleSetContext.class,0);
    }
    public MappingContext mapping() {
      return getRuleContext(MappingContext.class,0);
    }
    public LogicalContext logical() {
      return getRuleContext(LogicalContext.class,0);
    }
    public ResourceContext resource() {
      return getRuleContext(ResourceContext.class,0);
    }
    public EntityContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_entity; }
  }

  public static class AliasContext extends ParserRuleContext {
    public TerminalNode KW_ALIAS() { return getToken(FSHParser.KW_ALIAS, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public TerminalNode EQUAL() { return getToken(FSHParser.EQUAL, 0); }
    public TerminalNode SEQUENCE() { return getToken(FSHParser.SEQUENCE, 0); }
    public TerminalNode CODE() { return getToken(FSHParser.CODE, 0); }
    public AliasContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_alias; }
  }

  public static class ProfileContext extends ParserRuleContext {
    public TerminalNode KW_PROFILE() { return getToken(FSHParser.KW_PROFILE, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public List<SdMetadataContext> sdMetadata() {
      return getRuleContexts(SdMetadataContext.class);
    }
    public SdMetadataContext sdMetadata(int i) {
      return getRuleContext(SdMetadataContext.class,i);
    }
    public List<SdRuleContext> sdRule() {
      return getRuleContexts(SdRuleContext.class);
    }
    public SdRuleContext sdRule(int i) {
      return getRuleContext(SdRuleContext.class,i);
    }
    public ProfileContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_profile; }
  }

  public static class ExtensionContext extends ParserRuleContext {
    public TerminalNode KW_EXTENSION() { return getToken(FSHParser.KW_EXTENSION, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public List<SdMetadataContext> sdMetadata() {
      return getRuleContexts(SdMetadataContext.class);
    }
    public SdMetadataContext sdMetadata(int i) {
      return getRuleContext(SdMetadataContext.class,i);
    }
    public List<ContextContext> context() {
      return getRuleContexts(ContextContext.class);
    }
    public ContextContext context(int i) {
      return getRuleContext(ContextContext.class,i);
    }
    public List<SdRuleContext> sdRule() {
      return getRuleContexts(SdRuleContext.class);
    }
    public SdRuleContext sdRule(int i) {
      return getRuleContext(SdRuleContext.class,i);
    }
    public ExtensionContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_extension; }
  }

  public static class LogicalContext extends ParserRuleContext {
    public TerminalNode KW_LOGICAL() { return getToken(FSHParser.KW_LOGICAL, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public List<SdMetadataContext> sdMetadata() {
      return getRuleContexts(SdMetadataContext.class);
    }
    public SdMetadataContext sdMetadata(int i) {
      return getRuleContext(SdMetadataContext.class,i);
    }
    public List<CharacteristicsContext> characteristics() {
      return getRuleContexts(CharacteristicsContext.class);
    }
    public CharacteristicsContext characteristics(int i) {
      return getRuleContext(CharacteristicsContext.class,i);
    }
    public List<LrRuleContext> lrRule() {
      return getRuleContexts(LrRuleContext.class);
    }
    public LrRuleContext lrRule(int i) {
      return getRuleContext(LrRuleContext.class,i);
    }
    public LogicalContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_logical; }
  }

  public static class ResourceContext extends ParserRuleContext {
    public TerminalNode KW_RESOURCE() { return getToken(FSHParser.KW_RESOURCE, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public List<SdMetadataContext> sdMetadata() {
      return getRuleContexts(SdMetadataContext.class);
    }
    public SdMetadataContext sdMetadata(int i) {
      return getRuleContext(SdMetadataContext.class,i);
    }
    public List<LrRuleContext> lrRule() {
      return getRuleContexts(LrRuleContext.class);
    }
    public LrRuleContext lrRule(int i) {
      return getRuleContext(LrRuleContext.class,i);
    }
    public ResourceContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_resource; }
  }

  public static class SdMetadataContext extends ParserRuleContext {
    public ParentContext parent() {
      return getRuleContext(ParentContext.class,0);
    }
    public IdContext id() {
      return getRuleContext(IdContext.class,0);
    }
    public TitleContext title() {
      return getRuleContext(TitleContext.class,0);
    }
    public DescriptionContext description() {
      return getRuleContext(DescriptionContext.class,0);
    }
    public SdMetadataContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_sdMetadata; }
  }

  public static class SdRuleContext extends ParserRuleContext {
    public CardRuleContext cardRule() {
      return getRuleContext(CardRuleContext.class,0);
    }
    public FlagRuleContext flagRule() {
      return getRuleContext(FlagRuleContext.class,0);
    }
    public ValueSetRuleContext valueSetRule() {
      return getRuleContext(ValueSetRuleContext.class,0);
    }
    public FixedValueRuleContext fixedValueRule() {
      return getRuleContext(FixedValueRuleContext.class,0);
    }
    public ContainsRuleContext containsRule() {
      return getRuleContext(ContainsRuleContext.class,0);
    }
    public OnlyRuleContext onlyRule() {
      return getRuleContext(OnlyRuleContext.class,0);
    }
    public ObeysRuleContext obeysRule() {
      return getRuleContext(ObeysRuleContext.class,0);
    }
    public CaretValueRuleContext caretValueRule() {
      return getRuleContext(CaretValueRuleContext.class,0);
    }
    public InsertRuleContext insertRule() {
      return getRuleContext(InsertRuleContext.class,0);
    }
    public PathRuleContext pathRule() {
      return getRuleContext(PathRuleContext.class,0);
    }
    public SdRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_sdRule; }
  }

  public static class LrRuleContext extends ParserRuleContext {
    public SdRuleContext sdRule() {
      return getRuleContext(SdRuleContext.class,0);
    }
    public AddElementRuleContext addElementRule() {
      return getRuleContext(AddElementRuleContext.class,0);
    }
    public AddCRElementRuleContext addCRElementRule() {
      return getRuleContext(AddCRElementRuleContext.class,0);
    }
    public LrRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_lrRule; }
  }

  public static class InstanceContext extends ParserRuleContext {
    public TerminalNode KW_INSTANCE() { return getToken(FSHParser.KW_INSTANCE, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public List<InstanceMetadataContext> instanceMetadata() {
      return getRuleContexts(InstanceMetadataContext.class);
    }
    public InstanceMetadataContext instanceMetadata(int i) {
      return getRuleContext(InstanceMetadataContext.class,i);
    }
    public List<InstanceRuleContext> instanceRule() {
      return getRuleContexts(InstanceRuleContext.class);
    }
    public InstanceRuleContext instanceRule(int i) {
      return getRuleContext(InstanceRuleContext.class,i);
    }
    public InstanceContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_instance; }
  }

  public static class InstanceMetadataContext extends ParserRuleContext {
    public InstanceOfContext instanceOf() {
      return getRuleContext(InstanceOfContext.class,0);
    }
    public TitleContext title() {
      return getRuleContext(TitleContext.class,0);
    }
    public DescriptionContext description() {
      return getRuleContext(DescriptionContext.class,0);
    }
    public UsageContext usage() {
      return getRuleContext(UsageContext.class,0);
    }
    public InstanceMetadataContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_instanceMetadata; }
  }

  public static class InstanceRuleContext extends ParserRuleContext {
    public FixedValueRuleContext fixedValueRule() {
      return getRuleContext(FixedValueRuleContext.class,0);
    }
    public InsertRuleContext insertRule() {
      return getRuleContext(InsertRuleContext.class,0);
    }
    public PathRuleContext pathRule() {
      return getRuleContext(PathRuleContext.class,0);
    }
    public InstanceRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_instanceRule; }
  }

  public static class InvariantContext extends ParserRuleContext {
    public TerminalNode KW_INVARIANT() { return getToken(FSHParser.KW_INVARIANT, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public List<InvariantMetadataContext> invariantMetadata() {
      return getRuleContexts(InvariantMetadataContext.class);
    }
    public InvariantMetadataContext invariantMetadata(int i) {
      return getRuleContext(InvariantMetadataContext.class,i);
    }
    public List<InvariantRuleContext> invariantRule() {
      return getRuleContexts(InvariantRuleContext.class);
    }
    public InvariantRuleContext invariantRule(int i) {
      return getRuleContext(InvariantRuleContext.class,i);
    }
    public InvariantContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_invariant; }
  }

  public static class InvariantMetadataContext extends ParserRuleContext {
    public DescriptionContext description() {
      return getRuleContext(DescriptionContext.class,0);
    }
    public ExpressionContext expression() {
      return getRuleContext(ExpressionContext.class,0);
    }
    public XpathContext xpath() {
      return getRuleContext(XpathContext.class,0);
    }
    public SeverityContext severity() {
      return getRuleContext(SeverityContext.class,0);
    }
    public InvariantMetadataContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_invariantMetadata; }
  }

  public static class InvariantRuleContext extends ParserRuleContext {
    public FixedValueRuleContext fixedValueRule() {
      return getRuleContext(FixedValueRuleContext.class,0);
    }
    public InsertRuleContext insertRule() {
      return getRuleContext(InsertRuleContext.class,0);
    }
    public PathRuleContext pathRule() {
      return getRuleContext(PathRuleContext.class,0);
    }
    public InvariantRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_invariantRule; }
  }

  public static class ValueSetContext extends ParserRuleContext {
    public TerminalNode KW_VALUESET() { return getToken(FSHParser.KW_VALUESET, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public List<VsMetadataContext> vsMetadata() {
      return getRuleContexts(VsMetadataContext.class);
    }
    public VsMetadataContext vsMetadata(int i) {
      return getRuleContext(VsMetadataContext.class,i);
    }
    public List<VsRuleContext> vsRule() {
      return getRuleContexts(VsRuleContext.class);
    }
    public VsRuleContext vsRule(int i) {
      return getRuleContext(VsRuleContext.class,i);
    }
    public ValueSetContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_valueSet; }
  }

  public static class VsMetadataContext extends ParserRuleContext {
    public IdContext id() {
      return getRuleContext(IdContext.class,0);
    }
    public TitleContext title() {
      return getRuleContext(TitleContext.class,0);
    }
    public DescriptionContext description() {
      return getRuleContext(DescriptionContext.class,0);
    }
    public VsMetadataContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_vsMetadata; }
  }

  public static class VsRuleContext extends ParserRuleContext {
    public VsComponentContext vsComponent() {
      return getRuleContext(VsComponentContext.class,0);
    }
    public CaretValueRuleContext caretValueRule() {
      return getRuleContext(CaretValueRuleContext.class,0);
    }
    public CodeCaretValueRuleContext codeCaretValueRule() {
      return getRuleContext(CodeCaretValueRuleContext.class,0);
    }
    public InsertRuleContext insertRule() {
      return getRuleContext(InsertRuleContext.class,0);
    }
    public CodeInsertRuleContext codeInsertRule() {
      return getRuleContext(CodeInsertRuleContext.class,0);
    }
    public VsRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_vsRule; }
  }

  public static class CodeSystemContext extends ParserRuleContext {
    public TerminalNode KW_CODESYSTEM() { return getToken(FSHParser.KW_CODESYSTEM, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public List<CsMetadataContext> csMetadata() {
      return getRuleContexts(CsMetadataContext.class);
    }
    public CsMetadataContext csMetadata(int i) {
      return getRuleContext(CsMetadataContext.class,i);
    }
    public List<CsRuleContext> csRule() {
      return getRuleContexts(CsRuleContext.class);
    }
    public CsRuleContext csRule(int i) {
      return getRuleContext(CsRuleContext.class,i);
    }
    public CodeSystemContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_codeSystem; }
  }

  public static class CsMetadataContext extends ParserRuleContext {
    public IdContext id() {
      return getRuleContext(IdContext.class,0);
    }
    public TitleContext title() {
      return getRuleContext(TitleContext.class,0);
    }
    public DescriptionContext description() {
      return getRuleContext(DescriptionContext.class,0);
    }
    public CsMetadataContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_csMetadata; }
  }

  public static class CsRuleContext extends ParserRuleContext {
    public ConceptContext concept() {
      return getRuleContext(ConceptContext.class,0);
    }
    public CodeCaretValueRuleContext codeCaretValueRule() {
      return getRuleContext(CodeCaretValueRuleContext.class,0);
    }
    public CodeInsertRuleContext codeInsertRule() {
      return getRuleContext(CodeInsertRuleContext.class,0);
    }
    public CsRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_csRule; }
  }

  public static class RuleSetContext extends ParserRuleContext {
    public TerminalNode KW_RULESET() { return getToken(FSHParser.KW_RULESET, 0); }
    public TerminalNode RULESET_REFERENCE() { return getToken(FSHParser.RULESET_REFERENCE, 0); }
    public List<RuleSetRuleContext> ruleSetRule() {
      return getRuleContexts(RuleSetRuleContext.class);
    }
    public RuleSetRuleContext ruleSetRule(int i) {
      return getRuleContext(RuleSetRuleContext.class,i);
    }
    public RuleSetContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_ruleSet; }
  }

  public static class RuleSetRuleContext extends ParserRuleContext {
    public SdRuleContext sdRule() {
      return getRuleContext(SdRuleContext.class,0);
    }
    public AddElementRuleContext addElementRule() {
      return getRuleContext(AddElementRuleContext.class,0);
    }
    public AddCRElementRuleContext addCRElementRule() {
      return getRuleContext(AddCRElementRuleContext.class,0);
    }
    public ConceptContext concept() {
      return getRuleContext(ConceptContext.class,0);
    }
    public CodeCaretValueRuleContext codeCaretValueRule() {
      return getRuleContext(CodeCaretValueRuleContext.class,0);
    }
    public CodeInsertRuleContext codeInsertRule() {
      return getRuleContext(CodeInsertRuleContext.class,0);
    }
    public VsComponentContext vsComponent() {
      return getRuleContext(VsComponentContext.class,0);
    }
    public MappingRuleContext mappingRule() {
      return getRuleContext(MappingRuleContext.class,0);
    }
    public RuleSetRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_ruleSetRule; }
  }

  public static class ParamRuleSetContext extends ParserRuleContext {
    public TerminalNode KW_RULESET() { return getToken(FSHParser.KW_RULESET, 0); }
    public ParamRuleSetRefContext paramRuleSetRef() {
      return getRuleContext(ParamRuleSetRefContext.class,0);
    }
    public ParamRuleSetContentContext paramRuleSetContent() {
      return getRuleContext(ParamRuleSetContentContext.class,0);
    }
    public ParamRuleSetContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_paramRuleSet; }
  }

  public static class ParamRuleSetRefContext extends ParserRuleContext {
    public TerminalNode PARAM_RULESET_REFERENCE() { return getToken(FSHParser.PARAM_RULESET_REFERENCE, 0); }
    public LastParameterContext lastParameter() {
      return getRuleContext(LastParameterContext.class,0);
    }
    public List<ParameterContext> parameter() {
      return getRuleContexts(ParameterContext.class);
    }
    public ParameterContext parameter(int i) {
      return getRuleContext(ParameterContext.class,i);
    }
    public ParamRuleSetRefContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_paramRuleSetRef; }
  }

  public static class ParameterContext extends ParserRuleContext {
    public TerminalNode BRACKETED_PARAM() { return getToken(FSHParser.BRACKETED_PARAM, 0); }
    public TerminalNode PLAIN_PARAM() { return getToken(FSHParser.PLAIN_PARAM, 0); }
    public ParameterContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_parameter; }
  }

  public static class LastParameterContext extends ParserRuleContext {
    public TerminalNode LAST_BRACKETED_PARAM() { return getToken(FSHParser.LAST_BRACKETED_PARAM, 0); }
    public TerminalNode LAST_PLAIN_PARAM() { return getToken(FSHParser.LAST_PLAIN_PARAM, 0); }
    public LastParameterContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_lastParameter; }
  }

  public static class ParamRuleSetContentContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public List<TerminalNode> KW_PROFILE() { return getTokens(FSHParser.KW_PROFILE); }
    public TerminalNode KW_PROFILE(int i) {
      return getToken(FSHParser.KW_PROFILE, i);
    }
    public List<TerminalNode> KW_ALIAS() { return getTokens(FSHParser.KW_ALIAS); }
    public TerminalNode KW_ALIAS(int i) {
      return getToken(FSHParser.KW_ALIAS, i);
    }
    public List<TerminalNode> KW_EXTENSION() { return getTokens(FSHParser.KW_EXTENSION); }
    public TerminalNode KW_EXTENSION(int i) {
      return getToken(FSHParser.KW_EXTENSION, i);
    }
    public List<TerminalNode> KW_INSTANCE() { return getTokens(FSHParser.KW_INSTANCE); }
    public TerminalNode KW_INSTANCE(int i) {
      return getToken(FSHParser.KW_INSTANCE, i);
    }
    public List<TerminalNode> KW_INVARIANT() { return getTokens(FSHParser.KW_INVARIANT); }
    public TerminalNode KW_INVARIANT(int i) {
      return getToken(FSHParser.KW_INVARIANT, i);
    }
    public List<TerminalNode> KW_VALUESET() { return getTokens(FSHParser.KW_VALUESET); }
    public TerminalNode KW_VALUESET(int i) {
      return getToken(FSHParser.KW_VALUESET, i);
    }
    public List<TerminalNode> KW_CODESYSTEM() { return getTokens(FSHParser.KW_CODESYSTEM); }
    public TerminalNode KW_CODESYSTEM(int i) {
      return getToken(FSHParser.KW_CODESYSTEM, i);
    }
    public List<TerminalNode> KW_RULESET() { return getTokens(FSHParser.KW_RULESET); }
    public TerminalNode KW_RULESET(int i) {
      return getToken(FSHParser.KW_RULESET, i);
    }
    public List<TerminalNode> KW_MAPPING() { return getTokens(FSHParser.KW_MAPPING); }
    public TerminalNode KW_MAPPING(int i) {
      return getToken(FSHParser.KW_MAPPING, i);
    }
    public List<TerminalNode> KW_LOGICAL() { return getTokens(FSHParser.KW_LOGICAL); }
    public TerminalNode KW_LOGICAL(int i) {
      return getToken(FSHParser.KW_LOGICAL, i);
    }
    public List<TerminalNode> KW_RESOURCE() { return getTokens(FSHParser.KW_RESOURCE); }
    public TerminalNode KW_RESOURCE(int i) {
      return getToken(FSHParser.KW_RESOURCE, i);
    }
    public ParamRuleSetContentContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_paramRuleSetContent; }
  }

  public static class MappingContext extends ParserRuleContext {
    public TerminalNode KW_MAPPING() { return getToken(FSHParser.KW_MAPPING, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public List<MappingMetadataContext> mappingMetadata() {
      return getRuleContexts(MappingMetadataContext.class);
    }
    public MappingMetadataContext mappingMetadata(int i) {
      return getRuleContext(MappingMetadataContext.class,i);
    }
    public List<MappingEntityRuleContext> mappingEntityRule() {
      return getRuleContexts(MappingEntityRuleContext.class);
    }
    public MappingEntityRuleContext mappingEntityRule(int i) {
      return getRuleContext(MappingEntityRuleContext.class,i);
    }
    public MappingContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_mapping; }
  }

  public static class MappingMetadataContext extends ParserRuleContext {
    public IdContext id() {
      return getRuleContext(IdContext.class,0);
    }
    public SourceContext source() {
      return getRuleContext(SourceContext.class,0);
    }
    public TargetContext target() {
      return getRuleContext(TargetContext.class,0);
    }
    public DescriptionContext description() {
      return getRuleContext(DescriptionContext.class,0);
    }
    public TitleContext title() {
      return getRuleContext(TitleContext.class,0);
    }
    public MappingMetadataContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_mappingMetadata; }
  }

  public static class MappingEntityRuleContext extends ParserRuleContext {
    public MappingRuleContext mappingRule() {
      return getRuleContext(MappingRuleContext.class,0);
    }
    public InsertRuleContext insertRule() {
      return getRuleContext(InsertRuleContext.class,0);
    }
    public PathRuleContext pathRule() {
      return getRuleContext(PathRuleContext.class,0);
    }
    public MappingEntityRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_mappingEntityRule; }
  }

  public static class ParentContext extends ParserRuleContext {
    public TerminalNode KW_PARENT() { return getToken(FSHParser.KW_PARENT, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public ParentContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_parent; }
  }

  public static class IdContext extends ParserRuleContext {
    public TerminalNode KW_ID() { return getToken(FSHParser.KW_ID, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public IdContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_id; }
  }

  public static class TitleContext extends ParserRuleContext {
    public TerminalNode KW_TITLE() { return getToken(FSHParser.KW_TITLE, 0); }
    public TerminalNode STRING() { return getToken(FSHParser.STRING, 0); }
    public TitleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_title; }
  }

  public static class DescriptionContext extends ParserRuleContext {
    public TerminalNode KW_DESCRIPTION() { return getToken(FSHParser.KW_DESCRIPTION, 0); }
    public TerminalNode STRING() { return getToken(FSHParser.STRING, 0); }
    public TerminalNode MULTILINE_STRING() { return getToken(FSHParser.MULTILINE_STRING, 0); }
    public DescriptionContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_description; }
  }

  public static class ExpressionContext extends ParserRuleContext {
    public TerminalNode KW_EXPRESSION() { return getToken(FSHParser.KW_EXPRESSION, 0); }
    public TerminalNode STRING() { return getToken(FSHParser.STRING, 0); }
    public TerminalNode MULTILINE_STRING() { return getToken(FSHParser.MULTILINE_STRING, 0); }
    public ExpressionContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_expression; }
  }

  public static class XpathContext extends ParserRuleContext {
    public TerminalNode KW_XPATH() { return getToken(FSHParser.KW_XPATH, 0); }
    public TerminalNode STRING() { return getToken(FSHParser.STRING, 0); }
    public XpathContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_xpath; }
  }

  public static class SeverityContext extends ParserRuleContext {
    public TerminalNode KW_SEVERITY() { return getToken(FSHParser.KW_SEVERITY, 0); }
    public TerminalNode CODE() { return getToken(FSHParser.CODE, 0); }
    public SeverityContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_severity; }
  }

  public static class InstanceOfContext extends ParserRuleContext {
    public TerminalNode KW_INSTANCEOF() { return getToken(FSHParser.KW_INSTANCEOF, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public InstanceOfContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_instanceOf; }
  }

  public static class UsageContext extends ParserRuleContext {
    public TerminalNode KW_USAGE() { return getToken(FSHParser.KW_USAGE, 0); }
    public TerminalNode CODE() { return getToken(FSHParser.CODE, 0); }
    public UsageContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_usage; }
  }

  public static class SourceContext extends ParserRuleContext {
    public TerminalNode KW_SOURCE() { return getToken(FSHParser.KW_SOURCE, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public SourceContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_source; }
  }

  public static class TargetContext extends ParserRuleContext {
    public TerminalNode KW_TARGET() { return getToken(FSHParser.KW_TARGET, 0); }
    public TerminalNode STRING() { return getToken(FSHParser.STRING, 0); }
    public TargetContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_target; }
  }

  public static class ContextContext extends ParserRuleContext {
    public TerminalNode KW_CONTEXT() { return getToken(FSHParser.KW_CONTEXT, 0); }
    public LastContextItemContext lastContextItem() {
      return getRuleContext(LastContextItemContext.class,0);
    }
    public List<ContextItemContext> contextItem() {
      return getRuleContexts(ContextItemContext.class);
    }
    public ContextItemContext contextItem(int i) {
      return getRuleContext(ContextItemContext.class,i);
    }
    public ContextContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_context; }
  }

  public static class ContextItemContext extends ParserRuleContext {
    public TerminalNode QUOTED_CONTEXT() { return getToken(FSHParser.QUOTED_CONTEXT, 0); }
    public TerminalNode UNQUOTED_CONTEXT() { return getToken(FSHParser.UNQUOTED_CONTEXT, 0); }
    public ContextItemContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_contextItem; }
  }

  public static class LastContextItemContext extends ParserRuleContext {
    public TerminalNode LAST_QUOTED_CONTEXT() { return getToken(FSHParser.LAST_QUOTED_CONTEXT, 0); }
    public TerminalNode LAST_UNQUOTED_CONTEXT() { return getToken(FSHParser.LAST_UNQUOTED_CONTEXT, 0); }
    public LastContextItemContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_lastContextItem; }
  }

  public static class CharacteristicsContext extends ParserRuleContext {
    public TerminalNode KW_CHARACTERISTICS() { return getToken(FSHParser.KW_CHARACTERISTICS, 0); }
    public TerminalNode LAST_CODE_ITEM() { return getToken(FSHParser.LAST_CODE_ITEM, 0); }
    public List<TerminalNode> CODE_ITEM() { return getTokens(FSHParser.CODE_ITEM); }
    public TerminalNode CODE_ITEM(int i) {
      return getToken(FSHParser.CODE_ITEM, i);
    }
    public CharacteristicsContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_characteristics; }
  }

  public static class CardRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public PathContext path() {
      return getRuleContext(PathContext.class,0);
    }
    public TerminalNode CARD() { return getToken(FSHParser.CARD, 0); }
    public List<FlagContext> flag() {
      return getRuleContexts(FlagContext.class);
    }
    public FlagContext flag(int i) {
      return getRuleContext(FlagContext.class,i);
    }
    public CardRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_cardRule; }
  }

  public static class FlagRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public List<PathContext> path() {
      return getRuleContexts(PathContext.class);
    }
    public PathContext path(int i) {
      return getRuleContext(PathContext.class,i);
    }
    public List<TerminalNode> KW_AND() { return getTokens(FSHParser.KW_AND); }
    public TerminalNode KW_AND(int i) {
      return getToken(FSHParser.KW_AND, i);
    }
    public List<FlagContext> flag() {
      return getRuleContexts(FlagContext.class);
    }
    public FlagContext flag(int i) {
      return getRuleContext(FlagContext.class,i);
    }
    public FlagRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_flagRule; }
  }

  public static class ValueSetRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public PathContext path() {
      return getRuleContext(PathContext.class,0);
    }
    public TerminalNode KW_FROM() { return getToken(FSHParser.KW_FROM, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public StrengthContext strength() {
      return getRuleContext(StrengthContext.class,0);
    }
    public ValueSetRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_valueSetRule; }
  }

  public static class FixedValueRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public PathContext path() {
      return getRuleContext(PathContext.class,0);
    }
    public TerminalNode EQUAL() { return getToken(FSHParser.EQUAL, 0); }
    public ValueContext value() {
      return getRuleContext(ValueContext.class,0);
    }
    public TerminalNode KW_EXACTLY() { return getToken(FSHParser.KW_EXACTLY, 0); }
    public FixedValueRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_fixedValueRule; }
  }

  public static class ContainsRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public PathContext path() {
      return getRuleContext(PathContext.class,0);
    }
    public TerminalNode KW_CONTAINS() { return getToken(FSHParser.KW_CONTAINS, 0); }
    public List<ItemContext> item() {
      return getRuleContexts(ItemContext.class);
    }
    public ItemContext item(int i) {
      return getRuleContext(ItemContext.class,i);
    }
    public List<TerminalNode> KW_AND() { return getTokens(FSHParser.KW_AND); }
    public TerminalNode KW_AND(int i) {
      return getToken(FSHParser.KW_AND, i);
    }
    public ContainsRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_containsRule; }
  }

  public static class OnlyRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public PathContext path() {
      return getRuleContext(PathContext.class,0);
    }
    public TerminalNode KW_ONLY() { return getToken(FSHParser.KW_ONLY, 0); }
    public List<TargetTypeContext> targetType() {
      return getRuleContexts(TargetTypeContext.class);
    }
    public TargetTypeContext targetType(int i) {
      return getRuleContext(TargetTypeContext.class,i);
    }
    public List<TerminalNode> KW_OR() { return getTokens(FSHParser.KW_OR); }
    public TerminalNode KW_OR(int i) {
      return getToken(FSHParser.KW_OR, i);
    }
    public OnlyRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_onlyRule; }
  }

  public static class ObeysRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public TerminalNode KW_OBEYS() { return getToken(FSHParser.KW_OBEYS, 0); }
    public List<NameContext> name() {
      return getRuleContexts(NameContext.class);
    }
    public NameContext name(int i) {
      return getRuleContext(NameContext.class,i);
    }
    public PathContext path() {
      return getRuleContext(PathContext.class,0);
    }
    public List<TerminalNode> KW_AND() { return getTokens(FSHParser.KW_AND); }
    public TerminalNode KW_AND(int i) {
      return getToken(FSHParser.KW_AND, i);
    }
    public ObeysRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_obeysRule; }
  }

  public static class CaretValueRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public CaretPathContext caretPath() {
      return getRuleContext(CaretPathContext.class,0);
    }
    public TerminalNode EQUAL() { return getToken(FSHParser.EQUAL, 0); }
    public ValueContext value() {
      return getRuleContext(ValueContext.class,0);
    }
    public PathContext path() {
      return getRuleContext(PathContext.class,0);
    }
    public CaretValueRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_caretValueRule; }
  }

  public static class CodeCaretValueRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public CaretPathContext caretPath() {
      return getRuleContext(CaretPathContext.class,0);
    }
    public TerminalNode EQUAL() { return getToken(FSHParser.EQUAL, 0); }
    public ValueContext value() {
      return getRuleContext(ValueContext.class,0);
    }
    public List<TerminalNode> CODE() { return getTokens(FSHParser.CODE); }
    public TerminalNode CODE(int i) {
      return getToken(FSHParser.CODE, i);
    }
    public CodeCaretValueRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_codeCaretValueRule; }
  }

  public static class MappingRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public TerminalNode ARROW() { return getToken(FSHParser.ARROW, 0); }
    public List<TerminalNode> STRING() { return getTokens(FSHParser.STRING); }
    public TerminalNode STRING(int i) {
      return getToken(FSHParser.STRING, i);
    }
    public PathContext path() {
      return getRuleContext(PathContext.class,0);
    }
    public TerminalNode CODE() { return getToken(FSHParser.CODE, 0); }
    public TerminalNode MULTILINE_STRING() { return getToken(FSHParser.MULTILINE_STRING, 0); }
    public MappingRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_mappingRule; }
  }

  public static class InsertRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public TerminalNode KW_INSERT() { return getToken(FSHParser.KW_INSERT, 0); }
    public TerminalNode RULESET_REFERENCE() { return getToken(FSHParser.RULESET_REFERENCE, 0); }
    public ParamRuleSetRefContext paramRuleSetRef() {
      return getRuleContext(ParamRuleSetRefContext.class,0);
    }
    public PathContext path() {
      return getRuleContext(PathContext.class,0);
    }
    public InsertRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_insertRule; }
  }

  public static class CodeInsertRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public TerminalNode KW_INSERT() { return getToken(FSHParser.KW_INSERT, 0); }
    public TerminalNode RULESET_REFERENCE() { return getToken(FSHParser.RULESET_REFERENCE, 0); }
    public ParamRuleSetRefContext paramRuleSetRef() {
      return getRuleContext(ParamRuleSetRefContext.class,0);
    }
    public List<TerminalNode> CODE() { return getTokens(FSHParser.CODE); }
    public TerminalNode CODE(int i) {
      return getToken(FSHParser.CODE, i);
    }
    public CodeInsertRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_codeInsertRule; }
  }

  public static class AddCRElementRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public PathContext path() {
      return getRuleContext(PathContext.class,0);
    }
    public TerminalNode CARD() { return getToken(FSHParser.CARD, 0); }
    public TerminalNode KW_CONTENTREFERENCE() { return getToken(FSHParser.KW_CONTENTREFERENCE, 0); }
    public List<TerminalNode> STRING() { return getTokens(FSHParser.STRING); }
    public TerminalNode STRING(int i) {
      return getToken(FSHParser.STRING, i);
    }
    public TerminalNode SEQUENCE() { return getToken(FSHParser.SEQUENCE, 0); }
    public TerminalNode CODE() { return getToken(FSHParser.CODE, 0); }
    public List<FlagContext> flag() {
      return getRuleContexts(FlagContext.class);
    }
    public FlagContext flag(int i) {
      return getRuleContext(FlagContext.class,i);
    }
    public TerminalNode MULTILINE_STRING() { return getToken(FSHParser.MULTILINE_STRING, 0); }
    public AddCRElementRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_addCRElementRule; }
  }

  public static class AddElementRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public PathContext path() {
      return getRuleContext(PathContext.class,0);
    }
    public TerminalNode CARD() { return getToken(FSHParser.CARD, 0); }
    public List<TargetTypeContext> targetType() {
      return getRuleContexts(TargetTypeContext.class);
    }
    public TargetTypeContext targetType(int i) {
      return getRuleContext(TargetTypeContext.class,i);
    }
    public List<TerminalNode> STRING() { return getTokens(FSHParser.STRING); }
    public TerminalNode STRING(int i) {
      return getToken(FSHParser.STRING, i);
    }
    public List<FlagContext> flag() {
      return getRuleContexts(FlagContext.class);
    }
    public FlagContext flag(int i) {
      return getRuleContext(FlagContext.class,i);
    }
    public List<TerminalNode> KW_OR() { return getTokens(FSHParser.KW_OR); }
    public TerminalNode KW_OR(int i) {
      return getToken(FSHParser.KW_OR, i);
    }
    public TerminalNode MULTILINE_STRING() { return getToken(FSHParser.MULTILINE_STRING, 0); }
    public AddElementRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_addElementRule; }
  }

  public static class PathRuleContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public PathContext path() {
      return getRuleContext(PathContext.class,0);
    }
    public PathRuleContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_pathRule; }
  }

  public static class VsComponentContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public VsConceptComponentContext vsConceptComponent() {
      return getRuleContext(VsConceptComponentContext.class,0);
    }
    public VsFilterComponentContext vsFilterComponent() {
      return getRuleContext(VsFilterComponentContext.class,0);
    }
    public TerminalNode KW_INCLUDE() { return getToken(FSHParser.KW_INCLUDE, 0); }
    public TerminalNode KW_EXCLUDE() { return getToken(FSHParser.KW_EXCLUDE, 0); }
    public VsComponentContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_vsComponent; }
  }

  public static class VsConceptComponentContext extends ParserRuleContext {
    public CodeContext code() {
      return getRuleContext(CodeContext.class,0);
    }
    public VsComponentFromContext vsComponentFrom() {
      return getRuleContext(VsComponentFromContext.class,0);
    }
    public VsConceptComponentContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_vsConceptComponent; }
  }

  public static class VsFilterComponentContext extends ParserRuleContext {
    public TerminalNode KW_CODES() { return getToken(FSHParser.KW_CODES, 0); }
    public VsComponentFromContext vsComponentFrom() {
      return getRuleContext(VsComponentFromContext.class,0);
    }
    public TerminalNode KW_WHERE() { return getToken(FSHParser.KW_WHERE, 0); }
    public VsFilterListContext vsFilterList() {
      return getRuleContext(VsFilterListContext.class,0);
    }
    public VsFilterComponentContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_vsFilterComponent; }
  }

  public static class VsComponentFromContext extends ParserRuleContext {
    public TerminalNode KW_FROM() { return getToken(FSHParser.KW_FROM, 0); }
    public VsFromSystemContext vsFromSystem() {
      return getRuleContext(VsFromSystemContext.class,0);
    }
    public VsFromValuesetContext vsFromValueset() {
      return getRuleContext(VsFromValuesetContext.class,0);
    }
    public TerminalNode KW_AND() { return getToken(FSHParser.KW_AND, 0); }
    public VsComponentFromContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_vsComponentFrom; }
  }

  public static class VsFromSystemContext extends ParserRuleContext {
    public TerminalNode KW_SYSTEM() { return getToken(FSHParser.KW_SYSTEM, 0); }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public VsFromSystemContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_vsFromSystem; }
  }

  public static class VsFromValuesetContext extends ParserRuleContext {
    public TerminalNode KW_VSREFERENCE() { return getToken(FSHParser.KW_VSREFERENCE, 0); }
    public List<NameContext> name() {
      return getRuleContexts(NameContext.class);
    }
    public NameContext name(int i) {
      return getRuleContext(NameContext.class,i);
    }
    public List<TerminalNode> KW_AND() { return getTokens(FSHParser.KW_AND); }
    public TerminalNode KW_AND(int i) {
      return getToken(FSHParser.KW_AND, i);
    }
    public VsFromValuesetContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_vsFromValueset; }
  }

  public static class VsFilterListContext extends ParserRuleContext {
    public List<VsFilterDefinitionContext> vsFilterDefinition() {
      return getRuleContexts(VsFilterDefinitionContext.class);
    }
    public VsFilterDefinitionContext vsFilterDefinition(int i) {
      return getRuleContext(VsFilterDefinitionContext.class,i);
    }
    public List<TerminalNode> KW_AND() { return getTokens(FSHParser.KW_AND); }
    public TerminalNode KW_AND(int i) {
      return getToken(FSHParser.KW_AND, i);
    }
    public VsFilterListContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_vsFilterList; }
  }

  public static class VsFilterDefinitionContext extends ParserRuleContext {
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public VsFilterOperatorContext vsFilterOperator() {
      return getRuleContext(VsFilterOperatorContext.class,0);
    }
    public VsFilterValueContext vsFilterValue() {
      return getRuleContext(VsFilterValueContext.class,0);
    }
    public VsFilterDefinitionContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_vsFilterDefinition; }
  }

  public static class VsFilterOperatorContext extends ParserRuleContext {
    public TerminalNode EQUAL() { return getToken(FSHParser.EQUAL, 0); }
    public TerminalNode SEQUENCE() { return getToken(FSHParser.SEQUENCE, 0); }
    public VsFilterOperatorContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_vsFilterOperator; }
  }

  public static class VsFilterValueContext extends ParserRuleContext {
    public CodeContext code() {
      return getRuleContext(CodeContext.class,0);
    }
    public TerminalNode KW_TRUE() { return getToken(FSHParser.KW_TRUE, 0); }
    public TerminalNode KW_FALSE() { return getToken(FSHParser.KW_FALSE, 0); }
    public TerminalNode REGEX() { return getToken(FSHParser.REGEX, 0); }
    public TerminalNode STRING() { return getToken(FSHParser.STRING, 0); }
    public VsFilterValueContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_vsFilterValue; }
  }

  public static class NameContext extends ParserRuleContext {
    public TerminalNode SEQUENCE() { return getToken(FSHParser.SEQUENCE, 0); }
    public TerminalNode NUMBER() { return getToken(FSHParser.NUMBER, 0); }
    public TerminalNode DATETIME() { return getToken(FSHParser.DATETIME, 0); }
    public TerminalNode TIME() { return getToken(FSHParser.TIME, 0); }
    public MostAlphaKeywordsContext mostAlphaKeywords() {
      return getRuleContext(MostAlphaKeywordsContext.class,0);
    }
    public NameContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_name; }
  }

  public static class PathContext extends ParserRuleContext {
    public TerminalNode SEQUENCE() { return getToken(FSHParser.SEQUENCE, 0); }
    public TerminalNode NUMBER() { return getToken(FSHParser.NUMBER, 0); }
    public TerminalNode DATETIME() { return getToken(FSHParser.DATETIME, 0); }
    public TerminalNode TIME() { return getToken(FSHParser.TIME, 0); }
    public MostAlphaKeywordsContext mostAlphaKeywords() {
      return getRuleContext(MostAlphaKeywordsContext.class,0);
    }
    public PathContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_path; }
  }

  public static class CaretPathContext extends ParserRuleContext {
    public TerminalNode CARET_SEQUENCE() { return getToken(FSHParser.CARET_SEQUENCE, 0); }
    public CaretPathContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_caretPath; }
  }

  public static class FlagContext extends ParserRuleContext {
    public TerminalNode KW_MOD() { return getToken(FSHParser.KW_MOD, 0); }
    public TerminalNode KW_MS() { return getToken(FSHParser.KW_MS, 0); }
    public TerminalNode KW_SU() { return getToken(FSHParser.KW_SU, 0); }
    public TerminalNode KW_TU() { return getToken(FSHParser.KW_TU, 0); }
    public TerminalNode KW_NORMATIVE() { return getToken(FSHParser.KW_NORMATIVE, 0); }
    public TerminalNode KW_DRAFT() { return getToken(FSHParser.KW_DRAFT, 0); }
    public FlagContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_flag; }
  }

  public static class StrengthContext extends ParserRuleContext {
    public TerminalNode KW_EXAMPLE() { return getToken(FSHParser.KW_EXAMPLE, 0); }
    public TerminalNode KW_PREFERRED() { return getToken(FSHParser.KW_PREFERRED, 0); }
    public TerminalNode KW_EXTENSIBLE() { return getToken(FSHParser.KW_EXTENSIBLE, 0); }
    public TerminalNode KW_REQUIRED() { return getToken(FSHParser.KW_REQUIRED, 0); }
    public StrengthContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_strength; }
  }

  public static class ValueContext extends ParserRuleContext {
    public TerminalNode STRING() { return getToken(FSHParser.STRING, 0); }
    public TerminalNode MULTILINE_STRING() { return getToken(FSHParser.MULTILINE_STRING, 0); }
    public TerminalNode NUMBER() { return getToken(FSHParser.NUMBER, 0); }
    public TerminalNode DATETIME() { return getToken(FSHParser.DATETIME, 0); }
    public TerminalNode TIME() { return getToken(FSHParser.TIME, 0); }
    public ReferenceContext reference() {
      return getRuleContext(ReferenceContext.class,0);
    }
    public CanonicalContext canonical() {
      return getRuleContext(CanonicalContext.class,0);
    }
    public CodeContext code() {
      return getRuleContext(CodeContext.class,0);
    }
    public QuantityContext quantity() {
      return getRuleContext(QuantityContext.class,0);
    }
    public RatioContext ratio() {
      return getRuleContext(RatioContext.class,0);
    }
    public BoolContext bool() {
      return getRuleContext(BoolContext.class,0);
    }
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public ValueContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_value; }
  }

  public static class ItemContext extends ParserRuleContext {
    public List<NameContext> name() {
      return getRuleContexts(NameContext.class);
    }
    public NameContext name(int i) {
      return getRuleContext(NameContext.class,i);
    }
    public TerminalNode CARD() { return getToken(FSHParser.CARD, 0); }
    public TerminalNode KW_NAMED() { return getToken(FSHParser.KW_NAMED, 0); }
    public List<FlagContext> flag() {
      return getRuleContexts(FlagContext.class);
    }
    public FlagContext flag(int i) {
      return getRuleContext(FlagContext.class,i);
    }
    public ItemContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_item; }
  }

  public static class CodeContext extends ParserRuleContext {
    public TerminalNode CODE() { return getToken(FSHParser.CODE, 0); }
    public TerminalNode STRING() { return getToken(FSHParser.STRING, 0); }
    public CodeContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_code; }
  }

  public static class ConceptContext extends ParserRuleContext {
    public TerminalNode STAR() { return getToken(FSHParser.STAR, 0); }
    public List<TerminalNode> CODE() { return getTokens(FSHParser.CODE); }
    public TerminalNode CODE(int i) {
      return getToken(FSHParser.CODE, i);
    }
    public List<TerminalNode> STRING() { return getTokens(FSHParser.STRING); }
    public TerminalNode STRING(int i) {
      return getToken(FSHParser.STRING, i);
    }
    public TerminalNode MULTILINE_STRING() { return getToken(FSHParser.MULTILINE_STRING, 0); }
    public ConceptContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_concept; }
  }

  public static class QuantityContext extends ParserRuleContext {
    public TerminalNode UNIT() { return getToken(FSHParser.UNIT, 0); }
    public TerminalNode CODE() { return getToken(FSHParser.CODE, 0); }
    public TerminalNode NUMBER() { return getToken(FSHParser.NUMBER, 0); }
    public TerminalNode STRING() { return getToken(FSHParser.STRING, 0); }
    public QuantityContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_quantity; }
  }

  public static class RatioContext extends ParserRuleContext {
    public List<RatioPartContext> ratioPart() {
      return getRuleContexts(RatioPartContext.class);
    }
    public RatioPartContext ratioPart(int i) {
      return getRuleContext(RatioPartContext.class,i);
    }
    public TerminalNode COLON() { return getToken(FSHParser.COLON, 0); }
    public RatioContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_ratio; }
  }

  public static class ReferenceContext extends ParserRuleContext {
    public TerminalNode REFERENCE() { return getToken(FSHParser.REFERENCE, 0); }
    public TerminalNode STRING() { return getToken(FSHParser.STRING, 0); }
    public ReferenceContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_reference; }
  }

  public static class ReferenceTypeContext extends ParserRuleContext {
    public TerminalNode REFERENCE() { return getToken(FSHParser.REFERENCE, 0); }
    public ReferenceTypeContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_referenceType; }
  }

  public static class CodeableReferenceTypeContext extends ParserRuleContext {
    public TerminalNode CODEABLE_REFERENCE() { return getToken(FSHParser.CODEABLE_REFERENCE, 0); }
    public CodeableReferenceTypeContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_codeableReferenceType; }
  }

  public static class CanonicalContext extends ParserRuleContext {
    public TerminalNode CANONICAL() { return getToken(FSHParser.CANONICAL, 0); }
    public CanonicalContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_canonical; }
  }

  public static class RatioPartContext extends ParserRuleContext {
    public TerminalNode NUMBER() { return getToken(FSHParser.NUMBER, 0); }
    public QuantityContext quantity() {
      return getRuleContext(QuantityContext.class,0);
    }
    public RatioPartContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_ratioPart; }
  }

  public static class BoolContext extends ParserRuleContext {
    public TerminalNode KW_TRUE() { return getToken(FSHParser.KW_TRUE, 0); }
    public TerminalNode KW_FALSE() { return getToken(FSHParser.KW_FALSE, 0); }
    public BoolContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_bool; }
  }

  public static class TargetTypeContext extends ParserRuleContext {
    public NameContext name() {
      return getRuleContext(NameContext.class,0);
    }
    public ReferenceTypeContext referenceType() {
      return getRuleContext(ReferenceTypeContext.class,0);
    }
    public CanonicalContext canonical() {
      return getRuleContext(CanonicalContext.class,0);
    }
    public CodeableReferenceTypeContext codeableReferenceType() {
      return getRuleContext(CodeableReferenceTypeContext.class,0);
    }
    public TargetTypeContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_targetType; }
  }

  public static class MostAlphaKeywordsContext extends ParserRuleContext {
    public TerminalNode KW_MS() { return getToken(FSHParser.KW_MS, 0); }
    public TerminalNode KW_SU() { return getToken(FSHParser.KW_SU, 0); }
    public TerminalNode KW_TU() { return getToken(FSHParser.KW_TU, 0); }
    public TerminalNode KW_NORMATIVE() { return getToken(FSHParser.KW_NORMATIVE, 0); }
    public TerminalNode KW_DRAFT() { return getToken(FSHParser.KW_DRAFT, 0); }
    public TerminalNode KW_FROM() { return getToken(FSHParser.KW_FROM, 0); }
    public TerminalNode KW_CONTAINS() { return getToken(FSHParser.KW_CONTAINS, 0); }
    public TerminalNode KW_NAMED() { return getToken(FSHParser.KW_NAMED, 0); }
    public TerminalNode KW_AND() { return getToken(FSHParser.KW_AND, 0); }
    public TerminalNode KW_ONLY() { return getToken(FSHParser.KW_ONLY, 0); }
    public TerminalNode KW_OR() { return getToken(FSHParser.KW_OR, 0); }
    public TerminalNode KW_OBEYS() { return getToken(FSHParser.KW_OBEYS, 0); }
    public TerminalNode KW_TRUE() { return getToken(FSHParser.KW_TRUE, 0); }
    public TerminalNode KW_FALSE() { return getToken(FSHParser.KW_FALSE, 0); }
    public TerminalNode KW_INCLUDE() { return getToken(FSHParser.KW_INCLUDE, 0); }
    public TerminalNode KW_EXCLUDE() { return getToken(FSHParser.KW_EXCLUDE, 0); }
    public TerminalNode KW_CODES() { return getToken(FSHParser.KW_CODES, 0); }
    public TerminalNode KW_WHERE() { return getToken(FSHParser.KW_WHERE, 0); }
    public TerminalNode KW_VSREFERENCE() { return getToken(FSHParser.KW_VSREFERENCE, 0); }
    public TerminalNode KW_SYSTEM() { return getToken(FSHParser.KW_SYSTEM, 0); }
    public TerminalNode KW_CONTENTREFERENCE() { return getToken(FSHParser.KW_CONTENTREFERENCE, 0); }
    public MostAlphaKeywordsContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
    @Override public int getRuleIndex() { return RULE_mostAlphaKeywords; }
  }

  /** Creates the empty context object for a rule index (used by the parser and the test adapter). */
  public static ParserRuleContext newContext(int ruleIndex, ParserRuleContext parent, int invokingState) {
    return switch (ruleIndex) {
      case RULE_doc -> new DocContext(parent, invokingState);
      case RULE_entity -> new EntityContext(parent, invokingState);
      case RULE_alias -> new AliasContext(parent, invokingState);
      case RULE_profile -> new ProfileContext(parent, invokingState);
      case RULE_extension -> new ExtensionContext(parent, invokingState);
      case RULE_logical -> new LogicalContext(parent, invokingState);
      case RULE_resource -> new ResourceContext(parent, invokingState);
      case RULE_sdMetadata -> new SdMetadataContext(parent, invokingState);
      case RULE_sdRule -> new SdRuleContext(parent, invokingState);
      case RULE_lrRule -> new LrRuleContext(parent, invokingState);
      case RULE_instance -> new InstanceContext(parent, invokingState);
      case RULE_instanceMetadata -> new InstanceMetadataContext(parent, invokingState);
      case RULE_instanceRule -> new InstanceRuleContext(parent, invokingState);
      case RULE_invariant -> new InvariantContext(parent, invokingState);
      case RULE_invariantMetadata -> new InvariantMetadataContext(parent, invokingState);
      case RULE_invariantRule -> new InvariantRuleContext(parent, invokingState);
      case RULE_valueSet -> new ValueSetContext(parent, invokingState);
      case RULE_vsMetadata -> new VsMetadataContext(parent, invokingState);
      case RULE_vsRule -> new VsRuleContext(parent, invokingState);
      case RULE_codeSystem -> new CodeSystemContext(parent, invokingState);
      case RULE_csMetadata -> new CsMetadataContext(parent, invokingState);
      case RULE_csRule -> new CsRuleContext(parent, invokingState);
      case RULE_ruleSet -> new RuleSetContext(parent, invokingState);
      case RULE_ruleSetRule -> new RuleSetRuleContext(parent, invokingState);
      case RULE_paramRuleSet -> new ParamRuleSetContext(parent, invokingState);
      case RULE_paramRuleSetRef -> new ParamRuleSetRefContext(parent, invokingState);
      case RULE_parameter -> new ParameterContext(parent, invokingState);
      case RULE_lastParameter -> new LastParameterContext(parent, invokingState);
      case RULE_paramRuleSetContent -> new ParamRuleSetContentContext(parent, invokingState);
      case RULE_mapping -> new MappingContext(parent, invokingState);
      case RULE_mappingMetadata -> new MappingMetadataContext(parent, invokingState);
      case RULE_mappingEntityRule -> new MappingEntityRuleContext(parent, invokingState);
      case RULE_parent -> new ParentContext(parent, invokingState);
      case RULE_id -> new IdContext(parent, invokingState);
      case RULE_title -> new TitleContext(parent, invokingState);
      case RULE_description -> new DescriptionContext(parent, invokingState);
      case RULE_expression -> new ExpressionContext(parent, invokingState);
      case RULE_xpath -> new XpathContext(parent, invokingState);
      case RULE_severity -> new SeverityContext(parent, invokingState);
      case RULE_instanceOf -> new InstanceOfContext(parent, invokingState);
      case RULE_usage -> new UsageContext(parent, invokingState);
      case RULE_source -> new SourceContext(parent, invokingState);
      case RULE_target -> new TargetContext(parent, invokingState);
      case RULE_context -> new ContextContext(parent, invokingState);
      case RULE_contextItem -> new ContextItemContext(parent, invokingState);
      case RULE_lastContextItem -> new LastContextItemContext(parent, invokingState);
      case RULE_characteristics -> new CharacteristicsContext(parent, invokingState);
      case RULE_cardRule -> new CardRuleContext(parent, invokingState);
      case RULE_flagRule -> new FlagRuleContext(parent, invokingState);
      case RULE_valueSetRule -> new ValueSetRuleContext(parent, invokingState);
      case RULE_fixedValueRule -> new FixedValueRuleContext(parent, invokingState);
      case RULE_containsRule -> new ContainsRuleContext(parent, invokingState);
      case RULE_onlyRule -> new OnlyRuleContext(parent, invokingState);
      case RULE_obeysRule -> new ObeysRuleContext(parent, invokingState);
      case RULE_caretValueRule -> new CaretValueRuleContext(parent, invokingState);
      case RULE_codeCaretValueRule -> new CodeCaretValueRuleContext(parent, invokingState);
      case RULE_mappingRule -> new MappingRuleContext(parent, invokingState);
      case RULE_insertRule -> new InsertRuleContext(parent, invokingState);
      case RULE_codeInsertRule -> new CodeInsertRuleContext(parent, invokingState);
      case RULE_addCRElementRule -> new AddCRElementRuleContext(parent, invokingState);
      case RULE_addElementRule -> new AddElementRuleContext(parent, invokingState);
      case RULE_pathRule -> new PathRuleContext(parent, invokingState);
      case RULE_vsComponent -> new VsComponentContext(parent, invokingState);
      case RULE_vsConceptComponent -> new VsConceptComponentContext(parent, invokingState);
      case RULE_vsFilterComponent -> new VsFilterComponentContext(parent, invokingState);
      case RULE_vsComponentFrom -> new VsComponentFromContext(parent, invokingState);
      case RULE_vsFromSystem -> new VsFromSystemContext(parent, invokingState);
      case RULE_vsFromValueset -> new VsFromValuesetContext(parent, invokingState);
      case RULE_vsFilterList -> new VsFilterListContext(parent, invokingState);
      case RULE_vsFilterDefinition -> new VsFilterDefinitionContext(parent, invokingState);
      case RULE_vsFilterOperator -> new VsFilterOperatorContext(parent, invokingState);
      case RULE_vsFilterValue -> new VsFilterValueContext(parent, invokingState);
      case RULE_name -> new NameContext(parent, invokingState);
      case RULE_path -> new PathContext(parent, invokingState);
      case RULE_caretPath -> new CaretPathContext(parent, invokingState);
      case RULE_flag -> new FlagContext(parent, invokingState);
      case RULE_strength -> new StrengthContext(parent, invokingState);
      case RULE_value -> new ValueContext(parent, invokingState);
      case RULE_item -> new ItemContext(parent, invokingState);
      case RULE_code -> new CodeContext(parent, invokingState);
      case RULE_concept -> new ConceptContext(parent, invokingState);
      case RULE_quantity -> new QuantityContext(parent, invokingState);
      case RULE_ratio -> new RatioContext(parent, invokingState);
      case RULE_reference -> new ReferenceContext(parent, invokingState);
      case RULE_referenceType -> new ReferenceTypeContext(parent, invokingState);
      case RULE_codeableReferenceType -> new CodeableReferenceTypeContext(parent, invokingState);
      case RULE_canonical -> new CanonicalContext(parent, invokingState);
      case RULE_ratioPart -> new RatioPartContext(parent, invokingState);
      case RULE_bool -> new BoolContext(parent, invokingState);
      case RULE_targetType -> new TargetTypeContext(parent, invokingState);
      case RULE_mostAlphaKeywords -> new MostAlphaKeywordsContext(parent, invokingState);
      default -> throw new IllegalArgumentException("rule " + ruleIndex);
    };
  }
  // ---- END GENERATED CST ----
}
