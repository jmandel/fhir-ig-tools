package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.export.ExportTestUtil.*;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getTestFHIRDefinitions;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.*;
import org.hl7.fhir.sushi.export.Package.FshMapEntry;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirtypes.*;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.js.*;
import org.hl7.fhir.sushi.testhelpers.*;
import org.hl7.fhir.sushi.utils.*;
import org.junit.jupiter.api.*;

// port of sushi/test/export/InstanceExporter.test.ts (SUSHI 3.21.1)
@DisplayName("InstanceExporter")
class InstanceExporterTest {
  static TestFHIRDefinitions defs;
  FSHDocument doc;
  FSHTank tank;
  Package pkg;
  TestFisher fisher;
  StructureDefinitionExporter sdExporter;
  CodeSystemExporter csExporter;
  ValueSetExporter vsExporter;
  InstanceExporter exporter;
  java.util.function.Function<Instance, InstanceDefinition> exportInstance;

  @BeforeAll
  static void beforeAll() {
    defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
  }

  @BeforeEach
  void beforeEach() {
    loggerSpy.reset();
    loggerSpy.reset();
    doc = new FSHDocument("fileName");
    tank = new FSHTank(new ArrayList<>(Arrays.asList(doc)), MinimalConfig.minimalConfig());
    pkg = new Package(tank.config);
    fisher = new TestFisher(tank, defs, pkg);
    sdExporter = new StructureDefinitionExporter(tank, pkg, fisher);
    csExporter = new CodeSystemExporter(tank, pkg, fisher);
    vsExporter = new ValueSetExporter(tank, pkg, fisher);
    exporter = new InstanceExporter(tank, pkg, fisher);
    exportInstance = (instance) -> {
  csExporter.export();
  vsExporter.export();
  sdExporter.export();
  return exporter.exportInstance(instance);
};
  }

  @Test
  @DisplayName("InstanceExporter should output empty results with empty input")
  void shouldOutputEmptyResultsWithEmptyInput() {
    List<InstanceDefinition> exported = exporter.export().instances;
    assertJsonEqual("[]", exported);
  }

  @Test
  @DisplayName("InstanceExporter should export a single instance")
  void shouldExportASingleInstance() {
    Instance instance = new Instance("MyInstance");
    instance.instanceOf = "Patient";
    doc.instances.put(instance.name, instance);
    List<InstanceDefinition> exported = exporter.export().instances;
    assertEquals(1, exported.size());
  }

  @Test
  @DisplayName("InstanceExporter should add source info for the exported instance to the package")
  void shouldAddSourceInfoForTheExportedInstanceToThePackage() {
    Instance instance = new Instance("MyInstance").withFile("Examples.fsh").withLocation(new int[] {25, 3, 52, 33});
    instance.instanceOf = "Patient";
    doc.instances.put(instance.name, instance);
    List<InstanceDefinition> exported = exporter.export().instances;
    assertEquals(1, exported.size());
    assertJsonEqual("""
        {
          "file": "Examples.fsh",
          "location": {
            "startLine": 25,
            "startColumn": 3,
            "endLine": 52,
            "endColumn": 33
          },
          "fshName": "MyInstance",
          "fshType": "Instance"
        }
        """, fshMapJson(pkg.fshMap.get("Patient-MyInstance.json")));
  }

  @Test
  @DisplayName("InstanceExporter should export multiple instances")
  void shouldExportMultipleInstances() {
    Instance instanceFoo = new Instance("Foo");
    instanceFoo.instanceOf = "Patient";
    Instance instanceBar = new Instance("Bar");
    instanceBar.instanceOf = "Patient";
    doc.instances.put(instanceFoo.name, instanceFoo);
    doc.instances.put(instanceBar.name, instanceBar);
    List<InstanceDefinition> exported = exporter.export().instances;
    assertEquals(2, exported.size());
  }

  @Test
  @DisplayName("InstanceExporter should still export instance if one fails")
  void shouldStillExportInstanceIfOneFails() {
    Instance instanceFoo = new Instance("Foo");
    instanceFoo.instanceOf = "Baz";
    Instance instanceBar = new Instance("Bar");
    instanceBar.instanceOf = "Patient";
    doc.instances.put(instanceFoo.name, instanceFoo);
    doc.instances.put(instanceBar.name, instanceBar);
    List<InstanceDefinition> exported = exporter.export().instances;
    assertEquals(1, exported.size());
    assertEquals("Bar", exported.get(0)._instanceMeta.name);
  }

  @Test
  @DisplayName("InstanceExporter should log a message with source information when the parent is not found")
  void shouldLogAMessageWithSourceInformationWhenTheParentIsNotFound() {
    Instance instance = new Instance("Bogus").withFile("Bogus.fsh").withLocation(new int[] {2, 9, 4, 23});
    instance.instanceOf = "BogusParent";
    doc.instances.put(instance.name, instance);
    exporter.export();
    assertMatches("File: Bogus\\.fsh.*Line: 2 - 4\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("InstanceExporter should log a message with source information when the instanceOf is an abstract specialization")
  void shouldLogAMessageWithSourceInformationWhenTheInstanceOfIsAnAbstractSpecializatio() {
    Instance instance = new Instance("MyAbstractInstance").withFile("Abstract.fsh").withLocation(new int[] {16, 1, 20, 27});
    instance.instanceOf = "DomainResource";
    doc.instances.put(instance.name, instance);
    List<InstanceDefinition> exported = exporter.export().instances;
    assertEquals(0, exported.size());
    assertMatches("File: Abstract\\.fsh.*Line: 16 - 20\\D*", "s", loggerSpy.getLastMessage("error"));
    assertMatches("The definition for MyAbstractInstance is an instance of an abstract resource: DomainResource", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("InstanceExporter should log a message with source information when the instanceOf is a profile whose nearest specialization is abstract")
  void shouldLogAMessageWithSourceInformationWhenTheInstanceOfIsAProfileWhoseNearestSpe() {
    Profile firstProfile = new Profile("ProfileOfSomethingAbstract");
    firstProfile.parent = "DomainResource";
    doc.profiles.put(firstProfile.name, firstProfile);
    Profile secondProfile = new Profile("SecondaryProfile");
    secondProfile.parent = "ProfileOfSomethingAbstract";
    doc.profiles.put(secondProfile.name, secondProfile);
    sdExporter.export();
    Instance instance = new Instance("MyAbstractInstance").withFile("Abstract.fsh").withLocation(new int[] {17, 1, 23, 26});
    instance.instanceOf = "SecondaryProfile";
    doc.instances.put(instance.name, instance);
    List<InstanceDefinition> exported = exporter.export().instances;
    assertEquals(0, exported.size());
    assertMatches("File: Abstract\\.fsh.*Line: 17 - 23\\D*", "s", loggerSpy.getLastMessage("error"));
    assertMatches("The definition for MyAbstractInstance is an instance of an abstract resource: DomainResource", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("InstanceExporter should warn when title and/or description is an empty string")
  void shouldWarnWhenTitleAndOrDescriptionIsAnEmptyString() {
    Instance instance = new Instance("MyInstance");
    instance.instanceOf = "Patient";
    instance.title = "";
    instance.description = "";
    doc.instances.put(instance.name, instance);
    List<InstanceDefinition> exported = exporter.export().instances;
    assertEquals(1, exported.size());
    assertEquals(2, loggerSpy.getAllMessages("warn").size());
    assertContainsText("Instance MyInstance has a title field that should not be empty.", loggerSpy.getFirstMessage("warn"));
    assertContainsText("Instance MyInstance has a description field that should not be empty.", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("InstanceExporter should export instances with InstanceOf FSHy profile")
  void shouldExportInstancesWithInstanceOfFSHyProfile() {
    Profile profileFoo = new Profile("Foo");
    profileFoo.parent = "Patient";
    Instance instanceBar = new Instance("Bar");
    instanceBar.instanceOf = "Foo";
    doc.profiles.put(profileFoo.name, profileFoo);
    doc.instances.put(instanceBar.name, instanceBar);
    sdExporter.export();
    List<InstanceDefinition> exported = exporter.export().instances;
    assertEquals(1, exported.size());
    assertEquals("Bar", exported.get(0)._instanceMeta.name);
    assertEquals("Patient", at(exported.get(0), "resourceType"));
  }

  @Test
  @DisplayName("InstanceExporter should assign values on an instance")
  void shouldAssignValuesOnAnInstance() {
    Instance instance = new Instance("Bar");
    instance.instanceOf = "Patient";
    AssignmentRule assignedValRule = new AssignmentRule("gender");
    FshCode assignedFshCode = new FshCode("foo", "http://foo.com");
    assignedValRule.value = assignedFshCode;
    instance.rules.add(assignedValRule);
    doc.instances.put(instance.name, instance);
    List<InstanceDefinition> exported = exporter.export().instances;
    assertEquals(1, exported.size());
    assertEquals("foo", at(exported.get(0), "gender"));
  }

  @DisplayName("#exportInstance")
  @Nested
  class ExportInstance {
    Profile questionnaire;
    Profile patient;
    Profile respRate;
    Profile patientProf;
    Profile bundle;
    Profile communicationProf;
    Instance patientInstance;
    Instance patientProfInstance;
    Instance lipidInstance;
    Instance valueSetInstance;
    Instance respRateInstance;
    Instance bundleInstance;
    Instance carePlanInstance;
    Instance communicationInstance;
    Instance provenanceInstance;
    Instance patientInstance100;
    Instance patientInstance100x;
    Instance patientInstanceUsc;
    Instance patientInstance100MetaId;
    Instance patientInstance100MetaProfile1;
    Instance patientInstance100MetaProfiles;
    Instance patientInstanceUsc310;
    Instance patientInstanceUsc400;

    @BeforeEach
    void beforeEach() {
      questionnaire = new Profile("TestQuestionnaire");
      questionnaire.parent = "Questionnaire";
      doc.profiles.put(questionnaire.name, questionnaire);
      patient = new Profile("TestPatient");
      patient.parent = "Patient";
      doc.profiles.put(patient.name, patient);
      patientProf = new Profile("TestPatientProf");
      patientProf.parent = "patient-proficiency";
      doc.profiles.put(patientProf.name, patientProf);
      respRate = new Profile("TestRespRate");
      respRate.parent = "resprate";
      doc.profiles.put(respRate.name, respRate);
      bundle = new Profile("TestBundle");
      bundle.parent = "Bundle";
      doc.profiles.put(bundle.name, bundle);
      communicationProf = new Profile("TestCommunication");
      communicationProf.parent = "Communication";
      doc.profiles.put(communicationProf.name, communicationProf);
      patientInstance = new Instance("Bar").withFile("PatientInstance.fsh").withLocation(new int[] {10, 1, 20, 30});
      patientInstance.instanceOf = "TestPatient";
      doc.instances.put(patientInstance.name, patientInstance);
      patientProfInstance = new Instance("Baz");
      patientProfInstance.instanceOf = "TestPatientProf";
      doc.instances.put(patientProfInstance.name, patientProfInstance);
      carePlanInstance = new Instance("C");
      carePlanInstance.instanceOf = "CarePlan";
      doc.instances.put(carePlanInstance.name, carePlanInstance);
      AssignmentRule carePlanStatus = new AssignmentRule("status");
      carePlanStatus.value = new FshCode("draft");
      AssignmentRule carePlanIntent = new AssignmentRule("intent");
      carePlanIntent.value = new FshCode("plan");
      AssignmentRule carePlanSubject = new AssignmentRule("subject");
      carePlanSubject.value = new FshReference("Patient/fake");
      carePlanInstance.rules.addAll(Arrays.asList(carePlanStatus, carePlanIntent, carePlanSubject));
      lipidInstance = new Instance("Bam").withFile("LipidInstance.fsh").withLocation(new int[] {10, 1, 20, 30});
      lipidInstance.instanceOf = "lipidprofile";
      doc.instances.put(lipidInstance.name, lipidInstance);
      valueSetInstance = new Instance("Boom");
      valueSetInstance.instanceOf = "ValueSet";
      doc.instances.put(valueSetInstance.name, valueSetInstance);
      respRateInstance = new Instance("Bang");
      respRateInstance.instanceOf = "TestRespRate";
      doc.instances.put(respRateInstance.name, respRateInstance);
      bundleInstance = new Instance("Pow");
      bundleInstance.instanceOf = "TestBundle";
      doc.instances.put(bundleInstance.name, bundleInstance);
      communicationInstance = new Instance("CommunicationInstance");
      communicationInstance.instanceOf = "TestCommunication";
      doc.instances.put(communicationInstance.name, communicationInstance);
      provenanceInstance = new Instance("MyProvenance").withFile("ProvenanceInstance.fsh").withLocation(new int[] {10, 1, 20, 30});
      provenanceInstance.instanceOf = "Provenance";
      AssignmentRule prvRecordedRule = new AssignmentRule("recorded");
      prvRecordedRule.value = "2015-06-27T08:39:24+10:00";
      AssignmentRule prvWhoRule = new AssignmentRule("agent.who");
      prvWhoRule.value = new FshReference("Practitioner/Bob");
      provenanceInstance.rules = new ArrayList<>(Arrays.asList(prvRecordedRule, prvWhoRule));
      doc.instances.put(provenanceInstance.name, provenanceInstance);
      // Issue #1559 Bug Fix
      patientInstance100 = new Instance("Biz100").withFile("PatientInstance.fsh").withLocation(new int[] {10, 1, 20, 30});
      patientInstance100.instanceOf = "TestPatient";
      doc.instances.put(patientInstance100.name, patientInstance100);
      patientInstance100x = new Instance("BizBad").withFile("PatientInstance.fsh").withLocation(new int[] {10, 1, 20, 30});
      patientInstance100x.instanceOf = "TestPatient|unknown|version";
      doc.instances.put(patientInstance100x.name, patientInstance100x);
      patientInstanceUsc = new Instance("BizUSCore").withFile("PatientInstance.fsh").withLocation(new int[] {10, 1, 20, 30});
      patientInstanceUsc.instanceOf = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|3.1.0";
      doc.instances.put(patientInstanceUsc.name, patientInstanceUsc);
      patientInstance100MetaId = new Instance("Biz100MetaId").withFile("PatientInstance.fsh").withLocation(new int[] {10, 1, 20, 30});
      patientInstance100MetaId.instanceOf = "TestPatient";
      AssignmentRule assignmentMetaIdRule = new AssignmentRule("meta.id");
      assignmentMetaIdRule.value = "ID.Biz100-0001";
      patientInstance100MetaId.rules.add(assignmentMetaIdRule);
      doc.instances.put(patientInstance100MetaId.name, patientInstance100MetaId);
      patientInstance100MetaProfile1 = new Instance("Biz100MetaProfile1").withFile("PatientInstance.fsh").withLocation(new int[] {10, 1, 20, 30});
      patientInstance100MetaProfile1.instanceOf = "TestPatient";
      AssignmentRule assignmentMetaProfile1Rule = new AssignmentRule("meta.profile[+]");
      assignmentMetaProfile1Rule.value = new FshCanonical("http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatientProf");
      patientInstance100MetaProfile1.rules.add(assignmentMetaProfile1Rule);
      doc.instances.put(patientInstance100MetaProfile1.name, patientInstance100MetaProfile1);
      patientInstance100MetaProfiles = new Instance("Biz100MetaProfiles").withFile("PatientInstance.fsh").withLocation(new int[] {10, 1, 20, 30});
      patientInstance100MetaProfiles.instanceOf = "TestPatient";
      AssignmentRule assignmentMetaProfilesRule1 = new AssignmentRule("meta.profile[+]");
      assignmentMetaProfilesRule1.value = new FshCanonical("http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatientProf");
      patientInstance100MetaProfiles.rules.add(assignmentMetaProfilesRule1);
      AssignmentRule assignmentMetaProfilesRule2 = new AssignmentRule("meta.profile[+]");
      assignmentMetaProfilesRule2.value = new FshCanonical("http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient");
      setProp(assignmentMetaProfilesRule2.value, "version", "3.1.0");
      patientInstance100MetaProfiles.rules.add(assignmentMetaProfilesRule2);
      doc.instances.put(patientInstance100MetaProfiles.name, patientInstance100MetaProfiles);
      patientInstanceUsc310 = new Instance("BizUSCore310").withFile("PatientInstance.fsh").withLocation(new int[] {10, 1, 20, 30});
      patientInstanceUsc310.instanceOf = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|3.1.0";
      doc.instances.put(patientInstanceUsc310.name, patientInstanceUsc310);
      patientInstanceUsc400 = new Instance("BizUSCore400").withFile("PatientInstance.fsh").withLocation(new int[] {10, 1, 20, 30});
      patientInstanceUsc400.instanceOf = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|4.0.0";
      doc.instances.put(patientInstanceUsc400.name, patientInstanceUsc400);
    }

    // Setting resourceType
    @Test
    @DisplayName("InstanceExporter #exportInstance should set resourceType to the base resource type we are making an instance of")
    void shouldSetResourceTypeToTheBaseResourceTypeWeAreMakingAnInstanceOf() {
      Instance boo = new Instance("Boo");
      boo.instanceOf = "Patient";
      InstanceDefinition exported = exportInstance.apply(boo);
      assertEquals("Patient", at(exported, "resourceType"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set resourceType to the base resource type for the profile we are making an instance of")
    void shouldSetResourceTypeToTheBaseResourceTypeForTheProfileWeAreMakingAnInstanceOf() {
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals("Patient", at(exported, "resourceType"));
    }

    // Setting Metadata
    @Test
    @DisplayName("InstanceExporter #exportInstance should set meta.profile to the defining profile URL we are making an instance of")
    void shouldSetMetaProfileToTheDefiningProfileURLWeAreMakingAnInstanceOf() {
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "profile": [
            "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient"
          ]
        }
        """, at(exported, "meta"));
    }

    @DisplayName("Issue #1559 Bug Fix")
    @Nested
    class Issue1559BugFix {

      @Test
      @DisplayName("InstanceExporter #exportInstance Issue #1559 Bug Fix should throw Error when requested version is not in scope")
      void shouldThrowErrorWhenRequestedVersionIsNotInScope() {
        Runnable t = () -> {
  exportInstance.apply(patientInstance100x);
};
        assertThrows(InstanceOfNotDefinedError.class, t::run);
        assertThrowsMessage("InstanceOf TestPatient|unknown|version not found for BizBad", t::run);
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance Issue #1559 Bug Fix should set meta.profile (non-existent meta) to the defining profile canonical URL with profile name and canonical version")
      void shouldSetMetaProfileNonExistentMetaToTheDefiningProfileCanonicalURLWithProfileNa() {
        // patientInstance100.instanceOf = `TestPatient`;
        InstanceDefinition exported100 = exportInstance.apply(patientInstance100);
        assertJsonEqual("""
        {
          "profile": [
            "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient"
          ]
        }
        """, at(exported100, "meta"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance Issue #1559 Bug Fix should set meta.profile (non-existent meta) to the defining profile canonical URL with version")
      void shouldSetMetaProfileNonExistentMetaToTheDefiningProfileCanonicalURLWithVersion() {
        // patientInstanceUsc.instanceOf = 'http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|3.1.0';
        InstanceDefinition exported = exportInstance.apply(patientInstanceUsc);
        assertJsonEqual("""
        {
          "profile": [
            "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|3.1.0"
          ]
        }
        """, at(exported, "meta"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance Issue #1559 Bug Fix should set meta.profile (only meta.id) to the defining profile URL with canonical version")
      void shouldSetMetaProfileOnlyMetaIdToTheDefiningProfileURLWithCanonicalVersion() {
        // patientInstance100MetaId.instanceOf = 'TestPatient';
        InstanceDefinition exported = exportInstance.apply(patientInstance100MetaId);
        assertJsonEqual("""
        {
          "id": "ID.Biz100-0001",
          "profile": [
            "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient"
          ]
        }
        """, at(exported, "meta"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance Issue #1559 Bug Fix should set meta.profile (single meta.profile) to the defining profile URL with canonical version")
      void shouldSetMetaProfileSingleMetaProfileToTheDefiningProfileURLWithCanonicalVersion() {
        // patientInstance100MetaProfile1.instanceOf = 'TestPatient';
        InstanceDefinition exported = exportInstance.apply(patientInstance100MetaProfile1);
        assertJsonEqual("""
        {
          "profile": [
            "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient",
            "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatientProf"
          ]
        }
        """, at(exported, "meta"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance Issue #1559 Bug Fix should set meta.profile (multiple meta.profile) to the defining profile URL with canonical version")
      void shouldSetMetaProfileMultipleMetaProfileToTheDefiningProfileURLWithCanonicalVersi() {
        // patientInstance100MetaProfiles.instanceOf = 'TestPatient';
        InstanceDefinition exported = exportInstance.apply(patientInstance100MetaProfiles);
        assertJsonEqual("""
        {
          "profile": [
            "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient",
            "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatientProf",
            "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|3.1.0"
          ]
        }
        """, at(exported, "meta"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance Issue #1559 Bug Fix should set meta.profile (non-existent meta) to the proper profile canonical URL with version for which there are two different versions of the profile in scope")
      void shouldSetMetaProfileNonExistentMetaToTheProperProfileCanonicalURLWithVersionForW() {
        // patientInstanceUsc310.instanceOf = 'http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|3.1.0';
        // patientInstanceUsc400.instanceOf = 'http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|4.0.0';
        InstanceDefinition exported = exportInstance.apply(patientInstanceUsc310);
        assertJsonEqual("""
        {
          "profile": [
            "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|3.1.0"
          ]
        }
        """, at(exported, "meta"));
        exported = exportInstance.apply(patientInstanceUsc400);
        assertJsonEqual("""
        {
          "profile": [
            "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient|4.0.0"
          ]
        }
        """, at(exported, "meta"));
      }

    }
    @Test
    @DisplayName("InstanceExporter #exportInstance should not set meta.profile when we are making an instance of a base resource")
    void shouldNotSetMetaProfileWhenWeAreMakingAnInstanceOfABaseResource() {
      Instance boo = new Instance("Boo");
      boo.instanceOf = "Patient";
      InstanceDefinition exported = exportInstance.apply(boo);
      assertNull(at(exported, "meta"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set meta.profile with the InstanceOf profile before checking for required elements")
    void shouldSetMetaProfileWithTheInstanceOfProfileBeforeCheckingForRequiredElements() {
      /*
       * meta 1..1 MS
       * meta.profile 1..* MS
       */
      FlagRule metaMS = new FlagRule("meta");
      metaMS.mustSupport = true;
      CardRule metaCard = new CardRule("meta");
      metaCard.min = 1.0;
      metaCard.max = "1";
      FlagRule metaProfileMS = new FlagRule("meta.profile");
      metaProfileMS.mustSupport = true;
      CardRule metaProfileCard = new CardRule("meta.profile");
      metaProfileCard.min = 1.0;
      metaProfileCard.max = "*";
      patient.rules.addAll(Arrays.asList(metaMS, metaCard, metaProfileMS, metaProfileCard));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals(1, len(at(exported, "meta.profile")));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should only set meta.profile with one profile when profile is set on the InstanceOf profile")
    void shouldOnlySetMetaProfileWithOneProfileWhenProfileIsSetOnTheInstanceOfProfile() {
      Profile patientAbstractProfile = new Profile("MyPatientAbstract");
      patientAbstractProfile.parent = "Patient";
      /*
       * meta 1..1 MS
       * meta.profile 1..* MS
       * meta.profile ^slicing.discriminator.type = #pattern
       * meta.profile ^slicing.discriminator.path = "$this"
       * meta.profile contains supportedPatientProfile 1..1
       */
      FlagRule metaMS = new FlagRule("meta");
      metaMS.mustSupport = true;
      CardRule metaCard = new CardRule("meta");
      metaCard.min = 1.0;
      metaCard.max = "1";
      FlagRule metaProfileMS = new FlagRule("meta.profile");
      metaProfileMS.mustSupport = true;
      CardRule metaProfileCard = new CardRule("meta.profile");
      metaProfileCard.min = 1.0;
      metaProfileCard.max = "*";
      CaretValueRule typeCaretRule = new CaretValueRule("meta.profile");
      typeCaretRule.caretPath = "slicing.discriminator.type";
      typeCaretRule.value = new FshCode("pattern");
      CaretValueRule pathCaretRule = new CaretValueRule("meta.profile");
      pathCaretRule.caretPath = "slicing.discriminator.path";
      pathCaretRule.value = "$this";
      ContainsRule containsRule = new ContainsRule("meta.profile");
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "supportedPatientProfile"
        }
        """))));
      CardRule cardRule = new CardRule("meta.profile[supportedPatientProfile]");
      cardRule.min = 1.0;
      cardRule.max = "1";
      patientAbstractProfile.rules.addAll(Arrays.asList(metaMS, metaCard, metaProfileMS, metaProfileCard, typeCaretRule, pathCaretRule, containsRule, cardRule));
      Profile patientProfile = new Profile("MyPatient");
      patientProfile.parent = "MyPatientAbstract";
      // * meta.profile[supportedPatientProfile] = Canonical(MyPatient)
      AssignmentRule metaProfileAssignment = new AssignmentRule("meta.profile[supportedPatientProfile]");
      metaProfileAssignment.value = new FshCanonical("MyPatient");
      patientProfile.rules.add(metaProfileAssignment);
      doc.profiles.put(patientAbstractProfile.name, patientAbstractProfile);
      doc.profiles.put(patientProfile.name, patientProfile);
      Instance patientInstance = new Instance("MyPatientInstance");
      patientInstance.instanceOf = "MyPatient";
      // * name[0].family = "LastName"
      AssignmentRule patientAssignmentRule = new AssignmentRule("name[0].family");
      patientAssignmentRule.value = "LastName";
      patientInstance.rules.add(patientAssignmentRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals(1, len(at(exported, "meta.profile")));
      assertJsonEqual("""
        [
          "http://hl7.org/fhir/us/minimal/StructureDefinition/MyPatient"
        ]
        """, at(exported, "meta.profile"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should add the InstanceOf profile as the first meta.profile if it is not added by any rules")
    void shouldAddTheInstanceOfProfileAsTheFirstMetaProfileIfItIsNotAddedByAnyRules() {
      /*
       * meta 1..1 MS
       * meta.profile 1..* MS
       * meta.profile ^slicing.discriminator.type = #pattern
       * meta.profile ^slicing.discriminator.path = "$this"
       */
      FlagRule metaMS = new FlagRule("meta");
      metaMS.mustSupport = true;
      CardRule metaCard = new CardRule("meta");
      metaCard.min = 1.0;
      metaCard.max = "1";
      FlagRule metaProfileMS = new FlagRule("meta.profile");
      metaProfileMS.mustSupport = true;
      CardRule metaProfileCard = new CardRule("meta.profile");
      metaProfileCard.min = 1.0;
      metaProfileCard.max = "*";
      CaretValueRule typeCaretRule = new CaretValueRule("meta.profile");
      typeCaretRule.caretPath = "slicing.discriminator.type";
      typeCaretRule.value = new FshCode("pattern");
      CaretValueRule pathCaretRule = new CaretValueRule("meta.profile");
      pathCaretRule.caretPath = "slicing.discriminator.path";
      pathCaretRule.value = "$this";
      patient.rules.addAll(Arrays.asList(metaMS, metaCard, metaProfileMS, metaProfileCard, typeCaretRule, pathCaretRule));
      // * name[0].family = "LastName"
      // * meta.profile[+] = "http://example.org/fhir/StructureDefinition/OtherPatient"
      AssignmentRule patientAssignmentRule = new AssignmentRule("name[0].family");
      patientAssignmentRule.value = "LastName";
      AssignmentRule otherProfileRule = new AssignmentRule("meta.profile[+]");
      otherProfileRule.value = "http://example.org/fhir/StructureDefinition/OtherPatient";
      patientInstance.rules.addAll(Arrays.asList(patientAssignmentRule, otherProfileRule));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals(2, len(at(exported, "meta.profile")));
      assertJsonEqual("""
        [
          "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient",
          "http://example.org/fhir/StructureDefinition/OtherPatient"
        ]
        """, at(exported, "meta.profile"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set meta.profile without the unversioned InstanceOf profile if a versioned InstanceOf profile is present")
    void shouldSetMetaProfileWithoutTheUnversionedInstanceOfProfileIfAVersionedInstanceOf() {
      /*
       * meta 1..1 MS
       * meta.profile 1..* MS
       * meta.profile ^slicing.discriminator.type = #pattern
       * meta.profile ^slicing.discriminator.path = "$this"
       * meta.profile contains supportedProfile 1..1
       * meta.profile[supportedProfile] = Canonical(TestPatient|0.1.0)
       */
      FlagRule metaMS = new FlagRule("meta");
      metaMS.mustSupport = true;
      CardRule metaCard = new CardRule("meta");
      metaCard.min = 1.0;
      metaCard.max = "1";
      FlagRule metaProfileMS = new FlagRule("meta.profile");
      metaProfileMS.mustSupport = true;
      CardRule metaProfileCard = new CardRule("meta.profile");
      metaProfileCard.min = 1.0;
      metaProfileCard.max = "*";
      CaretValueRule typeCaretRule = new CaretValueRule("meta.profile");
      typeCaretRule.caretPath = "slicing.discriminator.type";
      typeCaretRule.value = new FshCode("pattern");
      CaretValueRule pathCaretRule = new CaretValueRule("meta.profile");
      pathCaretRule.caretPath = "slicing.discriminator.path";
      pathCaretRule.value = "$this";
      ContainsRule containsRule = new ContainsRule("meta.profile");
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "supportedProfile"
        }
        """))));
      CardRule cardRule = new CardRule("meta.profile[supportedProfile]");
      cardRule.min = 1.0;
      cardRule.max = "1";
      AssignmentRule supportedProfileCanonical = new AssignmentRule("meta.profile[supportedProfile]");
      supportedProfileCanonical.value = new FshCanonical("TestPatient");
      setProp(supportedProfileCanonical.value, "version", "0.1.0");
      patient.rules.addAll(Arrays.asList(metaMS, metaCard, metaProfileMS, metaProfileCard, typeCaretRule, pathCaretRule, containsRule, cardRule, supportedProfileCanonical));
      // * name[0].family = "LastName"
      AssignmentRule patientAssignmentRule = new AssignmentRule("name[0].family");
      patientAssignmentRule.value = "LastName";
      patientInstance.rules.add(patientAssignmentRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals(1, len(at(exported, "meta.profile")));
      assertJsonEqual("""
        [
          "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient|0.1.0"
        ]
        """, at(exported, "meta.profile"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should keep the unversioned InstanceOf in meta.profile if it is also added by a rule on the profile")
    void shouldKeepTheUnversionedInstanceOfInMetaProfileIfItIsAlsoAddedByARuleOnTheProfil() {
      /*
       * meta 1..1 MS
       * meta.profile 2..* MS
       * meta.profile ^slicing.discriminator.type = #pattern
       * meta.profile ^slicing.discriminator.path = "$this"
       * meta.profile contains supportedProfile 1..1 and unversionedProfile 1..1
       * meta.profile[supportedProfile] = Canonical(TestPatient|0.1.0)
       * meta.profile[unversionedProfile] = Canonical(TestPatient)
       */
      FlagRule metaMS = new FlagRule("meta");
      metaMS.mustSupport = true;
      CardRule metaCard = new CardRule("meta");
      metaCard.min = 1.0;
      metaCard.max = "1";
      FlagRule metaProfileMS = new FlagRule("meta.profile");
      metaProfileMS.mustSupport = true;
      CardRule metaProfileCard = new CardRule("meta.profile");
      metaProfileCard.min = 2.0;
      metaProfileCard.max = "*";
      CaretValueRule typeCaretRule = new CaretValueRule("meta.profile");
      typeCaretRule.caretPath = "slicing.discriminator.type";
      typeCaretRule.value = new FshCode("pattern");
      CaretValueRule pathCaretRule = new CaretValueRule("meta.profile");
      pathCaretRule.caretPath = "slicing.discriminator.path";
      pathCaretRule.value = "$this";
      ContainsRule containsRule = new ContainsRule("meta.profile");
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "supportedProfile"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "unversionedProfile"
        }
        """))));
      CardRule supportedCardRule = new CardRule("meta.profile[supportedProfile]");
      supportedCardRule.min = 1.0;
      supportedCardRule.max = "1";
      AssignmentRule supportedProfileCanonical = new AssignmentRule("meta.profile[supportedProfile]");
      supportedProfileCanonical.value = new FshCanonical("TestPatient");
      setProp(supportedProfileCanonical.value, "version", "0.1.0");
      CardRule unversionedCardRule = new CardRule("meta.profile[unversionedProfile]");
      unversionedCardRule.min = 1.0;
      unversionedCardRule.max = "1";
      AssignmentRule unversionedProfileCanonical = new AssignmentRule("meta.profile[unversionedProfile]");
      unversionedProfileCanonical.value = new FshCanonical("TestPatient");
      patient.rules.addAll(Arrays.asList(metaMS, metaCard, metaProfileMS, metaProfileCard, typeCaretRule, pathCaretRule, containsRule, supportedCardRule, supportedProfileCanonical, unversionedCardRule, unversionedProfileCanonical));
      // * name[0].family = "LastName"
      AssignmentRule patientAssignmentRule = new AssignmentRule("name[0].family");
      patientAssignmentRule.value = "LastName";
      patientInstance.rules.add(patientAssignmentRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals(2, len(at(exported, "meta.profile")));
      assertJsonEqual("""
        [
          "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient|0.1.0",
          "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient"
        ]
        """, at(exported, "meta.profile"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should keep the unversioned InstanceOf in meta.profile if it is also added by a rule on the instance")
    void shouldKeepTheUnversionedInstanceOfInMetaProfileIfItIsAlsoAddedByARuleOnTheInstan() {
      /*
       * meta 1..1 MS
       * meta.profile 1..* MS
       * meta.profile ^slicing.discriminator.type = #pattern
       * meta.profile ^slicing.discriminator.path = "$this"
       * meta.profile contains supportedProfile 1..1
       * meta.profile[supportedProfile] = Canonical(TestPatient|0.1.0)
       */
      FlagRule metaMS = new FlagRule("meta");
      metaMS.mustSupport = true;
      CardRule metaCard = new CardRule("meta");
      metaCard.min = 1.0;
      metaCard.max = "1";
      FlagRule metaProfileMS = new FlagRule("meta.profile");
      metaProfileMS.mustSupport = true;
      CardRule metaProfileCard = new CardRule("meta.profile");
      metaProfileCard.min = 1.0;
      metaProfileCard.max = "*";
      CaretValueRule typeCaretRule = new CaretValueRule("meta.profile");
      typeCaretRule.caretPath = "slicing.discriminator.type";
      typeCaretRule.value = new FshCode("pattern");
      CaretValueRule pathCaretRule = new CaretValueRule("meta.profile");
      pathCaretRule.caretPath = "slicing.discriminator.path";
      pathCaretRule.value = "$this";
      ContainsRule containsRule = new ContainsRule("meta.profile");
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "supportedProfile"
        }
        """))));
      CardRule cardRule = new CardRule("meta.profile[supportedProfile]");
      cardRule.min = 1.0;
      cardRule.max = "1";
      AssignmentRule supportedProfileCanonical = new AssignmentRule("meta.profile[supportedProfile]");
      supportedProfileCanonical.value = new FshCanonical("TestPatient");
      setProp(supportedProfileCanonical.value, "version", "0.1.0");
      patient.rules.addAll(Arrays.asList(metaMS, metaCard, metaProfileMS, metaProfileCard, typeCaretRule, pathCaretRule, containsRule, cardRule, supportedProfileCanonical));
      // * name[0].family = "LastName"
      // * meta.profile[+] = Canonical(TestPatient|0.1.0)
      // * meta.profile[+] = Canonical(TestPatient)
      AssignmentRule patientAssignmentRule = new AssignmentRule("name[0].family");
      patientAssignmentRule.value = "LastName";
      AssignmentRule versionedProfileRule = new AssignmentRule("meta.profile[+]");
      versionedProfileRule.value = new FshCanonical("TestPatient");
      setProp(versionedProfileRule.value, "version", "0.1.0");
      AssignmentRule unversionedProfileRule = new AssignmentRule("meta.profile[+]");
      unversionedProfileRule.value = new FshCanonical("TestPatient");
      patientInstance.rules.addAll(Arrays.asList(patientAssignmentRule, versionedProfileRule, unversionedProfileRule));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals(2, len(at(exported, "meta.profile")));
      assertJsonEqual("""
        [
          "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient|0.1.0",
          "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient"
        ]
        """, at(exported, "meta.profile"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set meta.profile on all instances when setMetaProfile is always")
    void shouldSetMetaProfileOnAllInstancesWhenSetMetaProfileIsAlways() {
      tank.config.instanceOptions = instanceOptions(JsAssert.json("""
        {
          "setMetaProfile": "always"
        }
        """));
      Instance boo = new Instance("Boo");
      boo.instanceOf = patient.getId();
      Instance spooky = new Instance("Skeleton");
      spooky.instanceOf = patient.getId();
      spooky.usage = InstanceUsage.Inline;
      InstanceDefinition booInstance = exportInstance.apply(boo);
      InstanceDefinition spookyInstance = exportInstance.apply(spooky);
      assertJsEqual(obj("profile", new ArrayList<>(Arrays.asList("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId())))), at(booInstance, "meta"));
      assertEquals("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId()), booInstance._instanceMeta.instanceOfUrl);
      assertJsEqual(obj("profile", new ArrayList<>(Arrays.asList("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId())))), at(spookyInstance, "meta"));
      assertEquals("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId()), spookyInstance._instanceMeta.instanceOfUrl);
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set meta.profile on all instances when setMetaProfile is not set")
    void shouldSetMetaProfileOnAllInstancesWhenSetMetaProfileIsNotSet() {
      tank.config.instanceOptions = instanceOptions(JsAssert.json("{}"));
      Instance boo = new Instance("Boo");
      boo.instanceOf = patient.getId();
      Instance spooky = new Instance("Skeleton");
      spooky.instanceOf = patient.getId();
      spooky.usage = InstanceUsage.Inline;
      InstanceDefinition booInstance = exportInstance.apply(boo);
      InstanceDefinition spookyInstance = exportInstance.apply(spooky);
      assertJsEqual(obj("profile", new ArrayList<>(Arrays.asList("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId())))), at(booInstance, "meta"));
      assertEquals("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId()), booInstance._instanceMeta.instanceOfUrl);
      assertJsEqual(obj("profile", new ArrayList<>(Arrays.asList("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId())))), at(spookyInstance, "meta"));
      assertEquals("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId()), spookyInstance._instanceMeta.instanceOfUrl);
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set meta.profile on no instances when setMetaProfile is never")
    void shouldSetMetaProfileOnNoInstancesWhenSetMetaProfileIsNever() {
      tank.config.instanceOptions = instanceOptions(JsAssert.json("""
        {
          "setMetaProfile": "never"
        }
        """));
      Instance boo = new Instance("Boo");
      boo.instanceOf = patient.getId();
      Instance spooky = new Instance("Skeleton");
      spooky.instanceOf = patient.getId();
      spooky.usage = InstanceUsage.Inline;
      InstanceDefinition booInstance = exportInstance.apply(boo);
      InstanceDefinition spookyInstance = exportInstance.apply(spooky);
      assertNull(at(booInstance, "meta"));
      assertEquals("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId()), booInstance._instanceMeta.instanceOfUrl);
      assertNull(at(spookyInstance, "meta"));
      assertEquals("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId()), spookyInstance._instanceMeta.instanceOfUrl);
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set meta.profile on inline instances when setMetaProfile is inline-only")
    void shouldSetMetaProfileOnInlineInstancesWhenSetMetaProfileIsInlineOnly() {
      tank.config.instanceOptions = instanceOptions(JsAssert.json("""
        {
          "setMetaProfile": "inline-only"
        }
        """));
      Instance boo = new Instance("Boo");
      boo.instanceOf = patient.getId();
      Instance spooky = new Instance("Skeleton");
      spooky.instanceOf = patient.getId();
      spooky.usage = InstanceUsage.Inline;
      InstanceDefinition booInstance = exportInstance.apply(boo);
      InstanceDefinition spookyInstance = exportInstance.apply(spooky);
      assertNull(at(booInstance, "meta"));
      assertEquals("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId()), booInstance._instanceMeta.instanceOfUrl);
      assertJsEqual(obj("profile", new ArrayList<>(Arrays.asList("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId())))), at(spookyInstance, "meta"));
      assertEquals("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId()), spookyInstance._instanceMeta.instanceOfUrl);
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set meta.profile on non-inline instances when setMetaProfile is standalone-only")
    void shouldSetMetaProfileOnNonInlineInstancesWhenSetMetaProfileIsStandaloneOnly() {
      tank.config.instanceOptions = instanceOptions(JsAssert.json("""
        {
          "setMetaProfile": "standalone-only"
        }
        """));
      Instance boo = new Instance("Boo");
      boo.instanceOf = patient.getId();
      Instance spooky = new Instance("Skeleton");
      spooky.instanceOf = patient.getId();
      spooky.usage = InstanceUsage.Inline;
      InstanceDefinition booInstance = exportInstance.apply(boo);
      InstanceDefinition spookyInstance = exportInstance.apply(spooky);
      assertJsEqual(obj("profile", new ArrayList<>(Arrays.asList("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId())))), at(booInstance, "meta"));
      assertEquals("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId()), booInstance._instanceMeta.instanceOfUrl);
      assertNull(at(spookyInstance, "meta"));
      assertEquals("" + Js.toStr(tank.config.canonical) + "/StructureDefinition/" + Js.toStr(patient.getId()), spookyInstance._instanceMeta.instanceOfUrl);
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should automatically set the URL property on definition instances")
    void shouldAutomaticallySetTheURLPropertyOnDefinitionInstances() {
      Instance codeSystemInstance = new Instance("TestInstance");
      codeSystemInstance.instanceOf = "CodeSystem";
      codeSystemInstance.usage = InstanceUsage.Definition;
      InstanceDefinition exported = exportInstance.apply(codeSystemInstance);
      assertEquals("http://hl7.org/fhir/us/minimal/CodeSystem/TestInstance", at(exported, "url"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not automatically set the URL property on definition instances if the URL is set explicitly")
    void shouldNotAutomaticallySetTheURLPropertyOnDefinitionInstancesIfTheURLIsSetExplici() {
      Instance codeSystemInstance = new Instance("TestInstance");
      codeSystemInstance.instanceOf = "CodeSystem";
      codeSystemInstance.usage = InstanceUsage.Definition;
      AssignmentRule urlRule = new AssignmentRule("url");
      urlRule.value = "http://someDifferentCanonical.org/testInstance";
      codeSystemInstance.rules.add(urlRule);
      InstanceDefinition exported = exportInstance.apply(codeSystemInstance);
      assertEquals("http://someDifferentCanonical.org/testInstance", at(exported, "url"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not automatically set the URL property on definition instances if the profile does not support URL setting")
    void shouldNotAutomaticallySetTheURLPropertyOnDefinitionInstancesIfTheProfileDoesNotS() {
      Instance patientInstance = new Instance("TestInstance");
      patientInstance.instanceOf = "Patient";
      patientInstance.usage = InstanceUsage.Definition;
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertNull(at(exported, "url"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set an extension on meta.profile when no rules set values on meta.profile")
    void shouldSetAnExtensionOnMetaProfileWhenNoRulesSetValuesOnMetaProfile() {
      // Extension: SimpleExt
      Extension simpleExt = new Extension("SimpleExt");
      doc.extensions.put(simpleExt.name, simpleExt);
      // * meta.profile.extension[SimpleExt].valueString = "simple string"
      AssignmentRule metaProfileExtension = new AssignmentRule("meta.profile.extension[SimpleExt].valueString");
      metaProfileExtension.value = "simple string";
      patientInstance.rules.add(metaProfileExtension);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      // since meta.profile[0] has child elements but no value, the best guess is that
      // the user expects us to fill in that value using instanceOf, and that they want
      // those child elements to be at the same index as the instanceOf value.
      assertJsonEqual("""
        [
          "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient"
        ]
        """, at(exported, "meta.profile"));
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/SimpleExt",
            "valueString": "simple string"
          }
        ]
        """, at(exported, "meta._profile[0].extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set an extension on meta.profile when a rule sets the InstanceOf url on meta.profile")
    void shouldSetAnExtensionOnMetaProfileWhenARuleSetsTheInstanceOfUrlOnMetaProfile() {
      // Extension: SimpleExt
      Extension simpleExt = new Extension("SimpleExt");
      doc.extensions.put(simpleExt.name, simpleExt);
      // * meta.profile = "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient"
      // * meta.profile.extension[SimpleExt].valueString = "simple string"
      AssignmentRule metaProfileValue = new AssignmentRule("meta.profile");
      metaProfileValue.value = "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient";
      AssignmentRule metaProfileExtension = new AssignmentRule("meta.profile.extension[SimpleExt].valueString");
      metaProfileExtension.value = "simple string";
      patientInstance.rules.addAll(Arrays.asList(metaProfileValue, metaProfileExtension));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      // since meta.profile already contains a value of instanceOf, nothing changes in either array.
      assertJsonEqual("""
        [
          "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient"
        ]
        """, at(exported, "meta.profile"));
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/SimpleExt",
            "valueString": "simple string"
          }
        ]
        """, at(exported, "meta._profile[0].extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set an extension on meta.profile when a rule sets a non-InstanceOf url on meta.profile")
    void shouldSetAnExtensionOnMetaProfileWhenARuleSetsANonInstanceOfUrlOnMetaProfile() {
      // Extension: SimpleExt
      Extension simpleExt = new Extension("SimpleExt");
      doc.extensions.put(simpleExt.name, simpleExt);
      // * meta.profile = "http://hl7.org/fhir/us/minimal/StructureDefinition/DifferentPatient"
      // * meta.profile.extension[SimpleExt].valueString = "simple string"
      AssignmentRule metaProfileValue = new AssignmentRule("meta.profile");
      metaProfileValue.value = "http://hl7.org/fhir/us/minimal/StructureDefinition/DifferentPatient";
      AssignmentRule metaProfileExtension = new AssignmentRule("meta.profile.extension[SimpleExt].valueString");
      metaProfileExtension.value = "simple string";
      patientInstance.rules.addAll(Arrays.asList(metaProfileValue, metaProfileExtension));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      // in this case, since profile[0] has both a value and a child element before receiving instanceOf,
      // keep those aligned.
      assertJsonEqual("""
        [
          "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient",
          "http://hl7.org/fhir/us/minimal/StructureDefinition/DifferentPatient"
        ]
        """, at(exported, "meta.profile"));
      assertJsonEqual("""
        [
          null,
          {
            "extension": [
              {
                "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/SimpleExt",
                "valueString": "simple string"
              }
            ]
          }
        ]
        """, at(exported, "meta._profile"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set a non-InstanceOf url and an extension on meta.profile at the same non-zero index")
    void shouldSetANonInstanceOfUrlAndAnExtensionOnMetaProfileAtTheSameNonZeroIndex() {
      // Extension: SimpleExt
      Extension simpleExt = new Extension("SimpleExt");
      doc.extensions.put(simpleExt.name, simpleExt);
      // * meta.profile[1] = "http://hl7.org/fhir/us/minimal/StructureDefinition/DifferentPatient"
      // * meta.profile[1].extension[SimpleExt].valueString = "simple string"
      AssignmentRule metaProfileValue = new AssignmentRule("meta.profile[1]");
      metaProfileValue.value = "http://hl7.org/fhir/us/minimal/StructureDefinition/DifferentPatient";
      AssignmentRule metaProfileExtension = new AssignmentRule("meta.profile[1].extension[SimpleExt].valueString");
      metaProfileExtension.value = "simple string";
      patientInstance.rules.addAll(Arrays.asList(metaProfileValue, metaProfileExtension));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      // in this case, since profile[1] has both a value and a child element before receiving instanceOf,
      // keep those aligned, and put instanceOf at profile[0]
      assertJsonEqual("""
        [
          "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient",
          "http://hl7.org/fhir/us/minimal/StructureDefinition/DifferentPatient"
        ]
        """, at(exported, "meta.profile"));
      assertJsonEqual("""
        [
          null,
          {
            "extension": [
              {
                "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/SimpleExt",
                "valueString": "simple string"
              }
            ]
          }
        ]
        """, at(exported, "meta._profile"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set InstanceOf and non-InstanceOf urls in meta.profile alongside extensions")
    void shouldSetInstanceOfAndNonInstanceOfUrlsInMetaProfileAlongsideExtensions() {
      // Extension: SimpleExt
      Extension simpleExt = new Extension("SimpleExt");
      doc.extensions.put(simpleExt.name, simpleExt);
      // * meta.profile[0] = "http://hl7.org/fhir/us/minimal/StructureDefinition/DifferentPatient"
      // * meta.profile[0].extension[SimpleExt].valueString = "simple string"
      // * meta.profile[1] = "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient"
      // * meta.profile[1].extension[SimpleExt].valueString = "less simple string"
      AssignmentRule metaProfileZero = new AssignmentRule("meta.profile[0]");
      metaProfileZero.value = "http://hl7.org/fhir/us/minimal/StructureDefinition/DifferentPatient";
      AssignmentRule metaExtensionZero = new AssignmentRule("meta.profile[0].extension[SimpleExt].valueString");
      metaExtensionZero.value = "simple string";
      AssignmentRule metaProfileOne = new AssignmentRule("meta.profile[1]");
      metaProfileOne.value = "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient";
      AssignmentRule metaExtensionOne = new AssignmentRule("meta.profile[1].extension[SimpleExt].valueString");
      metaExtensionOne.value = "less simple string";
      patientInstance.rules.addAll(Arrays.asList(metaProfileZero, metaExtensionZero, metaProfileOne, metaExtensionOne));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      // since a rule sets the InstanceOf url, we don't expect anything to change after the rules are applied.
      assertJsonEqual("""
        [
          "http://hl7.org/fhir/us/minimal/StructureDefinition/DifferentPatient",
          "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient"
        ]
        """, at(exported, "meta.profile"));
      assertJsonEqual("""
        [
          {
            "extension": [
              {
                "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/SimpleExt",
                "valueString": "simple string"
              }
            ]
          },
          {
            "extension": [
              {
                "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/SimpleExt",
                "valueString": "less simple string"
              }
            ]
          }
        ]
        """, at(exported, "meta._profile"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should keep meta.profile and child elements of meta.profile aligned when removing duplicates from meta.profile")
    void shouldKeepMetaProfileAndChildElementsOfMetaProfileAlignedWhenRemovingDuplicatesF() {
      // Extension: SimpleExt
      Extension simpleExt = new Extension("SimpleExt");
      doc.extensions.put(simpleExt.name, simpleExt);
      // * meta.profile[0] = "http://hl7.org/fhir/us/minimal/StructureDefinition/DifferentPatient"
      // * meta.profile[0].extension[SimpleExt].valueString = "this will be first"
      // * meta.profile[1] = "http://hl7.org/fhir/us/minimal/StructureDefinition/DifferentPatient"
      // * meta.profile[1].extension[SimpleExt].valueString = "this will be second"
      // * meta.profile[1].id = "use-this"
      AssignmentRule metaProfileZero = new AssignmentRule("meta.profile[0]");
      metaProfileZero.value = "http://hl7.org/fhir/us/minimal/StructureDefinition/DifferentPatient";
      AssignmentRule metaExtensionZero = new AssignmentRule("meta.profile[0].extension[SimpleExt].valueString");
      metaExtensionZero.value = "this will be first";
      AssignmentRule metaProfileOne = new AssignmentRule("meta.profile[1]");
      metaProfileOne.value = "http://hl7.org/fhir/us/minimal/StructureDefinition/DifferentPatient";
      AssignmentRule metaExtensionOne = new AssignmentRule("meta.profile[1].extension[SimpleExt].valueString");
      metaExtensionOne.value = "this will be second";
      AssignmentRule metaIdOne = new AssignmentRule("meta.profile[1].id");
      metaIdOne.value = "use-this";
      patientInstance.rules.addAll(Arrays.asList(metaProfileZero, metaExtensionZero, metaProfileOne, metaExtensionOne, metaIdOne));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      // we expect the duplicate DifferentPatient to be removed.
      // since the extensions are different, we take the union
      // see https://hl7.org/fhir/R5/resource.html#profile-tags
      assertJsonEqual("""
        [
          "http://hl7.org/fhir/us/minimal/StructureDefinition/TestPatient",
          "http://hl7.org/fhir/us/minimal/StructureDefinition/DifferentPatient"
        ]
        """, at(exported, "meta.profile"));
      assertJsonEqual("""
        [
          null,
          {
            "extension": [
              {
                "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/SimpleExt",
                "valueString": "this will be first"
              },
              {
                "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/SimpleExt",
                "valueString": "this will be second"
              }
            ],
            "id": "use-this"
          }
        ]
        """, at(exported, "meta._profile"));
    }

    // Setting instance id
    @Test
    @DisplayName("InstanceExporter #exportInstance should set id to instance name by default")
    void shouldSetIdToInstanceNameByDefault() {
      Instance myExamplePatient = new Instance("MyExample");
      myExamplePatient.instanceOf = "Patient";
      InstanceDefinition exported = exportInstance.apply(myExamplePatient);
      Object expectedInstanceJSON = JsAssert.json("""
        {
          "resourceType": "Patient",
          "id": "MyExample"
        }
        """);
      assertJsEqual(expectedInstanceJSON, exported.toJSON());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should overwrite id if it is set by a rule")
    void shouldOverwriteIdIfItIsSetByARule() {
      Instance myExamplePatient = new Instance("MyExample");
      myExamplePatient.instanceOf = "Patient";
      AssignmentRule assignedValRule = new AssignmentRule("id");
      assignedValRule.value = "PatientA";
      myExamplePatient.rules.add(assignedValRule);
      InstanceDefinition exported = exportInstance.apply(myExamplePatient);
      Object expectedInstanceJSON = JsAssert.json("""
        {
          "resourceType": "Patient",
          "id": "PatientA"
        }
        """);
      assertJsEqual(expectedInstanceJSON, exported.toJSON());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log a message when the instance has an invalid id")
    void shouldLogAMessageWhenTheInstanceHasAnInvalidId() {
      Instance myExamplePatient = new Instance("MyExample").withFile("Some.fsh").withLocation(new int[] {3, 6, 6, 45});
      myExamplePatient.instanceOf = "Patient";
      AssignmentRule assignedValRule = new AssignmentRule("id").withFile("Some.fsh").withLocation(new int[] {5, 0, 6, 45});
      assignedValRule.value = "Some Patient";
      myExamplePatient.rules.add(assignedValRule);
      InstanceDefinition exported = exportInstance.apply(myExamplePatient);
      Object expectedInstanceJSON = JsAssert.json("""
        {
          "resourceType": "Patient",
          "id": "Some Patient"
        }
        """);
      assertJsEqual(expectedInstanceJSON, exported.toJSON());
      assertMatches("does not represent a valid FHIR id", "s", loggerSpy.getLastMessage());
      assertMatches("File: Some\\.fsh.*Line: 5 - 6\\D*", "s", loggerSpy.getLastMessage());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should sanitize the id and log a message when a valid name is used to make an invalid id")
    void shouldSanitizeTheIdAndLogAMessageWhenAValidNameIsUsedToMakeAnInvalidId() {
      Instance instance = new Instance("Foo").withFile("Wrong.fsh").withLocation(new int[] {2, 8, 5, 18});
      instance.instanceOf = "Patient";
      AssignmentRule assignedValRule = new AssignmentRule("id").withFile("Wrong.fsh").withLocation(new int[] {5, 0, 5, 18});
      assignedValRule.value = "Some_Patient";
      instance.rules.add(assignedValRule);
      InstanceDefinition exported = exportInstance.apply(instance);
      Object expectedInstanceJSON = JsAssert.json("""
        {
          "resourceType": "Patient",
          "id": "Some_Patient"
        }
        """);
      assertJsEqual(expectedInstanceJSON, exported.toJSON());
      assertMatches("The string \"Some_Patient\" does not represent a valid FHIR id\\. FHIR ids only allow ASCII letters \\(A-Z, a-z\\), numbers \\(0-9\\), hyphens \\(-\\), and dots \\(\\.\\), with a length limit of 64 characters\\. Avoid this warning by changing the Instance declaration to follow the FHIR id requirements\\.", "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: Wrong\\.fsh.*Line: 5\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log a message when a long valid name is used to make an invalid id")
    void shouldLogAMessageWhenALongValidNameIsUsedToMakeAnInvalidId() {
      Instance instance = new Instance("Foo").withFile("Wrong.fsh").withLocation(new int[] {2, 8, 5, 18});
      instance.instanceOf = "Patient";
      AssignmentRule assignedValRule = new AssignmentRule("id").withFile("Wrong.fsh").withLocation(new int[] {4, 0, 5, 18});
      String longId = "Toolong";
      while (longId.length() < 65) {
        longId += "longer";
      }
      assignedValRule.value = longId;
      instance.rules.add(assignedValRule);
      InstanceDefinition exported = exportInstance.apply(instance);
      Object expectedInstanceJSON = obj("resourceType", "Patient", "id", longId);
      assertJsEqual(expectedInstanceJSON, exported.toJSON());
      assertMatches("The string \"Toolong(longer)+\" does not represent a valid FHIR id\\. FHIR ids only allow ASCII letters \\(A-Z, a-z\\), numbers \\(0-9\\), hyphens \\(-\\), and dots \\(\\.\\), with a length limit of 64 characters\\. Avoid this warning by changing the Instance declaration to follow the FHIR id requirements\\.", "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: Wrong\\.fsh.*Line: 4 - 5\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when multiple instances of the same type have the same id")
    void shouldLogAnErrorWhenMultipleInstancesOfTheSameTypeHaveTheSameId() {
      Instance firstExample = new Instance("FirstExample").withFile("Repeat.fsh").withLocation(new int[] {3, 8, 11, 25});
      firstExample.instanceOf = "Patient";
      AssignmentRule firstId = new AssignmentRule("id");
      firstId.value = "repeated-id";
      firstExample.rules.add(firstId);
      doc.instances.put(firstExample.name, firstExample);
      Instance secondExample = new Instance("SecondExample").withFile("Repeat.fsh").withLocation(new int[] {13, 8, 20, 22});
      secondExample.instanceOf = "Patient";
      AssignmentRule secondId = new AssignmentRule("id");
      secondId.value = "repeated-id";
      secondExample.rules.add(secondId);
      doc.instances.put(secondExample.name, secondExample);
      exporter.exportInstance(firstExample);
      exporter.exportInstance(secondExample);
      assertMatches("Multiple instances of type Patient with id repeated-id", "s", loggerSpy.getLastMessage("error"));
      assertMatches("File: Repeat\\.fsh.*Line: 13 - 20\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log an error when multiple instances of different types have the same id")
    void shouldNotLogAnErrorWhenMultipleInstancesOfDifferentTypesHaveTheSameId() {
      Instance firstExample = new Instance("FirstExample").withFile("Repeat.fsh").withLocation(new int[] {3, 8, 11, 25});
      firstExample.instanceOf = "Patient";
      AssignmentRule firstId = new AssignmentRule("id");
      firstId.value = "repeated-id";
      firstExample.rules.add(firstId);
      doc.instances.put(firstExample.name, firstExample);
      Instance secondExample = new Instance("SecondExample").withFile("Repeat.fsh").withLocation(new int[] {13, 8, 20, 22});
      secondExample.instanceOf = "Practitioner";
      AssignmentRule secondId = new AssignmentRule("id");
      secondId.value = "repeated-id";
      secondExample.rules.add(secondId);
      doc.instances.put(secondExample.name, secondExample);
      exporter.exportInstance(firstExample);
      exporter.exportInstance(secondExample);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log an error when multiple inline instances of the same type have the same id")
    void shouldNotLogAnErrorWhenMultipleInlineInstancesOfTheSameTypeHaveTheSameId() {
      // Inline instances will typically not have an id assigned to them
      Instance firstQuantity = new Instance("FirstQuantity");
      firstQuantity.instanceOf = "Quantity";
      firstQuantity.usage = InstanceUsage.Inline;
      doc.instances.put(firstQuantity.name, firstQuantity);
      Instance secondQuantity = new Instance("SecondQuantity");
      secondQuantity.instanceOf = "Quantity";
      secondQuantity.usage = InstanceUsage.Inline;
      doc.instances.put(secondQuantity.name, secondQuantity);
      InstanceDefinition firstInstance = exporter.exportInstance(firstQuantity);
      InstanceDefinition secondInstance = exporter.exportInstance(secondQuantity);
      assertJsEqual(at(secondInstance, "id"), at(firstInstance, "id"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log an error when an inline instance and a non-inline instance of the same type have the same id")
    void shouldNotLogAnErrorWhenAnInlineInstanceAndANonInlineInstanceOfTheSameTypeHaveThe() {
      Instance inlineQuantity = new Instance("FirstQuantity");
      inlineQuantity.instanceOf = "Quantity";
      inlineQuantity.usage = InstanceUsage.Inline;
      AssignmentRule inlineId = new AssignmentRule("id");
      inlineId.value = "my-quantity";
      inlineQuantity.rules.add(inlineId);
      doc.instances.put(inlineQuantity.name, inlineQuantity);
      Instance exampleQuantity = new Instance("SecondQuantity");
      exampleQuantity.instanceOf = "Quantity";
      exampleQuantity.usage = InstanceUsage.Example;
      AssignmentRule exampleId = new AssignmentRule("id");
      exampleId.value = "my-quantity";
      exampleQuantity.rules.add(exampleId);
      doc.instances.put(exampleQuantity.name, exampleQuantity);
      InstanceDefinition inlineInstance = exporter.exportInstance(inlineQuantity);
      InstanceDefinition exampleInstance = exporter.exportInstance(exampleQuantity);
      assertJsEqual(at(exampleInstance, "id"), at(inlineInstance, "id"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set id on all instances when setId is always")
    void shouldSetIdOnAllInstancesWhenSetIdIsAlways() {
      tank.config.instanceOptions = instanceOptions(JsAssert.json("""
        {
          "setId": "always"
        }
        """));
      Instance boo = new Instance("Boo");
      boo.instanceOf = "Patient";
      Instance spooky = new Instance("Skeleton");
      spooky.instanceOf = "Patient";
      spooky.usage = InstanceUsage.Inline;
      assertEquals("Boo", at(exportInstance.apply(boo), "id"));
      assertEquals("Skeleton", at(exportInstance.apply(spooky), "id"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set id on all instances when setId is not set")
    void shouldSetIdOnAllInstancesWhenSetIdIsNotSet() {
      tank.config.instanceOptions = instanceOptions(JsAssert.json("{}"));
      Instance boo = new Instance("Boo");
      boo.instanceOf = "Patient";
      Instance spooky = new Instance("Skeleton");
      spooky.instanceOf = "Patient";
      spooky.usage = InstanceUsage.Inline;
      assertEquals("Boo", at(exportInstance.apply(boo), "id"));
      assertEquals("Skeleton", at(exportInstance.apply(spooky), "id"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set id on only non-inline instances when setId is standalone-only")
    void shouldSetIdOnOnlyNonInlineInstancesWhenSetIdIsStandaloneOnly() {
      tank.config.instanceOptions = instanceOptions(JsAssert.json("""
        {
          "setId": "standalone-only"
        }
        """));
      Instance boo = new Instance("Boo");
      boo.instanceOf = "Patient";
      Instance spooky = new Instance("Skeleton");
      spooky.instanceOf = "Patient";
      spooky.usage = InstanceUsage.Inline;
      assertEquals("Boo", at(exportInstance.apply(boo), "id"));
      assertNull(at(exportInstance.apply(spooky), "id"));
    }

    // Assigning top level elements
    @Test
    @DisplayName("InstanceExporter #exportInstance should assign top level elements that are assigned by pattern[x] on the Structure Definition")
    void shouldAssignTopLevelElementsThatAreAssignedByPatternXOnTheStructureDefinition() {
      CardRule cardRule = new CardRule("active");
      cardRule.min = 1.0;
      patient.rules.add(cardRule);
      AssignmentRule assignedValRule = new AssignmentRule("active");
      assignedValRule.value = true;
      patient.rules.add(assignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("true", at(exported, "active"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign top level elements that are assigned by fixed[x] on the Structure Definition")
    void shouldAssignTopLevelElementsThatAreAssignedByFixedXOnTheStructureDefinition() {
      CardRule cardRule = new CardRule("active");
      cardRule.min = 1.0;
      patient.rules.add(cardRule);
      AssignmentRule assignedValRule = new AssignmentRule("active");
      assignedValRule.value = true;
      assignedValRule.exactly = true;
      patient.rules.add(assignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("true", at(exported, "active"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign boolean false values that are assigned on the Structure Definition")
    void shouldAssignBooleanFalseValuesThatAreAssignedOnTheStructureDefinition() {
      CardRule cardRule = new CardRule("active");
      cardRule.min = 1.0;
      patient.rules.add(cardRule);
      AssignmentRule assignedValRule = new AssignmentRule("active");
      assignedValRule.value = false;
      assignedValRule.exactly = true;
      patient.rules.add(assignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals(false, at(exported, "active"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign numeric 0 values that are assigned on the Structure Definition")
    void shouldAssignNumeric0ValuesThatAreAssignedOnTheStructureDefinition() {
      // Profile: ZeroGoal
      // Parent: Goal
      // * target.detailInteger 1..1
      // * target.detailInteger = 0
      Profile goalProfile = new Profile("ZeroGoal");
      goalProfile.parent = "Goal";
      CardRule cardRule = new CardRule("target.detailInteger");
      cardRule.min = 1.0;
      cardRule.max = "1";
      AssignmentRule assignmentRule = new AssignmentRule("target.detailInteger");
      assignmentRule.value = 0.0;
      goalProfile.rules.addAll(Arrays.asList(cardRule, assignmentRule));
      doc.profiles.put(goalProfile.name, goalProfile);
      // Instance: MyInstance
      // InstanceOf: ZeroGoal
      // * lifecycleStatus = #proposed
      // * description = #000
      // * subject.reference = "http://example.org/Someone"
      // * target.measure = #111
      Instance goalInstance = new Instance("MyInstance");
      goalInstance.instanceOf = "ZeroGoal";
      AssignmentRule lifecycleStatus = new AssignmentRule("lifecycleStatus");
      lifecycleStatus.value = new FshCode("proposed");
      AssignmentRule description = new AssignmentRule("description");
      description.value = new FshCode("000");
      AssignmentRule subjectReference = new AssignmentRule("subject.reference");
      subjectReference.value = "http://example.org/Someone";
      AssignmentRule targetMeasure = new AssignmentRule("target.measure");
      targetMeasure.value = new FshCode("111");
      goalInstance.rules.addAll(Arrays.asList(lifecycleStatus, description, subjectReference, targetMeasure));
      InstanceDefinition exported = exportInstance.apply(goalInstance);
      assertJsEqual(0.0, at(exported, "target[0].detailInteger"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign top level codes that are assigned on the Structure Definition")
    void shouldAssignTopLevelCodesThatAreAssignedOnTheStructureDefinition() {
      CardRule cardRule = new CardRule("gender");
      cardRule.min = 1.0;
      patient.rules.add(cardRule);
      AssignmentRule assignedValRule = new AssignmentRule("gender");
      assignedValRule.value = new FshCode("F");
      patient.rules.add(assignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals("F", at(exported, "gender"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not assign optional elements that are assigned on the Structure Definition")
    void shouldNotAssignOptionalElementsThatAreAssignedOnTheStructureDefinition() {
      AssignmentRule assignedValRule = new AssignmentRule("active");
      assignedValRule.value = true;
      patient.rules.add(assignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertNull(at(exported, "active"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign top level elements to an array even if constrained on the Structure Definition")
    void shouldAssignTopLevelElementsToAnArrayEvenIfConstrainedOnTheStructureDefinition() {
      Profile condition = new Profile("TestCondition");
      condition.parent = "Condition";
      CardRule cardRule = new CardRule("category");
      cardRule.min = 1.0;
      cardRule.max = "1";
      condition.rules.add(cardRule);
      doc.profiles.put(condition.name, condition);
      Instance conditionInstance = new Instance("Bar");
      conditionInstance.instanceOf = "TestCondition";
      doc.instances.put(conditionInstance.name, conditionInstance);
      AssignmentRule assignedValRule = new AssignmentRule("category");
      FshCode assignedFshCode = new FshCode("foo", "http://foo.com");
      assignedValRule.value = assignedFshCode;
      condition.rules.add(assignedValRule);
      InstanceDefinition exported = exportInstance.apply(conditionInstance);
      assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "foo",
                "system": "http://foo.com"
              }
            ]
          }
        ]
        """, at(exported, "category"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign top level elements that are assigned by a pattern on the Structure Definition")
    void shouldAssignTopLevelElementsThatAreAssignedByAPatternOnTheStructureDefinition() {
      CardRule cardRule = new CardRule("maritalStatus");
      cardRule.min = 1.0;
      patient.rules.add(cardRule);
      AssignmentRule assignedValRule = new AssignmentRule("maritalStatus");
      FshCode assignedFshCode = new FshCode("foo", "http://foo.com");
      assignedValRule.value = assignedFshCode;
      patient.rules.add(assignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "coding": [
            {
              "code": "foo",
              "system": "http://foo.com"
            }
          ]
        }
        """, at(exported, "maritalStatus"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a value onto an element that are assigned by a pattern on the Structure Definition")
    void shouldAssignAValueOntoAnElementThatAreAssignedByAPatternOnTheStructureDefinition() {
      Profile observation = new Profile("TestObservation");
      observation.parent = "Observation";
      doc.profiles.put(observation.name, observation);
      OnlyRule onlyRule = new OnlyRule("value[x]");
      onlyRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "Quantity"
        }
        """))));
      observation.rules.add(onlyRule);
      AssignmentRule assignedValRule = new AssignmentRule("valueQuantity");
      FshCode assignedFshCode = new FshCode("foo", "http://foo.com");
      assignedValRule.value = assignedFshCode;
      observation.rules.add(assignedValRule);
      CardRule cardRule = new CardRule("valueQuantity");
      cardRule.min = 1.0;
      observation.rules.add(cardRule);
      Instance observationInstance = new Instance("MyObservation");
      observationInstance.instanceOf = "TestObservation";
      AssignmentRule assignedQuantityValue = new AssignmentRule("valueQuantity.value");
      assignedQuantityValue.value = 100.0;
      observationInstance.rules.add(assignedQuantityValue);
      doc.instances.put(observationInstance.name, observationInstance);
      InstanceDefinition exported = exportInstance.apply(observationInstance);
      assertJsonEqual("""
        {
          "code": "foo",
          "system": "http://foo.com",
          "value": 100
        }
        """, at(exported, "valueQuantity"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a value onto slice elements that are assigned by a pattern on the Structure Definition")
    void shouldAssignAValueOntoSliceElementsThatAreAssignedByAPatternOnTheStructureDefini() {
      ContainsRule containsRule = new ContainsRule("category");
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "niceSlice"
        }
        """))));
      respRate.rules.add(containsRule);
      CardRule cardRule = new CardRule("category[niceSlice]");
      cardRule.min = 1.0;
      cardRule.max = "*";
      respRate.rules.add(cardRule);
      AssignmentRule assignedValRule = new AssignmentRule("category[niceSlice]");
      FshCode assignedFshCode = new FshCode("rice", "http://spice.com");
      assignedValRule.value = assignedFshCode;
      respRate.rules.add(assignedValRule);
      InstanceDefinition exported = exportInstance.apply(respRateInstance);
      assertContainsEqual(JsAssert.json("""
        {
          "coding": [
            {
              "code": "rice",
              "system": "http://spice.com"
            }
          ]
        }
        """), at(exported, "category"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign top level choice elements that are assigned on the Structure Definition")
    void shouldAssignTopLevelChoiceElementsThatAreAssignedOnTheStructureDefinition() {
      AssignmentRule assignedValRule = new AssignmentRule("deceasedBoolean");
      assignedValRule.value = true;
      patient.rules.add(assignedValRule);
      CardRule cardRule = new CardRule("deceasedBoolean");
      cardRule.min = 1.0;
      patient.rules.add(cardRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals(true, at(exported, "deceasedBoolean"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not assign fixed values from value[x] children when a specific choice has not been chosen")
    void shouldNotAssignFixedValuesFromValueXChildrenWhenASpecificChoiceHasNotBeenChosen() {
      // Profile: ObservationProfile
      // Parent: Observation
      Profile observationProfile = new Profile("ObservationProfile");
      observationProfile.parent = "Observation";
      // * value[x] 1..1
      CardRule valueCardRequired = new CardRule("value[x]");
      valueCardRequired.min = 1.0;
      valueCardRequired.max = "1";
      observationProfile.rules.add(valueCardRequired);
      // * value[x].id 1..1
      CardRule valueIdCardRequired = new CardRule("value[x].id");
      valueIdCardRequired.min = 1.0;
      valueIdCardRequired.max = "1";
      observationProfile.rules.add(valueIdCardRequired);
      // * value[x].id = "some-required-id"
      AssignmentRule valueIdAssignment = new AssignmentRule("value[x].id");
      valueIdAssignment.value = "Hello World";
      observationProfile.rules.add(valueIdAssignment);
      doc.profiles.put(observationProfile.name, observationProfile);
      // Instance: TestInstance
      // InstanceOf: ObservationProfile
      Instance testInstance = new Instance("TestInstance");
      testInstance.instanceOf = "ObservationProfile";
      // * status = #final
      AssignmentRule statusFinal = new AssignmentRule("status");
      statusFinal.value = new FshCode("final");
      testInstance.rules.add(statusFinal);
      // * code = #testcode
      AssignmentRule codeTestCode = new AssignmentRule("code");
      codeTestCode.value = new FshCode("testcode");
      testInstance.rules.add(codeTestCode);
      doc.instances.put(testInstance.name, testInstance);
      InstanceDefinition exported = exportInstance.apply(testInstance);
      assertJsonEqual("""
        {
          "resourceType": "Observation",
          "id": "TestInstance",
          "meta": {
            "profile": [
              "http://hl7.org/fhir/us/minimal/StructureDefinition/ObservationProfile"
            ]
          },
          "status": "final",
          "code": {
            "coding": [
              {
                "code": "testcode"
              }
            ]
          }
        }
        """, exported.toJSON());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign fixed values from value[x] children using the correct specific choice property name")
    void shouldAssignFixedValuesFromValueXChildrenUsingTheCorrectSpecificChoicePropertyNa() {
      // Profile: ObservationProfile
      // Parent: Observation
      Profile observationProfile = new Profile("ObservationProfile");
      observationProfile.parent = "Observation";
      // * value[x] 1..1
      CardRule valueCardRequired = new CardRule("value[x]");
      valueCardRequired.min = 1.0;
      valueCardRequired.max = "1";
      observationProfile.rules.add(valueCardRequired);
      // * value[x].id 1..1
      CardRule valueIdCardRequired = new CardRule("value[x].id");
      valueIdCardRequired.min = 1.0;
      valueIdCardRequired.max = "1";
      observationProfile.rules.add(valueIdCardRequired);
      // * value[x].id = "some-required-id"
      AssignmentRule valueIdAssignment = new AssignmentRule("value[x].id");
      valueIdAssignment.value = "some-id";
      observationProfile.rules.add(valueIdAssignment);
      doc.profiles.put(observationProfile.name, observationProfile);
      // Instance: TestInstance
      // InstanceOf: ObservationProfile
      Instance testInstance = new Instance("TestInstance");
      testInstance.instanceOf = "ObservationProfile";
      // * status = #final
      AssignmentRule statusFinal = new AssignmentRule("status");
      statusFinal.value = new FshCode("final");
      testInstance.rules.add(statusFinal);
      // * code = #testcode
      AssignmentRule codeTestCode = new AssignmentRule("code");
      codeTestCode.value = new FshCode("testcode");
      testInstance.rules.add(codeTestCode);
      // * valueQuantity = 5 'mm'
      AssignmentRule valueQuantityAssignment = new AssignmentRule("valueQuantity");
      valueQuantityAssignment.value = new FshQuantity(5.0, new FshCode("mm", "http://unitsofmeasure.org"));
      testInstance.rules.add(valueQuantityAssignment);
      doc.instances.put(testInstance.name, testInstance);
      InstanceDefinition exported = exportInstance.apply(testInstance);
      assertJsonEqual("""
        {
          "resourceType": "Observation",
          "id": "TestInstance",
          "meta": {
            "profile": [
              "http://hl7.org/fhir/us/minimal/StructureDefinition/ObservationProfile"
            ]
          },
          "status": "final",
          "code": {
            "coding": [
              {
                "code": "testcode"
              }
            ]
          },
          "valueQuantity": {
            "id": "some-id",
            "value": 5,
            "system": "http://unitsofmeasure.org",
            "code": "mm"
          }
        }
        """, exported.toJSON());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign fixed values from value[x] children using the correct specific choice property name (primitive edition)")
    void shouldAssignFixedValuesFromValueXChildrenUsingTheCorrectSpecificChoicePropertyNa2() {
      // Profile: ObservationProfile
      // Parent: Observation
      Profile observationProfile = new Profile("ObservationProfile");
      observationProfile.parent = "Observation";
      // * value[x] 1..1
      CardRule valueCardRequired = new CardRule("value[x]");
      valueCardRequired.min = 1.0;
      valueCardRequired.max = "1";
      observationProfile.rules.add(valueCardRequired);
      // * value[x].id 1..1
      CardRule valueIdCardRequired = new CardRule("value[x].id");
      valueIdCardRequired.min = 1.0;
      valueIdCardRequired.max = "1";
      observationProfile.rules.add(valueIdCardRequired);
      // * value[x].id = "some-required-id"
      AssignmentRule valueIdAssignment = new AssignmentRule("value[x].id");
      valueIdAssignment.value = "some-id";
      observationProfile.rules.add(valueIdAssignment);
      doc.profiles.put(observationProfile.name, observationProfile);
      // Instance: TestInstance
      // InstanceOf: ObservationProfile
      Instance testInstance = new Instance("TestInstance");
      testInstance.instanceOf = "ObservationProfile";
      // * status = #final
      AssignmentRule statusFinal = new AssignmentRule("status");
      statusFinal.value = new FshCode("final");
      testInstance.rules.add(statusFinal);
      // * code = #testcode
      AssignmentRule codeTestCode = new AssignmentRule("code");
      codeTestCode.value = new FshCode("testcode");
      testInstance.rules.add(codeTestCode);
      // * valueString = 'Hello World'
      AssignmentRule valueStringAssignment = new AssignmentRule("valueString");
      valueStringAssignment.value = "Hello World";
      testInstance.rules.add(valueStringAssignment);
      doc.instances.put(testInstance.name, testInstance);
      InstanceDefinition exported = exportInstance.apply(testInstance);
      assertJsonEqual("""
        {
          "resourceType": "Observation",
          "id": "TestInstance",
          "meta": {
            "profile": [
              "http://hl7.org/fhir/us/minimal/StructureDefinition/ObservationProfile"
            ]
          },
          "status": "final",
          "code": {
            "coding": [
              {
                "code": "testcode"
              }
            ]
          },
          "valueString": "Hello World",
          "_valueString": {
            "id": "some-id"
          }
        }
        """, exported.toJSON());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign fixed value[x] correctly and log no errors when multiple choice slices are assigned")
    void shouldAssignFixedValueXCorrectlyAndLogNoErrorsWhenMultipleChoiceSlicesAreAssigne() {
      // Example from SUSHI 911: https://github.com/FHIR/sushi/issues/911
      // Instance: MyQuestionnaire
      // InstanceOf: Questionnaire
      Instance questionnaireInstance = new Instance("MyQuestionnaire");
      questionnaireInstance.instanceOf = "Questionnaire";
      // Add required properties
      // status = #draft
      AssignmentRule statusDraft = new AssignmentRule("status");
      statusDraft.value = new FshCode("draft");
      questionnaireInstance.rules.add(statusDraft);
      // * item[+].linkId = "findrisc-score"
      // * item[=].type = #decimal
      AssignmentRule itemLinkId = new AssignmentRule("item[+].linkId");
      itemLinkId.value = "findrisc-score";
      questionnaireInstance.rules.add(itemLinkId);
      AssignmentRule itemType = new AssignmentRule("item[=].type");
      itemType.value = new FshCode("decimal");
      questionnaireInstance.rules.add(itemType);
      // Add choiceSlices and ensure values are set and no warnings are logged
      // * item[=].extension[0].url = "http://example.org"
      // * item[=].extension[=].valueExpression.name = "scoreExt"
      // * item[=].extension[=].valueExpression.language = #text/fhirpath
      // * item[=].extension[=].valueExpression.expression = "'http://hl7.org/fhir/StructureDefinition/ordinalValue'"
      // * item[=].extension[+].url = "http://example.org"
      // * item[=].extension[=].valueCoding.display = "{score}"
      AssignmentRule firstExtensionUrl = new AssignmentRule("item[=].extension[0].url");
      firstExtensionUrl.value = "http://example.org";
      questionnaireInstance.rules.add(firstExtensionUrl);
      AssignmentRule valueExpressionName = new AssignmentRule("item[=].extension[=].valueExpression.name");
      valueExpressionName.value = "scoreExt";
      questionnaireInstance.rules.add(valueExpressionName);
      AssignmentRule valueExpressionLanguage = new AssignmentRule("item[=].extension[=].valueExpression.language");
      valueExpressionLanguage.value = new FshCode("#text/fhirpath");
      questionnaireInstance.rules.add(valueExpressionLanguage);
      AssignmentRule valueExpressionExpression = new AssignmentRule("item[=].extension[=].valueExpression.expression");
      valueExpressionExpression.value = "'http://hl7.org/fhir/StructureDefinition/ordinalValue'";
      questionnaireInstance.rules.add(valueExpressionExpression);
      AssignmentRule secondExtensionUrl = new AssignmentRule("item[=].extension[+].url");
      secondExtensionUrl.value = "http://example.org";
      questionnaireInstance.rules.add(secondExtensionUrl);
      AssignmentRule valueCodingDisplay = new AssignmentRule("item[=].extension[=].valueCoding.display");
      valueCodingDisplay.value = "{score}";
      questionnaireInstance.rules.add(valueCodingDisplay);
      doc.instances.put(questionnaireInstance.name, questionnaireInstance);
      InstanceDefinition exported = exportInstance.apply(questionnaireInstance);
      // There should be no errors saying that valueExpression.language has min cardinality 1 but occurs 0 times
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertJsonEqual("""
        {
          "resourceType": "Questionnaire",
          "id": "MyQuestionnaire",
          "status": "draft",
          "item": [
            {
              "linkId": "findrisc-score",
              "type": "decimal",
              "extension": [
                {
                  "url": "http://example.org",
                  "valueExpression": {
                    "name": "scoreExt",
                    "language": "#text/fhirpath",
                    "expression": "'http://hl7.org/fhir/StructureDefinition/ordinalValue'"
                  }
                },
                {
                  "url": "http://example.org",
                  "valueCoding": {
                    "display": "{score}"
                  }
                }
              ]
            }
          ]
        }
        """, exported.toJSON());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign fixed value[x] correctly even in weird situations (SUSHI #760)")
    void shouldAssignFixedValueXCorrectlyEvenInWeirdSituationsSUSHI760() {
      // See https://github.com/FHIR/sushi/issues/760
      // Profile: EURMoney
      // Parent: Money
      Profile eurMoney = new Profile("EURMoney");
      eurMoney.parent = "Money";
      // * currency 1..1
      CardRule currencyRequired = new CardRule("currency");
      currencyRequired.min = 1.0;
      eurMoney.rules.add(currencyRequired);
      // * currency = #EUR (exactly)
      AssignmentRule currencyFixed = new AssignmentRule("currency");
      currencyFixed.value = new FshCode("EUR");
      currencyFixed.exactly = true;
      eurMoney.rules.add(currencyFixed);
      doc.profiles.put(eurMoney.name, eurMoney);
      // Extension: InsuranceCost
      Extension insCost = new Extension("InsuranceCost");
      // * extension contains Amount 1..1
      ContainsRule containsAmount = new ContainsRule("extension");
      containsAmount.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Amount"
        }
        """))));
      insCost.rules.add(containsAmount);
      CardRule amountRequired = new CardRule("extension[Amount]");
      amountRequired.min = 1.0;
      insCost.rules.add(amountRequired);
      CardRule noValue = new CardRule("value[x]");
      noValue.max = "0";
      insCost.rules.add(noValue);
      // * extension[Amount].value[x] 1..1
      CardRule amountValueRequired = new CardRule("extension[Amount].value[x]");
      amountValueRequired.min = 1.0;
      insCost.rules.add(amountValueRequired);
      CardRule noExtension = new CardRule("extension[Amount].extension");
      noExtension.max = "0";
      insCost.rules.add(noExtension);
      // * extension[Amount].value[x] only EURMoney
      OnlyRule amountOnlyEurMoney = new OnlyRule("extension[Amount].value[x]");
      amountOnlyEurMoney.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "EURMoney"
        }
        """))));
      insCost.rules.add(amountOnlyEurMoney);
      // * extension[Amount].value[x].value ^short = "Cost"
      CaretValueRule amountMoneyValueShort = new CaretValueRule("extension[Amount].value[x].value");
      amountMoneyValueShort.caretPath = "short";
      amountMoneyValueShort.value = "Cost";
      insCost.rules.add(amountMoneyValueShort);
      doc.extensions.put(insCost.name, insCost);
      // Profile: ObservationProfile
      // Parent: Observation
      Profile observationProfile = new Profile("ObservationProfile");
      observationProfile.parent = "Observation";
      // * extension contains InsuranceCost named InsCost 1..1
      ContainsRule containsInsCost = new ContainsRule("extension");
      containsInsCost.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "InsCost",
          "type": "InsuranceCost"
        }
        """))));
      observationProfile.rules.add(containsInsCost);
      CardRule insCostRequired = new CardRule("extension[InsCost]");
      insCostRequired.min = 1.0;
      observationProfile.rules.add(insCostRequired);
      doc.profiles.put(observationProfile.name, observationProfile);
      // Instance: TestInstance
      // InstanceOf: ObservationProfile
      Instance testInstance = new Instance("TestInstance");
      testInstance.instanceOf = "ObservationProfile";
      // * status = #final
      AssignmentRule statusFinal = new AssignmentRule("status");
      statusFinal.value = new FshCode("final");
      testInstance.rules.add(statusFinal);
      // * code = #testcode
      AssignmentRule codeTestCode = new AssignmentRule("code");
      codeTestCode.value = new FshCode("testcode");
      testInstance.rules.add(codeTestCode);
      // * extension[InsCost].extension[Amount].valueMoney.value = 5.00
      AssignmentRule moneyValueFive = new AssignmentRule("extension[InsCost].extension[Amount].valueMoney.value");
      moneyValueFive.value = 5.0;
      testInstance.rules.add(moneyValueFive);
      doc.instances.put(testInstance.name, testInstance);
      InstanceDefinition exported = exportInstance.apply(testInstance);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/InsuranceCost",
            "extension": [
              {
                "url": "Amount",
                "valueMoney": {
                  "currency": "EUR",
                  "value": 5
                }
              }
            ]
          }
        ]
        """, at(exported, "extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign value[x] to the correct path when the rule on the instance refers to value[x], and value[x] is constrained to one type")
    void shouldAssignValueXToTheCorrectPathWhenTheRuleOnTheInstanceRefersToValueXAndValue() {
      // Profile: ObservationProfile
      // Parent: Observation
      // * value[x] 1..1
      // * value[x] only string
      Profile observationProfile = new Profile("ObservationProfile");
      observationProfile.parent = "Observation";
      CardRule valueCard = new CardRule("value[x]");
      valueCard.min = 1.0;
      valueCard.max = "1";
      OnlyRule valueOnly = new OnlyRule("value[x]");
      valueOnly.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "string"
        }
        """))));
      observationProfile.rules.addAll(Arrays.asList(valueCard, valueOnly));
      doc.profiles.put(observationProfile.name, observationProfile);
      // Instance: MyObservation
      // InstanceOf: ObservationProfile
      // * status = #final
      // * code = #testcode
      // * value[x] = "some value"
      Instance testInstance = new Instance("MyObservation");
      testInstance.instanceOf = "ObservationProfile";
      AssignmentRule statusFinal = new AssignmentRule("status");
      statusFinal.value = new FshCode("final");
      AssignmentRule codeTestCode = new AssignmentRule("code");
      codeTestCode.value = new FshCode("testcode");
      AssignmentRule valueSomeValue = new AssignmentRule("value[x]");
      valueSomeValue.value = "some value";
      testInstance.rules.addAll(Arrays.asList(statusFinal, codeTestCode, valueSomeValue));
      InstanceDefinition exported = exportInstance.apply(testInstance);
      assertEquals("some value", at(exported, "valueString"));
      assertNull(at(exported, "value[x]"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertContainsText("When assigning values on instances, use the choice element's type. Rule path changed from 'value[x]' to 'valueString'.", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error and not assign to a descendant of a choice element when that choice element has more than one type")
    void shouldLogAnErrorAndNotAssignToADescendantOfAChoiceElementWhenThatChoiceElementHa() {
      // Profile: ObservationProfile
      // Parent: Observation
      // * value[x] only string or boolean
      Profile observationProfile = new Profile("ObservationProfile");
      observationProfile.parent = "Observation";
      OnlyRule valueOnly = new OnlyRule("value[x]");
      valueOnly.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "string"
        }
        """)), onlyRuleType(JsAssert.json("""
        {
          "type": "boolean"
        }
        """))));
      observationProfile.rules.add(valueOnly);
      doc.profiles.put(observationProfile.name, observationProfile);
      // Instance: MyObservation
      // InstanceOf: ObservationProfile
      // * status = #final
      // * code = #testcode
      // * value[x].extension.url = "http://example.org/SomeExtension"
      Instance testInstance = new Instance("MyObservation");
      testInstance.instanceOf = "ObservationProfile";
      AssignmentRule statusFinal = new AssignmentRule("status");
      statusFinal.value = new FshCode("final");
      AssignmentRule codeTestCode = new AssignmentRule("code");
      codeTestCode.value = new FshCode("testcode");
      AssignmentRule extensionUrl = new AssignmentRule("value[x].extension.url");
      extensionUrl.value = "http://example.org/SomeExtension";
      testInstance.rules.addAll(Arrays.asList(statusFinal, codeTestCode, extensionUrl));
      InstanceDefinition exported = exportInstance.apply(testInstance);
      assertNull(at(exported, "value[x]"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
      assertContainsText("Unable to assign value at value[x].extension.url: choice elements on an instance must use a specific type", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign an element to a value the same as the assigned value on the Structure Definition")
    void shouldAssignAnElementToAValueTheSameAsTheAssignedValueOnTheStructureDefinition() {
      AssignmentRule assignedValRule = new AssignmentRule("active");
      assignedValRule.value = true;
      assignedValRule.exactly = true;
      patient.rules.add(assignedValRule);
      AssignmentRule instanceAssignedValRule = new AssignmentRule("active");
      instanceAssignedValRule.value = true;
      patientInstance.rules.add(instanceAssignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("true", at(exported, "active"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign an element to a value the same as the assigned pattern on the Structure Definition")
    void shouldAssignAnElementToAValueTheSameAsTheAssignedPatternOnTheStructureDefinition() {
      AssignmentRule assignedValRule = new AssignmentRule("maritalStatus");
      FshCode assignedFshCode = new FshCode("foo", "http://foo.com");
      assignedValRule.value = assignedFshCode;
      patient.rules.add(assignedValRule);
      AssignmentRule instanceAssignedValRule = new AssignmentRule("maritalStatus");
      FshCode instanceAssignedFshCode = new FshCode("foo", "http://foo.com");
      instanceAssignedValRule.value = instanceAssignedFshCode;
      patientInstance.rules.add(instanceAssignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "coding": [
            {
              "code": "foo",
              "system": "http://foo.com"
            }
          ]
        }
        """, at(exported, "maritalStatus"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign an element to a value that is a superset of the assigned pattern on the Structure Definition")
    void shouldAssignAnElementToAValueThatIsASupersetOfTheAssignedPatternOnTheStructureDe() {
      AssignmentRule assignedValRule = new AssignmentRule("maritalStatus");
      FshCode assignedFshCode = new FshCode("foo", "http://foo.com");
      assignedValRule.value = assignedFshCode;
      patient.rules.add(assignedValRule);
      AssignmentRule instanceAssignedValRule = new AssignmentRule("maritalStatus");
      FshCode instanceAssignedFshCode = new FshCode("foo", "http://foo.com", "Foo Foo");
      instanceAssignedValRule.value = instanceAssignedFshCode;
      patientInstance.rules.add(instanceAssignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "coding": [
            {
              "code": "foo",
              "system": "http://foo.com",
              "display": "Foo Foo"
            }
          ]
        }
        """, at(exported, "maritalStatus"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not assign an element to a value different than the assigned value on the Structure Definition")
    void shouldNotAssignAnElementToAValueDifferentThanTheAssignedValueOnTheStructureDefin() {
      AssignmentRule assignedValRule = new AssignmentRule("active");
      assignedValRule.value = true;
      patient.rules.add(assignedValRule);
      CardRule cardRule = new CardRule("active");
      cardRule.min = 1.0;
      patient.rules.add(cardRule);
      AssignmentRule instanceAssignedValRule = new AssignmentRule("active");
      instanceAssignedValRule.value = false;
      patientInstance.rules.add(instanceAssignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals(true, at(exported, "active"));
      assertContainsText("Cannot assign false to this element; a different boolean is already assigned: true", loggerSpy.getLastMessage());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not assign an element to a value different than the pattern value on the Structure Definition")
    void shouldNotAssignAnElementToAValueDifferentThanThePatternValueOnTheStructureDefini() {
      AssignmentRule assignedValRule = new AssignmentRule("maritalStatus");
      FshCode assignedFshCode = new FshCode("foo", "http://foo.com");
      assignedValRule.value = assignedFshCode;
      patient.rules.add(assignedValRule);
      CardRule cardRule = new CardRule("maritalStatus");
      cardRule.min = 1.0;
      patient.rules.add(cardRule);
      AssignmentRule instanceAssignedValRule = new AssignmentRule("maritalStatus");
      FshCode instanceAssignedFshCode = new FshCode("bar", "http://bar.com");
      instanceAssignedValRule.value = instanceAssignedFshCode;
      patientInstance.rules.add(instanceAssignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "code": "foo",
          "system": "http://foo.com"
        }
        """, at(exported, "maritalStatus.coding[0]"));
      assertContainsText("Cannot assign http://bar.com#bar to this element; a different CodeableConcept is already assigned: {\"coding\":[{\"code\":\"foo\",\"system\":\"http://foo.com\"}]}.", loggerSpy.getLastMessage());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign an element to a value different than the pattern value on the Structure Definition on an array")
    void shouldAssignAnElementToAValueDifferentThanThePatternValueOnTheStructureDefinitio() {
      AssignmentRule assignedValRule = new AssignmentRule("maritalStatus");
      FshCode assignedFshCode = new FshCode("foo", "http://foo.com");
      assignedValRule.value = assignedFshCode;
      patient.rules.add(assignedValRule);
      CardRule cardRule = new CardRule("maritalStatus");
      cardRule.min = 1.0;
      patient.rules.add(cardRule);
      AssignmentRule instanceAssignedValRule = new AssignmentRule("maritalStatus.coding[1]");
      FshCode instanceAssignedFshCode = new FshCode("bar", "http://bar.com");
      instanceAssignedValRule.value = instanceAssignedFshCode;
      patientInstance.rules.add(instanceAssignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "code": "foo",
          "system": "http://foo.com"
        }
        """, at(exported, "maritalStatus.coding[0]"));
      assertJsonEqual("""
        {
          "code": "bar",
          "system": "http://bar.com"
        }
        """, at(exported, "maritalStatus.coding[1]"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    // Nested elements
    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a nested element that has parents defined in the instance and is assigned on the Structure Definition")
    void shouldAssignANestedElementThatHasParentsDefinedInTheInstanceAndIsAssignedOnTheSt() {
      CardRule cardRule = new CardRule("communication.preferred");
      cardRule.min = 1.0;
      patient.rules.add(cardRule);
      AssignmentRule assignedValRule = new AssignmentRule("communication.preferred");
      assignedValRule.value = true;
      patient.rules.add(assignedValRule);
      AssignmentRule instanceAssignedValRule = new AssignmentRule("communication[0].language");
      instanceAssignedValRule.value = new FshCode("foo");
      patientInstance.rules.add(instanceAssignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "preferred": true,
          "language": {
            "coding": [
              {
                "code": "foo"
              }
            ]
          }
        }
        """, at(exported, "communication[0]"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a nested element that has parents and children defined in the instance and is assigned on the Structure Definition")
    void shouldAssignANestedElementThatHasParentsAndChildrenDefinedInTheInstanceAndIsAssi() {
      CardRule cardRule = new CardRule("communication.language.text");
      cardRule.min = 1.0;
      patient.rules.add(cardRule);
      AssignmentRule assignedValRule = new AssignmentRule("communication.language.text");
      assignedValRule.value = "foo";
      patient.rules.add(assignedValRule);
      AssignmentRule instanceAssignedValRule = new AssignmentRule("communication[0].language.coding[0].version");
      instanceAssignedValRule.value = "bar";
      patientInstance.rules.add(instanceAssignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "language": {
            "text": "foo",
            "coding": [
              {
                "version": "bar"
              }
            ]
          }
        }
        """, at(exported, "communication[0]"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not assign a nested element that does not have parents defined in the instance")
    void shouldNotAssignANestedElementThatDoesNotHaveParentsDefinedInTheInstance() {
      AssignmentRule assignedValRule = new AssignmentRule("communication.preferred");
      assignedValRule.value = true;
      patient.rules.add(assignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertNull(at(exported, "communication"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a nested element that has parents defined in the instance and assigned on the SD to an array even if constrained")
    void shouldAssignANestedElementThatHasParentsDefinedInTheInstanceAndAssignedOnTheSDTo() {
      CardRule cardRule = new CardRule("contact");
      cardRule.min = 1.0;
      cardRule.max = "1";
      patient.rules.add(cardRule);
      CardRule cardRuleRelationship = new CardRule("contact.relationship");
      cardRuleRelationship.min = 1.0;
      cardRuleRelationship.max = "*";
      patient.rules.add(cardRuleRelationship);
      AssignmentRule assignedValRule = new AssignmentRule("contact.relationship");
      assignedValRule.value = new FshCode("mother");
      patient.rules.add(assignedValRule);
      AssignmentRule instanceAssignedValRule = new AssignmentRule("contact.gender");
      instanceAssignedValRule.value = new FshCode("foo");
      patientInstance.rules.add(instanceAssignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "gender": "foo",
            "relationship": [
              {
                "coding": [
                  {
                    "code": "mother"
                  }
                ]
              }
            ]
          }
        ]
        """, at(exported, "contact"));
    }

    // Deeply Nested Elements
    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a deeply nested element that is assigned on the Structure Definition and has 1..1 parents")
    void shouldAssignADeeplyNestedElementThatIsAssignedOnTheStructureDefinitionAndHas11Pa() {
      // * telecom.period 1..1
      // * telecom.period.start 1..1
      // * telecom.period.start = "2000-07-04"
      CardRule periodCard = new CardRule("telecom.period");
      periodCard.min = 1.0;
      periodCard.max = "1";
      CardRule startCard = new CardRule("telecom.period.start");
      startCard.min = 1.0;
      startCard.max = "1";
      AssignmentRule assignedValRule = new AssignmentRule("telecom.period.start");
      assignedValRule.value = "2000-07-04";
      patient.rules.addAll(Arrays.asList(periodCard, startCard, assignedValRule));
      AssignmentRule instanceAssignedValRule = new AssignmentRule("telecom[0].system");
      instanceAssignedValRule.value = new FshCode("email");
      patientInstance.rules.add(instanceAssignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "system": "email",
          "period": {
            "start": "2000-07-04"
          }
        }
        """, at(exported, "telecom[0]"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not get confused by matching path parts when assigning deeply nested elements")
    void shouldNotGetConfusedByMatchingPathPartsWhenAssigningDeeplyNestedElements() {
      // * maritalStatus, maritalStatus.coding, maritalStatus.coding.system 1..1
      // * maritalStatus.coding.system = "http://itscomplicated.com"
      CardRule statCard = new CardRule("maritalStatus");
      statCard.min = 1.0;
      statCard.max = "1";
      CardRule codingCard = new CardRule("maritalStatus.coding");
      codingCard.min = 1.0;
      codingCard.max = "1";
      CardRule sysCard = new CardRule("maritalStatus.coding.system");
      sysCard.min = 1.0;
      sysCard.max = "1";
      AssignmentRule assignedValRule = new AssignmentRule("maritalStatus.coding.system");
      assignedValRule.value = "http://itscomplicated.com";
      patient.rules.addAll(Arrays.asList(statCard, codingCard, sysCard, assignedValRule));
      AssignmentRule instanceAssignedValRule = new AssignmentRule("generalPractitioner.identifier.system");
      instanceAssignedValRule.value = "http://medicine.med";
      patientInstance.rules.add(instanceAssignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "coding": [
            {
              "system": "http://itscomplicated.com"
            }
          ]
        }
        """, at(exported, "maritalStatus"));
      assertJsonEqual("""
        [
          {
            "identifier": {
              "system": "http://medicine.med"
            }
          }
        ]
        """, at(exported, "generalPractitioner"));
      List<String> messages = loggerSpy.getAllMessages("error");
      assertEquals(0, messages.size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a deeply nested element that is assigned on the Structure Definition and has array parents with min > 1")
    void shouldAssignADeeplyNestedElementThatIsAssignedOnTheStructureDefinitionAndHasArra() {
      // * identifier 2..*
      // * identifier.type.coding 2..*
      // * identifier.type.coding.display 1..1
      // * identifier.type.coding.display = "This is my coding"
      CardRule idCard = new CardRule("identifier");
      idCard.min = 2.0;
      idCard.max = "*";
      CardRule typeCard = new CardRule("identifier.type.coding");
      typeCard.min = 2.0;
      typeCard.max = "*";
      CardRule displayCard = new CardRule("identifier.type.coding.display");
      displayCard.min = 1.0;
      displayCard.max = "1";
      AssignmentRule assignedValRule = new AssignmentRule("identifier.type.coding.display");
      assignedValRule.value = "This is my coding";
      patient.rules.addAll(Arrays.asList(idCard, typeCard, displayCard, assignedValRule));
      AssignmentRule instanceAssignedValRule = new AssignmentRule("identifier.type.coding[2].version");
      instanceAssignedValRule.value = "1.2.3";
      patientInstance.rules.add(instanceAssignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "type": {
              "coding": [
                {
                  "display": "This is my coding"
                },
                {
                  "display": "This is my coding"
                },
                {
                  "display": "This is my coding",
                  "version": "1.2.3"
                }
              ]
            }
          }
        ]
        """, at(exported, "identifier"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a deeply nested element that is assigned on the Structure Definition and has slice array parents with min > 1")
    void shouldAssignADeeplyNestedElementThatIsAssignedOnTheStructureDefinitionAndHasSlic() {
      // * category contains niceSlice
      // * category[niceSlice] 1..1
      // * category[niceSlice] = http://spice.com#rice
      ContainsRule containsRule = new ContainsRule("category");
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "niceSlice"
        }
        """))));
      respRate.rules.add(containsRule);
      CardRule cardRule = new CardRule("category[niceSlice]");
      cardRule.min = 1.0;
      cardRule.max = "1";
      respRate.rules.add(cardRule);
      AssignmentRule assignedValRule = new AssignmentRule("category[niceSlice]");
      FshCode assignedFshCode = new FshCode("rice", "http://spice.com");
      assignedValRule.value = assignedFshCode;
      respRate.rules.addAll(Arrays.asList(containsRule, cardRule, assignedValRule));
      InstanceDefinition exported = exportInstance.apply(respRateInstance);
      assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "rice",
                "system": "http://spice.com"
              }
            ]
          },
          {
            "coding": [
              {
                "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                "code": "vital-signs"
              }
            ]
          }
        ]
        """, at(exported, "category"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should create additional elements when assigning primitive implied properties from named slices")
    void shouldCreateAdditionalElementsWhenAssigningPrimitiveImpliedPropertiesFromNamedSl() {
      // Profile: ManateePatient
      // Parent: Patient
      // * contact.name.given ^slicing.discriminator.type = #pattern
      // * contact.name.given ^slicing.discriminator.path = "$this"
      // * contact.name.given ^slicing.rules = #open
      // * contact.name.given contains Manatee 1..1
      // * contact.name.given[Manatee] = "Manatee"
      Profile manateePatient = new Profile("ManateePatient");
      manateePatient.parent = "Patient";
      CaretValueRule discriminatorType = new CaretValueRule("contact.name.given");
      discriminatorType.caretPath = "slicing.discriminator.type";
      discriminatorType.value = new FshCode("pattern");
      CaretValueRule discriminatorPath = new CaretValueRule("contact.name.given");
      discriminatorPath.caretPath = "slicing.discriminator.path";
      discriminatorPath.value = "$this";
      CaretValueRule slicingRules = new CaretValueRule("contact.name.given");
      slicingRules.caretPath = "slicing.rules";
      slicingRules.value = new FshCode("open");
      ContainsRule manateeContains = new ContainsRule("contact.name.given");
      manateeContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Manatee"
        }
        """))));
      CardRule manateeCard = new CardRule("contact.name.given[Manatee]");
      manateeCard.min = 1.0;
      manateeCard.max = "1";
      AssignmentRule manateeAssignment = new AssignmentRule("contact.name.given[Manatee]");
      manateeAssignment.value = "Manatee";
      manateePatient.rules.addAll(Arrays.asList(discriminatorType, discriminatorPath, slicingRules, manateeContains, manateeCard, manateeAssignment));
      doc.profiles.put(manateePatient.name, manateePatient);
      // Profile: SeacowPatient
      // Parent: ManateePatient
      // * contact.name 1..1
      // * contact.name = SeacowName
      Profile seacowPatient = new Profile("SeacowPatient");
      seacowPatient.parent = "ManateePatient";
      CardRule seacowCard = new CardRule("contact.name");
      seacowCard.min = 1.0;
      seacowCard.max = "1";
      AssignmentRule seacowAssignment = new AssignmentRule("contact.name");
      seacowAssignment.value = "SeacowName";
      seacowAssignment.isInstance = true;
      seacowPatient.rules.addAll(Arrays.asList(seacowCard, seacowAssignment));
      doc.profiles.put(seacowPatient.name, seacowPatient);
      // Instance: SeacowName
      // InstanceOf: HumanName
      // Usage: #inline
      // * given[0] = "Seacow"
      Instance seacowName = new Instance("SeacowName");
      seacowName.instanceOf = "HumanName";
      seacowName.usage = InstanceUsage.Inline;
      AssignmentRule seacowGiven = new AssignmentRule("given[0]");
      seacowGiven.value = "Seacow";
      seacowName.rules.add(seacowGiven);
      doc.instances.put(seacowName.name, seacowName);
      exportInstance.apply(seacowName);
      // Instance: ThisIsSeacow
      // InstanceOf: SeacowPatient
      // * contact.name = SeacowName
      Instance thisIsSeacow = new Instance("ThisIsSeacow");
      thisIsSeacow.instanceOf = "SeacowPatient";
      AssignmentRule thisIsName = new AssignmentRule("contact.name");
      thisIsName.value = "SeacowName";
      thisIsName.isInstance = true;
      thisIsSeacow.rules.add(thisIsName);
      InstanceDefinition exported = exportInstance.apply(thisIsSeacow);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertJsonEqual("""
        [
          "Manatee",
          "Seacow"
        ]
        """, at(exported, "contact[0].name.given"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not create additional elements when assigning implied properties from named slices")
    void shouldNotCreateAdditionalElementsWhenAssigningImpliedPropertiesFromNamedSlices() {
      // Profile: ObservationProfile
      // Parent: observation-bodyweight
      // * code = http://loinc.org#29463-7
      Profile observationProfile = new Profile("ObservationProfile");
      observationProfile.parent = "http://hl7.org/fhir/StructureDefinition/bodyweight";
      AssignmentRule profileCode = new AssignmentRule("code");
      profileCode.value = new FshCode("29463-7", "http://loinc.org");
      profileCode.exactly = false;
      observationProfile.rules.add(profileCode);
      doc.profiles.put(observationProfile.name, observationProfile);
      // Instance: MyObservation
      // InstanceOf: ObservationProfile
      // * code = http://loinc.org#29463-7 "this should be the only one"
      // * status = #final
      Instance observationInstance = new Instance("MyObservation");
      observationInstance.instanceOf = "ObservationProfile";
      AssignmentRule instanceCode = new AssignmentRule("code");
      instanceCode.value = new FshCode("29463-7", "http://loinc.org", "this should be the only one");
      AssignmentRule instanceStatus = new AssignmentRule("status");
      instanceStatus.value = new FshCode("final");
      observationInstance.rules.addAll(Arrays.asList(instanceCode, instanceStatus));
      InstanceDefinition exported = exportInstance.apply(observationInstance);
      assertEquals(1, len(at(exported, "code.coding")));
      assertJsonEqual("""
        {
          "code": "29463-7",
          "system": "http://loinc.org",
          "display": "this should be the only one"
        }
        """, at(exported, "code.coding[0]"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should create additional elements when assigning implied properties if the value on the named slice and on an ancestor element are different")
    void shouldCreateAdditionalElementsWhenAssigningImpliedPropertiesIfTheValueOnTheNamed() {
      // Profile: ObservationProfile
      // Parent: observation-bodyweight
      // * code = http://loinc.org#54126-8
      Profile observationProfile = new Profile("ObservationProfile");
      observationProfile.parent = "http://hl7.org/fhir/StructureDefinition/bodyweight";
      AssignmentRule profileCode = new AssignmentRule("code");
      profileCode.value = new FshCode("54126-8", "http://loinc.org");
      profileCode.exactly = false;
      observationProfile.rules.add(profileCode);
      doc.profiles.put(observationProfile.name, observationProfile);
      // Instance: MyObservation
      // InstanceOf: ObservationProfile
      // * code = http://loinc.org#54126-8
      // * status = #final
      Instance observationInstance = new Instance("MyObservation");
      observationInstance.instanceOf = "ObservationProfile";
      AssignmentRule instanceCode = new AssignmentRule("code");
      instanceCode.value = new FshCode("54126-8", "http://loinc.org");
      AssignmentRule instanceStatus = new AssignmentRule("status");
      instanceStatus.value = new FshCode("final");
      observationInstance.rules.addAll(Arrays.asList(instanceCode, instanceStatus));
      InstanceDefinition exported = exportInstance.apply(observationInstance);
      assertEquals(2, len(at(exported, "code.coding")));
      assertJsonEqual("""
        {
          "code": "29463-7",
          "system": "http://loinc.org"
        }
        """, at(exported, "code.coding[0]"));
      assertJsonEqual("""
        {
          "code": "54126-8",
          "system": "http://loinc.org"
        }
        """, at(exported, "code.coding[1]"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not create additional elements when assigning implied properties on descdendants of named slices")
    void shouldNotCreateAdditionalElementsWhenAssigningImpliedPropertiesOnDescdendantsOfN() {
      // Profile: ObservationProfile
      // Parent: Observation
      // * component ^slicing.discriminator.type = #pattern
      // * component ^slicing.discriminator.path = "code"
      // * component ^slicing.rules = #open
      // * component contains MySlice 1..1
      // * component[MySlice].code.coding 1..*
      // * component[MySlice].code = http://foo#bar
      // * component[MySlice].code.coding ^slicing.discriminator.type = #pattern
      // * component[MySlice].code.coding ^slicing.discriminator.path = "$this"
      // * component[MySlice].code.coding ^slicing.rules = #open
      // * component[MySlice].code.coding contains MySubSlice 1..1
      // * component[MySlice].code.coding[MySubSlice] = http://foo#bar "extra display"
      Profile observationProfile = new Profile("ObservationProfile");
      observationProfile.parent = "Observation";
      CaretValueRule componentType = new CaretValueRule("component");
      componentType.caretPath = "slicing.discriminator.type";
      componentType.value = new FshCode("pattern");
      CaretValueRule componentPath = new CaretValueRule("component");
      componentPath.caretPath = "slicing.discriminator.path";
      componentPath.value = "code";
      CaretValueRule componentRules = new CaretValueRule("component");
      componentRules.caretPath = "slicing.rules";
      componentRules.value = new FshCode("open");
      ContainsRule componentContains = new ContainsRule("component");
      componentContains.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "MySlice"
        }
        """)));
      CardRule mySliceCard = new CardRule("component[MySlice]");
      mySliceCard.min = 1.0;
      mySliceCard.max = "1";
      CardRule codingCard = new CardRule("component[MySlice].code.coding");
      codingCard.min = 1.0;
      codingCard.max = "*";
      AssignmentRule codeAssignment = new AssignmentRule("component[MySlice].code");
      codeAssignment.value = new FshCode("bar", "http://foo");
      CaretValueRule codingType = new CaretValueRule("component[MySlice].code.coding");
      codingType.caretPath = "slicing.discriminator.type";
      codingType.value = new FshCode("pattern");
      CaretValueRule codingPath = new CaretValueRule("component[MySlice].code.coding");
      codingPath.caretPath = "slicing.discriminator.path";
      codingPath.value = "$this";
      CaretValueRule codingRules = new CaretValueRule("component[MySlice].code.coding");
      codingRules.caretPath = "slicing.rules";
      codingRules.value = new FshCode("open");
      ContainsRule codingContains = new ContainsRule("component[MySlice].code.coding");
      codingContains.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "MySubSlice"
        }
        """)));
      CardRule mySubSliceCard = new CardRule("component[MySlice].code.coding[MySubSlice]");
      mySubSliceCard.min = 1.0;
      mySubSliceCard.max = "1";
      AssignmentRule subSliceAssignment = new AssignmentRule("component[MySlice].code.coding[MySubSlice]");
      subSliceAssignment.value = new FshCode("bar", "http://foo", "extra display");
      observationProfile.rules.addAll(Arrays.asList(componentType, componentPath, componentRules, componentContains, mySliceCard, codingCard, codeAssignment, codingType, codingPath, codingRules, codingContains, mySubSliceCard, subSliceAssignment));
      doc.profiles.put(observationProfile.name, observationProfile);
      // Instance: ObservationInstance
      // InstanceOf: ObservationProfile
      // * code = http://any.com#code
      // * status = #final
      Instance observationInstance = new Instance("ObservationInstance");
      observationInstance.instanceOf = "ObservationProfile";
      AssignmentRule instanceCode = new AssignmentRule("code");
      instanceCode.value = new FshCode("code", "http://any.com");
      AssignmentRule instanceStatus = new AssignmentRule("status");
      instanceStatus.value = new FshCode("final");
      observationInstance.rules.addAll(Arrays.asList(instanceCode, instanceStatus));
      InstanceDefinition exported = exportInstance.apply(observationInstance);
      assertEquals(1, len(at(exported, "component[0].code.coding")));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not assign a deeply nested element that is assigned on the Structure Definition but does not have 1..1 parents")
    void shouldNotAssignADeeplyNestedElementThatIsAssignedOnTheStructureDefinitionButDoes() {
      // * telecom.period 0..1 // Element is optional
      // * telecom.period.start 1..1
      // * telecom.period.start = "2000-07-04"
      CardRule periodCard = new CardRule("telecom.period");
      periodCard.min = 0.0;
      periodCard.max = "1";
      CardRule startCard = new CardRule("telecom.period.start");
      startCard.min = 1.0;
      startCard.max = "1";
      AssignmentRule assignedValRule = new AssignmentRule("telecom.period.start");
      assignedValRule.value = "2000-07-04";
      patient.rules.addAll(Arrays.asList(periodCard, startCard, assignedValRule));
      AssignmentRule instanceAssignedValRule = new AssignmentRule("telecom[0].system");
      instanceAssignedValRule.value = new FshCode("email");
      patientInstance.rules.add(instanceAssignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "system": "email"
        }
        """, at(exported, "telecom[0]"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log a warning when assigning a value to an element nested within an element with multiple profiles")
    void shouldLogAWarningWhenAssigningAValueToAnElementNestedWithinAnElementWithMultiple() {
      // Consider two Identifier profiles with mutually incompatible assigned values
      Profile regularIdentifier = new Profile("RegularIdentifier");
      regularIdentifier.parent = "Identifier";
      AssignmentRule regularSystem = new AssignmentRule("system");
      regularSystem.value = "http://example.org/regular";
      regularIdentifier.rules.add(regularSystem);
      doc.profiles.put(regularIdentifier.name, regularIdentifier);
      Profile unusualIdentifier = new Profile("UnusualIdentifier");
      unusualIdentifier.parent = "Identifier";
      AssignmentRule unusualSystem = new AssignmentRule("system");
      unusualSystem.value = "http://example.org/unusual";
      unusualIdentifier.rules.add(unusualSystem);
      doc.profiles.put(unusualIdentifier.name, unusualIdentifier);
      // In TestPatient, give the generalPractitioner.identifier element multiple profiles
      OnlyRule gpOnly = new OnlyRule("generalPractitioner.identifier");
      gpOnly.types.addAll(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "RegularIdentifier"
        }
        """)), onlyRuleType(JsAssert.json("""
        {
          "type": "UnusualIdentifier"
        }
        """))));
      patient.rules.add(gpOnly);
      // Assign a value to generalPractitioner.identifier.value, which is nested within the profiled element
      AssignmentRule gpValue = new AssignmentRule("generalPractitioner.identifier.value");
      gpValue.value = "12345";
      // Assign a value to generalPractitioner.identifier.system. This value is invalid on both profiles, but those profiles are being ignored.
      AssignmentRule gpSystem = new AssignmentRule("generalPractitioner.identifier.system");
      gpSystem.value = "http://example.org/something-else";
      patientInstance.rules.addAll(Arrays.asList(gpValue, gpSystem));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      // The assigned values should be present
      assertEquals("12345", at(exported, "generalPractitioner[0].identifier.value"));
      assertEquals("http://example.org/something-else", at(exported, "generalPractitioner[0].identifier.system"));
      // We should receive a warning that the profiles are being ignored
      assertContainsText("Multiple profiles present on element Patient.generalPractitioner.identifier. Base element type will be used instead of any profiles.", loggerSpy.getLastMessage("warn"));
    }

    // Assigning with pattern[x]
    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a nested element that is assigned by pattern[x] from a parent on the SD")
    void shouldAssignANestedElementThatIsAssignedByPatternXFromAParentOnTheSD() {
      AssignmentRule assignedValRule = new AssignmentRule("maritalStatus.coding");
      assignedValRule.value = new FshCode("foo", "http://foo.com");
      patient.rules.add(assignedValRule);
      AssignmentRule instanceAssignedValRule = new AssignmentRule("maritalStatus.coding[0].version");
      instanceAssignedValRule.value = "1.2.3";
      patientInstance.rules.add(instanceAssignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "coding": [
            {
              "code": "foo",
              "system": "http://foo.com",
              "version": "1.2.3"
            }
          ]
        }
        """, at(exported, "maritalStatus"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign multiple nested elements that are assigned by pattern[x] from a parent on the SD")
    void shouldAssignMultipleNestedElementsThatAreAssignedByPatternXFromAParentOnTheSD() {
      AssignmentRule assignedValRule = new AssignmentRule("maritalStatus.coding");
      assignedValRule.value = new FshCode("foo", "http://foo.com");
      patient.rules.add(assignedValRule);
      AssignmentRule instanceAssignedValRule = new AssignmentRule("maritalStatus.coding[0].version");
      instanceAssignedValRule.value = "1.2.3";
      patientInstance.rules.add(instanceAssignedValRule);
      AssignmentRule instanceAssignedValRule2 = new AssignmentRule("maritalStatus.coding[1].version");
      instanceAssignedValRule2.value = "3.2.1";
      patientInstance.rules.add(instanceAssignedValRule2);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "coding": [
            {
              "code": "foo",
              "system": "http://foo.com",
              "version": "1.2.3"
            },
            {
              "code": "foo",
              "system": "http://foo.com",
              "version": "3.2.1"
            }
          ]
        }
        """, at(exported, "maritalStatus"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a nested element that is assigned by array pattern[x] from a parent on the SD")
    void shouldAssignANestedElementThatIsAssignedByArrayPatternXFromAParentOnTheSD() {
      AssignmentRule assignedValRule = new AssignmentRule("maritalStatus");
      assignedValRule.value = new FshCode("foo", "http://foo.com");
      patient.rules.add(assignedValRule);
      AssignmentRule instanceAssignedValRule = new AssignmentRule("maritalStatus.coding[0].version");
      instanceAssignedValRule.value = "1.2.3";
      patientInstance.rules.add(instanceAssignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "coding": [
            {
              "code": "foo",
              "system": "http://foo.com",
              "version": "1.2.3"
            }
          ]
        }
        """, at(exported, "maritalStatus"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign multiple nested elements that are assigned by array pattern[x] from a parent on the SD")
    void shouldAssignMultipleNestedElementsThatAreAssignedByArrayPatternXFromAParentOnThe() {
      AssignmentRule assignedValRule = new AssignmentRule("maritalStatus");
      assignedValRule.value = new FshCode("foo", "http://foo.com");
      patient.rules.add(assignedValRule);
      AssignmentRule instanceAssignedValRule1 = new AssignmentRule("maritalStatus.coding[0].version");
      instanceAssignedValRule1.value = "1.2.3";
      patientInstance.rules.add(instanceAssignedValRule1);
      AssignmentRule instanceAssignedValRule2 = new AssignmentRule("maritalStatus.coding[1].version");
      instanceAssignedValRule2.value = "3.2.1";
      patientInstance.rules.add(instanceAssignedValRule2);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "coding": [
            {
              "code": "foo",
              "system": "http://foo.com",
              "version": "1.2.3"
            },
            {
              "version": "3.2.1"
            }
          ]
        }
        """, at(exported, "maritalStatus"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign elements with soft indexing used within a path")
    void shouldAssignElementsWithSoftIndexingUsedWithinAPath() {
      AssignmentRule assignedValRule = new AssignmentRule("name[+].given");
      assignedValRule.value = "John";
      patientInstance.rules.add(assignedValRule);
      AssignmentRule assignedValRule2 = new AssignmentRule("name[=].family");
      assignedValRule2.value = "Johnson";
      patientInstance.rules.add(assignedValRule2);
      AssignmentRule assignedValRule3 = new AssignmentRule("name[+].given");
      assignedValRule3.value = "Johnny";
      patientInstance.rules.add(assignedValRule3);
      AssignmentRule assignedValRule4 = new AssignmentRule("name[=].family");
      assignedValRule4.value = "Jackson";
      patientInstance.rules.add(assignedValRule4);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "given": [
              "John"
            ],
            "family": "Johnson"
          },
          {
            "given": [
              "Johnny"
            ],
            "family": "Jackson"
          }
        ]
        """, at(exported, "name"));
    }

    @DisplayName("strict slice name usage")
    @Nested
    class StrictSliceNameUsage {

      @BeforeEach
      void beforeEach() {
        tank.config.instanceOptions = instanceOptions(obj(SPREAD, tank.config.instanceOptions, "manualSliceOrdering", true));
      }

      @AfterEach
      void afterEach() {
        tank.config.instanceOptions = instanceOptions(obj(SPREAD, tank.config.instanceOptions, "manualSliceOrdering", false));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should assign elements with soft indexing and named slices used in combination when enforcing strict slice name usage")
      void shouldAssignElementsWithSoftIndexingAndNamedSlicesUsedInCombinationWhenEnforcing() {
        // * contact ^slicing.discriminator.type = #pattern
        // * contact ^slicing.discriminator.path = "relationship"
        // * contact ^slicing.rules = #open
        // * contact contains Partners 0..* and Friends 1..*
        // * contact[Partners].relationship = #partner
        // * contact[Friends].relationship = #friend
        CaretValueRule slicingType = new CaretValueRule("contact");
        slicingType.caretPath = "slicing.discriminator.type";
        slicingType.value = new FshCode("pattern");
        CaretValueRule slicingPath = new CaretValueRule("contact");
        slicingPath.caretPath = "slicing.discriminator.path";
        slicingPath.value = "relationship";
        CaretValueRule slicingRules = new CaretValueRule("contact");
        slicingRules.caretPath = "slicing.rules";
        slicingRules.value = new FshCode("open");
        ContainsRule contactContains = new ContainsRule("contact");
        contactContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Partners"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "Friends"
        }
        """))));
        CardRule partnersCard = new CardRule("contact[Partners]");
        partnersCard.min = 0.0;
        partnersCard.max = "*";
        CardRule friendsCard = new CardRule("contact[Friends]");
        friendsCard.min = 1.0;
        friendsCard.max = "*";
        AssignmentRule partnerRelationship = new AssignmentRule("contact[Partners].relationship");
        partnerRelationship.value = new FshCode("partner");
        AssignmentRule friendRelationship = new AssignmentRule("contact[Friends].relationship");
        friendRelationship.value = new FshCode("friend");
        patient.rules.addAll(Arrays.asList(slicingType, slicingPath, slicingRules, contactContains, partnersCard, friendsCard, partnerRelationship, friendRelationship));
        // * contact.name.given = "Barret"
        // * contact[Partners].name.given = "Cloud"
        // * contact[Partners][+].name.given = "Aerith"
        // * contact[Friends][+].name.given = "Biggs"
        // * contact[Friends][+].name.given = "Wedge"
        // * contact[+].name.given = "Marle"
        AssignmentRule barretName = new AssignmentRule("contact.name.given");
        barretName.value = "Barret";
        AssignmentRule cloudName = new AssignmentRule("contact[Partners][+].name.given");
        cloudName.value = "Cloud";
        AssignmentRule aerithName = new AssignmentRule("contact[Partners][+].name.given");
        aerithName.value = "Aerith";
        AssignmentRule biggsName = new AssignmentRule("contact[Friends][+].name.given");
        biggsName.value = "Biggs";
        AssignmentRule wedgeName = new AssignmentRule("contact[Friends][+].name.given");
        wedgeName.value = "Wedge";
        AssignmentRule marleName = new AssignmentRule("contact[+].name.given");
        marleName.value = "Marle";
        patientInstance.rules.addAll(Arrays.asList(barretName, cloudName, aerithName, biggsName, wedgeName, marleName));
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        [
          {
            "name": {
              "given": [
                "Barret"
              ]
            }
          },
          {
            "name": {
              "given": [
                "Cloud"
              ]
            },
            "relationship": [
              {
                "coding": [
                  {
                    "code": "partner"
                  }
                ]
              }
            ]
          },
          {
            "name": {
              "given": [
                "Aerith"
              ]
            },
            "relationship": [
              {
                "coding": [
                  {
                    "code": "partner"
                  }
                ]
              }
            ]
          },
          {
            "name": {
              "given": [
                "Biggs"
              ]
            },
            "relationship": [
              {
                "coding": [
                  {
                    "code": "friend"
                  }
                ]
              }
            ]
          },
          {
            "name": {
              "given": [
                "Wedge"
              ]
            },
            "relationship": [
              {
                "coding": [
                  {
                    "code": "friend"
                  }
                ]
              }
            ]
          },
          {
            "name": {
              "given": [
                "Marle"
              ]
            }
          }
        ]
        """, at(exported, "contact"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should assign elements with implied values on required slices when enforcing strict slice name usage")
      void shouldAssignElementsWithImpliedValuesOnRequiredSlicesWhenEnforcingStrictSliceNam() {
        // Profile: BreadObservation
        // Parent: Observation
        // * component ^slicing.discriminator.type = #pattern
        // * component ^slicing.discriminator.path = "value"
        // * component ^slicing.rules = #open
        // * component contains Bread 0..*
        // * component[Bread].code = http://loinc.org#48018-6
        // * component[Bread] ^slicing.discriminator.type = #pattern
        // * component[Bread] ^slicing.discriminator.path = "$this"
        // * component[Bread] ^slicing.rules = #open
        // * component[Bread] contains Rye 1..1 and Wheat 1..1
        // * component[Bread][Rye].valueString 1..1
        // * component[Bread][Rye].valueString = "Rye"
        // * component[Bread][Wheat].valueString 1..1
        // * component[Bread][Wheat].valueString = "Wheat"
        Profile breadObservation = new Profile("BreadObservation");
        breadObservation.parent = "Observation";
        CaretValueRule componentSlicingType = new CaretValueRule("component");
        componentSlicingType.caretPath = "slicing.discriminator.type";
        componentSlicingType.value = new FshCode("pattern");
        CaretValueRule componentSlicingPath = new CaretValueRule("component");
        componentSlicingPath.caretPath = "slicing.discriminator.path";
        componentSlicingPath.value = "value";
        CaretValueRule componentSlicingRules = new CaretValueRule("component");
        componentSlicingRules.caretPath = "slicing.rules";
        componentSlicingRules.value = new FshCode("open");
        ContainsRule componentContains = new ContainsRule("component");
        componentContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Bread"
        }
        """))));
        CardRule breadCard = new CardRule("component[Bread]");
        breadCard.min = 0.0;
        breadCard.max = "*";
        AssignmentRule breadCode = new AssignmentRule("component[Bread].code");
        breadCode.value = new FshCode("48018-6", "http://loinc.org");
        CaretValueRule breadSlicingType = new CaretValueRule("component[Bread]");
        breadSlicingType.caretPath = "slicing.discriminator.type";
        breadSlicingType.value = new FshCode("pattern");
        CaretValueRule breadSlicingPath = new CaretValueRule("component[Bread]");
        breadSlicingPath.caretPath = "slicing.discriminator.path";
        breadSlicingPath.value = "$this";
        CaretValueRule breadSlicingRules = new CaretValueRule("component[Bread]");
        breadSlicingRules.caretPath = "slicing.rules";
        breadSlicingRules.value = new FshCode("open");
        ContainsRule breadContains = new ContainsRule("component[Bread]");
        breadContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Rye"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "Wheat"
        }
        """))));
        CardRule ryeCard = new CardRule("component[Bread][Rye]");
        ryeCard.min = 1.0;
        ryeCard.max = "1";
        CardRule wheatCard = new CardRule("component[Bread][Wheat]");
        wheatCard.min = 1.0;
        wheatCard.max = "1";
        CardRule ryeValueCard = new CardRule("component[Bread][Rye].valueString");
        ryeValueCard.min = 1.0;
        ryeValueCard.max = "1";
        AssignmentRule ryeValueString = new AssignmentRule("component[Bread][Rye].valueString");
        ryeValueString.value = "Rye";
        CardRule wheatValueCard = new CardRule("component[Bread][Wheat].valueString");
        wheatValueCard.min = 1.0;
        wheatValueCard.max = "1";
        AssignmentRule wheatValueString = new AssignmentRule("component[Bread][Wheat].valueString");
        wheatValueString.value = "Wheat";
        breadObservation.rules.addAll(Arrays.asList(componentSlicingType, componentSlicingPath, componentSlicingRules, componentContains, breadCard, breadCode, breadSlicingType, breadSlicingPath, breadSlicingRules, breadContains, ryeCard, wheatCard, ryeValueCard, ryeValueString, wheatValueCard, wheatValueString));
        doc.profiles.put(breadObservation.name, breadObservation);
        // Instance: BreadInstance
        // InstanceOf: BreadObservation
        // * status = #final
        // * code = #bread
        // * component[Bread][Wheat].interpretation = #low
        // * component[Bread].interpretation = #high
        Instance breadInstance = new Instance("BreadInstance");
        breadInstance.instanceOf = "BreadObservation";
        AssignmentRule instanceStatus = new AssignmentRule("status");
        instanceStatus.value = new FshCode("final");
        AssignmentRule instanceCode = new AssignmentRule("code");
        instanceCode.value = new FshCode("bread");
        AssignmentRule wheatInterpretation = new AssignmentRule("component[Bread][Wheat].interpretation");
        wheatInterpretation.value = new FshCode("low");
        AssignmentRule breadInterpretation = new AssignmentRule("component[Bread].interpretation");
        breadInterpretation.value = new FshCode("high");
        breadInstance.rules.addAll(Arrays.asList(instanceStatus, instanceCode, wheatInterpretation, breadInterpretation));
        doc.instances.put(breadInstance.name, breadInstance);
        InstanceDefinition result = exportInstance.apply(breadInstance);
        assertEquals(3, len(at(result, "component")));
        // Bread/Wheat[0] should come first
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Wheat",
          "interpretation": [
            {
              "coding": [
                {
                  "code": "low"
                }
              ]
            }
          ]
        }
        """, at(result, "component[0]"));
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "interpretation": [
            {
              "coding": [
                {
                  "code": "high"
                }
              ]
            }
          ]
        }
        """, at(result, "component[1]"));
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Rye"
        }
        """, at(result, "component[2]"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should create the correct number of required slices when enforcing strict slice name usage")
      void shouldCreateTheCorrectNumberOfRequiredSlicesWhenEnforcingStrictSliceNameUsage() {
        // Profile: BreadObservation
        // Parent: Observation
        // * component ^slicing.discriminator.type = #pattern
        // * component ^slicing.discriminator.path = "value"
        // * component ^slicing.rules = #open
        // * component contains Bread 4..*
        // * component[Bread].code = http://loinc.org#48018-6
        // * component[Bread] ^slicing.discriminator.type = #pattern
        // * component[Bread] ^slicing.discriminator.path = "$this"
        // * component[Bread] ^slicing.rules = #open
        // * component[Bread] contains Rye 1..1 and Wheat 2..2
        // * component[Bread][Rye].valueString 1..1
        // * component[Bread][Rye].valueString = "Rye"
        // * component[Bread][Wheat].valueString 1..1
        // * component[Bread][Wheat].valueString = "Wheat"
        Profile breadObservation = new Profile("BreadObservation");
        breadObservation.parent = "Observation";
        CaretValueRule componentSlicingType = new CaretValueRule("component");
        componentSlicingType.caretPath = "slicing.discriminator.type";
        componentSlicingType.value = new FshCode("pattern");
        CaretValueRule componentSlicingPath = new CaretValueRule("component");
        componentSlicingPath.caretPath = "slicing.discriminator.path";
        componentSlicingPath.value = "value";
        CaretValueRule componentSlicingRules = new CaretValueRule("component");
        componentSlicingRules.caretPath = "slicing.rules";
        componentSlicingRules.value = new FshCode("open");
        ContainsRule componentContains = new ContainsRule("component");
        componentContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Bread"
        }
        """))));
        CardRule breadCard = new CardRule("component[Bread]");
        breadCard.min = 4.0;
        breadCard.max = "*";
        AssignmentRule breadCode = new AssignmentRule("component[Bread].code");
        breadCode.value = new FshCode("48018-6", "http://loinc.org");
        CaretValueRule breadSlicingType = new CaretValueRule("component[Bread]");
        breadSlicingType.caretPath = "slicing.discriminator.type";
        breadSlicingType.value = new FshCode("pattern");
        CaretValueRule breadSlicingPath = new CaretValueRule("component[Bread]");
        breadSlicingPath.caretPath = "slicing.discriminator.path";
        breadSlicingPath.value = "$this";
        CaretValueRule breadSlicingRules = new CaretValueRule("component[Bread]");
        breadSlicingRules.caretPath = "slicing.rules";
        breadSlicingRules.value = new FshCode("open");
        ContainsRule breadContains = new ContainsRule("component[Bread]");
        breadContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Rye"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "Wheat"
        }
        """))));
        CardRule ryeCard = new CardRule("component[Bread][Rye]");
        ryeCard.min = 1.0;
        ryeCard.max = "1";
        CardRule wheatCard = new CardRule("component[Bread][Wheat]");
        wheatCard.min = 2.0;
        wheatCard.max = "2";
        CardRule ryeValueCard = new CardRule("component[Bread][Rye].valueString");
        ryeValueCard.min = 1.0;
        ryeValueCard.max = "1";
        AssignmentRule ryeValueString = new AssignmentRule("component[Bread][Rye].valueString");
        ryeValueString.value = "Rye";
        CardRule wheatValueCard = new CardRule("component[Bread][Wheat].valueString");
        wheatValueCard.min = 1.0;
        wheatValueCard.max = "1";
        AssignmentRule wheatValueString = new AssignmentRule("component[Bread][Wheat].valueString");
        wheatValueString.value = "Wheat";
        breadObservation.rules.addAll(Arrays.asList(componentSlicingType, componentSlicingPath, componentSlicingRules, componentContains, breadCard, breadCode, breadSlicingType, breadSlicingPath, breadSlicingRules, breadContains, ryeCard, wheatCard, ryeValueCard, ryeValueString, wheatValueCard, wheatValueString));
        doc.profiles.put(breadObservation.name, breadObservation);
        // Instance: BreadInstance
        // InstanceOf: BreadObservation
        // * status = #final
        // * code = #bread
        // * component[Bread][Wheat][1].interpretation = #low
        Instance breadInstance = new Instance("BreadInstance");
        breadInstance.instanceOf = "BreadObservation";
        AssignmentRule instanceStatus = new AssignmentRule("status");
        instanceStatus.value = new FshCode("final");
        AssignmentRule instanceCode = new AssignmentRule("code");
        instanceCode.value = new FshCode("bread");
        AssignmentRule wheatInterpretation = new AssignmentRule("component[Bread][Wheat][1].interpretation");
        wheatInterpretation.value = new FshCode("low");
        breadInstance.rules.addAll(Arrays.asList(instanceStatus, instanceCode, wheatInterpretation));
        doc.instances.put(breadInstance.name, breadInstance);
        InstanceDefinition result = exportInstance.apply(breadInstance);
        assertEquals(4, len(at(result, "component")));
        // the first element is Bread/Wheat [0], since it must precede Bread/Wheat [1]
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Wheat"
        }
        """, at(result, "component[0]"));
        // the second element is Bread/Wheat [1], since it is the first to have a rule on the instance
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Wheat",
          "interpretation": [
            {
              "coding": [
                {
                  "code": "low"
                }
              ]
            }
          ]
        }
        """, at(result, "component[1]"));
        // the third element is Bread [0], since a slice precedes its reslices
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          }
        }
        """, at(result, "component[2]"));
        // the fourth element is Bread/Rye [0]
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Rye"
        }
        """, at(result, "component[3]"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should create the correct number of required elements without slice names when enforcing strict slice name usage")
      void shouldCreateTheCorrectNumberOfRequiredElementsWithoutSliceNamesWhenEnforcingStri() {
        // Profile: BreadObservation
        // Parent: Observation
        // * component 5..*
        // * component.code = http://loinc.org#48018-6
        // * component ^slicing.discriminator.type = #pattern
        // * component ^slicing.discriminator.path = "value"
        // * component ^slicing.rules = #open
        // * component contains Bread 3..*
        // * component[Bread].referenceRange 1..*
        // * component[Bread].referenceRange.low 1..1
        // * component[Bread].referenceRange.low = 200 'g'
        // * component[Bread] ^slicing.discriminator.type = #pattern
        // * component[Bread] ^slicing.discriminator.path = "$this"
        // * component[Bread] ^slicing.rules = #open
        // * component[Bread] contains Rye 1..1 and Wheat 2..2
        // * component[Bread][Rye].valueString 1..1
        // * component[Bread][Rye].valueString = "Rye"
        // * component[Bread][Wheat].valueString 1..1
        // * component[Bread][Wheat].valueString = "Wheat"
        Profile breadObservation = new Profile("BreadObservation");
        breadObservation.parent = "Observation";
        CardRule componentCard = new CardRule("component");
        componentCard.min = 5.0;
        componentCard.max = "*";
        AssignmentRule componentCode = new AssignmentRule("component.code");
        componentCode.value = new FshCode("48018-6", "http://loinc.org");
        CaretValueRule componentSlicingType = new CaretValueRule("component");
        componentSlicingType.caretPath = "slicing.discriminator.type";
        componentSlicingType.value = new FshCode("pattern");
        CaretValueRule componentSlicingPath = new CaretValueRule("component");
        componentSlicingPath.caretPath = "slicing.discriminator.path";
        componentSlicingPath.value = "value";
        CaretValueRule componentSlicingRules = new CaretValueRule("component");
        componentSlicingRules.caretPath = "slicing.rules";
        componentSlicingRules.value = new FshCode("open");
        ContainsRule componentContains = new ContainsRule("component");
        componentContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Bread"
        }
        """))));
        CardRule breadCard = new CardRule("component[Bread]");
        breadCard.min = 3.0;
        breadCard.max = "*";
        CardRule referenceRangeCard = new CardRule("component[Bread].referenceRange");
        referenceRangeCard.min = 1.0;
        referenceRangeCard.max = "*";
        CardRule lowCard = new CardRule("component[Bread].referenceRange.low");
        lowCard.min = 1.0;
        lowCard.max = "1";
        AssignmentRule lowQuantity = new AssignmentRule("component[Bread].referenceRange.low");
        lowQuantity.value = new FshQuantity(200.0, new FshCode("g", "http://unitsofmeasure.org"));
        CaretValueRule breadSlicingType = new CaretValueRule("component[Bread]");
        breadSlicingType.caretPath = "slicing.discriminator.type";
        breadSlicingType.value = new FshCode("pattern");
        CaretValueRule breadSlicingPath = new CaretValueRule("component[Bread]");
        breadSlicingPath.caretPath = "slicing.discriminator.path";
        breadSlicingPath.value = "$this";
        CaretValueRule breadSlicingRules = new CaretValueRule("component[Bread]");
        breadSlicingRules.caretPath = "slicing.rules";
        breadSlicingRules.value = new FshCode("open");
        ContainsRule breadContains = new ContainsRule("component[Bread]");
        breadContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Rye"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "Wheat"
        }
        """))));
        CardRule ryeCard = new CardRule("component[Bread][Rye]");
        ryeCard.min = 1.0;
        ryeCard.max = "1";
        CardRule wheatCard = new CardRule("component[Bread][Wheat]");
        wheatCard.min = 2.0;
        wheatCard.max = "2";
        CardRule ryeValueCard = new CardRule("component[Bread][Rye].valueString");
        ryeValueCard.min = 1.0;
        ryeValueCard.max = "1";
        AssignmentRule ryeValueString = new AssignmentRule("component[Bread][Rye].valueString");
        ryeValueString.value = "Rye";
        CardRule wheatValueCard = new CardRule("component[Bread][Wheat].valueString");
        wheatValueCard.min = 1.0;
        wheatValueCard.max = "1";
        AssignmentRule wheatValueString = new AssignmentRule("component[Bread][Wheat].valueString");
        wheatValueString.value = "Wheat";
        breadObservation.rules.addAll(Arrays.asList(componentCard, componentCode, componentSlicingType, componentSlicingPath, componentSlicingRules, componentContains, breadCard, referenceRangeCard, lowCard, lowQuantity, breadSlicingType, breadSlicingPath, breadSlicingRules, breadContains, ryeCard, wheatCard, ryeValueCard, ryeValueString, wheatValueCard, wheatValueString));
        doc.profiles.put(breadObservation.name, breadObservation);
        // Instance: BreadInstance
        // InstanceOf: BreadObservation
        // * status = #final
        // * code = #bread
        // * component[Bread].interpretation = #high
        Instance breadInstance = new Instance("BreadInstance");
        breadInstance.instanceOf = "BreadObservation";
        AssignmentRule instanceStatus = new AssignmentRule("status");
        instanceStatus.value = new FshCode("final");
        AssignmentRule instanceCode = new AssignmentRule("code");
        instanceCode.value = new FshCode("bread");
        AssignmentRule breadInterpretation = new AssignmentRule("component[Bread].interpretation");
        breadInterpretation.value = new FshCode("high");
        breadInstance.rules.addAll(Arrays.asList(instanceStatus, instanceCode, breadInterpretation));
        doc.instances.put(breadInstance.name, breadInstance);
        InstanceDefinition result = exportInstance.apply(breadInstance);
        assertEquals(5, len(at(result, "component")));
        // Bread[0] comes first, since it has a rule on the Instance
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "interpretation": [
            {
              "coding": [
                {
                  "code": "high"
                }
              ]
            }
          ],
          "referenceRange": [
            {
              "low": {
                "value": 200,
                "code": "g",
                "system": "http://unitsofmeasure.org"
              }
            }
          ]
        }
        """, at(result, "component[0]"));
        // then four more, so that all minimum cardinalities are fulfilled
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          }
        }
        """, at(result, "component[1]"));
        // Bread/Rye[0] comes next, since Rye is defined before Wheat
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Rye",
          "referenceRange": [
            {
              "low": {
                "value": 200,
                "code": "g",
                "system": "http://unitsofmeasure.org"
              }
            }
          ]
        }
        """, at(result, "component[2]"));
        // Bread/Wheat[0] is the other required reslice
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Wheat",
          "referenceRange": [
            {
              "low": {
                "value": 200,
                "code": "g",
                "system": "http://unitsofmeasure.org"
              }
            }
          ]
        }
        """, at(result, "component[3]"));
        // Bread/Wheat[1] is also here, since it has a minimum of 2
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Wheat",
          "referenceRange": [
            {
              "low": {
                "value": 200,
                "code": "g",
                "system": "http://unitsofmeasure.org"
              }
            }
          ]
        }
        """, at(result, "component[4]"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should create required slices when rules use out-of-order indices when enforcing strict slice name usage")
      void shouldCreateRequiredSlicesWhenRulesUseOutOfOrderIndicesWhenEnforcingStrictSliceN() {
        // Profile: BreadObservation
        // Parent: Observation
        // * component ^slicing.discriminator.type = #pattern
        // * component ^slicing.discriminator.path = "value"
        // * component ^slicing.rules = #open
        // * component contains Bread 4..*
        // * component[Bread].code = http://loinc.org#48018-6
        // * component[Bread] ^slicing.discriminator.type = #pattern
        // * component[Bread] ^slicing.discriminator.path = "$this"
        // * component[Bread] ^slicing.rules = #open
        // * component[Bread] contains Rye 1..1 and Wheat 2..*
        // * component[Bread][Rye].valueString 1..1
        // * component[Bread][Rye].valueString = "Rye"
        // * component[Bread][Wheat].valueString 1..1
        // * component[Bread][Wheat].valueString = "Wheat"
        Profile breadObservation = new Profile("BreadObservation");
        breadObservation.parent = "Observation";
        CaretValueRule componentSlicingType = new CaretValueRule("component");
        componentSlicingType.caretPath = "slicing.discriminator.type";
        componentSlicingType.value = new FshCode("pattern");
        CaretValueRule componentSlicingPath = new CaretValueRule("component");
        componentSlicingPath.caretPath = "slicing.discriminator.path";
        componentSlicingPath.value = "value";
        CaretValueRule componentSlicingRules = new CaretValueRule("component");
        componentSlicingRules.caretPath = "slicing.rules";
        componentSlicingRules.value = new FshCode("open");
        ContainsRule componentContains = new ContainsRule("component");
        componentContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Bread"
        }
        """))));
        CardRule breadCard = new CardRule("component[Bread]");
        breadCard.min = 4.0;
        breadCard.max = "*";
        AssignmentRule breadCode = new AssignmentRule("component[Bread].code");
        breadCode.value = new FshCode("48018-6", "http://loinc.org");
        CaretValueRule breadSlicingType = new CaretValueRule("component[Bread]");
        breadSlicingType.caretPath = "slicing.discriminator.type";
        breadSlicingType.value = new FshCode("pattern");
        CaretValueRule breadSlicingPath = new CaretValueRule("component[Bread]");
        breadSlicingPath.caretPath = "slicing.discriminator.path";
        breadSlicingPath.value = "$this";
        CaretValueRule breadSlicingRules = new CaretValueRule("component[Bread]");
        breadSlicingRules.caretPath = "slicing.rules";
        breadSlicingRules.value = new FshCode("open");
        ContainsRule breadContains = new ContainsRule("component[Bread]");
        breadContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Rye"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "Wheat"
        }
        """))));
        CardRule ryeCard = new CardRule("component[Bread][Rye]");
        ryeCard.min = 1.0;
        ryeCard.max = "1";
        CardRule wheatCard = new CardRule("component[Bread][Wheat]");
        wheatCard.min = 2.0;
        wheatCard.max = "*";
        CardRule ryeValueCard = new CardRule("component[Bread][Rye].valueString");
        ryeValueCard.min = 1.0;
        ryeValueCard.max = "1";
        AssignmentRule ryeValueString = new AssignmentRule("component[Bread][Rye].valueString");
        ryeValueString.value = "Rye";
        CardRule wheatValueCard = new CardRule("component[Bread][Wheat].valueString");
        wheatValueCard.min = 1.0;
        wheatValueCard.max = "1";
        AssignmentRule wheatValueString = new AssignmentRule("component[Bread][Wheat].valueString");
        wheatValueString.value = "Wheat";
        breadObservation.rules.addAll(Arrays.asList(componentSlicingType, componentSlicingPath, componentSlicingRules, componentContains, breadCard, breadCode, breadSlicingType, breadSlicingPath, breadSlicingRules, breadContains, ryeCard, wheatCard, ryeValueCard, ryeValueString, wheatValueCard, wheatValueString));
        doc.profiles.put(breadObservation.name, breadObservation);
        // Instance: BreadInstance
        // InstanceOf: BreadObservation
        // * status = #final
        // * code = #bread
        // * component[Bread][Wheat][2].interpretation = #low
        // * component[Bread][Wheat][1].interpretation = #high
        Instance breadInstance = new Instance("BreadInstance");
        breadInstance.instanceOf = "BreadObservation";
        AssignmentRule instanceStatus = new AssignmentRule("status");
        instanceStatus.value = new FshCode("final");
        AssignmentRule instanceCode = new AssignmentRule("code");
        instanceCode.value = new FshCode("bread");
        AssignmentRule wheatLowInterpretation = new AssignmentRule("component[Bread][Wheat][2].interpretation");
        wheatLowInterpretation.value = new FshCode("low");
        AssignmentRule wheatHighInterpretation = new AssignmentRule("component[Bread][Wheat][1].interpretation");
        wheatHighInterpretation.value = new FshCode("high");
        breadInstance.rules.addAll(Arrays.asList(instanceStatus, instanceCode, wheatLowInterpretation, wheatHighInterpretation));
        doc.instances.put(breadInstance.name, breadInstance);
        InstanceDefinition result = exportInstance.apply(breadInstance);
        assertEquals(4, len(at(result, "component")));
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Wheat"
        }
        """, at(result, "component[0]"));
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Wheat",
          "interpretation": [
            {
              "coding": [
                {
                  "code": "high"
                }
              ]
            }
          ]
        }
        """, at(result, "component[1]"));
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Wheat",
          "interpretation": [
            {
              "coding": [
                {
                  "code": "low"
                }
              ]
            }
          ]
        }
        """, at(result, "component[2]"));
        assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Rye"
        }
        """, at(result, "component[3]"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should assign mixed sliced elements in an array when enforcing strict slice name usage")
      void shouldAssignMixedSlicedElementsInAnArrayWhenEnforcingStrictSliceNameUsage() {
        AssignmentRule fooRule = new AssignmentRule("extension[type][0].valueCoding.system");
        fooRule.value = "foo";
        patientProfInstance.rules.add(fooRule);
        AssignmentRule bazRule = new AssignmentRule("extension[level].valueCoding.system");
        bazRule.value = "baz";
        patientProfInstance.rules.add(bazRule);
        AssignmentRule barRule = new AssignmentRule("extension[type][1].valueCoding.system");
        barRule.value = "bar";
        patientProfInstance.rules.add(barRule);
        InstanceDefinition exported = exportInstance.apply(patientProfInstance);
        assertJsonEqual("""
        [
          {
            "url": "type",
            "valueCoding": {
              "system": "foo"
            }
          },
          {
            "url": "level",
            "valueCoding": {
              "system": "baz"
            }
          },
          {
            "url": "type",
            "valueCoding": {
              "system": "bar"
            }
          }
        ]
        """, at(exported, "extension"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should output no warnings when assigning a value[x] choice type on an extension element when enforcing strict slice name usage")
      void shouldOutputNoWarningsWhenAssigningAValueXChoiceTypeOnAnExtensionElementWhenEnfo() {
        Extension valueBooleanExtension = new Extension("ExtensionWithValueBoolean");
        OnlyRule onlyRule = new OnlyRule("value[x]");
        onlyRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "boolean"
        }
        """))));
        valueBooleanExtension.rules.add(onlyRule);
        doc.extensions.put(valueBooleanExtension.name, valueBooleanExtension);
        AssignmentRule valueBoolean = new AssignmentRule("extension[ExtensionWithValueBoolean].valueBoolean");
        valueBoolean.value = true;
        patientInstance.rules.add(valueBoolean);
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/ExtensionWithValueBoolean",
            "valueBoolean": true
          }
        ]
        """, at(exported, "extension"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should warn when an author creates an item loosely matching a slice without using the sliceName in the path")
      void shouldWarnWhenAnAuthorCreatesAnItemLooselyMatchingASliceWithoutUsingTheSliceName() {
        // NOTE: Corresponds to https://github.com/FHIR/sushi/issues/1180
        // Profile: LabProfile
        // Parent: Observation
        // * category ^slicing.discriminator.type = #pattern
        // * category ^slicing.discriminator.path = "$this"
        // * category ^slicing.rules = #open
        // * category contains lab 1..1
        // * category[lab] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
        Profile profile = new Profile("LabProfile");
        profile.parent = "Observation";
        CaretValueRule typeRule = new CaretValueRule("category");
        typeRule.caretPath = "slicing.discriminator[0].type";
        typeRule.value = new FshCode("pattern");
        CaretValueRule pathRule = new CaretValueRule("category");
        pathRule.caretPath = "slicing.discriminator[0].path";
        pathRule.value = "$this";
        CaretValueRule rulesRule = new CaretValueRule("category");
        rulesRule.caretPath = "slicing.rules";
        rulesRule.value = new FshCode("open");
        ContainsRule containsRule = new ContainsRule("category");
        containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "lab"
        }
        """)));
        CardRule cardRule = new CardRule("category[lab]");
        cardRule.min = 1.0;
        cardRule.max = "*";
        AssignmentRule assignmentRule = new AssignmentRule("category[lab]");
        assignmentRule.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, cardRule, assignmentRule));
        doc.profiles.put(profile.name, profile);
        // Instance: LabInstance
        // InstanceOf: LabProfile
        // * category = http://terminology.hl7.org/CodeSystem/observation-category#laboratory "Laboratory"
        // * status = #final
        // * code = http://foo.com#a
        // * valueBoolean = true
        Instance observationInstance = new Instance("LabInstance");
        observationInstance.instanceOf = "LabProfile";
        AssignmentRule categoryAssignment = new AssignmentRule("category");
        categoryAssignment.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category", "Laboratory");
        AssignmentRule statusAssignment = new AssignmentRule("status");
        statusAssignment.value = new FshCode("final");
        AssignmentRule codeAssignment = new AssignmentRule("code");
        codeAssignment.value = new FshCode("a", "http://foo.com");
        AssignmentRule valueAssignment = new AssignmentRule("valueBoolean");
        valueAssignment.value = true;
        observationInstance.rules.addAll(Arrays.asList(categoryAssignment, statusAssignment, codeAssignment, valueAssignment));
        InstanceDefinition exported = exportInstance.apply(observationInstance);
        assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                "display": "Laboratory"
              }
            ]
          },
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category"
              }
            ]
          }
        ]
        """, at(exported, "category"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertContainsText("LabInstance has an array item that matches a required slice", loggerSpy.getLastMessage("warn"));
        assertContainsText("Path:  Observation.category", loggerSpy.getLastMessage("warn"));
        assertContainsText("Slice: lab", loggerSpy.getLastMessage("warn"));
        assertContainsText("Value: " + Js.toStr(Json.stringify(JsAssert.json("""
        {
          "coding": [
            {
              "code": "laboratory",
              "system": "http://terminology.hl7.org/CodeSystem/observation-category",
              "display": "Laboratory"
            }
          ]
        }
        """))), loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should truncate long values when it warns an author about an item loosely matching a slice without using the sliceName in the path")
      void shouldTruncateLongValuesWhenItWarnsAnAuthorAboutAnItemLooselyMatchingASliceWitho() {
        // Profile: LabProfile
        // Parent: Observation
        // * category ^slicing.discriminator.type = #pattern
        // * category ^slicing.discriminator.path = "$this"
        // * category ^slicing.rules = #open
        // * category contains lab 1..1
        // * category[lab] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
        Profile profile = new Profile("LabProfile");
        profile.parent = "Observation";
        CaretValueRule typeRule = new CaretValueRule("category");
        typeRule.caretPath = "slicing.discriminator[0].type";
        typeRule.value = new FshCode("pattern");
        CaretValueRule pathRule = new CaretValueRule("category");
        pathRule.caretPath = "slicing.discriminator[0].path";
        pathRule.value = "$this";
        CaretValueRule rulesRule = new CaretValueRule("category");
        rulesRule.caretPath = "slicing.rules";
        rulesRule.value = new FshCode("open");
        ContainsRule containsRule = new ContainsRule("category");
        containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "lab"
        }
        """)));
        CardRule cardRule = new CardRule("category[lab]");
        cardRule.min = 1.0;
        cardRule.max = "*";
        AssignmentRule assignmentRule = new AssignmentRule("category[lab]");
        assignmentRule.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, cardRule, assignmentRule));
        doc.profiles.put(profile.name, profile);
        // Instance: LabInstance
        // InstanceOf: LabProfile
        // * category = http://terminology.hl7.org/CodeSystem/observation-category#laboratory "Laboraboraboraborabora...(etc)..tory"
        // * status = #final
        // * code = http://foo.com#a
        // * valueBoolean = true
        Instance observationInstance = new Instance("LabInstance");
        observationInstance.instanceOf = "LabProfile";
        AssignmentRule categoryAssignment = new AssignmentRule("category");
        categoryAssignment.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category", "La" + Js.toStr("bora".repeat(100)) + "tory");
        AssignmentRule statusAssignment = new AssignmentRule("status");
        statusAssignment.value = new FshCode("final");
        AssignmentRule codeAssignment = new AssignmentRule("code");
        codeAssignment.value = new FshCode("a", "http://foo.com");
        AssignmentRule valueAssignment = new AssignmentRule("valueBoolean");
        valueAssignment.value = true;
        observationInstance.rules.addAll(Arrays.asList(categoryAssignment, statusAssignment, codeAssignment, valueAssignment));
        InstanceDefinition exported = exportInstance.apply(observationInstance);
        assertJsEqual(new ArrayList<>(Arrays.asList(obj("coding", new ArrayList<>(Arrays.asList(obj("code", "laboratory", "system", "http://terminology.hl7.org/CodeSystem/observation-category", "display", "La" + Js.toStr("bora".repeat(100)) + "tory")))), JsAssert.json("""
        {
          "coding": [
            {
              "code": "laboratory",
              "system": "http://terminology.hl7.org/CodeSystem/observation-category"
            }
          ]
        }
        """))), at(exported, "category"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        String warning = loggerSpy.getLastMessage("warn");
        assertContainsText("LabInstance has an array item that matches a required slice", warning);
        assertContainsText("Path:  Observation.category", warning);
        assertContainsText("Slice: lab", warning);
        assertEquals(300, warning.substring(warning.indexOf("Value:") + 7).length());
        assertTrue(warning.endsWith("boraborabo... (truncated)"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should warn when an author creates an item loosely matching a slice (with extra sub-array values) without using the sliceName in the path")
      void shouldWarnWhenAnAuthorCreatesAnItemLooselyMatchingASliceWithExtraSubArrayValuesW() {
        // Profile: LabProfile
        // Parent: Observation
        // * category ^slicing.discriminator.type = #pattern
        // * category ^slicing.discriminator.path = "$this"
        // * category ^slicing.rules = #open
        // * category contains lab 1..1
        // * category[lab] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
        Profile profile = new Profile("LabProfile");
        profile.parent = "Observation";
        CaretValueRule typeRule = new CaretValueRule("category");
        typeRule.caretPath = "slicing.discriminator[0].type";
        typeRule.value = new FshCode("pattern");
        CaretValueRule pathRule = new CaretValueRule("category");
        pathRule.caretPath = "slicing.discriminator[0].path";
        pathRule.value = "$this";
        CaretValueRule rulesRule = new CaretValueRule("category");
        rulesRule.caretPath = "slicing.rules";
        rulesRule.value = new FshCode("open");
        ContainsRule containsRule = new ContainsRule("category");
        containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "lab"
        }
        """)));
        CardRule cardRule = new CardRule("category[lab]");
        cardRule.min = 1.0;
        cardRule.max = "*";
        AssignmentRule assignmentRule = new AssignmentRule("category[lab]");
        assignmentRule.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, cardRule, assignmentRule));
        doc.profiles.put(profile.name, profile);
        // Instance: LabInstance
        // InstanceOf: LabProfile
        // * category = http://terminology.hl7.org/CodeSystem/observation-category#laboratory "Laboratory"
        // * category.coding[1] = http://foo.bar#labby "Labby"
        // * status = #final
        // * code = http://foo.com#a
        // * valueBoolean = true
        Instance observationInstance = new Instance("LabInstance");
        observationInstance.instanceOf = "LabProfile";
        AssignmentRule categoryAssignment = new AssignmentRule("category");
        categoryAssignment.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category", "Laboratory");
        AssignmentRule extraCategoryCoding = new AssignmentRule("category.coding[1]");
        extraCategoryCoding.value = new FshCode("labby", "http://foo.bar", "Labby");
        AssignmentRule statusAssignment = new AssignmentRule("status");
        statusAssignment.value = new FshCode("final");
        AssignmentRule codeAssignment = new AssignmentRule("code");
        codeAssignment.value = new FshCode("a", "http://foo.com");
        AssignmentRule valueAssignment = new AssignmentRule("valueBoolean");
        valueAssignment.value = true;
        observationInstance.rules.addAll(Arrays.asList(categoryAssignment, extraCategoryCoding, statusAssignment, codeAssignment, valueAssignment));
        InstanceDefinition exported = exportInstance.apply(observationInstance);
        assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                "display": "Laboratory"
              },
              {
                "code": "labby",
                "system": "http://foo.bar",
                "display": "Labby"
              }
            ]
          },
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category"
              }
            ]
          }
        ]
        """, at(exported, "category"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertContainsText("LabInstance has an array item that matches a required slice", loggerSpy.getLastMessage("warn"));
        assertContainsText("Path:  Observation.category", loggerSpy.getLastMessage("warn"));
        assertContainsText("Slice: lab", loggerSpy.getLastMessage("warn"));
        assertContainsText("Value: " + Js.toStr(Json.stringify(JsAssert.json("""
        {
          "coding": [
            {
              "code": "laboratory",
              "system": "http://terminology.hl7.org/CodeSystem/observation-category",
              "display": "Laboratory"
            },
            {
              "code": "labby",
              "system": "http://foo.bar",
              "display": "Labby"
            }
          ]
        }
        """))), loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should warn when an author creates an item loosely matching a slice (with sub-array items in different order) without using the sliceName in the path")
      void shouldWarnWhenAnAuthorCreatesAnItemLooselyMatchingASliceWithSubArrayItemsInDiffe() {
        // Instance: MultiCategory
        // InstanceOf: CodeableConcept
        // Usage: #inline
        // * coding[0] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
        // * coding[1] = http://foo.bar#labby
        Instance multiCategoryInstance = new Instance("MultiCategory");
        multiCategoryInstance.instanceOf = "CodeableConcept";
        multiCategoryInstance.usage = InstanceUsage.Inline;
        AssignmentRule coding0AssignmentRule = new AssignmentRule("coding[0]");
        coding0AssignmentRule.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        AssignmentRule coding1AssignmentRule = new AssignmentRule("coding[1]");
        coding1AssignmentRule.value = new FshCode("labby", "http://foo.bar");
        multiCategoryInstance.rules.addAll(Arrays.asList(coding0AssignmentRule, coding1AssignmentRule));
        doc.instances.put(multiCategoryInstance.getId(), multiCategoryInstance);
        // Profile: LabProfile
        // Parent: Observation
        // * category ^slicing.discriminator.type = #pattern
        // * category ^slicing.discriminator.path = "$this"
        // * category ^slicing.rules = #open
        // * category contains lab 1..1
        // * category[lab] = MultiCategory
        Profile profile = new Profile("LabProfile");
        profile.parent = "Observation";
        CaretValueRule typeRule = new CaretValueRule("category");
        typeRule.caretPath = "slicing.discriminator[0].type";
        typeRule.value = new FshCode("pattern");
        CaretValueRule pathRule = new CaretValueRule("category");
        pathRule.caretPath = "slicing.discriminator[0].path";
        pathRule.value = "$this";
        CaretValueRule rulesRule = new CaretValueRule("category");
        rulesRule.caretPath = "slicing.rules";
        rulesRule.value = new FshCode("open");
        ContainsRule containsRule = new ContainsRule("category");
        containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "lab"
        }
        """)));
        CardRule cardRule = new CardRule("category[lab]");
        cardRule.min = 1.0;
        cardRule.max = "*";
        AssignmentRule assignmentRule = new AssignmentRule("category[lab]");
        assignmentRule.value = "MultiCategory";
        assignmentRule.isInstance = true;
        profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, cardRule, assignmentRule));
        doc.profiles.put(profile.name, profile);
        // Instance: LabInstance
        // InstanceOf: LabProfile
        // * category = http://terminology.hl7.org/CodeSystem/observation-category#laboratory "Laboratory"
        // * category.coding[1] = http://foo.bar#labby "Labby"
        // * status = #final
        // * code = http://foo.com#a
        // * valueBoolean = true
        Instance observationInstance = new Instance("LabInstance");
        observationInstance.instanceOf = "LabProfile";
        AssignmentRule categoryAssignment = new AssignmentRule("category");
        categoryAssignment.value = new FshCode("labby", "http://foo.bar", "Labby");
        AssignmentRule extraCategoryCoding = new AssignmentRule("category.coding[1]");
        extraCategoryCoding.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category", "Laboratory");
        AssignmentRule statusAssignment = new AssignmentRule("status");
        statusAssignment.value = new FshCode("final");
        AssignmentRule codeAssignment = new AssignmentRule("code");
        codeAssignment.value = new FshCode("a", "http://foo.com");
        AssignmentRule valueAssignment = new AssignmentRule("valueBoolean");
        valueAssignment.value = true;
        observationInstance.rules.addAll(Arrays.asList(categoryAssignment, extraCategoryCoding, statusAssignment, codeAssignment, valueAssignment));
        InstanceDefinition exported = exportInstance.apply(observationInstance);
        assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "labby",
                "system": "http://foo.bar",
                "display": "Labby"
              },
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                "display": "Laboratory"
              }
            ]
          },
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category"
              },
              {
                "code": "labby",
                "system": "http://foo.bar"
              }
            ]
          }
        ]
        """, at(exported, "category"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertContainsText("LabInstance has an array item that matches a required slice", loggerSpy.getLastMessage("warn"));
        assertContainsText("Path:  Observation.category", loggerSpy.getLastMessage("warn"));
        assertContainsText("Slice: lab", loggerSpy.getLastMessage("warn"));
        assertContainsText("Value: " + Js.toStr(Json.stringify(JsAssert.json("""
        {
          "coding": [
            {
              "code": "labby",
              "system": "http://foo.bar",
              "display": "Labby"
            },
            {
              "code": "laboratory",
              "system": "http://terminology.hl7.org/CodeSystem/observation-category",
              "display": "Laboratory"
            }
          ]
        }
        """))), loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should warn when an author creates an item loosely matching a slice (on non-array properties) without using the sliceName in the path")
      void shouldWarnWhenAnAuthorCreatesAnItemLooselyMatchingASliceOnNonArrayPropertiesWith() {
        // Instance: BogusCategory
        // InstanceOf: CodeableConcept
        // Usage: #inline
        // * text = "Bogus"
        Instance bogusCategoryInstance = new Instance("BogusCategory");
        bogusCategoryInstance.instanceOf = "CodeableConcept";
        bogusCategoryInstance.usage = InstanceUsage.Inline;
        AssignmentRule bogusTextAssignment = new AssignmentRule("text");
        bogusTextAssignment.value = "Bogus";
        bogusCategoryInstance.rules.add(bogusTextAssignment);
        doc.instances.put(bogusCategoryInstance.getId(), bogusCategoryInstance);
        // Profile: BogusProfile
        // Parent: Observation
        // * category ^slicing.discriminator.type = #pattern
        // * category ^slicing.discriminator.path = "$this"
        // * category ^slicing.rules = #open
        // * category contains bogus 1..1
        // * category[lab] = BogusCategory
        Profile profile = new Profile("BogusProfile");
        profile.parent = "Observation";
        CaretValueRule typeRule = new CaretValueRule("category");
        typeRule.caretPath = "slicing.discriminator[0].type";
        typeRule.value = new FshCode("pattern");
        CaretValueRule pathRule = new CaretValueRule("category");
        pathRule.caretPath = "slicing.discriminator[0].path";
        pathRule.value = "$this";
        CaretValueRule rulesRule = new CaretValueRule("category");
        rulesRule.caretPath = "slicing.rules";
        rulesRule.value = new FshCode("open");
        ContainsRule containsRule = new ContainsRule("category");
        containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "bogus"
        }
        """)));
        CardRule cardRule = new CardRule("category[bogus]");
        cardRule.min = 1.0;
        cardRule.max = "*";
        AssignmentRule assignmentRule = new AssignmentRule("category[bogus]");
        assignmentRule.value = "BogusCategory";
        assignmentRule.isInstance = true;
        profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, cardRule, assignmentRule));
        doc.profiles.put(profile.name, profile);
        // Instance: BogusInstance
        // InstanceOf: BogusProfile
        // * category.id = "BOGUS"
        // * category.text = "Bogus"
        // * status = #final
        // * code = http://foo.com#a
        // * valueBoolean = true
        Instance observationInstance = new Instance("BogusInstance");
        observationInstance.instanceOf = "BogusProfile";
        AssignmentRule categoryIdAssignment = new AssignmentRule("category.id");
        categoryIdAssignment.value = "BOGUS";
        AssignmentRule categoryTextAssignment = new AssignmentRule("category.text");
        categoryTextAssignment.value = "Bogus";
        AssignmentRule statusAssignment = new AssignmentRule("status");
        statusAssignment.value = new FshCode("final");
        AssignmentRule codeAssignment = new AssignmentRule("code");
        codeAssignment.value = new FshCode("a", "http://foo.com");
        AssignmentRule valueAssignment = new AssignmentRule("valueBoolean");
        valueAssignment.value = true;
        observationInstance.rules.addAll(Arrays.asList(categoryIdAssignment, categoryTextAssignment, statusAssignment, codeAssignment, valueAssignment));
        InstanceDefinition exported = exportInstance.apply(observationInstance);
        assertJsonEqual("""
        [
          {
            "id": "BOGUS",
            "text": "Bogus"
          },
          {
            "text": "Bogus"
          }
        ]
        """, at(exported, "category"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertContainsText("BogusInstance has an array item that matches a required slice", loggerSpy.getLastMessage("warn"));
        assertContainsText("Path:  Observation.category", loggerSpy.getLastMessage("warn"));
        assertContainsText("Slice: bogus", loggerSpy.getLastMessage("warn"));
        assertContainsText("Value: " + Js.toStr(Json.stringify(JsAssert.json("""
        {
          "id": "BOGUS",
          "text": "Bogus"
        }
        """))), loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should warn when an author creates an item exactly matching a slice without using the sliceName in the path")
      void shouldWarnWhenAnAuthorCreatesAnItemExactlyMatchingASliceWithoutUsingTheSliceName() {
        // NOTE: Variation of https://github.com/FHIR/sushi/issues/1180
        // Profile: LabProfile
        // Parent: Observation
        // * category ^slicing.discriminator.type = #pattern
        // * category ^slicing.discriminator.path = "$this"
        // * category ^slicing.rules = #open
        // * category contains lab 1..1
        // * category[lab] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
        Profile profile = new Profile("LabProfile");
        profile.parent = "Observation";
        CaretValueRule typeRule = new CaretValueRule("category");
        typeRule.caretPath = "slicing.discriminator[0].type";
        typeRule.value = new FshCode("pattern");
        CaretValueRule pathRule = new CaretValueRule("category");
        pathRule.caretPath = "slicing.discriminator[0].path";
        pathRule.value = "$this";
        CaretValueRule rulesRule = new CaretValueRule("category");
        rulesRule.caretPath = "slicing.rules";
        rulesRule.value = new FshCode("open");
        ContainsRule containsRule = new ContainsRule("category");
        containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "lab"
        }
        """)));
        CardRule cardRule = new CardRule("category[lab]");
        cardRule.min = 1.0;
        cardRule.max = "*";
        AssignmentRule assignmentRule = new AssignmentRule("category[lab]");
        assignmentRule.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, cardRule, assignmentRule));
        doc.profiles.put(profile.name, profile);
        // Instance: LabInstance
        // InstanceOf: LabProfile
        // * category = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
        // * status = #final
        // * code = http://foo.com#a
        // * valueBoolean = true
        Instance observationInstance = new Instance("LabInstance");
        observationInstance.instanceOf = "LabProfile";
        AssignmentRule categoryAssignment = new AssignmentRule("category");
        categoryAssignment.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        AssignmentRule statusAssignment = new AssignmentRule("status");
        statusAssignment.value = new FshCode("final");
        AssignmentRule codeAssignment = new AssignmentRule("code");
        codeAssignment.value = new FshCode("a", "http://foo.com");
        AssignmentRule valueAssignment = new AssignmentRule("valueBoolean");
        valueAssignment.value = true;
        observationInstance.rules.addAll(Arrays.asList(categoryAssignment, statusAssignment, codeAssignment, valueAssignment));
        InstanceDefinition exported = exportInstance.apply(observationInstance);
        assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category"
              }
            ]
          },
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category"
              }
            ]
          }
        ]
        """, at(exported, "category"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertContainsText("LabInstance has an array item that is exactly the same as a required slice", loggerSpy.getLastMessage("warn"));
        assertContainsText("Path:  Observation.category", loggerSpy.getLastMessage("warn"));
        assertContainsText("Slice: lab", loggerSpy.getLastMessage("warn"));
        assertContainsText("Value: " + Js.toStr(Json.stringify(JsAssert.json("""
        {
          "coding": [
            {
              "code": "laboratory",
              "system": "http://terminology.hl7.org/CodeSystem/observation-category"
            }
          ]
        }
        """))), loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should warn when an author creates an item exactly matching a slice (on non-array properties) without using the sliceName in the path")
      void shouldWarnWhenAnAuthorCreatesAnItemExactlyMatchingASliceOnNonArrayPropertiesWith() {
        // Instance: BogusCategory
        // InstanceOf: CodeableConcept
        // Usage: #inline
        // * id = "BOGUS"
        // * text = "Bogus"
        Instance bogusCategoryInstance = new Instance("BogusCategory");
        bogusCategoryInstance.instanceOf = "CodeableConcept";
        bogusCategoryInstance.usage = InstanceUsage.Inline;
        AssignmentRule bogusTextAssignment = new AssignmentRule("text");
        bogusTextAssignment.value = "Bogus";
        bogusCategoryInstance.rules.add(bogusTextAssignment);
        doc.instances.put(bogusCategoryInstance.getId(), bogusCategoryInstance);
        // Profile: BogusProfile
        // Parent: Observation
        // * category ^slicing.discriminator.type = #pattern
        // * category ^slicing.discriminator.path = "$this"
        // * category ^slicing.rules = #open
        // * category contains bogus 1..1
        // * category[lab] = BogusCategory
        Profile profile = new Profile("BogusProfile");
        profile.parent = "Observation";
        CaretValueRule typeRule = new CaretValueRule("category");
        typeRule.caretPath = "slicing.discriminator[0].type";
        typeRule.value = new FshCode("pattern");
        CaretValueRule pathRule = new CaretValueRule("category");
        pathRule.caretPath = "slicing.discriminator[0].path";
        pathRule.value = "$this";
        CaretValueRule rulesRule = new CaretValueRule("category");
        rulesRule.caretPath = "slicing.rules";
        rulesRule.value = new FshCode("open");
        ContainsRule containsRule = new ContainsRule("category");
        containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "bogus"
        }
        """)));
        CardRule cardRule = new CardRule("category[bogus]");
        cardRule.min = 1.0;
        cardRule.max = "*";
        AssignmentRule assignmentRule = new AssignmentRule("category[bogus]");
        assignmentRule.value = "BogusCategory";
        assignmentRule.isInstance = true;
        profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, cardRule, assignmentRule));
        doc.profiles.put(profile.name, profile);
        // Instance: BogusInstance
        // InstanceOf: BogusProfile
        // * category.text = "Bogus"
        // * status = #final
        // * code = http://foo.com#a
        // * valueBoolean = true
        Instance observationInstance = new Instance("BogusInstance");
        observationInstance.instanceOf = "BogusProfile";
        AssignmentRule categoryTextAssignment = new AssignmentRule("category.text");
        categoryTextAssignment.value = "Bogus";
        AssignmentRule statusAssignment = new AssignmentRule("status");
        statusAssignment.value = new FshCode("final");
        AssignmentRule codeAssignment = new AssignmentRule("code");
        codeAssignment.value = new FshCode("a", "http://foo.com");
        AssignmentRule valueAssignment = new AssignmentRule("valueBoolean");
        valueAssignment.value = true;
        observationInstance.rules.addAll(Arrays.asList(categoryTextAssignment, statusAssignment, codeAssignment, valueAssignment));
        InstanceDefinition exported = exportInstance.apply(observationInstance);
        assertJsonEqual("""
        [
          {
            "text": "Bogus"
          },
          {
            "text": "Bogus"
          }
        ]
        """, at(exported, "category"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertContainsText("BogusInstance has an array item that is exactly the same as a required slice", loggerSpy.getLastMessage("warn"));
        assertContainsText("Path:  Observation.category", loggerSpy.getLastMessage("warn"));
        assertContainsText("Slice: bogus", loggerSpy.getLastMessage("warn"));
        assertContainsText("Value: " + Js.toStr(Json.stringify(JsAssert.json("""
        {
          "text": "Bogus"
        }
        """))), loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should warn when an author creates an item exactly matching a slice (and not matching others) without using the sliceName in the path")
      void shouldWarnWhenAnAuthorCreatesAnItemExactlyMatchingASliceAndNotMatchingOthersWith() {
        // Profile: LabProfile
        // Parent: Observation
        // * category ^slicing.discriminator.type = #pattern
        // * category ^slicing.discriminator.path = "$this"
        // * category ^slicing.rules = #open
        // * category contains lab 1..1
        // * category[lab] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
        // * category[procedure] = http://terminology.hl7.org/CodeSystem/observation-category#procedure
        Profile profile = new Profile("LabProfile");
        profile.parent = "Observation";
        CaretValueRule typeRule = new CaretValueRule("category");
        typeRule.caretPath = "slicing.discriminator[0].type";
        typeRule.value = new FshCode("pattern");
        CaretValueRule pathRule = new CaretValueRule("category");
        pathRule.caretPath = "slicing.discriminator[0].path";
        pathRule.value = "$this";
        CaretValueRule rulesRule = new CaretValueRule("category");
        rulesRule.caretPath = "slicing.rules";
        rulesRule.value = new FshCode("open");
        ContainsRule containsRule = new ContainsRule("category");
        containsRule.items.addAll(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "lab"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "procedure"
        }
        """))));
        CardRule labCardRule = new CardRule("category[lab]");
        labCardRule.min = 1.0;
        labCardRule.max = "1";
        AssignmentRule labAssignmentRule = new AssignmentRule("category[lab]");
        labAssignmentRule.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        CardRule procCardRule = new CardRule("category[procedure]");
        procCardRule.min = 1.0;
        procCardRule.max = "1";
        AssignmentRule procAssignmentRule = new AssignmentRule("category[procedure]");
        procAssignmentRule.value = new FshCode("procedure", "http://terminology.hl7.org/CodeSystem/observation-category");
        profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, labCardRule, labAssignmentRule, procCardRule, procAssignmentRule));
        doc.profiles.put(profile.name, profile);
        // Instance: LabInstance
        // InstanceOf: LabProfile
        // * category = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
        // * status = #final
        // * code = http://foo.com#a
        // * valueBoolean = true
        Instance observationInstance = new Instance("LabInstance");
        observationInstance.instanceOf = "LabProfile";
        AssignmentRule categoryAssignment = new AssignmentRule("category");
        categoryAssignment.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        AssignmentRule statusAssignment = new AssignmentRule("status");
        statusAssignment.value = new FshCode("final");
        AssignmentRule codeAssignment = new AssignmentRule("code");
        codeAssignment.value = new FshCode("a", "http://foo.com");
        AssignmentRule valueAssignment = new AssignmentRule("valueBoolean");
        valueAssignment.value = true;
        observationInstance.rules.addAll(Arrays.asList(categoryAssignment, statusAssignment, codeAssignment, valueAssignment));
        InstanceDefinition exported = exportInstance.apply(observationInstance);
        assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category"
              }
            ]
          },
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category"
              }
            ]
          },
          {
            "coding": [
              {
                "code": "procedure",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category"
              }
            ]
          }
        ]
        """, at(exported, "category"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertContainsText("LabInstance has an array item that is exactly the same as a required slice", loggerSpy.getLastMessage("warn"));
        assertContainsText("Path:  Observation.category", loggerSpy.getLastMessage("warn"));
        assertContainsText("Slice: lab", loggerSpy.getLastMessage("warn"));
        assertContainsText("Value: " + Js.toStr(Json.stringify(JsAssert.json("""
        {
          "coding": [
            {
              "code": "laboratory",
              "system": "http://terminology.hl7.org/CodeSystem/observation-category"
            }
          ]
        }
        """))), loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should warn when an author creates an item exactly matching a slice and superset matching another slice without using the sliceName in the path")
      void shouldWarnWhenAnAuthorCreatesAnItemExactlyMatchingASliceAndSupersetMatchingAnoth() {
        // Profile: LabProfile
        // Parent: Observation
        // * category ^slicing.discriminator.type = #pattern
        // * category ^slicing.discriminator.path = "$this"
        // * category ^slicing.rules = #open
        // * category contains lab 1..1
        // * category[lab] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
        // * category[lab2] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory "Laboratory"
        Profile profile = new Profile("LabProfile");
        profile.parent = "Observation";
        CaretValueRule typeRule = new CaretValueRule("category");
        typeRule.caretPath = "slicing.discriminator[0].type";
        typeRule.value = new FshCode("pattern");
        CaretValueRule pathRule = new CaretValueRule("category");
        pathRule.caretPath = "slicing.discriminator[0].path";
        pathRule.value = "$this";
        CaretValueRule rulesRule = new CaretValueRule("category");
        rulesRule.caretPath = "slicing.rules";
        rulesRule.value = new FshCode("open");
        ContainsRule containsRule = new ContainsRule("category");
        containsRule.items.addAll(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "lab"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "lab2"
        }
        """))));
        CardRule labCardRule = new CardRule("category[lab]");
        labCardRule.min = 1.0;
        labCardRule.max = "1";
        AssignmentRule labAssignmentRule = new AssignmentRule("category[lab]");
        labAssignmentRule.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        CardRule lab2CardRule = new CardRule("category[lab2]");
        lab2CardRule.min = 1.0;
        lab2CardRule.max = "1";
        AssignmentRule lab2AssignmentRule = new AssignmentRule("category[lab2]");
        lab2AssignmentRule.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category", "Laboratory");
        profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, labCardRule, labAssignmentRule, lab2CardRule, lab2AssignmentRule));
        doc.profiles.put(profile.name, profile);
        // Instance: LabInstance
        // InstanceOf: LabProfile
        // * category = http://terminology.hl7.org/CodeSystem/observation-category#laboratory "Laboratory"
        // * status = #final
        // * code = http://foo.com#a
        // * valueBoolean = true
        Instance observationInstance = new Instance("LabInstance");
        observationInstance.instanceOf = "LabProfile";
        AssignmentRule categoryAssignment = new AssignmentRule("category");
        categoryAssignment.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category", "Laboratory");
        AssignmentRule statusAssignment = new AssignmentRule("status");
        statusAssignment.value = new FshCode("final");
        AssignmentRule codeAssignment = new AssignmentRule("code");
        codeAssignment.value = new FshCode("a", "http://foo.com");
        AssignmentRule valueAssignment = new AssignmentRule("valueBoolean");
        valueAssignment.value = true;
        observationInstance.rules.addAll(Arrays.asList(categoryAssignment, statusAssignment, codeAssignment, valueAssignment));
        InstanceDefinition exported = exportInstance.apply(observationInstance);
        assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                "display": "Laboratory"
              }
            ]
          },
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category"
              }
            ]
          },
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                "display": "Laboratory"
              }
            ]
          }
        ]
        """, at(exported, "category"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertContainsText("LabInstance has an array item that matches a required slice", loggerSpy.getLastMessage("warn"));
        assertContainsText("Path:  Observation.category", loggerSpy.getLastMessage("warn"));
        assertContainsText("Slice: lab or lab2", loggerSpy.getLastMessage("warn"));
        assertContainsText("Value: " + Js.toStr(Json.stringify(JsAssert.json("""
        {
          "coding": [
            {
              "code": "laboratory",
              "system": "http://terminology.hl7.org/CodeSystem/observation-category",
              "display": "Laboratory"
            }
          ]
        }
        """))), loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should NOT warn when an author creates an item partially matching a slice without using the sliceName in the path")
      void shouldNOTWarnWhenAnAuthorCreatesAnItemPartiallyMatchingASliceWithoutUsingTheSlic() {
        // Profile: LabProfile
        // Parent: Observation
        // * category ^slicing.discriminator.type = #pattern
        // * category ^slicing.discriminator.path = "$this"
        // * category ^slicing.rules = #open
        // * category contains lab 1..1
        // * category[lab] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
        Profile profile = new Profile("LabProfile");
        profile.parent = "Observation";
        CaretValueRule typeRule = new CaretValueRule("category");
        typeRule.caretPath = "slicing.discriminator[0].type";
        typeRule.value = new FshCode("pattern");
        CaretValueRule pathRule = new CaretValueRule("category");
        pathRule.caretPath = "slicing.discriminator[0].path";
        pathRule.value = "$this";
        CaretValueRule rulesRule = new CaretValueRule("category");
        rulesRule.caretPath = "slicing.rules";
        rulesRule.value = new FshCode("open");
        ContainsRule containsRule = new ContainsRule("category");
        containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "lab"
        }
        """)));
        CardRule cardRule = new CardRule("category[lab]");
        cardRule.min = 1.0;
        cardRule.max = "*";
        AssignmentRule assignmentRule = new AssignmentRule("category[lab]");
        assignmentRule.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, cardRule, assignmentRule));
        doc.profiles.put(profile.name, profile);
        // Instance: LabInstance
        // InstanceOf: LabProfile
        // * category = http://terminology.hl7.org/CodeSystem/observation-category#survey
        // * status = #final
        // * code = http://foo.com#a
        // * valueBoolean = true
        Instance observationInstance = new Instance("LabInstance");
        observationInstance.instanceOf = "LabProfile";
        AssignmentRule categoryAssignment = new AssignmentRule("category");
        categoryAssignment.value = new FshCode("survey", "http://terminology.hl7.org/CodeSystem/observation-category");
        AssignmentRule statusAssignment = new AssignmentRule("status");
        statusAssignment.value = new FshCode("final");
        AssignmentRule codeAssignment = new AssignmentRule("code");
        codeAssignment.value = new FshCode("a", "http://foo.com");
        AssignmentRule valueAssignment = new AssignmentRule("valueBoolean");
        valueAssignment.value = true;
        observationInstance.rules.addAll(Arrays.asList(categoryAssignment, statusAssignment, codeAssignment, valueAssignment));
        InstanceDefinition exported = exportInstance.apply(observationInstance);
        assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "survey",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category"
              }
            ]
          },
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category"
              }
            ]
          }
        ]
        """, at(exported, "category"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should NOT warn when an author creates an item matching a slice but missing an array item without using the sliceName in the path")
      void shouldNOTWarnWhenAnAuthorCreatesAnItemMatchingASliceButMissingAnArrayItemWithout() {
        // Instance: MultiCategory
        // InstanceOf: CodeableConcept
        // Usage: #inline
        // * coding[0] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
        // * coding[1] = http://foo.bar#labby
        Instance multiCategoryInstance = new Instance("MultiCategory");
        multiCategoryInstance.instanceOf = "CodeableConcept";
        multiCategoryInstance.usage = InstanceUsage.Inline;
        AssignmentRule coding0AssignmentRule = new AssignmentRule("coding[0]");
        coding0AssignmentRule.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        AssignmentRule coding1AssignmentRule = new AssignmentRule("coding[1]");
        coding1AssignmentRule.value = new FshCode("labby", "http://foo.bar");
        multiCategoryInstance.rules.addAll(Arrays.asList(coding0AssignmentRule, coding1AssignmentRule));
        doc.instances.put(multiCategoryInstance.getId(), multiCategoryInstance);
        // Profile: LabProfile
        // Parent: Observation
        // * category ^slicing.discriminator.type = #pattern
        // * category ^slicing.discriminator.path = "$this"
        // * category ^slicing.rules = #open
        // * category contains lab 1..1
        // * category[lab] = MultiCategory
        Profile profile = new Profile("LabProfile");
        profile.parent = "Observation";
        CaretValueRule typeRule = new CaretValueRule("category");
        typeRule.caretPath = "slicing.discriminator[0].type";
        typeRule.value = new FshCode("pattern");
        CaretValueRule pathRule = new CaretValueRule("category");
        pathRule.caretPath = "slicing.discriminator[0].path";
        pathRule.value = "$this";
        CaretValueRule rulesRule = new CaretValueRule("category");
        rulesRule.caretPath = "slicing.rules";
        rulesRule.value = new FshCode("open");
        ContainsRule containsRule = new ContainsRule("category");
        containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "lab"
        }
        """)));
        CardRule cardRule = new CardRule("category[lab]");
        cardRule.min = 1.0;
        cardRule.max = "*";
        AssignmentRule assignmentRule = new AssignmentRule("category[lab]");
        assignmentRule.value = "MultiCategory";
        assignmentRule.isInstance = true;
        profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, cardRule, assignmentRule));
        doc.profiles.put(profile.name, profile);
        // Instance: LabInstance
        // InstanceOf: LabProfile
        // * category = http://terminology.hl7.org/CodeSystem/observation-category#laboratory "Laboratory"
        // * status = #final
        // * code = http://foo.com#a
        // * valueBoolean = true
        Instance observationInstance = new Instance("LabInstance");
        observationInstance.instanceOf = "LabProfile";
        AssignmentRule categoryAssignment = new AssignmentRule("category");
        categoryAssignment.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        AssignmentRule statusAssignment = new AssignmentRule("status");
        statusAssignment.value = new FshCode("final");
        AssignmentRule codeAssignment = new AssignmentRule("code");
        codeAssignment.value = new FshCode("a", "http://foo.com");
        AssignmentRule valueAssignment = new AssignmentRule("valueBoolean");
        valueAssignment.value = true;
        observationInstance.rules.addAll(Arrays.asList(categoryAssignment, statusAssignment, codeAssignment, valueAssignment));
        InstanceDefinition exported = exportInstance.apply(observationInstance);
        assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category"
              }
            ]
          },
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category"
              },
              {
                "code": "labby",
                "system": "http://foo.bar"
              }
            ]
          }
        ]
        """, at(exported, "category"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should NOT warn when an author creates an item superset matching a slice and correctly uses the sliceName in the path")
      void shouldNOTWarnWhenAnAuthorCreatesAnItemSupersetMatchingASliceAndCorrectlyUsesTheS() {
        // Profile: LabProfile
        // Parent: Observation
        // * category ^slicing.discriminator.type = #pattern
        // * category ^slicing.discriminator.path = "$this"
        // * category ^slicing.rules = #open
        // * category contains lab 1..1
        // * category[lab] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
        Profile profile = new Profile("LabProfile");
        profile.parent = "Observation";
        CaretValueRule typeRule = new CaretValueRule("category");
        typeRule.caretPath = "slicing.discriminator[0].type";
        typeRule.value = new FshCode("pattern");
        CaretValueRule pathRule = new CaretValueRule("category");
        pathRule.caretPath = "slicing.discriminator[0].path";
        pathRule.value = "$this";
        CaretValueRule rulesRule = new CaretValueRule("category");
        rulesRule.caretPath = "slicing.rules";
        rulesRule.value = new FshCode("open");
        ContainsRule containsRule = new ContainsRule("category");
        containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "lab"
        }
        """)));
        CardRule cardRule = new CardRule("category[lab]");
        cardRule.min = 1.0;
        cardRule.max = "*";
        AssignmentRule assignmentRule = new AssignmentRule("category[lab]");
        assignmentRule.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, cardRule, assignmentRule));
        doc.profiles.put(profile.name, profile);
        // Instance: LabInstance
        // InstanceOf: LabProfile
        // * category[lab] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory "Laboratory"
        // * status = #final
        // * code = http://foo.com#a
        // * valueBoolean = true
        Instance observationInstance = new Instance("LabInstance");
        observationInstance.instanceOf = "LabProfile";
        AssignmentRule categoryAssignment = new AssignmentRule("category[lab]");
        categoryAssignment.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category", "Laboratory");
        AssignmentRule statusAssignment = new AssignmentRule("status");
        statusAssignment.value = new FshCode("final");
        AssignmentRule codeAssignment = new AssignmentRule("code");
        codeAssignment.value = new FshCode("a", "http://foo.com");
        AssignmentRule valueAssignment = new AssignmentRule("valueBoolean");
        valueAssignment.value = true;
        observationInstance.rules.addAll(Arrays.asList(categoryAssignment, statusAssignment, codeAssignment, valueAssignment));
        InstanceDefinition exported = exportInstance.apply(observationInstance);
        assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                "display": "Laboratory"
              }
            ]
          }
        ]
        """, at(exported, "category"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should NOT warn when an author creates an item exactly matching a slice and correctly uses the sliceName in the path")
      void shouldNOTWarnWhenAnAuthorCreatesAnItemExactlyMatchingASliceAndCorrectlyUsesTheSl() {
        // Profile: LabProfile
        // Parent: Observation
        // * category ^slicing.discriminator.type = #pattern
        // * category ^slicing.discriminator.path = "$this"
        // * category ^slicing.rules = #open
        // * category contains lab 1..1
        // * category[lab] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
        Profile profile = new Profile("LabProfile");
        profile.parent = "Observation";
        CaretValueRule typeRule = new CaretValueRule("category");
        typeRule.caretPath = "slicing.discriminator[0].type";
        typeRule.value = new FshCode("pattern");
        CaretValueRule pathRule = new CaretValueRule("category");
        pathRule.caretPath = "slicing.discriminator[0].path";
        pathRule.value = "$this";
        CaretValueRule rulesRule = new CaretValueRule("category");
        rulesRule.caretPath = "slicing.rules";
        rulesRule.value = new FshCode("open");
        ContainsRule containsRule = new ContainsRule("category");
        containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "lab"
        }
        """)));
        CardRule cardRule = new CardRule("category[lab]");
        cardRule.min = 1.0;
        cardRule.max = "*";
        AssignmentRule assignmentRule = new AssignmentRule("category[lab]");
        assignmentRule.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, cardRule, assignmentRule));
        doc.profiles.put(profile.name, profile);
        // Instance: LabInstance
        // InstanceOf: LabProfile
        // * category[lab] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
        // * status = #final
        // * code = http://foo.com#a
        // * valueBoolean = true
        Instance observationInstance = new Instance("LabInstance");
        observationInstance.instanceOf = "LabProfile";
        AssignmentRule categoryAssignment = new AssignmentRule("category[lab]");
        categoryAssignment.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        AssignmentRule statusAssignment = new AssignmentRule("status");
        statusAssignment.value = new FshCode("final");
        AssignmentRule codeAssignment = new AssignmentRule("code");
        codeAssignment.value = new FshCode("a", "http://foo.com");
        AssignmentRule valueAssignment = new AssignmentRule("valueBoolean");
        valueAssignment.value = true;
        observationInstance.rules.addAll(Arrays.asList(categoryAssignment, statusAssignment, codeAssignment, valueAssignment));
        InstanceDefinition exported = exportInstance.apply(observationInstance);
        assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category"
              }
            ]
          }
        ]
        """, at(exported, "category"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should allow path rules to be used to define a specific order of items in an array in manual slicing mode")
      void shouldAllowPathRulesToBeUsedToDefineASpecificOrderOfItemsInAnArrayInManualSlicin() {
        Profile labObservation = new Profile("LabObservation");
        labObservation.parent = "Observation";
        // * category ^slicing.discriminator.type = #pattern
        // * category ^slicing.discriminator.path = "$this"
        // * category ^slicing.rules = #open
        // * category contains lab 1..1
        // * category[lab] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
        CaretValueRule discriminatorType = new CaretValueRule("category");
        discriminatorType.caretPath = "slicing.discriminator.type";
        discriminatorType.value = new FshCode("pattern");
        CaretValueRule discriminatorPath = new CaretValueRule("category");
        discriminatorPath.caretPath = "slicing.discriminator.path";
        discriminatorPath.value = "$this";
        CaretValueRule slicingRules = new CaretValueRule("category");
        slicingRules.caretPath = "slicing.rules";
        slicingRules.value = new FshCode("open");
        ContainsRule labContains = new ContainsRule("category");
        labContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "lab"
        }
        """))));
        CardRule labCard = new CardRule("category[lab]");
        labCard.min = 1.0;
        labCard.max = "1";
        AssignmentRule labAssignment = new AssignmentRule("category[lab]");
        labAssignment.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
        labObservation.rules.addAll(Arrays.asList(discriminatorType, discriminatorPath, slicingRules, labContains, labCard, labAssignment));
        doc.profiles.put(labObservation.name, labObservation);
        Instance labInstance = new Instance("LabActivityObservation");
        labInstance.instanceOf = "LabObservation";
        // * category[lab]
        // * category[+] = http://terminology.hl7.org/CodeSystem/observation-category#activity "Activity"
        // * status = #final
        // * code = #wow
        PathRule labPathRule = new PathRule("category[lab]");
        AssignmentRule activityAssignmentRule = new AssignmentRule("category[+]");
        activityAssignmentRule.value = new FshCode("activity", "http://terminology.hl7.org/CodeSystem/observation-category", "Activity");
        AssignmentRule statusAssignmentRule = new AssignmentRule("status");
        statusAssignmentRule.value = new FshCode("final");
        AssignmentRule codeAssignmentRule = new AssignmentRule("code");
        codeAssignmentRule.value = new FshCode("wow");
        labInstance.rules.addAll(Arrays.asList(labPathRule, activityAssignmentRule, statusAssignmentRule, codeAssignmentRule));
        InstanceDefinition exported = exportInstance.apply(labInstance);
        // With manualSliceOrdering = true, PathRule keeps lab slice at first element
        // in array and category[+] adds activity element after
        assertEquals(2, len(at(exported, "category")));
        assertJsonEqual("""
        {
          "coding": [
            {
              "code": "laboratory",
              "system": "http://terminology.hl7.org/CodeSystem/observation-category"
            }
          ]
        }
        """, at(exported, "category[0]"));
        assertJsonEqual("""
        {
          "coding": [
            {
              "code": "activity",
              "system": "http://terminology.hl7.org/CodeSystem/observation-category",
              "display": "Activity"
            }
          ]
        }
        """, at(exported, "category[1]"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should not add null values with path rules")
      void shouldNotAddNullValuesWithPathRules() {
        // Instance: ExampleDS
        // InstanceOf: DiagnosticReport
        // * presentedForm
        Instance diagnosticReport = new Instance("ExampleDS");
        diagnosticReport.instanceOf = "DiagnosticReport";
        PathRule pathRule = new PathRule("presentedForm");
        diagnosticReport.rules.add(pathRule);
        InstanceDefinition exportedInstance = exportInstance.apply(diagnosticReport);
        assertNull(at(exportedInstance, "presentedForm"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should add an entry for each index used in a path rule")
      void shouldAddAnEntryForEachIndexUsedInAPathRule() {
        // Profile: ObsWithInterpretation
        // Parent: Observation
        // * interpretation.text 1..
        // * interpretation.text = "very important interpretation"
        Profile observation = new Profile("ObsWithInterpretation");
        observation.parent = "Observation";
        CardRule cardRule = new CardRule("interpretation.text");
        cardRule.min = 1.0;
        AssignmentRule assignmentRule = new AssignmentRule("interpretation.text");
        assignmentRule.value = "Important interpretations here";
        observation.rules.addAll(Arrays.asList(cardRule, assignmentRule));
        doc.profiles.put(observation.name, observation);
        // Instance: MyObsWithInterpretation
        // InstanceOf: ObsWithInterpretation
        // * interpretation[0]
        // * interpretation[1]
        Instance instance = new Instance("MyObsWithInterpretation");
        instance.instanceOf = "ObsWithInterpretation";
        PathRule pathRuleIndex0 = new PathRule("interpretation[0]");
        PathRule pathRuleIndex1 = new PathRule("interpretation[1]");
        instance.rules.addAll(Arrays.asList(pathRuleIndex0, pathRuleIndex1));
        InstanceDefinition exportedInstance = exportInstance.apply(instance);
        assertEquals(2, len(at(exportedInstance, "interpretation")));
        assertJsonEqual("""
        [
          {
            "text": "Important interpretations here"
          },
          {
            "text": "Important interpretations here"
          }
        ]
        """, at(exportedInstance, "interpretation"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should replace an array element with null when all other properties are replaced")
      void shouldReplaceAnArrayElementWithNullWhenAllOtherPropertiesAreReplaced() {
        // Instance: ExampleCS
        // InstanceOf: CapabilityStatement
        // * rest.resource[+].searchInclude
        // * rest.resource[+].type = #type
        Instance capabilityStatement = new Instance("ExampleCS");
        capabilityStatement.instanceOf = "CapabilityStatement";
        PathRule pathRule = new PathRule("rest.resource[+].searchInclude");
        AssignmentRule assignmentRule = new AssignmentRule("rest.resource[+].type");
        assignmentRule.value = new FshCode("type");
        capabilityStatement.rules.addAll(Arrays.asList(pathRule, assignmentRule));
        InstanceDefinition exportedInstance = exportInstance.apply(capabilityStatement);
        assertEquals(2, len(at(exportedInstance, "rest[0].resource")));
        assertNull(at(exportedInstance, "rest[0].resource[0]"));
        assertJsonEqual("""
        {
          "type": "type"
        }
        """, at(exportedInstance, "rest[0].resource[1]"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should assign extensions on elements of a primitive array")
      void shouldAssignExtensionsOnElementsOfAPrimitiveArray() {
        // Instance: MyAllergies
        // InstanceOf: AllergyIntolerance
        // * patient = Reference(SomePatient)
        // * category[0] = #environment
        // * category[1].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode = #unknown
        // * category[2] = #food
        Instance instance = new Instance("MyAllergies");
        instance.instanceOf = "AllergyIntolerance";
        AssignmentRule patientReference = new AssignmentRule("patient");
        patientReference.value = new FshReference("SomePatient");
        AssignmentRule categoryEnvironment = new AssignmentRule("category[0]");
        categoryEnvironment.value = new FshCode("environment");
        AssignmentRule categoryExtension = new AssignmentRule("category[1].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode");
        categoryExtension.value = new FshCode("unknown");
        AssignmentRule categoryFood = new AssignmentRule("category[2]");
        categoryFood.value = new FshCode("food");
        instance.rules.addAll(Arrays.asList(patientReference, categoryEnvironment, categoryExtension, categoryFood));
        InstanceDefinition exportedInstance = exportInstance.apply(instance);
        assertEquals(3, len(at(exportedInstance, "category")));
        assertEquals("environment", at(exportedInstance, "category[0]"));
        assertNull(at(exportedInstance, "category[1]"));
        assertEquals("food", at(exportedInstance, "category[2]"));
        assertEquals(3, len(at(exportedInstance, "_category")));
        assertNull(at(exportedInstance, "_category[0]"));
        assertJsonEqual("""
        {
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
              "valueCode": "unknown"
            }
          ]
        }
        """, at(exportedInstance, "_category[1]"));
        assertNull(at(exportedInstance, "_category[2]"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should assign extensions on elements of a primitive array when extensions are assigned before the values")
      void shouldAssignExtensionsOnElementsOfAPrimitiveArrayWhenExtensionsAreAssignedBefore() {
        // Instance: MyAllergies
        // InstanceOf: AllergyIntolerance
        // * patient = Reference(SomePatient)
        // * category[0].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode = #unknown
        // * category[1] = #environment
        // * category[2] = #food
        Instance instance = new Instance("MyAllergies");
        instance.instanceOf = "AllergyIntolerance";
        AssignmentRule patientReference = new AssignmentRule("patient");
        patientReference.value = new FshReference("SomePatient");
        AssignmentRule categoryExtension = new AssignmentRule("category[0].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode");
        categoryExtension.value = new FshCode("unknown");
        AssignmentRule categoryEnvironment = new AssignmentRule("category[1]");
        categoryEnvironment.value = new FshCode("environment");
        AssignmentRule categoryFood = new AssignmentRule("category[2]");
        categoryFood.value = new FshCode("food");
        instance.rules.addAll(Arrays.asList(patientReference, categoryExtension, categoryEnvironment, categoryFood));
        InstanceDefinition exportedInstance = exportInstance.apply(instance);
        assertEquals(3, len(at(exportedInstance, "category")));
        assertNull(at(exportedInstance, "category[0]"));
        assertEquals("environment", at(exportedInstance, "category[1]"));
        assertEquals("food", at(exportedInstance, "category[2]"));
        assertEquals(3, len(at(exportedInstance, "_category")));
        assertJsonEqual("""
        {
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
              "valueCode": "unknown"
            }
          ]
        }
        """, at(exportedInstance, "_category[0]"));
        assertNull(at(exportedInstance, "_category[1]"));
        assertNull(at(exportedInstance, "_category[2]"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should assign extensions and values on out-of-order elements on a primitive array")
      void shouldAssignExtensionsAndValuesOnOutOfOrderElementsOnAPrimitiveArray() {
        // Instance: MyAllergies
        // InstanceOf: AllergyIntolerance
        // * patient = Reference(SomePatient)
        // * category[2] = #food
        // * category[1].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode = #unknown
        // * category[0] = #environment
        Instance instance = new Instance("MyAllergies");
        instance.instanceOf = "AllergyIntolerance";
        AssignmentRule patientReference = new AssignmentRule("patient");
        patientReference.value = new FshReference("SomePatient");
        AssignmentRule categoryFood = new AssignmentRule("category[2]");
        categoryFood.value = new FshCode("food");
        AssignmentRule categoryExtension = new AssignmentRule("category[1].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode");
        categoryExtension.value = new FshCode("unknown");
        AssignmentRule categoryEnvironment = new AssignmentRule("category[0]");
        categoryEnvironment.value = new FshCode("environment");
        instance.rules.addAll(Arrays.asList(patientReference, categoryFood, categoryExtension, categoryEnvironment));
        InstanceDefinition exportedInstance = exportInstance.apply(instance);
        assertEquals(3, len(at(exportedInstance, "category")));
        assertEquals("environment", at(exportedInstance, "category[0]"));
        assertNull(at(exportedInstance, "category[1]"));
        assertEquals("food", at(exportedInstance, "category[2]"));
        assertEquals(3, len(at(exportedInstance, "_category")));
        assertNull(at(exportedInstance, "_category[0]"));
        assertJsonEqual("""
        {
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
              "valueCode": "unknown"
            }
          ]
        }
        """, at(exportedInstance, "_category[1]"));
        assertNull(at(exportedInstance, "_category[2]"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should assign extensions and values on out-of-order elements on a primitive array when extensions are assigned before values")
      void shouldAssignExtensionsAndValuesOnOutOfOrderElementsOnAPrimitiveArrayWhenExtensio() {
        // Instance: MyAllergies
        // InstanceOf: AllergyIntolerance
        // * patient = Reference(SomePatient)
        // * category[2].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode = #unknown
        // * category[1] = #food
        // * category[0] = #environment
        Instance instance = new Instance("MyAllergies");
        instance.instanceOf = "AllergyIntolerance";
        AssignmentRule patientReference = new AssignmentRule("patient");
        patientReference.value = new FshReference("SomePatient");
        AssignmentRule categoryExtension = new AssignmentRule("category[2].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode");
        categoryExtension.value = new FshCode("unknown");
        AssignmentRule categoryFood = new AssignmentRule("category[1]");
        categoryFood.value = new FshCode("food");
        AssignmentRule categoryEnvironment = new AssignmentRule("category[0]");
        categoryEnvironment.value = new FshCode("environment");
        instance.rules.addAll(Arrays.asList(patientReference, categoryExtension, categoryFood, categoryEnvironment));
        InstanceDefinition exportedInstance = exportInstance.apply(instance);
        assertEquals(3, len(at(exportedInstance, "category")));
        assertEquals("environment", at(exportedInstance, "category[0]"));
        assertEquals("food", at(exportedInstance, "category[1]"));
        assertNull(at(exportedInstance, "category[2]"));
        assertEquals(3, len(at(exportedInstance, "_category")));
        assertNull(at(exportedInstance, "_category[0]"));
        assertNull(at(exportedInstance, "_category[1]"));
        assertJsonEqual("""
        {
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
              "valueCode": "unknown"
            }
          ]
        }
        """, at(exportedInstance, "_category[2]"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should assign values and extensions on elements of a primitive array at the same index")
      void shouldAssignValuesAndExtensionsOnElementsOfAPrimitiveArrayAtTheSameIndex() {
        // Instance: MyAllergies
        // InstanceOf: AllergyIntolerance
        // * patient = Reference(SomePatient)
        // * category[0].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode = #unknown
        // * category[0] = #environment
        // * category[1] = #food
        Instance instance = new Instance("MyAllergies");
        instance.instanceOf = "AllergyIntolerance";
        AssignmentRule patientReference = new AssignmentRule("patient");
        patientReference.value = new FshReference("SomePatient");
        AssignmentRule categoryExtension = new AssignmentRule("category[0].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode");
        categoryExtension.value = new FshCode("unknown");
        AssignmentRule categoryEnvironment = new AssignmentRule("category[0]");
        categoryEnvironment.value = new FshCode("environment");
        AssignmentRule categoryFood = new AssignmentRule("category[1]");
        categoryFood.value = new FshCode("food");
        instance.rules.addAll(Arrays.asList(patientReference, categoryExtension, categoryEnvironment, categoryFood));
        InstanceDefinition exportedInstance = exportInstance.apply(instance);
        assertEquals(2, len(at(exportedInstance, "category")));
        assertEquals("environment", at(exportedInstance, "category[0]"));
        assertEquals("food", at(exportedInstance, "category[1]"));
        assertEquals(2, len(at(exportedInstance, "_category")));
        assertJsonEqual("""
        {
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
              "valueCode": "unknown"
            }
          ]
        }
        """, at(exportedInstance, "_category[0]"));
        assertNull(at(exportedInstance, "_category[1]"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should assign extensions on elements of a sliced primitive array")
      void shouldAssignExtensionsOnElementsOfASlicedPrimitiveArray() {
        // Profile: SlicedAllergyIntolerance
        // Parent: AllergyIntolerance
        // * category ^slicing.discriminator.type = #value
        // * category ^slicing.discriminator.path = "$this"
        // * category ^slicing.rules = #open
        // * category contains Primary 0..* and Secondary 0..*
        Profile profile = new Profile("SlicedAllergyIntolerance");
        profile.parent = "AllergyIntolerance";
        CaretValueRule slicingType = new CaretValueRule("category");
        slicingType.caretPath = "slicing.discriminator.type";
        slicingType.value = new FshCode("value");
        CaretValueRule slicingPath = new CaretValueRule("category");
        slicingPath.caretPath = "slicing.discriminator.path";
        slicingPath.value = "$this";
        CaretValueRule slicingRules = new CaretValueRule("category");
        slicingRules.caretPath = "slicing.rules";
        slicingRules.value = new FshCode("open");
        ContainsRule categoryContains = new ContainsRule("category");
        categoryContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Primary"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "Secondary"
        }
        """))));
        CardRule primaryCard = new CardRule("category[Primary]");
        primaryCard.min = 0.0;
        primaryCard.max = "*";
        CardRule secondaryCard = new CardRule("category[Secondary]");
        secondaryCard.min = 0.0;
        secondaryCard.max = "*";
        profile.rules.addAll(Arrays.asList(slicingType, slicingPath, slicingRules, categoryContains, primaryCard, secondaryCard));
        doc.profiles.put(profile.name, profile);
        // Instance: MyAllergies
        // InstanceOf: SlicedAllergyIntolerance
        // * patient = Reference(SomePatient)
        // * category[Secondary][+] = #environment
        // * category[Primary][+] = #food
        // * category[Primary][+].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode = #unknown
        // * category[Primary][+] = #medication
        // * category[+].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode = #unknown
        Instance instance = new Instance("MyAllergies");
        instance.instanceOf = "SlicedAllergyIntolerance";
        AssignmentRule patientReference = new AssignmentRule("patient");
        patientReference.value = new FshReference("SomePatient");
        AssignmentRule secondaryEnvironment = new AssignmentRule("category[Secondary][+]");
        secondaryEnvironment.value = new FshCode("environment");
        AssignmentRule primaryFood = new AssignmentRule("category[Primary][+]");
        primaryFood.value = new FshCode("food");
        AssignmentRule primaryExtension = new AssignmentRule("category[Primary][+].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode");
        primaryExtension.value = new FshCode("unknown");
        AssignmentRule unnamedExtension = new AssignmentRule("category[+].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode");
        AssignmentRule primaryMedication = new AssignmentRule("category[Primary][+]");
        primaryMedication.value = new FshCode("medication");
        unnamedExtension.value = new FshCode("unknown");
        instance.rules.addAll(Arrays.asList(patientReference, secondaryEnvironment, primaryFood, primaryExtension, primaryMedication, unnamedExtension));
        InstanceDefinition exported = exportInstance.apply(instance);
        assertEquals(5, len(at(exported, "category")));
        assertJsonEqual("""
        [
          "environment",
          "food",
          null,
          "medication",
          null
        ]
        """, at(exported, "category"));
        assertEquals(5, len(at(exported, "_category")));
        assertJsonEqual("""
        [
          null,
          null,
          {
            "extension": [
              {
                "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                "valueCode": "unknown"
              }
            ]
          },
          null,
          {
            "extension": [
              {
                "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                "valueCode": "unknown"
              }
            ]
          }
        ]
        """, at(exported, "_category"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance strict slice name usage should log an error when a required primitive value element is missing on the second element of a parent array primitive, with strict slice ordering enabled")
      void shouldLogAnErrorWhenARequiredPrimitiveValueElementIsMissingOnTheSecondElementOfA() {
        // Profile: TestAllergyIntolerance
        // Parent: AllergyIntolerance
        // * category 1..*
        // * category.value 1..1
        // * patient = Reference(SomePatient)
        Profile allergyProfile = new Profile("TestAllergyIntolerance");
        allergyProfile.parent = "AllergyIntolerance";
        CardRule categoryCard = new CardRule("category");
        categoryCard.min = 1.0;
        categoryCard.max = "*";
        CardRule valueCard = new CardRule("category.value");
        valueCard.min = 1.0;
        valueCard.max = "1";
        AssignmentRule patientValue = new AssignmentRule("patient");
        patientValue.value = new FshReference("SomePatient");
        allergyProfile.rules.addAll(Arrays.asList(categoryCard, valueCard, patientValue));
        doc.profiles.put(allergyProfile.name, allergyProfile);
        // Instance: MyAllergies
        // InstanceOf: TestAllergyIntolerance
        // * category[0] = #environment
        // * category[1].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode = #unknown
        Instance allergyInstance = new Instance("MyAllergies").withFile("AllergyInstance.fsh").withLocation(new int[] {14, 3, 21, 28});
        allergyInstance.instanceOf = "TestAllergyIntolerance";
        AssignmentRule environmentCategory = new AssignmentRule("category[0]");
        environmentCategory.value = new FshCode("environment");
        AssignmentRule unknownCategory = new AssignmentRule("category[1].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode");
        unknownCategory.value = new FshCode("unknown");
        allergyInstance.rules.addAll(Arrays.asList(environmentCategory, unknownCategory));
        doc.instances.put(allergyInstance.name, allergyInstance);
        exportInstance.apply(allergyInstance);
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertMatches("Element AllergyIntolerance.category.value has minimum cardinality 1.*File: AllergyInstance\\.fsh.*Line: 14 - 21", "s", loggerSpy.getLastMessage("error"));
      }

    }
    @Test
    @DisplayName("InstanceExporter #exportInstance should only create optional slices that are defined even if sibling in array has more slices than other siblings")
    void shouldOnlyCreateOptionalSlicesThatAreDefinedEvenIfSiblingInArrayHasMoreSlicesTha() {
      Extension simpleExt = new Extension("SimpleExt");
      OnlyRule onlyRule = new OnlyRule("value[x]");
      onlyRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "boolean"
        }
        """))));
      simpleExt.rules.add(onlyRule);
      doc.extensions.put(simpleExt.name, simpleExt);
      // * identifier[0].extension[my-ext][0].valueBoolean = true
      // * identifier[0].extension[my-ext][1].valueBoolean = false
      // * identifier[1].extension[my-ext][0].valueBoolean = true
      AssignmentRule identifier0Extension0 = new AssignmentRule("identifier[0].extension[SimpleExt][0].valueBoolean");
      identifier0Extension0.value = true;
      AssignmentRule identifier0Extension1 = new AssignmentRule("identifier[0].extension[SimpleExt][1].valueBoolean");
      identifier0Extension1.value = false;
      AssignmentRule identifier1Extension0 = new AssignmentRule("identifier[1].extension[SimpleExt][0].valueBoolean");
      identifier1Extension0.value = true;
      patientInstance.rules.addAll(Arrays.asList(identifier0Extension0, identifier0Extension1, identifier1Extension0));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "extension": [
              {
                "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/SimpleExt",
                "valueBoolean": true
              },
              {
                "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/SimpleExt",
                "valueBoolean": false
              }
            ]
          },
          {
            "extension": [
              {
                "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/SimpleExt",
                "valueBoolean": true
              }
            ]
          }
        ]
        """, at(exported, "identifier"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should do the above but with a required slice from the profile")
    void shouldDoTheAboveButWithARequiredSliceFromTheProfile() {
      Extension simpleExt = new Extension("SimpleExt");
      OnlyRule onlyRule = new OnlyRule("value[x]");
      onlyRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "boolean"
        }
        """))));
      simpleExt.rules.add(onlyRule);
      doc.extensions.put(simpleExt.name, simpleExt);
      // NOTE: This 1..* requirement is what differs this test from the one above
      // (aka 'should only create optional slices that are defined even if sibling in array has more slices than other siblings')
      // Profile rules
      // identifier.extension contains SimpleExt named my-ext 1..*
      ContainsRule containsExt = new ContainsRule("identifier.extension");
      containsExt.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "my-ext",
          "type": "SimpleExt"
        }
        """))));
      CardRule cardExt = new CardRule("identifier.extension[my-ext]");
      cardExt.min = 1.0;
      cardExt.max = "*";
      patient.rules.addAll(Arrays.asList(containsExt, cardExt));
      // Instance rules
      // * identifier[0].extension[my-ext][0].valueBoolean = true
      // * identifier[0].extension[my-ext][1].valueBoolean = false
      // * identifier[1].value = "Identifier One"
      AssignmentRule identifier0Extension0 = new AssignmentRule("identifier[0].extension[my-ext][0].valueBoolean");
      identifier0Extension0.value = true;
      AssignmentRule identifier0Extension1 = new AssignmentRule("identifier[0].extension[my-ext][1].valueBoolean");
      identifier0Extension1.value = false;
      AssignmentRule identifier1Value = new AssignmentRule("identifier[1].value");
      identifier1Value.value = "Identifier One";
      patientInstance.rules.addAll(Arrays.asList(identifier0Extension0, identifier0Extension1, identifier1Value));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "extension": [
              {
                "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/SimpleExt",
                "valueBoolean": true
              },
              {
                "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/SimpleExt",
                "valueBoolean": false
              }
            ]
          },
          {
            "extension": [
              {
                "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/SimpleExt"
              }
            ],
            "value": "Identifier One"
          }
        ]
        """, at(exported, "identifier"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should output no warnings when assigning a value[x] choice type on an extension element")
    void shouldOutputNoWarningsWhenAssigningAValueXChoiceTypeOnAnExtensionElement() {
      Extension valueBooleanExtension = new Extension("ExtensionWithValueBoolean");
      OnlyRule onlyRule = new OnlyRule("value[x]");
      onlyRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "boolean"
        }
        """))));
      valueBooleanExtension.rules.add(onlyRule);
      doc.extensions.put(valueBooleanExtension.name, valueBooleanExtension);
      AssignmentRule valueBoolean = new AssignmentRule("extension[ExtensionWithValueBoolean].valueBoolean");
      valueBoolean.value = true;
      patientInstance.rules.add(valueBoolean);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/ExtensionWithValueBoolean",
            "valueBoolean": true
          }
        ]
        """, at(exported, "extension"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should output an error when a choice element has values assigned to more than one choice type")
    void shouldOutputAnErrorWhenAChoiceElementHasValuesAssignedToMoreThanOneChoiceType() {
      // * multipleBirthBoolean = true
      // * multipleBirthInteger = 2
      AssignmentRule multipleBirthBoolean = new AssignmentRule("multipleBirthBoolean");
      multipleBirthBoolean.value = true;
      AssignmentRule multipleBirthInteger = new AssignmentRule("multipleBirthInteger").withFile("Twins.fsh").withLocation(new int[] {4, 3, 4, 34});
      multipleBirthInteger.value = new java.math.BigInteger("2");
      patientInstance.rules.addAll(Arrays.asList(multipleBirthBoolean, multipleBirthInteger));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals(true, at(exported, "multipleBirthBoolean"));
      assertJsEqual(2.0, at(exported, "multipleBirthInteger"));
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches("Bar contains multiple choice value assignments for choice element Patient\\.multipleBirth\\[x\\]\\..*File: PatientInstance\\.fsh.*Line: 10 - 20\\D*", "s", loggerSpy.getLastMessage("error"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should output an error when a choice element has values assigned to more than one choice type, some of which are a complex type")
    void shouldOutputAnErrorWhenAChoiceElementHasValuesAssignedToMoreThanOneChoiceTypeSom() {
      // Instance: sample-observation
      // InstanceOf: Observation
      // * status = #draft
      // * code = #123
      // * valueString = "my string value"
      // * valueCodeableConcept.text = "explanation of codeable concept"
      Instance obsInstance = new Instance("sample-observation").withFile("Observations.fsh").withLocation(new int[] {8, 3, 15, 44});
      obsInstance.instanceOf = "Observation";
      AssignmentRule obsStatus = new AssignmentRule("status");
      obsStatus.value = new FshCode("draft");
      AssignmentRule obsCode = new AssignmentRule("code");
      obsCode.value = new FshCode("123");
      AssignmentRule obsString = new AssignmentRule("valueString");
      obsString.value = "my string value";
      AssignmentRule obsCodeableConceptText = new AssignmentRule("valueCodeableConcept.text");
      obsCodeableConceptText.value = "explanation of codeable concept";
      obsInstance.rules.addAll(Arrays.asList(obsStatus, obsCode, obsString, obsCodeableConceptText));
      InstanceDefinition exported = exportInstance.apply(obsInstance);
      assertEquals("my string value", at(exported, "valueString"));
      assertEquals("explanation of codeable concept", at(exported, "valueCodeableConcept.text"));
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches("sample-observation contains multiple choice value assignments for choice element Observation\\.value\\[x\\]\\..*File: Observations\\.fsh.*Line: 8 - 15\\D*", "s", loggerSpy.getLastMessage("error"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not output an error when a multiple-cardinality choice element has different types at different indices")
    void shouldNotOutputAnErrorWhenAMultipleCardinalityChoiceElementHasDifferentTypesAtDi() {
      // Instance: sample-observation
      // InstanceOf: Observation
      // * status = #draft
      // * code = #123
      // * component[0].code = #123string
      // * component[0].valueString = "my string value"
      // * component[1].code = #123codeableConcept
      // * component[1].valueCodeableConcept = http://example.org#paper "the paper"
      Instance obsInstance = new Instance("sample-observation");
      obsInstance.instanceOf = "Observation";
      AssignmentRule obsStatus = new AssignmentRule("status");
      obsStatus.value = new FshCode("draft");
      AssignmentRule obsCode = new AssignmentRule("code");
      obsCode.value = new FshCode("123");
      AssignmentRule firstComponentCode = new AssignmentRule("component[0].code");
      firstComponentCode.value = new FshCode("123string");
      AssignmentRule firstComponentValue = new AssignmentRule("component[0].valueString");
      firstComponentValue.value = "my string value";
      AssignmentRule secondComponentCode = new AssignmentRule("component[1].code");
      secondComponentCode.value = new FshCode("123codeableConcept");
      AssignmentRule secondComponentValue = new AssignmentRule("component[1].valueCodeableConcept");
      secondComponentValue.value = new FshCode("paper", "http://example.org", "the paper");
      obsInstance.rules.addAll(Arrays.asList(obsStatus, obsCode, firstComponentCode, firstComponentValue, secondComponentCode, secondComponentValue));
      InstanceDefinition exported = exportInstance.apply(obsInstance);
      assertEquals("my string value", at(exported, "component[0].valueString"));
      assertJsonEqual("""
        {
          "coding": [
            {
              "code": "paper",
              "system": "http://example.org",
              "display": "the paper"
            }
          ]
        }
        """, at(exported, "component[1].valueCodeableConcept"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should output an error when a choice element within another element has values assigned to more than one choice type")
    void shouldOutputAnErrorWhenAChoiceElementWithinAnotherElementHasValuesAssignedToMore() {
      // * extension[0].url = "https://example.org/SomeExt"
      // * extension[0].valueString = "extension value is false"
      // * extension[0].valueBoolean = false
      AssignmentRule extensionUrl = new AssignmentRule("extension[0].url");
      extensionUrl.value = "https://example.org/SomeExt";
      AssignmentRule extensionString = new AssignmentRule("extension[0].valueString");
      extensionString.value = "extension value is false";
      AssignmentRule extensionBoolean = new AssignmentRule("extension[0].valueBoolean");
      extensionBoolean.value = false;
      extensionBoolean.rawValue = "false";
      patientInstance.rules.addAll(Arrays.asList(extensionUrl, extensionString, extensionBoolean));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "url": "https://example.org/SomeExt",
          "valueString": "extension value is false",
          "valueBoolean": false
        }
        """, at(exported, "extension[0]"));
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches("Bar contains multiple choice value assignments for choice element Patient\\.extension\\.value\\[x\\]\\..*File: PatientInstance\\.fsh.*Line: 10 - 20\\D*", "s", loggerSpy.getLastMessage("error"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should output an error when a choice element that is a descendant of a primitive has values assigned to more than one type")
    void shouldOutputAnErrorWhenAChoiceElementThatIsADescendantOfAPrimitiveHasValuesAssig() {
      // * gender.extension[0].url = "https://example.org/SomeExt"
      // * gender.extension[0].valueString = "patient's gender is unknowable"
      // * gender.extension[0].valueCode = #unknowable
      AssignmentRule extensionUrl = new AssignmentRule("gender.extension[0].url");
      extensionUrl.value = "https://example.org/SomeExt";
      AssignmentRule extensionString = new AssignmentRule("gender.extension[0].valueString");
      extensionString.value = "patient's gender is unknowable";
      AssignmentRule extensionCode = new AssignmentRule("gender.extension[0].valueCode");
      extensionCode.value = new FshCode("unknowable");
      patientInstance.rules.addAll(Arrays.asList(extensionUrl, extensionString, extensionCode));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "url": "https://example.org/SomeExt",
          "valueString": "patient's gender is unknowable",
          "valueCode": "unknowable"
        }
        """, at(exported, "_gender.extension[0]"));
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches("Bar contains multiple choice value assignments for choice element Patient\\.gender\\.extension\\.value\\[x\\]\\..*File: PatientInstance\\.fsh.*Line: 10 - 20\\D*", "s", loggerSpy.getLastMessage("error"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign cardinality 1..n elements that are assigned by array pattern[x] from a parent on the SD")
    void shouldAssignCardinality1NElementsThatAreAssignedByArrayPatternXFromAParentOnTheS() {
      AssignmentRule assignedValRule = new AssignmentRule("maritalStatus");
      assignedValRule.value = new FshCode("foo", "http://foo.com");
      patient.rules.add(assignedValRule);
      CardRule cardRule = new CardRule("maritalStatus");
      cardRule.min = 1.0;
      patient.rules.add(cardRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "coding": [
            {
              "code": "foo",
              "system": "http://foo.com"
            }
          ]
        }
        """, at(exported, "maritalStatus"));
    }

    // TODO: The assignValue functions should be updated to not assign a value when a parent element sets
    // the value to something different using a pattern
    @Test
    @DisplayName("InstanceExporter #exportInstance should not assign an element to a value different than a parent pattern value on the Structure Definition")
    @Disabled("it.skip")
    void shouldNotAssignAnElementToAValueDifferentThanAParentPatternValueOnTheStructureDe() {
      AssignmentRule assignedValRule = new AssignmentRule("maritalStatus");
      FshCode assignedFshCode = new FshCode("foo", "http://foo.com");
      assignedValRule.value = assignedFshCode;
      patient.rules.add(assignedValRule);
      AssignmentRule instanceAssignedValRule = new AssignmentRule("maritalStatus.coding[0].system");
      instanceAssignedValRule.value = "http://bar.com";
      patientInstance.rules.add(instanceAssignedValRule);
      assertThrows(RuntimeException.class, () -> exportInstance.apply(patientInstance));
    }

    // Assigning children of primitives
    @Test
    @DisplayName("InstanceExporter #exportInstance should assign children of primitive values on an instance")
    void shouldAssignChildrenOfPrimitiveValuesOnAnInstance() {
      AssignmentRule assignedValRule = new AssignmentRule("active.id");
      assignedValRule.value = "foo";
      patientInstance.rules.add(assignedValRule);
      doc.instances.put(patientInstance.name, patientInstance);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals("foo", at(exported, "_active.id"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign primitive values and their children on an instance")
    void shouldAssignPrimitiveValuesAndTheirChildrenOnAnInstance() {
      AssignmentRule assignedValRule1 = new AssignmentRule("active");
      assignedValRule1.value = true;
      patientInstance.rules.add(assignedValRule1);
      AssignmentRule assignedValRule2 = new AssignmentRule("active.id");
      assignedValRule2.value = "foo";
      patientInstance.rules.add(assignedValRule2);
      doc.instances.put(patientInstance.name, patientInstance);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals(true, at(exported, "active"));
      assertEquals("foo", at(exported, "_active.id"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign children of primitive value arrays on an instance")
    void shouldAssignChildrenOfPrimitiveValueArraysOnAnInstance() {
      AssignmentRule assignedValRule = new AssignmentRule("address[0].line[1].extension[0].url");
      assignedValRule.value = "foo";
      patientInstance.rules.add(assignedValRule);
      doc.instances.put(patientInstance.name, patientInstance);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals(1, len(at(exported, "address")));
      assertEquals(2, len(at(exported, "address[0]._line")));
      assertNull(at(exported, "address[0]._line[0]"));
      assertEquals(1, len(at(exported, "address[0]._line[1].extension")));
      assertEquals("foo", at(exported, "address[0]._line[1].extension[0].url"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign extensions and values on out-of-order elements on a primitive array")
    void shouldAssignExtensionsAndValuesOnOutOfOrderElementsOnAPrimitiveArray() {
      // Instance: MyAllergies
      // InstanceOf: AllergyIntolerance
      // * patient = Reference(SomePatient)
      // * category[1].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode = #unknown
      // * category[0] = #environment
      Instance instance = new Instance("MyAllergies");
      instance.instanceOf = "AllergyIntolerance";
      AssignmentRule patientReference = new AssignmentRule("patient");
      patientReference.value = new FshReference("SomePatient");
      AssignmentRule categoryValue = new AssignmentRule("category[0]");
      categoryValue.value = new FshCode("environment");
      AssignmentRule categoryExtension = new AssignmentRule("category[1].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode");
      categoryExtension.value = new FshCode("unknown");
      instance.rules.addAll(Arrays.asList(patientReference, categoryExtension, categoryValue));
      InstanceDefinition exportedInstance = exportInstance.apply(instance);
      assertEquals(2, len(at(exportedInstance, "category")));
      assertEquals("environment", at(exportedInstance, "category[0]"));
      assertEquals(2, len(at(exportedInstance, "_category")));
      assertNull(at(exportedInstance, "_category[0]"));
      assertJsonEqual("""
        {
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
              "valueCode": "unknown"
            }
          ]
        }
        """, at(exportedInstance, "_category[1]"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign children of primitive value arrays on an instance with out of order rules")
    void shouldAssignChildrenOfPrimitiveValueArraysOnAnInstanceWithOutOfOrderRules() {
      AssignmentRule assignedValRule1 = new AssignmentRule("address[0].line[1].extension[0].url");
      assignedValRule1.value = "bar";
      patientInstance.rules.add(assignedValRule1);
      AssignmentRule assignedValRule2 = new AssignmentRule("address[0].line[0].extension[0].url");
      assignedValRule2.value = "foo";
      patientInstance.rules.add(assignedValRule2);
      doc.instances.put(patientInstance.name, patientInstance);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertEquals(1, len(at(exported, "address")));
      assertEquals(2, len(at(exported, "address[0]._line")));
      assertEquals(1, len(at(exported, "address[0]._line[0].extension")));
      assertEquals("foo", at(exported, "address[0]._line[0].extension[0].url"));
      assertEquals(1, len(at(exported, "address[0]._line[1].extension")));
      assertEquals("bar", at(exported, "address[0]._line[1].extension[0].url"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign children of sliced primitive arrays on an instance")
    void shouldAssignChildrenOfSlicedPrimitiveArraysOnAnInstance() {
      CaretValueRule caretRule = new CaretValueRule("name.prefix");
      caretRule.caretPath = "slicing.discriminator.type";
      caretRule.value = new FshCode("value");
      ContainsRule containsRule = new ContainsRule("name.prefix");
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Dr"
        }
        """))));
      CardRule cardRule = new CardRule("name.prefix");
      cardRule.min = 0.0;
      cardRule.max = "*";
      // * name.prefix ^slicing.discriminator.type = #value
      // * name.prefix contains Dr 0..*
      patient.rules.addAll(Arrays.asList(caretRule, containsRule, cardRule));
      AssignmentRule assignedRule1 = new AssignmentRule("name[0].prefix[Dr][0]");
      assignedRule1.value = "Doctor";
      AssignmentRule assignedRule2 = new AssignmentRule("name[0].prefix[Dr][1]");
      assignedRule2.value = "Mister Doctor";
      AssignmentRule assignedRuleChild = new AssignmentRule("name[0].prefix[Dr][1].id");
      assignedRuleChild.value = "Sir Mister Doctor to you";
      // * name[0].prefix[Dr][0] = "Doctor"
      // * name[0].prefix[Dr][1] = "Mister Doctor"
      // * name[0].prefix[Dr][1].id = "Sir Mister Doctor to you";
      patientInstance.rules.addAll(Arrays.asList(assignedRule1, assignedRule2, assignedRuleChild));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "prefix": [
              "Doctor",
              "Mister Doctor"
            ],
            "_prefix": [
              null,
              {
                "id": "Sir Mister Doctor to you"
              }
            ]
          }
        ]
        """, at(exported, "name"));
    }

    @DisplayName("Issue #1631")
    @Nested
    class Issue1631 {
      String displayUrl = "http://hl7.org/fhir/StructureDefinition/display";

      @Test
      @DisplayName("InstanceExporter #exportInstance Issue #1631 should keep a null-padded value array when a repeating primitive has only an extension")
      void shouldKeepANullPaddedValueArrayWhenARepeatingPrimitiveHasOnlyAnExtension() {
        // Instance: NoValues
        // InstanceOf: Patient
        // * name[0].given[0].extension[0].url = http://hl7.org/fhir/StructureDefinition/display
        // * name[0].given[0].extension[0].valueString = "ext on given[0]"
        Instance instance = new Instance("NoValues");
        instance.instanceOf = "Patient";
        AssignmentRule urlRule = new AssignmentRule("name[0].given[0].extension[0].url");
        urlRule.value = displayUrl;
        AssignmentRule valueRule = new AssignmentRule("name[0].given[0].extension[0].valueString");
        valueRule.value = "ext on given[0]";
        instance.rules.addAll(Arrays.asList(urlRule, valueRule));
        InstanceDefinition exported = exportInstance.apply(instance);
        assertJsEqual(obj("given", new ArrayList<>(Collections.singletonList(null)), "_given", new ArrayList<>(Arrays.asList(obj("extension", new ArrayList<>(Arrays.asList(obj("url", displayUrl, "valueString", "ext on given[0]"))))))), at(exported, "name[0]"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance Issue #1631 should keep aligned value and underscore arrays when a repeating primitive has mixed values and extensions")
      void shouldKeepAlignedValueAndUnderscoreArraysWhenARepeatingPrimitiveHasMixedValuesAn() {
        // Instance: SomeValues
        // InstanceOf: Patient
        // * name[0].given[0] = "Xavier"
        // * name[0].given[1].extension[0].url = http://hl7.org/fhir/StructureDefinition/display
        // * name[0].given[1].extension[0].valueString = "ext on given[1]"
        Instance instance = new Instance("SomeValues");
        instance.instanceOf = "Patient";
        AssignmentRule givenRule = new AssignmentRule("name[0].given[0]");
        givenRule.value = "Xavier";
        AssignmentRule urlRule = new AssignmentRule("name[0].given[1].extension[0].url");
        urlRule.value = displayUrl;
        AssignmentRule valueRule = new AssignmentRule("name[0].given[1].extension[0].valueString");
        valueRule.value = "ext on given[1]";
        instance.rules.addAll(Arrays.asList(givenRule, urlRule, valueRule));
        InstanceDefinition exported = exportInstance.apply(instance);
        assertJsEqual(obj("given", new ArrayList<>(Arrays.asList("Xavier", null)), "_given", new ArrayList<>(Arrays.asList(null, obj("extension", new ArrayList<>(Arrays.asList(obj("url", displayUrl, "valueString", "ext on given[1]"))))))), at(exported, "name[0]"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance Issue #1631 should not add a dummy null value on a non-repeating primitive that has only an extension")
      void shouldNotAddADummyNullValueOnANonRepeatingPrimitiveThatHasOnlyAnExtension() {
        // Instance: NonRepeating
        // InstanceOf: Patient
        // * name[0].family.extension[0].url = http://hl7.org/fhir/StructureDefinition/display
        // * name[0].family.extension[0].valueString = "ext on family"
        Instance instance = new Instance("NonRepeating");
        instance.instanceOf = "Patient";
        AssignmentRule urlRule = new AssignmentRule("name[0].family.extension[0].url");
        urlRule.value = displayUrl;
        AssignmentRule valueRule = new AssignmentRule("name[0].family.extension[0].valueString");
        valueRule.value = "ext on family";
        instance.rules.addAll(Arrays.asList(urlRule, valueRule));
        InstanceDefinition exported = exportInstance.apply(instance);
        assertJsEqual(obj("_family", obj("extension", new ArrayList<>(Arrays.asList(obj("url", displayUrl, "valueString", "ext on family"))))), at(exported, "name[0]"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance Issue #1631 should keep a null-padded value array when a repeating primitive has only an id")
      void shouldKeepANullPaddedValueArrayWhenARepeatingPrimitiveHasOnlyAnId() {
        // Instance: NoValuesId
        // InstanceOf: Patient
        // * name[0].given[0].id = "given-0"
        Instance instance = new Instance("NoValuesId");
        instance.instanceOf = "Patient";
        AssignmentRule idRule = new AssignmentRule("name[0].given[0].id");
        idRule.value = "given-0";
        instance.rules.add(idRule);
        InstanceDefinition exported = exportInstance.apply(instance);
        assertJsonEqual("""
        {
          "given": [
            null
          ],
          "_given": [
            {
              "id": "given-0"
            }
          ]
        }
        """, at(exported, "name[0]"));
      }

    }
    // Assigning References
    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference while resolving the Instance of a resource being referred to")
    void shouldAssignAReferenceWhileResolvingTheInstanceOfAResourceBeingReferredTo() {
      Instance orgInstance = new Instance("TestOrganization");
      orgInstance.instanceOf = "Organization";
      AssignmentRule assignedIdRule = new AssignmentRule("id");
      assignedIdRule.value = "org-id";
      orgInstance.rules.add(assignedIdRule);
      AssignmentRule assignedRefRule = new AssignmentRule("managingOrganization");
      assignedRefRule.value = new FshReference("TestOrganization");
      patientInstance.rules.add(assignedRefRule);
      doc.instances.put(patientInstance.name, patientInstance);
      doc.instances.put(orgInstance.name, orgInstance);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "reference": "Organization/org-id"
        }
        """, at(exported, "managingOrganization"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference while resolving the Instance of a profile being referred to")
    void shouldAssignAReferenceWhileResolvingTheInstanceOfAProfileBeingReferredTo() {
      Instance orgInstance = new Instance("TestOrganization");
      orgInstance.instanceOf = "USCoreOrganizationProfile";
      AssignmentRule assignedIdRule = new AssignmentRule("id");
      assignedIdRule.value = "usc-org-id";
      orgInstance.rules.add(assignedIdRule);
      AssignmentRule assignedRefRule = new AssignmentRule("managingOrganization");
      assignedRefRule.value = new FshReference("TestOrganization");
      patientInstance.rules.add(assignedRefRule);
      doc.instances.put(patientInstance.name, patientInstance);
      doc.instances.put(orgInstance.name, orgInstance);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "reference": "Organization/usc-org-id"
        }
        """, at(exported, "managingOrganization"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference while resolving the profile being referred to")
    void shouldAssignAReferenceWhileResolvingTheProfileBeingReferredTo() {
      Profile patientProfile = new Profile("PatientProfile");
      patientProfile.parent = "Patient";
      patientProfile.setId("patient-profile");
      doc.profiles.put(patientProfile.name, patientProfile);
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("PatientProfile");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "StructureDefinition/patient-profile"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference while resolving the non-FSH profile being referred to")
    void shouldAssignAReferenceWhileResolvingTheNonFSHProfileBeingReferredTo() {
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("bodyweight");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "StructureDefinition/bodyweight"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log warning when reference values do not resolve and is not an absolute or relative URL")
    void shouldLogWarningWhenReferenceValuesDoNotResolveAndIsNotAnAbsoluteOrRelativeURL() {
      // * target = Reference(exampleReferenceUnableToBeResolved)
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("exampleReferenceUnableToBeResolved");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "exampleReferenceUnableToBeResolved"
          }
        ]
        """, at(exported, "target"));
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertContainsText("Cannot find the entity referenced at exampleReferenceUnableToBeResolved. The provided reference value will be used, but this reference does not conform to the FHIR Reference format.", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log warning when reference values do not resolve and is a UUID or OID")
    void shouldNotLogWarningWhenReferenceValuesDoNotResolveAndIsAUUIDOrOID() {
      // * target = Reference(urn:uuid:exampleReference)
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("urn:uuid:exampleReference");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "urn:uuid:exampleReference"
          }
        ]
        """, at(exported, "target"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log warning when reference values do not resolve and is a relative URL with correct number of parts")
    void shouldNotLogWarningWhenReferenceValuesDoNotResolveAndIsARelativeURLWithCorrectNu() {
      // * target = Reference(UnresolvableType/exampleReferenceUnableToBeResolved)
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("UnresolvableType/exampleReferenceUnableToBeResolved");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "UnresolvableType/exampleReferenceUnableToBeResolved"
          }
        ]
        """, at(exported, "target"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log warning when reference values do not resolve and is a relative URL but has more than two parts")
    void shouldNotLogWarningWhenReferenceValuesDoNotResolveAndIsARelativeURLButHasMoreTha() {
      // * target = Reference(More/Than/Two/Parts/exampleReferenceUnableToBeResolved)
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("More/Than/Two/Parts/exampleReferenceUnableToBeResolved");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "More/Than/Two/Parts/exampleReferenceUnableToBeResolved"
          }
        ]
        """, at(exported, "target"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log warning when reference values are an absolute URL")
    void shouldNotLogWarningWhenReferenceValuesAreAnAbsoluteURL() {
      // * target = Reference(http://foo.org/fhir/Patient/abc)
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("http://foo.org/fhir/Patient/abc");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "http://foo.org/fhir/Patient/abc"
          }
        ]
        """, at(exported, "target"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference leaving the full profile URL when it is specified")
    void shouldAssignAReferenceLeavingTheFullProfileURLWhenItIsSpecified() {
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("http://hl7.org/fhir/StructureDefinition/bodyweight");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "http://hl7.org/fhir/StructureDefinition/bodyweight"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference while resolving the Extension being referred to")
    void shouldAssignAReferenceWhileResolvingTheExtensionBeingReferredTo() {
      Extension simpleExtension = new Extension("SimpleExtension");
      simpleExtension.setId("simple-extension");
      doc.extensions.put(simpleExtension.name, simpleExtension);
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("SimpleExtension");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "StructureDefinition/simple-extension"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference while resolving the non-FSH extension being referred to")
    void shouldAssignAReferenceWhileResolvingTheNonFSHExtensionBeingReferredTo() {
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("fmm");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "StructureDefinition/structuredefinition-fmm"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference leaving the full extension URL when it is specified")
    void shouldAssignAReferenceLeavingTheFullExtensionURLWhenItIsSpecified() {
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference while resolving the Logical being referred to")
    void shouldAssignAReferenceWhileResolvingTheLogicalBeingReferredTo() {
      Logical surfboard = new Logical("SurfBoard");
      surfboard.setId("surfboard");
      doc.logicals.put(surfboard.name, surfboard);
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("SurfBoard");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "StructureDefinition/surfboard"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference while resolving the non-FSH logical being referred to")
    void shouldAssignAReferenceWhileResolvingTheNonFSHLogicalBeingReferredTo() {
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("ELTSSServiceModel");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "StructureDefinition/eLTSSServiceModel"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference leaving the full logical URL when it is specified")
    void shouldAssignAReferenceLeavingTheFullLogicalURLWhenItIsSpecified() {
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("http://hl7.org/fhir/us/eltss/StructureDefinition/eLTSSServiceModel");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "http://hl7.org/fhir/us/eltss/StructureDefinition/eLTSSServiceModel"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference while resolving the FSH Resource being referred to")
    void shouldAssignAReferenceWhileResolvingTheFSHResourceBeingReferredTo() {
      Resource pacman = new Resource("Pacman");
      pacman.setId("pacman");
      doc.resources.put(pacman.name, pacman);
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("Pacman");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "StructureDefinition/pacman"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference while resolving the non-FSH resource being referred to")
    void shouldAssignAReferenceWhileResolvingTheNonFSHResourceBeingReferredTo() {
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("Patient");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "StructureDefinition/Patient"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference leaving the full resource URL when it is specified")
    void shouldAssignAReferenceLeavingTheFullResourceURLWhenItIsSpecified() {
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("http://hl7.org/fhir/StructureDefinition/Patient");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "http://hl7.org/fhir/StructureDefinition/Patient"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference while resolving the CodeSystem being referred to")
    void shouldAssignAReferenceWhileResolvingTheCodeSystemBeingReferredTo() {
      FshCodeSystem codeSystem = new FshCodeSystem("SomeCodeSystem");
      codeSystem.setId("some-code-system");
      doc.codeSystems.put(codeSystem.name, codeSystem);
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("SomeCodeSystem");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "CodeSystem/some-code-system"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference while resolving the non-FSH CodeSystem being referred to")
    void shouldAssignAReferenceWhileResolvingTheNonFSHCodeSystemBeingReferredTo() {
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("W3cProvenanceActivityType");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "CodeSystem/w3c-provenance-activity-type"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference leaving the full CodeSystem URL when it is specified")
    void shouldAssignAReferenceLeavingTheFullCodeSystemURLWhenItIsSpecified() {
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("http://hl7.org/fhir/w3c-provenance-activity-type");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "http://hl7.org/fhir/w3c-provenance-activity-type"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference while resolving the ValueSet being referred to")
    void shouldAssignAReferenceWhileResolvingTheValueSetBeingReferredTo() {
      FshValueSet valueSet = new FshValueSet("SomeValueSet");
      valueSet.setId("some-value-set");
      doc.valueSets.put(valueSet.name, valueSet);
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("SomeValueSet");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "ValueSet/some-value-set"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference while resolving the non-FSH ValueSet being referred to")
    void shouldAssignAReferenceWhileResolvingTheNonFSHValueSetBeingReferredTo() {
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("ObservationCategoryCodes");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "ValueSet/observation-category"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference leaving the full ValueSet URL when it is specified")
    void shouldAssignAReferenceLeavingTheFullValueSetURLWhenItIsSpecified() {
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("http://hl7.org/fhir/ValueSet/observation-category");
      provenanceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "http://hl7.org/fhir/ValueSet/observation-category"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference to a contained instance using a fragment reference")
    void shouldAssignAReferenceToAContainedInstanceUsingAFragmentReference() {
      Instance orgInstance = new Instance("TestOrganization");
      orgInstance.instanceOf = "Organization";
      AssignmentRule assignedIdRule = new AssignmentRule("id");
      assignedIdRule.value = "org-id";
      orgInstance.rules.add(assignedIdRule);
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "TestOrganization";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("managingOrganization");
      assignedRefRule.value = new FshReference("TestOrganization");
      patientInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      doc.instances.put(patientInstance.name, patientInstance);
      doc.instances.put(orgInstance.name, orgInstance);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "reference": "#org-id"
        }
        """, at(exported, "managingOrganization"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference to a contained Profile using a fragment reference")
    void shouldAssignAReferenceToAContainedProfileUsingAFragmentReference() {
      Profile patientProfile = new Profile("PatientProfile");
      patientProfile.parent = "Patient";
      patientProfile.setId("patient-profile");
      doc.profiles.put(patientProfile.name, patientProfile);
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "PatientProfile";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("PatientProfile");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("patient-profile", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#patient-profile"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference to a contained non-FSH profile using a fragment reference")
    void shouldAssignAReferenceToAContainedNonFSHProfileUsingAFragmentReference() {
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "bodyweight";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("bodyweight");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("bodyweight", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#bodyweight"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a full URL reference to a contained non-FSH profile using a fragment reference")
    void shouldAssignAFullURLReferenceToAContainedNonFSHProfileUsingAFragmentReference() {
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "bodyweight";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("http://hl7.org/fhir/StructureDefinition/bodyweight");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("bodyweight", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#bodyweight"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference to a contained Extension using a fragment reference")
    void shouldAssignAReferenceToAContainedExtensionUsingAFragmentReference() {
      Extension simpleExtension = new Extension("SimpleExtension");
      simpleExtension.setId("simple-extension");
      doc.extensions.put(simpleExtension.name, simpleExtension);
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "SimpleExtension";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("SimpleExtension");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("simple-extension", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#simple-extension"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference to a contained non-FSH extension using a fragment reference")
    void shouldAssignAReferenceToAContainedNonFSHExtensionUsingAFragmentReference() {
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "fmm";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("fmm");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("structuredefinition-fmm", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#structuredefinition-fmm"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a full URL reference to a contained non-FSH extension using a fragment reference")
    void shouldAssignAFullURLReferenceToAContainedNonFSHExtensionUsingAFragmentReference() {
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "fmm";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("structuredefinition-fmm", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#structuredefinition-fmm"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference to a contained Logical using a fragment reference")
    void shouldAssignAReferenceToAContainedLogicalUsingAFragmentReference() {
      Logical surfboard = new Logical("SurfBoard");
      surfboard.setId("surfboard");
      doc.logicals.put(surfboard.name, surfboard);
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "SurfBoard";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("SurfBoard");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("surfboard", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#surfboard"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference to a contained non-FSH logical using a fragment reference")
    void shouldAssignAReferenceToAContainedNonFSHLogicalUsingAFragmentReference() {
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "ELTSSServiceModel";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("ELTSSServiceModel");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("eLTSSServiceModel", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#eLTSSServiceModel"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a full URL reference to a contained non-FSH logical using a fragment reference")
    void shouldAssignAFullURLReferenceToAContainedNonFSHLogicalUsingAFragmentReference() {
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "ELTSSServiceModel";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("http://hl7.org/fhir/us/eltss/StructureDefinition/eLTSSServiceModel");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("eLTSSServiceModel", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#eLTSSServiceModel"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference to a contained Resource using a fragment reference")
    void shouldAssignAReferenceToAContainedResourceUsingAFragmentReference() {
      Resource pacman = new Resource("Pacman");
      pacman.setId("pacman");
      doc.resources.put(pacman.name, pacman);
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "Pacman";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("Pacman");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("pacman", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#pacman"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference to a contained non-FSH resource using a fragment reference")
    void shouldAssignAReferenceToAContainedNonFSHResourceUsingAFragmentReference() {
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "Patient";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("Patient");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("Patient", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#Patient"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a full URL reference to a contained non-FSH resource using a fragment reference")
    void shouldAssignAFullURLReferenceToAContainedNonFSHResourceUsingAFragmentReference() {
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "Patient";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("http://hl7.org/fhir/StructureDefinition/Patient");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("Patient", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#Patient"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference to a contained CodeSystem using a fragment reference")
    void shouldAssignAReferenceToAContainedCodeSystemUsingAFragmentReference() {
      FshCodeSystem codeSystem = new FshCodeSystem("SomeCodeSystem");
      codeSystem.setId("some-code-system");
      doc.codeSystems.put(codeSystem.name, codeSystem);
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "SomeCodeSystem";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("SomeCodeSystem");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("some-code-system", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#some-code-system"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference to a contained non-FSH CodeSystem using a fragment reference")
    void shouldAssignAReferenceToAContainedNonFSHCodeSystemUsingAFragmentReference() {
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "W3cProvenanceActivityType";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("W3cProvenanceActivityType");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("w3c-provenance-activity-type", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#w3c-provenance-activity-type"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a full URL reference to a contained non-FSH CodeSystem using a fragment reference")
    void shouldAssignAFullURLReferenceToAContainedNonFSHCodeSystemUsingAFragmentReference() {
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "W3cProvenanceActivityType";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("http://hl7.org/fhir/w3c-provenance-activity-type");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("w3c-provenance-activity-type", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#w3c-provenance-activity-type"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference to a contained ValueSet using a fragment reference")
    void shouldAssignAReferenceToAContainedValueSetUsingAFragmentReference() {
      FshValueSet valueSet = new FshValueSet("SomeValueSet");
      valueSet.setId("some-value-set");
      doc.valueSets.put(valueSet.name, valueSet);
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "SomeValueSet";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("SomeValueSet");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("some-value-set", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#some-value-set"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference to a contained non-FSH ValueSet using a fragment reference")
    void shouldAssignAReferenceToAContainedNonFSHValueSetUsingAFragmentReference() {
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "ObservationCategoryCodes";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("ObservationCategoryCodes");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("observation-category", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#observation-category"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a full URL reference to a contained non-FSH ValueSet using a fragment reference")
    void shouldAssignAFullURLReferenceToAContainedNonFSHValueSetUsingAFragmentReference() {
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "ObservationCategoryCodes";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule = new AssignmentRule("target");
      assignedRefRule.value = new FshReference("http://hl7.org/fhir/ValueSet/observation-category");
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("observation-category", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          {
            "reference": "#observation-category"
          }
        ]
        """, at(exported, "target"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not convert non-reference values to contained fragment references")
    void shouldNotConvertNonReferenceValuesToContainedFragmentReferences() {
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "bodyweight";
      containedRule.isInstance = true;
      AssignmentRule assignedRefRule1 = new AssignmentRule("policy[0]");
      assignedRefRule1.value = "bodyweight";
      AssignmentRule assignedRefRule2 = new AssignmentRule("policy[1]");
      assignedRefRule2.value = "StructureDefinition/bodyweight";
      AssignmentRule assignedRefRule3 = new AssignmentRule("policy[2]");
      assignedRefRule3.value = "http://hl7.org/fhir/StructureDefinition/bodyweight";
      provenanceInstance.rules.addAll(Arrays.asList(containedRule, assignedRefRule1, assignedRefRule2, assignedRefRule3));
      InstanceDefinition exported = exportInstance.apply(provenanceInstance);
      assertEquals("bodyweight", at(exported, "contained[0].id"));
      assertJsonEqual("""
        [
          "bodyweight",
          "StructureDefinition/bodyweight",
          "http://hl7.org/fhir/StructureDefinition/bodyweight"
        ]
        """, at(exported, "policy"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference without replacing if the referred Instance does not exist")
    void shouldAssignAReferenceWithoutReplacingIfTheReferredInstanceDoesNotExist() {
      AssignmentRule assignedRefRule = new AssignmentRule("managingOrganization");
      assignedRefRule.value = new FshReference("http://hl7.org/fhir/us/minimal");
      patientInstance.rules.add(assignedRefRule);
      doc.instances.put(patientInstance.name, patientInstance);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "reference": "http://hl7.org/fhir/us/minimal"
        }
        """, at(exported, "managingOrganization"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference to a type based on a profile")
    void shouldAssignAReferenceToATypeBasedOnAProfile() {
      Instance basePatientInstance = new Instance("BasePatient");
      basePatientInstance.instanceOf = "Patient";
      doc.instances.put(basePatientInstance.name, basePatientInstance);
      // us-core-observation-lab constrains subject to be a reference to a us-core-patient
      // However, any patient instance can be assigned (because it might conform to the us-core-patient profile without explicitly specifying the profile)
      Instance profiledInstance = new Instance("MyExampleObservation");
      profiledInstance.instanceOf = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-lab";
      AssignmentRule assignedRefRule = new AssignmentRule("subject");
      assignedRefRule.value = new FshReference("BasePatient");
      profiledInstance.rules.add(assignedRefRule);
      doc.instances.put(profiledInstance.name, profiledInstance);
      InstanceDefinition exported = exportInstance.apply(profiledInstance);
      assertJsonEqual("""
        {
          "reference": "Patient/BasePatient"
        }
        """, at(exported, "subject"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference when the type has no targetProfile")
    void shouldAssignAReferenceWhenTheTypeHasNoTargetProfile() {
      Instance referencedPatientInstance = new Instance("ReferencedPatient");
      referencedPatientInstance.instanceOf = "Patient";
      doc.instances.put(referencedPatientInstance.name, referencedPatientInstance);
      AssignmentRule assignedRefRule = new AssignmentRule("extension.valueReference");
      assignedRefRule.value = new FshReference("ReferencedPatient");
      patientInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "reference": "Patient/ReferencedPatient"
        }
        """, at(exported, "extension[0].valueReference"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log a warning and ignore the version when assigning a reference that contains a version")
    void shouldLogAWarningAndIgnoreTheVersionWhenAssigningAReferenceThatContainsAVersion() {
      Instance orgInstance = new Instance("TestOrganization");
      orgInstance.instanceOf = "Organization";
      AssignmentRule assignedIdRule = new AssignmentRule("id");
      assignedIdRule.value = "org-id";
      orgInstance.rules.add(assignedIdRule);
      AssignmentRule assignedRefRule = new AssignmentRule("managingOrganization").withFile("Reference.fsh").withLocation(new int[] {5, 3, 5, 33});
      assignedRefRule.value = new FshReference("TestOrganization|2.3.4");
      patientInstance.rules.add(assignedRefRule);
      doc.instances.put(patientInstance.name, patientInstance);
      doc.instances.put(orgInstance.name, orgInstance);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "reference": "Organization/org-id"
        }
        """, at(exported, "managingOrganization"));
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("Reference assignments should not include a version\\..*File: Reference\\.fsh.*Line: 5\\D*", "s", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when an invalid reference is assigned")
    void shouldLogAnErrorWhenAnInvalidReferenceIsAssigned() {
      Instance observationInstance = new Instance("TestObservation");
      observationInstance.instanceOf = "Observation";
      doc.instances.put(observationInstance.name, observationInstance);
      AssignmentRule assignedRefRule = new AssignmentRule("contact[0].organization");
      assignedRefRule.value = new FshReference("TestObservation");
      // * contact[0].organization = Reference(TestObservation)
      patientInstance.rules.add(assignedRefRule);
      doc.instances.put(patientInstance.name, patientInstance);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("null", at(exported, "contact"));
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches("The type \"Reference\\(Observation\\)\" does not match any of the allowed types\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when assigning an invalid reference to a type based on a profile")
    void shouldLogAnErrorWhenAssigningAnInvalidReferenceToATypeBasedOnAProfile() {
      Instance groupInstance = new Instance("MyGroup");
      groupInstance.instanceOf = "Group";
      doc.instances.put(groupInstance.name, groupInstance);
      // us-core-observation-lab subject can only be a us-core-patient
      // Group, Device, and Location are allowed reference types on base Patient, but not this profile
      Instance profiledInstance = new Instance("MyExampleObservation");
      profiledInstance.instanceOf = "http://hl7.org/fhir/us/core/StructureDefinition/us-core-observation-lab";
      AssignmentRule assignedRefRule = new AssignmentRule("subject");
      assignedRefRule.value = new FshReference("MyGroup");
      profiledInstance.rules.add(assignedRefRule);
      doc.instances.put(profiledInstance.name, profiledInstance);
      InstanceDefinition exported = exportInstance.apply(profiledInstance);
      assertJsonEqual("null", at(exported, "subject"));
      assertMatches("The type \"Reference\\(Group\\)\" does not match any of the allowed types\\D*", "s", loggerSpy.getMessageAtIndex(0, "error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a reference to a child type of the referenced type")
    void shouldAssignAReferenceToAChildTypeOfTheReferencedType() {
      Instance documentReferenceInstance = new Instance("MyDocReference");
      documentReferenceInstance.instanceOf = "DocumentReference";
      doc.instances.put(documentReferenceInstance.name, documentReferenceInstance);
      // DocumentReference.context.related is a reference to Any
      AssignmentRule assignedRefRule = new AssignmentRule("context.related");
      assignedRefRule.value = new FshReference("Bar");
      documentReferenceInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(documentReferenceInstance);
      assertJsonEqual("""
        [
          {
            "reference": "Patient/Bar"
          }
        ]
        """, at(exported, "context.related"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error if an instance of a parent type is assigned")
    void shouldLogAnErrorIfAnInstanceOfAParentTypeIsAssigned() {
      Instance resourceInstance = new Instance("MyGeneralResource");
      resourceInstance.instanceOf = "Resource";
      doc.instances.put(resourceInstance.name, resourceInstance);
      // Subject can be a reference to Patient, Group, Device, or Location, which are all Resources
      // However, the reference must be to an instance of one of those types, not a generic Resource instance
      Instance observationInstance = new Instance("MyObservation");
      observationInstance.instanceOf = "Observation";
      AssignmentRule assignedRefRule = new AssignmentRule("subject");
      assignedRefRule.value = new FshReference("MyGeneralResource");
      observationInstance.rules.add(assignedRefRule);
      doc.instances.put(observationInstance.name, observationInstance);
      InstanceDefinition exported = exportInstance.apply(observationInstance);
      assertJsonEqual("null", at(exported, "subject"));
      assertMatches("The type \"Reference\\(Resource\\)\" does not match any of the allowed types\\D*", "s", loggerSpy.getMessageAtIndex(0, "error"));
    }

    // Assigning using Canonical
    @Test
    @DisplayName("InstanceExporter #exportInstance should apply an Assignment rule with a valid Canonical entity defined in FSH")
    void shouldApplyAnAssignmentRuleWithAValidCanonicalEntityDefinedInFSH() {
      Instance observationInstance = new Instance("MyObservation");
      observationInstance.instanceOf = "Observation";
      doc.instances.put(observationInstance.name, observationInstance);
      AssignmentRule assignedValueRule = new AssignmentRule("code.coding.system");
      assignedValueRule.value = new FshCanonical("VeryRealCodeSystem");
      observationInstance.rules.add(assignedValueRule);
      FshCodeSystem realCodeSystem = new FshCodeSystem("VeryRealCodeSystem");
      doc.codeSystems.put(realCodeSystem.name, realCodeSystem);
      InstanceDefinition exported = exportInstance.apply(observationInstance);
      assertJsonEqual("""
        {
          "coding": [
            {
              "system": "http://hl7.org/fhir/us/minimal/CodeSystem/VeryRealCodeSystem"
            }
          ]
        }
        """, at(exported, "code"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should apply an Assignment rule with Canonical of a FHIR entity")
    void shouldApplyAnAssignmentRuleWithCanonicalOfAFHIREntity() {
      Instance observationInstance = new Instance("MyObservation");
      observationInstance.instanceOf = "Observation";
      doc.instances.put(observationInstance.name, observationInstance);
      AssignmentRule assignedValueRule = new AssignmentRule("code.coding.system");
      assignedValueRule.value = new FshCanonical("MedicationRequest");
      observationInstance.rules.add(assignedValueRule);
      InstanceDefinition exported = exportInstance.apply(observationInstance);
      assertJsonEqual("""
        {
          "coding": [
            {
              "system": "http://hl7.org/fhir/StructureDefinition/MedicationRequest"
            }
          ]
        }
        """, at(exported, "code"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should apply an Assignment rule with Canonical of a Questionnaire instance")
    void shouldApplyAnAssignmentRuleWithCanonicalOfAQuestionnaireInstance() {
      Instance questionnaireInstance = new Instance("MyQuestionnaire");
      questionnaireInstance.usage = InstanceUsage.Definition;
      AssignmentRule urlRule = new AssignmentRule("url");
      urlRule.value = "http://my.awesome.questions.org/Questionnaire/MyQuestionnaire";
      questionnaireInstance.rules.add(urlRule);
      doc.instances.put(questionnaireInstance.name, questionnaireInstance);
      Instance responseInstance = new Instance("MyQuestionnaireResponse");
      responseInstance.instanceOf = "QuestionnaireResponse";
      AssignmentRule assignedValueRule = new AssignmentRule("questionnaire");
      assignedValueRule.value = new FshCanonical("MyQuestionnaire");
      responseInstance.rules.add(assignedValueRule);
      doc.instances.put(responseInstance.name, responseInstance);
      InstanceDefinition exported = exportInstance.apply(responseInstance);
      assertJsonEqual("\"http://my.awesome.questions.org/Questionnaire/MyQuestionnaire\"", at(exported, "questionnaire"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should apply an Assignment rule with Canonical of an inline instance")
    void shouldApplyAnAssignmentRuleWithCanonicalOfAnInlineInstance() {
      Instance observationInstance = new Instance("MyObservation");
      observationInstance.instanceOf = "Observation";
      doc.instances.put(observationInstance.name, observationInstance);
      Instance inlineInstance = new Instance("MyMedRequest");
      inlineInstance.usage = InstanceUsage.Inline;
      doc.instances.put(inlineInstance.name, inlineInstance);
      AssignmentRule assignedValueRule = new AssignmentRule("code.coding.system");
      assignedValueRule.value = new FshCanonical("MyMedRequest");
      observationInstance.rules.add(assignedValueRule);
      InstanceDefinition exported = exportInstance.apply(observationInstance);
      assertJsonEqual("""
        {
          "coding": [
            {
              "system": "#MyMedRequest"
            }
          ]
        }
        """, at(exported, "code"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should apply an Assignment rule with Canonical of an instance that has its url assigned by a RuleSet")
    void shouldApplyAnAssignmentRuleWithCanonicalOfAnInstanceThatHasItsUrlAssignedByARule() {
      // RuleSet: LibraryMetadata
      // * url = "http://fhir/ig/Library/273"
      // * version = "0.1.0"
      RuleSet ruleSet = new RuleSet("LibraryMetadata");
      AssignmentRule urlRule = new AssignmentRule("url");
      urlRule.value = "http://fhir/ig/Library/273";
      AssignmentRule versionRule = new AssignmentRule("version");
      versionRule.value = "0.1.0";
      ruleSet.rules.addAll(Arrays.asList(urlRule, versionRule));
      doc.ruleSets.put(ruleSet.name, ruleSet);
      // Instance: MyActivity
      // InstanceOf: ActivityDefinition
      // * status = #active
      // * library = Canonical(MyLibrary)
      Instance activityInstance = new Instance("MyActivity");
      activityInstance.instanceOf = "ActivityDefinition";
      AssignmentRule activityStatus = new AssignmentRule("status");
      activityStatus.value = new FshCode("active");
      AssignmentRule activityLibrary = new AssignmentRule("library");
      activityLibrary.value = new FshCanonical("MyLibrary");
      activityInstance.rules.addAll(Arrays.asList(activityStatus, activityLibrary));
      doc.instances.put(activityInstance.name, activityInstance);
      // Instance: MyLibrary
      // InstanceOf: Library
      // * insert LibraryMetadata
      // * status = #active
      // * type = #logic-library
      Instance libraryInstance = new Instance("MyLibrary");
      libraryInstance.instanceOf = "Library";
      InsertRule libraryInsert = new InsertRule("");
      libraryInsert.ruleSet = "LibraryMetadata";
      AssignmentRule libraryStatus = new AssignmentRule("status");
      libraryStatus.value = new FshCode("active");
      AssignmentRule libraryType = new AssignmentRule("type");
      libraryType.value = new FshCode("logic-library");
      libraryInstance.rules.addAll(Arrays.asList(libraryInsert, libraryStatus, libraryType));
      doc.instances.put(libraryInstance.name, libraryInstance);
      exporter.applyInsertRules();
      List<InstanceDefinition> instances = exporter.export().instances;
      InstanceDefinition exportedActivity = instances.stream().filter((instanceDefinition) -> Objects.equals(at(instanceDefinition, "id"), "MyActivity")).findFirst().orElse(null);
      assertJsonEqual("""
        [
          "http://fhir/ig/Library/273"
        ]
        """, at(exportedActivity, "library"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not apply an Assignment rule with an invalid Canonical entity and log an error")
    void shouldNotApplyAnAssignmentRuleWithAnInvalidCanonicalEntityAndLogAnError() {
      Instance observationInstance = new Instance("MyObservation");
      observationInstance.instanceOf = "Observation";
      doc.instances.put(observationInstance.name, observationInstance);
      AssignmentRule assignedValueRule = new AssignmentRule("code.coding.system");
      assignedValueRule.value = new FshCanonical("FakeCodeSystem");
      observationInstance.rules.add(assignedValueRule);
      InstanceDefinition exported = exportInstance.apply(observationInstance);
      assertJsonEqual("null", at(exported, "code"));
      assertMatches("Cannot use canonical URL of FakeCodeSystem because it does not exist.\\D*", "s", loggerSpy.getFirstMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a Canonical that is one of the valid types")
    void shouldAssignACanonicalThatIsOneOfTheValidTypes() {
      AssignmentRule assignedRefRule = new AssignmentRule("instantiatesCanonical");
      Instance pdInstance = new Instance("TestPD");
      pdInstance.instanceOf = "PlanDefinition";
      AssignmentRule urlRule = new AssignmentRule("url");
      urlRule.value = "http://example.org/PlanDefition/1";
      pdInstance.rules.add(urlRule);
      doc.instances.put(pdInstance.name, pdInstance);
      assignedRefRule.value = new FshCanonical("TestPD");
      // * instantiatesCanonical = Canonical(TestPD)
      carePlanInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(carePlanInstance);
      assertJsonEqual("""
        [
          "http://example.org/PlanDefition/1"
        ]
        """, at(exported, "instantiatesCanonical"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a Canonical that is one of the valid types (without checking the version) when the type is versioned")
    void shouldAssignACanonicalThatIsOneOfTheValidTypesWithoutCheckingTheVersionWhenTheTy() {
      // Profile: SpecialQuestionnaire
      // Parent: Questionnaire
      // derivedFrom only Canonical(TestQuestionnaire|3.1)
      Profile specialQuestionnaire = new Profile("SpecialQuestionnaire");
      specialQuestionnaire.parent = "Questionnaire";
      OnlyRule onlyRule = new OnlyRule("derivedFrom");
      onlyRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "TestQuestionnaire|3.1",
          "isCanonical": true
        }
        """))));
      specialQuestionnaire.rules.add(onlyRule);
      doc.profiles.put(specialQuestionnaire.name, specialQuestionnaire);
      // Instance: TQInstance
      // InstanceOf: TestQuestionnaire
      // url = "http://example.org/TQ/1"
      Instance tqInstance = new Instance("TQInstance");
      tqInstance.instanceOf = "TestQuestionnaire";
      AssignmentRule tqUrl = new AssignmentRule("url");
      tqUrl.value = "http://example.org/TQ/1";
      tqInstance.rules.add(tqUrl);
      doc.instances.put(tqInstance.name, tqInstance);
      // Instance: SQInstance
      // InstanceOf: SpecialQuestionnaire
      // derivedFrom = Canonical(TQInstance)
      Instance sqInstance = new Instance("SQInstance");
      sqInstance.instanceOf = "SpecialQuestionnaire";
      AssignmentRule sqDerivedFrom = new AssignmentRule("derivedFrom");
      sqDerivedFrom.value = new FshCanonical("TQInstance");
      sqInstance.rules.add(sqDerivedFrom);
      InstanceDefinition exported = exportInstance.apply(sqInstance);
      assertNotNull(at(exported, "derivedFrom"));
      assertJsonEqual("""
        [
          "http://example.org/TQ/1"
        ]
        """, at(exported, "derivedFrom"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a Canonical that is a child of the valid types")
    void shouldAssignACanonicalThatIsAChildOfTheValidTypes() {
      AssignmentRule assignedRefRule = new AssignmentRule("instantiatesCanonical");
      Profile pdProfile = new Profile("PlanDefProfile");
      pdProfile.parent = "PlanDefinition";
      doc.profiles.put(pdProfile.name, pdProfile);
      Instance pdInstance = new Instance("PlanDefInstance");
      pdInstance.instanceOf = "PlanDefProfile";
      AssignmentRule urlRule = new AssignmentRule("url");
      urlRule.value = "http://example.org/PlanDefition/1";
      pdInstance.rules.add(urlRule);
      doc.instances.put(pdInstance.name, pdInstance);
      assignedRefRule.value = new FshCanonical("PlanDefInstance");
      // * instantiatesCanonical = Canonical(PlanDefInstance)
      carePlanInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(carePlanInstance);
      assertJsonEqual("""
        [
          "http://example.org/PlanDefition/1"
        ]
        """, at(exported, "instantiatesCanonical"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign the right matching Canonical when the Canonical lookup matches multiple types")
    void shouldAssignTheRightMatchingCanonicalWhenTheCanonicalLookupMatchesMultipleTypes() {
      // Instance: MyClinicalGuidelinePD
      // InstanceOf: PlanDefinition
      // * id = "MyClinicalGuideline"
      // * status = #active
      Instance guidelinePD = new Instance("MyClinicalGuidelinePD");
      guidelinePD.instanceOf = "PlanDefinition";
      AssignmentRule idRule = new AssignmentRule("id");
      idRule.value = "MyClinicalGuideline";
      AssignmentRule statusRule = new AssignmentRule("status");
      statusRule.value = new FshCode("active");
      guidelinePD.rules.addAll(Arrays.asList(idRule, statusRule));
      doc.instances.put(guidelinePD.name, guidelinePD);
      // Instance: MyClinicalGuidelineLib
      // InstanceOf: Library
      // * id = "MyClinicalGuideline"
      // * status = #active
      // * type = #logic-library
      Instance guidelineLib = new Instance("MyClinicalGuidelineLib");
      guidelineLib.instanceOf = "Library";
      AssignmentRule typeRule = new AssignmentRule("type");
      typeRule.value = new FshCode("logic-library");
      // the id and status rules are the same as the pd one, so re-use them
      guidelineLib.rules.addAll(Arrays.asList(Js.cloneDeep(idRule), Js.cloneDeep(statusRule), typeRule));
      doc.instances.put(guidelineLib.name, guidelineLib);
      // Instance: MainPD
      // InstanceOf: PlanDefinition
      // * status = #active
      // * library = Canonical(MyClinicalGuideline)
      // * action.definitionCanonical = Canonical(MyClinicalGuideline)
      Instance mainPD = new Instance("MainPD");
      mainPD.instanceOf = "PlanDefinition";
      AssignmentRule libraryRule = new AssignmentRule("library");
      libraryRule.value = new FshCanonical("MyClinicalGuideline");
      AssignmentRule actionRule = new AssignmentRule("action.definitionCanonical");
      actionRule.value = new FshCanonical("MyClinicalGuideline");
      // the status rule is the same as the pd one so reuse it
      mainPD.rules.addAll(Arrays.asList(Js.cloneDeep(statusRule), libraryRule, actionRule));
      doc.instances.put(mainPD.name, mainPD);
      InstanceDefinition exported = exportInstance.apply(mainPD);
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      assertJsonEqual("""
        [
          "http://hl7.org/fhir/us/minimal/Library/MyClinicalGuideline"
        ]
        """, at(exported, "library"));
      assertJsonEqual("\"http://hl7.org/fhir/us/minimal/PlanDefinition/MyClinicalGuideline\"", at(exported, "action[0].definitionCanonical"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a Canonical as a #id fragment when referring to a contained resource created as a ValueSet entity")
    void shouldAssignACanonicalAsAIdFragmentWhenReferringToAContainedResourceCreatedAsAVa() {
      // ValueSet: ContainedValueSet
      // Id: contained-value-set
      FshValueSet valueSet = new FshValueSet("ContainedValueSet");
      valueSet.setId("contained-value-set");
      doc.valueSets.put(valueSet.name, valueSet);
      // ValueSet: SecondContainedValueSet
      // Id: second-contained-value-set
      FshValueSet valueSet2 = new FshValueSet("SecondContainedValueSet");
      valueSet2.setId("second-contained-value-set");
      doc.valueSets.put(valueSet2.name, valueSet2);
      // Instance: MyQuestionnaire
      // InstanceOf: Questionnaire
      Instance questionnaire = new Instance("MyQuestionnaire");
      questionnaire.instanceOf = "Questionnaire";
      // * status = #active
      AssignmentRule statusRule = new AssignmentRule("status");
      statusRule.value = new FshCode("active");
      // * contained = ContainedValueSet
      AssignmentRule containedRule = new AssignmentRule("contained");
      containedRule.value = "ContainedValueSet";
      containedRule.isInstance = true;
      // * contained[1] = SecondContainedValueSet
      AssignmentRule containedRule2 = new AssignmentRule("contained[1]");
      containedRule2.value = "SecondContainedValueSet";
      containedRule2.isInstance = true;
      // * item[0].linkId = "abc"
      AssignmentRule itemLinkId = new AssignmentRule("item[0].linkId");
      itemLinkId.value = "abc";
      // * item[=].type = #choice
      AssignmentRule itemType = new AssignmentRule("item[=].type");
      itemType.value = new FshCode("choice");
      // * item[=].answerValueSet = Canonical(ContainedValueSet)
      AssignmentRule itemAnswerValueSet = new AssignmentRule("item[=].answerValueSet");
      itemAnswerValueSet.value = new FshCanonical("ContainedValueSet");
      // * item[+].linkId = "xyz"
      AssignmentRule itemLinkId2 = new AssignmentRule("item[+].linkId");
      itemLinkId2.value = "xyz";
      // * item[=].type = #choice
      AssignmentRule itemType2 = new AssignmentRule("item[=].type");
      itemType2.value = new FshCode("choice");
      // * item[=].answerValueSet = Canonical(SecondContainedValueSet)
      AssignmentRule itemAnswerValueSet2 = new AssignmentRule("item[=].answerValueSet");
      itemAnswerValueSet2.value = new FshCanonical("SecondContainedValueSet");
      questionnaire.rules.addAll(Arrays.asList(statusRule, containedRule, containedRule2, itemLinkId, itemType, itemAnswerValueSet, itemLinkId2, itemType2, itemAnswerValueSet2));
      doc.instances.put(questionnaire.name, questionnaire);
      InstanceDefinition exported = exportInstance.apply(questionnaire);
      assertEquals(2, len(at(exported, "contained")));
      assertJsonEqual("\"contained-value-set\"", at(exported, "contained[0].id"));
      assertJsonEqual("\"second-contained-value-set\"", at(exported, "contained[1].id"));
      assertEquals(2, len(at(exported, "item")));
      assertJsonEqual("\"#contained-value-set\"", at(exported, "item[0].answerValueSet"));
      assertJsonEqual("\"#second-contained-value-set\"", at(exported, "item[1].answerValueSet"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a Canonical as a #id fragment when referring to a contained resource created directly on the instance")
    void shouldAssignACanonicalAsAIdFragmentWhenReferringToAContainedResourceCreatedDirec() {
      // Instance: MyQuestionnaire
      // InstanceOf: Questionnaire
      Instance questionnaire = new Instance("MyQuestionnaire");
      questionnaire.instanceOf = "Questionnaire";
      // * status = #active
      AssignmentRule statusRule = new AssignmentRule("status");
      statusRule.value = new FshCode("active");
      // * contained[+].resourceType = "ValueSet"
      AssignmentRule containedResourceType = new AssignmentRule("contained[+].resourceType");
      containedResourceType.value = "ValueSet";
      // * contained[=].id = "directly-contained-value-set"
      AssignmentRule containedId = new AssignmentRule("contained[=].id");
      containedId.value = "directly-contained-value-set";
      // * contained[=].name = "DirectlyContainedValueSet"
      AssignmentRule containedName = new AssignmentRule("contained[=].name");
      containedName.value = "DirectlyContainedValueSet";
      // * contained[=].url = "http://hl7.org/fhir/us/minimal/ValueSet/DirectlyContainedValueSet"
      AssignmentRule containedUrl = new AssignmentRule("contained[=].url");
      containedUrl.value = "http://hl7.org/fhir/us/minimal/ValueSet/DirectlyContainedValueSet";
      // * contained[+].resourceType = "ValueSet"
      AssignmentRule containedResourceType2 = new AssignmentRule("contained[+].resourceType");
      containedResourceType2.value = "ValueSet";
      // * contained[=].id = "directly-contained-value-set-2"
      AssignmentRule containedId2 = new AssignmentRule("contained[=].id");
      containedId2.value = "directly-contained-value-set-2";
      // * contained[=].name = "DirectlyContainedValueSet2"
      AssignmentRule containedName2 = new AssignmentRule("contained[=].name");
      containedName2.value = "DirectlyContainedValueSet2";
      // * contained[=].url = "http://hl7.org/fhir/us/minimal/ValueSet/DirectlyContainedValueSet2"
      AssignmentRule containedUrl2 = new AssignmentRule("contained[=].url");
      containedUrl2.value = "http://hl7.org/fhir/us/minimal/ValueSet/DirectlyContainedValueSet2";
      // * item[0].linkId = "abc"
      AssignmentRule itemLinkId = new AssignmentRule("item[0].linkId");
      itemLinkId.value = "abc";
      // * item[=].type = #choice
      AssignmentRule itemType = new AssignmentRule("item[=].type");
      itemType.value = new FshCode("choice");
      // * item[=].answerValueSet = Canonical(DirectlyContainedValueSet2)
      AssignmentRule itemAnswerValueSet = new AssignmentRule("item[=].answerValueSet");
      itemAnswerValueSet.value = new FshCanonical("DirectlyContainedValueSet2");
      questionnaire.rules.addAll(Arrays.asList(statusRule, containedResourceType, containedId, containedName, containedUrl, containedResourceType2, containedId2, containedName2, containedUrl2, itemLinkId, itemType, itemAnswerValueSet));
      doc.instances.put(questionnaire.name, questionnaire);
      InstanceDefinition exported = exportInstance.apply(questionnaire);
      assertEquals(2, len(at(exported, "contained")));
      assertJsonEqual("\"directly-contained-value-set\"", at(exported, "contained[0].id"));
      assertJsonEqual("\"directly-contained-value-set-2\"", at(exported, "contained[1].id"));
      assertEquals(1, len(at(exported, "item")));
      assertJsonEqual("\"#directly-contained-value-set-2\"", at(exported, "item[0].answerValueSet"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a Canonical as a #id fragment when referring to a contained resource that was added by slice name, slice name with index, and double digit indices")
    void shouldAssignACanonicalAsAIdFragmentWhenReferringToAContainedResourceThatWasAdded() {
      // Profile: QuestionnaireProfile
      // Parent:  Questionnaire
      // * contained ^slicing.discriminator.type = #pattern
      // * contained ^slicing.discriminator.path = "$this"
      // * contained ^slicing.rules = #open
      // * contained contains myvs 0..*
      Profile questionnaireProfile = new Profile("QuestionnaireProfile");
      questionnaireProfile.parent = "Questionnaire";
      CaretValueRule slicingType = new CaretValueRule("contained");
      slicingType.caretPath = "slicing.discriminator.type";
      slicingType.value = new FshCode("pattern");
      CaretValueRule slicingPath = new CaretValueRule("contained");
      slicingPath.caretPath = "slicing.discriminator.path";
      slicingPath.value = "this";
      CaretValueRule slicingRules = new CaretValueRule("contained");
      slicingRules.caretPath = "slicing.rules";
      slicingRules.value = new FshCode("open");
      ContainsRule containedContains = new ContainsRule("contained");
      containedContains.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "myvs"
        }
        """)));
      CardRule myvsCard = new CardRule("contained[myvs]");
      myvsCard.min = 0.0;
      myvsCard.max = "*";
      questionnaireProfile.rules.addAll(Arrays.asList(slicingType, slicingPath, slicingRules, containedContains, myvsCard));
      doc.profiles.put(questionnaireProfile.name, questionnaireProfile);
      // ValueSet: ContainedValueSet
      // Id: contained-value-set
      FshValueSet valueSet = new FshValueSet("ContainedValueSet");
      valueSet.setId("contained-value-set");
      doc.valueSets.put(valueSet.name, valueSet);
      // ValueSet: SecondContainedValueSet
      // Id: second-contained-value-set
      FshValueSet valueSet2 = new FshValueSet("SecondContainedValueSet");
      valueSet2.setId("second-contained-value-set");
      doc.valueSets.put(valueSet2.name, valueSet2);
      // ValueSet: ThirdContainedValueSet
      // Id: third-contained-value-set
      FshValueSet valueSet3 = new FshValueSet("ThirdContainedValueSet");
      valueSet3.setId("third-contained-value-set");
      doc.valueSets.put(valueSet3.name, valueSet3);
      // Instance: MyQuestionnaire
      // InstanceOf: QuestionnaireProfile
      Instance questionnaire = new Instance("MyQuestionnaire");
      questionnaire.instanceOf = "QuestionnaireProfile";
      // * status = #active
      AssignmentRule statusRule = new AssignmentRule("status");
      statusRule.value = new FshCode("active");
      // * contained[myvs] = ContainedValueSet
      AssignmentRule containedRule = new AssignmentRule("contained[myvs]");
      containedRule.value = "ContainedValueSet";
      containedRule.isInstance = true;
      // * contained[myvs][1] = SecondContainedValueSet
      AssignmentRule containedRule2 = new AssignmentRule("contained[myvs][1]");
      containedRule2.value = "SecondContainedValueSet";
      containedRule2.isInstance = true;
      // * contained[10] = ThirdContainedValueSet
      AssignmentRule containedRule3 = new AssignmentRule("contained[10]");
      containedRule3.value = "ThirdContainedValueSet";
      containedRule3.isInstance = true;
      // * item[0].linkId = "abc"
      AssignmentRule itemLinkId = new AssignmentRule("item[0].linkId");
      itemLinkId.value = "abc";
      // * item[=].type = #choice
      AssignmentRule itemType = new AssignmentRule("item[=].type");
      itemType.value = new FshCode("choice");
      // * item[=].answerValueSet = Canonical(ContainedValueSet)
      AssignmentRule itemAnswerValueSet = new AssignmentRule("item[=].answerValueSet");
      itemAnswerValueSet.value = new FshCanonical("ContainedValueSet");
      // * item[+].linkId = "xyz"
      AssignmentRule itemLinkId2 = new AssignmentRule("item[+].linkId");
      itemLinkId2.value = "xyz";
      // * item[=].type = #choice
      AssignmentRule itemType2 = new AssignmentRule("item[=].type");
      itemType2.value = new FshCode("choice");
      // * item[=].answerValueSet = Canonical(SecondContainedValueSet)
      AssignmentRule itemAnswerValueSet2 = new AssignmentRule("item[=].answerValueSet");
      itemAnswerValueSet2.value = new FshCanonical("SecondContainedValueSet");
      // * item[+].linkId = "mno"
      AssignmentRule itemLinkId3 = new AssignmentRule("item[+].linkId");
      itemLinkId3.value = "mno";
      // * item[=].type = #choice
      AssignmentRule itemType3 = new AssignmentRule("item[=].type");
      itemType3.value = new FshCode("choice");
      // * item[=].answerValueSet = Canonical(ThirdContainedValueSet)
      AssignmentRule itemAnswerValueSet3 = new AssignmentRule("item[=].answerValueSet");
      itemAnswerValueSet3.value = new FshCanonical("ThirdContainedValueSet");
      questionnaire.rules.addAll(Arrays.asList(statusRule, containedRule, containedRule2, containedRule3, itemLinkId, itemType, itemAnswerValueSet, itemLinkId2, itemType2, itemAnswerValueSet2, itemLinkId3, itemType3, itemAnswerValueSet3));
      doc.instances.put(questionnaire.name, questionnaire);
      InstanceDefinition exported = exportInstance.apply(questionnaire);
      assertEquals(11, len(at(exported, "contained")));
      assertJsonEqual("\"contained-value-set\"", at(exported, "contained[0].id"));
      assertJsonEqual("\"second-contained-value-set\"", at(exported, "contained[1].id"));
      assertNull(at(exported, "contained[2]"));
      assertJsonEqual("\"third-contained-value-set\"", at(exported, "contained[10].id"));
      assertEquals(3, len(at(exported, "item")));
      assertJsonEqual("\"#contained-value-set\"", at(exported, "item[0].answerValueSet"));
      assertJsonEqual("\"#second-contained-value-set\"", at(exported, "item[1].answerValueSet"));
      assertJsonEqual("\"#third-contained-value-set\"", at(exported, "item[2].answerValueSet"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a Canonical as a full url (not #id) when referring to a resource that is not directly on the contained array")
    void shouldAssignACanonicalAsAFullUrlNotIdWhenReferringToAResourceThatIsNotDirectlyOn() {
      // ValueSet: ContainedValueSet
      // Id: contained-value-set
      FshValueSet valueSet = new FshValueSet("ContainedValueSet");
      valueSet.setId("contained-value-set");
      doc.valueSets.put(valueSet.name, valueSet);
      // Instance: MyQuestionnaire
      // InstanceOf: Questionnaire
      Instance questionnaire = new Instance("MyQuestionnaire");
      questionnaire.instanceOf = "Questionnaire";
      // * status = #active
      AssignmentRule statusRule = new AssignmentRule("status");
      statusRule.value = new FshCode("active");
      // * contained[0].resourceType = "Bundle"
      AssignmentRule containedResourceTypeRule = new AssignmentRule("contained[0].resourceType");
      containedResourceTypeRule.value = "Bundle";
      // * contained[0].entry[0].resource = ContainedValueSet
      AssignmentRule containedEntryResourceRule = new AssignmentRule("contained[0].entry[0].resource");
      containedEntryResourceRule.value = "ContainedValueSet";
      containedEntryResourceRule.isInstance = true;
      // * item[0].linkId = "abc"
      AssignmentRule itemLinkId = new AssignmentRule("item[0].linkId");
      itemLinkId.value = "abc";
      // * item[=].type = #choice
      AssignmentRule itemType = new AssignmentRule("item[=].type");
      itemType.value = new FshCode("choice");
      // * item[=].answerValueSet = Canonical(ContainedValueSet)
      AssignmentRule itemAnswerValueSet = new AssignmentRule("item[=].answerValueSet");
      itemAnswerValueSet.value = new FshCanonical("ContainedValueSet");
      questionnaire.rules.addAll(Arrays.asList(statusRule, containedResourceTypeRule, containedEntryResourceRule, itemLinkId, itemType, itemAnswerValueSet));
      doc.instances.put(questionnaire.name, questionnaire);
      InstanceDefinition exported = exportInstance.apply(questionnaire);
      assertEquals(1, len(at(exported, "contained")));
      assertJsonEqual("\"contained-value-set\"", at(exported, "contained[0].entry[0].resource.id"));
      assertEquals(1, len(at(exported, "item")));
      assertJsonEqual("\"http://hl7.org/fhir/us/minimal/ValueSet/contained-value-set\"", at(exported, "item[0].answerValueSet"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when an invalid canonical is assigned")
    void shouldLogAnErrorWhenAnInvalidCanonicalIsAssigned() {
      AssignmentRule assignedRefRule = new AssignmentRule("instantiatesCanonical");
      Instance vsInstance = new Instance("TestVS");
      vsInstance.instanceOf = "ValueSet";
      doc.instances.put(vsInstance.name, vsInstance);
      assignedRefRule.value = new FshCanonical("TestVS");
      // * instantiatesCanonical = Canonical(TestVS)
      carePlanInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(carePlanInstance);
      assertJsonEqual("null", at(exported, "instantiatesCanonical"));
      assertMatches("The type \"Canonical\\(ValueSet\\)\" does not match any of the allowed types\\D*", "s", loggerSpy.getFirstMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when an already exported invalid canonical is assigned")
    void shouldLogAnErrorWhenAnAlreadyExportedInvalidCanonicalIsAssigned() {
      AssignmentRule assignedRefRule = new AssignmentRule("instantiatesCanonical");
      Instance vsInstance = new Instance("TestVS");
      vsInstance.instanceOf = "ValueSet";
      doc.instances.put(vsInstance.name, vsInstance);
      // First export the VS so that it is fished from  the package instead of FSHTank
      exportInstance.apply(vsInstance);
      assignedRefRule.value = new FshCanonical("TestVS");
      // * instantiatesCanonical = Canonical(TestVS)
      carePlanInstance.rules.add(assignedRefRule);
      InstanceDefinition exported = exportInstance.apply(carePlanInstance);
      assertJsonEqual("null", at(exported, "instantiatesCanonical"));
      assertMatches("The type \"Canonical\\(ValueSet\\)\" does not match any of the allowed types\\D*", "s", loggerSpy.getMessageAtIndex(1, "error"));
    }

    // Assigning codes from local systems
    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a code to a top level element while replacing the local code system name with its url")
    void shouldAssignACodeToATopLevelElementWhileReplacingTheLocalCodeSystemNameWithItsUr() {
      Instance brightInstance = new Instance("BrightObservation");
      brightInstance.instanceOf = "Observation";
      AssignmentRule assignedCodeRule = new AssignmentRule("code");
      assignedCodeRule.value = new FshCode("bright", "Visible");
      brightInstance.rules.add(assignedCodeRule);
      doc.instances.put(brightInstance.name, brightInstance);
      FshCodeSystem visibleSystem = new FshCodeSystem("Visible");
      doc.codeSystems.put(visibleSystem.name, visibleSystem);
      InstanceDefinition exported = exportInstance.apply(brightInstance);
      assertJsonEqual("""
        [
          {
            "code": "bright",
            "system": "http://hl7.org/fhir/us/minimal/CodeSystem/Visible"
          }
        ]
        """, at(exported, "code.coding"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a code with a version to a top level element while replacing the local code system name with its url and use the specified version")
    void shouldAssignACodeWithAVersionToATopLevelElementWhileReplacingTheLocalCodeSystemN() {
      Instance brightInstance = new Instance("BrightObservation");
      brightInstance.instanceOf = "Observation";
      AssignmentRule assignedCodeRule = new AssignmentRule("code");
      assignedCodeRule.value = new FshCode("bright", "Visible|1.0.0|a");
      brightInstance.rules.add(assignedCodeRule);
      doc.instances.put(brightInstance.name, brightInstance);
      FshCodeSystem visibleSystem = new FshCodeSystem("Visible");
      doc.codeSystems.put(visibleSystem.name, visibleSystem);
      InstanceDefinition exported = exportInstance.apply(brightInstance);
      assertJsonEqual("""
        [
          {
            "code": "bright",
            "version": "1.0.0|a",
            "system": "http://hl7.org/fhir/us/minimal/CodeSystem/Visible"
          }
        ]
        """, at(exported, "code.coding"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a code with a version to a top level element while replacing the code system name with its url when the correct version is found")
    void shouldAssignACodeWithAVersionToATopLevelElementWhileReplacingTheCodeSystemNameWi() {
      Instance brightInstance = new Instance("BrightObservation");
      brightInstance.instanceOf = "Observation";
      AssignmentRule assignedCodeRule = new AssignmentRule("code");
      assignedCodeRule.value = new FshCode("bright", "Visible|1.0.0");
      brightInstance.rules.add(assignedCodeRule);
      doc.instances.put(brightInstance.name, brightInstance);
      FshCodeSystem visibleSystem = new FshCodeSystem("Visible");
      CaretValueRule visibleSystemUrl = new CaretValueRule("");
      visibleSystemUrl.caretPath = "url";
      visibleSystemUrl.value = "http://hl7.org/fhir/us/minimal/CodeSystem/Visible";
      CaretValueRule visibleSystemVersion = new CaretValueRule("");
      visibleSystemVersion.caretPath = "version";
      visibleSystemVersion.value = "1.0.0";
      visibleSystem.rules.addAll(Arrays.asList(visibleSystemUrl, visibleSystemVersion));
      doc.codeSystems.put(visibleSystem.name, visibleSystem);
      InstanceDefinition exported = exportInstance.apply(brightInstance);
      assertJsonEqual("""
        [
          {
            "code": "bright",
            "version": "1.0.0",
            "system": "http://hl7.org/fhir/us/minimal/CodeSystem/Visible"
          }
        ]
        """, at(exported, "code.coding"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a code with a version while replacing the code system name with its url regardless of the specified version")
    void shouldAssignACodeWithAVersionWhileReplacingTheCodeSystemNameWithItsUrlRegardless() {
      // the version on a CodeSystem resource is not the same as the system's actual version out in the world.
      // so, they don't need to match.
      Instance brightInstance = new Instance("BrightObservation");
      brightInstance.instanceOf = "Observation";
      AssignmentRule assignedCodeRule = new AssignmentRule("code");
      assignedCodeRule.value = new FshCode("bright", "Visible|1.2.3");
      brightInstance.rules.add(assignedCodeRule);
      doc.instances.put(brightInstance.name, brightInstance);
      FshCodeSystem visibleSystem = new FshCodeSystem("Visible");
      CaretValueRule visibleSystemUrl = new CaretValueRule("");
      visibleSystemUrl.caretPath = "url";
      visibleSystemUrl.value = "http://hl7.org/fhir/us/minimal/CodeSystem/Visible";
      CaretValueRule visibleSystemVersion = new CaretValueRule("");
      visibleSystemVersion.caretPath = "version";
      visibleSystemVersion.value = "1.0.0";
      visibleSystem.rules.addAll(Arrays.asList(visibleSystemUrl, visibleSystemVersion));
      doc.codeSystems.put(visibleSystem.name, visibleSystem);
      InstanceDefinition exported = exportInstance.apply(brightInstance);
      assertJsonEqual("""
        [
          {
            "code": "bright",
            "version": "1.2.3",
            "system": "http://hl7.org/fhir/us/minimal/CodeSystem/Visible"
          }
        ]
        """, at(exported, "code.coding"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a code to a top level element if the code system was defined as an instance of usage definition")
    void shouldAssignACodeToATopLevelElementIfTheCodeSystemWasDefinedAsAnInstanceOfUsageD() {
      Instance visibleSystem = new Instance("Visible");
      visibleSystem.instanceOf = "CodeSystem";
      visibleSystem.usage = InstanceUsage.Definition;
      AssignmentRule urlRule = new AssignmentRule("url");
      urlRule.value = "http://hl7.org/fhir/us/minimal/Instance/Visible";
      AssignmentRule nameRule = new AssignmentRule("name");
      nameRule.value = "Visible";
      visibleSystem.rules.addAll(Arrays.asList(urlRule, nameRule));
      doc.instances.put(visibleSystem.name, visibleSystem);
      exportInstance.apply(visibleSystem);
      Instance brightInstance = new Instance("BrightObservation");
      brightInstance.instanceOf = "Observation";
      AssignmentRule assignedCodeRule = new AssignmentRule("code");
      assignedCodeRule.value = new FshCode("bright", "Visible");
      brightInstance.rules.add(assignedCodeRule);
      doc.instances.put(brightInstance.name, brightInstance);
      InstanceDefinition exported = exportInstance.apply(brightInstance);
      assertJsonEqual("""
        [
          {
            "code": "bright",
            "system": "http://hl7.org/fhir/us/minimal/Instance/Visible"
          }
        ]
        """, at(exported, "code.coding"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not assign a code to a top level element if the system references an instance that is not a CodeSystem")
    void shouldNotAssignACodeToATopLevelElementIfTheSystemReferencesAnInstanceThatIsNotAC() {
      Instance invalidSystem = new Instance("NonSystem");
      invalidSystem.instanceOf = "CapabilityStatement";
      AssignmentRule urlRule = new AssignmentRule("url");
      urlRule.value = "http://hl7.org/fhir/us/minimal/Instance/NonSystem";
      invalidSystem.rules.add(urlRule);
      doc.instances.put(invalidSystem.name, invalidSystem);
      exportInstance.apply(invalidSystem);
      Instance brightInstance = new Instance("BrightObservation");
      brightInstance.instanceOf = "Observation";
      AssignmentRule assignedCodeRule = new AssignmentRule("code");
      assignedCodeRule.value = new FshCode("bright", "NonSystem");
      brightInstance.rules.add(assignedCodeRule);
      doc.instances.put(brightInstance.name, brightInstance);
      InstanceDefinition exported = exportInstance.apply(brightInstance);
      assertContains("Resolved value \"NonSystem\" is not a valid URI.", loggerSpy.getAllMessages("error"));
      assertNull(at(exported, "code"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not assign a code to a top level element if the code system was defined as an instance of a non-definition usage")
    void shouldNotAssignACodeToATopLevelElementIfTheCodeSystemWasDefinedAsAnInstanceOfANo() {
      Instance invalidSystem = new Instance("NonDefinition");
      invalidSystem.instanceOf = "CodeSystem";
      AssignmentRule urlRule = new AssignmentRule("url");
      urlRule.value = "http://hl7.org/fhir/us/minimal/Instance/NonDefinition";
      AssignmentRule nameRule = new AssignmentRule("name");
      nameRule.value = "Visible";
      invalidSystem.rules.addAll(Arrays.asList(urlRule, nameRule));
      doc.instances.put(invalidSystem.name, invalidSystem);
      exportInstance.apply(invalidSystem);
      Instance brightInstance = new Instance("BrightObservation");
      brightInstance.instanceOf = "Observation";
      AssignmentRule assignedCodeRule = new AssignmentRule("code");
      assignedCodeRule.value = new FshCode("bright", "NonDefinition");
      brightInstance.rules.add(assignedCodeRule);
      doc.instances.put(brightInstance.name, brightInstance);
      InstanceDefinition exported = exportInstance.apply(brightInstance);
      assertContains("Resolved value \"NonDefinition\" is not a valid URI.", loggerSpy.getAllMessages("error"));
      assertNull(at(exported, "code"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a code to a nested element while replacing the local code system name with its url")
    void shouldAssignACodeToANestedElementWhileReplacingTheLocalCodeSystemNameWithItsUrl() {
      Instance brightInstance = new Instance("BrightObservation");
      brightInstance.instanceOf = "Observation";
      AssignmentRule assignedCodeRule = new AssignmentRule("component[0].code");
      assignedCodeRule.value = new FshCode("bright", "Visible");
      brightInstance.rules.add(assignedCodeRule);
      doc.instances.put(brightInstance.name, brightInstance);
      FshCodeSystem visibleSystem = new FshCodeSystem("Visible");
      doc.codeSystems.put(visibleSystem.name, visibleSystem);
      InstanceDefinition exported = exportInstance.apply(brightInstance);
      assertJsonEqual("""
        [
          {
            "code": "bright",
            "system": "http://hl7.org/fhir/us/minimal/CodeSystem/Visible"
          }
        ]
        """, at(exported, "component[0].code.coding"));
    }

    // Assigning codes from systems in the fisher.fhir (core fhir package or dependencies)
    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a code from a CodeSystem in the fisher by id")
    void shouldAssignACodeFromACodeSystemInTheFisherById() {
      // allergyintolerance-clinical is the id of a CodeSystem in the R4 definitions
      Instance observation = new Instance("MyObservation");
      observation.instanceOf = "Observation";
      AssignmentRule statusRule = new AssignmentRule("status");
      statusRule.value = new FshCode("active");
      AssignmentRule assignedCodeRule = new AssignmentRule("code");
      assignedCodeRule.value = new FshCode("test-code", "allergyintolerance-clinical");
      observation.rules.addAll(Arrays.asList(assignedCodeRule, statusRule));
      doc.instances.put(observation.name, observation);
      InstanceDefinition exported = exportInstance.apply(observation);
      assertJsonEqual("""
        {
          "coding": [
            {
              "code": "test-code",
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical"
            }
          ]
        }
        """, at(exported, "code"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a code from a CodeSystem in the fisher by name")
    void shouldAssignACodeFromACodeSystemInTheFisherByName() {
      // AllergyIntoleranceClinicalStatusCodes is the name of a CodeSystem in the R4 definitions
      Instance observation = new Instance("MyObservation");
      observation.instanceOf = "Observation";
      AssignmentRule statusRule = new AssignmentRule("status");
      statusRule.value = new FshCode("active");
      AssignmentRule assignedCodeRule = new AssignmentRule("code");
      assignedCodeRule.value = new FshCode("test-code", "AllergyIntoleranceClinicalStatusCodes");
      observation.rules.addAll(Arrays.asList(assignedCodeRule, statusRule));
      doc.instances.put(observation.name, observation);
      InstanceDefinition exported = exportInstance.apply(observation);
      assertJsonEqual("""
        {
          "coding": [
            {
              "code": "test-code",
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical"
            }
          ]
        }
        """, at(exported, "code"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a code from a CodeSystem in the fisher by url")
    void shouldAssignACodeFromACodeSystemInTheFisherByUrl() {
      // http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical is the url of a CodeSystem in the R4 definitions
      Instance observation = new Instance("MyObservation");
      observation.instanceOf = "Observation";
      AssignmentRule statusRule = new AssignmentRule("status");
      statusRule.value = new FshCode("active");
      AssignmentRule assignedCodeRule = new AssignmentRule("code");
      assignedCodeRule.value = new FshCode("test-code", "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical");
      observation.rules.addAll(Arrays.asList(assignedCodeRule, statusRule));
      doc.instances.put(observation.name, observation);
      InstanceDefinition exported = exportInstance.apply(observation);
      assertJsonEqual("""
        {
          "coding": [
            {
              "code": "test-code",
              "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical"
            }
          ]
        }
        """, at(exported, "code"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    // Assigning Quantities with value 0 (e.g., Age)
    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a Quantity with value 0 (and not drop the 0)")
    void shouldAssignAQuantityWithValue0AndNotDropThe0() {
      Instance observationInstance = new Instance("ZeroValueObservation");
      observationInstance.instanceOf = "Observation";
      AssignmentRule assignedValueQuantityRule = new AssignmentRule("valueQuantity");
      assignedValueQuantityRule.value = new FshQuantity(0.0, new FshCode("mm", "http://unitsofmeasure.org", "mm"));
      observationInstance.rules.add(assignedValueQuantityRule);
      doc.instances.put(observationInstance.name, observationInstance);
      InstanceDefinition exported = exportInstance.apply(observationInstance);
      assertJsonEqual("""
        {
          "value": 0,
          "code": "mm",
          "system": "http://unitsofmeasure.org",
          "unit": "mm"
        }
        """, at(exported, "valueQuantity"));
    }

    // Assigning Quantities to Quantity specializations (e.g., Age)
    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a Quantity to a Quantity specialization")
    void shouldAssignAQuantityToAQuantitySpecialization() {
      Instance conditionInstance = new Instance("SomeCondition");
      conditionInstance.instanceOf = "Condition";
      AssignmentRule assignedAgeRule = new AssignmentRule("onsetAge");
      assignedAgeRule.value = new FshQuantity(42.0, new FshCode("a", "http://unitsofmeasure.org", "years"));
      conditionInstance.rules.add(assignedAgeRule);
      doc.instances.put(conditionInstance.name, conditionInstance);
      InstanceDefinition exported = exportInstance.apply(conditionInstance);
      assertJsonEqual("""
        {
          "value": 42,
          "code": "a",
          "system": "http://unitsofmeasure.org",
          "unit": "years"
        }
        """, at(exported, "onsetAge"));
    }

    // Sliced elements
    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a single sliced element to a value")
    void shouldAssignASingleSlicedElementToAValue() {
      AssignmentRule assignedValRule = new AssignmentRule("extension[level].valueCoding.system");
      assignedValRule.value = "foo";
      patientProfInstance.rules.add(assignedValRule);
      InstanceDefinition exported = exportInstance.apply(patientProfInstance);
      assertJsonEqual("""
        [
          {
            "url": "level",
            "valueCoding": {
              "system": "foo"
            }
          }
        ]
        """, at(exported, "extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a single primitive sliced element to a value")
    void shouldAssignASinglePrimitiveSlicedElementToAValue() {
      CaretValueRule caretRule = new CaretValueRule("name.prefix");
      caretRule.caretPath = "slicing.discriminator.type";
      caretRule.value = new FshCode("value");
      ContainsRule containsRule = new ContainsRule("name.prefix");
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Dr"
        }
        """))));
      CardRule cardRule = new CardRule("name.prefix");
      cardRule.min = 1.0;
      cardRule.max = "1";
      // * name.prefix ^slicing.discriminator.type = #value
      // * name.prefix contains Dr 1..1
      patient.rules.addAll(Arrays.asList(caretRule, containsRule, cardRule));
      AssignmentRule assignedRule = new AssignmentRule("name[0].prefix[Dr]");
      assignedRule.value = "Doctor";
      // * name[0].prefix[Dr] = "Doctor"
      patientInstance.rules.add(assignedRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "prefix": [
              "Doctor"
            ]
          }
        ]
        """, at(exported, "name"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign sliced elements in an array that are assigned in order")
    void shouldAssignSlicedElementsInAnArrayThatAreAssignedInOrder() {
      AssignmentRule fooRule = new AssignmentRule("extension[type][0].valueCoding.system");
      fooRule.value = "foo";
      patientProfInstance.rules.add(fooRule);
      AssignmentRule barRule = new AssignmentRule("extension[type][1].valueCoding.system");
      barRule.value = "bar";
      patientProfInstance.rules.add(barRule);
      InstanceDefinition exported = exportInstance.apply(patientProfInstance);
      assertJsonEqual("""
        [
          {
            "url": "type",
            "valueCoding": {
              "system": "foo"
            }
          },
          {
            "url": "type",
            "valueCoding": {
              "system": "bar"
            }
          }
        ]
        """, at(exported, "extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a sliced primitive array")
    void shouldAssignASlicedPrimitiveArray() {
      CaretValueRule caretRule = new CaretValueRule("name.prefix");
      caretRule.caretPath = "slicing.discriminator.type";
      caretRule.value = new FshCode("value");
      ContainsRule containsRule = new ContainsRule("name.prefix");
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Dr"
        }
        """))));
      CardRule cardRule = new CardRule("name.prefix");
      cardRule.min = 0.0;
      cardRule.max = "*";
      // * name.prefix ^slicing.discriminator.type = #value
      // * name.prefix contains Dr 0..*
      patient.rules.addAll(Arrays.asList(caretRule, containsRule, cardRule));
      AssignmentRule assignedRule1 = new AssignmentRule("name[0].prefix[Dr][0]");
      assignedRule1.value = "Doctor";
      AssignmentRule assignedRule2 = new AssignmentRule("name[0].prefix[Dr][1]");
      assignedRule2.value = "Mister Doctor";
      // * name[0].prefix[Dr][0] = "Doctor"
      // * name[0].prefix[Dr][1] = "Mister Doctor"
      patientInstance.rules.addAll(Arrays.asList(assignedRule1, assignedRule2));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "prefix": [
              "Doctor",
              "Mister Doctor"
            ]
          }
        ]
        """, at(exported, "name"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a sliced element in an array that is assigned by multiple rules")
    void shouldAssignASlicedElementInAnArrayThatIsAssignedByMultipleRules() {
      AssignmentRule fooRule = new AssignmentRule("extension[type][1].valueCoding.system");
      fooRule.value = "foo";
      patientProfInstance.rules.add(fooRule);
      AssignmentRule barRule = new AssignmentRule("extension[type][1].valueCoding.version");
      barRule.value = "1.2.3";
      patientProfInstance.rules.add(barRule);
      InstanceDefinition exported = exportInstance.apply(patientProfInstance);
      assertJsonEqual("""
        [
          {
            "url": "type"
          },
          {
            "url": "type",
            "valueCoding": {
              "system": "foo",
              "version": "1.2.3"
            }
          }
        ]
        """, at(exported, "extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign sliced elements in an array that are assigned out of order")
    void shouldAssignSlicedElementsInAnArrayThatAreAssignedOutOfOrder() {
      AssignmentRule fooRule = new AssignmentRule("extension[type][1].valueCoding.system");
      fooRule.value = "foo";
      patientProfInstance.rules.add(fooRule);
      AssignmentRule barRule = new AssignmentRule("extension[type][0].valueCoding.system");
      barRule.value = "bar";
      patientProfInstance.rules.add(barRule);
      InstanceDefinition exported = exportInstance.apply(patientProfInstance);
      assertJsonEqual("""
        [
          {
            "url": "type",
            "valueCoding": {
              "system": "bar"
            }
          },
          {
            "url": "type",
            "valueCoding": {
              "system": "foo"
            }
          }
        ]
        """, at(exported, "extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign sliced elements in an array and fill empty values")
    void shouldAssignSlicedElementsInAnArrayAndFillEmptyValues() {
      AssignmentRule fooRule = new AssignmentRule("extension[type][1].valueCoding.system");
      fooRule.value = "foo";
      patientProfInstance.rules.add(fooRule);
      InstanceDefinition exported = exportInstance.apply(patientProfInstance);
      assertJsonEqual("""
        [
          {
            "url": "type"
          },
          {
            "url": "type",
            "valueCoding": {
              "system": "foo"
            }
          }
        ]
        """, at(exported, "extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign mixed sliced elements in an array out of order")
    void shouldAssignMixedSlicedElementsInAnArrayOutOfOrder() {
      AssignmentRule fooRule = new AssignmentRule("extension[type][1].valueCoding.system");
      fooRule.value = "foo";
      patientProfInstance.rules.add(fooRule);
      AssignmentRule bazRule = new AssignmentRule("extension[level].valueCoding.system");
      bazRule.value = "baz";
      patientProfInstance.rules.add(bazRule);
      AssignmentRule barRule = new AssignmentRule("extension[type][0].valueCoding.system");
      barRule.value = "bar";
      patientProfInstance.rules.add(barRule);
      InstanceDefinition exported = exportInstance.apply(patientProfInstance);
      assertJsonEqual("""
        [
          {
            "url": "type",
            "valueCoding": {
              "system": "bar"
            }
          },
          {
            "url": "type",
            "valueCoding": {
              "system": "foo"
            }
          },
          {
            "url": "level",
            "valueCoding": {
              "system": "baz"
            }
          }
        ]
        """, at(exported, "extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign mixed sliced elements in a deeper array element out of order")
    void shouldAssignMixedSlicedElementsInADeeperArrayElementOutOfOrder() {
      ContainsRule extensionContains = new ContainsRule("extension");
      extensionContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "MoreThings"
        }
        """))));
      ContainsRule moreThingsContains = new ContainsRule("extension[MoreThings].extension");
      moreThingsContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "TheseThings"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "ThoseThings"
        }
        """))));
      patientProf.rules.addAll(Arrays.asList(extensionContains, moreThingsContains));
      AssignmentRule socksRule = new AssignmentRule("extension[MoreThings].extension[ThoseThings][1].valueString");
      socksRule.value = "Socks";
      AssignmentRule shoesRule = new AssignmentRule("extension[MoreThings].extension[TheseThings].valueString");
      shoesRule.value = "Shoes";
      AssignmentRule oatsRule = new AssignmentRule("extension[MoreThings].extension[ThoseThings][0].valueString");
      oatsRule.value = "Oats";
      patientProfInstance.rules.addAll(Arrays.asList(socksRule, shoesRule, oatsRule));
      InstanceDefinition exported = exportInstance.apply(patientProfInstance);
      assertJsonEqual("""
        [
          {
            "url": "MoreThings",
            "extension": [
              {
                "url": "ThoseThings",
                "valueString": "Oats"
              },
              {
                "url": "ThoseThings",
                "valueString": "Socks"
              },
              {
                "url": "TheseThings",
                "valueString": "Shoes"
              }
            ]
          }
        ]
        """, at(exported, "extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should keep slices in usage order after the first used slice, followed by all required slices, when slices have non-required parents")
    void shouldKeepSlicesInUsageOrderAfterTheFirstUsedSliceFollowedByAllRequiredSlicesWhe() {
      // Profile: ManySliceObservation
      // Parent: Observation
      // * referenceRange.appliesTo ^slicing.discriminator.type = #value
      // * referenceRange.appliesTo ^slicing.discriminator.path = "text"
      // * referenceRange.appliesTo ^slicing.rules = #open
      // * referenceRange.appliesTo contains RequiredA 1..1
      //   and RequiredB 1..2
      //   and RequiredC 1..3
      //   and OptionalA 0..1
      //   and OptionalB 0..2
      //   and OptionalC 0..3
      // * referenceRange.appliesTo[RequiredA].text = "Text for Required A"
      // * referenceRange.appliesTo[RequiredB].text = "Text for Required B"
      // * referenceRange.appliesTo[RequiredC].text = "Text for Required C"
      // * referenceRange.appliesTo[OptionalA].text = "Text for Optional A"
      // * referenceRange.appliesTo[OptionalB].text = "Text for Optional B"
      // * referenceRange.appliesTo[OptionalC].text = "Text for Optional C"
      Profile observation = new Profile("ManySliceObservation");
      observation.parent = "Observation";
      CaretValueRule slicingType = new CaretValueRule("referenceRange.appliesTo");
      slicingType.caretPath = "slicing.discriminator.type";
      slicingType.value = new FshCode("value");
      CaretValueRule slicingPath = new CaretValueRule("referenceRange.appliesTo");
      slicingPath.caretPath = "slicing.discriminator.path";
      slicingPath.value = "text";
      CaretValueRule slicingRules = new CaretValueRule("referenceRange.appliesTo");
      slicingRules.caretPath = "slicing.rules";
      slicingRules.value = new FshCode("open");
      ContainsRule appliesToContains = new ContainsRule("referenceRange.appliesTo");
      appliesToContains.items.addAll(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "RequiredA"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "RequiredB"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "RequiredC"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "OptionalA"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "OptionalB"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "OptionalC"
        }
        """))));
      CardRule requiredACard = new CardRule("referenceRange.appliesTo[RequiredA]");
      requiredACard.min = 1.0;
      requiredACard.max = "1";
      CardRule requiredBCard = new CardRule("referenceRange.appliesTo[RequiredB]");
      requiredBCard.min = 1.0;
      requiredBCard.max = "2";
      CardRule requiredCCard = new CardRule("referenceRange.appliesTo[RequiredC]");
      requiredCCard.min = 1.0;
      requiredCCard.max = "3";
      CardRule optionalACard = new CardRule("referenceRange.appliesTo[OptionalA]");
      optionalACard.min = 0.0;
      optionalACard.max = "1";
      CardRule optionalBCard = new CardRule("referenceRange.appliesTo[OptionalB]");
      optionalBCard.min = 0.0;
      optionalBCard.max = "2";
      CardRule optionalCCard = new CardRule("referenceRange.appliesTo[OptionalC]");
      optionalCCard.min = 0.0;
      optionalCCard.max = "3";
      AssignmentRule requiredAText = new AssignmentRule("referenceRange.appliesTo[RequiredA].text");
      requiredAText.value = "Text for Required A";
      AssignmentRule requiredBText = new AssignmentRule("referenceRange.appliesTo[RequiredB].text");
      requiredBText.value = "Text for Required B";
      AssignmentRule requiredCText = new AssignmentRule("referenceRange.appliesTo[RequiredC].text");
      requiredCText.value = "Text for Required C";
      AssignmentRule optionalAText = new AssignmentRule("referenceRange.appliesTo[OptionalA].text");
      optionalAText.value = "Text for Optional A";
      AssignmentRule optionalBText = new AssignmentRule("referenceRange.appliesTo[OptionalB].text");
      optionalBText.value = "Text for Optional B";
      AssignmentRule optionalCText = new AssignmentRule("referenceRange.appliesTo[OptionalC].text");
      optionalCText.value = "Text for Optional C";
      observation.rules.addAll(Arrays.asList(slicingType, slicingPath, slicingRules, appliesToContains, requiredACard, requiredBCard, requiredCCard, optionalACard, optionalBCard, optionalCCard, requiredAText, requiredBText, requiredCText, optionalAText, optionalBText, optionalCText));
      doc.profiles.put(observation.name, observation);
      // Instance: StrangeSliceOrder
      // InstanceOf: ManySliceObservation
      // * status = #final
      // * code = #123
      // * referenceRange.appliesTo[OptionalB].coding = #option-b-0
      // * referenceRange.appliesTo[OptionalA].coding = #option-a
      // * referenceRange.appliesTo[OptionalB][1].coding = #option-b-1
      // * referenceRange.appliesTo[RequiredC][1].coding = #required-c-1
      Instance observationInstance = new Instance("StrangeSliceOrder");
      observationInstance.instanceOf = "ManySliceObservation";
      AssignmentRule statusAssignment = new AssignmentRule("status");
      statusAssignment.value = new FshCode("final");
      AssignmentRule codeAssignment = new AssignmentRule("code");
      codeAssignment.value = new FshCode("123");
      AssignmentRule optionalBCoding = new AssignmentRule("referenceRange.appliesTo[OptionalB].coding");
      optionalBCoding.value = new FshCode("option-b-0");
      AssignmentRule optionalACoding = new AssignmentRule("referenceRange.appliesTo[OptionalA].coding");
      optionalACoding.value = new FshCode("option-a");
      AssignmentRule optionalB1Coding = new AssignmentRule("referenceRange.appliesTo[OptionalB][1].coding");
      optionalB1Coding.value = new FshCode("option-b-1");
      AssignmentRule requiredC1Coding = new AssignmentRule("referenceRange.appliesTo[RequiredC][1].coding");
      requiredC1Coding.value = new FshCode("required-c-1");
      observationInstance.rules.addAll(Arrays.asList(statusAssignment, codeAssignment, optionalBCoding, optionalACoding, optionalB1Coding, requiredC1Coding));
      InstanceDefinition exported = exportInstance.apply(observationInstance);
      assertJsonEqual("""
        [
          {
            "text": "Text for Optional B",
            "coding": [
              {
                "code": "option-b-0"
              }
            ]
          },
          {
            "text": "Text for Required A"
          },
          {
            "text": "Text for Required B"
          },
          {
            "text": "Text for Required C"
          },
          {
            "text": "Text for Optional A",
            "coding": [
              {
                "code": "option-a"
              }
            ]
          },
          {
            "text": "Text for Optional B",
            "coding": [
              {
                "code": "option-b-1"
              }
            ]
          },
          {
            "text": "Text for Required C",
            "coding": [
              {
                "code": "required-c-1"
              }
            ]
          }
        ]
        """, at(exported, "referenceRange[0].appliesTo"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should provide a different warning when an author creates an item matching a slice without using the sliceName in the path when manual slice mode is OFF")
    void shouldProvideADifferentWarningWhenAnAuthorCreatesAnItemMatchingASliceWithoutUsin() {
      // NOTE: Corresponds to https://github.com/FHIR/sushi/issues/1180, but w/ manual slicing off.
      // The warning is different because the indexed rule just overwrites the slice w/ manual slicing off.
      // Profile: LabProfile
      // Parent: Observation
      // * category ^slicing.discriminator.type = #pattern
      // * category ^slicing.discriminator.path = "$this"
      // * category ^slicing.rules = #open
      // * category contains lab 1..1
      // * category[lab] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
      Profile profile = new Profile("LabProfile");
      profile.parent = "Observation";
      CaretValueRule typeRule = new CaretValueRule("category");
      typeRule.caretPath = "slicing.discriminator[0].type";
      typeRule.value = new FshCode("pattern");
      CaretValueRule pathRule = new CaretValueRule("category");
      pathRule.caretPath = "slicing.discriminator[0].path";
      pathRule.value = "$this";
      CaretValueRule rulesRule = new CaretValueRule("category");
      rulesRule.caretPath = "slicing.rules";
      rulesRule.value = new FshCode("open");
      ContainsRule containsRule = new ContainsRule("category");
      containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "lab"
        }
        """)));
      CardRule cardRule = new CardRule("category[lab]");
      cardRule.min = 1.0;
      cardRule.max = "*";
      AssignmentRule assignmentRule = new AssignmentRule("category[lab]");
      assignmentRule.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
      profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, cardRule, assignmentRule));
      doc.profiles.put(profile.name, profile);
      // Instance: LabInstance
      // InstanceOf: LabProfile
      // * category = http://terminology.hl7.org/CodeSystem/observation-category#laboratory "Laboratory"
      // * status = #final
      // * code = http://foo.com#a
      // * valueBoolean = true
      Instance observationInstance = new Instance("LabInstance");
      observationInstance.instanceOf = "LabProfile";
      AssignmentRule categoryAssignment = new AssignmentRule("category");
      categoryAssignment.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category", "Laboratory");
      AssignmentRule statusAssignment = new AssignmentRule("status");
      statusAssignment.value = new FshCode("final");
      AssignmentRule codeAssignment = new AssignmentRule("code");
      codeAssignment.value = new FshCode("a", "http://foo.com");
      AssignmentRule valueAssignment = new AssignmentRule("valueBoolean");
      valueAssignment.value = true;
      observationInstance.rules.addAll(Arrays.asList(categoryAssignment, statusAssignment, codeAssignment, valueAssignment));
      InstanceDefinition exported = exportInstance.apply(observationInstance);
      assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                "display": "Laboratory"
              }
            ]
          }
        ]
        """, at(exported, "category"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertJsonEqual("\"Sliced element Observation.category is being accessed via numeric index. Use slice names in rule paths when possible.\"", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should provide a different warning when an author creates an item exactly matching a slice without using the sliceName in the path when manual slice mode is OFF")
    void shouldProvideADifferentWarningWhenAnAuthorCreatesAnItemExactlyMatchingASliceWith() {
      // NOTE: Corresponds to https://github.com/FHIR/sushi/issues/1180, but w/ manual slicing off.
      // The warning is different because the indexed rule just overwrites the slice w/ manual slicing off.
      // Profile: LabProfile
      // Parent: Observation
      // * category ^slicing.discriminator.type = #pattern
      // * category ^slicing.discriminator.path = "$this"
      // * category ^slicing.rules = #open
      // * category contains lab 1..1
      // * category[lab] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
      Profile profile = new Profile("LabProfile");
      profile.parent = "Observation";
      CaretValueRule typeRule = new CaretValueRule("category");
      typeRule.caretPath = "slicing.discriminator[0].type";
      typeRule.value = new FshCode("pattern");
      CaretValueRule pathRule = new CaretValueRule("category");
      pathRule.caretPath = "slicing.discriminator[0].path";
      pathRule.value = "$this";
      CaretValueRule rulesRule = new CaretValueRule("category");
      rulesRule.caretPath = "slicing.rules";
      rulesRule.value = new FshCode("open");
      ContainsRule containsRule = new ContainsRule("category");
      containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "lab"
        }
        """)));
      CardRule cardRule = new CardRule("category[lab]");
      cardRule.min = 1.0;
      cardRule.max = "*";
      AssignmentRule assignmentRule = new AssignmentRule("category[lab]");
      assignmentRule.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
      profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, cardRule, assignmentRule));
      doc.profiles.put(profile.name, profile);
      // Instance: LabInstance
      // InstanceOf: LabProfile
      // * category = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
      // * status = #final
      // * code = http://foo.com#a
      // * valueBoolean = true
      Instance observationInstance = new Instance("LabInstance");
      observationInstance.instanceOf = "LabProfile";
      AssignmentRule categoryAssignment = new AssignmentRule("category");
      categoryAssignment.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
      AssignmentRule statusAssignment = new AssignmentRule("status");
      statusAssignment.value = new FshCode("final");
      AssignmentRule codeAssignment = new AssignmentRule("code");
      codeAssignment.value = new FshCode("a", "http://foo.com");
      AssignmentRule valueAssignment = new AssignmentRule("valueBoolean");
      valueAssignment.value = true;
      observationInstance.rules.addAll(Arrays.asList(categoryAssignment, statusAssignment, codeAssignment, valueAssignment));
      InstanceDefinition exported = exportInstance.apply(observationInstance);
      assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "laboratory",
                "system": "http://terminology.hl7.org/CodeSystem/observation-category"
              }
            ]
          }
        ]
        """, at(exported, "category"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertJsonEqual("\"Sliced element Observation.category is being accessed via numeric index. Use slice names in rule paths when possible.\"", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a sliced extension element that is referred to by name")
    void shouldAssignASlicedExtensionElementThatIsReferredToByName() {
      Extension fooExtension = new Extension("FooExtension");
      doc.extensions.put(fooExtension.name, fooExtension);
      ContainsRule containsRule = new ContainsRule("extension");
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "foo",
          "type": "FooExtension"
        }
        """))));
      patientProf.rules.add(containsRule);
      AssignmentRule barRule = new AssignmentRule("extension[foo].valueString");
      barRule.value = "bar";
      patientProfInstance.rules.add(barRule);
      InstanceDefinition exported = exportInstance.apply(patientProfInstance);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/FooExtension",
            "valueString": "bar"
          }
        ]
        """, at(exported, "extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a nested sliced extension element that is referred to by name")
    void shouldAssignANestedSlicedExtensionElementThatIsReferredToByName() {
      Extension fooExtension = new Extension("FooExtension");
      doc.extensions.put(fooExtension.name, fooExtension);
      ContainsRule containsRule = new ContainsRule("maritalStatus.extension");
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "foo",
          "type": "FooExtension"
        }
        """))));
      patient.rules.add(containsRule);
      AssignmentRule barRule = new AssignmentRule("maritalStatus.extension[foo].valueString");
      barRule.value = "bar";
      AssignmentRule maritalRule = new AssignmentRule("maritalStatus");
      maritalRule.value = new FshCode("boo");
      patientInstance.rules.addAll(Arrays.asList(maritalRule, barRule));
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        {
          "extension": [
            {
              "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/FooExtension",
              "valueString": "bar"
            }
          ],
          "coding": [
            {
              "code": "boo"
            }
          ]
        }
        """, at(exported, "maritalStatus"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a sliced extension element that is referred to by url")
    void shouldAssignASlicedExtensionElementThatIsReferredToByUrl() {
      Extension fooExtension = new Extension("FooExtension");
      doc.extensions.put(fooExtension.name, fooExtension);
      ContainsRule containsRule = new ContainsRule("extension");
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "foo",
          "type": "FooExtension"
        }
        """))));
      patientProf.rules.add(containsRule);
      AssignmentRule barRule = new AssignmentRule("extension[http://hl7.org/fhir/us/minimal/StructureDefinition/FooExtension].valueString");
      barRule.value = "bar";
      patientProfInstance.rules.add(barRule);
      InstanceDefinition exported = exportInstance.apply(patientProfInstance);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/FooExtension",
            "valueString": "bar"
          }
        ]
        """, at(exported, "extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a required extension slice with a version-pinned type.profile referred to by unversioned url")
    void shouldAssignARequiredExtensionSliceWithAVersionPinnedTypeProfileReferredToByUnve() {
      // Regression: a version-pinned slice type.profile (as produced by hl7.fhir.uv.xver-*
      // cross-version packages, e.g. '...extension-SubscriptionStatus.subscription|0.1.0') must
      // still bind to an extension assigned by its *unversioned* canonical URL, so the
      // required-element validator does not emit a false "occurs 0 time(s)" error.
      Extension fooExtension = new Extension("FooExtension");
      doc.extensions.put(fooExtension.name, fooExtension);
      ContainsRule containsRule = new ContainsRule("extension");
      // the |1.2.3 forces the slice's type[0].profile to be '<FooExtension url>|1.2.3'
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "foo",
          "type": "FooExtension|1.2.3"
        }
        """))));
      CardRule cardRule = new CardRule("extension[foo]");
      cardRule.min = 1.0;
      cardRule.max = "1";
      patientProf.rules.addAll(Arrays.asList(containsRule, cardRule));
      AssignmentRule barRule = new AssignmentRule("extension[http://hl7.org/fhir/us/minimal/StructureDefinition/FooExtension].valueString");
      barRule.value = "bar";
      patientProfInstance.rules.add(barRule);
      InstanceDefinition exported = exportInstance.apply(patientProfInstance);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/FooExtension",
            "valueString": "bar"
          }
        ]
        """, at(exported, "extension"));
      // Scope the assertion to the specific false-positive error only. Do NOT assert zero
      // warnings: the |1.2.3 request logs an expected version-fallback warning from
      // handleExtensionContainsRule's fishForFHIRBestVersion, unrelated to this bug.
      assertEquals(false, some(loggerSpy.getAllMessages("error"), (m) -> jsRegex("minimum cardinality 1 but occurs 0 time\\(s\\)", "").matcher(Js.toStr(((String) m))).find()));
      // Exercise the shared/cached StructureDefinition path: a second instance of the same
      // profile must also bind the slice. Pre-fix, the no-match fallback mutated the shared,
      // cached InstanceOf SD, so order-dependent regressions could slip past a single instance.
      Instance patientProfInstance2 = new Instance("Baz2");
      patientProfInstance2.instanceOf = "TestPatientProf";
      doc.instances.put(patientProfInstance2.name, patientProfInstance2);
      AssignmentRule barRule2 = new AssignmentRule("extension[http://hl7.org/fhir/us/minimal/StructureDefinition/FooExtension].valueString");
      barRule2.value = "bar";
      patientProfInstance2.rules.add(barRule2);
      InstanceDefinition exported2 = exportInstance.apply(patientProfInstance2);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/FooExtension",
            "valueString": "bar"
          }
        ]
        """, at(exported2, "extension"));
      assertEquals(false, some(loggerSpy.getAllMessages("error"), (m) -> jsRegex("minimum cardinality 1 but occurs 0 time\\(s\\)", "").matcher(Js.toStr(((String) m))).find()));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log a false modifier error when a modifierExtension slice name collides with an unrelated extension name")
    void shouldNotLogAFalseModifierErrorWhenAModifierExtensionSliceNameCollidesWithAnUnre() {
      // Regression: the slice's extension must be resolved via its type.profile, not by fishing the
      // bare slice name. A modifierExtension slice literally named "type" collides with R4 core
      // familymemberhistory-type (whose name is "type", a non-modifier). Pre-fix, fishing "type"
      // resolved that decoy and emitted a false "Non-modifier extension type used on
      // modifierExtension element" error even though the bound extension IS a modifier extension.
      Extension statusExtension = new Extension("StatusExtension");
      FlagRule modifierRule = new FlagRule(".");
      modifierRule.modifier = true;
      OnlyRule onlyRule = new OnlyRule("value[x]");
      onlyRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "code"
        }
        """))));
      statusExtension.rules.addAll(Arrays.asList(modifierRule, onlyRule));
      doc.extensions.put(statusExtension.name, statusExtension);
      ContainsRule containsRule = new ContainsRule("modifierExtension");
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "type",
          "type": "StatusExtension"
        }
        """))));
      patient.rules.add(containsRule);
      AssignmentRule valueRule = new AssignmentRule("modifierExtension[type].valueCode").withFile("Collide.fsh").withLocation(new int[] {5, 3, 5, 40});
      valueRule.value = new FshCode("final");
      patientInstance.rules.add(valueRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/StatusExtension",
            "valueCode": "final"
          }
        ]
        """, at(exported, "modifierExtension"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(false, some(loggerSpy.getAllMessages("error"), (m) -> jsRegex("Non-modifier extension .* used on modifierExtension element", "").matcher(Js.toStr(((String) m))).find()));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log a false modifier error when an extension slice name collides with an unrelated modifier extension")
    void shouldNotLogAFalseModifierErrorWhenAnExtensionSliceNameCollidesWithAnUnrelatedMo() {
      // Regression (mirror direction): an extension slice named after a modifier extension must not
      // emit a false "Modifier extension … used on extension element" error. The slice binds a
      // genuine non-modifier extension via its type.profile; only the removed bare-name fish would
      // hit the modifier decoy (whose id equals the slice name).
      Extension decoyModifier = new Extension("DecoyModifier");
      decoyModifier.setId("decoymod");
      FlagRule decoyModifierRule = new FlagRule(".");
      decoyModifierRule.modifier = true;
      decoyModifier.rules.add(decoyModifierRule);
      doc.extensions.put(decoyModifier.name, decoyModifier);
      Extension plainExtension = new Extension("PlainExtension");
      OnlyRule onlyRule = new OnlyRule("value[x]");
      onlyRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "code"
        }
        """))));
      plainExtension.rules.add(onlyRule);
      doc.extensions.put(plainExtension.name, plainExtension);
      ContainsRule containsRule = new ContainsRule("extension");
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "decoymod",
          "type": "PlainExtension"
        }
        """))));
      patient.rules.add(containsRule);
      AssignmentRule valueRule = new AssignmentRule("extension[decoymod].valueCode").withFile("Collide.fsh").withLocation(new int[] {6, 3, 6, 40});
      valueRule.value = new FshCode("ok");
      patientInstance.rules.add(valueRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/PlainExtension",
            "valueCode": "ok"
          }
        ]
        """, at(exported, "extension"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(false, some(loggerSpy.getAllMessages("error"), (m) -> jsRegex("Modifier extension .* used on extension element", "").matcher(Js.toStr(((String) m))).find()));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a sliced extension element that is referred to by aliased url")
    void shouldAssignASlicedExtensionElementThatIsReferredToByAliasedUrl() {
      Extension fooExtension = new Extension("FooExtension");
      doc.aliases.put("FooAlias", "http://hl7.org/fhir/us/minimal/StructureDefinition/FooExtension");
      doc.extensions.put(fooExtension.name, fooExtension);
      ContainsRule containsRule = new ContainsRule("extension");
      containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "foo",
          "type": "FooExtension"
        }
        """))));
      patientProf.rules.add(containsRule);
      AssignmentRule barRule = new AssignmentRule("extension[FooAlias].valueString");
      barRule.value = "bar";
      patientProfInstance.rules.add(barRule);
      InstanceDefinition exported = exportInstance.apply(patientProfInstance);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/FooExtension",
            "valueString": "bar"
          }
        ]
        """, at(exported, "extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign an extension that is defined but not present on the SD")
    void shouldAssignAnExtensionThatIsDefinedButNotPresentOnTheSD() {
      Extension fooExtension = new Extension("FooExtension");
      doc.aliases.put("FooAlias", "http://hl7.org/fhir/us/minimal/StructureDefinition/FooExtension");
      doc.extensions.put(fooExtension.name, fooExtension);
      AssignmentRule barRule = new AssignmentRule("extension[FooAlias].valueString");
      barRule.value = "bar";
      patientInstance.rules.add(barRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/FooExtension",
            "valueString": "bar"
          }
        ]
        """, at(exported, "extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not assign an extension that is not defined and not present on the SD")
    void shouldNotAssignAnExtensionThatIsNotDefinedAndNotPresentOnTheSD() {
      AssignmentRule barRule = new AssignmentRule("extension[FooAlias].valueString");
      barRule.value = "bar";
      patientInstance.rules.add(barRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertNull(at(exported, "extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when a modifier extension is assigned to an extension path")
    void shouldLogAnErrorWhenAModifierExtensionIsAssignedToAnExtensionPath() {
      // Extension: StrangeExtension
      // * . ?!
      Extension strangeExtension = new Extension("StrangeExtension");
      FlagRule modifierRule = new FlagRule(".");
      modifierRule.modifier = true;
      strangeExtension.rules.add(modifierRule);
      doc.extensions.put(strangeExtension.name, strangeExtension);
      // Instance: StrangeInstance
      // InstanceOf: StrangeExtension
      // Usage: #inline
      Instance strangeInstance = new Instance("StrangeInstance");
      strangeInstance.instanceOf = "StrangeExtension";
      strangeInstance.usage = InstanceUsage.Inline;
      doc.instances.put(strangeInstance.name, strangeInstance);
      // Instance: Bar
      // InstanceOf: TestPatient
      // extension[0] = StrangeInstance
      AssignmentRule strangeRule = new AssignmentRule("extension[0]").withFile("Strange.fsh").withLocation(new int[] {5, 3, 5, 28});
      strangeRule.value = "StrangeInstance";
      strangeRule.isInstance = true;
      patientInstance.rules.add(strangeRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/StrangeExtension"
          }
        ]
        """, at(exported, "extension"));
      assertMatches("Instance of modifier extension StrangeExtension assigned to extension path\\. Modifier extensions should only be assigned to modifierExtension paths\\..*File: Strange\\.fsh.*Line: 5\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when a non-modifier extension is assigned to a modifierExtension path")
    void shouldLogAnErrorWhenANonModifierExtensionIsAssignedToAModifierExtensionPath() {
      // Extension: StrangeExtension
      Extension strangeExtension = new Extension("StrangeExtension");
      doc.extensions.put(strangeExtension.name, strangeExtension);
      // Instance: StrangeInstance
      // InstanceOf: StrangeExtension
      // Usage: #inline
      Instance strangeInstance = new Instance("StrangeInstance");
      strangeInstance.instanceOf = "StrangeExtension";
      strangeInstance.usage = InstanceUsage.Inline;
      doc.instances.put(strangeInstance.name, strangeInstance);
      // Instance: Bar
      // InstanceOf: TestPatient
      // modifierExtension[0] = StrangeInstance
      AssignmentRule strangeRule = new AssignmentRule("modifierExtension[0]").withFile("Strange.fsh").withLocation(new int[] {5, 3, 5, 28});
      strangeRule.value = "StrangeInstance";
      strangeRule.isInstance = true;
      patientInstance.rules.add(strangeRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/StrangeExtension"
          }
        ]
        """, at(exported, "modifierExtension"));
      assertMatches("Instance of non-modifier extension StrangeExtension assigned to modifierExtension path\\. Non-modifier extensions should only be assigned to extension paths\\..*File: Strange\\.fsh.*Line: 5\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when a modifier extension is used on an extension element as part of a longer path")
    void shouldLogAnErrorWhenAModifierExtensionIsUsedOnAnExtensionElementAsPartOfALongerP() {
      // Extension: StrangeExtension
      // * value[x] only string
      // * . ?!
      Extension strangeExtension = new Extension("StrangeExtension");
      OnlyRule onlyRule = new OnlyRule("value[x]");
      onlyRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "string"
        }
        """))));
      FlagRule modifierRule = new FlagRule(".");
      modifierRule.modifier = true;
      strangeExtension.rules.addAll(Arrays.asList(onlyRule, modifierRule));
      doc.extensions.put(strangeExtension.name, strangeExtension);
      // Instance: Bar
      // InstanceOf: TestPatient
      // extension[StrangeExtension].valueString = "This is strange"
      AssignmentRule strangeRule = new AssignmentRule("extension[StrangeExtension].valueString").withFile("Strange.fsh").withLocation(new int[] {7, 7, 7, 19});
      strangeRule.value = "This is strange";
      patientInstance.rules.add(strangeRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/StrangeExtension",
            "valueString": "This is strange"
          }
        ]
        """, at(exported, "extension"));
      assertMatches("Modifier extension StrangeExtension used on extension element\\. Modifier extensions should only be used with modifierExtension elements\\..*File: Strange\\.fsh.*Line: 7\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when a modifier extension is used on an extension element in the middle of a path")
    void shouldLogAnErrorWhenAModifierExtensionIsUsedOnAnExtensionElementInTheMiddleOfAPa() {
      // Extension: StrangeExtension
      // * value[x] only string
      // * . ?!
      Extension strangeExtension = new Extension("StrangeExtension");
      OnlyRule onlyRule = new OnlyRule("value[x]");
      onlyRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "string"
        }
        """))));
      FlagRule modifierRule = new FlagRule(".");
      modifierRule.modifier = true;
      strangeExtension.rules.addAll(Arrays.asList(onlyRule, modifierRule));
      doc.extensions.put(strangeExtension.name, strangeExtension);
      // Instance: Bar
      // InstanceOf: TestPatient
      // maritalStatus.extension[StrangeExtension].valueString = "This is strange"
      AssignmentRule strangeRule = new AssignmentRule("maritalStatus.extension[StrangeExtension].valueString").withFile("Strange.fsh").withLocation(new int[] {9, 5, 9, 23});
      strangeRule.value = "This is strange";
      patientInstance.rules.add(strangeRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/StrangeExtension",
            "valueString": "This is strange"
          }
        ]
        """, at(exported, "maritalStatus.extension"));
      assertMatches("Modifier extension StrangeExtension used on extension element\\. Modifier extensions should only be used with modifierExtension elements\\..*File: Strange\\.fsh.*Line: 9\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when a non-modifier extension is used on a modifierExtension element as part of a longer path")
    void shouldLogAnErrorWhenANonModifierExtensionIsUsedOnAModifierExtensionElementAsPart() {
      // Extension: StrangeExtension
      // * value[x] only string
      Extension strangeExtension = new Extension("StrangeExtension");
      OnlyRule onlyRule = new OnlyRule("value[x]");
      onlyRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "string"
        }
        """))));
      strangeExtension.rules.add(onlyRule);
      doc.extensions.put(strangeExtension.name, strangeExtension);
      // Instance: Bar
      // InstanceOf: TestPatient
      // * modifierExtension[StrangeExtension].valueString = "This is normal"
      AssignmentRule strangeRule = new AssignmentRule("modifierExtension[StrangeExtension].valueString").withFile("Strange.fsh").withLocation(new int[] {6, 7, 6, 19});
      strangeRule.value = "This is normal";
      patientInstance.rules.add(strangeRule);
      InstanceDefinition exported = exportInstance.apply(patientInstance);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/us/minimal/StructureDefinition/StrangeExtension",
            "valueString": "This is normal"
          }
        ]
        """, at(exported, "modifierExtension"));
      assertMatches("Non-modifier extension StrangeExtension used on modifierExtension element\\. Non-modifier extensions should only be used with extension elements\\..*File: Strange\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should throw when ordered is set in the discriminator but slices arrive out of order")
    @Disabled("it.skip")
    void shouldThrowWhenOrderedIsSetInTheDiscriminatorButSlicesArriveOutOfOrder() {
      AssignmentRule assignedValRule = new AssignmentRule("result[Triglyceride].display");
      assignedValRule.value = "foo";
      lipidInstance.rules.add(assignedValRule);
      // Feel free to change this error message when actually implementing
      assertThrowsMessage("Slice Triglyceride of result assigned out of order", () -> exportInstance.apply(lipidInstance));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should throw if incorrect elements are added when the slicing is closed")
    @Disabled("it.skip")
    void shouldThrowIfIncorrectElementsAreAddedWhenTheSlicingIsClosed() {
      AssignmentRule assignedValRule = new AssignmentRule("result[0].display");
      assignedValRule.value = "foo";
      lipidInstance.rules.add(assignedValRule);
      assertThrowsMessage("Slicing on result is closed, only named slices may be added", () -> exportInstance.apply(lipidInstance));
    }

    // Content Reference
    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a child of a contentReference element")
    void shouldAssignAChildOfAContentReferenceElement() {
      AssignmentRule barRule = new AssignmentRule("compose.exclude.version");
      barRule.value = "bar";
      valueSetInstance.rules.add(barRule);
      InstanceDefinition exported = exportInstance.apply(valueSetInstance);
      assertJsonEqual("""
        {
          "exclude": [
            {
              "version": "bar"
            }
          ]
        }
        """, at(exported, "compose"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a child of a contentReference element in a logical model")
    void shouldAssignAChildOfAContentReferenceElementInALogicalModel() {
      // Modeled after: https://gist.github.com/bkaney/49485fb5316db4868af4fab9eddd56f6
      Instance viewDefinitionInstance = new Instance("PatientAddresses");
      viewDefinitionInstance.instanceOf = "ViewDefinition";
      viewDefinitionInstance.usage = InstanceUsage.Example;
      doc.instances.put(viewDefinitionInstance.name, viewDefinitionInstance);
      AssignmentRule selectNameRule = new AssignmentRule("select.name");
      selectNameRule.value = "patient_id";
      AssignmentRule selectExpressionRule = new AssignmentRule("select.expression");
      selectExpressionRule.value = "id";
      AssignmentRule selectForEachExpressionRule = new AssignmentRule("select.forEach.expression");
      selectForEachExpressionRule.value = "address";
      // NOTE: the next two assignments dive into nested properties of the contentReference
      AssignmentRule selectForEachSelectNameRule = new AssignmentRule("select.forEach.select.name");
      selectForEachSelectNameRule.value = "city";
      AssignmentRule selectForEachSelectExpressionRule = new AssignmentRule("select.forEach.select.expression");
      selectForEachSelectExpressionRule.value = "city";
      viewDefinitionInstance.rules.addAll(Arrays.asList(selectNameRule, selectExpressionRule, selectForEachExpressionRule, selectForEachSelectNameRule, selectForEachSelectExpressionRule));
      InstanceDefinition exported = exportInstance.apply(viewDefinitionInstance);
      assertJsonEqual("""
        [
          {
            "name": "patient_id",
            "expression": "id",
            "forEach": {
              "expression": "address",
              "select": [
                {
                  "name": "city",
                  "expression": "city"
                }
              ]
            }
          }
        ]
        """, at(exported, "select"));
    }

    // Validating required elements
    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when a required element is not present")
    void shouldLogAnErrorWhenARequiredElementIsNotPresent() {
      CardRule cardRule = new CardRule("active");
      cardRule.min = 1.0;
      cardRule.max = "1";
      patient.rules.add(cardRule);
      exportInstance.apply(patientInstance);
      assertMatches("Patient.active.*File: PatientInstance\\.fsh.*Line: 10 - 20", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log multiple errors when multiple required elements are not present")
    void shouldLogMultipleErrorsWhenMultipleRequiredElementsAreNotPresent() {
      CardRule cardRule1 = new CardRule("active");
      cardRule1.min = 1.0;
      cardRule1.max = "1";
      patient.rules.add(cardRule1);
      CardRule cardRule2 = new CardRule("gender");
      cardRule2.min = 1.0;
      cardRule2.max = "1";
      patient.rules.add(cardRule2);
      exportInstance.apply(patientInstance);
      List<String> messages = loggerSpy.getAllMessages("error");
      assertMatches("Patient.active.*File: PatientInstance\\.fsh.*Line: 10 - 20", "s", messages.get(messages.size() - 2));
      assertMatches("Patient.gender.*File: PatientInstance\\.fsh.*Line: 10 - 20", "s", messages.get(messages.size() - 1));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when an element required by an incomplete assigned parent is not present")
    void shouldLogAnErrorWhenAnElementRequiredByAnIncompleteAssignedParentIsNotPresent() {
      CardRule cardRule = new CardRule("maritalStatus.text");
      cardRule.min = 1.0;
      cardRule.max = "1";
      patient.rules.add(cardRule);
      AssignmentRule assignedValueRule = new AssignmentRule("maritalStatus");
      assignedValueRule.value = new FshCode("foo");
      patientInstance.rules.add(assignedValueRule);
      exportInstance.apply(patientInstance);
      assertMatches("Patient.maritalStatus.text.*File: PatientInstance\\.fsh.*Line: 10 - 20", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error for a parent only when a required parent is not present")
    void shouldLogAnErrorForAParentOnlyWhenARequiredParentIsNotPresent() {
      CardRule cardRule1 = new CardRule("maritalStatus.text");
      cardRule1.min = 1.0;
      cardRule1.max = "1";
      patient.rules.add(cardRule1);
      CardRule cardRule2 = new CardRule("maritalStatus");
      cardRule2.min = 1.0;
      cardRule2.max = "1";
      patient.rules.add(cardRule2);
      exportInstance.apply(patientInstance);
      assertMatches("Patient.maritalStatus.*File: PatientInstance\\.fsh.*Line: 10 - 20", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when an array does not have all required elements")
    void shouldLogAnErrorWhenAnArrayDoesNotHaveAllRequiredElements() {
      CardRule cardRule = new CardRule("contact");
      cardRule.min = 2.0;
      cardRule.max = "*";
      patient.rules.add(cardRule);
      AssignmentRule assignedValueRule = new AssignmentRule("contact[0].gender");
      assignedValueRule.value = new FshCode("F");
      patientInstance.rules.add(assignedValueRule);
      exportInstance.apply(patientInstance);
      assertMatches("Patient.contact.*File: PatientInstance\\.fsh.*Line: 10 - 20", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error multiple times for an element missing required elements in an array")
    void shouldLogAnErrorMultipleTimesForAnElementMissingRequiredElementsInAnArray() {
      CardRule cardRule = new CardRule("contact.gender");
      cardRule.min = 1.0;
      cardRule.max = "1";
      patient.rules.add(cardRule);
      AssignmentRule assignedValueRule1 = new AssignmentRule("contact[0].relationship");
      assignedValueRule1.value = new FshCode("Looking for love");
      patientInstance.rules.add(assignedValueRule1);
      AssignmentRule assignedValueRule2 = new AssignmentRule("contact[1].relationship");
      assignedValueRule2.value = new FshCode("Complicated");
      patientInstance.rules.add(assignedValueRule2);
      exportInstance.apply(patientInstance);
      List<String> messages = loggerSpy.getAllMessages("error");
      assertMatches("Patient.contact.gender.*File: PatientInstance\\.fsh.*Line: 10 - 20", "s", messages.get(messages.size() - 2));
      assertMatches("Patient.contact.gender.*File: PatientInstance\\.fsh.*Line: 10 - 20", "s", messages.get(messages.size() - 1));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when an [x] element is not present")
    void shouldLogAnErrorWhenAnXElementIsNotPresent() {
      CardRule cardRule = new CardRule("deceased[x]");
      cardRule.min = 1.0;
      cardRule.max = "1";
      patient.rules.add(cardRule);
      exportInstance.apply(patientInstance);
      List<String> messages = loggerSpy.getAllMessages("error");
      assertMatches("Patient.deceased\\[x\\].*File: PatientInstance\\.fsh.*Line: 10 - 20", "s", messages.get(messages.size() - 1));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log an error when an [x] element is present")
    void shouldNotLogAnErrorWhenAnXElementIsPresent() {
      int originalLength = loggerSpy.getAllMessages("error").size();
      CardRule cardRule = new CardRule("deceased[x]");
      cardRule.min = 1.0;
      cardRule.max = "1";
      patient.rules.add(cardRule);
      AssignmentRule assignedValueRule = new AssignmentRule("deceasedBoolean");
      assignedValueRule.value = true;
      patientInstance.rules.add(assignedValueRule);
      exportInstance.apply(patientInstance);
      assertEquals(originalLength, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when a required sliced element is not present")
    void shouldLogAnErrorWhenARequiredSlicedElementIsNotPresent() {
      AssignmentRule assignedValueRule = new AssignmentRule("result[Cholesterol]");
      assignedValueRule.value = new FshReference("Fsh are friends");
      lipidInstance.rules.add(assignedValueRule);
      exportInstance.apply(lipidInstance);
      List<String> messages = loggerSpy.getAllMessages("error");
      assertMatches("DiagnosticReport.status.*File: LipidInstance\\.fsh.*Line: 10 - 20", "s", messages.get(messages.size() - 4));
      assertMatches("DiagnosticReport.result.*File: LipidInstance\\.fsh.*Line: 10 - 20", "s", messages.get(messages.size() - 3));
      assertMatches("DiagnosticReport.result:Triglyceride.*File: LipidInstance\\.fsh.*Line: 10 - 20", "s", messages.get(messages.size() - 2));
      assertMatches("DiagnosticReport.result:HDLCholesterol.*File: LipidInstance\\.fsh.*Line: 10 - 20", "s", messages.get(messages.size() - 1));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log an error when a required sliced element could be satisfied by elements without a sliceName")
    void shouldNotLogAnErrorWhenARequiredSlicedElementCouldBeSatisfiedByElementsWithoutAS() {
      AssignmentRule assignedValueRule = new AssignmentRule("result[0]");
      assignedValueRule.value = new FshReference("Fsh are friends");
      lipidInstance.rules.add(assignedValueRule);
      exportInstance.apply(lipidInstance);
      List<String> messages = loggerSpy.getAllMessages("error");
      // No errors relating to specific slices are logged, since result[0] could be referring to any of them
      assertMatches("DiagnosticReport.status.*File: LipidInstance\\.fsh.*Line: 10 - 20", "s", messages.get(messages.size() - 2));
      assertMatches("DiagnosticReport.result.*File: LipidInstance\\.fsh.*Line: 10 - 20", "s", messages.get(messages.size() - 1));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when a required element inherited from a resource is not present")
    void shouldLogAnErrorWhenARequiredElementInheritedFromAResourceIsNotPresent() {
      Instance observationInstance = new Instance("Pow").withFile("ObservationInstance.fsh").withLocation(new int[] {10, 1, 20, 30});
      observationInstance.instanceOf = "Observation";
      doc.instances.put(observationInstance.name, observationInstance);
      exportInstance.apply(observationInstance);
      List<String> messages = loggerSpy.getAllMessages("error");
      assertMatches("Observation.code.*File: ObservationInstance\\.fsh.*Line: 10 - 20", "s", messages.get(messages.size() - 1));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when a required element inherited on a profile is not present")
    void shouldLogAnErrorWhenARequiredElementInheritedOnAProfileIsNotPresent() {
      Profile observationProfile = new Profile("TestObservation");
      observationProfile.parent = "Observation";
      doc.profiles.put(observationProfile.name, observationProfile);
      Instance observationInstance = new Instance("Pow").withFile("ObservationInstance.fsh").withLocation(new int[] {10, 1, 20, 30});
      observationInstance.instanceOf = "TestObservation";
      doc.instances.put(observationInstance.name, observationInstance);
      exportInstance.apply(observationInstance);
      List<String> messages = loggerSpy.getAllMessages("error");
      assertMatches("Observation.code.*File: ObservationInstance\\.fsh.*Line: 10 - 20", "s", messages.get(messages.size() - 1));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log an error when a required choice element has an extension on a complex type choice")
    void shouldNotLogAnErrorWhenARequiredChoiceElementHasAnExtensionOnAComplexTypeChoice() {
      // Profile: TestObservation
      // Parent: Observation
      // * effective[x] 1..1
      Profile observationProfile = new Profile("TestObservation");
      observationProfile.parent = "Observation";
      CardRule cardRule = new CardRule("effective[x]");
      cardRule.min = 1.0;
      cardRule.max = "1";
      observationProfile.rules.add(cardRule);
      doc.profiles.put(observationProfile.name, observationProfile);
      // Instance: HasExtension
      // InstanceOf: TestObservation
      // * status = #final
      // * code = #1234
      // * effectiveQuantity.extension.url = "http://example.org/SomeExtension"
      // * effectiveQuantity.extension.valueInteger = 7
      Instance observationInstance = new Instance("HasExtension");
      observationInstance.instanceOf = "TestObservation";
      AssignmentRule statusRule = new AssignmentRule("status");
      statusRule.value = new FshCode("final");
      AssignmentRule codeRule = new AssignmentRule("code");
      codeRule.value = new FshCode("1234");
      AssignmentRule extensionUrlRule = new AssignmentRule("effectivePeriod.extension.url");
      extensionUrlRule.value = "http://example.org/SomeExtension";
      AssignmentRule extensionValueRule = new AssignmentRule("effectivePeriod.extension.valueInteger");
      extensionValueRule.value = 7.0;
      observationInstance.rules.addAll(Arrays.asList(statusRule, codeRule, extensionUrlRule, extensionValueRule));
      doc.instances.put(observationInstance.name, observationInstance);
      exportInstance.apply(observationInstance);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log an error when a required choice element has an extension on a primitive type choice")
    void shouldNotLogAnErrorWhenARequiredChoiceElementHasAnExtensionOnAPrimitiveTypeChoic() {
      CardRule cardRule = new CardRule("deceased[x]");
      cardRule.min = 1.0;
      cardRule.max = "1";
      patient.rules.add(cardRule);
      AssignmentRule extensionUrl = new AssignmentRule("deceasedBoolean.extension.url");
      extensionUrl.value = "http://example.org/StrutureDefinition/SomeExtension";
      AssignmentRule extensionValue = new AssignmentRule("deceasedBoolean.extension.valueCode");
      extensionValue.value = new FshCode("complicated");
      patientInstance.rules.addAll(Arrays.asList(extensionUrl, extensionValue));
      exportInstance.apply(patientInstance);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when a required primitive child element is not present")
    void shouldLogAnErrorWhenARequiredPrimitiveChildElementIsNotPresent() {
      CardRule cardRule1 = new CardRule("active.id");
      cardRule1.min = 1.0;
      cardRule1.max = "1";
      CardRule cardRule2 = new CardRule("active");
      cardRule2.min = 1.0;
      cardRule2.max = "1";
      // * active.id 1..1
      // * active 1..1
      patient.rules.addAll(Arrays.asList(cardRule1, cardRule2));
      AssignmentRule activeRule = new AssignmentRule("active");
      activeRule.value = true;
      // * active = true
      patientInstance.rules.add(activeRule);
      exportInstance.apply(patientInstance);
      assertMatches("Patient.active.id has minimum cardinality 1 but occurs 0 time\\(s\\).*File: PatientInstance\\.fsh.*Line: 10 - 20", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log an error when a required primitive child element is present")
    void shouldNotLogAnErrorWhenARequiredPrimitiveChildElementIsPresent() {
      CardRule cardRule1 = new CardRule("active.id");
      cardRule1.min = 1.0;
      cardRule1.max = "1";
      CardRule cardRule2 = new CardRule("active");
      cardRule2.min = 1.0;
      cardRule2.max = "1";
      // * active.id 1..1
      // * active 1..1
      patient.rules.addAll(Arrays.asList(cardRule1, cardRule2));
      AssignmentRule idRule = new AssignmentRule("active.id");
      idRule.value = "foo";
      // * active.id = "foo"
      patientInstance.rules.add(idRule);
      exportInstance.apply(patientInstance);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when a required primitive child array is not large enough")
    void shouldLogAnErrorWhenARequiredPrimitiveChildArrayIsNotLargeEnough() {
      CardRule cardRule1 = new CardRule("active.extension");
      cardRule1.min = 2.0;
      cardRule1.max = "*";
      CardRule cardRule2 = new CardRule("active");
      cardRule2.min = 1.0;
      cardRule2.max = "1";
      // * active.extension 2..*
      // * active 1..1
      patient.rules.addAll(Arrays.asList(cardRule1, cardRule2));
      AssignmentRule activeRule = new AssignmentRule("active");
      activeRule.value = true;
      AssignmentRule extensionRule = new AssignmentRule("active.extension.url");
      extensionRule.value = "http://example.com";
      // * active = true
      // * active.extension.url = "http://example.com"
      patientInstance.rules.addAll(Arrays.asList(activeRule, extensionRule));
      exportInstance.apply(patientInstance);
      assertMatches("Patient.active.extension has minimum cardinality 2 but occurs 1 time\\(s\\).*File: PatientInstance\\.fsh.*Line: 10 - 20", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log an error when a required primitive child array is large enough")
    void shouldNotLogAnErrorWhenARequiredPrimitiveChildArrayIsLargeEnough() {
      CardRule cardRule1 = new CardRule("active.extension");
      cardRule1.min = 1.0;
      cardRule1.max = "*";
      CardRule cardRule2 = new CardRule("active");
      cardRule2.min = 1.0;
      cardRule2.max = "1";
      // * active.extension 1..*
      // * active 1..1
      patient.rules.addAll(Arrays.asList(cardRule1, cardRule2));
      AssignmentRule idRule = new AssignmentRule("active.extension.url");
      idRule.value = "http://example.com";
      // * active.extension.url = "http://example.com"
      patientInstance.rules.add(idRule);
      exportInstance.apply(patientInstance);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log an error when a required primitive value element is present on the parent primitive")
    void shouldNotLogAnErrorWhenARequiredPrimitiveValueElementIsPresentOnTheParentPrimiti() {
      // * active 1..1
      // * active.value 1..1
      CardRule activeCard = new CardRule("active");
      activeCard.min = 1.0;
      activeCard.max = "1";
      CardRule valueCard = new CardRule("active.value");
      valueCard.min = 1.0;
      valueCard.max = "1";
      patient.rules.addAll(Arrays.asList(activeCard, valueCard));
      // * active = true
      AssignmentRule assignedValue = new AssignmentRule("active");
      assignedValue.value = true;
      patientInstance.rules.add(assignedValue);
      exportInstance.apply(patientInstance);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log an error when a required primitive value element is present on the parent array primitive")
    void shouldNotLogAnErrorWhenARequiredPrimitiveValueElementIsPresentOnTheParentArrayPr() {
      // Profile: TestAllergyIntolerance
      // Parent: AllergyIntolerance
      // * category 1..*
      // * category.value 1..1
      // * patient = Reference(SomePatient)
      Profile allergyProfile = new Profile("TestAllergyIntolerance");
      allergyProfile.parent = "AllergyIntolerance";
      CardRule categoryCard = new CardRule("category");
      categoryCard.min = 1.0;
      categoryCard.max = "*";
      CardRule valueCard = new CardRule("category.value");
      valueCard.min = 1.0;
      valueCard.max = "1";
      AssignmentRule patientValue = new AssignmentRule("patient");
      patientValue.value = new FshReference("SomePatient");
      allergyProfile.rules.addAll(Arrays.asList(categoryCard, valueCard, patientValue));
      doc.profiles.put(allergyProfile.name, allergyProfile);
      // Instance: MyAllergies
      // InstanceOf: TestAllergyIntolerance
      // * category[0] = #food
      // * category[1] = #environment
      Instance allergyInstance = new Instance("MyAllergies");
      allergyInstance.instanceOf = "TestAllergyIntolerance";
      AssignmentRule foodCategory = new AssignmentRule("category[0]");
      foodCategory.value = new FshCode("food");
      AssignmentRule environmentCategory = new AssignmentRule("category[1]");
      environmentCategory.value = new FshCode("environment");
      allergyInstance.rules.addAll(Arrays.asList(foodCategory, environmentCategory));
      doc.instances.put(allergyInstance.name, allergyInstance);
      exportInstance.apply(allergyInstance);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when a required primitive value element is not present on the parent primitive")
    void shouldLogAnErrorWhenARequiredPrimitiveValueElementIsNotPresentOnTheParentPrimiti() {
      // * active 1..1
      // * active.value 1..1
      CardRule activeCard = new CardRule("active");
      activeCard.min = 1.0;
      activeCard.max = "1";
      CardRule valueCard = new CardRule("active.value");
      valueCard.min = 1.0;
      valueCard.max = "1";
      patient.rules.addAll(Arrays.asList(activeCard, valueCard));
      // * active.extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode = #unknown
      AssignmentRule assignedExtension = new AssignmentRule("active.extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode");
      assignedExtension.value = new FshCode("unknown");
      patientInstance.rules.add(assignedExtension);
      exportInstance.apply(patientInstance);
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches("Element Patient.active.value has minimum cardinality 1.*File: PatientInstance\\.fsh.*Line: 10 - 20", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when a required primitive value element is missing on the first element of a parent array primitive")
    void shouldLogAnErrorWhenARequiredPrimitiveValueElementIsMissingOnTheFirstElementOfAP() {
      // Profile: TestAllergyIntolerance
      // Parent: AllergyIntolerance
      // * category 1..*
      // * category.value 1..1
      // * patient = Reference(SomePatient)
      Profile allergyProfile = new Profile("TestAllergyIntolerance");
      allergyProfile.parent = "AllergyIntolerance";
      CardRule categoryCard = new CardRule("category");
      categoryCard.min = 1.0;
      categoryCard.max = "*";
      CardRule valueCard = new CardRule("category.value");
      valueCard.min = 1.0;
      valueCard.max = "1";
      AssignmentRule patientValue = new AssignmentRule("patient");
      patientValue.value = new FshReference("SomePatient");
      allergyProfile.rules.addAll(Arrays.asList(categoryCard, valueCard, patientValue));
      doc.profiles.put(allergyProfile.name, allergyProfile);
      // Instance: MyAllergies
      // InstanceOf: TestAllergyIntolerance
      // * category[0].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode = #unknown
      // * category[1] = #environment
      Instance allergyInstance = new Instance("MyAllergies").withFile("AllergyInstance.fsh").withLocation(new int[] {14, 3, 21, 28});
      allergyInstance.instanceOf = "TestAllergyIntolerance";
      AssignmentRule unknownCategory = new AssignmentRule("category[0].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode");
      unknownCategory.value = new FshCode("unknown");
      AssignmentRule environmentCategory = new AssignmentRule("category[1]");
      environmentCategory.value = new FshCode("environment");
      allergyInstance.rules.addAll(Arrays.asList(unknownCategory, environmentCategory));
      doc.instances.put(allergyInstance.name, allergyInstance);
      exportInstance.apply(allergyInstance);
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches("Element AllergyIntolerance.category.value has minimum cardinality 1.*File: AllergyInstance\\.fsh.*Line: 14 - 21", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log an error when a required primitive value element is missing on the parent sliced array primitive")
    void shouldLogAnErrorWhenARequiredPrimitiveValueElementIsMissingOnTheParentSlicedArra() {
      // Profile: SlicedAllergyIntolerance
      // Parent: AllergyIntolerance
      // * category 1..*
      // * category ^slicing.discriminator.type = #value
      // * category ^slicing.discriminator.path = "$this"
      // * category ^slicing.rules = #open
      // * category contains Strict 0..* and Flexible 0..*
      // * category[Strict].value 1..1
      // * patient = Reference(SomePatient)
      Profile allergyProfile = new Profile("SlicedAllergyIntolerance");
      allergyProfile.parent = "AllergyIntolerance";
      CardRule categoryCard = new CardRule("category");
      categoryCard.min = 1.0;
      categoryCard.max = "*";
      CaretValueRule slicingType = new CaretValueRule("category");
      slicingType.caretPath = "slicing.discriminator.type";
      slicingType.value = new FshCode("value");
      CaretValueRule slicingPath = new CaretValueRule("category");
      slicingPath.caretPath = "slicing.discriminator.path";
      slicingPath.value = "$this";
      CaretValueRule slicingRules = new CaretValueRule("category");
      slicingRules.caretPath = "slicing.rules";
      slicingRules.value = new FshCode("open");
      ContainsRule categoryContains = new ContainsRule("category");
      categoryContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Strict"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "Flexible"
        }
        """))));
      CardRule strictCard = new CardRule("category[Strict]");
      strictCard.min = 0.0;
      strictCard.max = "*";
      CardRule flexibleCard = new CardRule("category[Flexible]");
      flexibleCard.min = 0.0;
      flexibleCard.max = "*";
      CardRule strictValueCard = new CardRule("category[Strict].value");
      strictValueCard.min = 1.0;
      strictValueCard.max = "1";
      AssignmentRule patientValue = new AssignmentRule("patient");
      patientValue.value = new FshReference("SomePatient");
      allergyProfile.rules.addAll(Arrays.asList(categoryCard, slicingType, slicingPath, slicingRules, categoryContains, strictCard, flexibleCard, strictValueCard, patientValue));
      doc.profiles.put(allergyProfile.name, allergyProfile);
      // Instance: MyAllergies
      // InstanceOf: SlicedAllergyIntolerance
      // * category[Flexible][0].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode = #unknown
      // * category[Flexible][1] = #environment
      // * category[Strict][0] = #food
      // * category[Strict][1].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode = #unknown
      Instance allergyInstance = new Instance("MyAllergies").withFile("AllergyInstance.fsh").withLocation(new int[] {15, 3, 22, 28});
      allergyInstance.instanceOf = "SlicedAllergyIntolerance";
      AssignmentRule flexibleZero = new AssignmentRule("category[Flexible][0].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode");
      flexibleZero.value = new FshCode("unknown");
      AssignmentRule flexibleOne = new AssignmentRule("category[Flexible][1]");
      flexibleOne.value = new FshCode("environment");
      AssignmentRule strictZero = new AssignmentRule("category[Strict][0]");
      strictZero.value = new FshCode("food");
      AssignmentRule strictOne = new AssignmentRule("category[Strict][1].extension[http://hl7.org/fhir/StructureDefinition/data-absent-reason].valueCode");
      strictOne.value = new FshCode("unknown");
      allergyInstance.rules.addAll(Arrays.asList(flexibleZero, flexibleOne, strictZero, strictOne));
      doc.instances.put(allergyInstance.name, allergyInstance);
      InstanceDefinition exported = exportInstance.apply(allergyInstance);
      // because manual slice ordering is off, the slice order in category is:
      // Flexible[0], Strict[0], Strict[1], Flexible[1]
      assertEquals(4, len(at(exported, "category")));
      assertJsonEqual("""
        [
          null,
          "food",
          null,
          "environment"
        ]
        """, at(exported, "category"));
      assertEquals(4, len(at(exported, "_category")));
      assertJsonEqual("""
        [
          {
            "extension": [
              {
                "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                "valueCode": "unknown"
              }
            ]
          },
          null,
          {
            "extension": [
              {
                "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                "valueCode": "unknown"
              }
            ]
          },
          null
        ]
        """, at(exported, "_category"));
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertMatches("Element AllergyIntolerance.category:Strict\\.value has minimum cardinality 1.*File: AllergyInstance\\.fsh.*Line: 15 - 22", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log an error when a connected element fulfills the cardinality constraint")
    void shouldNotLogAnErrorWhenAConnectedElementFulfillsTheCardinalityConstraint() {
      CaretValueRule caretRule = new CaretValueRule("item");
      caretRule.caretPath = "slicing.discriminator.path";
      caretRule.value = "type";
      CaretValueRule dTypeRule = new CaretValueRule("item");
      dTypeRule.caretPath = "slicing.discriminator.type";
      dTypeRule.value = new FshCode("value");
      CaretValueRule rulesRule = new CaretValueRule("item");
      rulesRule.caretPath = "slicing.rules";
      rulesRule.value = new FshCode("open");
      ContainsRule containsRule = new ContainsRule("item");
      containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "boo"
        }
        """)));
      CardRule cardRule = new CardRule("item[boo]");
      cardRule.min = 0.0;
      cardRule.max = "1";
      // * item ^slicing.discriminator[0].path = "type"
      // * item ^slicing.discriminator[0].type = #value
      // * item ^slicing.rules = #open
      // * item contains boo 0..1
      questionnaire.rules.addAll(Arrays.asList(caretRule, dTypeRule, rulesRule, containsRule, cardRule));
      AssignmentRule answerRule = new AssignmentRule("item[boo].answerOption[0].valueString");
      answerRule.value = "foo";
      AssignmentRule linkIdRule = new AssignmentRule("item[boo].linkId");
      linkIdRule.value = "bar";
      AssignmentRule typeRule = new AssignmentRule("item[boo].type");
      typeRule.value = new FshCode("group");
      AssignmentRule statusRule = new AssignmentRule("status");
      statusRule.value = new FshCode("active");
      // * item[boo].answerOption[0].valueString = "foo"
      // * item[boo].linkId = "bar"
      // * item[boo].type = #group
      // * status = #active
      Instance questionnaireInstance = new Instance("Test");
      questionnaireInstance.instanceOf = "TestQuestionnaire";
      questionnaireInstance.rules.addAll(Arrays.asList(answerRule, linkIdRule, typeRule, statusRule));
      exportInstance.apply(questionnaireInstance);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should properly validate slices with child elements of differing cardinalities")
    void shouldProperlyValidateSlicesWithChildElementsOfDifferingCardinalities() {
      CaretValueRule caretRule = new CaretValueRule("payload");
      caretRule.caretPath = "slicing.discriminator.path";
      caretRule.value = "content.url";
      CaretValueRule dTypeRule = new CaretValueRule("payload");
      dTypeRule.caretPath = "slicing.discriminator.type";
      dTypeRule.value = new FshCode("exists");
      CaretValueRule rulesRule = new CaretValueRule("payload");
      rulesRule.caretPath = "slicing.rules";
      rulesRule.value = new FshCode("closed");
      ContainsRule containsRule = new ContainsRule("payload");
      containsRule.items.addAll(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "inline"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "link"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "extra"
        }
        """))));
      OnlyRule onlyRule1 = new OnlyRule("payload[inline].content[x]");
      onlyRule1.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "Attachment"
        }
        """))));
      OnlyRule onlyRule2 = new OnlyRule("payload[link].content[x]");
      onlyRule2.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "Attachment"
        }
        """))));
      OnlyRule onlyRule3 = new OnlyRule("payload[extra].content[x]");
      onlyRule3.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "string"
        }
        """))));
      CardRule inlineCardRule1 = new CardRule("payload[inline].contentAttachment.url");
      inlineCardRule1.max = "0";
      CardRule inlineCardRule2 = new CardRule("payload[inline].contentAttachment.data");
      inlineCardRule2.min = 1.0;
      CardRule inlineCardRule3 = new CardRule("payload[inline].contentAttachment.contentType");
      inlineCardRule3.min = 1.0;
      AssignmentRule inlineAssignmentRule = new AssignmentRule("payload[inline].contentAttachment.contentType");
      inlineAssignmentRule.value = new FshCode("text/plain");
      CardRule linkCardRule1 = new CardRule("payload[link].contentAttachment.url");
      linkCardRule1.min = 1.0;
      linkCardRule1.max = "1";
      CardRule linkCardRule2 = new CardRule("payload[link].contentAttachment.data");
      linkCardRule2.max = "0";
      CardRule linkCardRule3 = new CardRule("payload[link].contentAttachment.contentType");
      linkCardRule3.min = 1.0;
      AssignmentRule linkAssignmentRule = new AssignmentRule("payload[link].contentAttachment.contentType");
      linkAssignmentRule.value = new FshCode("image/png");
      // * payload contains
      //     inline 1..1 and
      //     link 0..* and
      //     extra 0..*
      // * payload[inline].content[x] only Attachment
      // * payload[inline].contentAttachment.url ..0
      // * payload[inline].contentAttachment.data 1..
      // * payload[inline].contentAttachment.contentType 1..
      // * payload[inline].contentAttachment.contentType = #text/plain
      // * payload[link].content[x] only Attachment
      // * payload[link].contentAttachment.url 1..1
      // * payload[link].contentAttachment.data ..0
      // * payload[link].contentAttachment.contentType 1..
      // * payload[link].contentAttachment.contentType = #image/png
      // * payload[extra].content[x] only string
      communicationProf.rules.addAll(Arrays.asList(caretRule, dTypeRule, rulesRule, containsRule, onlyRule1, inlineCardRule1, inlineCardRule2, inlineCardRule3, onlyRule2, linkCardRule1, linkCardRule2, linkCardRule3, onlyRule3, inlineAssignmentRule, linkAssignmentRule));
      AssignmentRule statusRule = new AssignmentRule("status");
      statusRule.value = new FshCode("active");
      AssignmentRule inlineCTAssignmentRule = new AssignmentRule("payload[inline].contentAttachment.contentType");
      inlineCTAssignmentRule.value = new FshCode("text/plain");
      AssignmentRule inlineDataAssignmentRule = new AssignmentRule("payload[inline].contentAttachment.data");
      inlineDataAssignmentRule.value = "OR3sMFkRaXsOtHff85+zrL+DXU3s5nkfIenVwcokMUx1qh8=";
      AssignmentRule linkCTAssignmentRule = new AssignmentRule("payload[link].contentAttachment.contentType");
      linkCTAssignmentRule.value = new FshCode("image/png");
      AssignmentRule linkUrlAssignmentRule = new AssignmentRule("payload[link].contentAttachment.url");
      linkUrlAssignmentRule.value = "Binary/1234";
      AssignmentRule stringAssignmentRule = new AssignmentRule("payload[extra].contentString");
      stringAssignmentRule.value = "Extra Content";
      // * status = #completed
      // * payload[inline].contentAttachment.contentType = #text/plain
      // * payload[inline].contentAttachment.data = "OR3sMFkRaXsOtHff85+zrL+DXU3s5nkfIenVwcokMUx1qh8="
      // * payload[link].contentAttachment.contentType = #image/png
      // * payload[link].contentAttachment.url = "Binary/1234"
      // * payload[extra].contentString = "Extra content"
      communicationInstance.rules.addAll(Arrays.asList(statusRule, inlineCTAssignmentRule, inlineDataAssignmentRule, linkCTAssignmentRule, linkUrlAssignmentRule, stringAssignmentRule));
      exportInstance.apply(communicationInstance);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log a warning when a pre-loaded element in a sliced array is accessed with a numeric index")
    void shouldLogAWarningWhenAPreLoadedElementInASlicedArrayIsAccessedWithANumericIndex() {
      CaretValueRule caretRule = new CaretValueRule("code");
      caretRule.caretPath = "slicing.discriminator.path";
      caretRule.value = "type";
      CaretValueRule dTypeRule = new CaretValueRule("code");
      dTypeRule.caretPath = "slicing.discriminator.type";
      dTypeRule.value = new FshCode("value");
      CaretValueRule rulesRule = new CaretValueRule("code");
      rulesRule.caretPath = "slicing.rules";
      rulesRule.value = new FshCode("open");
      ContainsRule containsRule = new ContainsRule("code");
      containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "boo"
        }
        """)));
      CardRule cardRule = new CardRule("code[boo]");
      cardRule.min = 1.0;
      cardRule.max = "1";
      AssignmentRule codeRule = new AssignmentRule("code[boo]");
      codeRule.value = new FshCode("1-8", "http://loinc.org/", "Acyclovir [Susceptibility]");
      // * code ^slicing.discriminator[0].path = "type"
      // * code ^slicing.discriminator[0].type = #value
      // * code ^slicing.rules = #open
      // * code contains boo 1..1
      // * code[boo] = http://loinc.org#1-8 "Acyclovir [Susceptibility]"
      questionnaire.rules.addAll(Arrays.asList(caretRule, dTypeRule, rulesRule, containsRule, cardRule, codeRule));
      AssignmentRule linkIdRule = new AssignmentRule("item[0].linkId");
      linkIdRule.value = "bar";
      AssignmentRule typeRule = new AssignmentRule("item[0].type");
      typeRule.value = new FshCode("group");
      AssignmentRule statusRule = new AssignmentRule("status");
      statusRule.value = new FshCode("active");
      AssignmentRule codeAssignmentRule = new AssignmentRule("code[0]");
      codeAssignmentRule.value = new FshCode("otherCode", "http://loinc.org/", "OtherDisplay");
      // * item[0].linkId = "bar"
      // * item[0].type = #group
      // * status = #active
      // * code[0] = http://loinc.org#otherCode "OtherDisplay"
      Instance questionnaireInstance = new Instance("Test");
      questionnaireInstance.instanceOf = "TestQuestionnaire";
      questionnaireInstance.rules.addAll(Arrays.asList(statusRule, linkIdRule, typeRule, codeAssignmentRule));
      exportInstance.apply(questionnaireInstance);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertEquals("Sliced element Questionnaire.code is being accessed via numeric index. Use slice names in rule paths when possible.", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log a warning when the child of a pre-loaded element in a sliced array is accessed with a numeric index")
    void shouldLogAWarningWhenTheChildOfAPreLoadedElementInASlicedArrayIsAccessedWithANum() {
      CaretValueRule caretRule = new CaretValueRule("item");
      caretRule.caretPath = "slicing.discriminator.path";
      caretRule.value = "type";
      CaretValueRule dTypeRule = new CaretValueRule("item");
      dTypeRule.caretPath = "slicing.discriminator.type";
      dTypeRule.value = new FshCode("value");
      CaretValueRule rulesRule = new CaretValueRule("item");
      rulesRule.caretPath = "slicing.rules";
      rulesRule.value = new FshCode("open");
      ContainsRule containsRule = new ContainsRule("item");
      containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "boo"
        }
        """)));
      CardRule cardRule = new CardRule("item[boo]");
      cardRule.min = 1.0;
      cardRule.max = "1";
      CardRule textCardRule = new CardRule("item[boo].text");
      textCardRule.min = 1.0;
      textCardRule.max = "1";
      AssignmentRule textAssignmentRule = new AssignmentRule("item[boo].text");
      textAssignmentRule.value = "boo!";
      // * item ^slicing.discriminator[0].path = "type"
      // * item ^slicing.discriminator[0].type = #value
      // * item ^slicing.rules = #open
      // * item contains boo 1..1
      // * item[boo].text 1..1
      // * item[boo].text = "boo!"
      questionnaire.rules.addAll(Arrays.asList(caretRule, dTypeRule, rulesRule, containsRule, cardRule, textCardRule, textAssignmentRule));
      AssignmentRule linkIdRule = new AssignmentRule("item[0].linkId");
      linkIdRule.value = "bar";
      AssignmentRule typeRule = new AssignmentRule("item[0].type");
      typeRule.value = new FshCode("group");
      AssignmentRule statusRule = new AssignmentRule("status");
      statusRule.value = new FshCode("active");
      // * item[0].linkId = "bar"
      // * item[0].type = #group
      // * status = #active
      Instance questionnaireInstance = new Instance("Test");
      questionnaireInstance.instanceOf = "TestQuestionnaire";
      questionnaireInstance.rules.addAll(Arrays.asList(statusRule, linkIdRule, typeRule));
      exportInstance.apply(questionnaireInstance);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(2, loggerSpy.getAllMessages("warn").size());
      assertEquals("Sliced element Questionnaire.item is being accessed via numeric index. Use slice names in rule paths when possible.", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log a warning when any element in a closed sliced array is accessed with a numeric index")
    void shouldLogAWarningWhenAnyElementInAClosedSlicedArrayIsAccessedWithANumericIndex() {
      CaretValueRule caretRule = new CaretValueRule("item");
      caretRule.caretPath = "slicing.discriminator.path";
      caretRule.value = "type";
      CaretValueRule dTypeRule = new CaretValueRule("item");
      dTypeRule.caretPath = "slicing.discriminator.type";
      dTypeRule.value = new FshCode("value");
      CaretValueRule rulesRule = new CaretValueRule("item");
      rulesRule.caretPath = "slicing.rules";
      rulesRule.value = new FshCode("closed");
      ContainsRule containsRule = new ContainsRule("item");
      containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "boo"
        }
        """)));
      CardRule cardRule = new CardRule("item[boo]");
      cardRule.min = 0.0;
      cardRule.max = "1";
      // * item ^slicing.discriminator[0].path = "type"
      // * item ^slicing.discriminator[0].type = #value
      // * item ^slicing.rules = #closed
      // * item contains boo 0..1
      questionnaire.rules.addAll(Arrays.asList(caretRule, dTypeRule, rulesRule, containsRule, cardRule));
      AssignmentRule textAssignmentRule = new AssignmentRule("item[boo].text");
      textAssignmentRule.value = "boo!";
      AssignmentRule linkIdRule = new AssignmentRule("item[0].linkId");
      linkIdRule.value = "bar";
      AssignmentRule typeRule = new AssignmentRule("item[0].type");
      typeRule.value = new FshCode("group");
      AssignmentRule statusRule = new AssignmentRule("status");
      statusRule.value = new FshCode("active");
      // * item[0].linkId = "bar"
      // * item[0].type = #group
      // * status = #active
      Instance questionnaireInstance = new Instance("Test");
      questionnaireInstance.instanceOf = "TestQuestionnaire";
      questionnaireInstance.rules.addAll(Arrays.asList(textAssignmentRule, statusRule, typeRule, linkIdRule));
      exportInstance.apply(questionnaireInstance);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(2, loggerSpy.getAllMessages("warn").size());
      assertEquals("Sliced element Questionnaire.item is being accessed via numeric index. Use slice names in rule paths when possible.", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should log a warning when a choice element has its cardinality satisfied, but an ancestor of the choice element is a named slice that is referenced numerically")
    void shouldLogAWarningWhenAChoiceElementHasItsCardinalitySatisfiedButAnAncestorOfTheC() {
      // Making an assignment rule on a required element inside the named slice forces the slice to be created when setting implied properties
      // see https://github.com/FHIR/sushi/issues/1028
      CaretValueRule caretRule = new CaretValueRule("item");
      caretRule.caretPath = "slicing.discriminator.path";
      caretRule.value = "type";
      CaretValueRule dTypeRule = new CaretValueRule("item");
      dTypeRule.caretPath = "slicing.discriminator.type";
      dTypeRule.value = new FshCode("value");
      CaretValueRule rulesRule = new CaretValueRule("item");
      rulesRule.caretPath = "slicing.rules";
      rulesRule.value = new FshCode("open");
      ContainsRule containsRule = new ContainsRule("item");
      containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "boo"
        }
        """)));
      CardRule cardRule = new CardRule("item[boo]");
      cardRule.min = 1.0;
      cardRule.max = "1";
      CardRule textCardRule = new CardRule("item[boo].text");
      textCardRule.min = 1.0;
      textCardRule.max = "1";
      AssignmentRule textAssignmentRule = new AssignmentRule("item[boo].text");
      textAssignmentRule.value = "boo!";
      // * item ^slicing.discriminator[0].path = "type"
      // * item ^slicing.discriminator[0].type = #value
      // * item ^slicing.rules = #open
      // * item contains boo 1..1
      // * item[boo].text 1..1
      // * item[boo].text = "boo!"
      questionnaire.rules.addAll(Arrays.asList(caretRule, dTypeRule, rulesRule, containsRule, cardRule, textCardRule, textAssignmentRule));
      AssignmentRule answerRule = new AssignmentRule("item[0].answerOption[0].valueString");
      answerRule.value = "foo";
      AssignmentRule linkIdRule = new AssignmentRule("item[0].linkId");
      linkIdRule.value = "bar";
      AssignmentRule typeRule = new AssignmentRule("item[0].type");
      typeRule.value = new FshCode("group");
      AssignmentRule statusRule = new AssignmentRule("status");
      statusRule.value = new FshCode("active");
      // * item[0].answerOption[0].valueString = "foo"
      // * item[0].linkId = "bar"
      // * item[0].type = #group
      // * status = #active
      Instance questionnaireInstance = new Instance("Test");
      questionnaireInstance.instanceOf = "TestQuestionnaire";
      questionnaireInstance.rules.addAll(Arrays.asList(answerRule, linkIdRule, typeRule, statusRule));
      exportInstance.apply(questionnaireInstance);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(4, loggerSpy.getAllMessages("warn").size());
      assertEquals("Element Questionnaire.item:boo.answerOption.value[x] has its cardinality satisfied by a rule that does not include the slice name. Use slice names in rule paths when possible.", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log a warning when a choice element with one type has its cardinality satisfied by a rule that includes the name of an ancestor slice")
    void shouldNotLogAWarningWhenAChoiceElementWithOneTypeHasItsCardinalitySatisfiedByARu() {
      // Profile: TestQuestionnaire
      // Parent: Questionnaire
      // * item ^slicing.discriminator[0].path = "type"
      // * item ^slicing.discriminator[0].type = #value
      // * item ^slicing.rules = #open
      // * item contains boo 0..1
      // * item[boo].answerOption.value[x] only string
      CaretValueRule slicingPath = new CaretValueRule("item");
      slicingPath.caretPath = "slicing.discriminator.path";
      slicingPath.value = "type";
      CaretValueRule slicingType = new CaretValueRule("item");
      slicingType.caretPath = "slicing.discriminator.type";
      slicingType.value = new FshCode("value");
      CaretValueRule slicingRules = new CaretValueRule("item");
      slicingRules.caretPath = "slicing.rules";
      slicingRules.value = new FshCode("open");
      ContainsRule itemContains = new ContainsRule("item");
      itemContains.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "boo"
        }
        """)));
      CardRule booCard = new CardRule("item[boo]");
      booCard.min = 0.0;
      booCard.max = "1";
      OnlyRule booOnly = new OnlyRule("item[boo].answerOption.value[x]");
      booOnly.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "string"
        }
        """))));
      questionnaire.rules.addAll(Arrays.asList(slicingPath, slicingType, slicingRules, itemContains, booCard, booOnly));
      // Instance: SurpriseQuestionnaire
      // InstanceOf: TestQuestionnaire
      // * status = #active
      // * item[0].answerOption[0].valueString = "regular"
      // * item[0].linkId = "regularLink"
      // * item[0].type = #group
      // * item[boo].answerOption[0].valueString = "boo!"
      // * item[boo].linkId = "booLink"
      // * item[boo].type = #group
      Instance questionnaireInstance = new Instance("SurpriseQuestionnaire");
      questionnaireInstance.instanceOf = "TestQuestionnaire";
      AssignmentRule status = new AssignmentRule("status");
      status.value = new FshCode("active");
      AssignmentRule regularValue = new AssignmentRule("item[0].answerOption[0].valueString");
      regularValue.value = "regular";
      AssignmentRule regularLink = new AssignmentRule("item[0].linkId");
      regularLink.value = "regularLink";
      AssignmentRule regularType = new AssignmentRule("item[0].type");
      regularType.value = new FshCode("group");
      AssignmentRule surpriseValue = new AssignmentRule("item[boo].answerOption[0].valueString");
      surpriseValue.value = "boo!";
      AssignmentRule surpriseLink = new AssignmentRule("item[boo].linkId");
      surpriseLink.value = "booLink";
      AssignmentRule surpriseType = new AssignmentRule("item[boo].type");
      surpriseType.value = new FshCode("group");
      questionnaireInstance.rules.addAll(Arrays.asList(status, regularValue, regularLink, regularType, surpriseValue, surpriseLink, surpriseType));
      exportInstance.apply(questionnaireInstance);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not log an error when a reslice element fulfills a cardinality constraint")
    void shouldNotLogAnErrorWhenAResliceElementFulfillsACardinalityConstraint() {
      // Profile: TestPatient
      // Parent: Patient
      // * identifier ^slicing.discriminator.type = #value
      // * identifier ^slicing.discriminator.path = "value"
      // * identifier ^slicing.rules = #open
      // * identifier contains ParentSlice 1..1
      // * identifier[ParentSlice] ^slicing.discriminator.type = #value
      // * identifier[ParentSlice] ^slicing.discriminator.path = "value"
      // * identifier[ParentSlice] ^slicing.rules = #open
      // * identifier[ParentSlice] contains ChildSlice 1..1
      CaretValueRule identifierSlicing = new CaretValueRule("identifier");
      identifierSlicing.caretPath = "slicing.discriminator.path";
      identifierSlicing.value = "value";
      CaretValueRule slicingType = new CaretValueRule("identifier");
      slicingType.caretPath = "slicing.discriminator.type";
      slicingType.value = new FshCode("value");
      CaretValueRule slicingRules = new CaretValueRule("identifier");
      slicingRules.caretPath = "slicing.rules";
      slicingRules.value = new FshCode("open");
      ContainsRule identifierContains = new ContainsRule("identifier");
      identifierContains.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "ParentSlice"
        }
        """)));
      CardRule parentCard = new CardRule("identifier[ParentSlice]");
      parentCard.min = 1.0;
      parentCard.max = "1";
      CaretValueRule parentSlicing = new CaretValueRule("identifier[ParentSlice]");
      parentSlicing.caretPath = "slicing.discriminator.path";
      parentSlicing.value = "value";
      CaretValueRule parentSlicingType = new CaretValueRule("identifier[ParentSlice]");
      parentSlicingType.caretPath = "slicing.discriminator.type";
      parentSlicingType.value = new FshCode("value");
      CaretValueRule parentSlicingRules = new CaretValueRule("identifier[ParentSlice]");
      parentSlicingRules.caretPath = "slicing.rules";
      parentSlicingRules.value = new FshCode("open");
      ContainsRule parentContains = new ContainsRule("identifier[ParentSlice]");
      parentContains.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "ChildSlice"
        }
        """)));
      CardRule childCard = new CardRule("identifier[ParentSlice][ChildSlice]");
      childCard.min = 1.0;
      childCard.max = "1";
      patient.rules.addAll(Arrays.asList(identifierSlicing, slicingRules, slicingType, identifierContains, parentCard, parentSlicing, parentSlicingType, parentSlicingRules, parentContains, childCard));
      // Instance: PatientInstance
      // InstanceOf: TestPatient
      // * identifier[ParentSlice][ChildSlice] = SomeIdentifier
      AssignmentRule identifierAssignment = new AssignmentRule("identifier[ParentSlice][ChildSlice]");
      identifierAssignment.isInstance = true;
      identifierAssignment.value = "SomeIdentifier";
      patientInstance.rules.add(identifierAssignment);
      // Instance: SomeIdentifier
      // InstanceOf: Identifier
      // * value = "Something"
      Instance someIdentifier = new Instance("SomeIdentifier");
      someIdentifier.instanceOf = "Identifier";
      AssignmentRule valueAssignment = new AssignmentRule("value");
      valueAssignment.value = "Something";
      someIdentifier.rules.add(valueAssignment);
      doc.instances.put(someIdentifier.name, someIdentifier);
      exportInstance.apply(patientInstance);
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should create the correct number of required elements on a resliced element")
    void shouldCreateTheCorrectNumberOfRequiredElementsOnAReslicedElement() {
      // Profile: BreadObservation
      // Parent: Observation
      // * component ^slicing.discriminator.type = #pattern
      // * component ^slicing.discriminator.path = "value"
      // * component ^slicing.rules = #open
      // * component contains Bread 0..*
      // * component[Bread].code = http://loinc.org#48018-6
      // * component[Bread] ^slicing.discriminator.type = #pattern
      // * component[Bread] ^slicing.discriminator.path = "$this"
      // * component[Bread] ^slicing.rules = #open
      // * component[Bread] contains Rye 1..1 and Wheat 1..1
      // * component[Bread][Rye].valueString 1..1
      // * component[Bread][Rye].valueString = "Rye"
      // * component[Bread][Wheat].valueString 1..1
      // * component[Bread][Wheat].valueString = "Wheat"
      Profile breadObservation = new Profile("BreadObservation");
      breadObservation.parent = "Observation";
      CaretValueRule componentSlicingType = new CaretValueRule("component");
      componentSlicingType.caretPath = "slicing.discriminator.type";
      componentSlicingType.value = new FshCode("pattern");
      CaretValueRule componentSlicingPath = new CaretValueRule("component");
      componentSlicingPath.caretPath = "slicing.discriminator.path";
      componentSlicingPath.value = "value";
      CaretValueRule componentSlicingRules = new CaretValueRule("component");
      componentSlicingRules.caretPath = "slicing.rules";
      componentSlicingRules.value = new FshCode("open");
      ContainsRule componentContains = new ContainsRule("component");
      componentContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Bread"
        }
        """))));
      CardRule breadCard = new CardRule("component[Bread]");
      breadCard.min = 0.0;
      breadCard.max = "*";
      AssignmentRule breadCode = new AssignmentRule("component[Bread].code");
      breadCode.value = new FshCode("48018-6", "http://loinc.org");
      CaretValueRule breadSlicingType = new CaretValueRule("component[Bread]");
      breadSlicingType.caretPath = "slicing.discriminator.type";
      breadSlicingType.value = new FshCode("pattern");
      CaretValueRule breadSlicingPath = new CaretValueRule("component[Bread]");
      breadSlicingPath.caretPath = "slicing.discriminator.path";
      breadSlicingPath.value = "$this";
      CaretValueRule breadSlicingRules = new CaretValueRule("component[Bread]");
      breadSlicingRules.caretPath = "slicing.rules";
      breadSlicingRules.value = new FshCode("open");
      ContainsRule breadContains = new ContainsRule("component[Bread]");
      breadContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Rye"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "Wheat"
        }
        """))));
      CardRule ryeCard = new CardRule("component[Bread][Rye]");
      ryeCard.min = 1.0;
      ryeCard.max = "1";
      CardRule wheatCard = new CardRule("component[Bread][Wheat]");
      wheatCard.min = 1.0;
      wheatCard.max = "1";
      CardRule ryeValueCard = new CardRule("component[Bread][Rye].valueString");
      ryeValueCard.min = 1.0;
      ryeValueCard.max = "1";
      AssignmentRule ryeValueString = new AssignmentRule("component[Bread][Rye].valueString");
      ryeValueString.value = "Rye";
      CardRule wheatValueCard = new CardRule("component[Bread][Wheat].valueString");
      wheatValueCard.min = 1.0;
      wheatValueCard.max = "1";
      AssignmentRule wheatValueString = new AssignmentRule("component[Bread][Wheat].valueString");
      wheatValueString.value = "Wheat";
      breadObservation.rules.addAll(Arrays.asList(componentSlicingType, componentSlicingPath, componentSlicingRules, componentContains, breadCard, breadCode, breadSlicingType, breadSlicingPath, breadSlicingRules, breadContains, ryeCard, wheatCard, ryeValueCard, ryeValueString, wheatValueCard, wheatValueString));
      doc.profiles.put(breadObservation.name, breadObservation);
      // Instance: BreadInstance
      // InstanceOf: BreadObservation
      // * status = #final
      // * code = #bread
      Instance breadInstance = new Instance("BreadInstance");
      breadInstance.instanceOf = "BreadObservation";
      AssignmentRule instanceStatus = new AssignmentRule("status");
      instanceStatus.value = new FshCode("final");
      AssignmentRule instanceCode = new AssignmentRule("code");
      instanceCode.value = new FshCode("bread");
      breadInstance.rules.addAll(Arrays.asList(instanceStatus, instanceCode));
      doc.instances.put(breadInstance.name, breadInstance);
      InstanceDefinition result = exportInstance.apply(breadInstance);
      assertEquals(2, len(at(result, "component")));
      assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Rye"
        }
        """, at(result, "component[0]"));
      assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Wheat"
        }
        """, at(result, "component[1]"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should create the correct number of required elements on a resliced element when required slices are greater than required reslices")
    void shouldCreateTheCorrectNumberOfRequiredElementsOnAReslicedElementWhenRequiredSlic() {
      // Profile: BreadObservation
      // Parent: Observation
      // * component ^slicing.discriminator.type = #pattern
      // * component ^slicing.discriminator.path = "value"
      // * component ^slicing.rules = #open
      // * component contains Bread 5..*
      // * component[Bread].code = http://loinc.org#48018-6
      // * component[Bread] ^slicing.discriminator.type = #pattern
      // * component[Bread] ^slicing.discriminator.path = "$this"
      // * component[Bread] ^slicing.rules = #open
      // * component[Bread] contains Rye 1..1 and Wheat 1..1
      // * component[Bread][Rye].valueString 1..1
      // * component[Bread][Rye].valueString = "Rye"
      // * component[Bread][Wheat].valueString 1..1
      // * component[Bread][Wheat].valueString = "Wheat"
      Profile breadObservation = new Profile("BreadObservation");
      breadObservation.parent = "Observation";
      CaretValueRule componentSlicingType = new CaretValueRule("component");
      componentSlicingType.caretPath = "slicing.discriminator.type";
      componentSlicingType.value = new FshCode("pattern");
      CaretValueRule componentSlicingPath = new CaretValueRule("component");
      componentSlicingPath.caretPath = "slicing.discriminator.path";
      componentSlicingPath.value = "value";
      CaretValueRule componentSlicingRules = new CaretValueRule("component");
      componentSlicingRules.caretPath = "slicing.rules";
      componentSlicingRules.value = new FshCode("open");
      ContainsRule componentContains = new ContainsRule("component");
      componentContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Bread"
        }
        """))));
      CardRule breadCard = new CardRule("component[Bread]");
      breadCard.min = 5.0;
      breadCard.max = "*";
      AssignmentRule breadCode = new AssignmentRule("component[Bread].code");
      breadCode.value = new FshCode("48018-6", "http://loinc.org");
      CaretValueRule breadSlicingType = new CaretValueRule("component[Bread]");
      breadSlicingType.caretPath = "slicing.discriminator.type";
      breadSlicingType.value = new FshCode("pattern");
      CaretValueRule breadSlicingPath = new CaretValueRule("component[Bread]");
      breadSlicingPath.caretPath = "slicing.discriminator.path";
      breadSlicingPath.value = "$this";
      CaretValueRule breadSlicingRules = new CaretValueRule("component[Bread]");
      breadSlicingRules.caretPath = "slicing.rules";
      breadSlicingRules.value = new FshCode("open");
      ContainsRule breadContains = new ContainsRule("component[Bread]");
      breadContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Rye"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "Wheat"
        }
        """))));
      CardRule ryeCard = new CardRule("component[Bread][Rye]");
      ryeCard.min = 1.0;
      ryeCard.max = "1";
      CardRule wheatCard = new CardRule("component[Bread][Wheat]");
      wheatCard.min = 1.0;
      wheatCard.max = "1";
      CardRule ryeValueCard = new CardRule("component[Bread][Rye].valueString");
      ryeValueCard.min = 1.0;
      ryeValueCard.max = "1";
      AssignmentRule ryeValueString = new AssignmentRule("component[Bread][Rye].valueString");
      ryeValueString.value = "Rye";
      CardRule wheatValueCard = new CardRule("component[Bread][Wheat].valueString");
      wheatValueCard.min = 1.0;
      wheatValueCard.max = "1";
      AssignmentRule wheatValueString = new AssignmentRule("component[Bread][Wheat].valueString");
      wheatValueString.value = "Wheat";
      breadObservation.rules.addAll(Arrays.asList(componentSlicingType, componentSlicingPath, componentSlicingRules, componentContains, breadCard, breadCode, breadSlicingType, breadSlicingPath, breadSlicingRules, breadContains, ryeCard, wheatCard, ryeValueCard, ryeValueString, wheatValueCard, wheatValueString));
      doc.profiles.put(breadObservation.name, breadObservation);
      // Instance: BreadInstance
      // InstanceOf: BreadObservation
      // * status = #final
      // * code = #bread
      Instance breadInstance = new Instance("BreadInstance");
      breadInstance.instanceOf = "BreadObservation";
      AssignmentRule instanceStatus = new AssignmentRule("status");
      instanceStatus.value = new FshCode("final");
      AssignmentRule instanceCode = new AssignmentRule("code");
      instanceCode.value = new FshCode("bread");
      breadInstance.rules.addAll(Arrays.asList(instanceStatus, instanceCode));
      doc.instances.put(breadInstance.name, breadInstance);
      InstanceDefinition result = exportInstance.apply(breadInstance);
      assertEquals(5, len(at(result, "component")));
      Object expectedBreadSlice = JsAssert.json("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          }
        }
        """);
      assertJsEqual(expectedBreadSlice, at(result, "component[0]"));
      assertJsEqual(expectedBreadSlice, at(result, "component[1]"));
      assertJsEqual(expectedBreadSlice, at(result, "component[2]"));
      assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Rye"
        }
        """, at(result, "component[3]"));
      assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Wheat"
        }
        """, at(result, "component[4]"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should create the correct number of required elements on a resliced element when required elements are greater than required slices and reslices")
    void shouldCreateTheCorrectNumberOfRequiredElementsOnAReslicedElementWhenRequiredElem() {
      // Profile: BreadObservation
      // Parent: Observation
      // * component 4..*
      // * component ^slicing.discriminator.type = #pattern
      // * component ^slicing.discriminator.path = "value"
      // * component ^slicing.rules = #open
      // * component contains Bread 3..*
      // * component[Bread].code = http://loinc.org#48018-6
      // * component[Bread] ^slicing.discriminator.type = #pattern
      // * component[Bread] ^slicing.discriminator.path = "$this"
      // * component[Bread] ^slicing.rules = #open
      // * component[Bread] contains Rye 1..1 and Wheat 1..1
      // * component[Bread][Rye].valueString 1..1
      // * component[Bread][Rye].valueString = "Rye"
      // * component[Bread][Wheat].valueString 1..1
      // * component[Bread][Wheat].valueString = "Wheat"
      Profile breadObservation = new Profile("BreadObservation");
      breadObservation.parent = "Observation";
      CardRule componentCardRule = new CardRule("component");
      componentCardRule.min = 4.0;
      componentCardRule.max = "*";
      CaretValueRule componentSlicingType = new CaretValueRule("component");
      componentSlicingType.caretPath = "slicing.discriminator.type";
      componentSlicingType.value = new FshCode("pattern");
      CaretValueRule componentSlicingPath = new CaretValueRule("component");
      componentSlicingPath.caretPath = "slicing.discriminator.path";
      componentSlicingPath.value = "value";
      CaretValueRule componentSlicingRules = new CaretValueRule("component");
      componentSlicingRules.caretPath = "slicing.rules";
      componentSlicingRules.value = new FshCode("open");
      ContainsRule componentContains = new ContainsRule("component");
      componentContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Bread"
        }
        """))));
      CardRule breadCard = new CardRule("component[Bread]");
      breadCard.min = 3.0;
      breadCard.max = "*";
      AssignmentRule breadCode = new AssignmentRule("component[Bread].code");
      breadCode.value = new FshCode("48018-6", "http://loinc.org");
      CaretValueRule breadSlicingType = new CaretValueRule("component[Bread]");
      breadSlicingType.caretPath = "slicing.discriminator.type";
      breadSlicingType.value = new FshCode("pattern");
      CaretValueRule breadSlicingPath = new CaretValueRule("component[Bread]");
      breadSlicingPath.caretPath = "slicing.discriminator.path";
      breadSlicingPath.value = "$this";
      CaretValueRule breadSlicingRules = new CaretValueRule("component[Bread]");
      breadSlicingRules.caretPath = "slicing.rules";
      breadSlicingRules.value = new FshCode("open");
      ContainsRule breadContains = new ContainsRule("component[Bread]");
      breadContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "Rye"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "Wheat"
        }
        """))));
      CardRule ryeCard = new CardRule("component[Bread][Rye]");
      ryeCard.min = 1.0;
      ryeCard.max = "1";
      CardRule wheatCard = new CardRule("component[Bread][Wheat]");
      wheatCard.min = 1.0;
      wheatCard.max = "1";
      CardRule ryeValueCard = new CardRule("component[Bread][Rye].valueString");
      ryeValueCard.min = 1.0;
      ryeValueCard.max = "1";
      AssignmentRule ryeValueString = new AssignmentRule("component[Bread][Rye].valueString");
      ryeValueString.value = "Rye";
      CardRule wheatValueCard = new CardRule("component[Bread][Wheat].valueString");
      wheatValueCard.min = 1.0;
      wheatValueCard.max = "1";
      AssignmentRule wheatValueString = new AssignmentRule("component[Bread][Wheat].valueString");
      wheatValueString.value = "Wheat";
      breadObservation.rules.addAll(Arrays.asList(componentCardRule, componentSlicingType, componentSlicingPath, componentSlicingRules, componentContains, breadCard, breadCode, breadSlicingType, breadSlicingPath, breadSlicingRules, breadContains, ryeCard, wheatCard, ryeValueCard, ryeValueString, wheatValueCard, wheatValueString));
      doc.profiles.put(breadObservation.name, breadObservation);
      // Instance: BreadInstance
      // InstanceOf: BreadObservation
      // * status = #final
      // * code = #bread
      // * component[3].code = #apple
      Instance breadInstance = new Instance("BreadInstance");
      breadInstance.instanceOf = "BreadObservation";
      AssignmentRule instanceStatus = new AssignmentRule("status");
      instanceStatus.value = new FshCode("final");
      AssignmentRule instanceCode = new AssignmentRule("code");
      instanceCode.value = new FshCode("bread");
      AssignmentRule instanceComponent = new AssignmentRule("component[3].code");
      instanceComponent.value = new FshCode("apple");
      breadInstance.rules.addAll(Arrays.asList(instanceStatus, instanceCode, instanceComponent));
      doc.instances.put(breadInstance.name, breadInstance);
      InstanceDefinition result = exportInstance.apply(breadInstance);
      assertEquals(4, len(at(result, "component")));
      // Bread slice
      assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          }
        }
        """, at(result, "component[0]"));
      // Bread/Rye reslice
      assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Rye"
        }
        """, at(result, "component[1]"));
      // Bread/Wheat reslice
      assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "48018-6",
                "system": "http://loinc.org"
              }
            ]
          },
          "valueString": "Wheat"
        }
        """, at(result, "component[2]"));
      // New fixed value that is not a bread slice or reslice
      assertJsonEqual("""
        {
          "code": {
            "coding": [
              {
                "code": "apple"
              }
            ]
          }
        }
        """, at(result, "component[3]"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not assign a value which violates a closed child slicing")
    void shouldNotAssignAValueWhichViolatesAClosedChildSlicing() {
      // Profile: MyProfile
      // Parent: Observation
      // * category.coding ^slicing.discriminator.type = #value
      // * category.coding ^slicing.discriminator.path = "coding"
      // * category.coding ^slicing.rules = #closed
      // * category.coding contains slice1 0..1
      // * category.coding[slice1] = http://example.com#foo
      Profile profile = new Profile("MyProfile");
      profile.parent = "Observation";
      CaretValueRule typeRule = new CaretValueRule("category.coding");
      typeRule.caretPath = "slicing.discriminator[0].type";
      typeRule.value = new FshCode("pattern");
      CaretValueRule pathRule = new CaretValueRule("category.coding");
      pathRule.caretPath = "slicing.discriminator[0].path";
      pathRule.value = "code";
      CaretValueRule rulesRule = new CaretValueRule("category.coding");
      rulesRule.caretPath = "slicing.rules";
      rulesRule.value = new FshCode("closed");
      ContainsRule containsRule = new ContainsRule("category.coding");
      containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "slice1"
        }
        """)));
      AssignmentRule assignmentRule = new AssignmentRule("category.coding[slice1]");
      assignmentRule.value = new FshCode("foo", "http://example.com");
      profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, assignmentRule));
      doc.profiles.put(profile.name, profile);
      // Instance: MyInstance
      // InstanceOf: MyProfile
      // * category[0] = http://not-example.org#bar
      Instance instance = new Instance("MyInstance");
      instance.instanceOf = "MyProfile";
      AssignmentRule categoryRule = new AssignmentRule("category[0]");
      categoryRule.value = new FshCode("bar", "http://not-example.com");
      instance.rules.add(categoryRule);
      doc.instances.put(instance.name, instance);
      sdExporter.export();
      List<InstanceDefinition> exported = exporter.export().instances;
      InstanceDefinition exportedInstance = exported.stream().filter((i) -> Objects.equals(i._instanceMeta.name, "MyInstance")).findFirst().orElse(null);
      assertNull(at(exportedInstance, "category"));
      assertContainsEqual(JsAssert.json("\"Cannot assign {\\\"coding\\\":[{\\\"code\\\":\\\"bar\\\",\\\"system\\\":\\\"http://not-example.com\\\"}]} to this element since it conflicts with all values of the closed slicing.\""), loggerSpy.getAllMessages("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a value which does not violate all elements of a closed child slicing")
    void shouldAssignAValueWhichDoesNotViolateAllElementsOfAClosedChildSlicing() {
      // Profile: MyProfile
      // Parent: Observation
      // * category.coding ^slicing.discriminator.type = #value
      // * category.coding ^slicing.discriminator.path = "coding"
      // * category.coding ^slicing.rules = #closed
      // * category.coding contains slice1 0..1 and slice2 0..1
      // * category.coding[slice1] = http://example.com#foo
      // * category.coding[slice2] = http://example.com#bar
      Profile profile = new Profile("MyProfile");
      profile.parent = "Observation";
      CaretValueRule typeRule = new CaretValueRule("category.coding");
      typeRule.caretPath = "slicing.discriminator[0].type";
      typeRule.value = new FshCode("pattern");
      CaretValueRule pathRule = new CaretValueRule("category.coding");
      pathRule.caretPath = "slicing.discriminator[0].path";
      pathRule.value = "code";
      CaretValueRule rulesRule = new CaretValueRule("category.coding");
      rulesRule.caretPath = "slicing.rules";
      rulesRule.value = new FshCode("closed");
      ContainsRule containsRule = new ContainsRule("category.coding");
      containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "slice1"
        }
        """)));
      containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "slice2"
        }
        """)));
      AssignmentRule assignmentRule = new AssignmentRule("category.coding[slice1]");
      assignmentRule.value = new FshCode("foo", "http://example.com");
      AssignmentRule assignmentRule2 = new AssignmentRule("category.coding[slice2]");
      assignmentRule2.value = new FshCode("bar", "http://example.com");
      profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, assignmentRule, assignmentRule2));
      doc.profiles.put(profile.name, profile);
      // Instance: MyInstance
      // InstanceOf: MyProfile
      // * category[0] = http://example#bar
      Instance instance = new Instance("MyInstance");
      instance.instanceOf = "MyProfile";
      AssignmentRule categoryRule = new AssignmentRule("category[0]");
      categoryRule.value = new FshCode("bar", "http://example.com");
      instance.rules.add(categoryRule);
      doc.instances.put(instance.name, instance);
      sdExporter.export();
      List<InstanceDefinition> exported = exporter.export().instances;
      InstanceDefinition exportedInstance = exported.stream().filter((i) -> Objects.equals(i._instanceMeta.name, "MyInstance")).findFirst().orElse(null);
      assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "bar",
                "system": "http://example.com"
              }
            ]
          }
        ]
        """, at(exportedInstance, "category"));
      assertNotContainsEqual(JsAssert.json("\"Cannot assign {\\\"coding\\\":[{\\\"code\\\":\\\"bar\\\",\\\"system\\\":\\\"http://not-example.com\\\"}]} to this element since it conflicts with all values of the closed slicing.\""), loggerSpy.getAllMessages("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should assign a value which violates an open child slicing")
    void shouldAssignAValueWhichViolatesAnOpenChildSlicing() {
      // Profile: MyProfile
      // Parent: Observation
      // * category.coding ^slicing.discriminator.type = #value
      // * category.coding ^slicing.discriminator.path = "coding"
      // * category.coding ^slicing.rules = #open
      // * category.coding contains slice1 0..1
      // * category.coding[slice1] = http://example.com#foo
      Profile profile = new Profile("MyProfile");
      profile.parent = "Observation";
      CaretValueRule typeRule = new CaretValueRule("category.coding");
      typeRule.caretPath = "slicing.discriminator[0].type";
      typeRule.value = new FshCode("pattern");
      CaretValueRule pathRule = new CaretValueRule("category.coding");
      pathRule.caretPath = "slicing.discriminator[0].path";
      pathRule.value = "code";
      CaretValueRule rulesRule = new CaretValueRule("category.coding");
      rulesRule.caretPath = "slicing.rules";
      rulesRule.value = new FshCode("open");
      ContainsRule containsRule = new ContainsRule("category.coding");
      containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "slice1"
        }
        """)));
      AssignmentRule assignmentRule = new AssignmentRule("category.coding[slice1]");
      assignmentRule.value = new FshCode("foo", "http://example.com");
      profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, assignmentRule));
      doc.profiles.put(profile.name, profile);
      // Instance: MyInstance
      // InstanceOf: MyProfile
      // * category[0] = http://not-example.org#bar
      Instance instance = new Instance("MyInstance");
      instance.instanceOf = "MyProfile";
      AssignmentRule categoryRule = new AssignmentRule("category[0]");
      categoryRule.value = new FshCode("bar", "http://not-example.com");
      instance.rules.add(categoryRule);
      doc.instances.put(instance.name, instance);
      sdExporter.export();
      List<InstanceDefinition> exported = exporter.export().instances;
      InstanceDefinition exportedInstance = exported.stream().filter((i) -> Objects.equals(i._instanceMeta.name, "MyInstance")).findFirst().orElse(null);
      assertJsonEqual("""
        [
          {
            "coding": [
              {
                "code": "bar",
                "system": "http://not-example.com"
              }
            ]
          }
        ]
        """, at(exportedInstance, "category"));
      assertNotContainsEqual(JsAssert.json("\"Cannot assign {\\\"coding\\\":[{\\\"code\\\":\\\"bar\\\",\\\"system\\\":\\\"http://not-example.com\\\"}]} to this element since it conflicts with all values of the closed slicing.\""), loggerSpy.getAllMessages("error"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should overwrite optional slice values when a numeric index refers to a slice before the end of a path")
    void shouldOverwriteOptionalSliceValuesWhenANumericIndexRefersToASliceBeforeTheEndOfA() {
      // Profile: MyTestPatient
      // Parent: Patient
      // * identifier ^slicing.discriminator.type = #pattern
      // * identifier ^slicing.discriminator.path = "system"
      // * identifier ^slicing.rules = #open
      // * identifier contains myid 0..1
      // * identifier[myid].system = "http://myexample.org/identifier/myid"
      Profile profile = new Profile("MyTestPatient");
      profile.parent = "Patient";
      CaretValueRule typeRule = new CaretValueRule("identifier");
      typeRule.caretPath = "slicing.discriminator[0].type";
      typeRule.value = new FshCode("pattern");
      CaretValueRule pathRule = new CaretValueRule("identifier");
      pathRule.caretPath = "slicing.discriminator[0].path";
      pathRule.value = "system";
      CaretValueRule rulesRule = new CaretValueRule("identifier");
      rulesRule.caretPath = "slicing.rules";
      rulesRule.value = new FshCode("open");
      ContainsRule containsRule = new ContainsRule("identifier");
      containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "myid"
        }
        """)));
      AssignmentRule assignmentRule = new AssignmentRule("identifier[myid].system");
      assignmentRule.value = "http://myexample.org/identifier/myid";
      profile.rules.addAll(Arrays.asList(typeRule, pathRule, rulesRule, containsRule, assignmentRule));
      doc.profiles.put(profile.name, profile);
      // Instance: MyTestPatientExample
      // InstanceOf: MyTestPatient
      // Usage: #example
      // * identifier[myid].value = "123456"
      // * identifier[0].system = "http://example.org/mrn"
      // * identifier[0].value = "abc"
      // * identifier[1].system = "http://anotherexample.org/mrn"
      // * identifier[1].value = "123"
      Instance instance = new Instance("MyTestPatientExample");
      instance.instanceOf = "MyTestPatient";
      instance.usage = InstanceUsage.Example;
      AssignmentRule myidValue = new AssignmentRule("identifier[myid].value");
      myidValue.value = "123456";
      AssignmentRule firstSystem = new AssignmentRule("identifier[0].system");
      firstSystem.value = "http://example.org/mrn";
      AssignmentRule firstValue = new AssignmentRule("identifier[0].value");
      firstValue.value = "abc";
      AssignmentRule secondSystem = new AssignmentRule("identifier[1].system");
      secondSystem.value = "http://anotherexample.org/mrn";
      AssignmentRule secondValue = new AssignmentRule("identifier[1].value");
      secondValue.value = "123";
      instance.rules.addAll(Arrays.asList(myidValue, firstSystem, firstValue, secondSystem, secondValue));
      doc.instances.put(instance.name, instance);
      sdExporter.export();
      List<InstanceDefinition> exported = exporter.export().instances;
      InstanceDefinition exportedInstance = exported.stream().filter((i) -> Objects.equals(i._instanceMeta.name, "MyTestPatientExample")).findFirst().orElse(null);
      assertJsonEqual("""
        [
          {
            "system": "http://example.org/mrn",
            "value": "abc"
          },
          {
            "system": "http://anotherexample.org/mrn",
            "value": "123"
          }
        ]
        """, at(exportedInstance, "identifier"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should only export an instance once")
    void shouldOnlyExportAnInstanceOnce() {
      Instance bundleInstance = new Instance("MyBundle");
      bundleInstance.instanceOf = "Bundle";
      AssignmentRule inlineRule = new AssignmentRule("entry[0].resource");
      inlineRule.value = "MyBundledPatient";
      inlineRule.isInstance = true;
      bundleInstance.rules.add(inlineRule);
      doc.instances.put(bundleInstance.name, bundleInstance);
      Instance inlineInstance = new Instance("MyBundledPatient");
      inlineInstance.instanceOf = "Patient";
      AssignmentRule assignedValRule = new AssignmentRule("active");
      assignedValRule.value = true;
      inlineInstance.rules.add(assignedValRule);
      doc.instances.put(inlineInstance.name, inlineInstance);
      List<InstanceDefinition> exported = exporter.export().instances;
      List<Object> exportedBundle = filter(exported, (i) -> Objects.equals(((InstanceDefinition) i)._instanceMeta.name, "MyBundle"));
      List<Object> exportedBundledPatient = filter(exported, (i) -> Objects.equals(((InstanceDefinition) i)._instanceMeta.name, "MyBundledPatient"));
      assertEquals(1, exportedBundle.size());
      assertEquals(1, exportedBundledPatient.size());
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should only add optional children of list elements and the implied elements of those children to entries in the list that assign values on those children")
    void shouldOnlyAddOptionalChildrenOfListElementsAndTheImpliedElementsOfThoseChildrenT() {
      // * generalPractitioner only Reference(Practitioner | Organization)
      OnlyRule onlyRule = new OnlyRule("generalPractitioner");
      onlyRule.types.addAll(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "Practitioner",
          "isReference": true
        }
        """)), onlyRuleType(JsAssert.json("""
        {
          "type": "Organization",
          "isReference": true
        }
        """))));
      // * generalPractitioner.extension contains
      //   http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName named mothers-maiden-name 0..1
      ContainsRule containsRule = new ContainsRule("generalPractitioner.extension");
      containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "mothers-maiden-name",
          "type": "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName"
        }
        """)));
      CardRule extensionCard = new CardRule("generalPractitioner.extension[mothers-maiden-name]");
      extensionCard.min = 0.0;
      extensionCard.max = "1";
      patient.rules.addAll(Arrays.asList(containsRule, extensionCard));
      // * generalPractitioner[0] = Reference(my-doctor)
      AssignmentRule gp = new AssignmentRule("generalPractitioner[0]");
      gp.value = new FshReference("my-doctor");
      // * generalPractitioner[1] = Reference(gp-org1)
      AssignmentRule gpOrg = new AssignmentRule("generalPractitioner[1]");
      gpOrg.value = new FshReference("gp-org1");
      // * generalPractitioner[1].extension[mothers-maiden-name].valueString = "Belnades"
      AssignmentRule directValue = new AssignmentRule("generalPractitioner[1].extension[mothers-maiden-name].valueString");
      directValue.value = "Belnades";
      patientInstance.rules.addAll(Arrays.asList(gp, gpOrg, directValue));
      sdExporter.export();
      InstanceDefinition result = exportInstance.apply(patientInstance);
      assertEquals(2, len(at(result, "generalPractitioner")));
      assertJsonEqual("""
        {
          "reference": "my-doctor"
        }
        """, at(result, "generalPractitioner[0]"));
      assertNull(at(result, "generalPractitioner[0].extension"));
      assertJsonEqual("""
        {
          "reference": "gp-org1",
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName",
              "valueString": "Belnades"
            }
          ]
        }
        """, at(result, "generalPractitioner[1]"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should set optional extensions on array elements with 1..* card as assigned without implying additional optional extensions")
    void shouldSetOptionalExtensionsOnArrayElementsWith1CardAsAssignedWithoutImplyingAddi() {
      // * generalPractitioner only Reference(Practitioner | Organization)
      OnlyRule onlyRule = new OnlyRule("generalPractitioner");
      onlyRule.types.addAll(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "Practitioner",
          "isReference": true
        }
        """)), onlyRuleType(JsAssert.json("""
        {
          "type": "Organization",
          "isReference": true
        }
        """))));
      // * generalPractitioner 1..*
      CardRule generalPractitionerCard = new CardRule("generalPractitioner.extension[mothers-maiden-name]");
      generalPractitionerCard.min = 0.0;
      generalPractitionerCard.max = "1";
      // * generalPractitioner.extension contains
      //   http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName named mothers-maiden-name 0..1
      ContainsRule containsRule = new ContainsRule("generalPractitioner.extension");
      containsRule.items.addAll(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "mothers-maiden-name",
          "type": "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName"
        }
        """)), containsRuleItem(JsAssert.json("""
        {
          "name": "fmm",
          "type": "http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm"
        }
        """))));
      CardRule extensionCard = new CardRule("generalPractitioner.extension[mothers-maiden-name]");
      extensionCard.min = 0.0;
      extensionCard.max = "1";
      CardRule fmmCard = new CardRule("generalPractitioner.extension[fmm]");
      fmmCard.min = 0.0;
      fmmCard.max = "1";
      patient.rules.addAll(Arrays.asList(generalPractitionerCard, containsRule, extensionCard, fmmCard));
      // * generalPractitioner[0] = Reference(my-doctor)
      AssignmentRule gp = new AssignmentRule("generalPractitioner[0]");
      gp.value = new FshReference("my-doctor");
      // * generalPractitioner[0].extension[fmm].valueInteger = 10
      AssignmentRule fmm0value = new AssignmentRule("generalPractitioner[0].extension[fmm].valueInteger");
      fmm0value.value = 10.0;
      // * generalPractitioner[1] = Reference(gp-org1)
      AssignmentRule gpOrg = new AssignmentRule("generalPractitioner[1]");
      gpOrg.value = new FshReference("gp-org1");
      // * generalPractitioner[1].extension[mothers-maiden-name].valueString = "Belnades"
      AssignmentRule directValue = new AssignmentRule("generalPractitioner[1].extension[mothers-maiden-name].valueString");
      directValue.value = "Belnades";
      // * generalPractitioner[1].extension[fmm].valueInteger = 99
      AssignmentRule fmm1value = new AssignmentRule("generalPractitioner[1].extension[fmm].valueInteger");
      fmm1value.value = 99.0;
      patientInstance.rules.addAll(Arrays.asList(gp, fmm0value, gpOrg, directValue, fmm1value));
      sdExporter.export();
      InstanceDefinition result = exportInstance.apply(patientInstance);
      assertEquals(2, len(at(result, "generalPractitioner")));
      assertJsonEqual("""
        {
          "reference": "my-doctor",
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm",
              "valueInteger": 10
            }
          ]
        }
        """, at(result, "generalPractitioner[0]"));
      // expect(result.generalPractitioner[0].extension).toBeUndefined();
      assertJsonEqual("""
        {
          "reference": "gp-org1",
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName",
              "valueString": "Belnades"
            },
            {
              "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-fmm",
              "valueInteger": 99
            }
          ]
        }
        """, at(result, "generalPractitioner[1]"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should handle extensions on non-zero element of primitive arrays")
    void shouldHandleExtensionsOnNonZeroElementOfPrimitiveArrays() {
      // * address.line.extension contains
      //     http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-additionalLocator named locator 0..1
      ContainsRule containsRule = new ContainsRule("address.line.extension");
      containsRule.items.add(containsRuleItem(JsAssert.json("""
        {
          "name": "locator",
          "type": "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-additionalLocator"
        }
        """)));
      CardRule extensionCard = new CardRule("address.line.extension[locator]");
      extensionCard.min = 0.0;
      extensionCard.max = "1";
      patient.rules.addAll(Arrays.asList(containsRule, extensionCard));
      // * address.line[1].extension[locator] = "3rd floor"
      AssignmentRule loc = new AssignmentRule("address.line[1].extension[locator].valueString");
      loc.value = "3rd Floor";
      patientInstance.rules.add(loc);
      // In-memory result should be correct
      InstanceDefinition result = exportInstance.apply(patientInstance);
      assertEquals(1, len(at(result, "address")));
      assertJsonEqual("""
        [
          null,
          null
        ]
        """, at(result, "address[0].line"));
      assertEquals(2, len(at(result, "address[0]._line")));
      assertNull(at(result, "address[0]._line[0]"));
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-additionalLocator",
            "valueString": "3rd Floor"
          }
        ]
        """, at(result, "address[0]._line[1].extension"));
      // JSON representation should be correct
      Object json = result.toJSON();
      assertEquals(1, len(at(json, "address")));
      assertJsonEqual("""
        [
          null,
          null
        ]
        """, at(json, "address[0].line"));
      assertEquals(2, len(at(json, "address[0]._line")));
      assertNull(at(json, "address[0]._line[0]"));
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-additionalLocator",
            "valueString": "3rd Floor"
          }
        ]
        """, at(json, "address[0]._line[1].extension"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should keep additional values assigned directly on a sibling path before assigning a value with Reference()")
    void shouldKeepAdditionalValuesAssignedDirectlyOnASiblingPathBeforeAssigningAValueWit() {
      Instance observationInstance = new Instance("MyObs");
      observationInstance.instanceOf = "Observation";
      AssignmentRule identifierValueRule = new AssignmentRule("subject.identifier.value");
      identifierValueRule.value = "foo";
      observationInstance.rules.add(identifierValueRule);
      AssignmentRule referenceRule = new AssignmentRule("subject");
      referenceRule.value = new FshReference("Bar");
      observationInstance.rules.add(referenceRule);
      doc.instances.put(observationInstance.name, observationInstance);
      InstanceDefinition result = exportInstance.apply(observationInstance);
      assertJsonEqual("\"Patient/Bar\"", at(result, "subject.reference"));
      assertJsonEqual("""
        {
          "value": "foo"
        }
        """, at(result, "subject.identifier"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should keep additional values assigned directly on a sibling but prefer later values when assigning a value with Reference()")
    void shouldKeepAdditionalValuesAssignedDirectlyOnASiblingButPreferLaterValuesWhenAssi() {
      Instance observationInstance = new Instance("MyObs");
      observationInstance.instanceOf = "Observation";
      AssignmentRule firstReferenceRule = new AssignmentRule("subject.reference");
      firstReferenceRule.value = "Patient/TooEarly";
      observationInstance.rules.add(firstReferenceRule);
      AssignmentRule secondReferenceRule = new AssignmentRule("subject");
      secondReferenceRule.value = new FshReference("Bar");
      observationInstance.rules.add(secondReferenceRule);
      doc.instances.put(observationInstance.name, observationInstance);
      InstanceDefinition result = exportInstance.apply(observationInstance);
      assertJsonEqual("""
        {
          "reference": "Patient/Bar"
        }
        """, at(result, "subject"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not allow path rules to be used to define a specific order of items in an array in classic slicing mode")
    void shouldNotAllowPathRulesToBeUsedToDefineASpecificOrderOfItemsInAnArrayInClassicSl() {
      Profile labObservation = new Profile("LabObservation");
      labObservation.parent = "Observation";
      // * category ^slicing.discriminator.type = #pattern
      // * category ^slicing.discriminator.path = "$this"
      // * category ^slicing.rules = #open
      // * category contains lab 1..1
      // * category[lab] = http://terminology.hl7.org/CodeSystem/observation-category#laboratory
      CaretValueRule discriminatorType = new CaretValueRule("category");
      discriminatorType.caretPath = "slicing.discriminator.type";
      discriminatorType.value = new FshCode("pattern");
      CaretValueRule discriminatorPath = new CaretValueRule("category");
      discriminatorPath.caretPath = "slicing.discriminator.path";
      discriminatorPath.value = "$this";
      CaretValueRule slicingRules = new CaretValueRule("category");
      slicingRules.caretPath = "slicing.rules";
      slicingRules.value = new FshCode("open");
      ContainsRule labContains = new ContainsRule("category");
      labContains.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "lab"
        }
        """))));
      CardRule labCard = new CardRule("category[lab]");
      labCard.min = 1.0;
      labCard.max = "1";
      AssignmentRule labAssignment = new AssignmentRule("category[lab]");
      labAssignment.value = new FshCode("laboratory", "http://terminology.hl7.org/CodeSystem/observation-category");
      labObservation.rules.addAll(Arrays.asList(discriminatorType, discriminatorPath, slicingRules, labContains, labCard, labAssignment));
      doc.profiles.put(labObservation.name, labObservation);
      Instance labInstance = new Instance("LabActivityObservation");
      labInstance.instanceOf = "LabObservation";
      // * category[lab]
      // * category[+] = http://terminology.hl7.org/CodeSystem/observation-category#activity "Activity"
      // * status = #final
      // * code = #wow
      PathRule labPathRule = new PathRule("category[lab]");
      AssignmentRule activityAssignmentRule = new AssignmentRule("category[+]");
      activityAssignmentRule.value = new FshCode("activity", "http://terminology.hl7.org/CodeSystem/observation-category", "Activity");
      AssignmentRule statusAssignmentRule = new AssignmentRule("status");
      statusAssignmentRule.value = new FshCode("final");
      AssignmentRule codeAssignmentRule = new AssignmentRule("code");
      codeAssignmentRule.value = new FshCode("wow");
      labInstance.rules.addAll(Arrays.asList(labPathRule, activityAssignmentRule, statusAssignmentRule, codeAssignmentRule));
      InstanceDefinition exported = exportInstance.apply(labInstance);
      // In classic slicing mode, soft index [+] activity slice overwrites the laboratory slice
      assertEquals(1, len(at(exported, "category")));
      assertJsonEqual("""
        {
          "coding": [
            {
              "code": "activity",
              "system": "http://terminology.hl7.org/CodeSystem/observation-category",
              "display": "Activity"
            }
          ]
        }
        """, at(exported, "category[0]"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("sliced element Observation.category is being accessed via numeric index", "i", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should add assigned values of optional elements when a path rule is used")
    void shouldAddAssignedValuesOfOptionalElementsWhenAPathRuleIsUsed() {
      // Profile: OptionalProfile
      // Parent: Observation
      // * dataAbsentReason = #unknown
      Profile optionalProfile = new Profile("OptionalProfile");
      optionalProfile.parent = "Observation";
      AssignmentRule dataAbsentReasonAssignmentRule = new AssignmentRule("dataAbsentReason");
      dataAbsentReasonAssignmentRule.value = new FshCode("unknown");
      optionalProfile.rules.add(dataAbsentReasonAssignmentRule);
      doc.profiles.put(optionalProfile.name, optionalProfile);
      // Instance: NoPathRule
      // InstanceOf: OptionalProfile
      // * code = #test-code
      // * status = #final
      Instance noPathRuleInstance = new Instance("NoPathRule");
      noPathRuleInstance.instanceOf = "OptionalProfile";
      AssignmentRule codeAssignmentRule = new AssignmentRule("code");
      codeAssignmentRule.value = new FshCode("test-code");
      AssignmentRule statusAssignmentRule = new AssignmentRule("status");
      statusAssignmentRule.value = new FshCode("final");
      noPathRuleInstance.rules.addAll(Arrays.asList(codeAssignmentRule, statusAssignmentRule));
      // Instance: WithPathRule
      // InstanceOf: OptionalProfile
      // * code = #test-code
      // * status = #final
      // * dataAbsentReason
      Instance withPathRuleInstance = new Instance("WithPathRule");
      withPathRuleInstance.instanceOf = "OptionalProfile";
      PathRule dataAbsentReasonPathRule = new PathRule("dataAbsentReason");
      withPathRuleInstance.rules.addAll(Arrays.asList(codeAssignmentRule, statusAssignmentRule, dataAbsentReasonPathRule));
      InstanceDefinition exportedNoPathRule = exportInstance.apply(noPathRuleInstance);
      InstanceDefinition exportedPathRule = exportInstance.apply(withPathRuleInstance);
      assertNull(at(exportedNoPathRule, "dataAbsentReason"));
      assertJsonEqual("""
        {
          "coding": [
            {
              "code": "unknown"
            }
          ]
        }
        """, at(exportedPathRule, "dataAbsentReason"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should add assigned values of required children of optional element when a path rule is used")
    void shouldAddAssignedValuesOfRequiredChildrenOfOptionalElementWhenAPathRuleIsUsed() {
      // Profile: OptionalProfile
      // Parent: Observation
      // * identifier.use 1..1
      // * identifier.use = #official
      // * identifier.system 1..1
      // * identifier.system = "http://example.org/my-real-identifiers"
      // NOTE - identifier is 0..*
      Profile optionalProfile = new Profile("OptionalProfile");
      optionalProfile.parent = "Observation";
      CardRule identifierUseCardRule = new CardRule("identifier.use");
      identifierUseCardRule.min = 1.0;
      identifierUseCardRule.max = "1";
      AssignmentRule identifierUseAssignmentRule = new AssignmentRule("identifier.use");
      identifierUseAssignmentRule.value = new FshCode("official");
      CardRule identifierSystemCardRule = new CardRule("identifier.system");
      identifierSystemCardRule.min = 1.0;
      identifierSystemCardRule.max = "1";
      AssignmentRule identifierSystemAssignmentRule = new AssignmentRule("identifier.system");
      identifierSystemAssignmentRule.value = "http://example.org/my-real-identifiers";
      optionalProfile.rules.addAll(Arrays.asList(identifierUseCardRule, identifierUseAssignmentRule, identifierSystemCardRule, identifierSystemAssignmentRule));
      doc.profiles.put(optionalProfile.name, optionalProfile);
      // Instance: NoPathRule
      // InstanceOf: OptionalProfile
      // * code = #test-code
      // * status = #final
      Instance noPathRuleInstance = new Instance("NoPathRule");
      noPathRuleInstance.instanceOf = "OptionalProfile";
      AssignmentRule codeAssignmentRule = new AssignmentRule("code");
      codeAssignmentRule.value = new FshCode("test-code");
      AssignmentRule statusAssignmentRule = new AssignmentRule("status");
      statusAssignmentRule.value = new FshCode("final");
      noPathRuleInstance.rules.addAll(Arrays.asList(codeAssignmentRule, statusAssignmentRule));
      // Instance: WithPathRule
      // InstanceOf: OptionalProfile
      // * code = #test-code
      // * status = #final
      // * identifier
      Instance withPathRuleInstance = new Instance("WithPathRule");
      withPathRuleInstance.instanceOf = "OptionalProfile";
      PathRule identifierPathRule = new PathRule("identifier");
      withPathRuleInstance.rules.addAll(Arrays.asList(codeAssignmentRule, statusAssignmentRule, identifierPathRule));
      InstanceDefinition exportedNoPathRule = exportInstance.apply(noPathRuleInstance);
      InstanceDefinition exportedPathRule = exportInstance.apply(withPathRuleInstance);
      assertNull(at(exportedNoPathRule, "identifier"));
      assertJsonEqual("""
        [
          {
            "use": "official",
            "system": "http://example.org/my-real-identifiers"
          }
        ]
        """, at(exportedPathRule, "identifier"));
    }

    @Test
    @DisplayName("InstanceExporter #exportInstance should not overwrite fixed values when a path rule is used later")
    void shouldNotOverwriteFixedValuesWhenAPathRuleIsUsedLater() {
      // Instance: ExampleObs
      // InstanceOf: Observation
      // * valueCodeableConcept = http://foo.com#bar "Foo System Bar Code"
      // * valueCodeableConcept
      //   * text = "Important concept about Bar"
      Instance observation = new Instance("ExampleObs");
      observation.instanceOf = "Observation";
      AssignmentRule assignmentRule = new AssignmentRule("valueCodeableConcept");
      assignmentRule.value = new FshCode("bar", "http://foo.com", "Foo System Bar Code");
      PathRule pathRule = new PathRule("valueCodeableConcept");
      AssignmentRule assignmentAfterPathRule = new AssignmentRule("valueCodeableConcept.text");
      assignmentAfterPathRule.value = "Important concept about Bar";
      observation.rules.addAll(Arrays.asList(assignmentRule, pathRule, assignmentAfterPathRule));
      InstanceDefinition exportedInstance = exportInstance.apply(observation);
      assertJsonEqual("""
        {
          "text": "Important concept about Bar",
          "coding": [
            {
              "code": "bar",
              "system": "http://foo.com",
              "display": "Foo System Bar Code"
            }
          ]
        }
        """, at(exportedInstance, "valueCodeableConcept"));
    }

    @DisplayName("#TimeTravelingResources")
    @Nested
    class TimeTravelingResources {

      @Test
      @DisplayName("InstanceExporter #exportInstance #TimeTravelingResources should export a R5 ActorDefinition in a R4 IG")
      void shouldExportAR5ActorDefinitionInAR4IG() {
        // Instance: AD1
        // InstanceOf: ActorDefinition
        // Usage: #definition
        // * status = #active
        // * type = #server
        Instance ad1 = new Instance("AD1");
        ad1.instanceOf = "ActorDefinition";
        ad1.usage = InstanceUsage.Definition;
        AssignmentRule statusRule = new AssignmentRule("status");
        statusRule.value = new FshCode("active");
        AssignmentRule typeRule = new AssignmentRule("type");
        typeRule.value = new FshCode("server");
        ad1.rules.addAll(Arrays.asList(statusRule, typeRule));
        InstanceDefinition exportedInstance = exportInstance.apply(ad1);
        assertJsonEqual("""
        {
          "_instanceMeta": {
            "instanceOfUrl": "http://hl7.org/fhir/StructureDefinition/ActorDefinition",
            "name": "AD1",
            "sdKind": "resource",
            "sdType": "ActorDefinition",
            "usage": "Definition"
          },
          "id": "AD1",
          "resourceType": "ActorDefinition",
          "url": "http://hl7.org/fhir/us/minimal/ActorDefinition/AD1",
          "status": "active",
          "type": "server"
        }
        """, withMeta(exportedInstance));
        assertEquals(0, loggerSpy.getAllLogs("warn").size());
        assertEquals(0, loggerSpy.getAllLogs("error").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #TimeTravelingResources should export a R5 Requirements in a R4 IG")
      void shouldExportAR5RequirementsInAR4IG() {
        // Instance: RQ1
        // InstanceOf: Requirements
        // Usage: #definition
        // * status = #active
        Instance rq1 = new Instance("RQ1");
        rq1.instanceOf = "Requirements";
        rq1.usage = InstanceUsage.Definition;
        AssignmentRule statusRule = new AssignmentRule("status");
        statusRule.value = new FshCode("active");
        rq1.rules.add(statusRule);
        InstanceDefinition exportedInstance = exportInstance.apply(rq1);
        assertJsonEqual("""
        {
          "_instanceMeta": {
            "instanceOfUrl": "http://hl7.org/fhir/StructureDefinition/Requirements",
            "name": "RQ1",
            "sdKind": "resource",
            "sdType": "Requirements",
            "usage": "Definition"
          },
          "id": "RQ1",
          "resourceType": "Requirements",
          "url": "http://hl7.org/fhir/us/minimal/Requirements/RQ1",
          "status": "active"
        }
        """, withMeta(exportedInstance));
        assertEquals(0, loggerSpy.getAllLogs("warn").size());
        assertEquals(0, loggerSpy.getAllLogs("error").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #TimeTravelingResources should export a R5 SubscriptionTopic in a R4 IG")
      void shouldExportAR5SubscriptionTopicInAR4IG() {
        // Instance: ST1
        // InstanceOf: SubscriptionTopic
        // Usage: #definition
        // * status = #active
        Instance rq1 = new Instance("ST1");
        rq1.instanceOf = "SubscriptionTopic";
        rq1.usage = InstanceUsage.Definition;
        AssignmentRule statusRule = new AssignmentRule("status");
        statusRule.value = new FshCode("active");
        rq1.rules.add(statusRule);
        InstanceDefinition exportedInstance = exportInstance.apply(rq1);
        assertJsonEqual("""
        {
          "_instanceMeta": {
            "instanceOfUrl": "http://hl7.org/fhir/StructureDefinition/SubscriptionTopic",
            "name": "ST1",
            "sdKind": "resource",
            "sdType": "SubscriptionTopic",
            "usage": "Definition"
          },
          "id": "ST1",
          "resourceType": "SubscriptionTopic",
          "url": "http://hl7.org/fhir/us/minimal/SubscriptionTopic/ST1",
          "status": "active"
        }
        """, withMeta(exportedInstance));
        assertEquals(0, loggerSpy.getAllLogs("warn").size());
        assertEquals(0, loggerSpy.getAllLogs("error").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #TimeTravelingResources should export a R5 TestPlan w/ a CodeableReference in a R4 IG")
      void shouldExportAR5TestPlanWACodeableReferenceInAR4IG() {
        // Instance: TP1
        // InstanceOf: TestPlan
        // Usage: #definition
        // * status = #active
        // * testCase.assertion.object.concept = http://example.org/cs#devreq
        // * testCase.assertion.object.reference = Reference(MyDeviceRequest)
        Instance tp1 = new Instance("TP1");
        tp1.instanceOf = "TestPlan";
        tp1.usage = InstanceUsage.Definition;
        AssignmentRule statusRule = new AssignmentRule("status");
        statusRule.value = new FshCode("active");
        AssignmentRule assertionConceptRule = new AssignmentRule("testCase.assertion.object.concept");
        assertionConceptRule.value = new FshCode("devreq", "http://example.org/cs");
        AssignmentRule assertionRefRule = new AssignmentRule("testCase.assertion.object.reference");
        assertionRefRule.value = new FshReference("DeviceRequest/123");
        tp1.rules.addAll(Arrays.asList(statusRule, assertionConceptRule, assertionRefRule));
        InstanceDefinition exportedInstance = exportInstance.apply(tp1);
        assertJsonEqual("""
        {
          "_instanceMeta": {
            "instanceOfUrl": "http://hl7.org/fhir/StructureDefinition/TestPlan",
            "name": "TP1",
            "sdKind": "resource",
            "sdType": "TestPlan",
            "usage": "Definition"
          },
          "id": "TP1",
          "resourceType": "TestPlan",
          "url": "http://hl7.org/fhir/us/minimal/TestPlan/TP1",
          "status": "active",
          "testCase": [
            {
              "assertion": [
                {
                  "object": [
                    {
                      "concept": {
                        "coding": [
                          {
                            "system": "http://example.org/cs",
                            "code": "devreq"
                          }
                        ]
                      },
                      "reference": {
                        "reference": "DeviceRequest/123"
                      }
                    }
                  ]
                }
              ]
            }
          ]
        }
        """, withMeta(exportedInstance));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(0, loggerSpy.getAllLogs("error").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #TimeTravelingResources should NOT export a R5 NutritionProduct in a R4 IG")
      void shouldNOTExportAR5NutritionProductInAR4IG() {
        // Instance: NP1
        // InstanceOf: NutritionProduct
        // Usage: #definition
        // * status = #active
        Instance np1 = new Instance("NP1");
        np1.instanceOf = "NutritionProduct";
        np1.usage = InstanceUsage.Definition;
        AssignmentRule statusRule = new AssignmentRule("status");
        statusRule.value = new FshCode("active");
        np1.rules.add(statusRule);
        assertThrowsMessage("InstanceOf NutritionProduct not found for NP1", () -> exportInstance.apply(np1));
      }

    }
    @DisplayName("#Logical Models")
    @Nested
    class LogicalModels {
      Logical carLogical;
      Profile carProfile;
      Instance carInstance;
      Instance carProfileInstance;

      @BeforeEach
      void beforeEach() {
        loggerSpy.reset();
        carLogical = new Logical("Car");
        doc.logicals.put(carLogical.name, carLogical);
        carProfile = new Profile("CarProfile");
        carProfile.parent = "Car";
        doc.profiles.put(carProfile.name, carProfile);
        carInstance = new Instance("CarInstance");
        carInstance.instanceOf = "Car";
        doc.instances.put(carInstance.name, carInstance);
        carProfileInstance = new Instance("CarProfileInstance");
        carProfileInstance.instanceOf = "CarProfile";
        doc.instances.put(carProfileInstance.name, carProfileInstance);
      }

      @AfterEach
      void afterEach() {
        // None of the test expect warnings or errors. All should be clean.
        assertEquals(0, loggerSpy.getAllLogs("warn").size());
        assertEquals(0, loggerSpy.getAllLogs("error").size());
      }

      // Setting resourceType
      @Test
      @DisplayName("InstanceExporter #exportInstance #Logical Models should set resourceType to the logical type we are making an instance of")
      void shouldSetResourceTypeToTheLogicalTypeWeAreMakingAnInstanceOf() {
        InstanceDefinition exported = exportInstance.apply(carInstance);
        assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Car", at(exported, "resourceType"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Logical Models should set resourceType to the logical type for the profile of a logical we are making an instance of")
      void shouldSetResourceTypeToTheLogicalTypeForTheProfileOfALogicalWeAreMakingAnInstanc() {
        InstanceDefinition exported = exportInstance.apply(carProfileInstance);
        assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/Car", at(exported, "resourceType"));
      }

      // Setting meta.profile
      @Test
      @DisplayName("InstanceExporter #exportInstance #Logical Models should not set meta.profile when we are making an instance of a logical")
      void shouldNotSetMetaProfileWhenWeAreMakingAnInstanceOfALogical() {
        InstanceDefinition exported = exportInstance.apply(carInstance);
        assertNull(at(exported, "meta"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Logical Models should not set meta.profile when we are making an instance of a logical even when it has meta")
      void shouldNotSetMetaProfileWhenWeAreMakingAnInstanceOfALogicalEvenWhenItHasMeta() {
        AddElementRule metaRule = new AddElementRule("meta");
        metaRule.min = 0.0;
        metaRule.max = "1";
        metaRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "Meta"
        }
        """))));
        carLogical.rules.add(metaRule);
        InstanceDefinition exported = exportInstance.apply(carInstance);
        assertNull(at(exported, "meta"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Logical Models should not set meta.profile when we are making an instance of a profile of logical that has no meta")
      void shouldNotSetMetaProfileWhenWeAreMakingAnInstanceOfAProfileOfLogicalThatHasNoMeta() {
        InstanceDefinition exported = exportInstance.apply(carProfileInstance);
        assertNull(at(exported, "meta"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Logical Models should set meta.profile to the defining profile URL we are making an instance of logical (for profile of logical that has meta)")
      void shouldSetMetaProfileToTheDefiningProfileURLWeAreMakingAnInstanceOfLogicalForProf() {
        AddElementRule metaRule = new AddElementRule("meta");
        metaRule.min = 0.0;
        metaRule.max = "1";
        metaRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "Meta"
        }
        """))));
        carLogical.rules.add(metaRule);
        InstanceDefinition exported = exportInstance.apply(carProfileInstance);
        assertJsonEqual("""
        {
          "profile": [
            "http://hl7.org/fhir/us/minimal/StructureDefinition/CarProfile"
          ]
        }
        """, at(exported, "meta"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Logical Models should not set meta.profile when we are making an instance of a profile of a logical with >1 meta")
      void shouldNotSetMetaProfileWhenWeAreMakingAnInstanceOfAProfileOfALogicalWith1Meta() {
        AddElementRule metaRule = new AddElementRule("meta");
        metaRule.min = 0.0;
        metaRule.max = "*";
        metaRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "Meta"
        }
        """))));
        carLogical.rules.add(metaRule);
        InstanceDefinition exported = exportInstance.apply(carProfileInstance);
        assertNull(at(exported, "meta"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Logical Models should not set meta.profile when we are making an instance of a profile that constrains >1 meta to 1 meta")
      void shouldNotSetMetaProfileWhenWeAreMakingAnInstanceOfAProfileThatConstrains1MetaTo1() {
        AddElementRule metaRule = new AddElementRule("meta");
        metaRule.min = 0.0;
        metaRule.max = "*";
        metaRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "Meta"
        }
        """))));
        carLogical.rules.add(metaRule);
        CardRule cardRule = new CardRule("meta");
        cardRule.max = "1";
        carProfile.rules.add(cardRule);
        InstanceDefinition exported = exportInstance.apply(carProfileInstance);
        assertNull(at(exported, "meta"));
      }

      // Setting id
      @Test
      @DisplayName("InstanceExporter #exportInstance #Logical Models should not set id for logicals without id element")
      void shouldNotSetIdForLogicalsWithoutIdElement() {
        InstanceDefinition exported = exportInstance.apply(carInstance);
        assertNull(at(exported, "id"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Logical Models should set id to instance name for logicals with inherited id element")
      void shouldSetIdToInstanceNameForLogicalsWithInheritedIdElement() {
        // Change carLogical parent to Element, which has an id
        carLogical.parent = "Element";
        InstanceDefinition exported = exportInstance.apply(carInstance);
        assertEquals("CarInstance", at(exported, "id"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Logical Models should set id to instance name for logicals with new id element")
      void shouldSetIdToInstanceNameForLogicalsWithNewIdElement() {
        AddElementRule idRule = new AddElementRule("id");
        idRule.min = 0.0;
        idRule.max = "1";
        idRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "id"
        }
        """))));
        carLogical.rules.add(idRule);
        InstanceDefinition exported = exportInstance.apply(carInstance);
        assertEquals("CarInstance", at(exported, "id"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Logical Models should not set id for logical with >1 id element")
      void shouldNotSetIdForLogicalWith1IdElement() {
        AddElementRule idRule = new AddElementRule("id");
        idRule.min = 0.0;
        idRule.max = "*";
        idRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "id"
        }
        """))));
        carLogical.rules.add(idRule);
        InstanceDefinition exported = exportInstance.apply(carInstance);
        assertNull(at(exported, "id"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Logical Models should not set id for logical with profile constraining >1 id to 1 id")
      void shouldNotSetIdForLogicalWithProfileConstraining1IdTo1Id() {
        AddElementRule idRule = new AddElementRule("id");
        idRule.min = 0.0;
        idRule.max = "*";
        idRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "id"
        }
        """))));
        carLogical.rules.add(idRule);
        CardRule cardRule = new CardRule("id");
        cardRule.max = "1";
        carProfile.rules.add(cardRule);
        InstanceDefinition exported = exportInstance.apply(carProfileInstance);
        assertNull(at(exported, "id"));
      }

      // Assignment rules
      @Test
      @DisplayName("InstanceExporter #exportInstance #Logical Models should export simple assignment rules for a logical model")
      void shouldExportSimpleAssignmentRulesForALogicalModel() {
        // Add make and model to Car
        AddElementRule makeRule = new AddElementRule("make");
        makeRule.min = 1.0;
        makeRule.max = "1";
        makeRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "code"
        }
        """))));
        AddElementRule modelRule = new AddElementRule("model");
        modelRule.min = 0.0;
        modelRule.max = "1";
        modelRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "string"
        }
        """))));
        carLogical.rules.addAll(Arrays.asList(makeRule, modelRule));
        // Set the make and model on the instance
        AssignmentRule makeValueRule = new AssignmentRule("make");
        makeValueRule.value = new FshCode("Subaru");
        AssignmentRule modelValueRule = new AssignmentRule("model");
        modelValueRule.value = "Legacy";
        carInstance.rules.addAll(Arrays.asList(makeValueRule, modelValueRule));
        InstanceDefinition exported = exportInstance.apply(carInstance);
        assertJsonEqual("""
        {
          "resourceType": "http://hl7.org/fhir/us/minimal/StructureDefinition/Car",
          "make": "Subaru",
          "model": "Legacy"
        }
        """, exported.toJSON());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Logical Models should export fixed values and assignment rules for a profile of a logical model")
      void shouldExportFixedValuesAndAssignmentRulesForAProfileOfALogicalModel() {
        // Add make and model to Car
        AddElementRule makeRule = new AddElementRule("make");
        makeRule.min = 1.0;
        makeRule.max = "1";
        makeRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "code"
        }
        """))));
        AddElementRule modelRule = new AddElementRule("model");
        modelRule.min = 0.0;
        modelRule.max = "1";
        modelRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "string"
        }
        """))));
        carLogical.rules.addAll(Arrays.asList(makeRule, modelRule));
        // Add fixed value to CarProfile
        AssignmentRule jeepRule = new AssignmentRule("make");
        jeepRule.value = new FshCode("Jeep");
        carProfile.rules.add(jeepRule);
        // Set the model on the instance of the profile
        AssignmentRule modelValueRule = new AssignmentRule("model");
        modelValueRule.value = "Wrangler";
        carProfileInstance.rules.add(modelValueRule);
        InstanceDefinition exported = exportInstance.apply(carProfileInstance);
        assertJsonEqual("""
        {
          "resourceType": "http://hl7.org/fhir/us/minimal/StructureDefinition/Car",
          "make": "Jeep",
          "model": "Wrangler"
        }
        """, exported.toJSON());
      }

    }
    @DisplayName("#Inline Instances")
    @Nested
    class InlineInstances {

      @BeforeEach
      void beforeEach() {
        Instance inlinePatient = new Instance("MyInlinePatient");
        inlinePatient.instanceOf = "Patient";
        inlinePatient.usage = InstanceUsage.Inline;
        AssignmentRule patientId = new AssignmentRule("id");
        patientId.value = "MyInlinePatient";
        AssignmentRule assignedValRule = new AssignmentRule("active");
        assignedValRule.value = true;
        inlinePatient.rules.addAll(Arrays.asList(patientId, assignedValRule));
        // * active = true
        doc.instances.put(inlinePatient.name, inlinePatient);
        Instance inlineObservation = new Instance("MyInlineObservation");
        inlineObservation.instanceOf = "Observation";
        inlineObservation.usage = InstanceUsage.Inline;
        AssignmentRule observationId = new AssignmentRule("id");
        observationId.value = "MyInlineObservation";
        AssignmentRule observationValueRule = new AssignmentRule("valueString");
        observationValueRule.value = "Some Observation";
        inlineObservation.rules.addAll(Arrays.asList(observationId, observationValueRule));
        // * valueString = "Some Observation"
        doc.instances.put(inlineObservation.name, inlineObservation);
        Instance inlineOrganization = new Instance("MyInlineOrganization");
        inlineOrganization.instanceOf = "Organization";
        inlineOrganization.usage = InstanceUsage.Inline;
        AssignmentRule organizationId = new AssignmentRule("id");
        organizationId.value = "MyInlineOrganization";
        AssignmentRule organizationName = new AssignmentRule("name");
        organizationName.value = "Everyone";
        inlineOrganization.rules.addAll(Arrays.asList(organizationId, organizationName));
        // * name = "Everyone"
        doc.instances.put(inlineOrganization.name, inlineOrganization);
        CaretValueRule slicingType = new CaretValueRule("entry");
        slicingType.caretPath = "slicing.discriminator.type";
        slicingType.value = new FshCode("value");
        CaretValueRule slicingPath = new CaretValueRule("entry");
        slicingPath.caretPath = "slicing.discriminator.path";
        slicingPath.value = "code";
        CaretValueRule slicingRules = new CaretValueRule("entry");
        slicingRules.caretPath = "slicing.rules";
        slicingRules.value = new FshCode("open");
        ContainsRule containsRule = new ContainsRule("entry");
        containsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "PatientsOnly"
        }
        """))));
        CardRule cardRule = new CardRule("entry[PatientsOnly]");
        cardRule.min = 0.0;
        cardRule.max = "1";
        OnlyRule typeRule = new OnlyRule("entry[PatientsOnly].resource");
        typeRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "Patient"
        }
        """))));
        ContainsRule choiceContainsRule = new ContainsRule("entry");
        choiceContainsRule.items = new ArrayList<>(Arrays.asList(containsRuleItem(JsAssert.json("""
        {
          "name": "PatientOrOrganization"
        }
        """))));
        CardRule choiceCardRule = new CardRule("entry[PatientOrOrganization]");
        choiceCardRule.min = 0.0;
        choiceCardRule.max = "1";
        OnlyRule choiceTypeRule = new OnlyRule("entry[PatientOrOrganization].resource");
        choiceTypeRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "Patient"
        }
        """)), onlyRuleType(JsAssert.json("""
        {
          "type": "Organization"
        }
        """))));
        // * entry ^slicing.discriminator.type = #value
        // * entry ^slicing.discriminator.path = "code"
        // * entry ^slicing.rules = #open
        // * entry contains Patient 0..1
        // * entry[PatientsOnly].resource only Patient
        // * entry contains PatientOrOrganization 0..1
        // * entry[PatientOrOrganization] only Patient or Organization
        bundle.rules.addAll(Arrays.asList(slicingType, slicingPath, slicingRules, containsRule, cardRule, typeRule, choiceContainsRule, choiceCardRule, choiceTypeRule));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an inline resource to an instance")
      void shouldAssignAnInlineResourceToAnInstance() {
        AssignmentRule inlineRule = new AssignmentRule("contained[0]");
        inlineRule.value = "MyInlinePatient";
        inlineRule.isInstance = true;
        patientInstance.rules.add(inlineRule);
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        [
          {
            "resourceType": "Patient",
            "id": "MyInlinePatient",
            "active": true
          }
        ]
        """, at(exported, "contained"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign multiple inline resources to an instance")
      void shouldAssignMultipleInlineResourcesToAnInstance() {
        AssignmentRule inlineRule = new AssignmentRule("contained[0]");
        inlineRule.value = "MyInlinePatient";
        inlineRule.isInstance = true;
        patientInstance.rules.add(inlineRule);
        AssignmentRule inlineRule2 = new AssignmentRule("contained[1]");
        inlineRule2.value = "MyInlineObservation";
        inlineRule2.isInstance = true;
        patientInstance.rules.add(inlineRule2);
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        [
          {
            "resourceType": "Patient",
            "id": "MyInlinePatient",
            "active": true
          },
          {
            "resourceType": "Observation",
            "id": "MyInlineObservation",
            "valueString": "Some Observation"
          }
        ]
        """, at(exported, "contained"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign other resources to an instance")
      void shouldAssignOtherResourcesToAnInstance() {
        AssignmentRule containedRule1 = new AssignmentRule("contained[0]");
        containedRule1.value = "allergyintolerance-clinical";
        containedRule1.isInstance = true;
        patientInstance.rules.add(containedRule1);
        AssignmentRule containedRule2 = new AssignmentRule("contained[1]");
        containedRule2.value = "w3c-provenance-activity-type";
        containedRule2.isInstance = true;
        patientInstance.rules.add(containedRule2);
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertEquals("allergyintolerance-clinical", at(exported, "contained[0].id"));
        assertEquals("ValueSet", at(exported, "contained[0].resourceType"));
        assertEquals("w3c-provenance-activity-type", at(exported, "contained[1].id"));
        assertEquals("CodeSystem", at(exported, "contained[1].resourceType"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an inline resource to an instance element with a specific type")
      void shouldAssignAnInlineResourceToAnInstanceElementWithASpecificType() {
        AssignmentRule bundleValRule = new AssignmentRule("entry[PatientsOnly].resource");
        bundleValRule.value = "MyInlinePatient";
        bundleValRule.isInstance = true;
        // * entry[PatientsOnly].resource = MyInlinePatient
        bundleInstance.rules.add(bundleValRule);
        InstanceDefinition exported = exportInstance.apply(bundleInstance);
        assertJsonEqual("""
        {
          "resource": {
            "resourceType": "Patient",
            "id": "MyInlinePatient",
            "active": true
          }
        }
        """, at(exported, "entry[0]"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an inline resource to an instance element with a choice type")
      void shouldAssignAnInlineResourceToAnInstanceElementWithAChoiceType() {
        AssignmentRule bundleValRule = new AssignmentRule("entry[PatientOrOrganization].resource");
        bundleValRule.value = "MyInlinePatient";
        bundleValRule.isInstance = true;
        // * entry[PatientOrOrganization].resource = MyInlinePatient
        bundleInstance.rules.add(bundleValRule);
        InstanceDefinition exported = exportInstance.apply(bundleInstance);
        assertJsonEqual("""
        {
          "resourceType": "Patient",
          "id": "MyInlinePatient",
          "active": true
        }
        """, at(exported, "entry[0].resource"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an inline resource that is not the first type to an instance element with a choice type")
      void shouldAssignAnInlineResourceThatIsNotTheFirstTypeToAnInstanceElementWithAChoiceT() {
        AssignmentRule bundleValRule = new AssignmentRule("entry[PatientOrOrganization].resource");
        bundleValRule.value = "MyInlineOrganization";
        bundleValRule.isInstance = true;
        // * entry[PatientOrOrganization].resource = MyInlineOrganization
        bundleInstance.rules.add(bundleValRule);
        InstanceDefinition exported = exportInstance.apply(bundleInstance);
        assertJsonEqual("""
        {
          "resourceType": "Organization",
          "id": "MyInlineOrganization",
          "name": "Everyone"
        }
        """, at(exported, "entry[0].resource"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an inline resource to an instance when the resource is not a profile and uses meta")
      void shouldAssignAnInlineResourceToAnInstanceWhenTheResourceIsNotAProfileAndUsesMeta() {
        // This test reflects a real-world bug reported on Zulip:
        // https://chat.fhir.org/#narrow/stream/215610-shorthand/topic/example.20FSH.20Bundle.20transaction.20with.20Create.20entries
        Instance inlineInstance = new Instance("ExampleInlinePatient");
        inlineInstance.instanceOf = "Patient";
        // * meta.security = http://terminology.hl7.org/CodeSystem/v3-ActReason#HTEST
        AssignmentRule assignedValRule = new AssignmentRule("meta.security");
        assignedValRule.value = new FshCode("HTEST", "http://terminology.hl7.org/CodeSystem/v3-ActReason");
        inlineInstance.rules.add(assignedValRule);
        doc.instances.put(inlineInstance.name, inlineInstance);
        // * contained[0] = ExampleInlinePatient
        AssignmentRule inlineRule = new AssignmentRule("contained[0]");
        inlineRule.value = "ExampleInlinePatient";
        inlineRule.isInstance = true;
        patientInstance.rules.add(inlineRule);
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        [
          {
            "resourceType": "Patient",
            "id": "ExampleInlinePatient",
            "meta": {
              "security": [
                {
                  "code": "HTEST",
                  "system": "http://terminology.hl7.org/CodeSystem/v3-ActReason"
                }
              ]
            }
          }
        ]
        """, at(exported, "contained"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should log an error when assigning an inline resource to an invalid choice")
      void shouldLogAnErrorWhenAssigningAnInlineResourceToAnInvalidChoice() {
        AssignmentRule bundleValRule = new AssignmentRule("entry[PatientOrOrganization].resource").withFile("BadChoice.fsh").withLocation(new int[] {1, 2, 3, 4});
        bundleValRule.value = "MyInlineObservation";
        bundleValRule.isInstance = true;
        // * entry[PatientOrOrganization].resource = MyInlineObservation
        bundleInstance.rules.add(bundleValRule);
        InstanceDefinition exported = exportInstance.apply(bundleInstance);
        assertNull(at(exported, "entry"));
        assertTrue(Js.truthy(some(loggerSpy.getAllMessages("error"), (e) -> Js.truthy(jsMatch(((String) e), jsRegex("Cannot assign Observation value: MyInlineObservation. Value does not match element type: Patient, Organization", ""))))));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should log an error when assigning an inline resource that does not exist to an instance")
      void shouldLogAnErrorWhenAssigningAnInlineResourceThatDoesNotExistToAnInstance() {
        AssignmentRule inlineRule = new AssignmentRule("contained[0]").withFile("FakeInstance.fsh").withLocation(new int[] {1, 2, 3, 4});
        inlineRule.value = "MyFakePatient";
        inlineRule.isInstance = true;
        patientInstance.rules.add(inlineRule);
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertNull(at(exported, "contained"));
        assertMatches("Cannot find definition for Instance: MyFakePatient. Skipping rule.*File: FakeInstance.fsh.*Line: 1 - 3\\D*", "s", loggerSpy.getLastMessage("error"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should override an assigned inline resource on an instance")
      void shouldOverrideAnAssignedInlineResourceOnAnInstance() {
        AssignmentRule inlineRule = new AssignmentRule("contained[0]");
        inlineRule.value = "MyInlinePatient";
        inlineRule.isInstance = true;
        AssignmentRule overrideRule = new AssignmentRule("contained[0].birthDate");
        overrideRule.value = "2000-02-24";
        // * contained[0] = MyInlinePatient
        // * contained[0].birthDate = 2000-02-24
        patientInstance.rules.addAll(Arrays.asList(inlineRule, overrideRule));
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        [
          {
            "resourceType": "Patient",
            "id": "MyInlinePatient",
            "active": true,
            "birthDate": "2000-02-24"
          }
        ]
        """, at(exported, "contained"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should override an assigned via resourceType inline resource on an instance")
      void shouldOverrideAnAssignedViaResourceTypeInlineResourceOnAnInstance() {
        AssignmentRule inlineRule = new AssignmentRule("contained[0].resourceType");
        inlineRule.value = "Patient";
        AssignmentRule overrideRule = new AssignmentRule("contained[0].birthDate");
        overrideRule.value = "2000-02-24";
        // * contained[0].resourceType = "Patient"
        // * contained[0].birthDate = 2000-02-24
        patientInstance.rules.addAll(Arrays.asList(inlineRule, overrideRule));
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        [
          {
            "resourceType": "Patient",
            "birthDate": "2000-02-24"
          }
        ]
        """, at(exported, "contained"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should override an assigned inline resource on an instance with paths that mix usage of [0] indexing")
      void shouldOverrideAnAssignedInlineResourceOnAnInstanceWithPathsThatMixUsageOf0Indexi() {
        AssignmentRule inlineRule = new AssignmentRule("contained[00]");
        inlineRule.value = "MyInlinePatient";
        inlineRule.isInstance = true;
        AssignmentRule overrideRule = new AssignmentRule("contained.birthDate");
        overrideRule.value = "2000-02-24";
        // * contained[0] = MyInlinePatient
        // * contained.birthDate = 2000-02-24
        patientInstance.rules.addAll(Arrays.asList(inlineRule, overrideRule));
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        [
          {
            "resourceType": "Patient",
            "id": "MyInlinePatient",
            "active": true,
            "birthDate": "2000-02-24"
          }
        ]
        """, at(exported, "contained"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should override an assigned via resourceType inline resource on an instance with paths that mix usage of [0] indexing")
      void shouldOverrideAnAssignedViaResourceTypeInlineResourceOnAnInstanceWithPathsThatMi() {
        AssignmentRule inlineRule = new AssignmentRule("contained[0].resourceType");
        inlineRule.value = "Patient";
        AssignmentRule overrideRule = new AssignmentRule("contained.birthDate");
        overrideRule.value = "2000-02-24";
        // * contained.birthDate = 2000-02-24
        // * contained[0].resourceType = "Patient"
        patientInstance.rules.addAll(Arrays.asList(overrideRule, inlineRule));
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        [
          {
            "resourceType": "Patient",
            "birthDate": "2000-02-24"
          }
        ]
        """, at(exported, "contained"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should override a nested assigned inline resource on an instance")
      void shouldOverrideANestedAssignedInlineResourceOnAnInstance() {
        AssignmentRule bundleRule = new AssignmentRule("contained[0].resourceType");
        bundleRule.value = "Bundle";
        AssignmentRule patientRule = new AssignmentRule("contained[0].entry[0].resource");
        patientRule.value = "MyInlinePatient";
        patientRule.isInstance = true;
        AssignmentRule birthDateRule = new AssignmentRule("contained[0].entry[0].resource.birthDate");
        birthDateRule.value = "2000-02-24";
        // * contained[0].resourceType = "Bundle"
        // * contained[0].entry[0].resource = MyInlinePatient
        // * contained[0].entry[0].resource.birthDate = "2000-02-24"
        patientInstance.rules.addAll(Arrays.asList(bundleRule, patientRule, birthDateRule));
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        [
          {
            "resourceType": "Bundle",
            "entry": [
              {
                "resource": {
                  "resourceType": "Patient",
                  "id": "MyInlinePatient",
                  "active": true,
                  "birthDate": "2000-02-24"
                }
              }
            ]
          }
        ]
        """, at(exported, "contained"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should override an inline profile on an instance")
      void shouldOverrideAnInlineProfileOnAnInstance() {
        Instance inlineBundle = new Instance("MyBundle");
        inlineBundle.instanceOf = "TestBundle";
        doc.instances.put(inlineBundle.name, inlineBundle);
        AssignmentRule bundleRule = new AssignmentRule("contained[0]");
        bundleRule.value = "MyBundle";
        bundleRule.isInstance = true;
        AssignmentRule birthDateRule = new AssignmentRule("contained[0].entry[PatientsOnly].resource.birthDate");
        birthDateRule.value = "2000-02-24";
        // contained[0] = MyBundle
        // contained[0].entry[PatientsOnly].resource.birthDate = "2000-02-24"
        patientInstance.rules.addAll(Arrays.asList(bundleRule, birthDateRule));
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        [
          {
            "id": "MyBundle",
            "meta": {
              "profile": [
                "http://hl7.org/fhir/us/minimal/StructureDefinition/TestBundle"
              ]
            },
            "resourceType": "Bundle",
            "entry": [
              {
                "resource": {
                  "birthDate": "2000-02-24"
                }
              }
            ]
          }
        ]
        """, at(exported, "contained"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an inline instance of a type to an instance")
      void shouldAssignAnInlineInstanceOfATypeToAnInstance() {
        Instance inlineCodeable = new Instance("MyCodeable");
        inlineCodeable.instanceOf = "CodeableConcept";
        inlineCodeable.usage = InstanceUsage.Inline;
        doc.instances.put(inlineCodeable.name, inlineCodeable);
        AssignmentRule codingRule = new AssignmentRule("coding");
        codingRule.value = new FshCode("foo", "http://bar.com");
        // * coding = http://bar.com#foo
        inlineCodeable.rules.add(codingRule);
        AssignmentRule inlineRule = new AssignmentRule("maritalStatus");
        inlineRule.value = "MyCodeable";
        inlineRule.isInstance = true;
        // * maritalStatus = MyCodeable
        patientInstance.rules.add(inlineRule);
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        {
          "coding": [
            {
              "system": "http://bar.com",
              "code": "foo"
            }
          ]
        }
        """, at(exported, "maritalStatus"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an inline instance of a specialization of a type to an instance")
      void shouldAssignAnInlineInstanceOfASpecializationOfATypeToAnInstance() {
        Instance inlineAge = new Instance("MyAge");
        inlineAge.instanceOf = "Age";
        inlineAge.usage = InstanceUsage.Inline;
        doc.instances.put(inlineAge.name, inlineAge);
        AssignmentRule ageRule = new AssignmentRule("value");
        ageRule.value = 42.0;
        // * value = 42
        inlineAge.rules.add(ageRule);
        AssignmentRule inlineRule = new AssignmentRule("valueQuantity");
        inlineRule.value = "MyAge";
        inlineRule.isInstance = true;
        // * valueQuantity = MyAge
        respRateInstance.rules.add(inlineRule);
        InstanceDefinition exported = exportInstance.apply(respRateInstance);
        assertJsEqual(42.0, at(exported, "valueQuantity.value"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should not overwrite the value property when assigning a Quantity object")
      void shouldNotOverwriteTheValuePropertyWhenAssigningAQuantityObject() {
        Profile exObservation = new Profile("ExObservation");
        exObservation.parent = "Observation";
        doc.profiles.put(exObservation.name, exObservation);
        OnlyRule onlyRule = new OnlyRule("value[x]");
        onlyRule.types = new ArrayList<>(Arrays.asList(onlyRuleType(JsAssert.json("""
        {
          "type": "Quantity"
        }
        """))));
        exObservation.rules.add(onlyRule);
        // * valueQuantity.value = 17
        AssignmentRule valueSettingRule = new AssignmentRule("valueQuantity.value");
        valueSettingRule.value = 17.0;
        valueSettingRule.isInstance = false;
        valueSettingRule.exactly = false;
        // * valueQuantity = UCUM#/min
        AssignmentRule codeSettingRule = new AssignmentRule("valueQuantity");
        codeSettingRule.value = new FshCode("mg", "http://unitsofmeasure.org", "mg");
        codeSettingRule.isInstance = false;
        codeSettingRule.exactly = false;
        Instance exInstance = new Instance("ExInstance");
        exInstance.instanceOf = "ExObservation";
        exInstance.rules.add(valueSettingRule);
        exInstance.rules.add(codeSettingRule);
        InstanceDefinition exported = exportInstance.apply(exInstance);
        assertJsonEqual("""
        {
          "value": 17,
          "code": "mg",
          "system": "http://unitsofmeasure.org",
          "unit": "mg"
        }
        """, at(exported, "valueQuantity"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an inline instance of a profile of a type to an instance")
      void shouldAssignAnInlineInstanceOfAProfileOfATypeToAnInstance() {
        Instance inlineSimple = new Instance("MySimple");
        inlineSimple.instanceOf = "SimpleQuantity";
        inlineSimple.usage = InstanceUsage.Inline;
        doc.instances.put(inlineSimple.name, inlineSimple);
        AssignmentRule quantRule = new AssignmentRule("value");
        quantRule.value = 7.0;
        // * value = 7
        inlineSimple.rules.add(quantRule);
        AssignmentRule inlineRule = new AssignmentRule("valueQuantity");
        inlineRule.value = "MySimple";
        inlineRule.isInstance = true;
        // * valueQuantity = MySimple
        respRateInstance.rules.add(inlineRule);
        InstanceDefinition exported = exportInstance.apply(respRateInstance);
        assertJsEqual(7.0, at(exported, "valueQuantity.value"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an inline instance of a FSH defined profile of a type to an instance")
      void shouldAssignAnInlineInstanceOfAFSHDefinedProfileOfATypeToAnInstance() {
        Profile profile = new Profile("Foo");
        profile.parent = "Quantity";
        doc.profiles.put(profile.name, profile);
        Instance inlineSimple = new Instance("MyQuantity");
        inlineSimple.instanceOf = "Foo";
        inlineSimple.usage = InstanceUsage.Inline;
        doc.instances.put(inlineSimple.name, inlineSimple);
        AssignmentRule quantRule = new AssignmentRule("value");
        quantRule.value = 7.0;
        // * value = 7
        inlineSimple.rules.add(quantRule);
        AssignmentRule inlineRule = new AssignmentRule("valueQuantity");
        inlineRule.value = "MyQuantity";
        inlineRule.isInstance = true;
        // * valueQuantity = MyQuantity
        respRateInstance.rules.add(inlineRule);
        InstanceDefinition exported = exportInstance.apply(respRateInstance);
        assertJsEqual(7.0, at(exported, "valueQuantity.value"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an inline instance of an extension to an instance")
      void shouldAssignAnInlineInstanceOfAnExtensionToAnInstance() {
        patientProfInstance.usage = InstanceUsage.Inline;
        AssignmentRule codingRule = new AssignmentRule("extension[level].valueCoding");
        codingRule.value = new FshCode("foo", "http://bar.com");
        // * extension[level].valueCoding = http://bar.com#foo
        patientProfInstance.rules.add(codingRule);
        AssignmentRule inlineRule = new AssignmentRule("extension");
        inlineRule.value = "Baz";
        inlineRule.isInstance = true;
        patientInstance.rules.add(inlineRule);
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        [
          {
            "extension": [
              {
                "url": "level",
                "valueCoding": {
                  "system": "http://bar.com",
                  "code": "foo"
                }
              }
            ],
            "url": "http://hl7.org/fhir/StructureDefinition/patient-proficiency"
          }
        ]
        """, at(exported, "extension"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an inline instance with a numeric id")
      void shouldAssignAnInlineInstanceWithANumericId() {
        // Instance: 99e1
        // InstanceOf: ContactPoint
        // Usage: #inline
        // * value = "Nine Nine E One"
        Instance nineNineEOne = new Instance("99e1");
        nineNineEOne.instanceOf = "ContactPoint";
        nineNineEOne.usage = InstanceUsage.Inline;
        AssignmentRule nineNineValue = new AssignmentRule("value");
        nineNineValue.value = "Nine Nine E One";
        nineNineEOne.rules.add(nineNineValue);
        doc.instances.put(nineNineEOne.name, nineNineEOne);
        AssignmentRule telecomRule = new AssignmentRule("telecom");
        telecomRule.value = new java.math.BigInteger("990");
        telecomRule.rawValue = "99e1";
        patientInstance.rules.add(telecomRule);
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        [
          {
            "value": "Nine Nine E One"
          }
        ]
        """, at(exported, "telecom"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should log a warning and assign an example instance within a definition instance")
      void shouldLogAWarningAndAssignAnExampleInstanceWithinADefinitionInstance() {
        // Instance: my-observation
        // InstanceOf: Observation
        // Usage: #example
        // * status = #draft
        // * code = #123
        Instance numericObservation = new Instance("my-observation");
        numericObservation.instanceOf = "Observation";
        numericObservation.usage = InstanceUsage.Example;
        AssignmentRule instanceStatus = new AssignmentRule("status");
        instanceStatus.value = new FshCode("draft");
        AssignmentRule instanceCode = new AssignmentRule("code");
        instanceCode.value = new FshCode("123");
        numericObservation.rules.addAll(Arrays.asList(instanceStatus, instanceCode));
        doc.instances.put(numericObservation.name, numericObservation);
        AssignmentRule contained = new AssignmentRule("contained").withFile("Patient.fsh").withLocation(new int[] {6, 3, 6, 18});
        contained.value = "my-observation";
        contained.isInstance = true;
        patientInstance.rules.add(contained);
        patientInstance.usage = InstanceUsage.Definition;
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        {
          "resourceType": "Observation",
          "id": "my-observation",
          "status": "draft",
          "code": {
            "coding": [
              {
                "code": "123"
              }
            ]
          }
        }
        """, at(exported, "contained[0]"));
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertMatches("Contained instance \"my-observation\" is an example", "s", loggerSpy.getLastMessage("warn"));
        assertMatches("File: Patient\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should log a warning and assign an example instance with a numeric id within a definition instance")
      void shouldLogAWarningAndAssignAnExampleInstanceWithANumericIdWithinADefinitionInstan() {
        // Instance: 765
        // InstanceOf: Observation
        // Usage: #example
        // * status = #draft
        // * code = #123
        Instance numericObservation = new Instance("765");
        numericObservation.instanceOf = "Observation";
        numericObservation.usage = InstanceUsage.Example;
        AssignmentRule instanceStatus = new AssignmentRule("status");
        instanceStatus.value = new FshCode("draft");
        AssignmentRule instanceCode = new AssignmentRule("code");
        instanceCode.value = new FshCode("123");
        numericObservation.rules.addAll(Arrays.asList(instanceStatus, instanceCode));
        doc.instances.put(numericObservation.name, numericObservation);
        AssignmentRule contained = new AssignmentRule("contained").withFile("Patient.fsh").withLocation(new int[] {5, 3, 5, 18});
        contained.value = new java.math.BigInteger("765");
        contained.rawValue = "765";
        patientInstance.rules.add(contained);
        patientInstance.usage = InstanceUsage.Definition;
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        {
          "resourceType": "Observation",
          "id": "765",
          "status": "draft",
          "code": {
            "coding": [
              {
                "code": "123"
              }
            ]
          }
        }
        """, at(exported, "contained[0]"));
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertMatches("Contained instance \"765\" is an example", "s", loggerSpy.getLastMessage("warn"));
        assertMatches("File: Patient\\.fsh.*Line: 5\\D*", "s", loggerSpy.getLastMessage("warn"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an inline instance with an id that resembles a boolean")
      void shouldAssignAnInlineInstanceWithAnIdThatResemblesABoolean() {
        // Instance: false
        // InstanceOf: ContactPoint
        // Usage: #inline
        // * value = "False Contact"
        Instance falseContact = new Instance("false");
        falseContact.instanceOf = "ContactPoint";
        falseContact.usage = InstanceUsage.Inline;
        AssignmentRule falseValue = new AssignmentRule("value");
        falseValue.value = "False Contact";
        falseContact.rules.add(falseValue);
        doc.instances.put(falseContact.name, falseContact);
        AssignmentRule telecomRule = new AssignmentRule("telecom");
        telecomRule.value = false;
        telecomRule.rawValue = "false";
        patientInstance.rules.add(telecomRule);
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("""
        [
          {
            "value": "False Contact"
          }
        ]
        """, at(exported, "telecom"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should log a message when trying to assign a value that is numeric and refers to an Instance, but both types are wrong")
      void shouldLogAMessageWhenTryingToAssignAValueThatIsNumericAndRefersToAnInstanceButBo() {
        // Instance: 99e1
        // InstanceOf: ContactPoint
        // Usage: #inline
        // * value = "Nine Nine E One"
        Instance nineNineEOne = new Instance("99e1");
        nineNineEOne.instanceOf = "ContactPoint";
        nineNineEOne.usage = InstanceUsage.Inline;
        AssignmentRule nineNineValue = new AssignmentRule("value");
        nineNineValue.value = "Nine Nine E One";
        nineNineEOne.rules.add(nineNineValue);
        doc.instances.put(nineNineEOne.name, nineNineEOne);
        AssignmentRule nameRule = new AssignmentRule("name").withFile("Instances.fsh").withLocation(new int[] {5, 3, 5, 29});
        nameRule.value = new java.math.BigInteger("990");
        nameRule.rawValue = "99e1";
        patientInstance.rules.add(nameRule);
        exportInstance.apply(patientInstance);
        assertMatches("Cannot assign number value: 990\\. Value does not match element type: HumanName.*File: Instances\\.fsh.*Line: 5\\D*", "s", loggerSpy.getLastMessage("error"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should log a message when trying to assign a value that is boolean and refers to an Instance, but both types are wrong")
      void shouldLogAMessageWhenTryingToAssignAValueThatIsBooleanAndRefersToAnInstanceButBo() {
        // Instance: false
        // InstanceOf: ContactPoint
        // Usage: #inline
        // * value = "False Contact"
        Instance falseContact = new Instance("false");
        falseContact.instanceOf = "ContactPoint";
        falseContact.usage = InstanceUsage.Inline;
        AssignmentRule falseValue = new AssignmentRule("value");
        falseValue.value = "False Contact";
        falseContact.rules.add(falseValue);
        doc.instances.put(falseContact.name, falseContact);
        AssignmentRule nameRule = new AssignmentRule("name").withFile("Instances.fsh").withLocation(new int[] {6, 3, 6, 29});
        nameRule.value = false;
        nameRule.rawValue = "false";
        patientInstance.rules.add(nameRule);
        exportInstance.apply(patientInstance);
        assertMatches("Cannot assign boolean value: false\\. Value does not match element type: HumanName.*File: Instances\\.fsh.*Line: 6\\D*", "s", loggerSpy.getLastMessage("error"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an instance that matches existing values")
      void shouldAssignAnInstanceThatMatchesExistingValues() {
        // Profile: TestPatient
        // Parent: Patient
        // * name 1..1
        // * name.family = "Goodweather"
        // * name.family 1..1
        // * name.text = "Regular text"
        CardRule nameCard = new CardRule("name");
        nameCard.min = 1.0;
        nameCard.max = "1";
        AssignmentRule familyAssignment = new AssignmentRule("name.family");
        familyAssignment.value = "Goodweather";
        CardRule familyCard = new CardRule("name.family");
        familyCard.min = 1.0;
        familyCard.max = "1";
        AssignmentRule textAssignment = new AssignmentRule("name.text");
        textAssignment.value = "Regular text";
        patient.rules.addAll(Arrays.asList(nameCard, familyAssignment, familyCard, textAssignment));
        // Instance: SameName
        // InstanceOf: HumanName
        // Usage: #inline
        // * text = "Regular text"
        // * use = #official
        Instance sameName = new Instance("SameName");
        sameName.instanceOf = "HumanName";
        sameName.usage = InstanceUsage.Inline;
        AssignmentRule sameText = new AssignmentRule("text");
        sameText.value = "Regular text";
        AssignmentRule sameUse = new AssignmentRule("use");
        sameUse.value = new FshCode("official");
        sameName.rules.addAll(Arrays.asList(sameText, sameUse));
        doc.instances.put(sameName.name, sameName);
        // Instance: Bar
        // InstanceOf: TestPatient
        // * name = SameName
        AssignmentRule nameAssignment = new AssignmentRule("name").withFile("Bar.fsh").withLocation(new int[] {3, 3, 3, 25});
        nameAssignment.value = "SameName";
        nameAssignment.isInstance = true;
        patientInstance.rules.add(nameAssignment);
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertEquals("Goodweather", at(exported, "name[0].family"));
        assertEquals("Regular text", at(exported, "name[0].text"));
        assertEquals("official", at(exported, "name[0].use"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should log an error when assigning an instance that would overwrite an existing value")
      void shouldLogAnErrorWhenAssigningAnInstanceThatWouldOverwriteAnExistingValue() {
        // Profile: TestPatient
        // Parent: Patient
        // * name 1..1
        // * name.family = "Goodweather"
        // * name.family 1..1
        // * name.text = "Regular text"
        CardRule nameCard = new CardRule("name");
        nameCard.min = 1.0;
        nameCard.max = "1";
        AssignmentRule familyAssignment = new AssignmentRule("name.family");
        familyAssignment.value = "Goodweather";
        CardRule familyCard = new CardRule("name.family");
        familyCard.min = 1.0;
        familyCard.max = "1";
        AssignmentRule textAssignment = new AssignmentRule("name.text");
        textAssignment.value = "Regular text";
        patient.rules.addAll(Arrays.asList(nameCard, familyAssignment, familyCard, textAssignment));
        // Instance: DifferentName
        // InstanceOf: HumanName
        // Usage: #inline
        // * text = "Different text"
        // * use = #official
        Instance differentName = new Instance("DifferentName");
        differentName.instanceOf = "HumanName";
        differentName.usage = InstanceUsage.Inline;
        AssignmentRule differentText = new AssignmentRule("text");
        differentText.value = "Different text";
        AssignmentRule differentUse = new AssignmentRule("use");
        differentUse.value = new FshCode("official");
        differentName.rules.addAll(Arrays.asList(differentText, differentUse));
        doc.instances.put(differentName.name, differentName);
        // Instance: Bar
        // InstanceOf: TestPatient
        // * name = DifferentName
        AssignmentRule nameAssignment = new AssignmentRule("name").withFile("Bar.fsh").withLocation(new int[] {3, 3, 3, 25});
        nameAssignment.value = "DifferentName";
        nameAssignment.isInstance = true;
        patientInstance.rules.add(nameAssignment);
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        // name.family has a minimum cardinality of 1, so it is set as an implied property
        assertEquals("Goodweather", at(exported, "name[0].family"));
        // text is not required, but the assigned Instance's text would violate the Profile, so it is not assigned
        assertNull(at(exported, "name[0].text"));
        // since the Instance is not assigned, the use is also undefined
        assertNull(at(exported, "name[0].use"));
        assertMatches("Cannot assign Different text to this element.*File: Bar\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("error"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should log an error when assigning an instance with a numeric id that would overwrite an existing value")
      void shouldLogAnErrorWhenAssigningAnInstanceWithANumericIdThatWouldOverwriteAnExistin() {
        // Profile: TestPatient
        // Parent: Patient
        // * name 1..1
        // * name.family = "Goodweather"
        // * name.family 1..1
        // * name.text = "Regular text"
        CardRule nameCard = new CardRule("name");
        nameCard.min = 1.0;
        nameCard.max = "1";
        AssignmentRule familyAssignment = new AssignmentRule("name.family");
        familyAssignment.value = "Goodweather";
        CardRule familyCard = new CardRule("name.family");
        familyCard.min = 1.0;
        familyCard.max = "1";
        AssignmentRule textAssignment = new AssignmentRule("name.text");
        textAssignment.value = "Regular text";
        patient.rules.addAll(Arrays.asList(nameCard, familyAssignment, familyCard, textAssignment));
        // Instance: 555
        // InstanceOf: HumanName
        // Usage: #inline
        // * text = "Different text"
        // * use = #official
        Instance differentName = new Instance("555");
        differentName.instanceOf = "HumanName";
        differentName.usage = InstanceUsage.Inline;
        AssignmentRule differentText = new AssignmentRule("text");
        differentText.value = "Different text";
        AssignmentRule differentUse = new AssignmentRule("use");
        differentUse.value = new FshCode("official");
        differentName.rules.addAll(Arrays.asList(differentText, differentUse));
        doc.instances.put(differentName.name, differentName);
        // Instance: Bar
        // InstanceOf: TestPatient
        // * name = 555
        AssignmentRule nameAssignment = new AssignmentRule("name").withFile("Bar.fsh").withLocation(new int[] {3, 3, 3, 25});
        nameAssignment.value = 555.0;
        nameAssignment.rawValue = "555";
        patientInstance.rules.add(nameAssignment);
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        // name.family has a minimum cardinality of 1, so it is set as an implied property
        assertEquals("Goodweather", at(exported, "name[0].family"));
        // text is not required, but the assigned Instance's text would violate the Profile, so it is not assigned
        assertNull(at(exported, "name[0].text"));
        // since the Instance is not assigned, the use is also undefined
        assertNull(at(exported, "name[0].use"));
        assertMatches("Cannot assign Different text to this element.*File: Bar\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("error"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an instance of a type to an instance and log a warning when the type is not inline")
      void shouldAssignAnInstanceOfATypeToAnInstanceAndLogAWarningWhenTheTypeIsNotInline() {
        Instance inlineCodeable = new Instance("MyCodeable").withFile("Code.fsh").withLocation(new int[] {1, 2, 3, 4});
        inlineCodeable.instanceOf = "CodeableConcept";
        doc.instances.put(inlineCodeable.name, inlineCodeable);
        AssignmentRule codingRule = new AssignmentRule("coding");
        codingRule.value = new FshCode("foo", "http://bar.com");
        // * coding = http://bar.com#foo
        inlineCodeable.rules.add(codingRule);
        AssignmentRule inlineRule = new AssignmentRule("maritalStatus");
        inlineRule.value = "MyCodeable";
        inlineRule.isInstance = true;
        // * maritalStatus = MyCodeable
        patientInstance.rules.add(inlineRule);
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertMatches("Instance MyCodeable is not an instance of a resource.*File: Code\\.fsh.*Line: 1 - 3\\D*", "s", loggerSpy.getLastMessage("warn"));
        assertJsonEqual("""
        {
          "coding": [
            {
              "system": "http://bar.com",
              "code": "foo"
            }
          ]
        }
        """, at(exported, "maritalStatus"));
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an inline instance of a primitive to a primitive element")
      void shouldAssignAnInlineInstanceOfAPrimitiveToAPrimitiveElement() {
        Instance primitiveInstannce = new Instance("MyFancyId").withFile("Primitive.fsh").withLocation(new int[] {1, 2, 3, 4});
        primitiveInstannce.instanceOf = "id";
        primitiveInstannce.usage = InstanceUsage.Inline;
        doc.instances.put(primitiveInstannce.name, primitiveInstannce);
        // * value = "fancy-id"
        AssignmentRule valueRule = new AssignmentRule("value");
        valueRule.value = "fancy-id";
        primitiveInstannce.rules.add(valueRule);
        // * id = MyFancyId
        AssignmentRule inlineRule = new AssignmentRule("id");
        inlineRule.value = "MyFancyId";
        inlineRule.isInstance = true;
        patientInstance.rules.add(inlineRule);
        InstanceDefinition exported = exportInstance.apply(patientInstance);
        assertJsonEqual("\"fancy-id\"", at(exported, "id"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
      }

      @Test
      @DisplayName("InstanceExporter #exportInstance #Inline Instances should assign an inline instance of a primitive with additional properties to a primitive element")
      void shouldAssignAnInlineInstanceOfAPrimitiveWithAdditionalPropertiesToAPrimitiveElem() {
        Instance primitiveInstance = new Instance("MyFancyString").withFile("Primitive.fsh").withLocation(new int[] {1, 2, 3, 4});
        primitiveInstance.instanceOf = "string";
        primitiveInstance.usage = InstanceUsage.Inline;
        doc.instances.put(primitiveInstance.name, primitiveInstance);
        // * value = "fancy title string"
        AssignmentRule valueRule = new AssignmentRule("value");
        valueRule.value = "fancy title string";
        // * extension.url = "http://example.org/fake"
        AssignmentRule extensionUrl = new AssignmentRule("extension.url");
        extensionUrl.value = "http://example.org/fake";
        // * extension.valueBoolean = true
        AssignmentRule extensionValue = new AssignmentRule("extension.valueBoolean");
        extensionValue.value = true;
        primitiveInstance.rules.addAll(Arrays.asList(valueRule, extensionUrl, extensionValue));
        // * title = MyFancyString
        AssignmentRule inlineRule = new AssignmentRule("title");
        inlineRule.value = "MyFancyString";
        inlineRule.isInstance = true;
        carePlanInstance.rules.add(inlineRule);
        InstanceDefinition exported = exportInstance.apply(carePlanInstance);
        assertJsonEqual("\"fancy title string\"", at(exported, "title"));
        assertJsonEqual("""
        {
          "extension": [
            {
              "url": "http://example.org/fake",
              "valueBoolean": true
            }
          ]
        }
        """, at(exported, "_title"));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
      }

    }
  }
  @DisplayName("#export")
  @Nested
  class Export {

    @Test
    @DisplayName("InstanceExporter #export should still apply valid rules if one fails")
    void shouldStillApplyValidRulesIfOneFails() {
      Instance instance = new Instance("UnmeasurableAttribute");
      instance.instanceOf = "Patient";
      AssignmentRule impossibleRule = new AssignmentRule("impossible");
      impossibleRule.value = "unmeasurable";
      instance.rules.add(impossibleRule);
      AssignmentRule possibleRule = new AssignmentRule("identifier.value");
      possibleRule.value = "Pascal";
      instance.rules.add(possibleRule);
      doc.instances.put(instance.name, instance);
      List<InstanceDefinition> exported = exporter.export().instances;
      assertEquals(1, exported.size());
      assertEquals("Pascal", at(exported.get(0), "identifier[0].value"));
    }

    @Test
    @DisplayName("InstanceExporter #export should log a message when the path for a assigned value is not found")
    void shouldLogAMessageWhenThePathForAAssignedValueIsNotFound() {
      Instance instance = new Instance("UnmeasurableAttribute");
      instance.instanceOf = "Patient";
      AssignmentRule impossibleRule = new AssignmentRule("impossible").withFile("Unmeasurable.fsh").withLocation(new int[] {3, 8, 3, 28});
      impossibleRule.value = "unmeasurable";
      instance.rules.add(impossibleRule);
      doc.instances.put(instance.name, instance);
      List<InstanceDefinition> exported = exporter.export().instances;
      assertEquals(1, exported.size());
      assertMatches("File: Unmeasurable\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #export should log a warning when exporting an instance of a custom resource")
    void shouldLogAWarningWhenExportingAnInstanceOfACustomResource() {
      Resource resource = new Resource("Foo");
      doc.resources.put(resource.name, resource);
      Instance instance = new Instance("FooInstance");
      instance.instanceOf = "Foo";
      doc.instances.put(instance.name, instance);
      sdExporter.export();
      List<InstanceDefinition> exported = exporter.export().instances;
      assertEquals(1, exported.size());
      assertEquals(2, loggerSpy.getAllMessages("warn").size());
      assertMatches("following instance\\(s\\) of custom resources:.*- FooInstance", "s", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("InstanceExporter #export should log a warning when exporting multiple instances of custom resources")
    void shouldLogAWarningWhenExportingMultipleInstancesOfCustomResources() {
      Resource resource1 = new Resource("Foo");
      Resource resource2 = new Resource("Bar");
      doc.resources.put(resource1.name, resource1);
      doc.resources.put(resource2.name, resource2);
      Instance instance1 = new Instance("FooInstance");
      instance1.instanceOf = "Foo";
      doc.instances.put(instance1.name, instance1);
      Instance instance2 = new Instance("BarInstance");
      instance2.instanceOf = "Bar";
      doc.instances.put(instance2.name, instance2);
      sdExporter.export();
      List<InstanceDefinition> exported = exporter.export().instances;
      assertEquals(2, exported.size());
      assertEquals(2, loggerSpy.getAllMessages("warn").size());
      assertMatches("following instance\\(s\\) of custom resources:.*- FooInstance.*- BarInstance", "s", loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("InstanceExporter #export should NOT log a warning when exporting an instance of a logical model")
    void shouldNOTLogAWarningWhenExportingAnInstanceOfALogicalModel() {
      Logical logical = new Logical("FooLogical");
      doc.logicals.put(logical.name, logical);
      Instance instance = new Instance("FooInstance");
      instance.instanceOf = "FooLogical";
      doc.instances.put(instance.name, instance);
      sdExporter.export();
      List<InstanceDefinition> exported = exporter.export().instances;
      assertEquals(1, exported.size());
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

  }
  @DisplayName("#insertRules")
  @Nested
  class InsertRules {
    Instance instance;
    Instance patientInstance;
    RuleSet ruleSet;

    @BeforeEach
    void beforeEach() {
      instance = new Instance("Foo");
      instance.instanceOf = "Organization";
      doc.instances.put(instance.name, instance);
      patientInstance = new Instance("TestPatient");
      patientInstance.instanceOf = "Patient";
      doc.instances.put(patientInstance.name, patientInstance);
      ruleSet = new RuleSet("Bar");
      doc.ruleSets.put(ruleSet.name, ruleSet);
    }

    @Test
    @DisplayName("InstanceExporter #insertRules should apply rules from an insert rule")
    void shouldApplyRulesFromAnInsertRule() {
      // RuleSet: Bar
      // * id = "my-id"
      //
      // Instance: Foo
      // InstanceOf: Resource
      // * insert Bar
      AssignmentRule valueRule = new AssignmentRule("id");
      valueRule.value = "my-id";
      ruleSet.rules.add(valueRule);
      InsertRule insertRule = new InsertRule("");
      insertRule.ruleSet = "Bar";
      instance.rules.add(insertRule);
      exporter.applyInsertRules();
      InstanceDefinition exported = exporter.exportInstance(instance);
      assertEquals("my-id", at(exported, "id"));
    }

    @Test
    @DisplayName("InstanceExporter #insertRules should assign elements from a rule set with soft indexing used within a path")
    void shouldAssignElementsFromARuleSetWithSoftIndexingUsedWithinAPath() {
      AssignmentRule assignedValRule = new AssignmentRule("name[+].given");
      assignedValRule.value = "John";
      ruleSet.rules.add(assignedValRule);
      AssignmentRule assignedValRule2 = new AssignmentRule("name[=].family");
      assignedValRule2.value = "Johnson";
      ruleSet.rules.add(assignedValRule2);
      AssignmentRule assignedValRule3 = new AssignmentRule("name[+].given");
      assignedValRule3.value = "Johnny";
      ruleSet.rules.add(assignedValRule3);
      AssignmentRule assignedValRule4 = new AssignmentRule("name[=].family");
      assignedValRule4.value = "Jackson";
      ruleSet.rules.add(assignedValRule4);
      InsertRule insertRule = new InsertRule("");
      insertRule.ruleSet = "Bar";
      patientInstance.rules.add(insertRule);
      exporter.applyInsertRules();
      InstanceDefinition exported = exporter.exportInstance(patientInstance);
      assertJsonEqual("""
        [
          {
            "given": [
              "John"
            ],
            "family": "Johnson"
          },
          {
            "given": [
              "Johnny"
            ],
            "family": "Jackson"
          }
        ]
        """, at(exported, "name"));
    }

    @Test
    @DisplayName("InstanceExporter #insertRules should log an error and not apply rules from an invalid insert rule")
    void shouldLogAnErrorAndNotApplyRulesFromAnInvalidInsertRule() {
      // RuleSet: Bar
      // * ^title = "Wow fancy"
      // * id = "my-id"
      //
      // Instance: Foo
      // InstanceOf: Resource
      // * insert Bar
      CaretValueRule caretRule = new CaretValueRule("").withFile("Caret.fsh").withLocation(new int[] {1, 2, 3, 4});
      caretRule.caretPath = "title";
      caretRule.value = "Wow fancy";
      AssignmentRule valueRule = new AssignmentRule("id");
      valueRule.value = "my-id";
      ruleSet.rules.addAll(Arrays.asList(caretRule, valueRule));
      InsertRule insertRule = new InsertRule("").withFile("Insert.fsh").withLocation(new int[] {5, 6, 7, 8});
      insertRule.ruleSet = "Bar";
      instance.rules.add(insertRule);
      exporter.applyInsertRules();
      InstanceDefinition exported = exporter.exportInstance(instance);
      // valueRule is still applied
      assertEquals("my-id", at(exported, "id"));
      assertMatches("CaretValueRule.*Instance.*File: Caret\\.fsh.*Line: 1 - 3.*Applied in File: Insert\\.fsh.*Applied on Line: 5 - 7", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("InstanceExporter #insertRules should populate title and description when specified for instances with #definition")
    void shouldPopulateTitleAndDescriptionWhenSpecifiedForInstancesWithDefinition() {
      // Instance: DemoQuestionnaire
      // InstanceOf: Questionnaire
      // Usage: #definition
      // Title: "My Demo Questionnaire"
      // Description: "My Demo Questionnaire's description"
      //* name = "DemoQuestionnaire"
      //* status = #draft
      Instance goalInstance = new Instance("DemoQuestionnaire");
      goalInstance.instanceOf = "Questionnaire";
      goalInstance.usage = InstanceUsage.Definition;
      goalInstance.title = "My Demo Questionnaire";
      goalInstance.description = "My Demo Questionnaire's description";
      AssignmentRule statusDraft = new AssignmentRule("status");
      statusDraft.value = new FshCode("draft");
      AssignmentRule nameDemo = new AssignmentRule("name");
      nameDemo.value = new FshCode("DemoQuestionnaire");
      goalInstance.rules.addAll(Arrays.asList(statusDraft, nameDemo));
      InstanceDefinition exported = exportInstance.apply(goalInstance);
      assertContainsText("My Demo Questionnaire", (String) at(exported, "title"));
      assertContainsText("My Demo Questionnaire's description", (String) at(exported, "description"));
    }

    @Test
    @DisplayName("InstanceExporter #insertRules should not populate title and description when specified for instances that aren't #definition")
    void shouldNotPopulateTitleAndDescriptionWhenSpecifiedForInstancesThatArenTDefinition() {
      // Instance: DemoQuestionnaire
      // InstanceOf: Questionnaire
      // Usage: #example
      // Title: "My Demo Questionnaire"
      // Description: "My Demo Questionnaire's description"
      //* name = "DemoQuestionnaire"
      //* status = #draft
      Instance goalInstance = new Instance("DemoQuestionnaire");
      goalInstance.instanceOf = "Questionnaire";
      goalInstance.usage = InstanceUsage.Example;
      goalInstance.title = "My Demo Questionnaire";
      goalInstance.description = "My Demo Questionnaire's description";
      AssignmentRule statusDraft = new AssignmentRule("status");
      statusDraft.value = new FshCode("draft");
      AssignmentRule nameDemo = new AssignmentRule("name");
      nameDemo.value = new FshCode("DemoQuestionnaire");
      goalInstance.rules.addAll(Arrays.asList(statusDraft, nameDemo));
      InstanceDefinition exported = exportInstance.apply(goalInstance);
      assertNull(at(exported, "title"));
      assertNull(at(exported, "description"));
    }

    @Test
    @DisplayName("InstanceExporter #insertRules should not populate title and description for instances that don't have title or description (like Patient)")
    void shouldNotPopulateTitleAndDescriptionForInstancesThatDonTHaveTitleOrDescriptionLi() {
      // Instance: JustAPatient
      // InstanceOf: Patient
      // Usage: #definition
      // Title: "Just a Patient"
      // Description: "This is just a patient"
      Instance goalInstance = new Instance("JustAPatient");
      goalInstance.instanceOf = "Patient";
      goalInstance.usage = InstanceUsage.Definition;
      goalInstance.title = "Just a Patient";
      goalInstance.description = "This is just a patient";
      InstanceDefinition exported = exportInstance.apply(goalInstance);
      assertNull(at(exported, "title"));
      assertNull(at(exported, "description"));
    }

  }
  @DisplayName("#fishForMetadata")
  @Nested
  class FishForMetadata {
    /** jest.spyOn(fisher, 'fishForMetadata'): the calls (item, types) made through a recording TestFisher. */
    List<Object[]> fisherSpy = new ArrayList<>();

    @BeforeEach
    void beforeEach() {
      TestFisher spy = new TestFisher(tank, defs, pkg) {
        @Override public Metadata fishForMetadata(String item, FishableType... types) {
          fisherSpy.add(new Object[] {item, types});
          return super.fishForMetadata(item, types);
        }
      };
      exporter = new InstanceExporter(tank, pkg, spy);
    }

    @Test
    @DisplayName("InstanceExporter #fishForMetadata should use the passed in fisher to fish metadata for instances")
    void shouldUseThePassedInFisherToFishMetadataForInstances() {
      assertNull(exporter.fishForMetadata("some-item"));
      assertTrue(fisherSpy.stream().anyMatch(c -> c[0].equals("some-item")
          && Arrays.equals((FishableType[]) c[1], new FishableType[] {FishableType.Instance})));
    }

  }
  @DisplayName("#fishForMetadatas")
  @Nested
  class FishForMetadatas {
    /** jest.spyOn(fisher, 'fishForMetadatas'): the calls (item, types) made through a recording TestFisher. */
    List<Object[]> fisherSpy = new ArrayList<>();

    @BeforeEach
    void beforeEach() {
      TestFisher spy = new TestFisher(tank, defs, pkg) {
        @Override public List<Metadata> fishForMetadatas(String item, FishableType... types) {
          fisherSpy.add(new Object[] {item, types});
          return super.fishForMetadatas(item, types);
        }
      };
      exporter = new InstanceExporter(tank, pkg, spy);
    }

    @Test
    @DisplayName("InstanceExporter #fishForMetadatas should use the passed in fisher to fish metadatas for instances")
    void shouldUseThePassedInFisherToFishMetadatasForInstances() {
      assertEquals(0, len(exporter.fishForMetadatas("some-item")));
      assertTrue(fisherSpy.stream().anyMatch(c -> c[0].equals("some-item")
          && Arrays.equals((FishableType[]) c[1], new FishableType[] {FishableType.Instance})));
    }

  }
}
@DisplayName("InstanceExporter R5")
class InstanceExporterR5Test {
  static TestFHIRDefinitions defs;
  FSHDocument doc;
  StructureDefinitionExporter sdExporter;
  InstanceExporter exporter;
  java.util.function.Function<Instance, InstanceDefinition> exportInstance;

  @BeforeAll
  static void beforeAll() {
    defs = getTestFHIRDefinitions(false, testDefsPath("r5-definitions"));
  }

  @BeforeEach
  void beforeEach() {
    loggerSpy.reset();
    doc = new FSHDocument("fileName");
    FSHTank input = new FSHTank(new ArrayList<>(Arrays.asList(doc)), MinimalConfig.minimalConfig());
    Package pkg = new Package(input.config);
    TestFisher fisher = new TestFisher(input, defs, pkg);
    sdExporter = new StructureDefinitionExporter(input, pkg, fisher);
    exporter = new InstanceExporter(input, pkg, fisher);
    exportInstance = (instance) -> {
  sdExporter.export();
  return exporter.exportInstance(instance);
};
  }

  @DisplayName("#exportInstance")
  @Nested
  class ExportInstance {

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
    }

    // Assignment on CodeableReference
    @Test
    @DisplayName("InstanceExporter R5 #exportInstance should set the reference child element when assigning a Reference directly to a CodeableReference")
    void shouldSetTheReferenceChildElementWhenAssigningAReferenceDirectlyToACodeableRefer() {
      Instance condition = new Instance("TestCondition");
      condition.instanceOf = "Condition";
      AssignmentRule assignedIdRule = new AssignmentRule("id");
      assignedIdRule.value = "condition-id";
      condition.rules.add(assignedIdRule);
      Instance carePlan = new Instance("TestCarePlan");
      carePlan.instanceOf = "CarePlan";
      AssignmentRule assignedRefRule = new AssignmentRule("addresses");
      assignedRefRule.value = new FshReference("TestCondition");
      carePlan.rules.add(assignedRefRule);
      doc.instances.put(condition.name, condition);
      doc.instances.put(carePlan.name, carePlan);
      InstanceDefinition exported = exportInstance.apply(carePlan);
      assertJsonEqual("""
        {
          "reference": {
            "reference": "Condition/condition-id"
          }
        }
        """, at(exported, "addresses[0]"));
    }

    @Test
    @DisplayName("InstanceExporter R5 #exportInstance should set the concept child element when assigning a code directly to a CodeableReference")
    void shouldSetTheConceptChildElementWhenAssigningACodeDirectlyToACodeableReference() {
      Instance carePlan = new Instance("TestCarePlan");
      carePlan.instanceOf = "CarePlan";
      AssignmentRule assignedRefRule = new AssignmentRule("addresses");
      assignedRefRule.value = new FshCode("foo");
      carePlan.rules.add(assignedRefRule);
      doc.instances.put(carePlan.name, carePlan);
      InstanceDefinition exported = exportInstance.apply(carePlan);
      assertJsonEqual("""
        {
          "concept": {
            "coding": [
              {
                "code": "foo"
              }
            ]
          }
        }
        """, at(exported, "addresses[0]"));
    }

    @Test
    @DisplayName("InstanceExporter R5 #exportInstance should set both reference and concept when assigning directly to a CodeableReference")
    void shouldSetBothReferenceAndConceptWhenAssigningDirectlyToACodeableReference() {
      Instance condition = new Instance("TestCondition");
      condition.instanceOf = "Condition";
      AssignmentRule assignedIdRule = new AssignmentRule("id");
      assignedIdRule.value = "condition-id";
      condition.rules.add(assignedIdRule);
      Instance carePlan = new Instance("TestCarePlan");
      carePlan.instanceOf = "CarePlan";
      AssignmentRule assignedRefRule = new AssignmentRule("addresses");
      assignedRefRule.value = new FshReference("TestCondition");
      AssignmentRule assignedConceptRule = new AssignmentRule("addresses");
      assignedConceptRule.value = new FshCode("foo");
      carePlan.rules.addAll(Arrays.asList(assignedRefRule, assignedConceptRule));
      doc.instances.put(condition.name, condition);
      doc.instances.put(carePlan.name, carePlan);
      InstanceDefinition exported = exportInstance.apply(carePlan);
      assertJsonEqual("""
        {
          "reference": {
            "reference": "Condition/condition-id"
          },
          "concept": {
            "coding": [
              {
                "code": "foo"
              }
            ]
          }
        }
        """, at(exported, "addresses[0]"));
    }

    @Test
    @DisplayName("InstanceExporter R5 #exportInstance should set both concept and reference when assigning directly to a CodeableReference")
    void shouldSetBothConceptAndReferenceWhenAssigningDirectlyToACodeableReference() {
      Instance condition = new Instance("TestCondition");
      condition.instanceOf = "Condition";
      AssignmentRule assignedIdRule = new AssignmentRule("id");
      assignedIdRule.value = "condition-id";
      condition.rules.add(assignedIdRule);
      Instance carePlan = new Instance("TestCarePlan");
      carePlan.instanceOf = "CarePlan";
      AssignmentRule assignedConceptRule = new AssignmentRule("addresses");
      assignedConceptRule.value = new FshCode("foo");
      AssignmentRule assignedRefRule = new AssignmentRule("addresses");
      assignedRefRule.value = new FshReference("TestCondition");
      carePlan.rules.addAll(Arrays.asList(assignedConceptRule, assignedRefRule));
      doc.instances.put(condition.name, condition);
      doc.instances.put(carePlan.name, carePlan);
      InstanceDefinition exported = exportInstance.apply(carePlan);
      assertJsonEqual("""
        {
          "reference": {
            "reference": "Condition/condition-id"
          },
          "concept": {
            "coding": [
              {
                "code": "foo"
              }
            ]
          }
        }
        """, at(exported, "addresses[0]"));
    }

    // Assigning References
    @Test
    @DisplayName("InstanceExporter R5 #exportInstance should assign a reference while resolving the Instance being referred to on a CodeableReference")
    void shouldAssignAReferenceWhileResolvingTheInstanceBeingReferredToOnACodeableReferen() {
      Instance condition = new Instance("TestCondition");
      condition.instanceOf = "Condition";
      AssignmentRule assignedIdRule = new AssignmentRule("id");
      assignedIdRule.value = "condition-id";
      condition.rules.add(assignedIdRule);
      Instance carePlan = new Instance("TestCarePlan");
      carePlan.instanceOf = "CarePlan";
      AssignmentRule assignedRefRule = new AssignmentRule("addresses.reference");
      assignedRefRule.value = new FshReference("TestCondition");
      carePlan.rules.add(assignedRefRule);
      doc.instances.put(condition.name, condition);
      doc.instances.put(carePlan.name, carePlan);
      InstanceDefinition exported = exportInstance.apply(carePlan);
      assertJsonEqual("""
        {
          "reference": "Condition/condition-id"
        }
        """, at(exported, "addresses[0].reference"));
    }

    @Test
    @DisplayName("InstanceExporter R5 #exportInstance should log an error when an invalid reference is assigned on a CodeableReference")
    void shouldLogAnErrorWhenAnInvalidReferenceIsAssignedOnACodeableReference() {
      Instance patient = new Instance("TestPatient");
      patient.instanceOf = "Patient";
      Instance carePlan = new Instance("TestCarePlan");
      carePlan.instanceOf = "CarePlan";
      AssignmentRule assignedRefRule = new AssignmentRule("addresses.reference");
      assignedRefRule.value = new FshReference("TestPatient");
      carePlan.rules.add(assignedRefRule);
      doc.instances.put(patient.name, patient);
      doc.instances.put(carePlan.name, carePlan);
      InstanceDefinition exported = exportInstance.apply(carePlan);
      assertNull(at(exported, "addresses"));
      assertMatches("The type \"Reference\\(Patient\\)\" does not match any of the allowed types\\D*", "s", loggerSpy.getMessageAtIndex(0, "error"));
    }

  }
}
