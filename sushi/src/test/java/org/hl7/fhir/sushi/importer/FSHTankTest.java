package org.hl7.fhir.sushi.importer;

import static org.hl7.fhir.sushi.testhelpers.Asserts.assertToEqual;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.Metadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/import/FSHTank.test.ts (SUSHI 3.21.1)
class FSHTankTest {

  /** TS test/utils/minimalConfig.ts */
  private static Configuration minimalConfig() {
    Configuration c = new Configuration();
    c.filePath = "sushi-config.yaml";
    c.id = "fhir.us.minimal";
    c.canonical = "http://hl7.org/fhir/us/minimal";
    c.name = "MinimalIG";
    c.status = "draft";
    c.version = "1.0.0";
    c.fhirVersion = new ArrayList<>(List.of("4.0.1"));
    return c;
  }

  /** TS object literal of type Metadata. */
  private static Metadata md(Consumer<Metadata> init) {
    Metadata m = new Metadata();
    init.accept(m);
    return m;
  }

  /** TS `entity.name` */
  private static String name(FshEntity e) {
    assertNotNull(e);
    return FSHTank.nameOf(e);
  }

  /** TS `entity.id` */
  private static String id(FshEntity e) {
    assertNotNull(e);
    return FhirCommonBridge.idOf(e);
  }

  private static CaretValueRule caret(String caretPath, Object value) {
    CaretValueRule r = new CaretValueRule("");
    r.caretPath = caretPath;
    r.value = value;
    return r;
  }

  private static AssignmentRule assign(String path, Object value) {
    AssignmentRule r = new AssignmentRule(path);
    r.value = value;
    return r;
  }

  private static List<Rule> rules(Rule... rs) {
    return new ArrayList<>(List.of(rs));
  }

  @Nested
  @DisplayName("FSHTank")
  class FSHTankTests {
    FSHTank tank;

    @BeforeEach void setUp() {
      FSHDocument doc1 = new FSHDocument("doc1.fsh");
      doc1.aliases.put("FOO", "http://foo.com");
      doc1.profiles.put("Profile1", new Profile("Profile1"));
      doc1.profiles.get("Profile1").setId("prf1");
      doc1.profiles.get("Profile1").parent = "Observation";
      doc1.profiles.put("Profile2", new Profile("Profile2"));
      doc1.profiles.get("Profile2").setId("prf2");
      doc1.profiles.get("Profile2").parent = "Observation";
      Profile profile3 = new Profile("Profile3");
      profile3.parent = "Observation";
      CaretValueRule profile3Id = caret("id", "prf3");
      CaretValueRule profile3Version = caret("version", "v-2.0.0");
      profile3.rules.add(profile3Id);
      profile3.rules.add(profile3Version);
      doc1.profiles.put(profile3.name, profile3);
      doc1.instances.put("ProfileInstance", new Instance("ProfileInstance"));
      doc1.instances.get("ProfileInstance").instanceOf = "StructureDefinition";
      doc1.instances.get("ProfileInstance").usage = InstanceUsage.Definition;
      AssignmentRule profileInstanceDerivation = assign("derivation", new FshCode("constraint"));
      AssignmentRule profileInstanceParent =
          assign("baseDefinition", "http://hl7.org/fhir/StructureDefinition/Observation");
      AssignmentRule profileInstanceUrl =
          assign("url", "http://hl7.org/fhir/us/minimal/StructureDefinition/ProfileInstance");
      doc1.instances.get("ProfileInstance").rules
          .addAll(List.of(profileInstanceDerivation, profileInstanceParent, profileInstanceUrl));
      Instance profileInstance2 = new Instance("ProfileInstance2");
      profileInstance2.instanceOf = "StructureDefinition";
      profileInstance2.usage = InstanceUsage.Definition;
      AssignmentRule profileInstance2Id = assign("id", "pi2");
      AssignmentRule profileInstance2Parent =
          assign("baseDefinition", "http://hl7.org/fhir/us/minimal/StructureDefinition/ProfileInstance");
      AssignmentRule profileInstance2Url =
          assign("url", "http://hl7.org/fhir/us/minimal/StructureDefinition/ProfileInstance2");
      AssignmentRule profileInstance2Version = assign("version", "v-2.0.0");
      profileInstance2.rules.addAll(List.of(profileInstanceDerivation, profileInstance2Id, profileInstance2Parent,
          profileInstance2Url, profileInstance2Version));
      doc1.instances.put(profileInstance2.name, profileInstance2);
      doc1.extensions.put("Extension1", new Extension("Extension1"));
      doc1.extensions.get("Extension1").setId("ext1");
      doc1.extensions.get("Extension1").parent = "Extension2";
      FSHDocument doc2 = new FSHDocument("doc2.fsh");
      doc2.aliases.put("BAR", "http://bar.com");
      doc2.extensions.put("Extension2", new Extension("Extension2"));
      doc2.extensions.get("Extension2").setId("ext2");
      Extension ext3 = new Extension("Extension3");
      CaretValueRule ext3Id = caret("id", "ext3");
      CaretValueRule ext3Version = caret("version", "v-2.0.0");
      ext3.rules.addAll(List.of(ext3Id, ext3Version));
      doc2.extensions.put(ext3.name, ext3);
      doc2.instances.put("ExtensionInstance", new Instance("ExtensionInstance"));
      doc2.instances.get("ExtensionInstance").instanceOf = "StructureDefinition";
      doc2.instances.get("ExtensionInstance").usage = InstanceUsage.Definition;
      AssignmentRule extensionInstanceDerivation = assign("derivation", new FshCode("constraint"));
      AssignmentRule extensionInstanceType = assign("type", "Extension");
      doc2.instances.get("ExtensionInstance").rules.addAll(List.of(extensionInstanceDerivation, extensionInstanceType));
      Instance extensionInstance2 = new Instance("ExtensionInstance2");
      extensionInstance2.instanceOf = "StructureDefinition";
      extensionInstance2.usage = InstanceUsage.Definition;
      AssignmentRule extensionInstance2Version = assign("version", "v-2.0.0");
      extensionInstance2.rules
          .addAll(List.of(extensionInstanceDerivation, extensionInstanceType, extensionInstance2Version));
      doc2.instances.put(extensionInstance2.name, extensionInstance2);
      doc2.valueSets.put("ValueSet1", new FshValueSet("ValueSet1"));
      doc2.valueSets.get("ValueSet1").setId("vs1");
      doc2.instances.put("ValueSetInstance", new Instance("ValueSetInstance"));
      doc2.instances.get("ValueSetInstance").instanceOf = "ValueSet";
      doc2.instances.get("ValueSetInstance").usage = InstanceUsage.Definition;
      AssignmentRule valueSetInstanceUrl =
          assign("url", "http://hl7.org/fhir/us/minimal/StructureDefinition/ValueSetInstance");
      doc2.instances.get("ValueSetInstance").rules.add(valueSetInstanceUrl);
      Instance valueSetInstance2 = new Instance("ValueSetInstance2");
      valueSetInstance2.instanceOf = "ValueSet";
      valueSetInstance2.usage = InstanceUsage.Definition;
      AssignmentRule valueSetInstance2Version = assign("version", "v-2.0.0");
      valueSetInstance2.rules.add(valueSetInstance2Version);
      doc2.instances.put(valueSetInstance2.name, valueSetInstance2);
      Instance valueSetInstance4 = new Instance("ValueSetInstance4");
      valueSetInstance4.instanceOf = "ValueSet";
      valueSetInstance4.usage = InstanceUsage.Inline;
      doc2.instances.put(valueSetInstance4.name, valueSetInstance4);
      Instance valueSetInstance5 = new Instance("ValueSetInstance5");
      valueSetInstance5.instanceOf = "ValueSet";
      valueSetInstance5.usage = InstanceUsage.Example;
      doc2.instances.put(valueSetInstance5.name, valueSetInstance5);
      Instance valueSetInstance6 = new Instance("ValueSetInstance6");
      valueSetInstance6.instanceOf = "ValueSet";
      valueSetInstance6.usage = InstanceUsage.Definition;
      AssignmentRule valueSetInstance6Name = assign("name", "VS6Instance");
      valueSetInstance6.rules.add(valueSetInstance6Name);
      doc2.instances.put(valueSetInstance6.name, valueSetInstance6);
      doc2.codeSystems.put("CodeSystem1", new FshCodeSystem("CodeSystem1"));
      doc2.codeSystems.get("CodeSystem1").setId("cs1");
      doc2.instances.put("CodeSystemInstance", new Instance("CodeSystemInstance"));
      doc2.instances.get("CodeSystemInstance").instanceOf = "CodeSystem";
      doc2.instances.get("CodeSystemInstance").usage = InstanceUsage.Definition;
      AssignmentRule codeSystemInstanceUrl =
          assign("url", "http://hl7.org/fhir/us/minimal/StructureDefinition/CodeSystemInstance");
      doc2.instances.get("CodeSystemInstance").rules.add(codeSystemInstanceUrl);
      Instance codeSystemInstance2 = new Instance("CodeSystemInstance2");
      codeSystemInstance2.instanceOf = "CodeSystem";
      codeSystemInstance2.usage = InstanceUsage.Definition;
      AssignmentRule codeSystemInstance2Version = assign("version", "v-2.0.0");
      codeSystemInstance2.rules.add(codeSystemInstance2Version);
      doc2.instances.put(codeSystemInstance2.name, codeSystemInstance2);
      doc2.logicals.put("Logical1", new Logical("Logical1"));
      doc2.logicals.get("Logical1").setId("log1");
      doc2.logicals.get("Logical1").parent = "Element";
      doc2.instances.put("LogicalInstance", new Instance("LogicalInstance"));
      doc2.instances.get("LogicalInstance").instanceOf = "StructureDefinition";
      doc2.instances.get("LogicalInstance").usage = InstanceUsage.Definition;
      AssignmentRule logicalInstanceDerivation = assign("derivation", new FshCode("specialization"));
      AssignmentRule logicalInstanceKind = assign("kind", new FshCode("logical"));
      AssignmentRule logicalInstanceParent =
          assign("baseDefinition", "http://hl7.org/fhir/us/minimal/StructureDefinition/log1");
      AssignmentRule logicalInstanceUrl =
          assign("url", "http://hl7.org/fhir/us/minimal/StructureDefinition/LogicalInstance");
      doc2.instances.get("LogicalInstance").rules.addAll(
          List.of(logicalInstanceDerivation, logicalInstanceKind, logicalInstanceParent, logicalInstanceUrl));
      Instance logicalInstance2 = new Instance("LogicalInstance2");
      logicalInstance2.instanceOf = "StructureDefinition";
      logicalInstance2.usage = InstanceUsage.Definition;
      AssignmentRule logicalInstance2Version = assign("version", "v-2.0.0");
      logicalInstance2.rules.addAll(List.of(logicalInstanceDerivation, logicalInstanceKind, logicalInstance2Version));
      doc2.instances.put(logicalInstance2.name, logicalInstance2);
      doc2.resources.put("Resource1", new Resource("Resource1"));
      doc2.resources.get("Resource1").setId("res1");
      FSHDocument doc3 = new FSHDocument("doc3.fsh");
      doc3.logicals.put("Logical2", new Logical("Logical2"));
      doc3.logicals.get("Logical2").setId("log2");
      doc3.logicals.get("Logical2").parent = "Logical1";
      doc3.resources.put("Resource2", new Resource("Resource2"));
      doc3.resources.get("Resource2").setId("res2");
      doc3.instances.put("ResourceInstance", new Instance("ResourceInstance"));
      doc3.instances.get("ResourceInstance").instanceOf = "StructureDefinition";
      doc3.instances.get("ResourceInstance").usage = InstanceUsage.Definition;
      AssignmentRule resourceInstanceDerivation = assign("derivation", new FshCode("specialization"));
      AssignmentRule resourceInstanceKind = assign("kind", new FshCode("resource"));
      doc3.instances.get("ResourceInstance").rules.addAll(List.of(resourceInstanceDerivation, resourceInstanceKind));
      Instance resourceInstance2 = new Instance("ResourceInstance2");
      resourceInstance2.instanceOf = "StructureDefinition";
      resourceInstance2.usage = InstanceUsage.Definition;
      AssignmentRule resourceInstance2Version = assign("version", "v-2.0.0");
      resourceInstance2.rules
          .addAll(List.of(resourceInstanceDerivation, resourceInstanceKind, resourceInstance2Version));
      doc3.instances.put(resourceInstance2.name, resourceInstance2);
      doc3.valueSets.put("ValueSet2", new FshValueSet("ValueSet2"));
      doc3.valueSets.get("ValueSet2").setId("vs2");
      FshValueSet vs3 = new FshValueSet("ValueSet3");
      CaretValueRule vs3Id = caret("id", "vs3");
      CaretValueRule vs3Version = caret("version", "v-2.0.0");
      vs3.rules.addAll(List.of(vs3Id, vs3Version));
      doc3.valueSets.put(vs3.name, vs3);
      doc3.codeSystems.put("CodeSystem2", new FshCodeSystem("CodeSystem2"));
      doc3.codeSystems.get("CodeSystem2").setId("cs2");
      FshCodeSystem cs3 = new FshCodeSystem("CodeSystem3");
      CaretValueRule cs3Id = caret("id", "cs3");
      CaretValueRule cs3Version = caret("version", "v-2.0.0");
      cs3.rules.addAll(List.of(cs3Id, cs3Version));
      doc3.codeSystems.put(cs3.name, cs3);
      Logical log3 = new Logical("Logical3");
      CaretValueRule log3Id = caret("version", "v-2.0.0");
      CaretValueRule log3Version = caret("id", "log3");
      log3.rules.addAll(List.of(log3Id, log3Version));
      doc3.logicals.put(log3.name, log3);
      Resource res3 = new Resource("Resource3");
      CaretValueRule res3Id = caret("id", "res3");
      CaretValueRule res3Version = caret("version", "v-2.0.0");
      res3.rules.addAll(List.of(res3Id, res3Version));
      doc3.resources.put(res3.name, res3);
      doc3.instances.put("Instance1", new Instance("Instance1"));
      doc3.instances.get("Instance1").setId("inst1");
      doc3.instances.get("Instance1").instanceOf = "Condition";
      Instance inst2 = new Instance("Instance2");
      inst2.instanceOf = "Condition";
      AssignmentRule inst2Id = assign("id", "inst2");
      AssignmentRule inst2Version = assign("version", "v-2.0.0");
      inst2.rules.addAll(List.of(inst2Id, inst2Version));
      doc3.instances.put(inst2.name, inst2);
      RuleSet idRuleset = new RuleSet("IdRuleset");
      AssignmentRule idAssignment = assign("id", "inst3");
      idRuleset.rules.add(idAssignment);
      doc3.ruleSets.put(idRuleset.name, idRuleset);
      doc3.invariants.put("Invariant1", new Invariant("Invariant1"));
      doc3.invariants.get("Invariant1").description = "first invariant";
      doc3.invariants.get("Invariant1").severity = new FshCode("error");
      doc3.ruleSets.put("RuleSet1", new RuleSet("RuleSet1"));
      doc3.mappings.put("Mapping1", new Mapping("Mapping1"));
      doc3.mappings.get("Mapping1").id = "map1";
      FSHDocument doc4 = new FSHDocument("doc4.fsh");
      doc4.valueSets.put("SomeCodes", new FshValueSet("SomeCodes"));
      doc4.valueSets.get("SomeCodes").setId("some-codes");
      doc4.codeSystems.put("SomeCodes", new FshCodeSystem("SomeCodes"));
      doc4.codeSystems.get("SomeCodes").setId("some-codes");

      tank = new FSHTank(new ArrayList<>(List.of(doc1, doc2, doc3, doc4)), minimalConfig());
    }

    @Nested
    @DisplayName("#fish")
    class Fish {
      @Test @DisplayName("should find valid fish when fishing by id for all types")
      void byIdAllTypes() {
        assertEquals("Profile1", name(tank.fish("prf1")));
        assertEquals("Extension1", name(tank.fish("ext1")));
        assertEquals("ValueSet1", name(tank.fish("vs1")));
        assertEquals("CodeSystem1", name(tank.fish("cs1")));
        assertEquals("Instance1", name(tank.fish("inst1")));
        assertEquals("Logical1", name(tank.fish("log1")));
        assertEquals("Resource1", name(tank.fish("res1")));
        // not applicable for Invariant or RuleSet or Mapping
      }

      @Test @DisplayName("should find valid fish when fishing by name for all types")
      void byNameAllTypes() {
        assertEquals("prf2", id(tank.fish("Profile2")));
        assertEquals("ext2", id(tank.fish("Extension2")));
        assertEquals("vs2", id(tank.fish("ValueSet2")));
        assertEquals("cs2", id(tank.fish("CodeSystem2")));
        assertEquals("inst1", id(tank.fish("Instance1")));
        assertEquals("Invariant1", name(tank.fish("Invariant1")));
        assertEquals("RuleSet1", name(tank.fish("RuleSet1")));
        assertEquals("Mapping1", name(tank.fish("Mapping1")));
        assertEquals("log2", id(tank.fish("Logical2")));
        assertEquals("res2", id(tank.fish("Resource2")));
      }

      @Test @DisplayName("should find valid fish when fishing by url for all types")
      void byUrlAllTypes() {
        assertEquals("Profile1", name(tank.fish("http://hl7.org/fhir/us/minimal/StructureDefinition/prf1")));
        assertEquals("Extension1", name(tank.fish("http://hl7.org/fhir/us/minimal/StructureDefinition/ext1")));
        assertEquals("ValueSet1", name(tank.fish("http://hl7.org/fhir/us/minimal/ValueSet/vs1")));
        assertEquals("CodeSystem1", name(tank.fish("http://hl7.org/fhir/us/minimal/CodeSystem/cs1")));
        assertEquals("Logical1", name(tank.fish("http://hl7.org/fhir/us/minimal/StructureDefinition/log1")));
        assertEquals("Resource2", name(tank.fish("http://hl7.org/fhir/us/minimal/StructureDefinition/res2")));
        // not applicable for Instance or Invariant or RuleSet or Mapping
      }

      @Test @DisplayName("should find valid fish by id, name, or url after the rules change")
      void afterRulesChange() {
        assertEquals("CodeSystem1", name(tank.fish("cs1")));
        assertNull(tank.fish("new-cs1"));
        assertNull(tank.fish("http://foo.org/CodeSystem/new-cs1"));
        assertNull(tank.fish("NewCodeSystem1"));
        FshCodeSystem cs1 = (FshCodeSystem) tank.fish("cs1");
        CaretValueRule idRule = caret("id", "new-cs1");
        CaretValueRule urlRule = caret("url", "http://foo.org/CodeSystem/new-cs1");
        CaretValueRule nameRule = caret("name", "NewCodeSystem1");
        cs1.rules.addAll(List.of(idRule, urlRule, nameRule));
        assertNull(tank.fish("cs1", FishableType.CodeSystem));
        assertEquals("CodeSystem1", name(tank.fish("new-cs1")));
        assertEquals("CodeSystem1", name(tank.fish("http://foo.org/CodeSystem/new-cs1")));
        assertEquals("CodeSystem1", name(tank.fish("NewCodeSystem1")));
        cs1.rules = new ArrayList<>();
        assertEquals("CodeSystem1", name(tank.fish("cs1")));
        assertNull(tank.fish("new-cs1"));
      }

      @Test @DisplayName("should not find fish when fishing by invalid name/id/url")
      void invalid() {
        assertNull(tank.fish("ProfileFake"));
      }

      @Test @DisplayName("should only find profiles when profiles are requested")
      void onlyProfiles() {
        assertEquals("Profile1", name(tank.fish("prf1", FishableType.Profile)));
        assertNull(tank.fish("prf1", FishableType.Extension, FishableType.ValueSet, FishableType.CodeSystem,
            FishableType.Instance, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should find profiles defined as instance definitions when profiles are requested")
      void profileInstances() {
        assertEquals("ProfileInstance", name(tank.fish("ProfileInstance", FishableType.Profile)));
        assertNull(tank.fish("ProfileInstance", FishableType.Extension, FishableType.ValueSet,
            FishableType.CodeSystem, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should not find instances where the derivation is not constraint when profiles are requested")
      void profileInstanceWrongDerivation() {
        Instance instance = tank.docs.get(0).instances.get("ProfileInstance");
        AssignmentRule profileInstanceDerivation = assign("derivation", new FshCode("specialization"));
        instance.rules = rules(profileInstanceDerivation);
        assertNull(tank.fish("ProfileInstance", FishableType.Profile));
        instance.rules = rules();
        assertNull(tank.fish("ProfileInstance", FishableType.Profile));
      }

      @Test @DisplayName("should find a profile when fishing by id when the profile id is set by a caret rule")
      void profileCaretId() {
        assertEquals("Profile3", name(tank.fish("prf3")));
      }

      @Test @DisplayName("should find a profile when fishing with a version")
      void profileVersion() {
        assertEquals("Profile1", name(tank.fish("prf1|1.0.0", FishableType.Profile))); // tank.config.version = 1.0.0
      }

      @Test @DisplayName("should find a profile when fishing with a version that is set by a caret rule")
      void profileCaretVersion() {
        assertEquals("Profile3", name(tank.fish("prf3|v-2.0.0", FishableType.Profile)));
      }

      @Test @DisplayName("should not find a profile when fishing with a version that does not match")
      void profileVersionMismatch() {
        assertNull(tank.fish("prf1|2.0.0", FishableType.Profile));
      }

      @Test @DisplayName("should find a profile defined as an instance definition when fishing with a version")
      void profileInstanceVersion() {
        assertEquals("ProfileInstance", name(tank.fish("ProfileInstance|1.0.0", FishableType.Profile)));
      }

      @Test @DisplayName("should find a profile defined as an instance definition when fishing with a version that is set by an assignment rule")
      void profileInstanceAssignedVersion() {
        assertEquals("ProfileInstance2", name(tank.fish("ProfileInstance2|v-2.0.0", FishableType.Profile)));
      }

      @Test @DisplayName("should not find a profile defined as an instance definition when fishing with a version that does not match")
      void profileInstanceVersionMismatch() {
        assertNull(tank.fish("ProfileInstance|2.0.0", FishableType.Profile));
      }

      @Test @DisplayName("should only find extensions when extensions are requested")
      void onlyExtensions() {
        assertEquals("Extension1", name(tank.fish("ext1", FishableType.Extension)));
        assertNull(tank.fish("ext1", FishableType.Profile, FishableType.ValueSet, FishableType.CodeSystem,
            FishableType.Instance, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should find extensions defined as instance definitions when extensions are requested")
      void extensionInstances() {
        assertEquals("ExtensionInstance", name(tank.fish("ExtensionInstance", FishableType.Extension)));
        assertNull(tank.fish("ExtensionInstance", FishableType.Profile, FishableType.ValueSet,
            FishableType.CodeSystem, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should not find instances where the type is not Extension when extensions are requested")
      void extensionInstanceWrongType() {
        Instance instance = tank.docs.get(1).instances.get("ExtensionInstance");
        AssignmentRule extensionInstanceType = assign("type", "Observation");
        AssignmentRule extensionInstanceDerivation = assign("derivation", new FshCode("specialization"));
        instance.rules = rules(extensionInstanceType, extensionInstanceDerivation);
        assertNull(tank.fish("ExtensionInstance", FishableType.Extension));
        instance.rules = rules();
        assertNull(tank.fish("ExtensionInstance", FishableType.Extension));
      }

      @Test @DisplayName("should not find instances where the derivation is not constraint when extensions are requested")
      void extensionInstanceWrongDerivation() {
        Instance instance = tank.docs.get(1).instances.get("ExtensionInstance");
        AssignmentRule extensionInstanceType = assign("type", "Extension");
        AssignmentRule extensionInstanceDerivation = assign("derivation", new FshCode("specialization"));
        instance.rules = rules(extensionInstanceType, extensionInstanceDerivation);
        assertNull(tank.fish("ExtensionInstance", FishableType.Extension));
        instance.rules = rules();
        assertNull(tank.fish("ExtensionInstance", FishableType.Extension));
      }

      @Test @DisplayName("should find an extension when fishing by id when the extension id is set by a caret rule")
      void extensionCaretId() {
        assertEquals("Extension3", name(tank.fish("ext3")));
      }

      @Test @DisplayName("should find an extension when fishing with a version")
      void extensionVersion() {
        assertEquals("Extension1", name(tank.fish("ext1|1.0.0", FishableType.Extension))); // tank.config.version = 1.0.0
      }

      @Test @DisplayName("should find an extension when fishing with a version that is set by a caret rule")
      void extensionCaretVersion() {
        assertEquals("Extension3", name(tank.fish("ext3|v-2.0.0", FishableType.Extension)));
      }

      @Test @DisplayName("should not find an extension when fishing with a version that does not match")
      void extensionVersionMismatch() {
        assertNull(tank.fish("ext1|2.0.0", FishableType.Extension));
      }

      @Test @DisplayName("should find an extension defined as an instance definition when fishing with a version")
      void extensionInstanceVersion() {
        assertEquals("ExtensionInstance", name(tank.fish("ExtensionInstance|1.0.0", FishableType.Extension)));
      }

      @Test @DisplayName("should find an extension defined as an instance definition when fishing with a version that is set by an assignment rule")
      void extensionInstanceAssignedVersion() {
        assertEquals("ExtensionInstance2", name(tank.fish("ExtensionInstance2|v-2.0.0", FishableType.Extension)));
      }

      @Test @DisplayName("should not find an extension defined as an instance definition when fishing with a version that does not match")
      void extensionInstanceVersionMismatch() {
        assertNull(tank.fish("ExtensionInstance|2.0.0", FishableType.Extension));
      }

      @Test @DisplayName("should find an extension defined as an instance definition when fishing by its default url")
      void extensionInstanceDefaultUrl() {
        assertEquals("ExtensionInstance", name(
            tank.fish("http://hl7.org/fhir/us/minimal/StructureDefinition/ExtensionInstance", FishableType.Extension)));
      }

      @Test @DisplayName("should only find logical models when logical models are requested")
      void onlyLogicals() {
        assertEquals("Logical1", name(tank.fish("log1", FishableType.Logical)));
        assertNull(tank.fish("log1", FishableType.Extension, FishableType.Profile, FishableType.ValueSet,
            FishableType.CodeSystem, FishableType.Instance, FishableType.Invariant, FishableType.RuleSet,
            FishableType.Mapping, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should find logical models defined as instance definitions when logical models are requested")
      void logicalInstances() {
        assertEquals("LogicalInstance", name(tank.fish("LogicalInstance", FishableType.Logical)));
        assertNull(tank.fish("LogicalInstance", FishableType.Extension, FishableType.Profile, FishableType.ValueSet,
            FishableType.CodeSystem, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should find a logical model when fishing by id when the logical model id is set by a caret rule")
      void logicalCaretId() {
        assertEquals("Logical3", name(tank.fish("log3")));
      }

      @Test @DisplayName("should find a logical when fishing with a version")
      void logicalVersion() {
        assertEquals("Logical1", name(tank.fish("log1|1.0.0", FishableType.Logical))); // tank.config.version = 1.0.0
      }

      @Test @DisplayName("should find a logical when fishing with a version that is set by a caret rule")
      void logicalCaretVersion() {
        assertEquals("Logical3", name(tank.fish("log3|v-2.0.0", FishableType.Logical)));
      }

      @Test @DisplayName("should not find a logical when fishing with a version that does not match")
      void logicalVersionMismatch() {
        assertNull(tank.fish("log1|2.0.0", FishableType.Logical));
      }

      @Test @DisplayName("should find a logical defined as an instance definition when fishing with a version")
      void logicalInstanceVersion() {
        assertEquals("LogicalInstance", name(tank.fish("LogicalInstance|1.0.0", FishableType.Logical)));
      }

      @Test @DisplayName("should find a logical defined as an instance definition when fishing with a version that is set by an assignment rule")
      void logicalInstanceAssignedVersion() {
        assertEquals("LogicalInstance2", name(tank.fish("LogicalInstance2|v-2.0.0", FishableType.Logical)));
      }

      @Test @DisplayName("should not find a logical defined as an instance definition when fishing with a version that does not match")
      void logicalInstanceVersionMismatch() {
        assertNull(tank.fish("LogicalInstance|2.0.0", FishableType.Logical));
      }

      @Test @DisplayName("should only find resources when resources are requested")
      void onlyResources() {
        assertEquals("Resource2", name(tank.fish("res2", FishableType.Resource)));
        assertNull(tank.fish("res2", FishableType.Extension, FishableType.Profile, FishableType.ValueSet,
            FishableType.CodeSystem, FishableType.Instance, FishableType.Invariant, FishableType.RuleSet,
            FishableType.Mapping, FishableType.Logical, FishableType.Type));
      }

      @Test @DisplayName("should find resources defined as instance definitions when resources are requested")
      void resourceInstances() {
        assertEquals("ResourceInstance", name(tank.fish("ResourceInstance", FishableType.Resource)));
        assertNull(tank.fish("ResourceInstance", FishableType.Extension, FishableType.Profile, FishableType.ValueSet,
            FishableType.CodeSystem, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Type));
      }

      @Test @DisplayName("should find a resource when fishing by id when the resource id is set by a caret rule")
      void resourceCaretId() {
        assertEquals("Resource3", name(tank.fish("res3")));
      }

      @Test @DisplayName("should find a resource when fishing with a version")
      void resourceVersion() {
        assertEquals("Resource1", name(tank.fish("res1|1.0.0", FishableType.Resource))); // tank.config.version = 1.0.0
      }

      @Test @DisplayName("should find a resource when fishing with a version that is set by a caret rule")
      void resourceCaretVersion() {
        assertEquals("Resource3", name(tank.fish("res3|v-2.0.0", FishableType.Resource)));
      }

      @Test @DisplayName("should not find a resource when fishing with a version that does not match")
      void resourceVersionMismatch() {
        assertNull(tank.fish("res1|2.0.0", FishableType.Resource));
      }

      @Test @DisplayName("should find a resource defined as an instance definition when fishing with a version")
      void resourceInstanceVersion() {
        assertEquals("ResourceInstance", name(tank.fish("ResourceInstance|1.0.0", FishableType.Resource)));
      }

      @Test @DisplayName("should find a resource defined as an instance definition when fishing with a version that is set by an assignment rule")
      void resourceInstanceAssignedVersion() {
        assertEquals("ResourceInstance2", name(tank.fish("ResourceInstance2|v-2.0.0", FishableType.Resource)));
      }

      @Test @DisplayName("should not find a resource defined as an instance definition when fishing with a version that does not match")
      void resourceInstanceVersionMismatch() {
        assertNull(tank.fish("ResourceInstance|2.0.0", FishableType.Resource));
      }

      @Test @DisplayName("should only find valuesets when valuesets are requested")
      void onlyValueSets() {
        assertEquals("ValueSet1", name(tank.fish("vs1", FishableType.ValueSet)));
        assertNull(tank.fish("vs1", FishableType.Profile, FishableType.Extension, FishableType.CodeSystem,
            FishableType.Instance, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should find valuesets defined as instance definitions when valuesets are requested")
      void valueSetInstances() {
        assertEquals("ValueSetInstance", name(tank.fish("ValueSetInstance", FishableType.ValueSet)));
        assertNull(tank.fish("ValueSetInstance", FishableType.Profile, FishableType.Extension,
            FishableType.CodeSystem, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should find valuesets defined as inline instances when valuesets are requested")
      void valueSetInlineInstances() {
        assertEquals("ValueSetInstance4", name(tank.fish("ValueSetInstance4", FishableType.ValueSet)));
        assertNull(tank.fish("ValueSetInstance4", FishableType.Profile, FishableType.Extension,
            FishableType.CodeSystem, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should not find valuesets defined as example instances when valuesets are requested")
      void valueSetExampleInstances() {
        assertNull(tank.fish("ValueSetInstance5", FishableType.ValueSet));
      }

      @Test @DisplayName("should find a valueset when fishing by id when the valueset id is set by a caret rule")
      void valueSetCaretId() {
        assertEquals("ValueSet3", name(tank.fish("vs3")));
      }

      @Test @DisplayName("should find a valueset when fishing with a version")
      void valueSetVersion() {
        assertEquals("ValueSet1", name(tank.fish("vs1|1.0.0", FishableType.ValueSet))); // tank.config.version = 1.0.0
      }

      @Test @DisplayName("should find a valueset when fishing with a version that is set by a caret rule")
      void valueSetCaretVersion() {
        assertEquals("ValueSet3", name(tank.fish("vs3|v-2.0.0", FishableType.ValueSet)));
      }

      @Test @DisplayName("should not find a valueset when fishing with a version that does not match")
      void valueSetVersionMismatch() {
        assertNull(tank.fish("vs1|2.0.0", FishableType.ValueSet));
      }

      @Test @DisplayName("should find a valueset defined as an instance definition when fishing with a version")
      void valueSetInstanceVersion() {
        assertEquals("ValueSetInstance", name(tank.fish("ValueSetInstance|1.0.0", FishableType.ValueSet)));
      }

      @Test @DisplayName("should find a valueset defined as an instance definition when fishing with a version that is set by an assignment rule")
      void valueSetInstanceAssignedVersion() {
        assertEquals("ValueSetInstance2", name(tank.fish("ValueSetInstance2|v-2.0.0", FishableType.ValueSet)));
      }

      @Test @DisplayName("should not find a valueset defined as an instance definition when fishing with a version that does not match")
      void valueSetInstanceVersionMismatch() {
        assertNull(tank.fish("ValueSetInstance|2.0.0", FishableType.ValueSet));
      }

      @Test @DisplayName("should find a valueset defined as an instance definition when fishing by its default url")
      void valueSetInstanceDefaultUrl() {
        assertEquals("ValueSetInstance2",
            name(tank.fish("http://hl7.org/fhir/us/minimal/ValueSet/ValueSetInstance2", FishableType.ValueSet)));
      }

      @Test @DisplayName("should not find a valueset defined as an instance definition when fishing by a StructureDefinition url")
      void valueSetInstanceSdUrl() {
        assertNull(
            tank.fish("http://hl7.org/fhir/us/minimal/StructureDefinition/ValueSetInstance2", FishableType.ValueSet));
      }

      @Test @DisplayName("should only find codesystems when codesystems are requested")
      void onlyCodeSystems() {
        assertEquals("CodeSystem1", name(tank.fish("cs1", FishableType.CodeSystem)));
        assertNull(tank.fish("cs1", FishableType.Profile, FishableType.Extension, FishableType.ValueSet,
            FishableType.Instance, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should find codesystems defined as instance definitions when codesystems are requested")
      void codeSystemInstances() {
        assertEquals("CodeSystemInstance", name(tank.fish("CodeSystemInstance", FishableType.CodeSystem)));
        assertNull(tank.fish("CodeSystemInstance", FishableType.Profile, FishableType.Extension,
            FishableType.ValueSet, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should find a codesystem when fishing by id when the codesystem id is set by a caret rule")
      void codeSystemCaretId() {
        assertEquals("CodeSystem3", name(tank.fish("cs3")));
      }

      @Test @DisplayName("should find a codesystem when fishing with a version")
      void codeSystemVersion() {
        assertEquals("CodeSystem1", name(tank.fish("cs1|1.0.0", FishableType.CodeSystem))); // tank.config.version = 1.0.0
      }

      @Test @DisplayName("should find a codesystem when fishing with a version that is set by a caret rule")
      void codeSystemCaretVersion() {
        assertEquals("CodeSystem3", name(tank.fish("cs3|v-2.0.0", FishableType.CodeSystem)));
      }

      @Test @DisplayName("should not find a codesystem when fishing with a version that does not match")
      void codeSystemVersionMismatch() {
        assertNull(tank.fish("cs1|2.0.0", FishableType.CodeSystem));
      }

      @Test @DisplayName("should find a codesystem defined as an instance definition when fishing with a version")
      void codeSystemInstanceVersion() {
        assertEquals("CodeSystemInstance", name(tank.fish("CodeSystemInstance|1.0.0", FishableType.CodeSystem)));
      }

      @Test @DisplayName("should find a codesystem defined as an instance definition when fishing with a version that is set by an assignment rule")
      void codeSystemInstanceAssignedVersion() {
        assertEquals("CodeSystemInstance2",
            name(tank.fish("CodeSystemInstance2|v-2.0.0", FishableType.CodeSystem)));
      }

      @Test @DisplayName("should not find a codesystem defined as an instance definition when fishing with a version that does not match")
      void codeSystemInstanceVersionMismatch() {
        assertNull(tank.fish("CodeSystemInstance|2.0.0", FishableType.CodeSystem));
      }

      @Test @DisplayName("should find a codesystem defined as an instance definition when fishing by its default url")
      void codeSystemInstanceDefaultUrl() {
        assertEquals("CodeSystemInstance2",
            name(tank.fish("http://hl7.org/fhir/us/minimal/CodeSystem/CodeSystemInstance2", FishableType.CodeSystem)));
      }

      @Test @DisplayName("should not find a codesystem defined as an instance definition when fishing by a StructureDefinition url")
      void codeSystemInstanceSdUrl() {
        assertNull(tank.fish("http://hl7.org/fhir/us/minimal/StructureDefinition/CodeSystemInstance2",
            FishableType.CodeSystem));
      }

      @Test @DisplayName("should only find instances when instances are requested")
      void onlyInstances() {
        assertEquals("Instance1", name(tank.fish("inst1", FishableType.Instance)));
        assertNull(tank.fish("inst1", FishableType.Profile, FishableType.Extension, FishableType.ValueSet,
            FishableType.CodeSystem, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should find an instance when fishing by id when the instance id is set by an assignment rule")
      void instanceAssignedId() {
        assertEquals("Instance2", name(tank.fish("inst2")));
      }

      @Test @DisplayName("should find an instance when fishing by name when the instance name is set by an assignment rule")
      void instanceAssignedName() {
        assertEquals("ValueSetInstance6", name(tank.fish("VS6Instance")));
      }

      @Test @DisplayName("should find an instance when fishing with a version")
      void instanceVersion() {
        assertEquals("Instance1", name(tank.fish("inst1|1.0.0", FishableType.Instance))); // tank.config.version = 1.0.0
      }

      @Test @DisplayName("should find an instance when fishing with a version that is set by an assignment rule")
      void instanceAssignedVersion() {
        assertEquals("Instance2", name(tank.fish("inst2|v-2.0.0", FishableType.Instance)));
      }

      @Test @DisplayName("should not find an instance when fishing with a version that does not match")
      void instanceVersionMismatch() {
        assertNull(tank.fish("inst1|2.0.0", FishableType.Instance));
      }

      @Test @DisplayName("should only find invariants when invariants are requested")
      void onlyInvariants() {
        assertEquals("Invariant1", name(tank.fish("Invariant1", FishableType.Invariant)));
        assertNull(tank.fish("Invariant1", FishableType.Profile, FishableType.Extension, FishableType.ValueSet,
            FishableType.CodeSystem, FishableType.Instance, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should only find ruleSets when ruleSets are requested")
      void onlyRuleSets() {
        assertEquals("RuleSet1", name(tank.fish("RuleSet1", FishableType.RuleSet)));
        assertNull(tank.fish("RuleSet1", FishableType.Profile, FishableType.Extension, FishableType.ValueSet,
            FishableType.CodeSystem, FishableType.Instance, FishableType.Invariant, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should find ruleSets even if fishing with a version")
      void ruleSetsWithVersion() {
        // NOTE: this doesn't make much sense and wouldn't happen in real FSH, but testing for completeness
        assertEquals("RuleSet1", name(tank.fish("RuleSet1|any", FishableType.RuleSet)));
      }

      @Test @DisplayName("should only find Mappings when Mappings are requested")
      void onlyMappings() {
        assertEquals("Mapping1", name(tank.fish("Mapping1", FishableType.Mapping)));
        assertNull(tank.fish("Mapping1", FishableType.Profile, FishableType.Extension, FishableType.ValueSet,
            FishableType.CodeSystem, FishableType.Instance, FishableType.Invariant, FishableType.RuleSet,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should find Mappings even if fishing with a version")
      void mappingsWithVersion() {
        // NOTE: this doesn't make much sense and wouldn't happen in real FSH, but testing for completeness
        assertEquals("Mapping1", name(tank.fish("Mapping1|any", FishableType.Mapping)));
      }
    }

    @Nested
    @DisplayName("#fishAll")
    class FishAll {
      @Test @DisplayName("should return all matches when there are multiple matches")
      void multipleMatches() {
        List<FshEntity> results = tank.fishAll("SomeCodes");
        assertEquals(2, results.size());
        assertInstanceOf(FshValueSet.class, results.get(0));
        assertEquals("some-codes", id(results.get(0)));
        assertInstanceOf(FshCodeSystem.class, results.get(1));
        assertEquals("some-codes", id(results.get(1)));
        assertToEqual(results, tank.fishAll("some-codes"));
        assertToEqual(results.subList(0, 1), tank.fishAll("http://hl7.org/fhir/us/minimal/ValueSet/some-codes"));
        assertToEqual(results.subList(1, 2), tank.fishAll("http://hl7.org/fhir/us/minimal/CodeSystem/some-codes"));
      }

      @Test @DisplayName("should return one match when there is a single match")
      void singleMatch() {
        List<FshEntity> results = tank.fishAll("Profile2");
        assertEquals(1, results.size());
        assertEquals("prf2", id(results.get(0)));
        assertToEqual(results, tank.fishAll("prf2"));
        assertToEqual(results, tank.fishAll("http://hl7.org/fhir/us/minimal/StructureDefinition/prf2"));
      }

      @Test @DisplayName("should return empty array when there are no matches")
      void noMatches() {
        List<FshEntity> results = tank.fishAll("NonExistentItem");
        assertTrue(results.isEmpty());
      }
    }

    @Nested
    @DisplayName("#fishForMetadata")
    class FishForMetadata {
      final Metadata prf1MD = md(m -> {
        m.id = "prf1";
        m.name = "Profile1";
        m.url = "http://hl7.org/fhir/us/minimal/StructureDefinition/prf1";
        m.version = "1.0.0";
        m.parent = "Observation";
        m.resourceType = "StructureDefinition";
      });
      final Metadata ext1MD = md(m -> {
        m.id = "ext1";
        m.name = "Extension1";
        m.url = "http://hl7.org/fhir/us/minimal/StructureDefinition/ext1";
        m.version = "1.0.0";
        m.parent = "Extension2";
        m.resourceType = "StructureDefinition";
      });
      final Metadata log1MD = md(m -> {
        m.id = "log1";
        m.name = "Logical1";
        m.url = "http://hl7.org/fhir/us/minimal/StructureDefinition/log1";
        m.version = "1.0.0";
        m.sdType = "http://hl7.org/fhir/us/minimal/StructureDefinition/log1";
        m.parent = "Element";
        m.resourceType = "StructureDefinition";
        m.canBeTarget = false;
        m.canBind = false;
      });
      final Metadata res1MD = md(m -> {
        m.id = "res1";
        m.name = "Resource1";
        m.parent = "DomainResource";
        m.url = "http://hl7.org/fhir/us/minimal/StructureDefinition/res1";
        m.version = "1.0.0";
        m.resourceType = "StructureDefinition";
      });
      final Metadata vs1MD = md(m -> {
        m.id = "vs1";
        m.name = "ValueSet1";
        m.url = "http://hl7.org/fhir/us/minimal/ValueSet/vs1";
        m.version = "1.0.0";
        m.resourceType = "ValueSet";
      });
      final Metadata cs1MD = md(m -> {
        m.id = "cs1";
        m.name = "CodeSystem1";
        m.url = "http://hl7.org/fhir/us/minimal/CodeSystem/cs1";
        m.version = "1.0.0";
        m.resourceType = "CodeSystem";
      });
      final Metadata inst1MD = md(m -> {
        m.id = "inst1";
        m.name = "Instance1";
        m.version = "1.0.0";
        m.instanceUsage = "Example";
      });
      final Metadata inv1MD = md(m -> {
        m.id = "Invariant1"; // id will always be name on Invariants
        m.name = "Invariant1";
      });
      final Metadata rul1MD = md(m -> {
        m.id = "RuleSet1"; // id will always be name for RuleSets
        m.name = "RuleSet1";
      });
      final Metadata map1MD = md(m -> {
        m.id = "map1";
        m.name = "Mapping1";
      });

      @Test @DisplayName("should find valid fish metadata when fishing by id for all types")
      void byIdAllTypes() {
        assertToEqual(prf1MD, tank.fishForMetadata("prf1"));
        assertToEqual(ext1MD, tank.fishForMetadata("ext1"));
        assertToEqual(log1MD, tank.fishForMetadata("log1"));
        assertToEqual(res1MD, tank.fishForMetadata("res1"));
        assertToEqual(vs1MD, tank.fishForMetadata("vs1"));
        assertToEqual(cs1MD, tank.fishForMetadata("cs1"));
        assertToEqual(inst1MD, tank.fishForMetadata("inst1"));
        // not applicable for Invariant or RuleSet or Mapping
      }

      @Test @DisplayName("should find valid fish when fishing by name for all types")
      void byNameAllTypes() {
        assertToEqual(prf1MD, tank.fishForMetadata("Profile1"));
        assertToEqual(ext1MD, tank.fishForMetadata("Extension1"));
        assertToEqual(log1MD, tank.fishForMetadata("Logical1"));
        assertToEqual(res1MD, tank.fishForMetadata("Resource1"));
        assertToEqual(vs1MD, tank.fishForMetadata("ValueSet1"));
        assertToEqual(cs1MD, tank.fishForMetadata("CodeSystem1"));
        assertToEqual(inst1MD, tank.fishForMetadata("Instance1"));
        assertToEqual(inv1MD, tank.fishForMetadata("Invariant1"));
        assertToEqual(rul1MD, tank.fishForMetadata("RuleSet1"));
        assertToEqual(map1MD, tank.fishForMetadata("Mapping1"));
      }

      @Test @DisplayName("should find valid fish when fishing by url for all types")
      void byUrlAllTypes() {
        assertToEqual(prf1MD, tank.fishForMetadata("http://hl7.org/fhir/us/minimal/StructureDefinition/prf1"));
        assertToEqual(ext1MD, tank.fishForMetadata("http://hl7.org/fhir/us/minimal/StructureDefinition/ext1"));
        assertToEqual(log1MD, tank.fishForMetadata("http://hl7.org/fhir/us/minimal/StructureDefinition/log1"));
        assertToEqual(res1MD, tank.fishForMetadata("http://hl7.org/fhir/us/minimal/StructureDefinition/res1"));
        assertToEqual(vs1MD, tank.fishForMetadata("http://hl7.org/fhir/us/minimal/ValueSet/vs1"));
        assertToEqual(cs1MD, tank.fishForMetadata("http://hl7.org/fhir/us/minimal/CodeSystem/cs1"));
        // not applicable for Instance or Invariant or RuleSet or Mapping
      }

      @Test @DisplayName("should find valid fish metadata for definitions defined as instances")
      void definitionsAsInstances() {
        assertToEqual(md(m -> {
          m.instanceUsage = "Definition";
          m.id = "ProfileInstance";
          m.name = "ProfileInstance";
          m.url = "http://hl7.org/fhir/us/minimal/StructureDefinition/ProfileInstance";
          m.version = "1.0.0";
          m.parent = "Observation";
          m.resourceType = "StructureDefinition";
        }), tank.fishForMetadata("ProfileInstance"));
        assertToEqual(md(m -> {
          m.instanceUsage = "Definition";
          m.id = "pi2";
          m.name = "ProfileInstance2";
          m.url = "http://hl7.org/fhir/us/minimal/StructureDefinition/ProfileInstance2";
          m.version = "v-2.0.0";
          m.parent = "http://hl7.org/fhir/us/minimal/StructureDefinition/ProfileInstance";
          m.resourceType = "StructureDefinition";
        }), tank.fishForMetadata("ProfileInstance2"));
        assertToEqual(md(m -> {
          m.instanceUsage = "Definition";
          m.id = "LogicalInstance";
          m.name = "LogicalInstance";
          m.url = "http://hl7.org/fhir/us/minimal/StructureDefinition/LogicalInstance";
          m.version = "1.0.0";
          m.sdType = "http://hl7.org/fhir/us/minimal/StructureDefinition/LogicalInstance";
          m.parent = "http://hl7.org/fhir/us/minimal/StructureDefinition/log1";
          m.resourceType = "StructureDefinition";
        }), tank.fishForMetadata("LogicalInstance"));
        assertToEqual(md(m -> {
          m.instanceUsage = "Definition";
          m.id = "ValueSetInstance";
          m.name = "ValueSetInstance";
          m.url = "http://hl7.org/fhir/us/minimal/StructureDefinition/ValueSetInstance";
          m.version = "1.0.0";
          m.resourceType = "ValueSet";
        }), tank.fishForMetadata("ValueSetInstance"));
        assertToEqual(md(m -> {
          m.instanceUsage = "Definition";
          m.id = "CodeSystemInstance";
          m.name = "CodeSystemInstance";
          m.url = "http://hl7.org/fhir/us/minimal/StructureDefinition/CodeSystemInstance";
          m.version = "1.0.0";
          m.resourceType = "CodeSystem";
        }), tank.fishForMetadata("CodeSystemInstance"));
      }

      @Test @DisplayName("should find fsh imposeProfiles when they are declared using indexed rules")
      void imposeProfilesIndexed() {
        Profile profile = new Profile("ImposeProfile1");
        profile.setId("ip1");
        profile.parent = "Patient";
        addIndexedImposeProfileExtension(profile, "http://example.org/imposedProfileA", 0);
        addIndexedImposeProfileExtension(profile, "http://example.org/imposedProfileB", 1);
        FSHDocument doc4 = new FSHDocument("doc4.fsh");
        doc4.profiles.put(profile.name, profile);
        tank.docs.add(doc4);

        assertToEqual(md(m -> {
          m.id = "ip1";
          m.name = "ImposeProfile1";
          m.url = "http://hl7.org/fhir/us/minimal/StructureDefinition/ip1";
          m.version = "1.0.0";
          m.parent = "Patient";
          m.resourceType = "StructureDefinition";
          m.imposeProfiles =
              new ArrayList<>(List.of("http://example.org/imposedProfileA", "http://example.org/imposedProfileB"));
        }), tank.fishForMetadata("ImposeProfile1"));
      }

      @Test @DisplayName("should find fsh imposeProfiles when they are declared using indexed rules and default 0 index")
      void imposeProfilesIndexedDefault0() {
        Profile profile = new Profile("ImposeProfile2");
        profile.setId("ip2");
        profile.parent = "Patient";
        addIndexedImposeProfileExtension(profile, "http://example.org/imposedProfileA", null);
        addIndexedImposeProfileExtension(profile, "http://example.org/imposedProfileB", 1);
        FSHDocument doc4 = new FSHDocument("doc4.fsh");
        doc4.profiles.put(profile.name, profile);
        tank.docs.add(doc4);

        assertToEqual(md(m -> {
          m.id = "ip2";
          m.name = "ImposeProfile2";
          m.url = "http://hl7.org/fhir/us/minimal/StructureDefinition/ip2";
          m.version = "1.0.0";
          m.parent = "Patient";
          m.resourceType = "StructureDefinition";
          m.imposeProfiles =
              new ArrayList<>(List.of("http://example.org/imposedProfileA", "http://example.org/imposedProfileB"));
        }), tank.fishForMetadata("ImposeProfile2"));
      }

      @Test @DisplayName("should find fsh imposeProfiles when they are declared using named rules")
      void imposeProfilesNamed() {
        Profile profile = new Profile("ImposeProfile3");
        profile.setId("ip3");
        profile.parent = "Patient";
        addNamedImposeProfileExtension(profile, "http://example.org/imposedProfileA");
        addNamedImposeProfileExtension(profile, "http://example.org/imposedProfileB", 1);
        FSHDocument doc4 = new FSHDocument("doc4.fsh");
        doc4.profiles.put(profile.name, profile);
        tank.docs.add(doc4);

        assertToEqual(md(m -> {
          m.id = "ip3";
          m.name = "ImposeProfile3";
          m.url = "http://hl7.org/fhir/us/minimal/StructureDefinition/ip3";
          m.version = "1.0.0";
          m.parent = "Patient";
          m.resourceType = "StructureDefinition";
          m.imposeProfiles =
              new ArrayList<>(List.of("http://example.org/imposedProfileA", "http://example.org/imposedProfileB"));
        }), tank.fishForMetadata("ImposeProfile3"));
      }

      @Test @DisplayName("should find fsh imposeProfiles when they are declared using named rules with aliases")
      void imposeProfilesNamedAliases() {
        Profile profile = new Profile("ImposeProfile4");
        profile.setId("ip4");
        profile.parent = "Patient";
        addNamedImposeProfileExtension(profile, "http://example.org/imposedProfileA", 0, "$IMPOSE");
        addNamedImposeProfileExtension(profile, "http://example.org/imposedProfileB", 1, "$IMPOSE");
        FSHDocument doc4 = new FSHDocument("doc4.fsh");
        doc4.aliases.put("$IMPOSE", "http://hl7.org/fhir/StructureDefinition/structuredefinition-imposeProfile");
        doc4.profiles.put(profile.name, profile);
        tank.docs.add(doc4);

        assertToEqual(md(m -> {
          m.id = "ip4";
          m.name = "ImposeProfile4";
          m.url = "http://hl7.org/fhir/us/minimal/StructureDefinition/ip4";
          m.version = "1.0.0";
          m.parent = "Patient";
          m.resourceType = "StructureDefinition";
          m.imposeProfiles =
              new ArrayList<>(List.of("http://example.org/imposedProfileA", "http://example.org/imposedProfileB"));
        }), tank.fishForMetadata("ImposeProfile4"));
      }

      @Test @DisplayName("should find fsh imposeProfiles when they are declared using the Canonical keyword")
      void imposeProfilesCanonical() {
        FSHDocument doc4 = new FSHDocument("doc4.fsh");
        Profile imposedProfileA = new Profile("ImposedProfileA");
        imposedProfileA.setId("ipA");
        imposedProfileA.parent = "Patient";
        doc4.profiles.put(imposedProfileA.name, imposedProfileA);
        Profile imposedProfileB = new Profile("ImposedProfileB");
        imposedProfileB.setId("ipB");
        imposedProfileB.parent = "Patient";
        doc4.profiles.put(imposedProfileB.name, imposedProfileB);
        Profile profile = new Profile("ImposeProfile5");
        profile.setId("ip5");
        profile.parent = "Patient";
        addIndexedImposeProfileExtension(profile, new FshCanonical("ImposedProfileA"), 0);
        addIndexedImposeProfileExtension(profile, new FshCanonical("ImposedProfileB"), 1);
        doc4.profiles.put(profile.name, profile);
        tank.docs.add(doc4);

        assertToEqual(md(m -> {
          m.id = "ip5";
          m.name = "ImposeProfile5";
          m.url = "http://hl7.org/fhir/us/minimal/StructureDefinition/ip5";
          m.version = "1.0.0";
          m.parent = "Patient";
          m.resourceType = "StructureDefinition";
          m.imposeProfiles =
              new ArrayList<>(List.of(new FshCanonical("ImposedProfileA"), new FshCanonical("ImposedProfileB")));
        }), tank.fishForMetadata("ImposeProfile5"));
      }

      @Test @DisplayName("should not find fish when fishing by invalid name/id/url")
      void invalid() {
        assertNull(tank.fishForMetadata("ProfileFake"));
      }

      @Test @DisplayName("should only find profiles when profiles are requested")
      void onlyProfiles() {
        assertToEqual(prf1MD, tank.fishForMetadata("prf1", FishableType.Profile));
        assertNull(tank.fishForMetadata("prf1", FishableType.Extension, FishableType.ValueSet,
            FishableType.CodeSystem, FishableType.Instance, FishableType.Invariant, FishableType.RuleSet,
            FishableType.Mapping, FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should only find extensions when extensions are requested")
      void onlyExtensions() {
        assertToEqual(ext1MD, tank.fishForMetadata("ext1", FishableType.Extension));
        assertNull(tank.fishForMetadata("ext1", FishableType.Profile, FishableType.ValueSet, FishableType.CodeSystem,
            FishableType.Instance, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should only find logical models when logical models are requested")
      void onlyLogicals() {
        assertToEqual(log1MD, tank.fishForMetadata("log1", FishableType.Logical));
        assertNull(tank.fishForMetadata("log1", FishableType.Extension, FishableType.Profile, FishableType.ValueSet,
            FishableType.CodeSystem, FishableType.Instance, FishableType.Invariant, FishableType.RuleSet,
            FishableType.Mapping, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should only find resources when resources are requested")
      void onlyResources() {
        assertToEqual(res1MD, tank.fishForMetadata("res1", FishableType.Resource));
        assertNull(tank.fishForMetadata("res1", FishableType.Extension, FishableType.Profile, FishableType.ValueSet,
            FishableType.CodeSystem, FishableType.Instance, FishableType.Invariant, FishableType.RuleSet,
            FishableType.Mapping, FishableType.Logical, FishableType.Type));
      }

      @Test @DisplayName("should only find valuesets when valuesets are requested")
      void onlyValueSets() {
        assertToEqual(vs1MD, tank.fishForMetadata("vs1", FishableType.ValueSet));
        assertNull(tank.fishForMetadata("vs1", FishableType.Profile, FishableType.Extension, FishableType.CodeSystem,
            FishableType.Instance, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should only find codesystems when codesystems are requested")
      void onlyCodeSystems() {
        assertToEqual(cs1MD, tank.fishForMetadata("cs1", FishableType.CodeSystem));
        assertNull(tank.fishForMetadata("cs1", FishableType.Profile, FishableType.Extension, FishableType.ValueSet,
            FishableType.Instance, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should only find instances when instances are requested")
      void onlyInstances() {
        assertToEqual(inst1MD, tank.fishForMetadata("inst1", FishableType.Instance));
        assertNull(tank.fishForMetadata("inst1", FishableType.Profile, FishableType.Extension, FishableType.ValueSet,
            FishableType.CodeSystem, FishableType.Invariant, FishableType.RuleSet, FishableType.Mapping,
            FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should only find invariants when invariants are requested")
      void onlyInvariants() {
        assertToEqual(inv1MD, tank.fishForMetadata("Invariant1", FishableType.Invariant));
        assertNull(tank.fishForMetadata("Invariant1", FishableType.Profile, FishableType.Extension,
            FishableType.ValueSet, FishableType.CodeSystem, FishableType.Instance, FishableType.RuleSet,
            FishableType.Mapping, FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should only find ruleSets when ruleSets are requested")
      void onlyRuleSets() {
        assertToEqual(rul1MD, tank.fishForMetadata("RuleSet1", FishableType.RuleSet));
        assertNull(tank.fishForMetadata("RuleSet1", FishableType.Profile, FishableType.Extension,
            FishableType.ValueSet, FishableType.CodeSystem, FishableType.Instance, FishableType.Invariant,
            FishableType.Mapping, FishableType.Logical, FishableType.Resource, FishableType.Type));
      }

      @Test @DisplayName("should only find Mappings when Mappings are requested")
      void onlyMappings() {
        assertToEqual(map1MD, tank.fishForMetadata("Mapping1", FishableType.Mapping));
        assertNull(tank.fishForMetadata("Mapping1", FishableType.Profile, FishableType.Extension,
            FishableType.ValueSet, FishableType.CodeSystem, FishableType.Instance, FishableType.Invariant,
            FishableType.RuleSet, FishableType.Logical, FishableType.Resource, FishableType.Type));
      }
    }

    @Nested
    @DisplayName("#fishForMetadatas")
    class FishForMetadatas {
      @Test @DisplayName("should return all matches when there are multiple matches")
      void multipleMatches() {
        List<Metadata> results = tank.fishForMetadatas("SomeCodes");
        assertEquals(2, results.size());
        assertToEqual(List.of(md(m -> {
          m.id = "some-codes";
          m.name = "SomeCodes";
          m.url = "http://hl7.org/fhir/us/minimal/ValueSet/some-codes";
          m.version = "1.0.0";
          m.resourceType = "ValueSet";
        }), md(m -> {
          m.id = "some-codes";
          m.name = "SomeCodes";
          m.url = "http://hl7.org/fhir/us/minimal/CodeSystem/some-codes";
          m.version = "1.0.0";
          m.resourceType = "CodeSystem";
        })), results);
        assertToEqual(results, tank.fishForMetadatas("some-codes"));
        assertToEqual(results.subList(0, 1),
            tank.fishForMetadatas("http://hl7.org/fhir/us/minimal/ValueSet/some-codes"));
        assertToEqual(results.subList(1, 2),
            tank.fishForMetadatas("http://hl7.org/fhir/us/minimal/CodeSystem/some-codes"));
      }

      @Test @DisplayName("should return one match when there is a single match")
      void singleMatch() {
        List<Metadata> results = tank.fishForMetadatas("Logical1");
        assertEquals(1, results.size());
        assertToEqual(md(m -> {
          m.id = "log1";
          m.name = "Logical1";
          m.url = "http://hl7.org/fhir/us/minimal/StructureDefinition/log1";
          m.version = "1.0.0";
          m.sdType = "http://hl7.org/fhir/us/minimal/StructureDefinition/log1";
          m.parent = "Element";
          m.resourceType = "StructureDefinition";
          m.canBeTarget = false;
          m.canBind = false;
        }), results.get(0));
        assertToEqual(results, tank.fishForMetadatas("log1"));
        assertToEqual(results, tank.fishForMetadatas("http://hl7.org/fhir/us/minimal/StructureDefinition/log1"));
      }

      @Test @DisplayName("should return empty array when there are no matches")
      void noMatches() {
        List<Metadata> results = tank.fishForMetadatas("NonExistentItem");
        assertTrue(results.isEmpty());
      }
    }

    @Nested
    @DisplayName("#fishForFHIR")
    class FishForFHIR {
      @Test @DisplayName("should never return any results for fishForFHIR")
      void neverReturns() {
        assertNull(tank.fishForFHIR("prf1"));
        assertNull(tank.fishForFHIR("prf1", FishableType.Profile));
        assertNull(tank.fishForFHIR("Profile2"));
        assertNull(tank.fishForFHIR("http://hl7.org/fhir/us/minimal/StructureDefinition/prf1"));
        assertNull(tank.fishForFHIR("ext1"));
        assertNull(tank.fishForFHIR("ext1", FishableType.Extension));
        assertNull(tank.fishForFHIR("Extension2"));
        assertNull(tank.fishForFHIR("http://hl7.org/fhir/us/minimal/StructureDefinition/ext1"));
        assertNull(tank.fishForFHIR("log1"));
        assertNull(tank.fishForFHIR("log1", FishableType.Logical));
        assertNull(tank.fishForFHIR("Logical2"));
        assertNull(tank.fishForFHIR("http://hl7.org/fhir/us/minimal/StructureDefinition/log1"));
        assertNull(tank.fishForFHIR("ref1"));
        assertNull(tank.fishForFHIR("ref1", FishableType.Resource));
        assertNull(tank.fishForFHIR("Reference2"));
        assertNull(tank.fishForFHIR("http://hl7.org/fhir/us/minimal/StructureDefinition/ref1"));
        assertNull(tank.fishForFHIR("vs1"));
        assertNull(tank.fishForFHIR("vs1", FishableType.ValueSet));
        assertNull(tank.fishForFHIR("ValueSet2"));
        assertNull(tank.fishForFHIR("http://hl7.org/fhir/us/minimal/ValueSet/vs1"));
        assertNull(tank.fishForFHIR("cs1"));
        assertNull(tank.fishForFHIR("cs1", FishableType.CodeSystem));
        assertNull(tank.fishForFHIR("CodeSystem2"));
        assertNull(tank.fishForFHIR("http://hl7.org/fhir/us/minimal/ValueSet/vs1"));
        assertNull(tank.fishForFHIR("inst1"));
        assertNull(tank.fishForFHIR("inst1", FishableType.Instance));
        assertNull(tank.fishForFHIR("Instance1"));
        assertNull(tank.fishForFHIR("Invariant1"));
        assertNull(tank.fishForFHIR("Invariant1", FishableType.Invariant));
        assertNull(tank.fishForFHIR("RuleSet1"));
        assertNull(tank.fishForFHIR("RuleSet1", FishableType.RuleSet));
        assertNull(tank.fishForFHIR("map1"));
        assertNull(tank.fishForFHIR("map1", FishableType.Mapping));
        assertNull(tank.fishForFHIR("Mapping1"));
      }
    }
  }

  @Nested
  @DisplayName("FSHTank for HL7")
  class FSHTankForHL7 {
    Configuration hl7Config() {
      Configuration c = new Configuration();
      c.filePath = "sushi-config.yaml";
      c.id = "hl7.fhir";
      c.canonical = "http://hl7.org/fhir";
      c.name = "HL7";
      c.status = "draft";
      c.version = "4.9.0";
      c.fhirVersion = new ArrayList<>(List.of("4.9.0"));
      c.template = "hl7.fhir.template#0.0.5";
      return c;
    }

    FSHTank hl7Tank;

    @BeforeEach void setUp() {
      FSHDocument hl7Doc = new FSHDocument("HL7Doc.fsh");
      hl7Doc.logicals.put("HL7Logical", new Logical("HL7Logical"));
      hl7Doc.logicals.get("HL7Logical").setId("hl7-log");
      hl7Doc.logicals.get("HL7Logical").parent = "Element";

      hl7Tank = new FSHTank(new ArrayList<>(List.of(hl7Doc)), hl7Config());
    }

    @Nested
    @DisplayName("#fishForMetadata")
    class FishForMetadata {
      final Metadata hl7logMD = md(m -> {
        m.id = "hl7-log";
        m.name = "HL7Logical";
        m.version = "4.9.0";
        m.url = "http://hl7.org/fhir/StructureDefinition/hl7-log";
        m.sdType = "hl7-log";
        m.parent = "Element";
        m.resourceType = "StructureDefinition";
        m.canBeTarget = false;
        m.canBind = false;
      });

      @Test @DisplayName("should find valid HL7 fish metadata when fishing by id for logical models")
      void hl7LogicalById() {
        assertToEqual(hl7logMD, hl7Tank.fishForMetadata("hl7-log"));
      }
    }
  }

  /** TS addIndexedImposeProfileExtension(profile, imposedProfile, index?); index null = TS undefined. */
  private static void addIndexedImposeProfileExtension(Profile profile, Object imposedProfile, Integer index) {
    String extension = index != null ? "extension[" + index + "]" : "extension";
    CaretValueRule extensionUrlRule = caret(extension + ".url",
        "http://hl7.org/fhir/StructureDefinition/structuredefinition-imposeProfile");
    CaretValueRule extensionValueRule = caret(extension + ".valueCanonical", imposedProfile);
    profile.rules.addAll(List.of(extensionUrlRule, extensionValueRule));
  }

  private static void addNamedImposeProfileExtension(Profile profile, Object imposedProfile) {
    addNamedImposeProfileExtension(profile, imposedProfile, 0);
  }

  private static void addNamedImposeProfileExtension(Profile profile, Object imposedProfile, int index) {
    addNamedImposeProfileExtension(profile, imposedProfile, index,
        "http://hl7.org/fhir/StructureDefinition/structuredefinition-imposeProfile");
  }

  private static void addNamedImposeProfileExtension(Profile profile, Object imposedProfile, int index,
      String name) {
    String extension = "extension[" + name + "]" + (index > 0 ? "[" + index + "]" : "");
    CaretValueRule extensionRule = caret(extension + ".valueCanonical", imposedProfile);
    profile.rules.add(extensionRule);
  }
}
