package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getTestFHIRDefinitions;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Mapping;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.RuleSet;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.InsertRule;
import org.hl7.fhir.sushi.fshtypes.rules.MappingRule;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.js.Js;
import org.hl7.fhir.sushi.testhelpers.MinimalConfig;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/export/MappingExporter.test.ts (SUSHI 3.21.1)
@DisplayName("MappingExporter")
class MappingExporterTest {
  static FHIRDefinitions defs;
  FSHDocument doc;
  MappingExporter exporter;
  StructureDefinitionExporter sdExporter;
  StructureDefinition observation;
  StructureDefinition practitioner;
  StructureDefinition noMappingsProfile;
  StructureDefinition logical;
  StructureDefinition resource;

  @BeforeAll
  static void beforeAll() {
    defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"),
        testDefsPath("StructureDefinition-NoMappingsProfile.json"));
  }

  @BeforeEach
  void beforeEach() {
    loggerSpy.reset();
    loggerSpy.reset();
    doc = new FSHDocument("fileName");
    FSHTank input = new FSHTank(List.of(doc), MinimalConfig.minimalConfig());
    Package pkg = new Package(input.config);
    TestFisher fisher = new TestFisher(input, defs, pkg);
    sdExporter = new StructureDefinitionExporter(input, pkg, fisher);
    exporter = new MappingExporter(input, pkg, fisher);
    observation = fisher.fishForStructureDefinition("Observation");
    observation.setId("MyObservation");
    pkg.profiles.add(observation);
    practitioner = fisher.fishForStructureDefinition("Practitioner");
    practitioner.setId("MyPractitioner");
    pkg.profiles.add(practitioner);
    noMappingsProfile = fisher.fishForStructureDefinition("NoMappingsProfile");
    noMappingsProfile.setId("NoMappingsProfile");
    pkg.profiles.add(noMappingsProfile);
    logical = fisher.fishForStructureDefinition("eLTSSServiceModel");
    logical.setId("MyLogical");
    pkg.logicals.add(logical);
    resource = fisher.fishForStructureDefinition("Duration");
    resource.setId("MyResource");
    pkg.resources.add(resource);
  }

  static ElementDefinition element(StructureDefinition sd, String id) {
    return sd.elements.stream().filter(e -> Objects.equals(e.getId(), id)).findFirst().orElse(null);
  }

  static Object last(List<Object> list) {
    return list.get(list.size() - 1);
  }

  static Object findMapping(StructureDefinition sd, String identity) {
    return sd.getMapping().stream().filter(m -> Objects.equals(((Map<?, ?>) m).get("identity"), identity))
        .findFirst().orElse(null);
  }

  @Test
  @DisplayName("MappingExporter should log an error when the mapping source does not exist")
  void shouldLogAnErrorWhenTheMappingSourceDoesNotExist() {
    Mapping mapping = new Mapping("MyMapping").withFile("NoSource.fsh").withLocation(new int[] {1, 2, 3, 4});
    mapping.source = "MyInvalidSource";
    doc.mappings.put(mapping.name, mapping);
    exporter.export();
    assertMatches("Unable to find source \"MyInvalidSource\".*File: NoSource\\.fsh.*Line: 1 - 3\\D*", "s",
        loggerSpy.getLastMessage("error"));
  }

  @Nested
  @DisplayName("#setMetadata")
  class SetMetadata {
    @Test
    @DisplayName("MappingExporter #setMetadata should export no mappings with empty input")
    void shouldExportNoMappingsWithEmptyInput() {
      int originalLength = observation.getMapping().size();
      exporter.export();
      assertEquals(originalLength, observation.getMapping().size());
    }

    @Test
    @DisplayName("MappingExporter #setMetadata should export the simplest possible mapping")
    void shouldExportTheSimplestPossibleMapping() {
      int originalLength = observation.getMapping().size();
      Mapping mapping = new Mapping("MyMapping");
      mapping.source = "MyObservation";
      doc.mappings.put(mapping.name, mapping);
      exporter.export();
      assertEquals(originalLength + 1, observation.getMapping().size());
      Object exported = last(observation.getMapping());
      assertEquals("MyMapping", at(exported, "identity"));
    }

    @Test
    @DisplayName("MappingExporter #setMetadata should export a mapping when one does not yet exist")
    void shouldExportAMappingWhenOneDoesNotYetExist() {
      observation.deleteProperty("mapping");
      Mapping mapping = new Mapping("MyMapping");
      mapping.source = "MyObservation";
      doc.mappings.put(mapping.name, mapping);
      exporter.export();
      assertEquals(1, observation.getMapping().size());
      Object exported = last(observation.getMapping());
      assertEquals("MyMapping", at(exported, "identity"));
    }

    @Test
    @DisplayName("MappingExporter #setMetadata should export a mapping whose source is based on a structure definition "
        + "without any existing mappings")
    void shouldExportAMappingWhoseSourceIsBasedOnAStructureDefinitionWithoutAnyExistingMappings() {
      Profile childProfile = new Profile("ChildProfile");
      childProfile.parent = "NoMappingsProfile";
      doc.profiles.put(childProfile.name, childProfile);
      Mapping mapping = new Mapping("MyMapping");
      mapping.source = "ChildProfile"; // Note - ChildProfile is based off ProfileWithNoMappings
      doc.mappings.put(mapping.name, mapping);
      StructureDefinition childSd = sdExporter.export().profiles.get(3);
      exporter.export();
      assertEquals(1, childSd.getMapping().size());
      assertEquals("MyMapping", at(childSd.getMapping().get(0), "identity"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("MappingExporter #setMetadata should export a mapping with optional metadata")
    void shouldExportAMappingWithOptionalMetadata() {
      int originalLength = observation.getMapping().size();
      Mapping mapping = new Mapping("MyMapping");
      mapping.id = "my-map";
      mapping.source = "MyObservation";
      mapping.target = "http://mytarget.com";
      mapping.description = "Hello there";
      mapping.title = "HEY THERE";
      doc.mappings.put(mapping.name, mapping);
      exporter.export();
      assertEquals(originalLength + 1, observation.getMapping().size());
      Object exported = last(observation.getMapping());
      assertEquals("my-map", at(exported, "identity"));
      assertEquals("HEY THERE", at(exported, "name"));
      assertEquals("http://mytarget.com", at(exported, "uri"));
      assertEquals("Hello there", at(exported, "comment"));
    }

    @Test
    @DisplayName("MappingExporter #setMetadata should log an error and not apply a mapping with an invalid Id")
    void shouldLogAnErrorAndNotApplyAMappingWithAnInvalidId() {
      int originalLength = observation.getMapping().size();
      Mapping mapping = new Mapping("MyMapping").withLocation(new int[] {1, 2, 3, 4}).withFile("BadMapping.fsh");
      mapping.source = "MyObservation";
      mapping.id = "Invalid!";
      doc.mappings.put(mapping.name, mapping);
      exporter.export();
      assertEquals(originalLength, observation.getMapping().size());
      assertMatches("The string \"Invalid!\" does not represent a valid FHIR id", loggerSpy.getLastMessage("error"));
      assertMatches("File: BadMapping\\.fsh.*Line: 1 - 3\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("MappingExporter #setMetadata should log an error when multiple mappings have the same source and "
        + "the same id")
    void shouldLogAnErrorWhenMultipleMappingsHaveTheSameSourceAndTheSameId() {
      Mapping firstMapping = new Mapping("FirstMapping").withFile("Mappings.fsh").withLocation(new int[] {3, 8, 7, 18});
      firstMapping.source = "MyObservation";
      firstMapping.id = "reused-id";
      doc.mappings.put(firstMapping.name, firstMapping);
      Mapping secondMapping = new Mapping("SecondMapping").withFile("Mappings.fsh")
          .withLocation(new int[] {8, 8, 11, 19});
      secondMapping.source = "MyObservation";
      secondMapping.id = "reused-id";
      doc.mappings.put(secondMapping.name, secondMapping);

      exporter.export();
      assertMatches("Multiple mappings on MyObservation found with id reused-id", loggerSpy.getLastMessage("error"));
      assertMatches("File: Mappings\\.fsh.*Line: 8 - 11\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("MappingExporter #setMetadata should not log an error when multiple mappings have different sources "
        + "and the same id")
    void shouldNotLogAnErrorWhenMultipleMappingsHaveDifferentSourcesAndTheSameId() {
      Mapping firstMapping = new Mapping("FirstMapping").withFile("Mappings.fsh").withLocation(new int[] {3, 8, 7, 18});
      firstMapping.source = "MyObservation";
      firstMapping.id = "reused-id";
      doc.mappings.put(firstMapping.name, firstMapping);
      Mapping secondMapping = new Mapping("SecondMapping").withFile("Mappings.fsh")
          .withLocation(new int[] {8, 8, 11, 19});
      secondMapping.source = "MyPractitioner";
      secondMapping.id = "reused-id";
      doc.mappings.put(secondMapping.name, secondMapping);

      exporter.export();
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("MappingExporter #setMetadata should not log an error and not add metadata but add rules for a simple "
        + "Mapping that is inherited from the parent")
    void shouldNotLogAnErrorAndNotAddMetadataButAddRulesForASimpleMappingThatIsInheritedFromTheParent() {
      Mapping mapping = new Mapping("rim");
      mapping.source = "MyObservation";
      MappingRule newRule = new MappingRule("status");
      newRule.map = "Something.new";
      mapping.rules.add(newRule);
      doc.mappings.put(mapping.name, mapping);

      int originalMappingLength = observation.getMapping().size();
      ElementDefinition status = element(observation, "Observation.status");
      int originalStatusMappingLength = status.getMapping().size();
      Object originalRimMapping = Js.cloneDeep(findMapping(observation, "rim"));

      exporter.export();
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(originalMappingLength, observation.getMapping().size()); // No metadata added
      assertEquals(originalStatusMappingLength + 1, status.getMapping().size()); // New rule added to status element

      // Metadata is same as the parent
      assertJsEqual(originalRimMapping, findMapping(observation, "rim"));
    }

    @Test
    @DisplayName("MappingExporter #setMetadata should not log an error and not add metadata but add rules for a "
        + "Mapping that is inherited from the parent with the same metadata")
    void shouldNotLogAnErrorAndNotAddMetadataButAddRulesForAMappingThatIsInheritedFromTheParentWithTheSameMetadata() {
      Mapping mapping = new Mapping("rim");
      mapping.source = "MyObservation";
      mapping.id = "rim";
      mapping.title = "RIM Mapping";
      mapping.target = "http://hl7.org/v3";
      MappingRule newRule = new MappingRule("status");
      newRule.map = "Something.new";
      mapping.rules.add(newRule);
      doc.mappings.put(mapping.name, mapping);

      int originalMappingLength = observation.getMapping().size();
      ElementDefinition status = element(observation, "Observation.status");
      int originalStatusMappingLength = status.getMapping().size();

      exporter.export();
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(originalMappingLength, observation.getMapping().size()); // No metadata added
      assertEquals(originalStatusMappingLength + 1, status.getMapping().size()); // New rule added to status element
    }

    @Test
    @DisplayName("MappingExporter #setMetadata should not log an error, should update metadata, and should add rules "
        + "for a Mapping that is inherited from the parent and has additional metadata not on the parent")
    @SuppressWarnings("unchecked")
    void shouldNotLogAnErrorShouldUpdateMetadataAndShouldAddRulesForAMappingThatIsInheritedFromTheParent() {
      Mapping mapping = new Mapping("rim");
      mapping.source = "MyObservation";
      mapping.description = "A totally new description"; // There is no comment on the parent rim mapping
      MappingRule newRule = new MappingRule("status");
      newRule.map = "Something.new";
      mapping.rules.add(newRule);
      doc.mappings.put(mapping.name, mapping);

      int originalMappingLength = observation.getMapping().size();
      ElementDefinition status = element(observation, "Observation.status");
      int originalStatusMappingLength = status.getMapping().size();
      Map<String, Object> originalRimMapping = (Map<String, Object>) Js.cloneDeep(findMapping(observation, "rim"));

      exporter.export();
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(originalMappingLength, observation.getMapping().size()); // No metadata added
      assertEquals(originalStatusMappingLength + 1, status.getMapping().size()); // New rule added to status element

      // New comment (Description) is added to mapping, all other metadata should be the same
      Object rimMapping = findMapping(observation, "rim");
      originalRimMapping.put("comment", "A totally new description"); // Description is added
      assertJsEqual(originalRimMapping, rimMapping);
    }

    @Test
    @DisplayName("MappingExporter #setMetadata should log an error and not add mapping or rules when a Mapping has "
        + "the same identity as one on the parent but name or uri differs")
    void shouldLogAnErrorAndNotAddMappingOrRulesWhenAMappingHasTheSameIdentityAsOneOnTheParentButNameOrUriDiffers() {
      Mapping mapping = new Mapping("rim");
      mapping.source = "MyObservation";
      mapping.id = "rim";
      mapping.title = "RIM Mapping";
      mapping.target = "http://real.org/not";
      MappingRule newRule = new MappingRule("status");
      newRule.map = "Something.new";
      mapping.rules.add(newRule);
      doc.mappings.put(mapping.name, mapping);

      int originalMappingLength = observation.getMapping().size();
      ElementDefinition status = element(observation, "Observation.status");
      int originalStatusMappingLength = status.getMapping().size();

      exporter.export();
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertEquals(originalMappingLength, observation.getMapping().size()); // No metadata added
      assertEquals(originalStatusMappingLength, status.getMapping().size()); // No rule added to status element
    }
  }

  @Nested
  @DisplayName("#setMappingRules")
  class SetMappingRules {
    Mapping mapping;

    @BeforeEach
    void beforeEach() {
      mapping = new Mapping("MyMapping");
      mapping.source = "MyObservation";
      doc.mappings.put(mapping.name, mapping);
    }

    @Test
    @DisplayName("MappingExporter #setMappingRules should apply a valid mapping rule")
    void shouldApplyAValidMappingRule() {
      MappingRule mapRule = new MappingRule("status");
      mapRule.map = "Observation.otherStatus";
      mapping.rules.add(mapRule);
      ElementDefinition status = element(observation, "Observation.status");
      int originalLength = status.getMapping().size();
      exporter.export();
      assertEquals(originalLength + 1, status.getMapping().size());
      Object exported = last(status.getMapping());
      assertEquals("Observation.otherStatus", at(exported, "map"));
      assertEquals("MyMapping", at(exported, "identity"));
    }

    @Test
    @DisplayName("MappingExporter #setMappingRules should apply a valid mapping rule with no path")
    void shouldApplyAValidMappingRuleWithNoPath() {
      MappingRule mapRule = new MappingRule("");
      mapRule.map = "OtherObservation";
      mapping.rules.add(mapRule);
      ElementDefinition baseElement = element(observation, "Observation");
      int originalLength = baseElement.getMapping().size();
      exporter.export();
      assertEquals(originalLength + 1, baseElement.getMapping().size());
      Object exported = last(baseElement.getMapping());
      assertEquals("OtherObservation", at(exported, "map"));
      assertEquals("MyMapping", at(exported, "identity"));
    }

    @Test
    @DisplayName("MappingExporter #setMappingRules should apply a valid mapping rule with a Logical source")
    void shouldApplyAValidMappingRuleWithALogicalSource() {
      mapping.source = "MyLogical";
      MappingRule mapRule = new MappingRule("name");
      mapRule.map = "Something.provider.name";
      mapping.rules.add(mapRule);
      ElementDefinition baseName = element(logical, "eLTSSServiceModel.name");
      assertNull(baseName.getMapping());
      exporter.export();
      assertEquals(1, baseName.getMapping().size());
      assertEquals("MyMapping", at(baseName.getMapping().get(0), "identity"));
      assertEquals("Something.provider.name", at(baseName.getMapping().get(0), "map"));
    }

    @Test
    @DisplayName("MappingExporter #setMappingRules should apply a valid mapping rule with a Resource source")
    void shouldApplyAValidMappingRuleWithAResourceSource() {
      mapping.source = "MyResource";
      MappingRule mapRule = new MappingRule("comparator");
      mapRule.map = "Something.operator";
      mapping.rules.add(mapRule);
      ElementDefinition baseComparator = element(resource, "Duration.comparator");
      int originalMappingLength = baseComparator.getMapping().size();
      exporter.export();
      assertEquals(originalMappingLength + 1, baseComparator.getMapping().size());
      Object exported = last(baseComparator.getMapping());
      assertEquals("MyMapping", at(exported, "identity"));
      assertEquals("Something.operator", at(exported, "map"));
    }

    @Test
    @DisplayName("MappingExporter #setMappingRules should log an error and skip rules with paths that cannot be found")
    void shouldLogAnErrorAndSkipRulesWithPathsThatCannotBeFound() {
      MappingRule fakePathRule = new MappingRule("notAPath").withFile("BadRule.fsh")
          .withLocation(new int[] {1, 2, 1, 3});
      fakePathRule.map = "whoCares";
      mapping.rules.add(fakePathRule);
      MappingRule statusRule = new MappingRule("status");
      statusRule.map = "Observation.otherStatus";
      mapping.rules.add(statusRule);
      ElementDefinition status = element(observation, "Observation.status");
      int originalLength = status.getMapping().size();
      exporter.export();
      // valid rule on status should still be applied
      assertEquals(originalLength + 1, status.getMapping().size());
      Object exported = last(status.getMapping());
      assertEquals("Observation.otherStatus", at(exported, "map"));
      assertEquals("MyMapping", at(exported, "identity"));
      assertMatches("No element found at path notAPath for MyMapping.*File: BadRule\\.fsh.*Line: 1\\D*", "s",
          loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("MappingExporter #setMappingRules should log an error and skip rules with invalid mappings")
    void shouldLogAnErrorAndSkipRulesWithInvalidMappings() {
      MappingRule noTargetRule = new MappingRule("category").withFile("BadRule.fsh")
          .withLocation(new int[] {1, 2, 1, 3});
      mapping.rules.add(noTargetRule);
      MappingRule statusRule = new MappingRule("status");
      statusRule.map = "Observation.otherStatus";
      mapping.rules.add(statusRule);
      ElementDefinition status = element(observation, "Observation.status");
      int originalLength = status.getMapping().size();
      exporter.export();
      // valid rule on status should still be applied
      assertEquals(originalLength + 1, status.getMapping().size());
      Object exported = last(status.getMapping());
      assertEquals("Observation.otherStatus", at(exported, "map"));
      assertEquals("MyMapping", at(exported, "identity"));
      assertMatches("Invalid mapping, mapping.identity and mapping.map are 1..1 and must be set.*File: BadRule\\.fsh"
          + ".*Line: 1\\D*", "s", loggerSpy.getLastMessage("error"));
    }
  }

  @Nested
  @DisplayName("#insertRules")
  class InsertRules {
    Mapping mapping;
    RuleSet ruleSet;

    @BeforeEach
    void beforeEach() {
      mapping = new Mapping("Foo");
      mapping.source = "MyObservation";
      doc.mappings.put(mapping.name, mapping);

      ruleSet = new RuleSet("Bar");
      doc.ruleSets.put(ruleSet.name, ruleSet);
    }

    @Test
    @DisplayName("MappingExporter #insertRules should apply rules from an insert rule")
    void shouldApplyRulesFromAnInsertRule() {
      MappingRule mapRule = new MappingRule("status");
      mapRule.map = "Observation.otherStatus";
      ruleSet.rules.add(mapRule);

      InsertRule insertRule = new InsertRule("");
      insertRule.ruleSet = "Bar";
      mapping.rules.add(insertRule);

      exporter.applyInsertRules();
      exporter.export();
      ElementDefinition status = element(observation, "Observation.status");
      Object exported = last(status.getMapping());
      assertEquals("Observation.otherStatus", at(exported, "map"));
      assertEquals("Foo", at(exported, "identity"));
    }

    @Test
    @DisplayName("MappingExporter #insertRules should log an error and not apply rules from an invalid insert rule")
    void shouldLogAnErrorAndNotApplyRulesFromAnInvalidInsertRule() {
      AssignmentRule valueRule = new AssignmentRule("experimental").withFile("Value.fsh")
          .withLocation(new int[] {1, 2, 3, 4});
      valueRule.value = true;
      MappingRule mapRule = new MappingRule("status");
      mapRule.map = "Observation.otherStatus";
      ruleSet.rules.add(mapRule);
      ruleSet.rules.add(valueRule);
      ruleSet.rules.add(mapRule);

      InsertRule insertRule = new InsertRule("").withFile("Insert.fsh").withLocation(new int[] {5, 6, 7, 8});
      insertRule.ruleSet = "Bar";
      mapping.rules.add(insertRule);

      exporter.applyInsertRules();
      exporter.export();
      // mapping rule is still applied
      ElementDefinition status = element(observation, "Observation.status");
      Object exported = last(status.getMapping());
      assertEquals("Observation.otherStatus", at(exported, "map"));
      assertEquals("Foo", at(exported, "identity"));
      assertMatches("AssignmentRule.*Mapping.*File: Value\\.fsh.*Line: 1 - 3.*Applied in File: Insert\\.fsh.*Applied "
          + "on Line: 5 - 7", "s", loggerSpy.getLastMessage("error"));
    }
  }
}
