package org.hl7.fhir.sushi.fshtypes.rules;

import static org.hl7.fhir.sushi.fshtypes.Common.EOL;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.common.js.*;
import org.junit.jupiter.api.*;

// port of sushi/test/fshtypes/rules/AddElementRule.test.ts (SUSHI 3.21.1)
class AddElementRuleTest {
  @Nested
  @DisplayName("AddElementRule")
  class DAddelementrule1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("AddElementRule #constructor should set the properties correctly")
      void t3() throws Exception {
        var r = new AddElementRule("component.code");
        assertEquals("component.code", r.path);
        assertNull(r.min);
        assertNull(r.max);
        assertEquals(List.of(), r.types);
        assertNull(r.mustSupport);
        assertNull(r.summary);
        assertNull(r.modifier);
        assertNull(r.trialUse);
        assertNull(r.normative);
        assertNull(r.draft);
        assertNull(r.short_);
        assertNull(r.definition);
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Nested
      @DisplayName("#types")
      class DTypes5 {
        @Test
        @DisplayName("AddElementRule #toFSH #types should produce FSH for an AddElementRule with cardinality and one type")
        void t6() throws Exception {
          var rule = new AddElementRule("barley");
          rule.min = 0.0;
          rule.max = "*";
          rule.types.add(new OnlyRuleType("Quantity"));
          var expectedFSH = "* barley 0..* Quantity";
          assertEquals(expectedFSH, rule.toFSH());
        }
        @Test
        @DisplayName("AddElementRule #toFSH #types should produce FSH for an AddElementRule that contains flags")
        void t7() throws Exception {
          var rule = new AddElementRule("road.brick");
          rule.min = 0.0;
          rule.max = "7";
          rule.types.add(OnlyRuleType.reference("Material"));
          rule.modifier = true;
          rule.draft = true;
          var expectedFSH = "* road.brick 0..7 ?! D Reference(Material)";
          assertEquals(expectedFSH, rule.toFSH());
        }
        @Test
        @DisplayName("AddElementRule #toFSH #types should produce FSH for an AddElementRule that contains short text")
        void t8() throws Exception {
          var rule = new AddElementRule("barley");
          rule.min = 0.0;
          rule.max = "*";
          rule.types.add(new OnlyRuleType("Quantity"));
          rule.short_ = "Fresh barley";
          var expectedFSH = "* barley 0..* Quantity \"Fresh barley\" \"Fresh barley\"";
          assertEquals(expectedFSH, rule.toFSH());
        }
        @Test
        @DisplayName("AddElementRule #toFSH #types should produce FSH for an AddElementRule that contains short and definition text")
        void t9() throws Exception {
          var rule = new AddElementRule("barley");
          rule.min = 0.0;
          rule.max = "*";
          rule.types.add(new OnlyRuleType("Quantity"));
          rule.short_ = "Fresh barley";
          rule.definition = "Barley, one of the known ancient grains.";
          var expectedFSH = "* barley 0..* Quantity \"Fresh barley\" \"Barley, one of the known ancient grains.\"";
          assertEquals(expectedFSH, rule.toFSH());
        }
        @Test
        @DisplayName("AddElementRule #toFSH #types should produce FSH for an AddElementRule that contains multiple types")
        void t10() throws Exception {
          var rule = new AddElementRule("barley");
          rule.min = 0.0;
          rule.max = "*";
          rule.types.addAll(List.of(new OnlyRuleType("Quantity"), OnlyRuleType.reference("Specimen"), OnlyRuleType.canonical("Medication"), OnlyRuleType.codeableReference("Immunization")));
          var expectedFSH = "* barley 0..* Quantity or Reference(Specimen) or Canonical(Medication) or CodeableReference(Immunization)";
          assertEquals(expectedFSH, rule.toFSH());
        }
        @Test
        @DisplayName("AddElementRule #toFSH #types should produce FSH for an AddElementRule that contains flags, multiple types, short text, and definition text")
        void t11() throws Exception {
          var rule = new AddElementRule("barley");
          rule.min = 0.0;
          rule.max = "*";
          rule.types.addAll(List.of(new OnlyRuleType("Quantity"), OnlyRuleType.reference("Specimen"), OnlyRuleType.codeableReference("Immunization")));
          rule.trialUse = true;
          rule.mustSupport = true;
          rule.short_ = "Fresh barley";
          rule.definition = "Barley, one of the \"ancient grains.\"";
          var expectedFSH = "* barley 0..* MS TU Quantity or Reference(Specimen) or CodeableReference(Immunization) \"Fresh barley\" \"Barley, one of the \\\"ancient grains.\\\"\"";
          assertEquals(expectedFSH, rule.toFSH());
        }
      }
      @Nested
      @DisplayName("#contentReference")
      class DContentreference12 {
        @Test
        @DisplayName("AddElementRule #toFSH #contentReference should produce FSH for a content reference AddElementRule with cardinality")
        void t13() throws Exception {
          var rule = new AddElementRule("barley");
          rule.min = 0.0;
          rule.max = "*";
          rule.contentReference = "http://example.org/StructureDefinition/Grain#Grain.barley";
          var expectedFSH = "* barley 0..* contentReference http://example.org/StructureDefinition/Grain#Grain.barley";
          assertEquals(expectedFSH, rule.toFSH());
        }
        @Test
        @DisplayName("AddElementRule #toFSH #contentReference should produce FSH for a content reference AddElementRule that contains flags")
        void t14() throws Exception {
          var rule = new AddElementRule("road.brick");
          rule.min = 0.0;
          rule.max = "7";
          rule.contentReference = "http://example.org/StructureDefinition/Materials#Materials.brick";
          rule.modifier = true;
          rule.draft = true;
          var expectedFSH = "* road.brick 0..7 ?! D contentReference http://example.org/StructureDefinition/Materials#Materials.brick";
          assertEquals(expectedFSH, rule.toFSH());
        }
        @Test
        @DisplayName("AddElementRule #toFSH #contentReference should produce FSH for a content reference AddElementRule that contains short text")
        void t15() throws Exception {
          var rule = new AddElementRule("barley");
          rule.min = 0.0;
          rule.max = "*";
          rule.contentReference = "http://example.org/StructureDefinition/Grain#Grain.barley";
          rule.short_ = "Fresh barley";
          var expectedFSH = "* barley 0..* contentReference http://example.org/StructureDefinition/Grain#Grain.barley \"Fresh barley\" \"Fresh barley\"";
          assertEquals(expectedFSH, rule.toFSH());
        }
        @Test
        @DisplayName("AddElementRule #toFSH #contentReference should produce FSH for a contentReference AddElementRule that contains short and definition text")
        void t16() throws Exception {
          var rule = new AddElementRule("barley");
          rule.min = 0.0;
          rule.max = "*";
          rule.contentReference = "http://example.org/StructureDefinition/Grain#Grain.barley";
          rule.short_ = "Fresh barley";
          rule.definition = "Barley, one of the known ancient grains.";
          var expectedFSH = "* barley 0..* contentReference http://example.org/StructureDefinition/Grain#Grain.barley \"Fresh barley\" \"Barley, one of the known ancient grains.\"";
          assertEquals(expectedFSH, rule.toFSH());
        }
      }
    }
  }
}
