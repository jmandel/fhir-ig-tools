package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.WithDebugLogging.withDebugLogging;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinitionType;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Extension;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.Logical;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.rules.AddElementRule;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.CardRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.sushi.fshtypes.rules.FlagRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/export/StructureDefinitionExporter.test.ts (SUSHI 3.21.1), describe blocks
// 'StructureDefinitionExporter R4' > '#OnlyRule' (lines 3879-5651)
@DisplayName("StructureDefinitionExporter R4")
class StructureDefinitionExporterR4CTest extends SdeTestBase {

  private static final String TYPE_CHARACTERISTICS =
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics";
  private static final String LOGICAL_TARGET = "http://hl7.org/fhir/tools/StructureDefinition/logical-target";
  private static final String FHIR_SD = "http://hl7.org/fhir/StructureDefinition/";
  private static final String MINIMAL_SD = "http://hl7.org/fhir/us/minimal/StructureDefinition/";

  private static List<OnlyRuleType> types(OnlyRuleType... t) {
    return new ArrayList<>(Arrays.asList(t));
  }

  private static List<Rule> rules(Rule... r) {
    return new ArrayList<>(Arrays.asList(r));
  }

  private static OnlyRuleType t(String type) {
    return new OnlyRuleType(type);
  }

  private static OnlyRuleType ref(String type) {
    return OnlyRuleType.reference(type);
  }

  private static OnlyRuleType can(String type) {
    return OnlyRuleType.canonical(type);
  }

  private static CaretValueRule caret(String path, String caretPath, Object value) {
    CaretValueRule rule = new CaretValueRule(path);
    rule.caretPath = caretPath;
    rule.value = value;
    return rule;
  }

  /** TS {@code ed.type.find(t => t.code === code)}. */
  private static ElementDefinitionType findType(ElementDefinition ed, String code) {
    return ed.getType().stream().filter(t -> code.equals(t.getCode())).findFirst().orElse(null);
  }

  private static StructureDefinition findByName(List<StructureDefinition> sds, String name) {
    return sds.stream().filter(d -> name.equals(at(d, "name"))).findFirst().orElse(null);
  }

  private static StructureDefinition findById(List<StructureDefinition> sds, String id) {
    return sds.stream().filter(d -> id.equals(at(d, "id"))).findFirst().orElse(null);
  }

  /** TS {@code loggerSpy.getAllMessages().forEach(m => expect(m).not.toMatch(/circular/i))}. */
  private static void assertNoCircularMessages() {
    Pattern circular = Pattern.compile("circular", Pattern.CASE_INSENSITIVE);
    for (String m : loggerSpy.getAllMessages()) {
      assertFalse(circular.matcher(m).find(), m);
    }
  }

  /** Base Observation.value[x] checks repeated in several tests. */
  private static void assertBaseObservationValue(ElementDefinition baseValue) {
    assertEquals(11, baseValue.getType().size());
    assertJsEqual(new ElementDefinitionType("Quantity"), baseValue.getType().get(0));
    assertJsEqual(new ElementDefinitionType("CodeableConcept"), baseValue.getType().get(1));
    assertJsEqual(new ElementDefinitionType("string"), baseValue.getType().get(2));
  }

  /** Base Observation.hasMember checks repeated in several tests. */
  private static void assertBaseHasMember(ElementDefinition baseHasMember) {
    assertEquals(1, baseHasMember.getType().size());
    assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(FHIR_SD + "Observation",
        FHIR_SD + "QuestionnaireResponse", FHIR_SD + "MolecularSequence"), baseHasMember.getType().get(0));
  }

  /** Base PlanDefinition.action.definition[x] checks repeated in several tests. */
  private static void assertBaseActionDef(ElementDefinition baseActionDef) {
    assertEquals(2, baseActionDef.getType().size());
    assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles(FHIR_SD + "ActivityDefinition",
        FHIR_SD + "PlanDefinition", FHIR_SD + "Questionnaire"), baseActionDef.getType().get(0));
    assertJsEqual(new ElementDefinitionType("uri"), baseActionDef.getType().get(1));
  }

  /** Base Extension.value[x] checks repeated in several tests. */
  private static void assertBaseExtensionValueX(ElementDefinition baseValueX, String code) {
    assertEquals(50, baseValueX.getType().size());
    assertJsEqual(new ElementDefinitionType(code), findType(baseValueX, code));
  }

  @Nested
  @DisplayName("#OnlyRule")
  class OnlyRuleTests {

    /**
     * Shared tail of the FutureLogic tests: exports Extension MyExtension with
     * {@code * value[x] only Reference(FutureLogic or Observation)} and checks the result.
     */
    private void exportFutureLogicExtensionAndCheck() {
      // Extension: MyExtension
      // * value[x] only Reference(FutureLogic or Observation)
      Extension extension = new Extension("MyExtension");
      OnlyRule rule = new OnlyRule("value[x]");
      rule.types = types(ref("FutureLogic"), ref("Observation"));
      extension.rules.add(rule);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Extension");

      ElementDefinition baseValueX = baseStructDef.findElement("Extension.value[x]");
      ElementDefinition constrainedValueX = sd.findElement("Extension.value[x]");

      assertBaseExtensionValueX(baseValueX, "Reference");

      assertEquals(1, constrainedValueX.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(MINIMAL_SD + "FutureLogic",
          FHIR_SD + "Observation"), constrainedValueX.getType().get(0));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a non-reference choice")
    void nonReferenceChoice() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      OnlyRule rule = new OnlyRule("value[x]");
      rule.types = types(t("string"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseValue = baseStructDef.findElement("Observation.value[x]");
      ElementDefinition constrainedValue = sd.findElement("Observation.value[x]");

      assertBaseObservationValue(baseValue);

      assertEquals(1, constrainedValue.getType().size());
      assertJsEqual(new ElementDefinitionType("string"), constrainedValue.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a reference")
    void reference() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      OnlyRule rule = new OnlyRule("subject");
      rule.types = types(ref("Device"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseSubject = baseStructDef.findElement("Observation.subject");
      ElementDefinition constrainedSubject = sd.findElement("Observation.subject");

      assertEquals(1, baseSubject.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(FHIR_SD + "Patient", FHIR_SD + "Group",
          FHIR_SD + "Device", FHIR_SD + "Location"), baseSubject.getType().get(0));

      assertEquals(1, constrainedSubject.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(FHIR_SD + "Device"),
          constrainedSubject.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a reference to Any")
    void referenceToAny() {
      Extension extension = new Extension("Foo");

      OnlyRule rule = new OnlyRule("value[x]");
      rule.types = types(ref("Observation"), ref("Condition"));
      extension.rules.add(rule);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Extension");

      ElementDefinition baseValueX = baseStructDef.findElement("Extension.value[x]");
      ElementDefinition constrainedValueX = sd.findElement("Extension.value[x]");

      assertBaseExtensionValueX(baseValueX, "Reference");

      assertEquals(1, constrainedValueX.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(FHIR_SD + "Observation",
          FHIR_SD + "Condition"), constrainedValueX.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a canonical")
    void canonical() {
      Profile profile = new Profile("Foo");
      profile.parent = "PlanDefinition";

      OnlyRule rule = new OnlyRule("action.definition[x]");
      rule.types = types(can("Questionnaire"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("PlanDefinition");

      ElementDefinition baseActionDef = baseStructDef.findElement("PlanDefinition.action.definition[x]");
      ElementDefinition constrainedActionDef = sd.findElement("PlanDefinition.action.definition[x]");

      assertBaseActionDef(baseActionDef);

      assertEquals(1, constrainedActionDef.getType().size());
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles(FHIR_SD + "Questionnaire"),
          constrainedActionDef.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule with a version on a canonical")
    void versionOnCanonical() {
      Profile profile = new Profile("Foo");
      profile.parent = "PlanDefinition";

      OnlyRule rule = new OnlyRule("action.definition[x]");
      rule.types = types(can("Questionnaire|4.0.1"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("PlanDefinition");

      ElementDefinition baseActionDef = baseStructDef.findElement("PlanDefinition.action.definition[x]");
      ElementDefinition constrainedActionDef = sd.findElement("PlanDefinition.action.definition[x]");

      assertBaseActionDef(baseActionDef);

      assertEquals(1, constrainedActionDef.getType().size());
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles(FHIR_SD + "Questionnaire|4.0.1"),
          constrainedActionDef.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a canonical to Any")
    void canonicalToAny() {
      Extension extension = new Extension("Foo");

      OnlyRule rule = new OnlyRule("value[x]");
      rule.types = types(can("Observation"), can("Condition"));
      extension.rules.add(rule);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Extension");

      ElementDefinition baseValueX = baseStructDef.findElement("Extension.value[x]");
      ElementDefinition constrainedValueX = sd.findElement("Extension.value[x]");

      assertBaseExtensionValueX(baseValueX, "canonical");

      assertEquals(1, constrainedValueX.getType().size());
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles(FHIR_SD + "Observation",
          FHIR_SD + "Condition"), constrainedValueX.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule with a specific reference target constrained")
    void specificReferenceTarget() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      OnlyRule rule = new OnlyRule("hasMember[Observation]");
      rule.types = types(ref("http://hl7.org/fhir/StructureDefinition/bodyheight"),
          ref("http://hl7.org/fhir/StructureDefinition/bodyweight"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseHasMember = baseStructDef.findElement("Observation.hasMember");
      ElementDefinition constrainedHasMember = sd.findElement("Observation.hasMember");

      assertBaseHasMember(baseHasMember);

      assertEquals(1, constrainedHasMember.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(FHIR_SD + "bodyheight",
          FHIR_SD + "bodyweight", FHIR_SD + "QuestionnaireResponse", FHIR_SD + "MolecularSequence"),
          constrainedHasMember.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule with a specific canonical target constrained")
    void specificCanonicalTarget() {
      Profile profile = new Profile("Foo");
      profile.parent = "PlanDefinition";

      OnlyRule rule = new OnlyRule("action.definitionCanonical[PlanDefinition]");
      rule.types = types(can("http://hl7.org/fhir/StructureDefinition/shareableplandefinition"),
          can("http://hl7.org/fhir/StructureDefinition/computableplandefinition"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("PlanDefinition");

      ElementDefinition baseActionDef = baseStructDef.findElement("PlanDefinition.action.definition[x]");
      ElementDefinition constrainedActionDefCan =
          sd.findElement("PlanDefinition.action.definition[x]:definitionCanonical");

      assertBaseActionDef(baseActionDef);

      assertEquals(1, constrainedActionDefCan.getType().size());
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles(FHIR_SD + "ActivityDefinition",
          FHIR_SD + "shareableplandefinition", FHIR_SD + "computableplandefinition", FHIR_SD + "Questionnaire"),
          constrainedActionDefCan.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a non-reference FSHy choice")
    void nonReferenceFshyChoice() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      Profile targetProfile = new Profile("MySpecialQuantity");
      targetProfile.parent = "Quantity";
      doc.profiles.put(targetProfile.name, targetProfile);

      OnlyRule rule = new OnlyRule("value[x]");
      rule.types = types(t("MySpecialQuantity"));
      profile.rules.add(rule);

      // force-load Quantity into the cache since MySpecialQuantity declares it as parent
      fisher.fishForStructureDefinition("Quantity");

      exporter.exportStructDef(profile);
      StructureDefinition sd = findByName(pkg.profiles, "Foo");
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseValue = baseStructDef.findElement("Observation.value[x]");
      ElementDefinition constrainedValue = sd.findElement("Observation.value[x]");

      assertBaseObservationValue(baseValue);

      assertEquals(1, constrainedValue.getType().size());
      assertJsEqual(new ElementDefinitionType("Quantity").withProfiles(MINIMAL_SD + "MySpecialQuantity"),
          constrainedValue.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a FSHy reference")
    void fshyReference() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      Profile targetProfile = new Profile("MySpecialDevice");
      targetProfile.parent = "Device";
      doc.profiles.put(targetProfile.name, targetProfile);

      OnlyRule rule = new OnlyRule("subject");
      rule.types = types(ref("MySpecialDevice"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = findByName(pkg.profiles, "Foo");
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseSubject = baseStructDef.findElement("Observation.subject");
      ElementDefinition constrainedSubject = sd.findElement("Observation.subject");

      assertEquals(1, baseSubject.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(FHIR_SD + "Patient", FHIR_SD + "Group",
          FHIR_SD + "Device", FHIR_SD + "Location"), baseSubject.getType().get(0));

      assertEquals(1, constrainedSubject.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(MINIMAL_SD + "MySpecialDevice"),
          constrainedSubject.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a FSHy canonical")
    void fshyCanonical() {
      Profile profile = new Profile("Foo");
      profile.parent = "PlanDefinition";

      Profile targetProfile = new Profile("MySpecialPlanDefinition");
      targetProfile.parent = "PlanDefinition";
      doc.profiles.put(targetProfile.name, targetProfile);

      OnlyRule rule = new OnlyRule("action.definitionCanonical");
      rule.types = types(can("MySpecialPlanDefinition"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = findByName(pkg.profiles, "Foo");
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("PlanDefinition");

      ElementDefinition baseActionDef = baseStructDef.findElement("PlanDefinition.action.definition[x]");
      ElementDefinition constrainedActionDefCan =
          sd.findElement("PlanDefinition.action.definition[x]:definitionCanonical");

      assertBaseActionDef(baseActionDef);

      assertEquals(1, constrainedActionDefCan.getType().size());
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles(MINIMAL_SD + "MySpecialPlanDefinition"),
          constrainedActionDefCan.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule with a specific target constrained to FSHy definition")
    void specificTargetFshyDefinition() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      Profile targetProfile1 = new Profile("MySpecialObservation1");
      targetProfile1.parent = "Observation";
      Profile targetProfile2 = new Profile("MySpecialObservation2");
      targetProfile2.parent = "Observation";
      doc.profiles.put(targetProfile1.name, targetProfile1);
      doc.profiles.put(targetProfile2.name, targetProfile2);

      OnlyRule rule = new OnlyRule("hasMember[Observation]");
      rule.types = types(ref("MySpecialObservation1"), ref("MySpecialObservation2"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = findByName(pkg.profiles, "Foo");
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseHasMember = baseStructDef.findElement("Observation.hasMember");
      ElementDefinition constrainedHasMember = sd.findElement("Observation.hasMember");

      assertBaseHasMember(baseHasMember);

      assertEquals(1, constrainedHasMember.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(MINIMAL_SD + "MySpecialObservation1",
          MINIMAL_SD + "MySpecialObservation2", FHIR_SD + "QuestionnaireResponse", FHIR_SD + "MolecularSequence"),
          constrainedHasMember.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule with a specific canonical target constrained to FSHy definition")
    void specificCanonicalTargetFshyDefinition() {
      Profile profile = new Profile("Foo");
      profile.parent = "PlanDefinition";

      Profile targetProfile1 = new Profile("MySpecialPlanDefinition1");
      targetProfile1.parent = "PlanDefinition";
      Profile targetProfile2 = new Profile("MySpecialPlanDefinition2");
      targetProfile2.parent = "PlanDefinition";
      doc.profiles.put(targetProfile1.name, targetProfile1);
      doc.profiles.put(targetProfile2.name, targetProfile2);

      OnlyRule rule = new OnlyRule("action.definitionCanonical[PlanDefinition]");
      rule.types = types(can("MySpecialPlanDefinition1"), can("MySpecialPlanDefinition2"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = findByName(pkg.profiles, "Foo");
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("PlanDefinition");

      ElementDefinition baseActionDef = baseStructDef.findElement("PlanDefinition.action.definition[x]");
      ElementDefinition constrainedActionDefCan =
          sd.findElement("PlanDefinition.action.definition[x]:definitionCanonical");

      assertBaseActionDef(baseActionDef);

      assertEquals(1, constrainedActionDefCan.getType().size());
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles(FHIR_SD + "ActivityDefinition",
          MINIMAL_SD + "MySpecialPlanDefinition1", MINIMAL_SD + "MySpecialPlanDefinition2", FHIR_SD + "Questionnaire"),
          constrainedActionDefCan.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply correct OnlyRules on circular FSHy reference choices")
    void circularFshyReferenceChoices() {
      Profile profile1 = new Profile("Foo");
      profile1.parent = "Observation";
      doc.profiles.put(profile1.name, profile1);
      Profile profile2 = new Profile("Bar");
      profile2.parent = "Observation";
      doc.profiles.put(profile2.name, profile2);

      OnlyRule rule1 = new OnlyRule("hasMember[Observation]");
      rule1.types = types(ref("Bar"));
      OnlyRule rule2 = new OnlyRule("hasMember[Observation]");
      rule2.types = types(ref("Foo"));
      profile1.rules.add(rule1);
      profile2.rules.add(rule2);

      withDebugLogging(() -> exporter.export());
      assertNoCircularMessages();

      StructureDefinition sdFoo = findById(pkg.profiles, "Foo");
      StructureDefinition sdBar = findById(pkg.profiles, "Bar");
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseHasMember = baseStructDef.findElement("Observation.hasMember");
      ElementDefinition constrainedHasMemberFoo = sdFoo.findElement("Observation.hasMember");
      ElementDefinition constrainedHasMemberBar = sdBar.findElement("Observation.hasMember");

      assertBaseHasMember(baseHasMember);

      assertEquals(1, constrainedHasMemberFoo.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(MINIMAL_SD + "Bar",
          FHIR_SD + "QuestionnaireResponse", FHIR_SD + "MolecularSequence"), constrainedHasMemberFoo.getType().get(0));

      assertEquals(1, constrainedHasMemberBar.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(MINIMAL_SD + "Foo",
          FHIR_SD + "QuestionnaireResponse", FHIR_SD + "MolecularSequence"), constrainedHasMemberBar.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply correct OnlyRules on circular FSHy canonical choices")
    void circularFshyCanonicalChoices() {
      Profile profile1 = new Profile("Foo");
      profile1.parent = "PlanDefinition";
      doc.profiles.put(profile1.name, profile1);
      Profile profile2 = new Profile("Bar");
      profile2.parent = "PlanDefinition";
      doc.profiles.put(profile2.name, profile2);

      OnlyRule rule1 = new OnlyRule("action.definitionCanonical[PlanDefinition]");
      rule1.types = types(can("Bar"));
      OnlyRule rule2 = new OnlyRule("action.definitionCanonical[PlanDefinition]");
      rule2.types = types(can("Foo"));
      profile1.rules.add(rule1);
      profile2.rules.add(rule2);

      withDebugLogging(() -> exporter.export());
      assertNoCircularMessages();

      StructureDefinition sdFoo = findById(pkg.profiles, "Foo");
      StructureDefinition sdBar = findById(pkg.profiles, "Bar");
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("PlanDefinition");

      ElementDefinition baseActionDef = baseStructDef.findElement("PlanDefinition.action.definition[x]");
      ElementDefinition constrainedActionDefCan =
          sdFoo.findElement("PlanDefinition.action.definition[x]:definitionCanonical");
      ElementDefinition constrainedHasMemberBar =
          sdBar.findElement("PlanDefinition.action.definition[x]:definitionCanonical");

      assertBaseActionDef(baseActionDef);

      assertEquals(1, constrainedActionDefCan.getType().size());
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles(FHIR_SD + "ActivityDefinition",
          MINIMAL_SD + "Bar", FHIR_SD + "Questionnaire"), constrainedActionDefCan.getType().get(0));

      assertEquals(1, constrainedHasMemberBar.getType().size());
      assertJsEqual(new ElementDefinitionType("canonical").withTargetProfiles(FHIR_SD + "ActivityDefinition",
          MINIMAL_SD + "Foo", FHIR_SD + "Questionnaire"), constrainedHasMemberBar.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should safely apply correct OnlyRule with circular FSHy parent")
    void circularFshyParent() {
      Profile profile1 = new Profile("Foo");
      profile1.parent = "Observation";
      doc.profiles.put(profile1.name, profile1);
      Profile profile2 = new Profile("Bar");
      profile2.parent = "Foo";
      doc.profiles.put(profile2.name, profile2);

      OnlyRule rule = new OnlyRule("hasMember[Observation]");
      rule.types = types(ref("Bar"));
      profile1.rules.add(rule);

      withDebugLogging(() -> exporter.export());
      assertNoCircularMessages();

      StructureDefinition sdFoo = findById(pkg.profiles, "Foo");
      StructureDefinition sdBar = findById(pkg.profiles, "Bar");
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      assertEquals("http://hl7.org/fhir/StructureDefinition/Observation", at(sdFoo, "baseDefinition"));
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Foo", at(sdBar, "baseDefinition"));

      ElementDefinition baseHasMember = baseStructDef.findElement("Observation.hasMember");
      ElementDefinition constrainedHasMemberFoo = sdFoo.findElement("Observation.hasMember");
      ElementDefinition constrainedHasMemberBar = sdBar.findElement("Observation.hasMember");

      assertBaseHasMember(baseHasMember);

      assertEquals(1, constrainedHasMemberFoo.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(MINIMAL_SD + "Bar",
          FHIR_SD + "QuestionnaireResponse", FHIR_SD + "MolecularSequence"), constrainedHasMemberFoo.getType().get(0));

      assertEquals(1, constrainedHasMemberBar.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(MINIMAL_SD + "Bar",
          FHIR_SD + "QuestionnaireResponse", FHIR_SD + "MolecularSequence"), constrainedHasMemberBar.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a reference to a logical type defined as a reference target with the type characteristics extension")
    void logicalTargetTypeCharacteristicsExtension() {
      // Logical: FutureLogic
      // * ^extension.url = "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics"
      // * ^extension.valueCode = #has-size
      // * ^extension[1].url = "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics",
      // * ^extension[1].valueCode = #can-be-target
      Logical logical = new Logical("FutureLogic");
      CaretValueRule hasSizeUrl = caret("", "extension.url", TYPE_CHARACTERISTICS);
      CaretValueRule hasSizeCode = caret("", "extension.valueCode", new FshCode("has-size"));
      CaretValueRule canBeTargetUrl = caret("", "extension[1].url", TYPE_CHARACTERISTICS);
      CaretValueRule canBeTargetCode = caret("", "extension[1].valueCode", new FshCode("can-be-target"));
      logical.rules.addAll(List.of(hasSizeUrl, hasSizeCode, canBeTargetUrl, canBeTargetCode));
      doc.logicals.put(logical.name, logical);

      exportFutureLogicExtensionAndCheck();
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a reference to a logical type defined as a reference target with the type characteristics extension defined using extension path syntax with url")
    void logicalTargetTypeCharacteristicsPathWithUrl() {
      // Logical: FutureLogic
      // * ^extension[http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics].valueCode = #has-size
      // * ^extension[http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics].valueCode = #can-be-target
      Logical logical = new Logical("FutureLogic");
      CaretValueRule hasSizeCode =
          caret("", "extension[" + TYPE_CHARACTERISTICS + "].valueCode", new FshCode("has-size"));
      CaretValueRule canBeTargetCode =
          caret("", "extension[" + TYPE_CHARACTERISTICS + "].valueCode", new FshCode("can-be-target"));
      logical.rules.addAll(List.of(hasSizeCode, canBeTargetCode));
      doc.logicals.put(logical.name, logical);

      exportFutureLogicExtensionAndCheck();
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a reference to a logical type defined as a reference target with the type characteristics extension defined using extension path syntax with alias")
    void logicalTargetTypeCharacteristicsPathWithAlias() {
      // Alias: $TYPECHAR = http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics
      doc.aliases.put("$TYPECHAR", TYPE_CHARACTERISTICS);

      // Logical: FutureLogic
      // * ^extension[$TYPECHAR].valueCode = #can-be-target
      // * ^extension[$TYPECHAR][1].valueCode = #has-size
      Logical logical = new Logical("FutureLogic");
      CaretValueRule canBeTargetCode = caret("", "extension[$TYPECHAR].valueCode", new FshCode("can-be-target"));
      CaretValueRule hasSizeCode = caret("", "extension[$TYPECHAR][1].valueCode", new FshCode("has-size"));
      logical.rules.addAll(List.of(canBeTargetCode, hasSizeCode));
      doc.logicals.put(logical.name, logical);

      exportFutureLogicExtensionAndCheck();
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a reference to a logical type defined as a reference target with the type characteristics extension defined using extension path syntax with id")
    void logicalTargetTypeCharacteristicsPathWithId() {
      // Logical: FutureLogic
      // * ^extension[structuredefinition-type-characteristics].valueCode = #has-size
      // * ^extension[structuredefinition-type-characteristics].valueCode = #can-be-target
      Logical logical = new Logical("FutureLogic");
      CaretValueRule hasSizeCode = caret("", "extension[structuredefinition-type-characteristics].valueCode",
          new FshCode("has-size"));
      CaretValueRule canBeTargetCode = caret("", "extension[structuredefinition-type-characteristics].valueCode",
          new FshCode("can-be-target"));
      logical.rules.addAll(List.of(hasSizeCode, canBeTargetCode));
      doc.logicals.put(logical.name, logical);

      exportFutureLogicExtensionAndCheck();
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a reference to a logical type defined as a reference target with the type characteristics extension defined using extension path syntax with name")
    void logicalTargetTypeCharacteristicsPathWithName() {
      // Logical: FutureLogic
      // * ^extension[SDTypeCharacteristics].valueCode = #has-size
      // * ^extension[SDTypeCharacteristics].valueCode = #can-be-target
      Logical logical = new Logical("FutureLogic");
      CaretValueRule hasSizeCode = caret("", "extension[SDTypeCharacteristics].valueCode", new FshCode("has-size"));
      CaretValueRule canBeTargetCode =
          caret("", "extension[SDTypeCharacteristics].valueCode", new FshCode("can-be-target"));
      logical.rules.addAll(List.of(hasSizeCode, canBeTargetCode));
      doc.logicals.put(logical.name, logical);

      exportFutureLogicExtensionAndCheck();
    }

    // TS reuses the title of the "... extension path syntax with url" test above for this logical-target variant
    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a reference to a logical type defined as a reference target with the type characteristics extension defined using extension path syntax with url")
    void logicalTargetLogicalTargetPathWithUrl() {
      // Logical: FutureLogic
      // * ^extension[http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics].valueCode = #has-size
      // * ^extension[http://hl7.org/fhir/tools/StructureDefinition/logical-target].valueBoolean = true
      Logical logical = new Logical("FutureLogic");
      CaretValueRule hasSizeCode =
          caret("", "extension[" + TYPE_CHARACTERISTICS + "].valueCode", new FshCode("has-size"));
      CaretValueRule logicalTargetValue = caret("", "extension[" + LOGICAL_TARGET + "].valueBoolean", true);
      logical.rules.addAll(List.of(hasSizeCode, logicalTargetValue));
      doc.logicals.put(logical.name, logical);

      exportFutureLogicExtensionAndCheck();
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a reference to a logical type defined as a reference target with the logical target extension")
    void logicalTargetLogicalTargetExtension() {
      // Logical: FutureLogic
      // * ^extension.url = "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics"
      // * ^extension.valueCode = #has-size
      // * ^extension[1].url = "http://hl7.org/fhir/tools/StructureDefinition/logical-target",
      // * ^extension[1].valueBoolean = true
      Logical logical = new Logical("FutureLogic");
      CaretValueRule hasSizeUrl = caret("", "extension.url", TYPE_CHARACTERISTICS);
      CaretValueRule hasSizeCode = caret("", "extension.valueCode", new FshCode("has-size"));
      CaretValueRule logicalTargetUrl = caret("", "extension[1].url", LOGICAL_TARGET);
      CaretValueRule logicalTargetValue = caret("", "extension[1].valueBoolean", true);
      logical.rules.addAll(List.of(hasSizeUrl, hasSizeCode, logicalTargetUrl, logicalTargetValue));
      doc.logicals.put(logical.name, logical);

      exportFutureLogicExtensionAndCheck();
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a reference to a FSHy logical type defined with the can-be-target characteristic")
    void fshyLogicalCanBeTargetCharacteristic() {
      // Logical: FutureLogic
      // Characteristics: #has-size, #can-be-target
      Logical logical = new Logical("FutureLogic");
      logical.characteristics = new ArrayList<>(List.of("has-size", "can-be-target"));
      doc.logicals.put(logical.name, logical);

      exportFutureLogicExtensionAndCheck();
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a reference to a FSHy logical type defined with the type characteristics extension")
    void fshyLogicalTypeCharacteristicsExtension() {
      // Logical: FutureLogic
      // * ^extension[0].url = "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics"
      // * ^extension[0].valueCode = #can-be-target
      Logical logical = new Logical("FutureLogic");
      CaretValueRule typeCharacteristicsUrl = caret("", "extension[0].url", TYPE_CHARACTERISTICS);
      CaretValueRule typeCharacteristicsValue = caret("", "extension[0].valueCode", new FshCode("can-be-target"));
      logical.rules.addAll(List.of(typeCharacteristicsUrl, typeCharacteristicsValue));
      doc.logicals.put(logical.name, logical);

      exportFutureLogicExtensionAndCheck();
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a reference to a FSHy logical type defined with the logical target extension")
    void fshyLogicalLogicalTargetExtension() {
      // Logical: FutureLogic
      // * ^extension[0].url = "http://hl7.org/fhir/tools/StructureDefinition/logical-target"
      // * ^extension[0].valueBoolean = true
      Logical logical = new Logical("FutureLogic");
      CaretValueRule logicalTargetUrl = caret("", "extension[0].url", LOGICAL_TARGET);
      CaretValueRule logicalTargetValue = caret("", "extension[0].valueBoolean", true);
      logical.rules.addAll(List.of(logicalTargetUrl, logicalTargetValue));
      doc.logicals.put(logical.name, logical);

      exportFutureLogicExtensionAndCheck();
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a self-referential FSHy logical type with the can-be-target characteristic")
    void selfReferentialFshyLogicalCanBeTarget() {
      // Logical: FutureLogic
      // Characteristics: #has-size, #can-be-target
      // * alternate 0..* Reference(FutureLogic) "Reference to alternate future"
      Logical logical = new Logical("FutureLogic");
      logical.characteristics = new ArrayList<>(List.of("has-size", "can-be-target"));
      AddElementRule alternateElement = new AddElementRule("alternate");
      alternateElement.min = 0.0;
      alternateElement.max = "*";
      alternateElement.types = types(ref("FutureLogic"));
      alternateElement.short_ = "Reference to alternate future";
      logical.rules.add(alternateElement);
      doc.logicals.put(logical.name, logical);

      exporter.exportStructDef(logical);
      StructureDefinition sd = pkg.logicals.get(0);
      ElementDefinition alternateDefinition = sd.findElement("FutureLogic.alternate");
      assertEquals(1, alternateDefinition.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(MINIMAL_SD + "FutureLogic"),
          alternateDefinition.getType().get(0));

      assertEquals(0, loggerSpy.getAllMessages("warn").size());
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a self-referential FSHy logical type with the type characteristics extension")
    void selfReferentialFshyLogicalTypeCharacteristics() {
      // Logical: FutureLogic
      // * alternate 0..* Reference(FutureLogic) "Reference to alternate future"
      // * ^extension[0].url = "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics"
      // * ^extension[0].valueCode = #can-be-target
      Logical logical = new Logical("FutureLogic");
      AddElementRule alternateElement = new AddElementRule("alternate");
      alternateElement.min = 0.0;
      alternateElement.max = "*";
      alternateElement.types = types(ref("FutureLogic"));
      alternateElement.short_ = "Reference to alternate future";
      CaretValueRule typeCharacteristicsUrl = caret("", "extension[0].url", TYPE_CHARACTERISTICS);
      CaretValueRule typeCharacteristicsValue = caret("", "extension[0].valueCode", new FshCode("can-be-target"));
      logical.rules.addAll(List.of(alternateElement, typeCharacteristicsUrl, typeCharacteristicsValue));
      doc.logicals.put(logical.name, logical);

      exporter.exportStructDef(logical);
      StructureDefinition sd = pkg.logicals.get(0);
      ElementDefinition alternateDefinition = sd.findElement("FutureLogic.alternate");
      assertEquals(1, alternateDefinition.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(MINIMAL_SD + "FutureLogic"),
          alternateDefinition.getType().get(0));

      assertEquals(0, loggerSpy.getAllMessages("warn").size());
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply an OnlyRule on a reference to a FSHy logical type and log a warning if it is not specified as a reference target")
    void fshyLogicalNotReferenceTargetWarns() {
      // Logical: FutureLogic
      // Characteristics: #has-size, #has-length
      Logical logical = new Logical("FutureLogic");
      logical.characteristics = new ArrayList<>(List.of("has-size", "has-length"));
      doc.logicals.put(logical.name, logical);

      // Extension: MyExtension
      // * value[x] only Reference(FutureLogic or Observation)
      Extension extension = new Extension("MyExtension");
      OnlyRule rule = new OnlyRule("value[x]").<OnlyRule>withFile("Extension.fsh").withLocation(5, 3, 5, 28);
      rule.types = types(ref("FutureLogic"), ref("Observation"));
      extension.rules.add(rule);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Extension");

      ElementDefinition baseValueX = baseStructDef.findElement("Extension.value[x]");
      ElementDefinition constrainedValueX = sd.findElement("Extension.value[x]");

      assertBaseExtensionValueX(baseValueX, "Reference");

      assertEquals(1, constrainedValueX.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(MINIMAL_SD + "FutureLogic",
          FHIR_SD + "Observation"), constrainedValueX.getType().get(0));
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("Referenced type FutureLogic does not specify that it can be the target of a reference\\.", "s",
          loggerSpy.getLastMessage("warn"));
      assertMatches("File: Extension\\.fsh.*Line: 5", "s", loggerSpy.getLastMessage("warn"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a reference to a defined logical type defined with the logical target extension")
    void definedLogicalLogicalTargetExtension() {
      // Extension: MyExtension
      // * value[x] only Reference(FuturePlanet or Location)
      Extension extension = new Extension("MyExtension");
      OnlyRule rule = new OnlyRule("value[x]");
      rule.types = types(ref("FuturePlanet"), ref("Location"));
      extension.rules.add(rule);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition constrainedValueX = sd.findElement("Extension.value[x]");
      assertEquals(1, constrainedValueX.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(
          "http://hl7.org/planet/logicals/StructureDefinition/FuturePlanet", FHIR_SD + "Location"),
          constrainedValueX.getType().get(0));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply a correct OnlyRule on a reference to a defined logical type and log a warning if it is defined without the logical target extension")
    void definedLogicalWithoutLogicalTargetWarns() {
      // Extension: MyExtension
      // * value[x] only Reference(PastPlanet or Location)
      Extension extension = new Extension("MyExtension");
      OnlyRule rule = new OnlyRule("value[x]").<OnlyRule>withFile("Extension.fsh").withLocation(4, 3, 4, 26);
      rule.types = types(ref("PastPlanet"), ref("Location"));
      extension.rules.add(rule);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition constrainedValueX = sd.findElement("Extension.value[x]");
      assertEquals(1, constrainedValueX.getType().size());
      assertJsEqual(new ElementDefinitionType("Reference").withTargetProfiles(
          "http://hl7.org/planet/logicals/StructureDefinition/PastPlanet", FHIR_SD + "Location"),
          constrainedValueX.getType().get(0));
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("Referenced type PastPlanet does not specify that it can be the target of a reference\\.", "s",
          loggerSpy.getLastMessage("warn"));
      assertMatches("File: Extension\\.fsh.*Line: 4", "s", loggerSpy.getLastMessage("warn"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should log a debug message when we detect a circular dependency in OnlyRules that might result in incomplete definitions")
    void circularDependencyDebugMessage() {
      Profile profile1 = new Profile("FooQuantity");
      profile1.parent = "Quantity";
      ContainsRule p1ContainsRule = new ContainsRule("extension");
      p1ContainsRule.items.add(new ContainsRuleItem("quantity-ext", "QuantityExtension"));
      OnlyRule p1OnlyRule = new OnlyRule("extension[quantity-ext].valueQuantity");
      p1OnlyRule.types = types(t("BarQuantity"));
      AssignmentRule p1AssignedValueRule = new AssignmentRule("extension[quantity-ext].valueQuantity.code");
      p1AssignedValueRule.value = new FshCode("mg");
      profile1.rules = rules(p1ContainsRule, p1OnlyRule, p1AssignedValueRule);
      doc.profiles.put(profile1.name, profile1);

      Profile profile2 = new Profile("BarQuantity");
      profile2.parent = "Quantity";
      ContainsRule p2ContainsRule = new ContainsRule("extension");
      p2ContainsRule.items.add(new ContainsRuleItem("quantity-ext", "QuantityExtension"));
      OnlyRule p2OnlyRule = new OnlyRule("extension[quantity-ext].valueQuantity");
      p2OnlyRule.types = types(t("FooQuantity"));
      AssignmentRule p2AssignedValueRule = new AssignmentRule("extension[quantity-ext].valueQuantity.code");
      p2AssignedValueRule.value = new FshCode("mg");
      profile2.rules = rules(p2ContainsRule, p2OnlyRule, p2AssignedValueRule);
      doc.profiles.put(profile2.name, profile2);

      Extension extension = new Extension("QuantityExtension");
      OnlyRule extOnlyRule = new OnlyRule("value[x]");
      extOnlyRule.types = types(t("Quantity"));
      extension.rules = rules(extOnlyRule);
      doc.extensions.put(extension.name, extension);

      withDebugLogging(() -> exporter.export());

      assertMatches("Warning: Circular .* BarQuantity and FooQuantity", loggerSpy.getLastMessage("debug"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should log a warning message when we detect a circular dependency that causes an incomplete parent")
    void circularDependencyIncompleteParentWarning() {
      Profile profile1 = new Profile("FooQuantity").<Profile>withFile("FooQuantity.fsh").withLocation(6, 7, 11, 33);
      profile1.parent = "BarQuantity";
      doc.profiles.put(profile1.name, profile1);

      Profile profile2 = new Profile("BarQuantity");
      profile2.parent = "Quantity";
      ContainsRule p2ContainsRule = new ContainsRule("extension");
      p2ContainsRule.items.add(new ContainsRuleItem("quantity-ext", "QuantityExtension"));
      OnlyRule p2OnlyRule = new OnlyRule("extension[quantity-ext].valueQuantity");
      p2OnlyRule.types = types(t("FooQuantity"));
      AssignmentRule p2AssignedValueRule = new AssignmentRule("extension[quantity-ext].valueQuantity.code");
      p2AssignedValueRule.value = new FshCode("mg");
      profile2.rules = rules(p2ContainsRule, p2OnlyRule, p2AssignedValueRule);
      doc.profiles.put(profile2.name, profile2);

      Extension extension = new Extension("QuantityExtension");
      OnlyRule extOnlyRule = new OnlyRule("value[x]");
      extOnlyRule.types = types(t("Quantity"));
      extension.rules = rules(extOnlyRule);
      doc.extensions.put(extension.name, extension);

      exporter.export();

      String lastMessage = loggerSpy.getLastMessage("warn");
      assertMatches("The definition of FooQuantity may be incomplete .* BarQuantity", lastMessage);
      assertMatches("File: FooQuantity\\.fsh.*Line: 6 - 11\\D*", "s", lastMessage);
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply an OnlyRule to constrain an id element")
    void constrainIdElement() {
      Profile specialString = new Profile("SpecialString");
      specialString.parent = "string";
      doc.profiles.put(specialString.name, specialString);
      Profile profile = new Profile("MyObservation");
      profile.parent = "Observation";
      OnlyRule onlyRule = new OnlyRule("code.id");
      onlyRule.types = types(t("SpecialString"));
      profile.rules.add(onlyRule);
      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition codeId = sd.findElement("Observation.code.id");
      assertEquals(1, codeId.getType().size());
      assertEquals("http://hl7.org/fhirpath/System.String", codeId.getType().get(0).getActualCode());
      assertEquals(1, codeId.getType().get(0).getProfile().size());
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/SpecialString",
          codeId.getType().get(0).getProfile().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should apply an OnlyRule to constrain a url element")
    void constrainUrlElement() {
      Profile specialUri = new Profile("SpecialUri");
      specialUri.parent = "uri";
      doc.profiles.put(specialUri.name, specialUri);
      Extension extension = new Extension("SpecialExtension");
      OnlyRule onlyRule = new OnlyRule("url");
      onlyRule.types = types(t("SpecialUri"));
      extension.rules.add(onlyRule);
      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      ElementDefinition url = sd.findElement("Extension.url");
      assertEquals(1, url.getType().size());
      assertEquals("http://hl7.org/fhirpath/System.String", url.getType().get(0).getActualCode());
      assertEquals(1, url.getType().get(0).getProfile().size());
      assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/SpecialUri",
          url.getType().get(0).getProfile().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should not apply an incorrect OnlyRule")
    void incorrectOnlyRule() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      OnlyRule rule = new OnlyRule("value[x]").<OnlyRule>withFile("Only.fsh").withLocation(10, 12, 10, 22);
      rule.types = types(t("instant"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");

      ElementDefinition baseValue = baseStructDef.findElement("Observation.value[x]");
      ElementDefinition constrainedValue = sd.findElement("Observation.value[x]");
      assertEquals(11, baseValue.getType().size());
      assertEquals(11, constrainedValue.getType().size());
      assertMatches("File: Only\\.fsh.*Line: 10\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should log an error when a type constraint implicitly removes a choice created in the current StructureDefinition")
    void implicitlyRemovesChoiceCreatedHere() {
      Profile flagFirst = new Profile("FlagFirst");
      flagFirst.parent = "Observation";
      FlagRule flagRule = new FlagRule("valueCodeableConcept").<FlagRule>withFile("FlagFirst.fsh")
          .withLocation(7, 12, 7, 31);
      flagRule.mustSupport = true;
      FlagRule secondFlagRule = new FlagRule("valueString").<FlagRule>withFile("FlagFirst.fsh")
          .withLocation(8, 12, 8, 31);
      secondFlagRule.mustSupport = true;
      OnlyRule onlyRule = new OnlyRule("value[x]").<OnlyRule>withFile("FlagFirst.fsh").withLocation(9, 12, 9, 24);
      onlyRule.types = types(t("Quantity"), t("string"));

      flagFirst.rules.add(flagRule); // * valueCodeableConcept MS
      flagFirst.rules.add(secondFlagRule); // * valueString MS
      flagFirst.rules.add(onlyRule); // value[x] only Quantity or String

      exporter.exportStructDef(flagFirst);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition constrainedValue = sd.findElement("Observation.value[x]");
      assertEquals(2, constrainedValue.getType().size());
      assertMatches("File: FlagFirst\\.fsh.*Line: 9\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should not log an error when a type constraint implicitly removes a choice that has no rules applied in the current StructureDefinition")
    void implicitlyRemovesChoiceWithoutRulesHere() {
      loggerSpy.reset();
      Profile parentProfile = new Profile("ParentProfile");
      parentProfile.parent = "Observation";
      FlagRule flagRule = new FlagRule("valueCodeableConcept");
      flagRule.mustSupport = true;
      parentProfile.rules.add(flagRule); // * valueCodeableConcept MS

      Profile childProfile = new Profile("ChildProfile");
      childProfile.parent = "ParentProfile";
      OnlyRule onlyRule = new OnlyRule("value[x]");
      onlyRule.types = types(t("Quantity"));
      childProfile.rules.add(onlyRule); // * value[x] only Quantity
      exporter.exportStructDef(parentProfile);
      exporter.exportStructDef(childProfile);
      assertEquals(2, pkg.profiles.size());
      assertEquals(0, loggerSpy.getAllLogs().size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should not log an error when a type constraint is applied to a specific slice")
    void typeConstraintOnSpecificSlice() {
      loggerSpy.reset();
      Profile profile = new Profile("ConstrainedObservation");
      profile.parent = "Observation";
      // * component ^slicing.discriminator[0].type = #pattern
      // * component ^slicing.discriminator[0].path = "code"
      // * component ^slicing.rules = #open
      // * component contains FirstSlice and SecondSlice
      // * component[FirstSlice].value[x] only Quantity
      // * component[FirstSlice].valueQuantity 1..1
      // * component[SecondSlice].value[x] only string
      CaretValueRule slicingType = caret("component", "slicing.discriminator[0].type", new FshCode("pattern"));
      CaretValueRule slicingPath = caret("component", "slicing.discriminator[0].path", "code");
      CaretValueRule slicingRules = caret("component", "slicing.rules", new FshCode("open"));
      ContainsRule componentSlices = new ContainsRule("component");
      componentSlices.items = new ArrayList<>(List.of(new ContainsRuleItem("FirstSlice"),
          new ContainsRuleItem("SecondSlice")));
      OnlyRule firstType = new OnlyRule("component[FirstSlice].value[x]");
      firstType.types = types(t("Quantity"));
      CardRule firstCard = new CardRule("component[FirstSlice].valueQuantity");
      firstCard.min = 1.0;
      firstCard.max = "1";
      OnlyRule secondType = new OnlyRule("component[SecondSlice].value[x]");
      secondType.types = types(t("string"));

      profile.rules.addAll(List.of(slicingType, slicingPath, slicingRules, componentSlices, firstType, firstCard,
          secondType));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      assertNotNull(sd);
      assertEquals(0, loggerSpy.getAllLogs().size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should not log an error when a type constraint is applied to a slice with a name that is the prefix of another slice")
    void typeConstraintOnPrefixSlice() {
      loggerSpy.reset();
      Profile profile = new Profile("ConstrainedObservation");
      profile.parent = "Observation";
      // * component ^slicing.discriminator[0].type = #pattern
      // * component ^slicing.discriminator[0].path = "code"
      // * component ^slicing.rules = #open
      // * component contains GoodSlice and GoodSliceAgain
      // * component[GoodSliceAgain].value[x] only Quantity
      // * component[GoodSliceAgain].valueQuantity 1..1
      // * component[GoodSlice].value[x] only string
      CaretValueRule slicingType = caret("component", "slicing.discriminator[0].type", new FshCode("pattern"));
      CaretValueRule slicingPath = caret("component", "slicing.discriminator[0].path", "code");
      CaretValueRule slicingRules = caret("component", "slicing.rules", new FshCode("open"));
      ContainsRule componentSlices = new ContainsRule("component");
      componentSlices.items = new ArrayList<>(List.of(new ContainsRuleItem("GoodSlice"),
          new ContainsRuleItem("GoodSliceAgain")));
      OnlyRule againType = new OnlyRule("component[GoodSliceAgain].value[x]");
      againType.types = types(t("Quantity"));
      CardRule againCard = new CardRule("component[GoodSliceAgain].valueQuantity");
      againCard.min = 1.0;
      againCard.max = "1";
      OnlyRule goodType = new OnlyRule("component[GoodSlice].value[x]");
      goodType.types = types(t("string"));

      profile.rules.addAll(List.of(slicingType, slicingPath, slicingRules, componentSlices, againType, againCard,
          goodType));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      assertNotNull(sd);
      assertEquals(0, loggerSpy.getAllLogs().size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should not log an error when a rule path has a subpath of a sliced choice element")
    void subpathOfSlicedChoiceElement() {
      loggerSpy.reset();
      Profile profile = new Profile("ConstrainedObservation");
      profile.parent = "Observation";
      // * value[x] ^slicing.discriminator[0].type = #type
      // * value[x] ^slicing.discriminator[0].path = "$this"
      // * value[x] ^slicing.rules = #open
      // * value[x] contains valueString 0..1  and valueOther 0..1
      // * value[x][valueString] only string
      // * value[x][valueOther] only Quantity or CodeableConcept
      // * value[x][valueOther].extension 1..1
      CaretValueRule slicingType = caret("value[x]", "slicing.discriminator[0].type", new FshCode("type"));
      CaretValueRule slicingPath = caret("value[x]", "slicing.discriminator[0].path", "$this");
      CaretValueRule slicingRules = caret("value[x]", "slicing.rules", new FshCode("open"));
      ContainsRule valueXSlices = new ContainsRule("value[x]");
      valueXSlices.items = new ArrayList<>(List.of(new ContainsRuleItem("valueString"),
          new ContainsRuleItem("valueOther")));
      CardRule stringCard = new CardRule("value[x][valueString]");
      stringCard.min = 0.0;
      stringCard.max = "1";
      CardRule otherCard = new CardRule("value[x][valueOther]");
      otherCard.min = 0.0;
      otherCard.max = "1";
      OnlyRule stringType = new OnlyRule("value[x][valueString]");
      stringType.types = types(t("string"));
      OnlyRule otherType = new OnlyRule("value[x][valueOther]");
      otherType.types = types(t("Quantity"), t("CodeableConcept"));
      CardRule otherExtensionCard = new CardRule("value[x][valueOther].extension");
      otherExtensionCard.min = 1.0;
      otherExtensionCard.max = "1";

      profile.rules.addAll(List.of(slicingType, slicingPath, slicingRules, valueXSlices, stringCard, otherCard,
          stringType, otherType, otherExtensionCard));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      assertNotNull(sd);
      assertEquals(0, loggerSpy.getAllLogs().size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should log an error when extension is constrained with a modifier extension")
    void extensionConstrainedWithModifierExtension() {
      Extension extension = new Extension("StrangeExtension");
      FlagRule modifier = new FlagRule(".");
      modifier.modifier = true;
      extension.rules.add(modifier);
      doc.extensions.put(extension.name, extension);

      Profile profile = new Profile("ConstrainedObservation");
      profile.parent = "Observation";
      OnlyRule onlyRule = new OnlyRule("category.extension").<OnlyRule>withFile("WrongModifier.fsh")
          .withLocation(8, 3, 8, 55);
      onlyRule.types = types(t("StrangeExtension"));
      profile.rules.add(onlyRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition categoryExtension = sd.findElement("Observation.category.extension");
      assertEquals(1, categoryExtension.getType().size());

      assertJsEqual(new ElementDefinitionType("Extension").withProfiles(MINIMAL_SD + "StrangeExtension"),
          categoryExtension.getType().get(0));
      assertMatches("Modifier extension StrangeExtension used to constrain extension element\\. Modifier extensions "
          + "should only be used with modifierExtension elements\\..*File: WrongModifier\\.fsh.*Line: 8\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should log an error each time a modifier extension is used to constrain an extension element")
    void eachModifierExtensionOnExtensionLogs() {
      Extension strangeExtension = new Extension("StrangeExtension");
      FlagRule modifier = new FlagRule(".");
      modifier.modifier = true;
      strangeExtension.rules.add(modifier);
      doc.extensions.put(strangeExtension.name, strangeExtension);

      Extension oddExtension = new Extension("OddExtension");
      oddExtension.rules.add(modifier);
      doc.extensions.put(oddExtension.name, oddExtension);

      Extension regularExtension = new Extension("RegularExtension");
      doc.extensions.put(regularExtension.name, regularExtension);

      Profile profile = new Profile("ConstrainedObservation");
      profile.parent = "Observation";
      OnlyRule onlyRule = new OnlyRule("category.extension").<OnlyRule>withFile("WrongModifier.fsh")
          .withLocation(8, 3, 8, 55);
      onlyRule.types = types(t("StrangeExtension"), t("RegularExtension"), t("OddExtension"));
      profile.rules.add(onlyRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition categoryExtension = sd.findElement("Observation.category.extension");
      assertEquals(1, categoryExtension.getType().size());

      assertJsEqual(new ElementDefinitionType("Extension").withProfiles(MINIMAL_SD + "StrangeExtension",
          MINIMAL_SD + "RegularExtension", MINIMAL_SD + "OddExtension"), categoryExtension.getType().get(0));
      assertEquals(2, loggerSpy.getAllMessages("error").size());
      assertMatches("Modifier extension StrangeExtension used to constrain extension element\\. Modifier extensions "
          + "should only be used with modifierExtension elements\\..*File: WrongModifier\\.fsh.*Line: 8\\D*", "s",
          loggerSpy.getMessageAtIndex(0, "error"));
      assertMatches("Modifier extension OddExtension used to constrain extension element\\. Modifier extensions "
          + "should only be used with modifierExtension elements\\..*File: WrongModifier\\.fsh.*Line: 8\\D*", "s",
          loggerSpy.getMessageAtIndex(1, "error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should not log an error when extension is constrained with a non-modifier extension")
    void extensionConstrainedWithNonModifierExtension() {
      Extension extension = new Extension("RegularExtension");
      doc.extensions.put(extension.name, extension);

      Profile profile = new Profile("ConstrainedObservation");
      profile.parent = "Observation";
      OnlyRule onlyRule = new OnlyRule("category.extension");
      onlyRule.types = types(t("RegularExtension"));
      profile.rules.add(onlyRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition categoryExtension = sd.findElement("Observation.category.extension");
      assertEquals(1, categoryExtension.getType().size());
      assertJsEqual(new ElementDefinitionType("Extension").withProfiles(MINIMAL_SD + "RegularExtension"),
          categoryExtension.getType().get(0));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should log an error when modifierExtension is constrained with a non-modifier extension")
    void modifierExtensionConstrainedWithNonModifierExtension() {
      Extension extension = new Extension("OrdinaryExtension");
      doc.extensions.put(extension.name, extension);

      Profile profile = new Profile("ConstrainedObservation");
      profile.parent = "Observation";
      OnlyRule onlyRule = new OnlyRule("modifierExtension").<OnlyRule>withFile("WrongModifier.fsh")
          .withLocation(8, 3, 8, 55);
      onlyRule.types = types(t("OrdinaryExtension"));
      profile.rules.add(onlyRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition categoryExtension = sd.findElement("Observation.modifierExtension");
      assertEquals(1, categoryExtension.getType().size());

      assertJsEqual(new ElementDefinitionType("Extension").withProfiles(MINIMAL_SD + "OrdinaryExtension"),
          categoryExtension.getType().get(0));
      assertMatches("Non-modifier extension OrdinaryExtension used to constrain modifierExtension element\\. "
          + "Non-modifier extensions should only be used with extension elements\\..*File: WrongModifier\\.fsh"
          + ".*Line: 8\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #OnlyRule should not log an error when modifierExtension is constrained with a modifier extension")
    void modifierExtensionConstrainedWithModifierExtension() {
      Extension extension = new Extension("UsefulExtension");
      FlagRule modifier = new FlagRule(".");
      modifier.modifier = true;
      extension.rules.add(modifier);
      doc.extensions.put(extension.name, extension);

      Profile profile = new Profile("ConstrainedObservation");
      profile.parent = "Observation";
      OnlyRule onlyRule = new OnlyRule("modifierExtension");
      onlyRule.types = types(t("UsefulExtension"));
      profile.rules.add(onlyRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition categoryExtension = sd.findElement("Observation.modifierExtension");
      assertEquals(1, categoryExtension.getType().size());

      assertJsEqual(new ElementDefinitionType("Extension").withProfiles(MINIMAL_SD + "UsefulExtension"),
          categoryExtension.getType().get(0));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }
  }
}
