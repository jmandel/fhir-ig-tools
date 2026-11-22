package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertContainsText;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsonEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirtypes.Common;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinitionType;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Extension;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.rules.CardRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.sushi.fshtypes.rules.FlagRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.common.js.Js;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/export/StructureDefinitionExporter.test.ts (SUSHI 3.21.1), describe blocks
// 'StructureDefinitionExporter R4' > '#ContainsRule' (lines 7123-8660)
@DisplayName("StructureDefinitionExporter R4")
class StructureDefinitionExporterR4ETest extends SdeTestBase {

  private static final String FHIR_SD = "http://hl7.org/fhir/StructureDefinition/";

  private static List<ContainsRuleItem> items(ContainsRuleItem... i) {
    return new ArrayList<>(Arrays.asList(i));
  }

  private static ContainsRuleItem item(String name) {
    return new ContainsRuleItem(name);
  }

  private static ContainsRuleItem item(String name, String type) {
    return new ContainsRuleItem(name, type);
  }

  private static List<OnlyRuleType> types(OnlyRuleType... t) {
    return new ArrayList<>(Arrays.asList(t));
  }

  private static ContainsRule contains(String path, ContainsRuleItem... i) {
    ContainsRule rule = new ContainsRule(path);
    rule.items = items(i);
    return rule;
  }

  private static CaretValueRule caret(String path, String caretPath, Object value) {
    CaretValueRule rule = new CaretValueRule(path);
    rule.caretPath = caretPath;
    rule.value = value;
    return rule;
  }

  private static CardRule card(String path, double min, String max) {
    CardRule rule = new CardRule(path);
    rule.min = min;
    rule.max = max;
    return rule;
  }

  /** TS {@code sd.elements.find(e => e.id === id)}. */
  private static ElementDefinition el(StructureDefinition sd, String id) {
    return sd.elements.stream().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
  }

  /**
   * TS: {@code expect(ed.slicing).toBeDefined(); expect(ed.slicing.discriminator.length).toBe(1);
   * expect(ed.slicing.discriminator[0]).toEqual({ type: 'value', path: 'url' })}.
   */
  private static void assertUrlSlicing(ElementDefinition ed) {
    assertNotNull(at(ed, "slicing"));
    assertEquals(1, ((List<?>) at(ed, "slicing.discriminator")).size());
    assertJsonEqual("""
        { "type": "value", "path": "url" }""", at(ed, "slicing.discriminator[0]"));
  }

  /** TS {@code expect(diff.min).toBe(0); expect(diff.max).toBe('*')}. */
  private static void assertDiffZeroToStar(ElementDefinition ed) {
    ElementDefinition diff = ed.calculateDiff();
    assertJsEqual(0, at(diff, "min"));
    assertEquals("*", at(diff, "max"));
  }

  /** TS {@code loggerSpy.getAllMessages(level).some(m => /regex/.test(m))}. */
  private static boolean anyMessageMatches(String level, String regex) {
    Pattern p = Pattern.compile(regex);
    return loggerSpy.getAllMessages(level).stream().anyMatch(m -> p.matcher(m).find());
  }

  @Nested
  @DisplayName("#ContainsRule")
  class ContainsRuleTests {

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should apply a ContainsRule on an element with defined slicing")
    void elementWithDefinedSlicing() {
      Profile profile = new Profile("Foo");
      profile.parent = "resprate";

      ContainsRule rule = contains("code.coding", item("barSlice"));
      CaretValueRule rulesRule = caret("code.coding", "slicing.rules", new FshCode("open"));
      CaretValueRule typeRule = caret("code.coding", "slicing.discriminator.type", new FshCode("pattern"));
      CaretValueRule pathRule = caret("code.coding", "slicing.discriminator.path", "foo");
      profile.rules.addAll(List.of(rule, rulesRule, typeRule, pathRule));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("resprate");

      ElementDefinition barSlice = el(sd, "Observation.code.coding:barSlice");

      assertEquals(baseStructDef.elements.size() + 1, sd.elements.size());
      assertNotNull(barSlice);
      assertDiffZeroToStar(barSlice);
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should apply a ContainsRule on a slice")
    void onASlice() {
      // Profile: Foo
      // Parent: resprate
      // * code.coding contains fooSlice
      // * code.coding[fooSlice] contains barReslice
      Profile profile = new Profile("Foo");
      profile.parent = "resprate";

      ContainsRule sliceRule = contains("code.coding", item("fooSlice"));
      ContainsRule resliceRule = contains("code.coding[fooSlice]", item("barReslice"));
      profile.rules.addAll(List.of(sliceRule, resliceRule));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("resprate");

      ElementDefinition barReslice = el(sd, "Observation.code.coding:fooSlice/barReslice");

      assertEquals(baseStructDef.elements.size() + 2, sd.elements.size());
      assertNotNull(barReslice);
      assertDiffZeroToStar(barReslice);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should apply a ContainsRule on an extension slice")
    void onAnExtensionSlice() {
      // Extension: Foo
      // * extension contains bar
      // * extension[bar] contains barReslice
      Extension extension = new Extension("Foo");

      ContainsRule sliceRule = contains("extension", item("bar"));
      ContainsRule resliceRule = contains("extension[bar]", item("barReslice"));
      extension.rules.addAll(List.of(sliceRule, resliceRule));

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Extension");

      ElementDefinition barReslice = el(sd, "Extension.extension:bar/barReslice");

      assertEquals(baseStructDef.elements.size() + 6, sd.elements.size());
      assertNotNull(barReslice);
      assertDiffZeroToStar(barReslice);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should log a warning when an element has both a slice name and slicing")
    void sliceNameAndSlicingWarning() {
      // Profile: Foo
      // Parent: resprate
      // * code.coding contains fooSlice
      // * code.coding[fooSlice] ^slicing.discriminator.type = #pattern
      // * code.coding[fooSlice] ^slicing.discriminator.path = "system"
      // * code.coding[fooSlice] ^slicing.rules = #open
      // * code.coding[fooSlice] contains barReslice
      Profile profile = new Profile("Foo");
      profile.parent = "resprate";

      ContainsRule sliceRule = contains("code.coding", item("fooSlice"));
      CaretValueRule caretRule = caret("code.coding[fooSlice]", "slicing.discriminator.type", new FshCode("pattern"));
      CaretValueRule pathRule = caret("code.coding[fooSlice]", "slicing.discriminator.path", "system");
      CaretValueRule rulesRule = caret("code.coding[fooSlice]", "slicing.rules", new FshCode("open"));
      ContainsRule resliceRule = contains("code.coding[fooSlice]", item("barReslice"));
      profile.rules.addAll(List.of(sliceRule, caretRule, pathRule, rulesRule, resliceRule));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("resprate");

      ElementDefinition barReslice = el(sd, "Observation.code.coding:fooSlice/barReslice");

      assertEquals(baseStructDef.elements.size() + 2, sd.elements.size());
      assertNotNull(barReslice);
      assertDiffZeroToStar(barReslice);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      // putting slice info on a slice is a warning-level event
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertContainsText(
          "Observation.code.coding[fooSlice]: An element with a slice name should not define its own slicing.",
          loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should apply a ContainsRule of a defined extension on an extension element")
    void definedExtensionOnExtension() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      ContainsRule rule = contains("extension", item("vs", "valueset-expression"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition extension = el(sd, "Observation.extension");
      ElementDefinition valuesetExpression = el(sd, "Observation.extension:vs");

      assertUrlSlicing(extension);
      assertNotNull(valuesetExpression);
      assertJsEqual(new ElementDefinitionType("Extension").withProfiles(FHIR_SD + "valueset-expression"),
          valuesetExpression.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should apply a ContainsRule of a defined extension on a modifierExtension element")
    void definedExtensionOnModifierExtension() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      ContainsRule rule = contains("modifierExtension", item("vs", "valueset-expression"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition extension = el(sd, "Observation.modifierExtension");
      ElementDefinition valuesetExpression = el(sd, "Observation.modifierExtension:vs");

      assertUrlSlicing(extension);
      assertNotNull(valuesetExpression);
      assertJsEqual(new ElementDefinitionType("Extension").withProfiles(FHIR_SD + "valueset-expression"),
          valuesetExpression.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should apply a ContainsRule of an aliased extension on an extension element")
    void aliasedExtension() {
      Profile profile = new Profile("Foo");
      Extension extBar = new Extension("Bar");
      Extension extBaz = new Extension("Baz");
      extBaz.setId("BazId");
      profile.parent = "Observation";

      doc.aliases.put("barAlias", "Bar");
      doc.aliases.put("bazAlias", "BazId");
      doc.extensions.put("Bar", extBar);
      doc.extensions.put("Baz", extBaz);

      ContainsRule ruleBar = contains("extension", item("bar", "barAlias"));
      profile.rules.add(ruleBar);
      ContainsRule ruleBaz = contains("extension", item("baz", "bazAlias"));
      profile.rules.add(ruleBaz);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition extension = el(sd, "Observation.extension");
      ElementDefinition bar = el(sd, "Observation.extension:bar");
      ElementDefinition baz = el(sd, "Observation.extension:baz");

      assertUrlSlicing(extension);
      assertNotNull(bar);
      assertJsEqual(new ElementDefinitionType("Extension").withProfiles(
          "http://hl7.org/fhir/us/minimal/StructureDefinition/Bar"), bar.getType().get(0));
      assertNotNull(baz);
      assertJsEqual(new ElementDefinitionType("Extension").withProfiles(
          "http://hl7.org/fhir/us/minimal/StructureDefinition/BazId"), baz.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should apply a ContainsRule of an existing aliased extension on an extension element")
    void existingAliasedExtension() {
      Profile profile = new Profile("Foo");
      Extension ext = new Extension("VSExpression");
      profile.parent = "Observation";

      doc.aliases.put("VSAlias", FHIR_SD + "valueset-expression");
      doc.extensions.put("VSExpression", ext);

      ContainsRule ruleBar = contains("extension", item("vs", "VSAlias"));
      profile.rules.add(ruleBar);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition extension = el(sd, "Observation.extension");
      ElementDefinition vsExpression = el(sd, "Observation.extension:vs");

      assertUrlSlicing(extension);
      assertNotNull(vsExpression);
      assertJsEqual(new ElementDefinitionType("Extension").withProfiles(FHIR_SD + "valueset-expression"),
          vsExpression.getType().get(0));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should apply a ContainsRule of an inline extension to an extension element")
    void inlineExtension() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      ContainsRule containsRule = contains("extension", item("my-inline-extension"));
      profile.rules.add(containsRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition extension = el(sd, "Observation.extension");
      ElementDefinition extensionSlice = el(sd, "Observation.extension:my-inline-extension");
      ElementDefinition extensionSliceUrl = el(sd, "Observation.extension:my-inline-extension.url");

      assertUrlSlicing(extension);
      assertNotNull(extensionSlice);
      assertNotNull(extensionSliceUrl);
      assertEquals("my-inline-extension", at(extensionSliceUrl, "fixedUri"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should apply a ContainsRule of an inline extension with a name that resolves to a non-extension type")
    void inlineExtensionNameResolvesToNonExtension() {
      // This tests the use case in https://github.com/FHIR/sushi/issues/83, which we originally thought
      // was an issue w/ reserved words, but was actually an issue because "code" resolves to the StructureDefinition
      // for the code type.  This test initially failed and was assigned by changing the code that handles extension
      // slices to only look for Extension resolutions (as opposed to all types).
      // NOTE: This test is mainly irrelevant now that we switched to a syntax that distinguishes slice name from type.
      Extension extension = new Extension("Foo");

      ContainsRule containsRule = contains("extension", item("code"));
      extension.rules.add(containsRule);

      OnlyRule onlyRule = new OnlyRule("extension[code].value[x]");
      onlyRule.types = types(new OnlyRuleType("Quantity"));
      extension.rules.add(onlyRule);

      exporter.exportStructDef(extension);
      StructureDefinition sd = pkg.extensions.get(0);

      assertEquals(0, loggerSpy.getAllLogs("error").size());

      ElementDefinition innerExtension = el(sd, "Extension.extension");
      ElementDefinition extensionSlice = el(sd, "Extension.extension:code");
      ElementDefinition extensionSliceUrl = el(sd, "Extension.extension:code.url");
      ElementDefinition extensionSliceValueX = el(sd, "Extension.extension:code.value[x]");

      assertUrlSlicing(innerExtension);
      assertNotNull(extensionSlice);
      assertNotNull(extensionSliceUrl);
      assertEquals("code", at(extensionSliceUrl, "fixedUri"));
      assertNotNull(extensionSliceValueX);
      assertJsEqual(List.of(new ElementDefinitionType("Quantity")), extensionSliceValueX.getType());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should apply a ContainsRule of an extension with a versioned URL")
    void versionedUrl() {
      Profile profile = new Profile("MyFamilyHistory");
      profile.parent = "FamilyMemberHistory";
      ContainsRule containsRule = contains("extension",
          item("history", FHIR_SD + "familymemberhistory-type|4.0.1"));
      profile.rules.add(containsRule);
      doc.profiles.put(profile.name, profile);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition extension = el(sd, "FamilyMemberHistory.extension");
      ElementDefinition extensionSlice = el(sd, "FamilyMemberHistory.extension:history");
      assertUrlSlicing(extension);
      assertNotNull(extensionSlice);
      assertJsEqual(List.of(new ElementDefinitionType("Extension").withProfiles(
          FHIR_SD + "familymemberhistory-type|4.0.1")), extensionSlice.getType());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should apply a ContainsRule of an extension with a versioned URL and log a warning if the version does not match")
    void versionedUrlMismatchWarning() {
      Profile profile = new Profile("MyFamilyHistory");
      profile.parent = "FamilyMemberHistory";
      ContainsRule containsRule = new ContainsRule("extension").<ContainsRule>withFile("FSHyFile.fsh")
          .withLocation(3, 0, 3, 45);
      containsRule.items = items(item("history", FHIR_SD + "familymemberhistory-type|1.2.3"));
      profile.rules.add(containsRule);
      doc.profiles.put(profile.name, profile);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition extension = el(sd, "FamilyMemberHistory.extension");
      ElementDefinition extensionSlice = el(sd, "FamilyMemberHistory.extension:history");
      assertUrlSlicing(extension);
      assertNotNull(extensionSlice);
      assertJsEqual(List.of(new ElementDefinitionType("Extension").withProfiles(
          FHIR_SD + "familymemberhistory-type|1.2.3")), extensionSlice.getType());
      assertMatches(
          "http://hl7\\.org/fhir/StructureDefinition/familymemberhistory-type\\|1\\.2\\.3 was requested, "
              + "but SUSHI found "
              + "http://hl7\\.org/fhir/StructureDefinition/familymemberhistory-type\\|4\\.0\\.1.*File: FSHyFile\\.fsh"
              + ".*Line: 3\\D*",
          "s", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should apply a ContainsRule of an extension with an overridden URL")
    void overriddenUrl() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      doc.profiles.put("Foo", profile);

      Extension extBar = new Extension("Bar");
      CaretValueRule caretValueRule = caret("", "url", "http://different-url.com/StructureDefinition/Bar");
      extBar.rules.add(caretValueRule);
      doc.extensions.put("Bar", extBar);

      ContainsRule containsRule = contains("extension", item("bar", "Bar"));
      profile.rules.add(containsRule);

      OnlyRule onlyRule = new OnlyRule("extension[bar].value[x]");
      onlyRule.types = types(new OnlyRuleType("Quantity"));
      profile.rules.add(onlyRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      assertEquals(0, loggerSpy.getAllLogs("error").size());

      ElementDefinition extension = el(sd, "Observation.extension");
      ElementDefinition extensionSlice = el(sd, "Observation.extension:bar");
      ElementDefinition extensionSliceUrl = el(sd, "Observation.extension:bar.url");
      ElementDefinition extensionSliceValueX = el(sd, "Observation.extension:bar.value[x]");

      assertUrlSlicing(extension);
      assertNotNull(extensionSlice);
      assertNotNull(extensionSliceUrl);
      assertEquals("http://different-url.com/StructureDefinition/Bar", at(extensionSliceUrl, "fixedUri"));
      assertNotNull(extensionSliceValueX);
      assertJsEqual(List.of(new ElementDefinitionType("Quantity")), extensionSliceValueX.getType());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should apply a ContainsRule of an extension with an overridden URL by URL")
    void overriddenUrlByUrl() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      doc.profiles.put("Foo", profile);

      Extension extBar = new Extension("Bar");
      CaretValueRule caretValueRule = caret("", "url", "http://different-url.com/StructureDefinition/Bar");
      extBar.rules.add(caretValueRule);
      doc.extensions.put("Bar", extBar);

      ContainsRule containsRule = contains("extension", item("bar", "Bar"));
      profile.rules.add(containsRule);

      OnlyRule onlyRule = new OnlyRule("extension[http://different-url.com/StructureDefinition/Bar].value[x]");
      onlyRule.types = types(new OnlyRuleType("Quantity"));
      profile.rules.add(onlyRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      assertEquals(0, loggerSpy.getAllLogs("error").size());

      ElementDefinition extension = el(sd, "Observation.extension");
      ElementDefinition extensionSlice = el(sd, "Observation.extension:bar");
      ElementDefinition extensionSliceUrl = el(sd, "Observation.extension:bar.url");
      ElementDefinition extensionSliceValueX = el(sd, "Observation.extension:bar.value[x]");

      assertUrlSlicing(extension);
      assertNotNull(extensionSlice);
      assertNotNull(extensionSliceUrl);
      assertEquals("http://different-url.com/StructureDefinition/Bar", at(extensionSliceUrl, "fixedUri"));
      assertNotNull(extensionSliceValueX);
      assertJsEqual(List.of(new ElementDefinitionType("Quantity")), extensionSliceValueX.getType());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should apply multiple ContainsRule on an element with defined slicing")
    void multipleContainsRules() {
      Profile profile = new Profile("Foo");
      profile.parent = "resprate";

      ContainsRule rule1 = contains("code.coding", item("barSlice"));
      ContainsRule rule2 = contains("code.coding", item("fooSlice"));
      profile.rules.add(rule1);
      profile.rules.add(rule2);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("resprate");

      ElementDefinition barSlice = el(sd, "Observation.code.coding:barSlice");
      ElementDefinition fooSlice = el(sd, "Observation.code.coding:fooSlice");

      assertEquals(baseStructDef.elements.size() + 2, sd.elements.size());
      assertNotNull(barSlice);
      assertNotNull(fooSlice);
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should apply a containsRule on the child of a choice element with a common ancestor of Element")
    void childOfChoiceElement() {
      // Element contains extension and id
      // * value[x].extension contains my-inline-extension 0..1

      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      ContainsRule containsRule = contains("value[x].extension", item("my-inline-extension"));
      profile.rules.add(containsRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition extension = el(sd, "Observation.value[x].extension");
      ElementDefinition extensionSlice = el(sd, "Observation.value[x].extension:my-inline-extension");
      ElementDefinition extensionSliceUrl = el(sd, "Observation.value[x].extension:my-inline-extension.url");

      assertUrlSlicing(extension);
      assertNotNull(extensionSlice);
      assertNotNull(extensionSliceUrl);
      assertEquals("my-inline-extension", at(extensionSliceUrl, "fixedUri"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should report an error and not add the slice when a ContainsRule tries to add a slice that already exists")
    void duplicateSliceError() {
      // Profile: DoubleSlice
      // Parent: resprate
      // * code.coding contains onlySlice
      // * code.coding contains onlySlice and okJustOneMoreSlice

      Profile profile = new Profile("DoubleSlice");
      profile.parent = "resprate";

      ContainsRule rule1 = contains("code.coding", item("onlySlice"));
      ContainsRule rule2 = contains("code.coding", item("onlySlice"), item("okJustOneMoreSlice"));
      profile.rules.addAll(List.of(rule1, rule2));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("resprate");
      ElementDefinition onlySlice = el(sd, "Observation.code.coding:onlySlice");
      ElementDefinition otherSlice = el(sd, "Observation.code.coding:okJustOneMoreSlice");

      assertEquals(baseStructDef.elements.size() + 2, sd.elements.size());
      assertNotNull(onlySlice);
      assertNotNull(otherSlice);
      assertMatches("Slice named onlySlice already exists on element Observation\\.code\\.coding of DoubleSlice", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should report an error and not add the extension when an extension ContainsRule tries to add a slice that already exists but has a different extension URL")
    void duplicateExtensionDifferentUrlError() {
      // Profile: DoubleConflictingExtension
      // Parent: resprate
      // * extension contains http://hl7.org/fhir/StructureDefinition/observation-precondition named precondition
      // * extension contains
      //     http://hl7.org/fhir/StructureDefinition/servicerequest-precondition named precondition and
      //     http://hl7.org/fhir/StructureDefinition/observation-sequelTo named sequelTo

      Profile profile = new Profile("DoubleConflictingExtension");
      profile.parent = "resprate";

      ContainsRule rule1 = contains("extension", item("precondition", FHIR_SD + "observation-precondition"));
      ContainsRule rule2 = contains("extension", item("precondition", FHIR_SD + "servicerequest-precondition"),
          item("sequelTo", FHIR_SD + "observation-sequelTo"));
      profile.rules.addAll(List.of(rule1, rule2));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("resprate");
      ElementDefinition precondition = el(sd, "Observation.extension:precondition");
      ElementDefinition sequelTo = el(sd, "Observation.extension:sequelTo");

      assertEquals(baseStructDef.elements.size() + 2, sd.elements.size());
      assertNotNull(precondition);
      assertJsEqual(List.of(FHIR_SD + "observation-precondition"), precondition.getType().get(0).getProfile());
      assertNotNull(sequelTo);
      assertJsEqual(List.of(FHIR_SD + "observation-sequelTo"), sequelTo.getType().get(0).getProfile());
      assertMatches(
          "Slice named precondition already exists on element Observation\\.extension of DoubleConflictingExtension",
          "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should report a warning and not re-add the extension when an extension ContainsRule tries to add a slice that already exists with a matching extension URL")
    void duplicateExtensionSameUrlWarning() {
      // Profile: DoubleSameExtension
      // Parent: resprate
      // * extension contains http://hl7.org/fhir/StructureDefinition/observation-precondition named precondition
      // * extension contains
      //     http://hl7.org/fhir/StructureDefinition/observation-precondition named precondition and
      //     http://hl7.org/fhir/StructureDefinition/observation-sequelTo named sequelTo

      Profile profile = new Profile("DoubleSameExtension");
      profile.parent = "resprate";

      ContainsRule rule1 = contains("extension", item("precondition", FHIR_SD + "observation-precondition"));
      ContainsRule rule2 = contains("extension", item("precondition", FHIR_SD + "observation-precondition"),
          item("sequelTo", FHIR_SD + "observation-sequelTo"));
      profile.rules.addAll(List.of(rule1, rule2));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("resprate");
      ElementDefinition precondition = el(sd, "Observation.extension:precondition");
      ElementDefinition sequelTo = el(sd, "Observation.extension:sequelTo");

      assertEquals(baseStructDef.elements.size() + 2, sd.elements.size());
      assertNotNull(precondition);
      assertJsEqual(List.of(FHIR_SD + "observation-precondition"), precondition.getType().get(0).getProfile());
      assertNotNull(sequelTo);
      assertJsEqual(List.of(FHIR_SD + "observation-sequelTo"), sequelTo.getType().get(0).getProfile());
      assertMatches("Slice named precondition already exists on element Observation\\.extension of DoubleSameExtension",
          "s", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should warn (not error) when a duplicate extension ContainsRule has a version-pinned type.profile")
    void duplicateVersionPinnedWarning() {
      // A version-pinned slice type.profile (e.g. from hl7.fhir.uv.xver-* packages, '...|0.1.0')
      // must be recognized as the same request as an identically-pinned duplicate, so a harmless
      // duplicate is a warning rather than a "conflicting duplicate" error.
      Profile profile = new Profile("VersionedDuplicateExtension");
      profile.parent = "resprate";

      ContainsRule rule1 = contains("extension", item("precondition", FHIR_SD + "observation-precondition|4.0.1"));
      ContainsRule rule2 = contains("extension", item("precondition", FHIR_SD + "observation-precondition|4.0.1"),
          item("sequelTo", FHIR_SD + "observation-sequelTo"));
      profile.rules.addAll(List.of(rule1, rule2));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition precondition = el(sd, "Observation.extension:precondition");
      assertNotNull(precondition);
      // the versioned profile from rule1 is retained on the slice
      assertJsEqual(List.of(FHIR_SD + "observation-precondition|4.0.1"), precondition.getType().get(0).getProfile());
      // the duplicate is a warning, not an error
      assertTrue(anyMessageMatches("warn",
          "Slice named precondition already exists on element Observation\\.extension of VersionedDuplicateExtension"));
      assertFalse(anyMessageMatches("error", "Slice named precondition already exists"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should error when a duplicate extension ContainsRule references a different version of the same extension")
    void duplicateDifferentVersionError() {
      // The second slice definition is discarded, so the FSH would not reflect the final SD.
      Profile profile = new Profile("DifferentVersionDuplicateExtension");
      profile.parent = "resprate";

      ContainsRule rule1 = contains("extension", item("precondition", FHIR_SD + "observation-precondition|4.0.1"));
      ContainsRule rule2 = contains("extension", item("precondition", FHIR_SD + "observation-precondition|5.0.0"));
      profile.rules.addAll(List.of(rule1, rule2));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition precondition = el(sd, "Observation.extension:precondition");
      assertNotNull(precondition);
      // only the first (rule1) profile is kept
      assertJsEqual(List.of(FHIR_SD + "observation-precondition|4.0.1"), precondition.getType().get(0).getProfile());
      assertTrue(anyMessageMatches("error",
          "Slice named precondition already exists on element Observation\\.extension of "
              + "DifferentVersionDuplicateExtension"));
      assertFalse(anyMessageMatches("warn", "Slice named precondition already exists"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should error when a duplicate extension ContainsRule pins a version and the existing slice does not")
    void duplicateNewPinsVersionError() {
      Profile profile = new Profile("NewVersionedDuplicateExtension");
      profile.parent = "resprate";

      ContainsRule rule1 = contains("extension", item("precondition", FHIR_SD + "observation-precondition"));
      ContainsRule rule2 = contains("extension", item("precondition", FHIR_SD + "observation-precondition|4.0.1"));
      profile.rules.addAll(List.of(rule1, rule2));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition precondition = el(sd, "Observation.extension:precondition");
      assertNotNull(precondition);
      assertJsEqual(List.of(FHIR_SD + "observation-precondition"), precondition.getType().get(0).getProfile());
      assertTrue(anyMessageMatches("error",
          "Slice named precondition already exists on element Observation\\.extension of "
              + "NewVersionedDuplicateExtension"));
      assertFalse(anyMessageMatches("warn", "Slice named precondition already exists"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should error when a duplicate extension ContainsRule omits a version and the existing slice pins one")
    void duplicateExistingPinsVersionError() {
      Profile profile = new Profile("ExistingVersionedDuplicateExtension");
      profile.parent = "resprate";

      ContainsRule rule1 = contains("extension", item("precondition", FHIR_SD + "observation-precondition|4.0.1"));
      ContainsRule rule2 = contains("extension", item("precondition", FHIR_SD + "observation-precondition"));
      profile.rules.addAll(List.of(rule1, rule2));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition precondition = el(sd, "Observation.extension:precondition");
      assertNotNull(precondition);
      assertJsEqual(List.of(FHIR_SD + "observation-precondition|4.0.1"), precondition.getType().get(0).getProfile());
      assertTrue(anyMessageMatches("error",
          "Slice named precondition already exists on element Observation\\.extension of "
              + "ExistingVersionedDuplicateExtension"));
      assertFalse(anyMessageMatches("warn", "Slice named precondition already exists"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should report an error and not add the slice when a ContainsRule tries to add a slice that was created on the parent")
    void sliceCreatedOnParentError() {
      // Profile: FirstProfile
      // Parent: resprate
      // * code.coding contains onlySlice

      // Profile: SecondProfile
      // Parent: FirstProfile
      // * code.coding contains onlySlice

      Profile firstProfile = new Profile("FirstProfile");
      firstProfile.parent = "resprate";
      ContainsRule firstRule = contains("code.coding", item("onlySlice"));
      firstProfile.rules.add(firstRule);
      doc.profiles.put(firstProfile.name, firstProfile);

      Profile secondProfile = new Profile("SecondProfile");
      secondProfile.parent = "FirstProfile";
      ContainsRule secondRule = contains("code.coding", item("onlySlice"));
      secondProfile.rules.add(secondRule);
      doc.profiles.put(secondProfile.name, secondProfile);

      exporter.exportStructDef(firstProfile);
      exporter.exportStructDef(secondProfile);
      StructureDefinition firstSd = pkg.profiles.get(0);
      StructureDefinition secondSd = pkg.profiles.get(1);
      ElementDefinition firstSlice = el(firstSd, "Observation.code.coding:onlySlice");
      ElementDefinition secondSlice = el(secondSd, "Observation.code.coding:onlySlice");

      assertNotNull(firstSlice);
      assertNotNull(secondSlice);
      assertEquals(firstSd.elements.size(), secondSd.elements.size());
      assertMatches("Slice named onlySlice already exists on element Observation\\.code\\.coding of SecondProfile", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should report an error and not add the slice when a ContainsRule tries to add a slice to a slicing that was closed on the parent")
    void slicingClosedOnParentError() {
      // Profile: FirstProfile
      // Parent: Observation
      // * component ^slicing.discriminator[0].type = #pattern
      // * component ^slicing.discriminator[0].path = "code"
      // * component ^slicing.rules = #closed
      // * component contains firstSlice

      // Profile: SecondProfile
      // Parent: FirstProfile
      // * component contains secondSlice

      Profile firstProfile = new Profile("FirstProfile");
      firstProfile.parent = "Observation";
      CaretValueRule slicingType = caret("component", "slicing.discriminator[0].type", new FshCode("pattern"));
      CaretValueRule slicingPath = caret("component", "slicing.discriminator[0].path", "code");
      CaretValueRule slicingRules = caret("component", "slicing.rules", new FshCode("closed"));
      ContainsRule firstRule = contains("component", item("firstSlice"));
      firstProfile.rules.addAll(List.of(slicingType, slicingPath, slicingRules, firstRule));
      doc.profiles.put(firstProfile.name, firstProfile);

      Profile secondProfile = new Profile("SecondProfile");
      secondProfile.parent = "FirstProfile";
      ContainsRule secondRule = contains("component", item("secondSlice"));
      secondProfile.rules.add(secondRule);
      doc.profiles.put(secondProfile.name, secondProfile);

      exporter.exportStructDef(firstProfile);
      exporter.exportStructDef(secondProfile);
      StructureDefinition firstSd = pkg.profiles.get(0);
      StructureDefinition secondSd = pkg.profiles.get(1);

      assertNotNull(el(firstSd, "Observation.component:firstSlice"));
      assertNotNull(el(secondSd, "Observation.component:firstSlice"));
      assertNull(el(secondSd, "Observation.component:secondSlice"));
      assertEquals(firstSd.elements.size(), secondSd.elements.size());
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches(
          "Cannot add secondSlice to Observation\\.component since its slicing was closed by a parent definition", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should not report an error when a ContainsRule adds slices to a slicing that is closed in the same profile")
    void slicingClosedInSameProfile() {
      // Profile: Foo
      // Parent: Observation
      // * component ^slicing.discriminator[0].type = #pattern
      // * component ^slicing.discriminator[0].path = "code"
      // * component ^slicing.rules = #closed
      // * component contains firstSlice
      // * component contains secondSlice

      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      CaretValueRule slicingType = caret("component", "slicing.discriminator[0].type", new FshCode("pattern"));
      CaretValueRule slicingPath = caret("component", "slicing.discriminator[0].path", "code");
      CaretValueRule slicingRules = caret("component", "slicing.rules", new FshCode("closed"));
      ContainsRule firstRule = contains("component", item("firstSlice"));
      ContainsRule secondRule = contains("component", item("secondSlice"));
      profile.rules.addAll(List.of(slicingType, slicingPath, slicingRules, firstRule, secondRule));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      assertEquals("closed", at(el(sd, "Observation.component"), "slicing.rules"));
      assertNotNull(el(sd, "Observation.component:firstSlice"));
      assertNotNull(el(sd, "Observation.component:secondSlice"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should not report an error when a ContainsRule reslices a slice of a slicing that was closed on the parent")
    void resliceOfSlicingClosedOnParent() {
      // Profile: FirstProfile
      // Parent: Observation
      // * component ^slicing.discriminator[0].type = #pattern
      // * component ^slicing.discriminator[0].path = "code"
      // * component ^slicing.rules = #closed
      // * component contains firstSlice

      // Profile: SecondProfile
      // Parent: FirstProfile
      // * component[firstSlice] contains reslice

      Profile firstProfile = new Profile("FirstProfile");
      firstProfile.parent = "Observation";
      CaretValueRule slicingType = caret("component", "slicing.discriminator[0].type", new FshCode("pattern"));
      CaretValueRule slicingPath = caret("component", "slicing.discriminator[0].path", "code");
      CaretValueRule slicingRules = caret("component", "slicing.rules", new FshCode("closed"));
      ContainsRule firstRule = contains("component", item("firstSlice"));
      firstProfile.rules.addAll(List.of(slicingType, slicingPath, slicingRules, firstRule));
      doc.profiles.put(firstProfile.name, firstProfile);

      Profile secondProfile = new Profile("SecondProfile");
      secondProfile.parent = "FirstProfile";
      ContainsRule resliceRule = contains("component[firstSlice]", item("reslice"));
      secondProfile.rules.add(resliceRule);
      doc.profiles.put(secondProfile.name, secondProfile);

      exporter.exportStructDef(firstProfile);
      exporter.exportStructDef(secondProfile);
      StructureDefinition secondSd = pkg.profiles.get(1);

      assertNotNull(el(secondSd, "Observation.component:firstSlice/reslice"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should report an error and not add the extension when an extension ContainsRule tries to add an extension to a slicing that was closed on the parent")
    void extensionSlicingClosedOnParentError() {
      // Profile: FirstProfile
      // Parent: Observation
      // * extension ^slicing.rules = #closed
      // * extension contains valueset-expression named vs

      // Profile: SecondProfile
      // Parent: FirstProfile
      // * extension contains patient-mothersMaidenName named maidenName

      Profile firstProfile = new Profile("FirstProfile");
      firstProfile.parent = "Observation";
      CaretValueRule slicingRules = caret("extension", "slicing.rules", new FshCode("closed"));
      ContainsRule firstRule = contains("extension", item("vs", "valueset-expression"));
      firstProfile.rules.addAll(List.of(slicingRules, firstRule));
      doc.profiles.put(firstProfile.name, firstProfile);

      Profile secondProfile = new Profile("SecondProfile");
      secondProfile.parent = "FirstProfile";
      ContainsRule secondRule = contains("extension", item("maidenName", "patient-mothersMaidenName"));
      secondProfile.rules.add(secondRule);
      doc.profiles.put(secondProfile.name, secondProfile);

      exporter.exportStructDef(firstProfile);
      exporter.exportStructDef(secondProfile);
      StructureDefinition firstSd = pkg.profiles.get(0);
      StructureDefinition secondSd = pkg.profiles.get(1);

      assertNotNull(el(firstSd, "Observation.extension:vs"));
      assertNotNull(el(secondSd, "Observation.extension:vs"));
      assertNull(el(secondSd, "Observation.extension:maidenName"));
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches(
          "Cannot add maidenName to Observation\\.extension since its slicing was closed by a parent definition", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should not report an error when a ContainsRule adds a slice to an open slicing from the parent that the profile closes itself")
    void openSlicingFromParentClosedByProfile() {
      // Profile: FirstProfile
      // Parent: Observation
      // * component ^slicing.discriminator[0].type = #pattern
      // * component ^slicing.discriminator[0].path = "code"
      // * component ^slicing.rules = #open
      // * component contains firstSlice

      // Profile: SecondProfile
      // Parent: FirstProfile
      // * component ^slicing.rules = #closed
      // * component contains secondSlice

      Profile firstProfile = new Profile("FirstProfile");
      firstProfile.parent = "Observation";
      CaretValueRule slicingType = caret("component", "slicing.discriminator[0].type", new FshCode("pattern"));
      CaretValueRule slicingPath = caret("component", "slicing.discriminator[0].path", "code");
      CaretValueRule slicingRules = caret("component", "slicing.rules", new FshCode("open"));
      ContainsRule firstRule = contains("component", item("firstSlice"));
      firstProfile.rules.addAll(List.of(slicingType, slicingPath, slicingRules, firstRule));
      doc.profiles.put(firstProfile.name, firstProfile);

      Profile secondProfile = new Profile("SecondProfile");
      secondProfile.parent = "FirstProfile";
      CaretValueRule closeRule = caret("component", "slicing.rules", new FshCode("closed"));
      ContainsRule secondRule = contains("component", item("secondSlice"));
      secondProfile.rules.addAll(List.of(closeRule, secondRule));
      doc.profiles.put(secondProfile.name, secondProfile);

      exporter.exportStructDef(firstProfile);
      exporter.exportStructDef(secondProfile);
      StructureDefinition secondSd = pkg.profiles.get(1);

      assertEquals("closed", at(el(secondSd, "Observation.component"), "slicing.rules"));
      assertNotNull(el(secondSd, "Observation.component:secondSlice"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should report an error and not add the slice when a ContainsRule tries to add a slice to a slicing that is closed on a parent from a dependency")
    void slicingClosedOnDependencyParentError() {
      // Profile: Foo
      // Parent: lipidprofile
      // * result contains extraResult

      Profile profile = new Profile("Foo");
      profile.parent = "lipidprofile";
      ContainsRule rule = contains("result", item("extraResult"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("lipidprofile");

      assertEquals("closed", at(el(sd, "DiagnosticReport.result"), "slicing.rules"));
      assertNull(el(sd, "DiagnosticReport.result:extraResult"));
      assertEquals(baseStructDef.elements.size(), sd.elements.size());
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches(
          "Cannot add extraResult to DiagnosticReport\\.result since its slicing was closed by a parent definition",
          "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should report a warning and not an error when an extension ContainsRule restates an extension on a slicing that was closed on the parent")
    void restatedExtensionOnClosedSlicingWarning() {
      // Profile: FirstProfile
      // Parent: Observation
      // * extension ^slicing.rules = #closed
      // * extension contains valueset-expression named vs

      // Profile: SecondProfile
      // Parent: FirstProfile
      // * extension contains valueset-expression named vs

      Profile firstProfile = new Profile("FirstProfile");
      firstProfile.parent = "Observation";
      CaretValueRule slicingRules = caret("extension", "slicing.rules", new FshCode("closed"));
      ContainsRule firstRule = contains("extension", item("vs", "valueset-expression"));
      firstProfile.rules.addAll(List.of(slicingRules, firstRule));
      doc.profiles.put(firstProfile.name, firstProfile);

      Profile secondProfile = new Profile("SecondProfile");
      secondProfile.parent = "FirstProfile";
      ContainsRule secondRule = contains("extension", item("vs", "valueset-expression"));
      secondProfile.rules.add(secondRule);
      doc.profiles.put(secondProfile.name, secondProfile);

      exporter.exportStructDef(firstProfile);
      exporter.exportStructDef(secondProfile);
      StructureDefinition firstSd = pkg.profiles.get(0);
      StructureDefinition secondSd = pkg.profiles.get(1);

      assertNotNull(el(secondSd, "Observation.extension:vs"));
      assertEquals(firstSd.elements.size(), secondSd.elements.size());
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("Slice named vs already exists on element Observation\\.extension of SecondProfile", "s",
          loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should not apply a ContainsRule on an element without defined slicing")
    void elementWithoutDefinedSlicing() {
      Profile profile = new Profile("Foo");
      profile.parent = "resprate";

      ContainsRule rule = new ContainsRule("identifier").<ContainsRule>withFile("NoSlice.fsh")
          .withLocation(6, 3, 6, 12);
      rule.items = items(item("barSlice"));
      profile.rules.add(rule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("resprate");

      ElementDefinition barSlice = el(sd, "Observation.identifier:barSlice");

      assertEquals(baseStructDef.elements.size(), sd.elements.size());
      assertNull(barSlice);
      assertMatches("File: NoSlice\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    // Since previous versions of SUSHI used the slicename as a type lookup as well, we used to issue a warning when we
    // found a rule that may have been intended to work that way (essentially, a user who has not updated their fsh).
    // Time has passed since then and we no longer issue that warning.  This test proves that.
    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should NOT report a warning if the extension slice name resolves to an external extension type and no explicit type was specified")
    void sliceNameResolvesToExternalExtensionNoWarning() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      ContainsRule containsRule = new ContainsRule("extension").<ContainsRule>withFile("ExternalSliceName.fsh")
          .withLocation(6, 3, 6, 12);
      // maxSize is the id of a core FHIR extension
      containsRule.items = items(item("maxSize"));
      CardRule cardRule = card("extension[maxSize]", 0.0, "1");
      profile.rules.addAll(List.of(containsRule, cardRule));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition extension = el(sd, "Observation.extension");
      ElementDefinition extensionSlice = el(sd, "Observation.extension:maxSize");
      ElementDefinition extensionSliceUrl = el(sd, "Observation.extension:maxSize.url");

      assertUrlSlicing(extension);
      assertNotNull(extensionSlice);
      assertNotNull(extensionSliceUrl);
      assertEquals("maxSize", at(extensionSliceUrl, "fixedUri"));

      // In previous versions of this test, a warning was expected here
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should NOT report a warning if the extension slice name resolves to a FSH extension and no explicit type was specified")
    void sliceNameResolvesToFshExtensionNoWarning() {
      Extension extension = new Extension("MyFshExtension");
      CardRule extCardRule = card("extension", 0.0, "0");
      extension.rules.add(extCardRule);
      doc.extensions.put(extension.name, extension);

      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      ContainsRule containsRule = new ContainsRule("extension").<ContainsRule>withFile("FSHSliceName.fsh")
          .withLocation(6, 3, 6, 12);
      containsRule.items = items(item("MyFshExtension"));
      CardRule cardRule = card("extension[MyFshExtension]", 0.0, "1");
      profile.rules.addAll(List.of(containsRule, cardRule));

      exporter.exportStructDef(extension);
      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition extensionEl = el(sd, "Observation.extension");
      ElementDefinition extensionSlice = el(sd, "Observation.extension:MyFshExtension");
      ElementDefinition extensionSliceUrl = el(sd, "Observation.extension:MyFshExtension.url");

      assertUrlSlicing(extensionEl);
      assertNotNull(extensionSlice);
      assertNotNull(extensionSliceUrl);
      assertEquals("MyFshExtension", at(extensionSliceUrl, "fixedUri"));

      // In previous versions of this test, a warning was expected here
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should not report a warning if the extension slice name resolves to an extension type but explicit type was specified")
    void sliceNameResolvesWithExplicitTypeNoWarning() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      // maxSize is the id of a core FHIR extension
      ContainsRule containsRule = contains("extension", item("maxSize", "maxSize"));
      CardRule cardRule = card("extension[maxSize]", 0.0, "1");
      profile.rules.addAll(List.of(containsRule, cardRule));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition extension = el(sd, "Observation.extension");
      ElementDefinition extensionSlice = el(sd, "Observation.extension:maxSize");
      ElementDefinition extensionSliceUrl = el(sd, "Observation.extension:maxSize.url");

      assertUrlSlicing(extension);
      assertNotNull(extensionSlice);
      assertJsEqual(List.of(new ElementDefinitionType("Extension").withProfiles(FHIR_SD + "maxSize")),
          extensionSlice.getType());
      assertNull(extensionSliceUrl);

      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should not report a warning if the extension slice name does not resolve to an extension type")
    void sliceNameDoesNotResolveNoWarning() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      // maxSize is the id of a core FHIR extension
      ContainsRule containsRule = contains("extension", item("foo"));
      CardRule cardRule = card("extension[foo]", 0.0, "1");
      profile.rules.addAll(List.of(containsRule, cardRule));

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);

      ElementDefinition extension = el(sd, "Observation.extension");
      ElementDefinition extensionSlice = el(sd, "Observation.extension:foo");
      ElementDefinition extensionSliceUrl = el(sd, "Observation.extension:foo.url");

      assertUrlSlicing(extension);
      assertNotNull(extensionSlice);
      assertNotNull(extensionSliceUrl);
      assertEquals("foo", at(extensionSliceUrl, "fixedUri"));

      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should report an error if the author specifies a slice type on a non-extension")
    void sliceTypeOnNonExtensionError() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      CaretValueRule slicingType = caret("component", "slicing.discriminator[0].type", new FshCode("pattern"));
      CaretValueRule slicingPath = caret("component", "slicing.discriminator[0].path", "code");
      CaretValueRule slicingRules = caret("component", "slicing.rules", new FshCode("open"));
      ContainsRule containsRule = new ContainsRule("component").<ContainsRule>withFile("BadSlice.fsh")
          .withLocation(6, 3, 6, 12);
      containsRule.items = items(item("offset", "observation-timeOffset"));
      profile.rules.addAll(List.of(slicingType, slicingPath, slicingRules, containsRule));

      exporter.exportStructDef(profile);

      assertMatches("Cannot specify type on offset slice since component is not an extension path\\..*"
          + "File: BadSlice\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("error"));

      // But it should still go ahead and create the slice
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition offsetSlice = el(sd, "Observation.component:offset");
      assertJsEqual("offset", at(offsetSlice, "sliceName"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should report an error for an extension ContainsRule with a type that resolves to a non-extension")
    void typeResolvesToNonExtensionError() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      ContainsRule containsRule = new ContainsRule("extension").<ContainsRule>withFile("BadExt.fsh")
          .withLocation(6, 3, 6, 12);
      containsRule.items = items(item("condition", "Condition"));
      profile.rules.add(containsRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");
      assertEquals(baseStructDef.elements.size(), sd.elements.size());

      ElementDefinition conditionSlice = el(sd, "Observation.extension:condition");
      assertNull(conditionSlice);
      assertMatches("Cannot create condition extension; unable to locate extension definition for: Condition\\..*"
          + "File: BadExt\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should report an error for an extension ContainsRule with a non-modifier extension type on a modifierExtension path")
    void nonModifierOnModifierExtensionError() {
      Extension extension = new Extension("MyExtension");
      doc.extensions.put(extension.name, extension);

      Profile profile = new Profile("MyObservation");
      profile.parent = "Observation";
      ContainsRule containsRule = new ContainsRule("modifierExtension").<ContainsRule>withFile("WrongModifier.fsh")
          .withLocation(5, 3, 5, 29);
      containsRule.items = items(item("myExt", "MyExtension"));
      profile.rules.add(containsRule);
      doc.profiles.put(profile.name, profile);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition extensionSlice = el(sd, "Observation.modifierExtension:myExt");
      assertNotNull(extensionSlice);
      assertMatches("Non-modifier extension MyExtension assigned to modifierExtension path\\. Non-modifier extensions "
          + "should only be assigned to extension paths\\..*File: WrongModifier\\.fsh.*Line: 5\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should not report an error for an extension ContainsRule with a modifier extension type on a modifierExtension path")
    void modifierOnModifierExtension() {
      Extension extension = new Extension("MyExtension");
      FlagRule modifierRule = new FlagRule(".");
      modifierRule.modifier = true;
      extension.rules.add(modifierRule);
      doc.extensions.put(extension.name, extension);

      Profile profile = new Profile("MyObservation");
      profile.parent = "Observation";
      ContainsRule containsRule = contains("modifierExtension", item("myExt", "MyExtension"));
      profile.rules.add(containsRule);
      doc.profiles.put(profile.name, profile);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition extensionSlice = el(sd, "Observation.modifierExtension:myExt");
      assertNotNull(extensionSlice);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should report an error for an extension ContainsRule with a modifier extension type on an extension path")
    void modifierOnExtensionError() {
      Extension extension = new Extension("MyExtension");
      FlagRule modifierRule = new FlagRule(".");
      modifierRule.modifier = true;
      extension.rules.add(modifierRule);
      doc.extensions.put(extension.name, extension);

      Profile profile = new Profile("MyObservation");
      profile.parent = "Observation";
      ContainsRule containsRule = new ContainsRule("extension").<ContainsRule>withFile("WrongModifier.fsh")
          .withLocation(9, 4, 9, 21);
      containsRule.items = items(item("myExt", "MyExtension"));
      profile.rules.add(containsRule);
      doc.profiles.put(profile.name, profile);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition extensionSlice = el(sd, "Observation.extension:myExt");
      assertNotNull(extensionSlice);
      assertMatches("Modifier extension MyExtension assigned to extension path\\. Modifier extensions should only be "
          + "assigned to modifierExtension paths\\..*File: WrongModifier\\.fsh.*Line: 9\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should not report an error for an extension ContainsRule with a non-modifier extension type on an extension path")
    void nonModifierOnExtension() {
      Extension extension = new Extension("MyExtension");
      doc.extensions.put(extension.name, extension);

      Profile profile = new Profile("MyObservation");
      profile.parent = "Observation";
      ContainsRule containsRule = contains("category.extension", item("myExt", "MyExtension"));
      profile.rules.add(containsRule);
      doc.profiles.put(profile.name, profile);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition extensionSlice = el(sd, "Observation.category.extension:myExt");
      assertNotNull(extensionSlice);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should report an error for an extension ContainsRule with a type that does not resolve")
    void typeDoesNotResolveError() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      ContainsRule containsRule = new ContainsRule("extension").<ContainsRule>withFile("BadExt.fsh")
          .withLocation(6, 3, 6, 12);
      containsRule.items = items(item("spoon", "IDoNotExist"));
      profile.rules.add(containsRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");
      assertEquals(baseStructDef.elements.size(), sd.elements.size());

      ElementDefinition spoonSlice = el(sd, "Observation.extension:spoon");
      assertNull(spoonSlice);
      assertMatches("Cannot create spoon extension; unable to locate extension definition for: IDoNotExist\\..*"
          + "File: BadExt\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should report an error for a ContainsRule on a single element")
    void singleElementError() {
      Profile profile = new Profile("Foo");
      profile.parent = "Observation";

      ContainsRule containsRule = new ContainsRule("status").<ContainsRule>withFile("SingleElement.fsh")
          .withLocation(6, 3, 6, 12);
      containsRule.items = items(item("test"));
      profile.rules.add(containsRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      StructureDefinition baseStructDef = fisher.fishForStructureDefinition("Observation");
      assertEquals(baseStructDef.elements.size(), sd.elements.size());

      ElementDefinition slice = el(sd, "Observation.status:test");
      assertNull(slice);
      assertMatches("Cannot slice element 'status'.*File: SingleElement\\.fsh.*Line: 6\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("StructureDefinitionExporter R4 #ContainsRule should not report an error for an extension Contains rule with an extension that is missing a snapshot when checking if its a modifierExtension")
    void extensionMissingSnapshot() {
      // Create an extension without a snapshot for testing
      // Note: SUSHI wouldn't create an extension like this, but it might be provided by a package or custom resource
      // (As in TS, this mutates the shared defs: the predefined package is loaded into them.)
      Map<String, Object> predefinedResourceMap = new LinkedHashMap<>();
      Object noSnapshotExtension = Js.cloneDeep(defs.fishForFHIR("familymemberhistory-type"));
      Common.setProp(noSnapshotExtension, "id", "familymemberhistory-type-no-snapshot");
      Common.setProp(noSnapshotExtension, "url", FHIR_SD + "familymemberhistory-type-no-snapshot");
      Common.deleteProp(noSnapshotExtension, "snapshot");
      predefinedResourceMap.put("familymemberhistory-type-no-snapshot", noSnapshotExtension);
      loadInMemoryVirtualPackage(defs, FHIRDefinitions.PREDEFINED_PACKAGE_NAME,
          FHIRDefinitions.PREDEFINED_PACKAGE_VERSION, predefinedResourceMap);

      Profile profile = new Profile("Foo");
      profile.parent = "Observation";
      ContainsRule containsRule = new ContainsRule("extension").<ContainsRule>withFile("BadExt.fsh")
          .withLocation(6, 3, 6, 12);
      containsRule.items = items(item("nosnapshot", "familymemberhistory-type-no-snapshot"));
      profile.rules.add(containsRule);

      exporter.exportStructDef(profile);
      StructureDefinition sd = pkg.profiles.get(0);
      ElementDefinition noSnapshotSlice = el(sd, "Observation.extension:nosnapshot");
      assertNotNull(noSnapshotSlice);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }
  }
}
