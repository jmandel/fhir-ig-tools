package org.hl7.fhir.sushi.importer.parser;

import static org.hl7.fhir.sushi.importer.parser.FSHParser.*;
import static org.hl7.fhir.sushi.importer.parser.Grammar.*;

// port of sushi/antlr/src/main/antlr/FSH.g4 and MiniFSH.g4 (SUSHI 3.21.1)
/** The FSH and MiniFSH parser grammars, rule by rule as in FSH.g4 / MiniFSH.g4 (same order, same structure). */
final class FshGrammar {
  private FshGrammar() {}

  static final Grammar FSH = fsh();
  static final Grammar MINI = mini();

  private static Grammar fsh() {
    Grammar g = new Grammar(CODE_LIST_WHITESPACE, literalNames, symbolicNames, ruleNames);
    // doc:                entity* EOF;
    g.rule("doc", seq(star(r("entity")), t(EOF)));
    // entity:             alias | profile | extension | invariant | instance | valueSet | codeSystem | ruleSet
    //                     | paramRuleSet | mapping | logical | resource;
    g.rule("entity", r("alias"), r("profile"), r("extension"), r("invariant"), r("instance"), r("valueSet"),
        r("codeSystem"), r("ruleSet"), r("paramRuleSet"), r("mapping"), r("logical"), r("resource"));
    // alias:              KW_ALIAS name EQUAL (SEQUENCE | CODE);
    g.rule("alias", seq(t(KW_ALIAS), r("name"), t(EQUAL), alts(t(SEQUENCE), t(CODE))));
    // profile:            KW_PROFILE name sdMetadata+ sdRule*;
    g.rule("profile", seq(t(KW_PROFILE), r("name"), plus(r("sdMetadata")), star(r("sdRule"))));
    // extension:          KW_EXTENSION name (sdMetadata | context)* sdRule*;
    g.rule("extension", seq(t(KW_EXTENSION), r("name"), star(r("sdMetadata"), r("context")), star(r("sdRule"))));
    // logical:            KW_LOGICAL name (sdMetadata | characteristics)* lrRule*;
    g.rule("logical", seq(t(KW_LOGICAL), r("name"), star(r("sdMetadata"), r("characteristics")), star(r("lrRule"))));
    // resource:           KW_RESOURCE name sdMetadata* lrRule*;
    g.rule("resource", seq(t(KW_RESOURCE), r("name"), star(r("sdMetadata")), star(r("lrRule"))));
    // sdMetadata:         parent | id | title | description;
    g.rule("sdMetadata", r("parent"), r("id"), r("title"), r("description"));
    // sdRule:             cardRule | flagRule | valueSetRule | fixedValueRule | containsRule | onlyRule | obeysRule
    //                     | caretValueRule | insertRule | pathRule;
    g.rule("sdRule", r("cardRule"), r("flagRule"), r("valueSetRule"), r("fixedValueRule"), r("containsRule"),
        r("onlyRule"), r("obeysRule"), r("caretValueRule"), r("insertRule"), r("pathRule"));
    // lrRule:             sdRule | addElementRule | addCRElementRule;
    g.rule("lrRule", r("sdRule"), r("addElementRule"), r("addCRElementRule"));
    // instance:           KW_INSTANCE name instanceMetadata* instanceRule*;
    g.rule("instance", seq(t(KW_INSTANCE), r("name"), star(r("instanceMetadata")), star(r("instanceRule"))));
    // instanceMetadata:   instanceOf | title | description | usage;
    g.rule("instanceMetadata", r("instanceOf"), r("title"), r("description"), r("usage"));
    // instanceRule:       fixedValueRule | insertRule | pathRule;
    g.rule("instanceRule", r("fixedValueRule"), r("insertRule"), r("pathRule"));
    // invariant:          KW_INVARIANT name invariantMetadata* invariantRule*;
    g.rule("invariant", seq(t(KW_INVARIANT), r("name"), star(r("invariantMetadata")), star(r("invariantRule"))));
    // invariantMetadata:  description | expression | xpath | severity;
    g.rule("invariantMetadata", r("description"), r("expression"), r("xpath"), r("severity"));
    // invariantRule:      fixedValueRule | insertRule | pathRule;
    g.rule("invariantRule", r("fixedValueRule"), r("insertRule"), r("pathRule"));
    // valueSet:           KW_VALUESET name vsMetadata* vsRule*;
    g.rule("valueSet", seq(t(KW_VALUESET), r("name"), star(r("vsMetadata")), star(r("vsRule"))));
    // vsMetadata:         id | title | description;
    g.rule("vsMetadata", r("id"), r("title"), r("description"));
    // vsRule:             vsComponent | caretValueRule | codeCaretValueRule | insertRule | codeInsertRule;
    g.rule("vsRule", r("vsComponent"), r("caretValueRule"), r("codeCaretValueRule"), r("insertRule"),
        r("codeInsertRule"));
    // codeSystem:         KW_CODESYSTEM name csMetadata* csRule*;
    g.rule("codeSystem", seq(t(KW_CODESYSTEM), r("name"), star(r("csMetadata")), star(r("csRule"))));
    // csMetadata:         id | title | description;
    g.rule("csMetadata", r("id"), r("title"), r("description"));
    // csRule:             concept | codeCaretValueRule | codeInsertRule;
    g.rule("csRule", r("concept"), r("codeCaretValueRule"), r("codeInsertRule"));
    // ruleSet:            KW_RULESET RULESET_REFERENCE ruleSetRule+;
    g.rule("ruleSet", seq(t(KW_RULESET), t(RULESET_REFERENCE), plus(r("ruleSetRule"))));
    // ruleSetRule:        sdRule | addElementRule | addCRElementRule | concept | codeCaretValueRule | codeInsertRule
    //                     | vsComponent | mappingRule;
    g.rule("ruleSetRule", r("sdRule"), r("addElementRule"), r("addCRElementRule"), r("concept"),
        r("codeCaretValueRule"), r("codeInsertRule"), r("vsComponent"), r("mappingRule"));
    // paramRuleSet:       KW_RULESET paramRuleSetRef paramRuleSetContent;
    g.rule("paramRuleSet", seq(t(KW_RULESET), r("paramRuleSetRef"), r("paramRuleSetContent")));
    // paramRuleSetRef:    PARAM_RULESET_REFERENCE parameter* lastParameter;
    g.rule("paramRuleSetRef", seq(t(PARAM_RULESET_REFERENCE), star(r("parameter")), r("lastParameter")));
    // parameter:          BRACKETED_PARAM | PLAIN_PARAM;
    g.rule("parameter", t(BRACKETED_PARAM), t(PLAIN_PARAM));
    // lastParameter:      LAST_BRACKETED_PARAM | LAST_PLAIN_PARAM;
    g.rule("lastParameter", t(LAST_BRACKETED_PARAM), t(LAST_PLAIN_PARAM));
    // paramRuleSetContent:   STAR ~(KW_PROFILE | KW_ALIAS | KW_EXTENSION | KW_INSTANCE | KW_INVARIANT | KW_VALUESET
    //                     | KW_CODESYSTEM | KW_RULESET | KW_MAPPING | KW_LOGICAL | KW_RESOURCE)*;
    g.rule("paramRuleSetContent", seq(t(STAR), star(not(KW_PROFILE, KW_ALIAS, KW_EXTENSION, KW_INSTANCE,
        KW_INVARIANT, KW_VALUESET, KW_CODESYSTEM, KW_RULESET, KW_MAPPING, KW_LOGICAL, KW_RESOURCE))));
    // mapping:            KW_MAPPING name mappingMetadata* mappingEntityRule*;
    g.rule("mapping", seq(t(KW_MAPPING), r("name"), star(r("mappingMetadata")), star(r("mappingEntityRule"))));
    // mappingMetadata:    id | source | target | description | title;
    g.rule("mappingMetadata", r("id"), r("source"), r("target"), r("description"), r("title"));
    // mappingEntityRule:  mappingRule | insertRule | pathRule;
    g.rule("mappingEntityRule", r("mappingRule"), r("insertRule"), r("pathRule"));

    // METADATA FIELDS
    g.rule("parent", seq(t(KW_PARENT), r("name")));
    g.rule("id", seq(t(KW_ID), r("name")));
    g.rule("title", seq(t(KW_TITLE), t(STRING)));
    g.rule("description", seq(t(KW_DESCRIPTION), alts(t(STRING), t(MULTILINE_STRING))));
    g.rule("expression", seq(t(KW_EXPRESSION), alts(t(STRING), t(MULTILINE_STRING))));
    g.rule("xpath", seq(t(KW_XPATH), t(STRING)));
    g.rule("severity", seq(t(KW_SEVERITY), t(CODE)));
    g.rule("instanceOf", seq(t(KW_INSTANCEOF), r("name")));
    g.rule("usage", seq(t(KW_USAGE), t(CODE)));
    g.rule("source", seq(t(KW_SOURCE), r("name")));
    g.rule("target", seq(t(KW_TARGET), t(STRING)));
    // context:            KW_CONTEXT contextItem* lastContextItem;
    g.rule("context", seq(t(KW_CONTEXT), star(r("contextItem")), r("lastContextItem")));
    g.rule("contextItem", t(QUOTED_CONTEXT), t(UNQUOTED_CONTEXT));
    g.rule("lastContextItem", t(LAST_QUOTED_CONTEXT), t(LAST_UNQUOTED_CONTEXT));
    // characteristics:    KW_CHARACTERISTICS CODE_ITEM* LAST_CODE_ITEM;
    g.rule("characteristics", seq(t(KW_CHARACTERISTICS), star(t(CODE_ITEM)), t(LAST_CODE_ITEM)));

    // RULES
    // cardRule:           STAR path CARD flag*;
    g.rule("cardRule", seq(t(STAR), r("path"), t(CARD), star(r("flag"))));
    // flagRule:           STAR path (KW_AND path)* flag+;
    g.rule("flagRule", seq(t(STAR), r("path"), star(seq(t(KW_AND), r("path"))), plus(r("flag"))));
    // valueSetRule:       STAR path KW_FROM name strength?;
    g.rule("valueSetRule", seq(t(STAR), r("path"), t(KW_FROM), r("name"), opt(r("strength"))));
    // fixedValueRule:     STAR path EQUAL value KW_EXACTLY?;
    g.rule("fixedValueRule", seq(t(STAR), r("path"), t(EQUAL), r("value"), opt(t(KW_EXACTLY))));
    // containsRule:       STAR path KW_CONTAINS item (KW_AND item)*;
    g.rule("containsRule", seq(t(STAR), r("path"), t(KW_CONTAINS), r("item"), star(seq(t(KW_AND), r("item")))));
    // onlyRule:           STAR path KW_ONLY targetType (KW_OR targetType)*;
    g.rule("onlyRule", seq(t(STAR), r("path"), t(KW_ONLY), r("targetType"), star(seq(t(KW_OR), r("targetType")))));
    // obeysRule:          STAR path? KW_OBEYS name (KW_AND name)*;
    g.rule("obeysRule", seq(t(STAR), opt(r("path")), t(KW_OBEYS), r("name"), star(seq(t(KW_AND), r("name")))));
    // caretValueRule:     STAR path? caretPath EQUAL value;
    g.rule("caretValueRule", seq(t(STAR), opt(r("path")), r("caretPath"), t(EQUAL), r("value")));
    // codeCaretValueRule: STAR CODE* caretPath EQUAL value;
    g.rule("codeCaretValueRule", seq(t(STAR), star(t(CODE)), r("caretPath"), t(EQUAL), r("value")));
    // mappingRule:        STAR path? ARROW STRING (STRING | MULTILINE_STRING)? CODE?;
    g.rule("mappingRule", seq(t(STAR), opt(r("path")), t(ARROW), t(STRING), opt(t(STRING), t(MULTILINE_STRING)),
        opt(t(CODE))));
    // insertRule:         STAR path? KW_INSERT (RULESET_REFERENCE | paramRuleSetRef);
    g.rule("insertRule", seq(t(STAR), opt(r("path")), t(KW_INSERT), alts(t(RULESET_REFERENCE), r("paramRuleSetRef"))));
    // codeInsertRule:     STAR CODE* KW_INSERT (RULESET_REFERENCE | paramRuleSetRef);
    g.rule("codeInsertRule", seq(t(STAR), star(t(CODE)), t(KW_INSERT), alts(t(RULESET_REFERENCE),
        r("paramRuleSetRef"))));
    // addCRElementRule:   STAR path CARD flag* KW_CONTENTREFERENCE (SEQUENCE | CODE) STRING (STRING | MULTILINE_STRING)?;
    g.rule("addCRElementRule", seq(t(STAR), r("path"), t(CARD), star(r("flag")), t(KW_CONTENTREFERENCE),
        alts(t(SEQUENCE), t(CODE)), t(STRING), opt(t(STRING), t(MULTILINE_STRING))));
    // addElementRule:     STAR path CARD flag* targetType (KW_OR targetType)* STRING (STRING | MULTILINE_STRING)?;
    g.rule("addElementRule", seq(t(STAR), r("path"), t(CARD), star(r("flag")), r("targetType"),
        star(seq(t(KW_OR), r("targetType"))), t(STRING), opt(t(STRING), t(MULTILINE_STRING))));
    // pathRule:           STAR path;
    g.rule("pathRule", seq(t(STAR), r("path")));

    // VALUESET COMPONENTS
    // vsComponent:        STAR ( KW_INCLUDE | KW_EXCLUDE )? ( vsConceptComponent | vsFilterComponent );
    g.rule("vsComponent", seq(t(STAR), opt(t(KW_INCLUDE), t(KW_EXCLUDE)),
        alts(r("vsConceptComponent"), r("vsFilterComponent"))));
    // vsConceptComponent: code vsComponentFrom?;
    g.rule("vsConceptComponent", seq(r("code"), opt(r("vsComponentFrom"))));
    // vsFilterComponent:  KW_CODES vsComponentFrom (KW_WHERE vsFilterList)?;
    g.rule("vsFilterComponent", seq(t(KW_CODES), r("vsComponentFrom"), opt(seq(t(KW_WHERE), r("vsFilterList")))));
    // vsComponentFrom:    KW_FROM (vsFromSystem (KW_AND vsFromValueset)? | vsFromValueset (KW_AND vsFromSystem)?);
    g.rule("vsComponentFrom", seq(t(KW_FROM), alts(
        seq(r("vsFromSystem"), opt(seq(t(KW_AND), r("vsFromValueset")))),
        seq(r("vsFromValueset"), opt(seq(t(KW_AND), r("vsFromSystem")))))));
    // vsFromSystem:       KW_SYSTEM name;
    g.rule("vsFromSystem", seq(t(KW_SYSTEM), r("name")));
    // vsFromValueset:     KW_VSREFERENCE name (KW_AND name)*;
    g.rule("vsFromValueset", seq(t(KW_VSREFERENCE), r("name"), star(seq(t(KW_AND), r("name")))));
    // vsFilterList:       vsFilterDefinition (KW_AND vsFilterDefinition)*;
    g.rule("vsFilterList", seq(r("vsFilterDefinition"), star(seq(t(KW_AND), r("vsFilterDefinition")))));
    // vsFilterDefinition: name vsFilterOperator vsFilterValue?;
    g.rule("vsFilterDefinition", seq(r("name"), r("vsFilterOperator"), opt(r("vsFilterValue"))));
    // vsFilterOperator:   EQUAL | SEQUENCE;
    g.rule("vsFilterOperator", t(EQUAL), t(SEQUENCE));
    // vsFilterValue:      code | KW_TRUE | KW_FALSE | REGEX | STRING;
    g.rule("vsFilterValue", r("code"), t(KW_TRUE), t(KW_FALSE), t(REGEX), t(STRING));

    // MISC
    // name:               SEQUENCE | NUMBER | DATETIME | TIME | mostAlphaKeywords;
    g.rule("name", t(SEQUENCE), t(NUMBER), t(DATETIME), t(TIME), r("mostAlphaKeywords"));
    // path:               SEQUENCE | NUMBER | DATETIME | TIME | mostAlphaKeywords;
    g.rule("path", t(SEQUENCE), t(NUMBER), t(DATETIME), t(TIME), r("mostAlphaKeywords"));
    // caretPath:          CARET_SEQUENCE;
    g.rule("caretPath", t(CARET_SEQUENCE));
    // flag:               KW_MOD | KW_MS | KW_SU | KW_TU | KW_NORMATIVE | KW_DRAFT;
    g.rule("flag", t(KW_MOD), t(KW_MS), t(KW_SU), t(KW_TU), t(KW_NORMATIVE), t(KW_DRAFT));
    // strength:           KW_EXAMPLE | KW_PREFERRED | KW_EXTENSIBLE | KW_REQUIRED;
    g.rule("strength", t(KW_EXAMPLE), t(KW_PREFERRED), t(KW_EXTENSIBLE), t(KW_REQUIRED));
    // value:              STRING | MULTILINE_STRING | NUMBER | DATETIME | TIME | reference | canonical | code
    //                     | quantity | ratio | bool | name;
    g.rule("value", t(STRING), t(MULTILINE_STRING), t(NUMBER), t(DATETIME), t(TIME), r("reference"),
        r("canonical"), r("code"), r("quantity"), r("ratio"), r("bool"), r("name"));
    // item:               name (KW_NAMED name)? CARD flag*;
    g.rule("item", seq(r("name"), opt(seq(t(KW_NAMED), r("name"))), t(CARD), star(r("flag"))));
    // code:               CODE STRING?;
    g.rule("code", seq(t(CODE), opt(t(STRING))));
    // concept:            STAR CODE+ STRING? (STRING | MULTILINE_STRING)?;
    g.rule("concept", seq(t(STAR), plus(t(CODE)), opt(t(STRING)), opt(t(STRING), t(MULTILINE_STRING))));
    // quantity:           NUMBER? (UNIT | CODE) STRING?;
    g.rule("quantity", seq(opt(t(NUMBER)), alts(t(UNIT), t(CODE)), opt(t(STRING))));
    // ratio:              ratioPart COLON ratioPart;
    g.rule("ratio", seq(r("ratioPart"), t(COLON), r("ratioPart")));
    // reference:          REFERENCE STRING?;
    g.rule("reference", seq(t(REFERENCE), opt(t(STRING))));
    g.rule("referenceType", t(REFERENCE));
    g.rule("codeableReferenceType", t(CODEABLE_REFERENCE));
    g.rule("canonical", t(CANONICAL));
    // ratioPart:          NUMBER | quantity;
    g.rule("ratioPart", t(NUMBER), r("quantity"));
    // bool:               KW_TRUE | KW_FALSE;
    g.rule("bool", t(KW_TRUE), t(KW_FALSE));
    // targetType:         name | referenceType | canonical | codeableReferenceType;
    g.rule("targetType", r("name"), r("referenceType"), r("canonical"), r("codeableReferenceType"));
    // mostAlphaKeywords:  KW_MS | KW_SU | KW_TU | KW_NORMATIVE | KW_DRAFT | KW_FROM | KW_CONTAINS | KW_NAMED | KW_AND
    //                     | KW_ONLY | KW_OR | KW_OBEYS | KW_TRUE | KW_FALSE | KW_INCLUDE | KW_EXCLUDE | KW_CODES
    //                     | KW_WHERE | KW_VSREFERENCE | KW_SYSTEM | KW_CONTENTREFERENCE;
    g.rule("mostAlphaKeywords", t(KW_MS), t(KW_SU), t(KW_TU), t(KW_NORMATIVE), t(KW_DRAFT), t(KW_FROM),
        t(KW_CONTAINS), t(KW_NAMED), t(KW_AND), t(KW_ONLY), t(KW_OR), t(KW_OBEYS), t(KW_TRUE), t(KW_FALSE),
        t(KW_INCLUDE), t(KW_EXCLUDE), t(KW_CODES), t(KW_WHERE), t(KW_VSREFERENCE), t(KW_SYSTEM),
        t(KW_CONTENTREFERENCE));
    g.resolve();
    return g;
  }

  private static Grammar mini() {
    Grammar g = new Grammar(MiniFSHParser.LINE_COMMENT, MiniFSHParser.literalNames, MiniFSHParser.symbolicNames,
        MiniFSHParser.ruleNames);
    // ruleSet: someRule+ EOF;
    g.rule("ruleSet", seq(plus(r("someRule")), t(Token.EOF)));
    // someRule: STAR rulePart+;
    g.rule("someRule", seq(t(MiniFSHParser.STAR), plus(r("rulePart"))));
    // rulePart: SEQUENCE | STRING | MULTILINE_STRING;
    g.rule("rulePart", t(MiniFSHParser.SEQUENCE), t(MiniFSHParser.STRING), t(MiniFSHParser.MULTILINE_STRING));
    g.resolve();
    return g;
  }
}
