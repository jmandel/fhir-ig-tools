package org.hl7.fhir.sushi.testhelpers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.ValueSetFilter;
import org.hl7.fhir.sushi.fshtypes.rules.*;

// port of sushi/test/testhelpers/asserts.ts (SUSHI 3.21.1), the importer-related helpers
/**
 * Jest {@code toEqual} on FSH objects = {@link #assertToEqual} (deep, by fields, null/undefined fields ignored, class
 * types ignored; compared through {@link JsDump#canon}). TS optional trailing params are Java overloads; pass null
 * for an "undefined" in the middle. Numbers: TS {@code min: 1} is a Double here ({@code 1.0}); a JS number that a FSH
 * integer becomes is a BigInteger (see FSHImporter.extractNumberValue), a decimal a Double.
 */
public final class Asserts {
  private Asserts() {}

  /** Jest expect(actual).toEqual(expected). */
  public static void assertToEqual(Object expected, Object actual) {
    assertEquals(JsDump.canon(expected), JsDump.canon(actual));
  }

  private static <T> T as(Class<T> c, Rule rule) {
    assertInstanceOf(c, rule);
    return c.cast(rule);
  }

  /** TS splitOnPathPeriods(path).filter(p => p). */
  static List<String> splitPath(String path) {
    List<String> out = new ArrayList<>();
    for (String p : path.split("\\.(?![^\\[]*\\])", -1)) {
      if (!p.isEmpty()) out.add(p);
    }
    return out;
  }

  /** max: TS {@code max.toString()}: a number max prints like JS (1 -> "1"). */
  public static void assertCardRule(Rule rule, String path, double min, Object max) {
    CardRule cardRule = as(CardRule.class, rule);
    assertEquals(path, cardRule.path);
    assertEquals(Double.valueOf(min), cardRule.min);
    assertEquals(max instanceof Number n ? org.hl7.fhir.common.js.JsNumber.toString(n.doubleValue()) : max.toString(),
        cardRule.max);
  }

  /** Booleans compared with TS toBe: an unset flag (undefined) only equals a null expectation. */
  public static void assertFlagRule(Rule rule, String path, Boolean mustSupport, Boolean summary, Boolean modifier,
      Boolean trialUse, Boolean normative, Boolean draft) {
    FlagRule flagRule = as(FlagRule.class, rule);
    assertEquals(path, flagRule.path);
    assertEquals(mustSupport, flagRule.mustSupport);
    assertEquals(summary, flagRule.summary);
    assertEquals(modifier, flagRule.modifier);
    assertEquals(trialUse, flagRule.trialUse);
    assertEquals(normative, flagRule.normative);
    assertEquals(draft, flagRule.draft);
  }

  public static void assertBindingRule(Rule rule, String path, String valueSet, String strength) {
    BindingRule bindingRule = as(BindingRule.class, rule);
    assertEquals(path, bindingRule.path);
    assertEquals(valueSet, bindingRule.valueSet);
    assertEquals(strength, bindingRule.strength);
  }

  public static void assertAssignmentRule(Rule rule, String path, Object value) {
    assertAssignmentRule(rule, path, value, false, false, null);
  }

  public static void assertAssignmentRule(Rule rule, String path, Object value, boolean exactly) {
    assertAssignmentRule(rule, path, value, exactly, false, null);
  }

  public static void assertAssignmentRule(Rule rule, String path, Object value, boolean exactly, boolean isInstance) {
    assertAssignmentRule(rule, path, value, exactly, isInstance, null);
  }

  public static void assertAssignmentRule(Rule rule, String path, Object value, boolean exactly, boolean isInstance,
      String rawValue) {
    AssignmentRule assignmentRule = as(AssignmentRule.class, rule);
    assertEquals(path, assignmentRule.path);
    assertToEqual(value, assignmentRule.value);
    assertEquals(exactly, assignmentRule.exactly);
    assertEquals(isInstance, assignmentRule.isInstance);
    if (rawValue != null) assertEquals(rawValue, assignmentRule.rawValue);
  }

  public static void assertOnlyRule(Rule rule, String path, OnlyRuleType... types) {
    OnlyRule onlyRule = as(OnlyRule.class, rule);
    assertEquals(path, onlyRule.path);
    assertToEqual(Arrays.asList(types), onlyRule.types);
  }

  /** OnlyRuleType literal: {@code {type}}, plus flags (null = absent). */
  public static OnlyRuleType ort(String type) {
    return new OnlyRuleType(type);
  }

  public static OnlyRuleType ortRef(String type) {
    OnlyRuleType t = new OnlyRuleType(type);
    t.isReference = true;
    return t;
  }

  public static OnlyRuleType ortCanonical(String type) {
    OnlyRuleType t = new OnlyRuleType(type);
    t.isCanonical = true;
    return t;
  }

  public static OnlyRuleType ortCodeableRef(String type) {
    OnlyRuleType t = new OnlyRuleType(type);
    t.isCodeableReference = true;
    return t;
  }

  /** items: String (= {name}) or ContainsRuleItem. */
  public static void assertContainsRule(Rule rule, String path, Object... items) {
    ContainsRule containsRule = as(ContainsRule.class, rule);
    assertEquals(path, containsRule.path);
    List<ContainsRuleItem> itemObjects = new ArrayList<>();
    for (Object i : items) itemObjects.add(i instanceof String s ? new ContainsRuleItem(s) : (ContainsRuleItem) i);
    assertToEqual(itemObjects, containsRule.items);
  }

  public static void assertCaretValueRule(Rule rule, String path, String caretPath, Object value, boolean isInstance) {
    assertCaretValueRule(rule, path, caretPath, value, isInstance, null, null);
  }

  public static void assertCaretValueRule(Rule rule, String path, String caretPath, Object value, boolean isInstance,
      List<String> pathArray) {
    assertCaretValueRule(rule, path, caretPath, value, isInstance, pathArray, null);
  }

  public static void assertCaretValueRule(Rule rule, String path, String caretPath, Object value, boolean isInstance,
      List<String> pathArray, String rawValue) {
    CaretValueRule caretValueRule = as(CaretValueRule.class, rule);
    assertEquals(path, caretValueRule.path);
    assertEquals(caretPath, caretValueRule.caretPath);
    assertToEqual(value, caretValueRule.value);
    assertEquals(isInstance, caretValueRule.isInstance);
    assertToEqual(pathArray != null ? pathArray : splitPath(path), caretValueRule.pathArray);
    if (rawValue != null) assertEquals(rawValue, caretValueRule.rawValue);
  }

  public static void assertObeysRule(Rule rule, String path, String invariant) {
    ObeysRule obeysRule = as(ObeysRule.class, rule);
    assertEquals(path, obeysRule.path);
    assertEquals(invariant, obeysRule.invariant);
  }

  public static void assertInsertRule(Rule rule, String path, String ruleSet) {
    assertInsertRule(rule, path, ruleSet, List.of(), List.of());
  }

  public static void assertInsertRule(Rule rule, String path, String ruleSet, List<String> params) {
    assertInsertRule(rule, path, ruleSet, params, List.of());
  }

  public static void assertInsertRule(Rule rule, String path, String ruleSet, List<String> params,
      List<String> pathArray) {
    InsertRule insertRule = as(InsertRule.class, rule);
    assertEquals(path, insertRule.path);
    assertEquals(ruleSet, insertRule.ruleSet);
    assertToEqual(params == null ? List.of() : params, insertRule.params);
    assertToEqual(pathArray == null ? List.of() : pathArray, insertRule.pathArray);
  }

  public static void assertMappingRule(Rule rule, String path, String map, String comment, FshCode language) {
    MappingRule mappingRule = as(MappingRule.class, rule);
    assertEquals(path, mappingRule.path);
    assertEquals(map, mappingRule.map);
    assertEquals(comment, mappingRule.comment);
    assertToEqual(language, mappingRule.language);
  }

  public static void assertValueSetConceptComponent(Rule component, String fromSystem, List<String> fromValueSets,
      List<FshCode> concepts) {
    assertValueSetConceptComponent(component, fromSystem, fromValueSets, concepts, true);
  }

  public static void assertValueSetConceptComponent(Rule component, String fromSystem, List<String> fromValueSets,
      List<FshCode> concepts, boolean included) {
    ValueSetConceptComponentRule conceptComponent = as(ValueSetConceptComponentRule.class, component);
    assertEquals(fromSystem, conceptComponent.from.system);
    assertToEqual(fromValueSets, conceptComponent.from.valueSets);
    assertToEqual(concepts, conceptComponent.concepts);
    assertEquals(included, conceptComponent.inclusion);
  }

  public static void assertValueSetFilterComponent(Rule component, String fromSystem, List<String> fromValueSets,
      List<ValueSetFilter> filters) {
    assertValueSetFilterComponent(component, fromSystem, fromValueSets, filters, true);
  }

  public static void assertValueSetFilterComponent(Rule component, String fromSystem, List<String> fromValueSets,
      List<ValueSetFilter> filters, boolean included) {
    ValueSetFilterComponentRule filterComponent = as(ValueSetFilterComponentRule.class, component);
    assertEquals(fromSystem, filterComponent.from.system);
    assertToEqual(fromValueSets, filterComponent.from.valueSets);
    assertToEqual(filters, filterComponent.filters);
    assertEquals(included, filterComponent.inclusion);
  }

  /** TS AddElementArgs: card {min, max}, flags (only truthy ones checked), types, defs (only truthy ones checked). */
  public record AddElementArgs(double min, String max, Flags flags, List<OnlyRuleType> types, Defs defs) {}

  public record Flags(Boolean mustSupport, Boolean summary, Boolean modifier, Boolean trialUse, Boolean normative,
      Boolean draft) {}

  public record Defs(String short_, String definition, String contentReference) {}

  public static void assertAddElementRule(Rule rule, String path, AddElementArgs args) {
    AddElementRule addElementRule = as(AddElementRule.class, rule);
    assertEquals("AddElementRule", addElementRule.constructorName());
    assertEquals(path, addElementRule.path);
    assertEquals(Double.valueOf(args.min()), addElementRule.min);
    assertEquals(args.max(), addElementRule.max);
    Flags f = args.flags();
    if (f != null) {
      if (Boolean.TRUE.equals(f.mustSupport())) assertEquals(true, addElementRule.mustSupport);
      if (Boolean.TRUE.equals(f.summary())) assertEquals(true, addElementRule.summary);
      if (Boolean.TRUE.equals(f.modifier())) assertEquals(true, addElementRule.modifier);
      if (Boolean.TRUE.equals(f.trialUse())) assertEquals(true, addElementRule.trialUse);
      if (Boolean.TRUE.equals(f.normative())) assertEquals(true, addElementRule.normative);
      if (Boolean.TRUE.equals(f.draft())) assertEquals(true, addElementRule.draft);
    }
    // the parser leaves isReference/isCanonical/isCodeableReference unset when false
    List<String> expected = new ArrayList<>();
    for (OnlyRuleType t : args.types()) {
      OnlyRuleType e = new OnlyRuleType(t.type);
      if (Boolean.TRUE.equals(t.isReference)) e.isReference = true;
      else if (Boolean.TRUE.equals(t.isCanonical)) e.isCanonical = true;
      else if (Boolean.TRUE.equals(t.isCodeableReference)) e.isCodeableReference = true;
      expected.add(JsDump.canon(e));
    }
    List<String> actual = new ArrayList<>();
    for (OnlyRuleType t : addElementRule.types) actual.add(JsDump.canon(t));
    // toIncludeSameMembers: same multiset, any order
    List<String> e2 = new ArrayList<>(expected);
    List<String> a2 = new ArrayList<>(actual);
    e2.sort(null);
    a2.sort(null);
    assertEquals(e2, a2);
    Defs d = args.defs();
    if (d != null) {
      if (d.short_() != null && !d.short_().isEmpty()) assertEquals(d.short_(), addElementRule.short_);
      if (d.definition() != null && !d.definition().isEmpty()) assertEquals(d.definition(), addElementRule.definition);
      if (d.contentReference() != null && !d.contentReference().isEmpty()) {
        assertEquals(d.contentReference(), addElementRule.contentReference);
      }
    }
  }

  public static void assertConceptRule(Rule rule, String code) {
    assertConceptRule(rule, code, null, null, null);
  }

  public static void assertConceptRule(Rule rule, String code, String display) {
    assertConceptRule(rule, code, display, null, null);
  }

  public static void assertConceptRule(Rule rule, String code, String display, String definition) {
    assertConceptRule(rule, code, display, definition, null);
  }

  public static void assertConceptRule(Rule rule, String code, String display, String definition,
      List<String> hierarchy) {
    ConceptRule conceptRule = as(ConceptRule.class, rule);
    assertEquals(code, conceptRule.code);
    assertEquals(display, conceptRule.display);
    assertEquals(definition, conceptRule.definition);
    if (hierarchy != null) assertToEqual(hierarchy, conceptRule.hierarchy);
  }

  public static void assertPathRule(Rule rule, String path) {
    PathRule pathRule = as(PathRule.class, rule);
    assertEquals(path, pathRule.path);
  }
}
