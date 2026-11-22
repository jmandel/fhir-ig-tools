package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.fhirtypes.Common.applyInsertRules;
import static org.hl7.fhir.sushi.testhelpers.Asserts.assertAddElementRule;
import static org.hl7.fhir.sushi.testhelpers.Asserts.assertAssignmentRule;
import static org.hl7.fhir.sushi.testhelpers.Asserts.assertCardRule;
import static org.hl7.fhir.sushi.testhelpers.Asserts.assertCaretValueRule;
import static org.hl7.fhir.sushi.testhelpers.Asserts.assertConceptRule;
import static org.hl7.fhir.sushi.testhelpers.Asserts.assertValueSetConceptComponent;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshCodeSystem;
import org.hl7.fhir.sushi.fshtypes.FshValueSet;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.Logical;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.RuleSet;
import org.hl7.fhir.sushi.fshtypes.rules.AddElementRule;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.CardRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ConceptRule;
import org.hl7.fhir.sushi.fshtypes.rules.InsertRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.testhelpers.Asserts;
import org.hl7.fhir.sushi.testhelpers.MinimalConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/fhirtypes/applyInsertRules.test.ts (SUSHI 3.21.1)
@DisplayName("applyInsertRules")
class ApplyInsertRulesTest {
  FSHDocument doc;
  FSHTank tank;
  Profile profile;
  Instance instance;
  RuleSet ruleSet1;
  RuleSet ruleSet2;

  @BeforeEach
  void beforeEach() {
    doc = new FSHDocument("fileName");
    tank = new FSHTank(List.of(doc), MinimalConfig.minimalConfig());
    loggerSpy.reset();

    profile = new Profile("Foo").<Profile>withFile("Profile.fsh").withLocation(5, 6, 7, 16);
    profile.parent = "Observation";
    doc.profiles.put(profile.name, profile);

    instance = new Instance("Far");
    instance.instanceOf = "Patient";
    doc.instances.put(instance.name, instance);

    ruleSet1 = new RuleSet("Bar");
    doc.ruleSets.put(ruleSet1.name, ruleSet1);

    ruleSet2 = new RuleSet("Baz");
    doc.ruleSets.put(ruleSet2.name, ruleSet2);
  }

  static CardRule card(String path, double min, String max) {
    CardRule r = new CardRule(path);
    r.min = min;
    r.max = max;
    return r;
  }

  static InsertRule insert(String path, String ruleSet) {
    InsertRule r = new InsertRule(path);
    r.ruleSet = ruleSet;
    return r;
  }

  static String errorMessage() {
    return loggerSpy.getLastMessage("error");
  }

  @Test
  @DisplayName("applyInsertRules should apply rules from a single level insert rule")
  void t1() {
    ruleSet1.rules.add(card("category", 1, "1"));
    profile.rules.add(insert("", "Bar"));
    applyInsertRules(profile, tank);
    assertEquals(1, profile.rules.size());
    assertCardRule(profile.rules.get(0), "category", 1, "1");
  }

  @Test
  @DisplayName("applyInsertRules should apply rules from a single level insert rule with a path")
  void t2() {
    ruleSet1.rules.add(card("coding", 1, "*"));
    profile.rules.add(insert("category", "Bar"));
    applyInsertRules(profile, tank);
    assertEquals(1, profile.rules.size());
    assertCardRule(profile.rules.get(0), "category.coding", 1, "*");
  }

  @Test
  @DisplayName("applyInsertRules should apply a rule without a path from a single level insert rule with a path")
  void t3() {
    CaretValueRule caretRule = new CaretValueRule("");
    caretRule.caretPath = "short";
    caretRule.value = "foo";
    ruleSet1.rules.add(caretRule);
    profile.rules.add(insert("category", "Bar"));
    applyInsertRules(profile, tank);
    assertEquals(1, profile.rules.size());
    assertCaretValueRule(profile.rules.get(0), "category", "short", "foo", false, List.of());
  }

  @Test
  @DisplayName("applyInsertRules should only apply soft indexing once when applying rules from a rule with a path")
  void t4() {
    AssignmentRule rule1 = new AssignmentRule("family");
    rule1.value = "foo";
    rule1.exactly = false;
    rule1.isInstance = false;
    AssignmentRule rule2 = new AssignmentRule("given[0]");
    rule2.value = "bar";
    rule2.exactly = false;
    rule2.isInstance = false;
    ruleSet1.rules.add(rule1);
    ruleSet1.rules.add(rule2);
    instance.rules.add(insert("name[+]", "Bar"));
    applyInsertRules(instance, tank);
    assertEquals(2, instance.rules.size());
    assertAssignmentRule(instance.rules.get(0), "name[+].family", "foo", false, false);
    assertAssignmentRule(instance.rules.get(1), "name[=].given[0]", "bar", false, false);
  }

  @Test
  @DisplayName("applyInsertRules should not apply rules from a single level insert rule that are not valid")
  void t5() {
    ConceptRule concept = new ConceptRule("bear").<ConceptRule>withFile("Concept.fsh").withLocation(1, 2, 3, 4);
    ruleSet1.rules.add(concept);
    InsertRule insertRule = insert("", "Bar").<InsertRule>withFile("Insert.fsh").withLocation(5, 6, 7, 8);
    profile.rules.add(insertRule);
    applyInsertRules(profile, tank);
    assertEquals(0, profile.rules.size());
    assertMatches("ConceptRule.*Profile.*File: Concept\\.fsh.*Line: 1 - 3.*Applied in File: Insert\\.fsh.*Line: 5 - 7\\D*",
        "s", errorMessage());
  }

  @Test
  @DisplayName("applyInsertRules should apply rules from a single level insert rule in correct order")
  void t6() {
    ruleSet1.rules.add(card("subject", 1, "1"));
    profile.rules.add(card("category", 1, "1"));
    profile.rules.add(insert("", "Bar"));
    profile.rules.add(card("focus", 1, "1"));
    applyInsertRules(profile, tank);
    assertEquals(3, profile.rules.size());
    assertCardRule(profile.rules.get(0), "category", 1, "1");
    assertCardRule(profile.rules.get(1), "subject", 1, "1");
    assertCardRule(profile.rules.get(2), "focus", 1, "1");
  }

  @Test
  @DisplayName("applyInsertRules should apply rules from multiple single level insert rules in correct order")
  void t7() {
    ruleSet1.rules.add(card("subject", 1, "1"));
    ruleSet2.rules.add(card("focus", 1, "1"));
    profile.rules.add(insert("", "Bar"));
    profile.rules.add(insert("", "Baz"));
    applyInsertRules(profile, tank);
    assertEquals(2, profile.rules.size());
    assertCardRule(profile.rules.get(0), "subject", 1, "1");
    assertCardRule(profile.rules.get(1), "focus", 1, "1");
  }

  @Test
  @DisplayName("applyInsertRules should apply rules from repeated single level insert rules in correct order")
  void t8() {
    ruleSet1.rules.add(card("subject", 1, "1"));
    InsertRule barRule = insert("", "Bar");
    profile.rules.add(barRule);
    profile.rules.add(barRule);
    applyInsertRules(profile, tank);
    assertEquals(2, profile.rules.size());
    assertCardRule(profile.rules.get(0), "subject", 1, "1");
    assertCardRule(profile.rules.get(1), "subject", 1, "1");
  }

  @Test
  @DisplayName("applyInsertRules should apply rules from a nested insert rule in the correct order")
  void t9() {
    ruleSet1.rules.add(card("category", 1, "1"));
    ruleSet1.rules.add(insert("", "Baz"));
    ruleSet2.rules.add(card("subject", 1, "1"));
    profile.rules.add(insert("", "Bar"));
    profile.rules.add(card("focus", 1, "1"));
    applyInsertRules(profile, tank);
    assertEquals(3, profile.rules.size());
    assertCardRule(profile.rules.get(0), "category", 1, "1");
    assertCardRule(profile.rules.get(1), "subject", 1, "1");
    assertCardRule(profile.rules.get(2), "focus", 1, "1");
  }

  @Test
  @DisplayName("applyInsertRules should apply rules from a nested insert rule with paths")
  void t10() {
    ruleSet1.rules.add(card("coding", 1, "*"));
    ruleSet1.rules.add(insert("coding", "Baz"));
    ruleSet2.rules.add(card("system", 1, "1"));
    profile.rules.add(insert("category", "Bar"));
    profile.rules.add(card("focus", 1, "1"));
    applyInsertRules(profile, tank);
    assertEquals(3, profile.rules.size());
    assertCardRule(profile.rules.get(0), "category.coding", 1, "*");
    assertCardRule(profile.rules.get(1), "category.coding.system", 1, "1");
    assertCardRule(profile.rules.get(2), "focus", 1, "1");
  }

  @Test
  @DisplayName("applyInsertRules should apply rules from a nested insert rule even when they repeat")
  void t11() {
    InsertRule insertBazRule = insert("", "Baz");
    ruleSet1.rules.add(card("category", 1, "1"));
    ruleSet1.rules.add(insertBazRule);
    ruleSet2.rules.add(card("subject", 1, "1"));
    profile.rules.add(insertBazRule);
    profile.rules.add(insert("", "Bar"));
    profile.rules.add(card("focus", 1, "1"));
    applyInsertRules(profile, tank);
    assertEquals(4, profile.rules.size());
    assertCardRule(profile.rules.get(0), "subject", 1, "1");
    assertCardRule(profile.rules.get(1), "category", 1, "1");
    assertCardRule(profile.rules.get(2), "subject", 1, "1");
    assertCardRule(profile.rules.get(3), "focus", 1, "1");
  }

  @Test
  @DisplayName("applyInsertRules should convert a ConceptRule with a system to a ValueSetConceptComponent when applying to a FshValueSet")
  void t12() {
    FshValueSet vs = new FshValueSet("Foo").<FshValueSet>withFile("VS.fsh").withLocation(5, 6, 7, 16);
    doc.valueSets.put(vs.name, vs);
    ConceptRule concept = new ConceptRule("bear", "brown bear");
    concept.system = "ZOO";
    ruleSet1.rules.add(concept);
    vs.rules.add(insert("", "Bar"));
    applyInsertRules(vs, tank);
    assertEquals(1, vs.rules.size());
    assertValueSetConceptComponent(vs.rules.get(0), "ZOO", null, List.of(new FshCode("bear", "ZOO", "brown bear")),
        true);
  }

  @Test
  @DisplayName("applyInsertRules should not convert a ConceptRule without a system to a ValueSetConceptComponent when applying to a FshValueSet")
  void t13() {
    FshValueSet vs = new FshValueSet("Foo");
    doc.valueSets.put(vs.name, vs);
    ConceptRule concept = new ConceptRule("bear", "brown bear").<ConceptRule>withFile("RuleSets.fsh")
        .withLocation(8, 5, 8, 16);
    ruleSet1.rules.add(concept);
    vs.rules.add(insert("", "Bar").<InsertRule>withFile("VS.fsh").withLocation(7, 6, 7, 16));
    applyInsertRules(vs, tank);
    assertEquals(0, vs.rules.size());
    assertMatches("ConceptRule.*ValueSet.*File: RuleSets\\.fsh.*Line: 8.*Applied in File: VS\\.fsh.*Line: 7\\D*", "s",
        errorMessage());
  }

  @Test
  @DisplayName("applyInsertRules should log an error when a ConceptRule with a system is applied to a FshCodeSystem")
  void t14() {
    FshCodeSystem cs = new FshCodeSystem("Foo");
    doc.codeSystems.put(cs.name, cs);
    ConceptRule concept = new ConceptRule("bear", "brown bear").<ConceptRule>withFile("RuleSets.fsh")
        .withLocation(7, 5, 7, 12);
    concept.system = "ZOO";
    ruleSet1.rules.add(concept);
    cs.rules.add(insert("", "Bar").<InsertRule>withFile("CodeSystems.fsh").withLocation(3, 5, 3, 19));
    applyInsertRules(cs, tank);
    assertEquals(1, cs.rules.size());
    assertConceptRule(cs.rules.get(0), "bear", "brown bear", null, List.of());
    assertMatches("Do not include the system when listing concepts for a code system\\..*File: RuleSets\\.fsh.*Line: 7"
        + ".*Applied in File: CodeSystems\\.fsh.*Line: 3\\D*", "s", errorMessage());
  }

  @Test
  @DisplayName("applyInsertRules should log an error when a ConceptRule is inserted at a path")
  void t15() {
    FshCodeSystem cs = new FshCodeSystem("MyCodeSystem");
    doc.codeSystems.put(cs.name, cs);
    ConceptRule concept = new ConceptRule("bear", "Bear").<ConceptRule>withFile("RuleSets.fsh")
        .withLocation(7, 5, 7, 12);
    ruleSet1.rules.add(concept);
    cs.rules.add(insert("somePath", "Bar").<InsertRule>withFile("CodeSystems.fsh").withLocation(3, 5, 3, 19));
    applyInsertRules(cs, tank);
    assertEquals(1, cs.rules.size());
    assertConceptRule(cs.rules.get(0), "bear", "Bear", null, List.of());
    assertMatches("Do not insert a RuleSet at a path when the RuleSet adds a concept\\..*File: RuleSets\\.fsh.*Line: 7"
        + ".*Applied in File: CodeSystems\\.fsh.*Line: 3\\D*", "s", errorMessage());
  }

  @Test
  @DisplayName("applyInsertRules should not convert a ConceptRule to a ValueSetConceptComponent when applying to a Profile")
  void t16() {
    ConceptRule concept = new ConceptRule("bear", "brown bear").<ConceptRule>withFile("Concept.fsh")
        .withLocation(1, 2, 3, 4);
    concept.system = "ZOO";
    ruleSet1.rules.add(concept);
    profile.rules.add(insert("", "Bar").<InsertRule>withFile("Insert.fsh").withLocation(1, 2, 3, 4));
    applyInsertRules(profile, tank);
    assertEquals(0, profile.rules.size());
    assertMatches("ConceptRule.*Profile.*File: Concept\\.fsh.*Line: 1 - 3.*Applied in File: Insert\\.fsh.*Line: 1 - 3\\D*",
        "s", errorMessage());
  }

  @Test
  @DisplayName("applyInsertRules should ignore insert rules that cause a circular dependency, and log an error")
  void t17() {
    InsertRule insertBazRule = insert("", "Baz");
    InsertRule insertBarRule = insert("", "Bar").<InsertRule>withFile("Insert.fsh").withLocation(1, 2, 3, 4);
    ruleSet1.rules.add(insertBazRule);
    ruleSet2.rules.add(insertBarRule);
    ruleSet2.rules.add(card("subject", 1, "1"));
    profile.rules.add(insertBarRule);
    applyInsertRules(profile, tank);
    assertEquals(1, profile.rules.size());
    assertCardRule(profile.rules.get(0), "subject", 1, "1");
    assertMatches("Bar will cause a circular dependency.*File: Insert\\.fsh.*Line: 1 - 3.*", "s", errorMessage());
  }

  @Test
  @DisplayName("applyInsertRules should log an error when a ruleSet cannot be found")
  void t18() {
    ruleSet1.rules.add(card("category", 1, "1"));
    profile.rules.add(insert("", "Bam").<InsertRule>withFile("NoBam.fsh").withLocation(1, 2, 3, 4));
    applyInsertRules(profile, tank);
    assertEquals(0, profile.rules.size());
    assertMatches("Unable to find definition for RuleSet Bam.*File: NoBam\\.fsh.*Line: 1 - 3\\D*", "s",
        errorMessage());
  }

  @Test
  @DisplayName("applyInsertRules should log an error when applying context to a . path")
  void t19() {
    CaretValueRule caretValueRule = new CaretValueRule(".");
    caretValueRule.caretPath = "short";
    caretValueRule.path = ".";
    caretValueRule.value = "foo";
    ruleSet1.rules.add(caretValueRule);
    profile.rules.add(insert("category", "Bar").<InsertRule>withFile("Bar.fsh").withLocation(1, 2, 3, 4));
    applyInsertRules(profile, tank);
    assertEquals(1, profile.rules.size());
    assertCaretValueRule(profile.rules.get(0), ".", "short", "foo", false, List.of());
    assertMatches("The special '\\.' path.*File: Bar\\.fsh.*Line: 1 - 3\\D*", "s", errorMessage());
  }

  @Test
  @DisplayName("applyInsertRules should apply add element rules from a single level insert rule")
  void t20() {
    RuleSet ruleSet = new RuleSet("AddCoreElements");
    AddElementRule addElementRule1 = new AddElementRule("primaryId");
    addElementRule1.min = 1.0;
    addElementRule1.max = "1";
    addElementRule1.summary = true;
    addElementRule1.types = new ArrayList<>(List.of(new OnlyRuleType("uri")));
    addElementRule1.short_ = "Primary ID";
    AddElementRule addElementRule2 = new AddElementRule("primaryOrganization");
    addElementRule2.min = 1.0;
    addElementRule2.max = "1";
    addElementRule2.summary = true;
    addElementRule2.types = new ArrayList<>(List.of(OnlyRuleType.reference("Organization")));
    addElementRule2.short_ = "Primary Organization";
    AddElementRule addElementRule3 = new AddElementRule("sharedNotes");
    addElementRule3.min = 0.0;
    addElementRule3.max = "*";
    addElementRule3.types = new ArrayList<>(List.of(new OnlyRuleType("Annotation")));
    addElementRule3.short_ = "Shared notes";
    ruleSet.rules.add(addElementRule1);
    ruleSet.rules.add(addElementRule2);
    ruleSet.rules.add(addElementRule3);
    doc.ruleSets.put(ruleSet.name, ruleSet);

    Logical logical = new Logical("Foo").<Logical>withFile("Logical.fsh").withLocation(5, 6, 7, 16);
    doc.logicals.put(logical.name, logical);
    logical.rules.add(insert("", "AddCoreElements"));

    applyInsertRules(logical, tank);
    assertEquals(3, logical.rules.size());
    Asserts.Flags summary = new Asserts.Flags(null, true, null, null, null, null);
    assertAddElementRule(logical.rules.get(0), "primaryId", new Asserts.AddElementArgs(1, "1", summary,
        List.of(new OnlyRuleType("uri")), new Asserts.Defs("Primary ID", null, null)));
    assertAddElementRule(logical.rules.get(1), "primaryOrganization", new Asserts.AddElementArgs(1, "1", summary,
        List.of(OnlyRuleType.reference("Organization")), new Asserts.Defs("Primary Organization", null, null)));
    assertAddElementRule(logical.rules.get(2), "sharedNotes", new Asserts.AddElementArgs(0, "*", null,
        List.of(new OnlyRuleType("Annotation")), new Asserts.Defs("Shared notes", null, null)));
  }

  @Nested
  @DisplayName("#appliedRuleSet")
  class AppliedRuleSet {
    @Test
    @DisplayName("applyInsertRules #appliedRuleSet should apply rules from a single level insert rule with parameters")
    void t1() {
      RuleSet appliedSimpleRules = new RuleSet("SimpleRules");
      AssignmentRule valueRule = new AssignmentRule("valueString");
      valueRule.value = "Something";
      valueRule.exactly = false;
      valueRule.isInstance = false;
      appliedSimpleRules.rules.add(valueRule);
      appliedSimpleRules.rules.add(card("note", 0, "5"));
      doc.appliedRuleSets.put(Json.stringify(List.of("SimpleRules", "\"Something\"", "5")), appliedSimpleRules);

      InsertRule insertRule = insert("", "SimpleRules");
      insertRule.params = new ArrayList<>(List.of("\"Something\"", "5"));
      profile.rules.add(insertRule);

      applyInsertRules(profile, tank);
      assertEquals(2, profile.rules.size());
      assertAssignmentRule(profile.rules.get(0), "valueString", "Something", false, false);
      assertCardRule(profile.rules.get(1), "note", 0, 5.0);
    }

    @Test
    @DisplayName("applyInsertRules #appliedRuleSet should ignore insert rules with parameters that cause a circular dependency, and log an error")
    void t2() {
      RuleSet appliedSimpleRules = new RuleSet("SimpleRules");
      InsertRule insertFancy = insert("", "FancyRules");
      insertFancy.params = new ArrayList<>(List.of("5"));
      appliedSimpleRules.rules.add(card("note", 0, "5"));
      appliedSimpleRules.rules.add(insertFancy);

      RuleSet appliedFancyRules = new RuleSet("FancyRules");
      InsertRule insertSimple = insert("", "SimpleRules").<InsertRule>withFile("Fancy.fsh").withLocation(4, 9, 4, 29);
      insertSimple.params = new ArrayList<>(List.of("5"));
      appliedFancyRules.rules.add(card("interpretation", 0, "5"));
      appliedFancyRules.rules.add(insertSimple);

      doc.appliedRuleSets.put(Json.stringify(List.of("SimpleRules", "5")), appliedSimpleRules);
      doc.appliedRuleSets.put(Json.stringify(List.of("FancyRules", "5")), appliedFancyRules);

      InsertRule insertRule = insert("", "SimpleRules");
      insertRule.params = new ArrayList<>(List.of("5"));
      profile.rules.add(insertRule);

      applyInsertRules(profile, tank);
      assertEquals(2, profile.rules.size());
      assertCardRule(profile.rules.get(0), "note", 0, 5.0);
      assertCardRule(profile.rules.get(1), "interpretation", 0, 5.0);
      assertMatches("SimpleRules will cause a circular dependency", "s", errorMessage());
      assertMatches("File: Fancy\\.fsh.*Line: 4\\D*", "s", errorMessage());
    }
  }
}
