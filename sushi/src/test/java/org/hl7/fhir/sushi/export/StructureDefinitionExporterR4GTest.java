package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertContainsText;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsonEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.json;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinitionType;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Extension;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.Invariant;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.RuleSet;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.BindingRule;
import org.hl7.fhir.sushi.fshtypes.rules.CardRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ConceptRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.sushi.fshtypes.rules.FlagRule;
import org.hl7.fhir.sushi.fshtypes.rules.InsertRule;
import org.hl7.fhir.sushi.fshtypes.rules.ObeysRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/export/StructureDefinitionExporter.test.ts (SUSHI 3.21.1), describe blocks
// 'StructureDefinitionExporter R4' > '#RulesWithSlices', '#toJSON', '#insertRules', '#fishForMetadata',
// '#fishForMetadatas' (lines 10301-11592)
@DisplayName("StructureDefinitionExporter R4")
class StructureDefinitionExporterR4GTest extends SdeTestBase {

  private static final String OWS_URL =
      "http://hl7.org/fhir/us/minimal/StructureDefinition/ObservationWithSlice";

  private static final String IMPORTANT_BINDING = """
      { "valueSet": "http://hl7.org/fhir/us/minimal/ImportantObservationCodes", "strength": "required" }""";
  private static final String MEDIOCRE_BINDING = """
      { "valueSet": "http://hl7.org/fhir/us/minimal/MediocreObservationCodes", "strength": "extensible" }""";
  private static final String REGULAR_BINDING = """
      { "valueSet": "http://hl7.org/fhir/us/minimal/RegularObservationCodes", "strength": "required" }""";

  private static List<OnlyRuleType> types(OnlyRuleType... t) {
    return new ArrayList<>(Arrays.asList(t));
  }

  private static OnlyRuleType t(String type) {
    return new OnlyRuleType(type);
  }

  private static List<ContainsRuleItem> items(String... names) {
    List<ContainsRuleItem> items = new ArrayList<>();
    for (String n : names) items.add(new ContainsRuleItem(n));
    return items;
  }

  private static CardRule card(String path, double min, String max) {
    CardRule rule = new CardRule(path);
    rule.min = min;
    rule.max = max;
    return rule;
  }

  private static CaretValueRule caret(String path, String caretPath, Object value) {
    CaretValueRule rule = new CaretValueRule(path);
    rule.caretPath = caretPath;
    rule.value = value;
    return rule;
  }

  private static BindingRule binding(String path, String valueSet, String strength) {
    BindingRule rule = new BindingRule(path);
    rule.valueSet = valueSet;
    rule.strength = strength;
    return rule;
  }

  private static int len(Object list) {
    return ((List<?>) list).size();
  }

  private static Object norm(Object v) {
    return Json.parse(Json.stringify(v));
  }

  /** Jest {@code expect(list).toContainEqual(expected)}; expected is a JS value or a JsonSerializable. */
  private static boolean containsEqual(Object list, Object expected) {
    if (!(list instanceof List<?> l)) return false;
    Object exp = norm(expected);
    for (Object e : l) {
      if (Js.isEqual(exp, norm(e))) return true;
    }
    return false;
  }

  private static void assertContainsEqual(Object list, Object expected) {
    assertTrue(containsEqual(list, expected),
        () -> "expected " + Json.stringify(list) + " to contain " + Json.stringify(expected));
  }

  private static void assertCard(ElementDefinition ed, int min, String max) {
    assertJsEqual(min, at(ed, "min"));
    assertEquals(max, at(ed, "max"));
  }

  @Nested
  @DisplayName("#RulesWithSlices")
  class RulesWithSlicesTests {
    // rules applied to slices, sliced elements, and their children
    Profile observationWithSlice;

    @BeforeEach
    void setUp() {
      // Profile: ObservationWithSlice
      // Parent: Observation
      // * category ^slicing.discriminator[0].type = #pattern
      // * category ^slicing.discriminator[0].path = "$this"
      // * category ^slicing.rules = #open
      // * category contains Procedure 0..1
      // * component ^slicing.discriminator[0].type = #pattern
      // * component ^slicing.discriminator[0].path = "$this"
      // * component ^slicing.rules = #open
      // * component contains Lab 0..1
      observationWithSlice = new Profile("ObservationWithSlice");
      observationWithSlice.parent = "Observation";
      CaretValueRule categorySlicingType = caret("category", "slicing.discriminator[0].type", new FshCode("pattern"));
      CaretValueRule categorySlicingPath = caret("category", "slicing.discriminator[0].path", "$this");
      CaretValueRule categorySlicingRules = caret("category", "slicing.rules", new FshCode("open"));
      ContainsRule categoryContainsProcedure = new ContainsRule("category");
      categoryContainsProcedure.items = items("Procedure");
      CardRule procedureCard = card("category[Procedure]", 0, "1");
      CaretValueRule componentSlicingType =
          caret("component", "slicing.discriminator[0].type", new FshCode("pattern"));
      CaretValueRule componentSlicingPath = caret("component", "slicing.discriminator[0].path", "$this");
      CaretValueRule componentSlicingRules = caret("component", "slicing.rules", new FshCode("open"));
      ContainsRule componentContainsLab = new ContainsRule("component");
      componentContainsLab.items = items("Lab");
      CardRule labCard = card("component[Lab]", 0, "1");
      observationWithSlice.rules.addAll(List.<Rule>of(categorySlicingType, categorySlicingPath,
          categorySlicingRules, categoryContainsProcedure, procedureCard, componentSlicingType,
          componentSlicingPath, componentSlicingRules, componentContainsLab, labCard));
    }

    private StructureDefinition exportIt(Rule... rules) {
      observationWithSlice.rules.addAll(Arrays.asList(rules));
      doc.profiles.put(observationWithSlice.name, observationWithSlice);
      exporter.export();
      return pkg.profiles.get(0);
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply a CardRule that makes the cardinality of the child of a slice narrower")
    void cardChildOfSliceNarrower() {
      loggerSpy.reset();
      // * component[Lab].interpretation 2..2
      // * component.interpretation 0..5
      CardRule rootCard = card("component.interpretation", 0, "5");
      CardRule labCard = card("component[Lab].interpretation", 2, "2");

      StructureDefinition sd = exportIt(labCard, rootCard);
      ElementDefinition rootInterpretation = sd.findElement("Observation.component.interpretation");
      ElementDefinition labInterpretation = sd.findElement("Observation.component:Lab.interpretation");
      assertCard(rootInterpretation, 0, "5");
      assertCard(labInterpretation, 2, "2");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply a CardRule that would make the cardinality of a slice smaller than the root")
    void cardSliceSmallerThanRoot() {
      loggerSpy.reset();
      // component[Lab] has card 0..1
      // * component 1..5

      CardRule rootCard = new CardRule("component").<CardRule>withFile("Narrower.fsh").withLocation(7, 9, 7, 23);
      rootCard.min = 1.0;
      rootCard.max = "5";

      StructureDefinition sd = exportIt(rootCard);
      assertEquals("5", at(sd.findElement("Observation.component"), "max"));
      assertJsEqual(1, at(sd.findElement("Observation.component"), "min"));
      assertJsEqual(0, at(sd.findElement("Observation.component:Lab"), "min"));
      assertEquals("1", at(sd.findElement("Observation.component:Lab"), "max"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply a CardRule that would increase the minimum cardinality of a child of a slice")
    void cardIncreaseMinChildOfSlice() {
      // * component[Lab].interpretation 1..5
      // * component.interpretation 2..6
      CardRule labCard = card("component[Lab].interpretation", 1, "5");
      CardRule rootCard = card("component.interpretation", 2, "6");

      StructureDefinition sd = exportIt(labCard, rootCard);
      ElementDefinition rootInterpretation = sd.findElement("Observation.component.interpretation");
      ElementDefinition labInterpretation = sd.findElement("Observation.component:Lab.interpretation");
      assertCard(rootInterpretation, 2, "6");
      assertCard(labInterpretation, 2, "5");
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply a CardRule that would decrease the maximum cardinality of a child of a slice")
    void cardDecreaseMaxChildOfSlice() {
      // * component[Lab].interpretation 1..5
      // * component.interpretation 0..3
      CardRule labCard = card("component[Lab].interpretation", 1, "5");
      CardRule rootCard = card("component.interpretation", 0, "3");

      StructureDefinition sd = exportIt(labCard, rootCard);
      ElementDefinition rootInterpretation = sd.findElement("Observation.component.interpretation");
      ElementDefinition labInterpretation = sd.findElement("Observation.component:Lab.interpretation");
      assertCard(rootInterpretation, 0, "3");
      assertCard(labInterpretation, 1, "3");
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply a CardRule that would increase the minimum cardinality and decrease the maximum cardinality of a child of a slice")
    void cardIncreaseMinDecreaseMaxChildOfSlice() {
      // * component[Lab].interpretation 1..5
      // * component.interpretation 2..2
      CardRule labCard = card("component[Lab].interpretation", 1, "5");
      CardRule rootCard = card("component.interpretation", 2, "2");

      StructureDefinition sd = exportIt(labCard, rootCard);
      ElementDefinition rootInterpretation = sd.findElement("Observation.component.interpretation");
      ElementDefinition labInterpretation = sd.findElement("Observation.component:Lab.interpretation");
      assertCard(rootInterpretation, 2, "2");
      assertCard(labInterpretation, 2, "2");
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should not apply a CardRule that is incompatible with the existing cardinality of a child of a slice")
    void cardIncompatibleChildOfSlice() {
      // * component[Lab].interpretation 1..5
      // * component.interpretation 6..10
      CardRule labCard = card("component[Lab].interpretation", 1, "5");
      CardRule rootCard = new CardRule("component.interpretation").<CardRule>withFile("Incompatible.fsh")
          .withLocation(8, 3, 8, 25);
      rootCard.min = 6.0;
      rootCard.max = "10";

      StructureDefinition sd = exportIt(labCard, rootCard);
      ElementDefinition rootInterpretation = sd.findElement("Observation.component.interpretation");
      ElementDefinition labInterpretation = sd.findElement("Observation.component:Lab.interpretation");
      assertCard(rootInterpretation, 0, "*");
      assertCard(labInterpretation, 1, "5");
      assertContainsText("Cardinality on Observation.component.interpretation cannot be narrowed",
          loggerSpy.getLastMessage("error"));
      assertMatches("File: Incompatible\\.fsh.*Line: 8\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should not apply a CardRule that is incompatible with the existing cardinality on some of the children of slices")
    void cardIncompatibleSomeChildrenOfSlices() {
      // * component contains Field 0..1
      // * component[Lab].interpretation 1..5
      // * component[Field].interpretation 0..2
      // * component.interpretation 6..10
      ContainsRule containsField = new ContainsRule("component");
      containsField.items = items("Field");
      CardRule containsCard = card("component[Field]", 0, "1");
      CardRule labCard = card("component[Lab].interpretation", 1, "5");
      CardRule fieldCard = card("component[Field].interpretation", 0, "2");
      CardRule rootCard = new CardRule("component.interpretation").<CardRule>withFile("Incompatible.fsh")
          .withLocation(9, 3, 9, 23);
      rootCard.min = 6.0;
      rootCard.max = "10";

      StructureDefinition sd = exportIt(containsField, containsCard, labCard, fieldCard, rootCard);
      ElementDefinition rootInterpretation = sd.findElement("Observation.component.interpretation");
      ElementDefinition labInterpretation = sd.findElement("Observation.component:Lab.interpretation");
      ElementDefinition fieldInterpretation = sd.findElement("Observation.component:Field.interpretation");
      assertCard(rootInterpretation, 0, "*");
      assertCard(labInterpretation, 1, "5");
      assertCard(fieldInterpretation, 0, "2");
      assertContainsText("Cardinality on Observation.component.interpretation cannot be narrowed",
          loggerSpy.getLastMessage("error"));
      assertMatches("File: Incompatible\\.fsh.*Line: 9\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply a FlagRule on a sliced element that updates the flags on its slices")
    void flagOnSlicedElement() {
      // * component MS ?!
      // modifier should be applied to the slices, but must support is not applied to slices.
      // see https://confluence.hl7.org/display/FHIR/Guide+to+Designing+Resources
      FlagRule rootFlag = new FlagRule("component");
      rootFlag.mustSupport = true;
      rootFlag.modifier = true;

      StructureDefinition sd = exportIt(rootFlag);
      ElementDefinition rootComponent = sd.findElement("Observation.component");
      ElementDefinition labComponent = sd.findElement("Observation.component:Lab");
      assertEquals(true, at(rootComponent, "mustSupport"));
      assertFalse(Js.truthy(at(labComponent, "mustSupport")));
      assertEquals(true, at(rootComponent, "isModifier"));
      assertEquals(true, at(labComponent, "isModifier"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply a FlagRule on the child of a sliced element that updates the flags on the child of a slice")
    void flagOnChildOfSlicedElement() {
      // * component[Lab].interpretation 0..1 // this forces the creation of the unfolded slice
      // * component.interpretation MS
      // must support is applied to children of slices, in contrast to previous test case
      // again, see https://confluence.hl7.org/display/FHIR/Guide+to+Designing+Resources
      CardRule labCard = card("component[Lab].interpretation", 0, "1");
      FlagRule rootFlag = new FlagRule("component.interpretation");
      rootFlag.mustSupport = true;

      StructureDefinition sd = exportIt(labCard, rootFlag);
      ElementDefinition rootInterpretation = sd.findElement("Observation.component.interpretation");
      ElementDefinition labInterpretation = sd.findElement("Observation.component:Lab.interpretation");
      assertEquals(true, at(rootInterpretation, "mustSupport"));
      assertEquals(true, at(labInterpretation, "mustSupport"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply BindingRules on a slice, then a sliced element, with different value sets")
    void bindingSliceThenSlicedDifferent() {
      // * category[Procedure] from http://hl7.org/fhir/us/minimal/MediocreObservationCodes (extensible)
      // * category from http://hl7.org/fhir/us/minimal/ImportantObservationCodes (required)
      BindingRule procedureValueSet = binding("category[Procedure]",
          "http://hl7.org/fhir/us/minimal/MediocreObservationCodes", "extensible");
      BindingRule rootValueSet =
          binding("category", "http://hl7.org/fhir/us/minimal/ImportantObservationCodes", "required");

      StructureDefinition sd = exportIt(procedureValueSet, rootValueSet);
      ElementDefinition rootCategory = sd.findElement("Observation.category");
      ElementDefinition procedureCategory = sd.findElement("Observation.category:Procedure");
      assertJsonEqual(IMPORTANT_BINDING, at(rootCategory, "binding"));
      assertJsonEqual(MEDIOCRE_BINDING, at(procedureCategory, "binding"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply BindingRules on a sliced element, then a slice, with different value sets")
    void bindingSlicedThenSliceDifferent() {
      // * category from http://hl7.org/fhir/us/minimal/ImportantObservationCodes (required)
      // * category[Procedure] from http://hl7.org/fhir/us/minimal/MediocreObservationCodes (extensible)
      BindingRule rootValueSet =
          binding("category", "http://hl7.org/fhir/us/minimal/ImportantObservationCodes", "required");
      BindingRule procedureValueSet = binding("category[Procedure]",
          "http://hl7.org/fhir/us/minimal/MediocreObservationCodes", "extensible");

      StructureDefinition sd = exportIt(rootValueSet, procedureValueSet);
      ElementDefinition rootCategory = sd.findElement("Observation.category");
      ElementDefinition procedureCategory = sd.findElement("Observation.category:Procedure");
      assertJsonEqual(IMPORTANT_BINDING, at(rootCategory, "binding"));
      assertJsonEqual(MEDIOCRE_BINDING, at(procedureCategory, "binding"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply BindingRules on a slice, then the sliced element, with the same value set")
    void bindingSliceThenSlicedSame() {
      // * category[Procedure] from http://hl7.org/fhir/us/minimal/RegularObservationCodes (extensible)
      // * category from http://hl7.org/fhir/us/minimal/RegularObservationCodes (required)
      BindingRule procedureValueSet = binding("category[Procedure]",
          "http://hl7.org/fhir/us/minimal/RegularObservationCodes", "extensible");
      BindingRule rootValueSet =
          binding("category", "http://hl7.org/fhir/us/minimal/RegularObservationCodes", "required");

      StructureDefinition sd = exportIt(procedureValueSet, rootValueSet);
      ElementDefinition rootCategory = sd.findElement("Observation.category");
      ElementDefinition procedureCategory = sd.findElement("Observation.category:Procedure");
      assertJsonEqual(REGULAR_BINDING, at(rootCategory, "binding"));
      assertJsonEqual(REGULAR_BINDING, at(procedureCategory, "binding"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should not apply a BindingRule on a sliced element that would bind it to the same value set as the root, but more weakly")
    void bindingSliceWeakerSame() {
      // * category from http://hl7.org/fhir/us/minimal/RegularObservationCodes (required)
      // * category[Procedure] from http://hl7.org/fhir/us/minimal/RegularObservationCodes (extensible)
      BindingRule rootValueSet =
          binding("category", "http://hl7.org/fhir/us/minimal/RegularObservationCodes", "required");
      BindingRule procedureValueSet = new BindingRule("category[Procedure]").<BindingRule>withFile("Weaker.fsh")
          .withLocation(4, 8, 4, 23);
      procedureValueSet.valueSet = "http://hl7.org/fhir/us/minimal/RegularObservationCodes";
      procedureValueSet.strength = "extensible";

      StructureDefinition sd = exportIt(rootValueSet, procedureValueSet);
      ElementDefinition rootCategory = sd.findElement("Observation.category");
      assertJsonEqual(REGULAR_BINDING, at(rootCategory, "binding"));
      assertMatches("Cannot override required binding with extensible", "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: Weaker\\.fsh.*Line: 4\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply BindingRules on the child of a slice, then the child of a sliced element, with different value sets")
    void bindingChildSliceThenChildSlicedDifferent() {
      // * component[Lab].code from http://hl7.org/fhir/us/minimal/MediocreObservationCodes (extensible)
      // * component.code from http://hl7.org/fhir/us/minimal/ImportantObservationCodes (required)
      BindingRule labValueSet = binding("component[Lab].code",
          "http://hl7.org/fhir/us/minimal/MediocreObservationCodes", "extensible");
      BindingRule rootValueSet =
          binding("component.code", "http://hl7.org/fhir/us/minimal/ImportantObservationCodes", "required");

      StructureDefinition sd = exportIt(labValueSet, rootValueSet);
      ElementDefinition rootCode = sd.findElement("Observation.component.code");
      ElementDefinition labCode = sd.findElement("Observation.component:Lab.code");
      assertJsonEqual(IMPORTANT_BINDING, at(rootCode, "binding"));
      assertJsonEqual(MEDIOCRE_BINDING, at(labCode, "binding"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply BindingRules on the child of a sliced element, then the child of a slice, with different value sets")
    void bindingChildSlicedThenChildSliceDifferent() {
      // * component[Lab].code MS // force creation of element
      // * component.code from http://hl7.org/fhir/us/minimal/ImportantObservationCodes (required)
      // * component[Lab].code from http://hl7.org/fhir/us/minimal/MediocreObservationCodes (extensible)
      FlagRule flagRule = new FlagRule("component[Lab].code");
      flagRule.mustSupport = true;
      BindingRule rootValueSet =
          binding("component.code", "http://hl7.org/fhir/us/minimal/ImportantObservationCodes", "required");
      BindingRule labValueSet = binding("component[Lab].code",
          "http://hl7.org/fhir/us/minimal/MediocreObservationCodes", "extensible");

      StructureDefinition sd = exportIt(flagRule, rootValueSet, labValueSet);
      ElementDefinition rootCode = sd.findElement("Observation.component.code");
      ElementDefinition labCode = sd.findElement("Observation.component:Lab.code");
      assertJsonEqual(IMPORTANT_BINDING, at(rootCode, "binding"));
      assertJsonEqual(MEDIOCRE_BINDING, at(labCode, "binding"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply BindingRules on the child of a slice, then the child of the sliced element, with the same value set")
    void bindingChildSliceThenChildSlicedSame() {
      // * component[Lab].code from http://hl7.org/fhir/us/minimal/RegularObservationCodes (extensible)
      // * component.code from http://hl7.org/fhir/us/minimal/RegularObservationCodes (required)
      BindingRule labValueSet = binding("component[Lab].code",
          "http://hl7.org/fhir/us/minimal/RegularObservationCodes", "extensible");
      BindingRule rootValueSet =
          binding("component.code", "http://hl7.org/fhir/us/minimal/RegularObservationCodes", "required");

      StructureDefinition sd = exportIt(labValueSet, rootValueSet);
      ElementDefinition rootCode = sd.findElement("Observation.component.code");
      ElementDefinition labCode = sd.findElement("Observation.component:Lab.code");
      assertJsonEqual(REGULAR_BINDING, at(rootCode, "binding"));
      assertJsonEqual(REGULAR_BINDING, at(labCode, "binding"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should not apply a BindingRule on the child of a sliced element that would bind it to the same value set as the child of the root, but more weakly")
    void bindingChildSliceWeakerSame() {
      // * component.code from http://hl7.org/fhir/us/minimal/RegularObservationCodes (required)
      // * component[Lab].code from http://hl7.org/fhir/us/minimal/RegularObservationCodes (extensible)
      BindingRule rootValueSet =
          binding("component.code", "http://hl7.org/fhir/us/minimal/RegularObservationCodes", "required");
      BindingRule labValueSet = new BindingRule("component[Lab].code").<BindingRule>withFile("Weaker.fsh")
          .withLocation(9, 15, 9, 33);
      labValueSet.valueSet = "http://hl7.org/fhir/us/minimal/RegularObservationCodes";
      labValueSet.strength = "extensible";

      StructureDefinition sd = exportIt(rootValueSet, labValueSet);
      ElementDefinition rootCode = sd.findElement("Observation.component.code");
      // const labCode = sd.findElement('Observation.component:Lab.code');
      assertJsonEqual(REGULAR_BINDING, at(rootCode, "binding"));
      assertMatches("Cannot override required binding with extensible", "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: Weaker\\.fsh.*Line: 9\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @Disabled("TS it.todo")
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply a AssignmentRule on a sliced element that updates the assigned value on its slices")
    void todoAssignmentUpdatesSlices() {}

    @Test
    @Disabled("TS it.todo")
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should not apply a AssignmentRule on a sliced element that would invalidate the assigned value on a slice")
    void todoAssignmentInvalidatesSlice() {}

    @Test
    @Disabled("TS it.todo")
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply a ContainsRule on the child of a sliced element that updates the slices on the child of a slice")
    void todoContainsUpdatesChildSlices() {}

    @Test
    @Disabled("TS it.todo")
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should not apply a ContainsRule on the child of a sliced element that would invalidate slices within a defined slice")
    void todoContainsInvalidatesSlices() {}

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply an OnlyRule on a sliced element that updates the types on its slices")
    void onlyUpdatesSliceTypes() {
      loggerSpy.reset();
      // * component[Lab].value[x] MS // this forces the creation of the unfolded slice
      // * component.value[x] only Quantity or Ratio
      FlagRule labFlag = new FlagRule("component[Lab].value[x]");
      labFlag.mustSupport = true;
      OnlyRule rootOnly = new OnlyRule("component.value[x]");
      rootOnly.types = types(t("Quantity"), t("string"));

      StructureDefinition sd = exportIt(labFlag, rootOnly);
      ElementDefinition rootValue = sd.findElement("Observation.component.value[x]");
      ElementDefinition labValue = sd.findElement("Observation.component:Lab.value[x]");
      ElementDefinitionType expectedTypeQuantity = new ElementDefinitionType("Quantity");
      ElementDefinitionType expectedTypeString = new ElementDefinitionType("string");
      assertEquals(2, rootValue.getType().size());
      assertContainsEqual(rootValue.getType(), expectedTypeQuantity);
      assertContainsEqual(rootValue.getType(), expectedTypeString);
      assertEquals(2, labValue.getType().size());
      assertContainsEqual(labValue.getType(), expectedTypeQuantity);
      assertContainsEqual(labValue.getType(), expectedTypeString);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply an OnlyRule on a sliced element that includes more types than are allowed on its slices")
    void onlyMoreTypesThanSlices() {
      loggerSpy.reset();
      // * component[Lab].value[x] only Quantity
      // * component.value[x] only Quantity or Ratio
      OnlyRule labOnly = new OnlyRule("component[Lab].value[x]");
      labOnly.types = types(t("Quantity"));
      OnlyRule rootOnly = new OnlyRule("component.value[x]");
      rootOnly.types = types(t("Quantity"), t("Ratio"));

      StructureDefinition sd = exportIt(labOnly, rootOnly);
      ElementDefinition rootValue = sd.findElement("Observation.component.value[x]");
      ElementDefinition labValue = sd.findElement("Observation.component:Lab.value[x]");
      ElementDefinitionType expectedTypeQuantity = new ElementDefinitionType("Quantity");
      ElementDefinitionType expectedTypeRatio = new ElementDefinitionType("Ratio");
      assertEquals(2, rootValue.getType().size());
      assertContainsEqual(rootValue.getType(), expectedTypeQuantity);
      assertContainsEqual(rootValue.getType(), expectedTypeRatio);
      assertEquals(1, labValue.getType().size());
      assertContainsEqual(labValue.getType(), expectedTypeQuantity);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply an OnlyRule on a sliced element that removes types available on a slice")
    void onlyRemovesSliceTypes() {
      loggerSpy.reset();
      // * component[Lab].value[x] only Quantity or Ratio
      // * component.value[x] only Quantity
      OnlyRule labOnly = new OnlyRule("component[Lab].value[x]");
      labOnly.types = types(t("Quantity"), t("Ratio"));
      OnlyRule rootOnly = new OnlyRule("component.value[x]");
      rootOnly.types = types(t("Quantity"));

      StructureDefinition sd = exportIt(labOnly, rootOnly);
      ElementDefinition rootValue = sd.findElement("Observation.component.value[x]");
      ElementDefinition labValue = sd.findElement("Observation.component:Lab.value[x]");
      ElementDefinitionType expectedTypeQuantity = new ElementDefinitionType("Quantity");
      assertEquals(1, rootValue.getType().size());
      assertContainsEqual(rootValue.getType(), expectedTypeQuantity);
      assertEquals(1, labValue.getType().size());
      assertContainsEqual(labValue.getType(), expectedTypeQuantity);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply an OnlyRule using a profile on a sliced element that matches the types available on its slices")
    void onlyProfileMatchesSlices() {
      // Profile: MyQuestionnaire
      // Parent: http://hl7.org/fhir/StructureDefinition/cqf-questionnaire
      // * extension only http://hl7.org/fhir/StructureDefinition/cqf-library
      Profile profile = new Profile("MyQuestionnaire");
      profile.parent = "http://hl7.org/fhir/StructureDefinition/cqf-questionnaire";
      OnlyRule onlyRule = new OnlyRule("extension");
      onlyRule.types.add(t("http://hl7.org/fhir/StructureDefinition/cqf-library"));
      profile.rules.add(onlyRule);
      doc.profiles.put(profile.name, profile);
      exporter.export();
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition baseExtension = sd.findElement("Questionnaire.extension");
      ElementDefinition extensionSlice = sd.findElement("Questionnaire.extension:library");
      ElementDefinitionType expectedType = new ElementDefinitionType("Extension")
          .withProfiles("http://hl7.org/fhir/StructureDefinition/cqf-library");
      assertEquals(1, baseExtension.getType().size());
      assertContainsEqual(baseExtension.getType(), expectedType);
      assertEquals(1, extensionSlice.getType().size());
      assertContainsEqual(extensionSlice.getType(), expectedType);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply an OnlyRule using multiple profiles on a sliced element where at least one of the profiles matches the types available on its slices")
    void onlyMultipleProfilesOneMatchesSlices() {
      // Profile: MyQuestionnaire
      // Parent: http://hl7.org/fhir/StructureDefinition/cqf-questionnaire
      // * extension only http://hl7.org/fhir/StructureDefinition/cqf-library or
      //   http://hl7.org/fhir/StructureDefinition/familymemberhistory-type
      Profile profile = new Profile("MyQuestionnaire");
      profile.parent = "http://hl7.org/fhir/StructureDefinition/cqf-questionnaire";
      OnlyRule onlyRule = new OnlyRule("extension");
      onlyRule.types.add(t("http://hl7.org/fhir/StructureDefinition/cqf-library"));
      onlyRule.types.add(t("http://hl7.org/fhir/StructureDefinition/familymemberhistory-type"));
      profile.rules.add(onlyRule);
      doc.profiles.put(profile.name, profile);
      exporter.export();
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition baseExtension = sd.findElement("Questionnaire.extension");
      ElementDefinition extensionSlice = sd.findElement("Questionnaire.extension:library");
      ElementDefinitionType expectedBaseType = new ElementDefinitionType("Extension").withProfiles(
          "http://hl7.org/fhir/StructureDefinition/cqf-library",
          "http://hl7.org/fhir/StructureDefinition/familymemberhistory-type");
      ElementDefinitionType expectedSliceType = new ElementDefinitionType("Extension")
          .withProfiles("http://hl7.org/fhir/StructureDefinition/cqf-library");
      assertEquals(1, baseExtension.getType().size());
      assertContainsEqual(baseExtension.getType(), expectedBaseType);
      assertEquals(1, extensionSlice.getType().size());
      assertContainsEqual(extensionSlice.getType(), expectedSliceType);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should not apply an OnlyRule on a sliced element that would invalidate any of its slices")
    void onlyWouldInvalidateSlices() {
      // * component[Lab].value[x] only Quantity
      // * component.value[x] only Ratio // this would remove all types from the slice!
      OnlyRule labOnly = new OnlyRule("component[Lab].value[x]");
      labOnly.types = types(t("Quantity"));
      OnlyRule rootOnly = new OnlyRule("component.value[x]").<OnlyRule>withFile("RemoveType.fsh")
          .withLocation(12, 5, 12, 23);
      rootOnly.types = types(t("Ratio"));

      StructureDefinition sd = exportIt(labOnly, rootOnly);
      ElementDefinition rootValue = sd.findElement("Observation.component.value[x]");
      ElementDefinition labValue = sd.findElement("Observation.component:Lab.value[x]");
      ElementDefinitionType expectedTypeQuantity = new ElementDefinitionType("Quantity");
      assertEquals(11, rootValue.getType().size());
      assertEquals(1, labValue.getType().size());
      assertContainsEqual(labValue.getType(), expectedTypeQuantity);
      assertMatches("eliminate all types", "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: RemoveType\\.fsh.*Line: 12\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply an OnlyRule on a sliced element that would remove all types from a zeroed-out slice")
    void onlyRemovesAllTypesFromZeroedSlice() {
      // * component[Lab].value[x] only Quantity
      // * component[Lab] 0..0
      // * component.value[x] only Ratio
      OnlyRule labOnly = new OnlyRule("component[Lab].value[x]");
      labOnly.types = types(t("Quantity"));
      CardRule labCard = card("component[Lab]", 0, "0");
      OnlyRule rootOnly = new OnlyRule("component.value[x]");
      rootOnly.types = types(t("Ratio"));

      StructureDefinition sd = exportIt(labOnly, labCard, rootOnly);
      ElementDefinition rootValue = sd.findElement("Observation.component.value[x]");
      ElementDefinitionType expectedTypeRatio = new ElementDefinitionType("Ratio");
      assertEquals(1, rootValue.getType().size());
      assertContainsEqual(rootValue.getType(), expectedTypeRatio);
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply an OnlyRule on a sliced element that constrains the types on its slices to subtypes")
    void onlyConstrainsSliceTypesToSubtypes() {
      loggerSpy.reset();
      // * component[Lab].value[x] only Quantity
      // * component.value[x] only SimpleQuantity or Ratio
      OnlyRule labOnly = new OnlyRule("component[Lab].value[x]");
      labOnly.types = types(t("Quantity"));
      OnlyRule rootOnly = new OnlyRule("component.value[x]");
      rootOnly.types = types(t("SimpleQuantity"), t("Ratio"));

      StructureDefinition sd = exportIt(labOnly, rootOnly);
      ElementDefinition rootValue = sd.findElement("Observation.component.value[x]");
      ElementDefinition labValue = sd.findElement("Observation.component:Lab.value[x]");
      ElementDefinitionType expectedTypeRatio = new ElementDefinitionType("Ratio");
      ElementDefinitionType expectedTypeSimpleQuantity = new ElementDefinitionType("Quantity")
          .withProfiles("http://hl7.org/fhir/StructureDefinition/SimpleQuantity");
      assertEquals(2, rootValue.getType().size());
      assertContainsEqual(rootValue.getType(), expectedTypeRatio);
      assertContainsEqual(rootValue.getType(), expectedTypeSimpleQuantity);
      assertEquals(1, labValue.getType().size());
      assertContainsEqual(labValue.getType(), expectedTypeSimpleQuantity);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should log an error when a type constraint implicitly removes a choice on a sliced element")
    void typeConstraintRemovesChoiceOnSlicedElement() {
      // * component[Lab].valueString = "Please leave"
      // * component.value[x] only Quantity
      AssignmentRule labAssignedValue = new AssignmentRule("component[Lab].valueString");
      labAssignedValue.value = "Please leave";
      OnlyRule rootOnly = new OnlyRule("component.value[x]").<OnlyRule>withFile("RemoveString.fsh")
          .withLocation(8, 4, 8, 23);
      rootOnly.types = types(t("Quantity"));

      StructureDefinition sd = exportIt(labAssignedValue, rootOnly);
      ElementDefinition rootValue = sd.findElement("Observation.component.value[x]");
      ElementDefinition labValue = sd.findElement("Observation.component:Lab.value[x]");
      assertEquals(1, rootValue.getType().size());
      assertEquals(1, labValue.getType().size());
      assertMatches("obsolete for choices.*valueString", "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: RemoveString\\.fsh.*Line: 8\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should log an error when a type constraint on the child of a slice implicitly removes a choice")
    void typeConstraintOnChildOfSliceRemovesChoice() {
      // * component[Lab].valueString = "Please leave"
      // * component[Lab].value[x] only Quantity
      AssignmentRule labAssignedValue = new AssignmentRule("component[Lab].valueString");
      labAssignedValue.value = "Please leave";
      OnlyRule labOnly = new OnlyRule("component[Lab].value[x]").<OnlyRule>withFile("RemoveString.fsh")
          .withLocation(9, 4, 9, 23);
      labOnly.types = types(t("Quantity"));

      StructureDefinition sd = exportIt(labAssignedValue, labOnly);
      ElementDefinition labValue = sd.findElement("Observation.component:Lab.value[x]");
      assertEquals(1, labValue.getType().size());
      assertMatches("obsolete for choices.*valueString", "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: RemoveString\\.fsh.*Line: 9\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @Disabled("TS it.todo")
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply an OnlyRule on the child of a sliced element that updates the types on the children of its slices")
    void todoOnlyChildUpdatesSliceChildren() {}

    @Test
    @Disabled("TS it.todo")
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should not apply an OnlyRule on the child of a sliced element that would invalidate any of its slices")
    void todoOnlyChildInvalidatesSlices() {}

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply an ObeysRule on a sliced element and not update the constraints on its slices")
    void obeysOnSlicedElement() {
      // NOTE: This is a change in behavior introduced in SUSHI 3.0

      // * category[Procedure] obeys ShoutingRule
      // * category obeys TalkingRule
      // Invariant: ShoutingRule
      // Description: "No shouting allowed."
      // Invariant: TalkingRule
      // Description: "Talking is prohibited."
      ObeysRule shoutingRule = new ObeysRule("category[Procedure]");
      shoutingRule.invariant = "shout-1";
      ObeysRule talkingRule = new ObeysRule("category");
      talkingRule.invariant = "talk-1";

      Invariant shoutingInvariant = new Invariant("shout-1");
      shoutingInvariant.description = "No shouting allowed.";
      Invariant talkingInvariant = new Invariant("talk-1");
      talkingInvariant.description = "Talking is prohibited.";

      observationWithSlice.rules.add(shoutingRule);
      observationWithSlice.rules.add(talkingRule);
      doc.profiles.put(observationWithSlice.name, observationWithSlice);
      doc.invariants.put(shoutingInvariant.name, shoutingInvariant);
      doc.invariants.put(talkingInvariant.name, talkingInvariant);
      exporter.export();
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition rootCategory = sd.findElement("Observation.category");
      ElementDefinition procedureCategory = sd.findElement("Observation.category:Procedure");
      Object expectedShouting = json("""
          { "key": "shout-1", "human": "No shouting allowed.", "source": "%s" }""".formatted(OWS_URL));
      Object expectedTalking = json("""
          { "key": "talk-1", "human": "Talking is prohibited.", "source": "%s" }""".formatted(OWS_URL));
      assertContainsEqual(at(rootCategory, "constraint"), expectedTalking);
      assertContainsEqual(at(procedureCategory, "constraint"), expectedShouting);
      assertFalse(containsEqual(at(procedureCategory, "constraint"), expectedTalking));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should apply an ObeysRule on the child of a sliced element and not update the child elements on its slices")
    void obeysOnChildOfSlicedElement() {
      // NOTE: This is a change in behavior introduced in SUSHI 3.0

      // * component[Lab].code obeys RunningRule
      // * component.code obeys WalkingRule
      // Invariant: RunningRule
      // Description: "Run as fast as you can!"
      // Invariant: WalkingRule
      // Description: "Walk at your own pace."
      ObeysRule runningRule = new ObeysRule("component[Lab].code");
      runningRule.invariant = "run-1";
      ObeysRule walkingRule = new ObeysRule("component.code");
      walkingRule.invariant = "walk-1";

      Invariant runningInvariant = new Invariant("run-1");
      runningInvariant.description = "Run as fast as you can!";
      Invariant walkingInvariant = new Invariant("walk-1");
      walkingInvariant.description = "Walk at your own pace.";

      observationWithSlice.rules.add(runningRule);
      observationWithSlice.rules.add(walkingRule);
      doc.profiles.put(observationWithSlice.name, observationWithSlice);
      doc.invariants.put(runningInvariant.name, runningInvariant);
      doc.invariants.put(walkingInvariant.name, walkingInvariant);
      exporter.export();
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition rootCode = sd.findElement("Observation.component.code");
      ElementDefinition labCode = sd.findElement("Observation.component:Lab.code");
      Object expectedRunning = json("""
          { "key": "run-1", "human": "Run as fast as you can!", "source": "%s" }""".formatted(OWS_URL));
      Object expectedWalking = json("""
          { "key": "walk-1", "human": "Walk at your own pace.", "source": "%s" }""".formatted(OWS_URL));
      assertContainsEqual(at(rootCode, "constraint"), expectedWalking);
      assertContainsEqual(at(labCode, "constraint"), expectedRunning);
      assertFalse(containsEqual(at(labCode, "constraint"), expectedWalking));
    }

    @Test
    @Disabled("TS it.todo")
    @DisplayName("StructureDefinitionExporter R4 #RulesWithSlices should have some tests involving slices and CaretValueRule")
    void todoSlicesAndCaretValueRule() {}
  }

  @Nested
  @DisplayName("#toJSON")
  class ToJSONTests {
    @Test
    @DisplayName("StructureDefinitionExporter R4 #toJSON should correctly generate a diff containing only changed elements")
    void diffOnlyChanged() {
      // We already have separate tests for the differentials, so this just ensures that the
      // StructureDefinition is setup correctly to produce accurate differential elements.
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      profile.rules.add(card("subject", 1, "1"));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      Object json = sd.toJSON();

      assertEquals(1, len(at(json, "differential.element")));
      assertJsonEqual("""
          [{ "id": "Observation.subject", "path": "Observation.subject", "min": 1 }]""",
          at(json, "differential.element"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #toJSON should correctly generate a diff containing only changed elements when elements are unfolded")
    void diffOnlyChangedUnfolded() {
      // We already have separate tests for the differentials, so this just ensures that the
      // StructureDefinition captures originals at the right time to produce the most correct
      // differentials
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      // Create a few rules that will force complex types to be "unfolded"
      profile.rules.add(card("code.coding", 1, "*"));
      profile.rules.add(card("code.coding.userSelected", 1, "1"));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      Object json = sd.toJSON();

      assertEquals(2, len(at(json, "differential.element")));
      assertJsonEqual("""
          [
            { "id": "Observation.code.coding", "path": "Observation.code.coding", "min": 1 },
            {
              "id": "Observation.code.coding.userSelected",
              "path": "Observation.code.coding.userSelected",
              "min": 1
            }
          ]""", at(json, "differential.element"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #toJSON should correctly generate a diff containing only changed elements when elements are sliced")
    void diffOnlyChangedSliced() {
      // We already have separate tests for the differentials, so this just ensures that the
      // StructureDefinition captures originals at the right time to produce the most correct
      // differentials
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      // Create some rules to slice component
      // * component ^slicing.discriminator[0].type = #pattern
      // * component ^slicing.discriminator[0].path = "code"
      // * component ^slicing.rules = #open
      // * component ^comment = "BP comment"
      // * component contains SystolicBP 0..1 and DiastolicBP 0..1
      // * component[SystolicBP].code = LOINC#8480-6
      // * component[SystolicBP].value[x] only Quantity
      // * component[DiastolicBP].code = LOINC#8462-4
      // * component[DiastolicBP].value[x] only Quantity
      CaretValueRule ruleA = caret("component", "slicing.discriminator[0].type", new FshCode("pattern"));
      CaretValueRule ruleB = caret("component", "slicing.discriminator[0].path", "code");
      CaretValueRule ruleC = caret("component", "slicing.rules", new FshCode("open"));
      CaretValueRule ruleD = caret("component", "comment", "BP comment");
      ContainsRule rule1 = new ContainsRule("component");
      rule1.items = items("SystolicBP", "DiastolicBP");
      CardRule rule2 = new CardRule("component[SystolicBP]");
      rule2.max = "1";
      AssignmentRule rule3 = new AssignmentRule("component[SystolicBP].code");
      rule3.value = new FshCode("8480-6", "http://loinc.org");
      OnlyRule rule4 = new OnlyRule("component[SystolicBP].value[x]");
      rule4.types = types(t("Quantity"));
      CardRule rule5 = new CardRule("component[DiastolicBP]");
      rule5.max = "1";
      AssignmentRule rule6 = new AssignmentRule("component[DiastolicBP].code");
      rule6.value = new FshCode("8462-4", "http://loinc.org");
      OnlyRule rule7 = new OnlyRule("component[DiastolicBP].value[x]");
      rule7.types = types(t("Quantity"));
      profile.rules.addAll(List.<Rule>of(ruleA, ruleB, ruleC, ruleD, rule1, rule2, rule3, rule4, rule5, rule6, rule7));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      Object json = sd.toJSON();

      assertEquals(7, len(at(json, "differential.element")));
      assertJsonEqual("""
          [
            {
              "id": "Observation.component",
              "path": "Observation.component",
              "slicing": {
                "discriminator": [{ "type": "pattern", "path": "code" }],
                "rules": "open"
              },
              "comment": "BP comment"
            },
            {
              "id": "Observation.component:SystolicBP",
              "path": "Observation.component",
              "sliceName": "SystolicBP",
              "min": 0,
              "max": "1"
            },
            {
              "id": "Observation.component:SystolicBP.code",
              "path": "Observation.component.code",
              "patternCodeableConcept": {
                "coding": [{ "code": "8480-6", "system": "http://loinc.org" }]
              }
            },
            {
              "id": "Observation.component:SystolicBP.value[x]",
              "path": "Observation.component.value[x]",
              "type": [{ "code": "Quantity" }]
            },
            {
              "id": "Observation.component:DiastolicBP",
              "path": "Observation.component",
              "sliceName": "DiastolicBP",
              "min": 0,
              "max": "1"
            },
            {
              "id": "Observation.component:DiastolicBP.code",
              "path": "Observation.component.code",
              "patternCodeableConcept": {
                "coding": [{ "code": "8462-4", "system": "http://loinc.org" }]
              }
            },
            {
              "id": "Observation.component:DiastolicBP.value[x]",
              "path": "Observation.component.value[x]",
              "type": [{ "code": "Quantity" }]
            }
          ]""", at(json, "differential.element"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #toJSON should not include inherited extension slices in a child differential when the child adds slicing on another element")
    void noInheritedExtensionSlicesInChildDiff() {
      // Parent profile adds an extension slice on Observation.component
      // Profile: FooParent
      // Parent: Observation
      // * component.extension contains bar 0..1
      Profile parentProfile = new Profile("FooParent");
      parentProfile.parent = "Observation";

      Extension extBar = new Extension("Bar");
      doc.extensions.put("Bar", extBar);

      ContainsRule parentContains = new ContainsRule("component.extension");
      parentContains.items = new ArrayList<>(List.of(new ContainsRuleItem("bar", "Bar")));
      CardRule parentCard = card("component.extension[bar]", 0, "1");
      parentProfile.rules.add(parentContains);
      parentProfile.rules.add(parentCard);
      doc.profiles.put(parentProfile.name, parentProfile);
      exporter.exportStructDef(parentProfile);

      // Child profile inherits the extension slice from parent and adds slicing on component
      // Profile: FooChild
      // Parent: FooParent
      // * component ^slicing.discriminator[0].type = #pattern
      // * component ^slicing.discriminator[0].path = "code"
      // * component ^slicing.rules = #open
      // * component contains SystolicBP 0..1
      // * component[SystolicBP].code = LOINC#8480-6
      Profile childProfile = new Profile("FooChild");
      childProfile.parent = "FooParent";

      CaretValueRule ruleA = caret("component", "slicing.discriminator[0].type", new FshCode("pattern"));
      CaretValueRule ruleB = caret("component", "slicing.discriminator[0].path", "code");
      CaretValueRule ruleC = caret("component", "slicing.rules", new FshCode("open"));
      ContainsRule rule1 = new ContainsRule("component");
      rule1.items = items("SystolicBP");
      CardRule rule2 = new CardRule("component[SystolicBP]");
      rule2.max = "1";
      AssignmentRule rule3 = new AssignmentRule("component[SystolicBP].code");
      rule3.value = new FshCode("8480-6", "http://loinc.org");
      childProfile.rules.addAll(List.<Rule>of(ruleA, ruleB, ruleC, rule1, rule2, rule3));
      doc.profiles.put(childProfile.name, childProfile);
      exporter.exportStructDef(childProfile);

      StructureDefinition sd = pkg.profiles.get(1);
      Object json = sd.toJSON();

      // The child's differential should only contain the component slicing elements,
      // NOT the inherited extension slice from the parent (component:SystolicBP.extension:bar)
      Object extensionSliceInDiff = ((List<?>) at(json, "differential.element")).stream()
          .filter(e -> at(e, "id") instanceof String id && id.contains("extension:bar"))
          .findFirst().orElse(null);
      assertNull(extensionSliceInDiff);

      assertEquals(3, len(at(json, "differential.element")));
      assertJsonEqual("""
          [
            {
              "id": "Observation.component",
              "path": "Observation.component",
              "slicing": {
                "discriminator": [{ "type": "pattern", "path": "code" }],
                "rules": "open"
              }
            },
            {
              "id": "Observation.component:SystolicBP",
              "path": "Observation.component",
              "sliceName": "SystolicBP",
              "min": 0,
              "max": "1"
            },
            {
              "id": "Observation.component:SystolicBP.code",
              "path": "Observation.component.code",
              "patternCodeableConcept": {
                "coding": [{ "code": "8480-6", "system": "http://loinc.org" }]
              }
            }
          ]""", at(json, "differential.element"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #toJSON should include sliceName in a differential when an attribute of the slice is changed")
    void sliceNameInDiff() {
      Profile profile = new Profile("MustSlice");
      profile.parent = "resprate";
      FlagRule mustCode = new FlagRule("code.coding[RespRateCode]");
      mustCode.mustSupport = true;
      profile.rules.add(mustCode);
      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      Object json = sd.toJSON();
      assertEquals(1, len(at(json, "differential.element")));
      assertJsonEqual("""
          [
            {
              "id": "Observation.code.coding:RespRateCode",
              "path": "Observation.code.coding",
              "sliceName": "RespRateCode",
              "mustSupport": true
            }
          ]""", at(json, "differential.element"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #toJSON should include mustSupport in the differential of a new slice, even if the base element is also mustSupport")
    void mustSupportInNewSliceDiff() {
      Profile profile = new Profile("MustSlice");
      profile.parent = "resprate";
      FlagRule mustCode = new FlagRule("code.coding");
      mustCode.mustSupport = true;
      ContainsRule codeSlice = new ContainsRule("code.coding");
      codeSlice.items = items("OxygenCode");
      FlagRule mustSlice = new FlagRule("code.coding[OxygenCode]");
      mustSlice.mustSupport = true;
      profile.rules.addAll(List.<Rule>of(mustCode, codeSlice, mustSlice));
      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      Object json = sd.toJSON();
      assertContainsEqual(at(json, "differential.element"), json("""
          {
            "id": "Observation.code.coding:OxygenCode",
            "path": "Observation.code.coding",
            "sliceName": "OxygenCode",
            "min": 0,
            "max": "*",
            "mustSupport": true
          }"""));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #toJSON should include the children of primitive elements when serializing to JSON")
    void primitiveChildrenInJson() {
      Profile profile = new Profile("SpecialUrlId");
      profile.parent = "Observation";

      profile.rules.add(caret("", "url.id", "my-id"));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      Object json = sd.toJSON();
      assertJsonEqual("""
          { "id": "my-id" }""", at(json, "_url"));
    }

    @Test
    @Disabled("TS it.skip")
    @DisplayName("StructureDefinitionExporter R4 #toJSON should not change sliceName based on a CaretValueRule")
    void sliceNameNotChangedByCaret() {
      Profile profile = new Profile("NameChange");
      profile.parent = "resprate";
      profile.rules.add(caret("code.coding[RespRateCode]", "sliceName", "SomeOtherCode"));
      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      Object json = sd.toJSON();
      assertEquals(1, len(at(json, "differential.element")));
      assertJsonEqual("""
          {
            "id": "Observation.code.coding:RespRateCode",
            "path": "Observation.code.coding",
            "sliceName": "RespRateCode"
          }""", at(json, "differential.element[0]"));
    }
  }

  @Nested
  @DisplayName("#insertRules")
  class InsertRulesTests {
    Profile profile;
    RuleSet ruleSet;

    @BeforeEach
    void setUp() {
      profile = new Profile("Foo");
      profile.parent = "Observation";
      doc.profiles.put(profile.name, profile);

      ruleSet = new RuleSet("Bar");
      doc.ruleSets.put(ruleSet.name, ruleSet);
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #insertRules should apply rules from an insert rule")
    void appliesInsertRule() {
      // RuleSet: Bar
      // * ^title = "Wow fancy"
      //
      // Profile: Foo
      // Parent: Observation
      // * insert Bar
      ruleSet.rules.add(caret("", "title", "Wow fancy"));

      InsertRule insertRule = new InsertRule("");
      insertRule.ruleSet = "Bar";
      profile.rules.add(insertRule);

      exporter.applyInsertRules();
      StructureDefinition exported = exporter.exportStructDef(profile);
      assertEquals("Wow fancy", at(exported, "title"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #insertRules should log an error and not apply rules from an invalid insert rule")
    void invalidInsertRule() {
      // RuleSet: Bar
      // * #lion
      // * ^title = "Wow fancy"
      //
      // Profile: Foo
      // Parent: Observation
      // * insert Bar
      ConceptRule concept = new ConceptRule("bear").<ConceptRule>withFile("Concept.fsh").withLocation(1, 2, 3, 4);
      CaretValueRule nameRule = caret("", "title", "Wow fancy");
      ruleSet.rules.add(concept);
      ruleSet.rules.add(nameRule);

      InsertRule insertRule = new InsertRule("").<InsertRule>withFile("Insert.fsh").withLocation(5, 6, 7, 8);
      insertRule.ruleSet = "Bar";
      profile.rules.add(insertRule);

      exporter.applyInsertRules();
      StructureDefinition exported = exporter.exportStructDef(profile);
      // CaretRule is still applied
      assertEquals("Wow fancy", at(exported, "title"));
      assertMatches(
          "ConceptRule.*Profile.*File: Concept\\.fsh.*Line: 1 - 3.*Applied in File: Insert\\.fsh.*Applied on Line: 5 - 7",
          "s", loggerSpy.getLastMessage("error"));
    }
  }

  /** TS jest.spyOn(fisher, 'fishForMetadata' | 'fishForMetadatas'): a TestFisher that records the calls. */
  static class RecordingFisher extends TestFisher {
    record Call(String item, List<FishableType> types) {}

    final List<Call> metadataCalls = new ArrayList<>();
    final List<Call> metadatasCalls = new ArrayList<>();

    RecordingFisher(FSHTank tank, FHIRDefinitions fhir, Package pkg) {
      super(tank, fhir, pkg);
    }

    @java.lang.Override public Metadata fishForMetadata(String item, FishableType... types) {
      metadataCalls.add(new Call(item, List.of(types)));
      return super.fishForMetadata(item, types);
    }

    @java.lang.Override public List<Metadata> fishForMetadatas(String item, FishableType... types) {
      metadatasCalls.add(new Call(item, List.of(types)));
      return super.fishForMetadatas(item, types);
    }
  }

  @Nested
  @DisplayName("#fishForMetadata")
  class FishForMetadataTests {
    RecordingFisher fisherSpy;

    @BeforeEach
    void setUp() {
      fisherSpy = new RecordingFisher(tank, defs, pkg);
      fisher = fisherSpy;
      exporter = new StructureDefinitionExporter(tank, pkg, fisher);
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #fishForMetadata should use the passed in fisher to fish metadata for instances")
    void usesFisher() {
      assertNull(exporter.fishForMetadata("some-item", FishableType.Profile, FishableType.Extension));
      assertTrue(fisherSpy.metadataCalls.contains(
          new RecordingFisher.Call("some-item", List.of(FishableType.Profile, FishableType.Extension))));
    }
  }

  @Nested
  @DisplayName("#fishForMetadatas")
  class FishForMetadatasTests {
    RecordingFisher fisherSpy;

    @BeforeEach
    void setUp() {
      fisherSpy = new RecordingFisher(tank, defs, pkg);
      fisher = fisherSpy;
      exporter = new StructureDefinitionExporter(tank, pkg, fisher);
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #fishForMetadatas should use the passed in fisher to fish metadatas for instances")
    void usesFisher() {
      assertTrue(exporter.fishForMetadatas("some-item", FishableType.Profile, FishableType.Extension).isEmpty());
      assertTrue(fisherSpy.metadatasCalls.contains(
          new RecordingFisher.Call("some-item", List.of(FishableType.Profile, FishableType.Extension))));
    }
  }
}
