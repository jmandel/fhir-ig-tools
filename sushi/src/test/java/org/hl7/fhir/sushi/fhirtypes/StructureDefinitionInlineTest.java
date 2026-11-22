package org.hl7.fhir.sushi.fhirtypes;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.json;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hl7.fhir.sushi.errors.ValidationError;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

// port of sushi/test/fhirtypes/StructureDefinition.test.ts lines 2080-2571 (SUSHI 3.21.1): #validateValueAtPath
// #Inline Instances, #captureOriginalElements(), #clearOriginalElements(), #getReferenceOrCanonicalName, #valid
@DisplayName("StructureDefinition")
class StructureDefinitionInlineTest {
  TestFisher fisher;
  StructureDefinition observation;
  StructureDefinition modifiedObservation;
  StructureDefinition planDefinition;
  StructureDefinition usCoreObservation;
  StructureDefinition resprate;

  @BeforeEach
  void setUpTop() {
    SdTestSupport.init();
    fisher = SdTestSupport.fisher();
    loggerSpy.reset();
    observation = StructureDefinition.fromJSON(SdTestSupport.jsonObservation);
    modifiedObservation = StructureDefinition.fromJSON(SdTestSupport.jsonModifiedObservation);
    planDefinition = StructureDefinition.fromJSON(SdTestSupport.jsonPlanDefinition);
    usCoreObservation = StructureDefinition.fromJSON(SdTestSupport.jsonUsCoreObservation);
    resprate = fisher.fishForStructureDefinition("resprate");
  }

  static PathPart pp(String base, String... brackets) {
    PathPart p = new PathPart(base);
    if (brackets.length > 0) p.brackets = new ArrayList<>(List.of(brackets));
    return p;
  }

  static PathPart prim(String base) {
    return new PathPart(base).withPrimitive(true);
  }

  static void assertThrowsMessage(String expectedSubstring, Executable e) {
    Throwable t = assertThrows(Throwable.class, e);
    assertTrue(t.getMessage() != null && t.getMessage().contains(expectedSubstring),
        () -> "expected message containing <" + expectedSubstring + "> but was <" + t.getMessage() + ">");
  }

  static void assertThrowsMatching(String jsRegex, Executable e) {
    Throwable t = assertThrows(Throwable.class, e);
    assertMatches(jsRegex, t.getMessage());
  }

  @Nested
  @DisplayName("#validateValueAtPath")
  class ValidateValueAtPath {
    StructureDefinition structureDefinition;
    StructureDefinition respRate;
    StructureDefinition CSSPC;
    StructureDefinition bundle;

    @BeforeEach
    void setUpValidate() {
      structureDefinition = fisher.fishForStructureDefinition("StructureDefinition");
      respRate = fisher.fishForStructureDefinition("resprate");
      bundle = fisher.fishForStructureDefinition("Bundle");
      CSSPC = fisher.fishForStructureDefinition("capabilitystatement-search-parameter-combination");
    }

    @Nested
    @DisplayName("#Inline Instances")
    class InlineInstances {
      @BeforeEach
      void setUpInline() {
        ElementDefinition contained = respRate.findElementByPath("contained", fisher);
        contained.sliceIt("type", "$this", false, "open");
        contained.addSlice("PatientsOnly");
        ElementDefinition containedPatients = respRate.findElementByPath("contained[PatientsOnly]", fisher);
        OnlyRule patientRule = new OnlyRule("contained[PatientsOnly]");
        patientRule.types = new ArrayList<>(List.of(new OnlyRuleType("Patient")));
        containedPatients.constrainType(patientRule, fisher);

        contained.addSlice("DomainsOnly");
        ElementDefinition containedDomains = respRate.findElementByPath("contained[DomainsOnly]", fisher);
        OnlyRule domainRule = new OnlyRule("contained[DomainsOnly]");
        domainRule.types = new ArrayList<>(List.of(new OnlyRuleType("DomainResource")));
        containedDomains.constrainType(domainRule, fisher);

        contained.addSlice("PatientOrObservation");
        ElementDefinition containedChoice = respRate.findElementByPath("contained[PatientOrObservation]", fisher);
        OnlyRule choiceRule = new OnlyRule("contained[PatientOrObservation]");
        choiceRule.types = new ArrayList<>(List.of(new OnlyRuleType("Patient"), new OnlyRuleType("Observation")));
        containedChoice.constrainType(choiceRule, fisher);
      }

      Object assigned(StructureDefinition sd, String path, Object value) {
        return sd.validateValueAtPath(path, value, fisher).assignedValue();
      }

      InstanceDefinition withResourceType(String rt) {
        InstanceDefinition instanceDef = new InstanceDefinition();
        instanceDef.put("resourceType", rt);
        return instanceDef;
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow assigning a Patient type InstanceDefinition to a Resource element")
      void t1() {
        Object a = assigned(respRate, "contained[0]", withResourceType("Patient"));
        assertEquals("Patient", Common.getProp(a, "resourceType"));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow assigning a Patient type InstanceDefinition to a DomainResource element")
      void t2() {
        Object a = assigned(respRate, "contained[DomainsOnly][0]", withResourceType("Patient"));
        assertEquals("Patient", Common.getProp(a, "resourceType"));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow assigning a Patient type InstanceDefinition to a Patient element")
      void t3() {
        Object a = assigned(respRate, "contained[PatientsOnly][0]", withResourceType("Patient"));
        assertEquals("Patient", Common.getProp(a, "resourceType"));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow assigning a Patient type InstanceDefinition to a choice element that includes Patient")
      void t4() {
        Object a = assigned(respRate, "contained[PatientOrObservation][0]", withResourceType("Patient"));
        assertEquals("Patient", Common.getProp(a, "resourceType"));
      }

      InstanceDefinition bundleOfJoy() {
        InstanceDefinition instanceDef = new InstanceDefinition();
        instanceDef.put("id", "OfJoy");
        instanceDef.put("resourceType", "Bundle");
        return instanceDef;
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow assigning a Bundle type InstanceDefinition to a Resource element")
      void t5() {
        Object a = assigned(respRate, "contained[0]", bundleOfJoy());
        assertEquals("Bundle", Common.getProp(a, "resourceType"));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow assigning a Bundle type InstanceDefinition to a DomainResource element")
      void t6() {
        InstanceDefinition b = bundleOfJoy();
        assertThrowsMatching("Bundle.*OfJoy.*DomainResource",
            () -> respRate.validateValueAtPath("contained[DomainsOnly][0]", b, fisher));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow assigning a Bundle type InstanceDefinition to a Patient element")
      void t7() {
        InstanceDefinition b = bundleOfJoy();
        assertThrowsMatching("Bundle.*OfJoy.*Patient",
            () -> respRate.validateValueAtPath("contained[PatientsOnly][0]", b, fisher));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow assigning a Bundle type InstanceDefinition to a choice element that does not include Bundle")
      void t8() {
        InstanceDefinition b = bundleOfJoy();
        assertThrowsMatching("Bundle.*OfJoy.*Patient, Observation",
            () -> respRate.validateValueAtPath("contained[PatientOrObservation][0]", b, fisher));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow assigning a CodeableConcept type InstanceDefinition to a CodeableConcept element")
      void t9() {
        InstanceDefinition instanceDef = new InstanceDefinition();
        instanceDef.put("coding", json("[{\"value\": \"#5\"}]"));
        instanceDef._instanceMeta.sdType = "CodeableConcept";
        assertJsEqual(json("{\"coding\": [{\"value\": \"#5\"}]}"), assigned(respRate, "code", instanceDef));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow assigning a specialization of a type to a type")
      void t10() {
        InstanceDefinition instanceDef = new InstanceDefinition();
        instanceDef.put("value", 5.0);
        instanceDef._instanceMeta.sdType = "Age";
        assertJsEqual(json("{\"value\": 5}"), assigned(respRate, "valueQuantity", instanceDef));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow assigning a profile of a type to a type")
      void t11() {
        InstanceDefinition instanceDef = new InstanceDefinition();
        instanceDef.put("value", 5.0);
        instanceDef._instanceMeta.sdType = "SimpleQuantity";
        assertJsEqual(json("{\"value\": 5}"), assigned(respRate, "valueQuantity", instanceDef));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow assigning a type to a choice type element")
      void t12() {
        InstanceDefinition instanceDef = new InstanceDefinition();
        instanceDef.put("value", 5.0);
        instanceDef._instanceMeta.sdType = "Quantity";
        assertJsEqual(json("{\"value\": 5}"), assigned(observation, "value[x]", instanceDef));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow assigning a type that is not in the choice to a choice type element")
      void t13() {
        InstanceDefinition instanceDef = new InstanceDefinition();
        instanceDef.put("value", 5.0);
        instanceDef._instanceMeta.sdType = "Money";
        instanceDef.put("id", "Cash");
        assertThrowsMessage("Cannot assign Money value: Cash. Value does not match element type: ",
            () -> observation.validateValueAtPath("value[x]", instanceDef, fisher));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow assigning a type to a non-matching type")
      void t14() {
        InstanceDefinition instanceDef = new InstanceDefinition();
        instanceDef.put("value", 5.0);
        instanceDef._instanceMeta.sdType = "Quantity";
        instanceDef.put("id", "Foo");
        assertThrowsMessage("Cannot assign Quantity value: Foo. Value does not match element type: CodeableConcept",
            () -> respRate.validateValueAtPath("code", instanceDef, fisher));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow assigning a parent of a type to a type")
      void t15() {
        InstanceDefinition instanceDef = new InstanceDefinition();
        instanceDef._instanceMeta.sdType = "Element";
        instanceDef.put("id", "Foo");
        assertThrowsMessage("Cannot assign Element value: Foo. Value does not match element type: CodeableConcept",
            () -> respRate.validateValueAtPath("code", instanceDef, fisher));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow assigning an extension of to an extension element")
      void t16() {
        InstanceDefinition instanceDef = new InstanceDefinition();
        instanceDef.put("valueString", "hello");
        instanceDef._instanceMeta.sdType = "Extension";
        assertJsEqual(json("{\"valueString\": \"hello\"}"), assigned(respRate, "extension", instanceDef));
      }

      // Overriding elements
      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow replacing parts of a Resource element")
      void t17() {
        assertEquals("French", assigned(respRate, "contained[0].language", new FshCode("French")));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow replacing parts of a Patient element")
      void t18() {
        assertEquals("F", assigned(respRate, "contained[PatientsOnly][0].gender", new FshCode("F")));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow overriding a Resource with a Patient")
      void t19() {
        var r = respRate.validateValueAtPath("contained[0].gender", new FshCode("F"), fisher,
            new ArrayList<>(List.of("Patient")));
        assertEquals("F", r.assignedValue());
        assertEquals(List.of(pp("contained", "0"), prim("gender")), r.pathParts());
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow overriding a Resource with a Patient within a Resource overriden by a Bundle")
      void t20() {
        var r = respRate.validateValueAtPath("contained[0].entry[0].resource.gender", new FshCode("F"), fisher,
            new ArrayList<>(Arrays.asList("Bundle", null, "Patient", null)));
        assertEquals("F", r.assignedValue());
        assertEquals(List.of(pp("contained", "0"), pp("entry", "0"), pp("resource"), prim("gender")),
            r.pathParts());
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow overriding a Resource with a Patient within a Resource overriden by a Bundle within a Bundle")
      void t21() {
        var r = respRate.validateValueAtPath("contained[0].entry[0].resource.entry[0].resource.gender",
            new FshCode("F"), fisher, new ArrayList<>(Arrays.asList("Bundle", null, "Bundle", null, "Patient", null)));
        assertEquals("F", r.assignedValue());
        assertEquals(List.of(pp("contained", "0"), pp("entry", "0"), pp("resource"), pp("entry", "0"),
            pp("resource"), prim("gender")), r.pathParts());
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow overriding a Resource with a Profile")
      void t22() {
        var r = respRate.validateValueAtPath("contained[0].valueQuantity.unit", "slugs", fisher,
            new ArrayList<>(Arrays.asList("http://hl7.org/fhir/StructureDefinition/resprate", null, null)));
        assertEquals("slugs", r.assignedValue());
        assertEquals(List.of(pp("contained", "0"), pp("valueQuantity"), prim("unit")), r.pathParts());
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow overriding a Resource constrained to Patient with a non-Patient path")
      void t23() {
        assertThrowsMessage("The element or path you referenced does not exist: contained[0].system",
            () -> respRate.validateValueAtPath("contained[0].system", "http://hello.com", fisher,
                new ArrayList<>(List.of("Patient"))));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow overriding a Resource constrained to Patient with a non-Patient path inside a Resource")
      void t24() {
        assertThrowsMessage(
            "The element or path you referenced does not exist: contained[0].entry[0].resource.system",
            () -> respRate.validateValueAtPath("contained[0].entry[0].resource.system", "http://hello.com", fisher,
                new ArrayList<>(Arrays.asList("Bundle", null, "Patient", null))));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow overriding a Patient with an Observation")
      void t25() {
        FshCode method = new FshCode("man", "http://method.com");
        assertThrowsMessage("The element or path you referenced does not exist: contained[PatientsOnly].method",
            () -> respRate.validateValueAtPath("contained[PatientsOnly].method", method, fisher,
                new ArrayList<>(List.of("Observation"))));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow overriding a Resource constrained to a non-FHIR Resource")
      void t26() {
        assertThrowsMessage("The element or path you referenced does not exist: contained[0].system",
            () -> respRate.validateValueAtPath("contained[0].system", "http://hello.com", fisher,
                new ArrayList<>(List.of("CodeableConcept"))));
      }

      // resourceType
      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow a valid FHIR resourceType to be set on a Resource element")
      void t27() {
        var r = respRate.validateValueAtPath("contained[0].resourceType", "Patient", fisher);
        assertEquals("Patient", r.assignedValue());
        assertEquals(List.of(pp("contained", "0"), pp("resourceType")), r.pathParts());
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow a valid FHIR resourceType to be set on a DomainResource element")
      void t28() {
        var r = respRate.validateValueAtPath("contained[DomainsOnly][0].resourceType", "Patient", fisher);
        assertEquals("Patient", r.assignedValue());
        assertEquals(List.of(pp("contained", "DomainsOnly", "0").withSlices("DomainsOnly"), pp("resourceType")),
            r.pathParts());
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should allow a valid FHIR resourceType to be set on a Patient element")
      void t29() {
        var r = respRate.validateValueAtPath("contained[PatientsOnly][0].resourceType", "Patient", fisher);
        assertEquals("Patient", r.assignedValue());
        assertEquals(List.of(pp("contained", "PatientsOnly", "0").withSlices("PatientsOnly"), pp("resourceType")),
            r.pathParts());
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow a resourceType to be set on a non-Resource element")
      void t30() {
        assertThrowsMessage("A resourceType of CodeableConcept cannot be set on an element of type CodeableConcept.",
            () -> respRate.validateValueAtPath("code.resourceType", "CodeableConcept", fisher));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow an invalid FHIR resourceType to be set on a DomainResource element")
      void t31() {
        assertThrowsMessage("A resourceType of Bundle cannot be set on an element of type DomainResource.",
            () -> respRate.validateValueAtPath("contained[DomainsOnly][0].resourceType", "Bundle", fisher));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow an invalid FHIR resourceType to be set on a Patient element")
      void t32() {
        assertThrowsMessage("A resourceType of Resource cannot be set on an element of type Patient.",
            () -> respRate.validateValueAtPath("contained[PatientsOnly][0].resourceType", "Resource", fisher));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow a profile resourceType to be set on a Resource element")
      void t33() {
        assertThrowsMessage("A resourceType of resprate cannot be set on an element of type Resource",
            () -> respRate.validateValueAtPath("contained[0].resourceType", "resprate", fisher));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow an invalid FHIR resourceType to be set on a choice element")
      void t34() {
        assertThrowsMessage("The element or path you referenced does not exist: component.value[x].resourceType",
            () -> respRate.validateValueAtPath("component.value[x].resourceType", "Patient", fisher));
      }

      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should not allow a resourceType to be set at the top level of the instance")
      void t35() {
        assertThrowsMessage("The element or path you referenced does not exist: resourceType",
            () -> respRate.validateValueAtPath("resourceType", "Patient", fisher));
      }

      // https://github.com/FHIR/sushi/issues/1020
      @Test
      @DisplayName("StructureDefinition #validateValueAtPath #Inline Instances should throw when attempting to access extension on a bundle")
      void t36() {
        assertThrowsMessage("The element or path you referenced does not exist: extension.url",
            () -> bundle.validateValueAtPath("extension.url", "test", fisher));
      }
    }
  }

  ElementDefinition valueX() {
    for (ElementDefinition e : observation.elements) {
      if ("Observation.value[x]".equals(e.getId())) return e;
    }
    return null;
  }

  @Nested
  @DisplayName("#captureOriginalElements()")
  class CaptureOriginalElements {
    @Test
    @DisplayName("StructureDefinition #captureOriginalElements() should create a new starting point for diffs")
    void t1() {
      ElementDefinition valueX = valueX();
      observation.captureOriginalElements();
      assertFalse(valueX.hasDiff());
      valueX.setMin(1.0);
      assertTrue(valueX.hasDiff());
    }
  }

  @Nested
  @DisplayName("#clearOriginalElements()")
  class ClearOriginalElements {
    @Test
    @DisplayName("StructureDefinition #clearOriginalElements() should remove the starting point for diffs, making everything diff")
    void t1() {
      ElementDefinition valueX = valueX();
      observation.captureOriginalElements();
      assertFalse(valueX.hasDiff());
      valueX.clearOriginal();
      assertTrue(valueX.hasDiff());
    }
  }

  @Nested
  @DisplayName("#getReferenceOrCanonicalName")
  class GetReferenceOrCanonicalName {
    ElementDefinition basedOn;
    ElementDefinition actDef;

    @BeforeEach
    void setUpRefs() {
      basedOn = observation.findElement("Observation.basedOn");
      actDef = planDefinition.findElement("PlanDefinition.action.definition[x]");
    }

    @Test
    @DisplayName("StructureDefinition #getReferenceOrCanonicalName should find the reference target when it exists")
    void t1() {
      assertEquals("MedicationRequest", observation.getReferenceOrCanonicalName("basedOn[MedicationRequest]", basedOn));
    }

    @Test
    @DisplayName("StructureDefinition #getReferenceOrCanonicalName should not find the reference target when it does not exist")
    void t2() {
      assertNull(observation.getReferenceOrCanonicalName("basedOn[foo]", basedOn));
    }

    @Test
    @DisplayName("StructureDefinition #getReferenceOrCanonicalName should not find the reference target when there are no brackets")
    void t3() {
      assertNull(observation.getReferenceOrCanonicalName("basedOn", basedOn));
    }

    @Test
    @DisplayName("StructureDefinition #getReferenceOrCanonicalName should find the canonical target when it exists")
    void t4() {
      assertEquals("ActivityDefinition",
          planDefinition.getReferenceOrCanonicalName("action.definition[x][ActivityDefinition]", actDef));
    }

    @Test
    @DisplayName("StructureDefinition #getReferenceOrCanonicalName should not find the reference target when it does not exist")
    void t5() {
      assertNull(planDefinition.getReferenceOrCanonicalName("action.definition[x][foo]", actDef));
    }

    @Test
    @DisplayName("StructureDefinition #getReferenceOrCanonicalName should not find the reference target when there are no brackets")
    void t6() {
      assertNull(planDefinition.getReferenceOrCanonicalName("action.definition[x]", actDef));
    }
  }

  @Nested
  @DisplayName("#valid")
  class Valid {
    @Test
    @DisplayName("StructureDefinition #valid should log an error when at least one element is invalid")
    void t1() {
      // TS: jest.spyOn(valueX, 'validate').mockReturnValue([...]); Java: swap in an element whose validate() is stubbed
      ElementDefinition valueX = valueX();
      int idx = observation.elements.indexOf(valueX);
      ElementDefinition stub = new ElementDefinition(valueX.getId()) {
        @Override public List<ValidationError> validate() {
          return new ArrayList<>(List.of(new ValidationError("issue", "path")));
        }
      };
      observation.elements.set(idx, stub);
      List<ValidationError> validationErrors = observation.validate();
      observation.elements.set(idx, valueX);
      assertEquals(1, validationErrors.size());
      assertMatches("Observation\\.value\\[x\\] \\^path: issue", validationErrors.get(0).getMessage());
    }
  }
}
