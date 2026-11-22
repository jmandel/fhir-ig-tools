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

// port of sushi/test/fshtypes/rules/ValueSetComponentRule.test.ts (SUSHI 3.21.1)
class ValueSetComponentRuleTest {
  @Nested
  @DisplayName("ValueSetComponentRule")
  class DValuesetcomponentrule1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("ValueSetComponentRule #constructor should set the properties correctly")
      void t3() throws Exception {
        var v = new ValueSetComponentRule(true);
        assertEquals(true, v.inclusion);
        // toEqual({}): no properties set
        assertNull(v.from.system);
        assertNull(v.from.valueSets);
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh4 {
      @Test
      @DisplayName("ValueSetComponentRule #toFSH should produce FSH for a ValueSetComponentRule")
      void t5() throws Exception {
        var v = new ValueSetComponentRule(true);
        v.from.system = "SomeSystem";
        v.from.valueSets = List.of("VSOne", "VSTwo");
        assertEquals("* include codes from system SomeSystem and valueset VSOne and VSTwo", v.toFSH());
      }
    }
  }
  @Nested
  @DisplayName("ValueSetConceptComponentRule")
  class DValuesetconceptcomponentrule6 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor7 {
      @Test
      @DisplayName("ValueSetConceptComponentRule #constructor should set the properties correctly")
      void t8() throws Exception {
        var v = new ValueSetConceptComponentRule(true);
        assertEquals(true, v.inclusion);
        // toEqual({}): no properties set
        assertNull(v.from.system);
        assertNull(v.from.valueSets);
        assertEquals(List.of(), v.concepts);
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh9 {
      @Test
      @DisplayName("ValueSetConceptComponentRule #toFSH should produce FSH for a ValueSetConceptComponentRule with a single concept")
      void t10() throws Exception {
        var rule = new ValueSetConceptComponentRule(true);
        rule.concepts.add(new FshCode("foo", "bar"));
        assertEquals("* bar#foo", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetConceptComponentRule #toFSH should produce FSH for a ValueSetConceptComponentRule with a single excluded concept")
      void t11() throws Exception {
        var rule = new ValueSetConceptComponentRule(false);
        rule.concepts.add(new FshCode("foo", "bar"));
        assertEquals("* exclude bar#foo", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetConceptComponentRule #toFSH should produce FSH for a ValueSetConceptComponentRule with a single concept with system and display")
      void t12() throws Exception {
        var rule = new ValueSetConceptComponentRule(true);
        rule.concepts.add(new FshCode("foo", "bar", "baz"));
        assertEquals("* bar#foo \"baz\"", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetConceptComponentRule #toFSH should produce FSH for a ValueSetConceptComponentRule with a concept from a system")
      void t13() throws Exception {
        var rule = new ValueSetConceptComponentRule(true);
        rule.concepts.add(new FshCode("foo", "someSystem"));
        rule.from.system = "someSystem";
        assertEquals("* someSystem#foo", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetConceptComponentRule #toFSH should produce FSH for a ValueSetConceptComponentRule with several concepts included from a system")
      void t14() throws Exception {
        var rule = new ValueSetConceptComponentRule(true);
        rule.concepts.add(new FshCode("foo", "someSystem"));
        rule.concepts.add(new FshCode("bar", "someSystem"));
        rule.from.system = "someSystem";
        assertEquals("* someSystem#foo" + (EOL) + "* someSystem#bar", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetConceptComponentRule #toFSH should produce FSH for a ValueSetConceptComponentRule with several concepts excluded from a system")
      void t15() throws Exception {
        var rule = new ValueSetConceptComponentRule(false);
        rule.concepts.add(new FshCode("foo", "someSystem"));
        rule.concepts.add(new FshCode("bar", "someSystem"));
        rule.from.system = "someSystem";
        assertEquals("* exclude someSystem#foo" + (EOL) + "* exclude someSystem#bar", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetConceptComponentRule #toFSH should produce FSH for a ValueSetConceptComponentRule with a concept included from a valueset")
      void t16() throws Exception {
        var rule = new ValueSetConceptComponentRule(true);
        rule.concepts.add(new FshCode("foo", "bar"));
        rule.from.system = "bar";
        rule.from.valueSets = List.of("someValueSet");
        assertEquals("* bar#foo from valueset someValueSet", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetConceptComponentRule #toFSH should produce FSH for a ValueSetConceptComponentRule with a concept excluded from a valueset")
      void t17() throws Exception {
        var rule = new ValueSetConceptComponentRule(false);
        rule.concepts.add(new FshCode("foo", "bar"));
        rule.from.system = "bar";
        rule.from.valueSets = List.of("someValueSet");
        assertEquals("* exclude bar#foo from valueset someValueSet", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetConceptComponentRule #toFSH should produce FSH for a ValueSetConceptComponentRule with a concept included from several valuesets")
      void t18() throws Exception {
        var rule = new ValueSetConceptComponentRule(true);
        rule.concepts.add(new FshCode("foo", "bar"));
        rule.from.system = "bar";
        rule.from.valueSets = List.of("someValueSet", "otherValueSet");
        assertEquals("* bar#foo from valueset someValueSet and otherValueSet", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetConceptComponentRule #toFSH should produce FSH for a ValueSetConceptComponentRule with a concept excluded from several valuesets")
      void t19() throws Exception {
        var rule = new ValueSetConceptComponentRule(false);
        rule.concepts.add(new FshCode("foo", "bar"));
        rule.from.system = "bar";
        rule.from.valueSets = List.of("someValueSet", "otherValueSet");
        assertEquals("* exclude bar#foo from valueset someValueSet and otherValueSet", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetConceptComponentRule #toFSH should produce FSH for a ValueSetConceptComponentRule with a concept included from a system and several valuesets")
      void t20() throws Exception {
        var rule = new ValueSetConceptComponentRule(true);
        rule.concepts.add(new FshCode("foo", "someSystem"));
        rule.from.system = "someSystem";
        rule.from.valueSets = List.of("someValueSet", "otherValueSet");
        assertEquals("* someSystem#foo from valueset someValueSet and otherValueSet", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetConceptComponentRule #toFSH should produce FSH for a ValueSetConceptComponentRule with a concept excluded from a system and several valuesets")
      void t21() throws Exception {
        var rule = new ValueSetConceptComponentRule(false);
        rule.concepts.add(new FshCode("foo", "someSystem"));
        rule.from.system = "someSystem";
        rule.from.valueSets = List.of("someValueSet", "otherValueSet");
        assertEquals("* exclude someSystem#foo from valueset someValueSet and otherValueSet", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetConceptComponentRule #toFSH should produce FSH for a ValueSetConceptComponentRule with several concepts included from a valueset")
      void t22() throws Exception {
        var rule = new ValueSetConceptComponentRule(true);
        rule.concepts.add(new FshCode("bear", "zoo"));
        rule.concepts.add(new FshCode("pelican", "zoo"));
        rule.from.system = "zoo";
        rule.from.valueSets = List.of("someValueSet");
        assertEquals("* zoo#bear from valueset someValueSet" + (EOL) + "* zoo#pelican from valueset someValueSet", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetConceptComponentRule #toFSH should format a long ValueSetConceptComponentRule to take up multiple lines")
      void t23() throws Exception {
        var rule = new ValueSetConceptComponentRule(true);
        rule.concepts = List.of(new FshCode("cookies", "http://fhir.food-pyramid.org/FoodPyramidGuide/CodeSystems/FoodGroupsCS", "Cookies"), new FshCode("candy", "http://fhir.food-pyramid.org/FoodPyramidGuide/CodeSystems/FoodGroupsCS", "Candy"), new FshCode("chips", "http://fhir.food-pyramid.org/FoodPyramidGuide/CodeSystems/FoodGroupsCS", "Chips"), new FshCode("cakes", "http://fhir.food-pyramid.org/FoodPyramidGuide/CodeSystems/FoodGroupsCS", "Cakes"), new FshCode("verylargecakes", "http://fhir.food-pyramid.org/FoodPyramidGuide/CodeSystems/FoodGroupsCS", "Very Large Cakes"));
        rule.from.system = "http://fhir.food-pyramid.org/FoodPyramidGuide/CodeSystems/FoodGroupsCS";
        rule.from.valueSets = List.of("http://fhir.food-pyramid.org/FoodPyramidGuide/ValueSets/DeliciousVS");
        var result = rule.toFSH();
        var expectedResult = String.join(EOL, "* http://fhir.food-pyramid.org/FoodPyramidGuide/CodeSystems/FoodGroupsCS#cookies \"Cookies\"", "    from valueset http://fhir.food-pyramid.org/FoodPyramidGuide/ValueSets/DeliciousVS", "* http://fhir.food-pyramid.org/FoodPyramidGuide/CodeSystems/FoodGroupsCS#candy \"Candy\"", "    from valueset http://fhir.food-pyramid.org/FoodPyramidGuide/ValueSets/DeliciousVS", "* http://fhir.food-pyramid.org/FoodPyramidGuide/CodeSystems/FoodGroupsCS#chips \"Chips\"", "    from valueset http://fhir.food-pyramid.org/FoodPyramidGuide/ValueSets/DeliciousVS", "* http://fhir.food-pyramid.org/FoodPyramidGuide/CodeSystems/FoodGroupsCS#cakes \"Cakes\"", "    from valueset http://fhir.food-pyramid.org/FoodPyramidGuide/ValueSets/DeliciousVS", "* http://fhir.food-pyramid.org/FoodPyramidGuide/CodeSystems/FoodGroupsCS#verylargecakes \"Very Large Cakes\"", "    from valueset http://fhir.food-pyramid.org/FoodPyramidGuide/ValueSets/DeliciousVS");
        assertEquals(expectedResult, result);
      }
    }
  }
  @Nested
  @DisplayName("ValueSetFilterComponentRule")
  class DValuesetfiltercomponentrule24 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor25 {
      @Test
      @DisplayName("ValueSetFilterComponentRule #constructor should set the properties correctly")
      void t26() throws Exception {
        var v = new ValueSetFilterComponentRule(true);
        assertEquals(true, v.inclusion);
        // toEqual({}): no properties set
        assertNull(v.from.system);
        assertNull(v.from.valueSets);
        assertEquals(List.of(), v.filters);
      }
    }
    @Nested
    @DisplayName("#toFSH")
    class DTofsh27 {
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule with codes from a system")
      void t28() throws Exception {
        var rule = new ValueSetFilterComponentRule(true);
        rule.from.system = "someSystem";
        assertEquals("* include codes from system someSystem", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule that excludes codes from a system")
      void t29() throws Exception {
        var rule = new ValueSetFilterComponentRule(false);
        rule.from.system = "someSystem";
        assertEquals("* exclude codes from system someSystem", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule with codes from a valueset")
      void t30() throws Exception {
        var rule = new ValueSetFilterComponentRule(true);
        rule.from.valueSets = List.of("someValueSet");
        assertEquals("* include codes from valueset someValueSet", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule that excludes codes from a valueset")
      void t31() throws Exception {
        var rule = new ValueSetFilterComponentRule(false);
        rule.from.valueSets = List.of("someValueSet");
        assertEquals("* exclude codes from valueset someValueSet", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule with codes from several valuesets")
      void t32() throws Exception {
        var rule = new ValueSetFilterComponentRule(true);
        rule.from.valueSets = List.of("someValueSet", "otherValueSet");
        assertEquals("* include codes from valueset someValueSet and otherValueSet", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule that excludes codes from several valuesets")
      void t33() throws Exception {
        var rule = new ValueSetFilterComponentRule(false);
        rule.from.valueSets = List.of("someValueSet", "otherValueSet");
        assertEquals("* exclude codes from valueset someValueSet and otherValueSet", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule with codes from a system and several valuesets")
      void t34() throws Exception {
        var rule = new ValueSetFilterComponentRule(true);
        rule.from.system = "someSystem";
        rule.from.valueSets = List.of("someValueSet", "otherValueSet");
        assertEquals("* include codes from system someSystem and valueset someValueSet and otherValueSet", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule with codes from a system that are filtered")
      void t35() throws Exception {
        var rule = new ValueSetFilterComponentRule(true);
        rule.from.system = "someSystem";
        rule.filters.add(new ValueSetFilter("version", VsOperator.EQUALS, "2.0"));
        assertEquals("* include codes from system someSystem where version = \"2.0\"", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule that excludes codes from a system that are filtered")
      void t36() throws Exception {
        var rule = new ValueSetFilterComponentRule(false);
        rule.from.system = "someSystem";
        rule.filters.add(new ValueSetFilter("version", VsOperator.EQUALS, "2.0"));
        assertEquals("* exclude codes from system someSystem where version = \"2.0\"", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule with codes from a system that are filtered with regex")
      void t37() throws Exception {
        var rule = new ValueSetFilterComponentRule(true);
        rule.from.system = "someSystem";
        rule.filters.add(new ValueSetFilter("version", VsOperator.REGEX, Pattern.compile("2.0")));
        assertEquals("* include codes from system someSystem where version regex /2.0/", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule that excludes codes from a system that are filtered with regex")
      void t38() throws Exception {
        var rule = new ValueSetFilterComponentRule(false);
        rule.from.system = "someSystem";
        rule.filters.add(new ValueSetFilter("version", VsOperator.REGEX, Pattern.compile("2.0")));
        assertEquals("* exclude codes from system someSystem where version regex /2.0/", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule with codes from a system that are filtered with code")
      void t39() throws Exception {
        var rule = new ValueSetFilterComponentRule(true);
        rule.from.system = "someSystem";
        rule.filters.add(new ValueSetFilter("version", VsOperator.IS_A, new FshCode("foo")));
        assertEquals("* include codes from system someSystem where version is-a #foo", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule that excludes codes from a system that are filtered with code")
      void t40() throws Exception {
        var rule = new ValueSetFilterComponentRule(false);
        rule.from.system = "someSystem";
        rule.filters.add(new ValueSetFilter("version", VsOperator.IS_A, new FshCode("foo")));
        assertEquals("* exclude codes from system someSystem where version is-a #foo", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule with codes from a system that are filtered with boolean")
      void t41() throws Exception {
        var rule = new ValueSetFilterComponentRule(true);
        rule.from.system = "someSystem";
        rule.filters.add(new ValueSetFilter("version", VsOperator.EXISTS, true));
        assertEquals("* include codes from system someSystem where version exists true", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule that excludes codes from a system that are filtered with boolean")
      void t42() throws Exception {
        var rule = new ValueSetFilterComponentRule(false);
        rule.from.system = "someSystem";
        rule.filters.add(new ValueSetFilter("version", VsOperator.EXISTS, true));
        assertEquals("* exclude codes from system someSystem where version exists true", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule with codes from a system that are multi-filtered")
      void t43() throws Exception {
        var rule = new ValueSetFilterComponentRule(true);
        rule.from.system = "someSystem";
        rule.filters.add(new ValueSetFilter("version", VsOperator.EQUALS, "2.0"));
        rule.filters.add(new ValueSetFilter("status", VsOperator.EXISTS, true));
        assertEquals("* include codes from system someSystem where version = \"2.0\" and status exists true", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should produce FSH for a ValueSetFilterComponentRule that excludes codes from a system that are multi-filtered")
      void t44() throws Exception {
        var rule = new ValueSetFilterComponentRule(false);
        rule.from.system = "someSystem";
        rule.filters.add(new ValueSetFilter("version", VsOperator.EQUALS, "2.0"));
        rule.filters.add(new ValueSetFilter("status", VsOperator.EXISTS, true));
        assertEquals("* exclude codes from system someSystem where version = \"2.0\" and status exists true", rule.toFSH());
      }
      @Test
      @DisplayName("ValueSetFilterComponentRule #toFSH should format a long ValueSetFilterComponentRule to take up multiple lines")
      void t45() throws Exception {
        var rule = new ValueSetFilterComponentRule(false);
        rule.from.system = "http://fhir.example.org/myImplementationGuide/CodeSystem/AppleCS";
        rule.from.valueSets = List.of("http://fhir.example.org/myImplementationGuide/ValueSet/BananaVS", "http://fhir.example.org/myImplementationGuide/ValueSet/CupcakeVS");
        rule.filters.add(new ValueSetFilter("display", VsOperator.EQUALS, "this and that"));
        rule.filters.add(new ValueSetFilter("version", VsOperator.EXISTS, true));
        rule.filters.add(new ValueSetFilter("concept", VsOperator.DESCENDENT_OF, new FshCode("comestible")));
        var result = rule.toFSH();
        var expectedResult = String.join(EOL, "* exclude codes from system http://fhir.example.org/myImplementationGuide/CodeSystem/AppleCS and", "    valueset http://fhir.example.org/myImplementationGuide/ValueSet/BananaVS and", "    http://fhir.example.org/myImplementationGuide/ValueSet/CupcakeVS", "    where display = \"this and that\" and", "    version exists true and", "    concept descendent-of #comestible");
        assertEquals(expectedResult, result);
      }
    }
  }
}
