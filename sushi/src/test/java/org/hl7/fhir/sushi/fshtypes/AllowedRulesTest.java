package org.hl7.fhir.sushi.fshtypes;

import static org.hl7.fhir.sushi.fshtypes.AllowedRules.isAllowedRule;
import static org.hl7.fhir.sushi.fshtypes.Common.EOL;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.common.js.*;
import org.junit.jupiter.api.*;

// port of sushi/test/fshtypes/AllowedRules.test.ts (SUSHI 3.21.1)
class AllowedRulesTest {
  @Nested
  @DisplayName("isAllowedRule")
  class DIsallowedrule1 {
    @Nested
    @DisplayName("#Profile")
    class DProfile2 {
      Profile p;
      @BeforeEach
      void beforeAll2() throws Exception {
        p = new Profile("Bar");
      }
      @Test
      @DisplayName("isAllowedRule #Profile should allow valid rules on a Profile")
      void t3() throws Exception {
        assertEquals(true, isAllowedRule(p, new CardRule("foo")));
        assertEquals(true, isAllowedRule(p, new CaretValueRule("foo")));
        assertEquals(true, isAllowedRule(p, new ContainsRule("foo")));
        assertEquals(true, isAllowedRule(p, new AssignmentRule("foo")));
        assertEquals(true, isAllowedRule(p, new FlagRule("foo")));
        assertEquals(true, isAllowedRule(p, new ObeysRule("foo")));
        assertEquals(true, isAllowedRule(p, new OnlyRule("foo")));
        assertEquals(true, isAllowedRule(p, new BindingRule("foo")));
      }
      @Test
      @DisplayName("isAllowedRule #Profile should not allow invalid rules on a Profile")
      void t4() throws Exception {
        assertEquals(false, isAllowedRule(p, new MappingRule("foo")));
        assertEquals(false, isAllowedRule(p, new InsertRule("")));
        assertEquals(false, isAllowedRule(p, new ValueSetComponentRule(true)));
        assertEquals(false, isAllowedRule(p, new ConceptRule("foo")));
      }
    }
    @Nested
    @DisplayName("#Extension")
    class DExtension5 {
      Extension e;
      @BeforeEach
      void beforeAll5() throws Exception {
        e = new Extension("Bar");
      }
      @Test
      @DisplayName("isAllowedRule #Extension should allow valid rules on an Extension")
      void t6() throws Exception {
        assertEquals(true, isAllowedRule(e, new CardRule("foo")));
        assertEquals(true, isAllowedRule(e, new CaretValueRule("foo")));
        assertEquals(true, isAllowedRule(e, new ContainsRule("foo")));
        assertEquals(true, isAllowedRule(e, new AssignmentRule("foo")));
        assertEquals(true, isAllowedRule(e, new FlagRule("foo")));
        assertEquals(true, isAllowedRule(e, new ObeysRule("foo")));
        assertEquals(true, isAllowedRule(e, new OnlyRule("foo")));
        assertEquals(true, isAllowedRule(e, new BindingRule("foo")));
      }
      @Test
      @DisplayName("isAllowedRule #Extension should not allow invalid rules on an Extension")
      void t7() throws Exception {
        assertEquals(false, isAllowedRule(e, new MappingRule("foo")));
        assertEquals(false, isAllowedRule(e, new InsertRule("")));
        assertEquals(false, isAllowedRule(e, new ValueSetComponentRule(true)));
        assertEquals(false, isAllowedRule(e, new ConceptRule("foo")));
      }
    }
    @Nested
    @DisplayName("#Logical")
    class DLogical8 {
      Logical l;
      @BeforeEach
      void beforeAll8() throws Exception {
        l = new Logical("Bar");
      }
      @Test
      @DisplayName("isAllowedRule #Logical should allow valid rules on an Logical")
      void t9() throws Exception {
        assertEquals(true, isAllowedRule(l, new AddElementRule("foo")));
        assertEquals(true, isAllowedRule(l, new CardRule("foo")));
        assertEquals(true, isAllowedRule(l, new CaretValueRule("foo")));
        assertEquals(true, isAllowedRule(l, new AssignmentRule("foo")));
        assertEquals(true, isAllowedRule(l, new FlagRule("foo")));
        assertEquals(true, isAllowedRule(l, new ObeysRule("foo")));
        assertEquals(true, isAllowedRule(l, new OnlyRule("foo")));
        assertEquals(true, isAllowedRule(l, new BindingRule("foo")));
      }
      @Test
      @DisplayName("isAllowedRule #Logical should not allow invalid rules on an Logical")
      void t10() throws Exception {
        assertEquals(false, isAllowedRule(l, new MappingRule("foo")));
        assertEquals(false, isAllowedRule(l, new InsertRule("")));
        assertEquals(false, isAllowedRule(l, new ValueSetComponentRule(true)));
        assertEquals(false, isAllowedRule(l, new ConceptRule("foo")));
        assertEquals(false, isAllowedRule(l, new ContainsRule("foo")));
      }
    }
    @Nested
    @DisplayName("#Resource")
    class DResource11 {
      Resource r;
      @BeforeEach
      void beforeAll11() throws Exception {
        r = new Resource("Bar");
      }
      @Test
      @DisplayName("isAllowedRule #Resource should allow valid rules on an Resource")
      void t12() throws Exception {
        assertEquals(true, isAllowedRule(r, new AddElementRule("foo")));
        assertEquals(true, isAllowedRule(r, new CardRule("foo")));
        assertEquals(true, isAllowedRule(r, new CaretValueRule("foo")));
        assertEquals(true, isAllowedRule(r, new AssignmentRule("foo")));
        assertEquals(true, isAllowedRule(r, new FlagRule("foo")));
        assertEquals(true, isAllowedRule(r, new ObeysRule("foo")));
        assertEquals(true, isAllowedRule(r, new OnlyRule("foo")));
        assertEquals(true, isAllowedRule(r, new BindingRule("foo")));
      }
      @Test
      @DisplayName("isAllowedRule #Resource should not allow invalid rules on an Resource")
      void t13() throws Exception {
        assertEquals(false, isAllowedRule(r, new MappingRule("foo")));
        assertEquals(false, isAllowedRule(r, new InsertRule("")));
        assertEquals(false, isAllowedRule(r, new ValueSetComponentRule(true)));
        assertEquals(false, isAllowedRule(r, new ConceptRule("foo")));
        assertEquals(false, isAllowedRule(r, new ContainsRule("foo")));
      }
    }
    @Nested
    @DisplayName("#Instance")
    class DInstance14 {
      Instance i;
      @BeforeEach
      void beforeAll14() throws Exception {
        i = new Instance("Bar");
      }
      @Test
      @DisplayName("isAllowedRule #Instance should allow valid rules on an Instance")
      void t15() throws Exception {
        assertEquals(true, isAllowedRule(i, new AssignmentRule("foo")));
      }
      @Test
      @DisplayName("isAllowedRule #Instance should not allow invalid rules on Instance")
      void t16() throws Exception {
        assertEquals(false, isAllowedRule(i, new MappingRule("foo")));
        assertEquals(false, isAllowedRule(i, new InsertRule("")));
        assertEquals(false, isAllowedRule(i, new ValueSetComponentRule(true)));
        assertEquals(false, isAllowedRule(i, new ConceptRule("foo")));
        assertEquals(false, isAllowedRule(i, new CardRule("foo")));
        assertEquals(false, isAllowedRule(i, new CaretValueRule("foo")));
        assertEquals(false, isAllowedRule(i, new ContainsRule("foo")));
        assertEquals(false, isAllowedRule(i, new FlagRule("foo")));
        assertEquals(false, isAllowedRule(i, new ObeysRule("foo")));
        assertEquals(false, isAllowedRule(i, new OnlyRule("foo")));
        assertEquals(false, isAllowedRule(i, new BindingRule("foo")));
      }
    }
    @Nested
    @DisplayName("FshValueSet")
    class DFshvalueset17 {
      FshValueSet v;
      @BeforeEach
      void beforeAll17() throws Exception {
        v = new FshValueSet("Bar");
      }
      @Test
      @DisplayName("isAllowedRule FshValueSet should allow valid rules on a FshValueSet")
      void t18() throws Exception {
        assertEquals(true, isAllowedRule(v, new CaretValueRule("foo")));
        assertEquals(true, isAllowedRule(v, new ValueSetComponentRule(true)));
        assertEquals(true, isAllowedRule(v, new ValueSetConceptComponentRule(true)));
        assertEquals(true, isAllowedRule(v, new ValueSetFilterComponentRule(true)));
      }
      @Test
      @DisplayName("isAllowedRule FshValueSet should not allow invalid rules on a FshValueSet")
      void t19() throws Exception {
        assertEquals(false, isAllowedRule(v, new AssignmentRule("foo")));
        assertEquals(false, isAllowedRule(v, new MappingRule("foo")));
        assertEquals(false, isAllowedRule(v, new InsertRule("")));
        assertEquals(false, isAllowedRule(v, new ConceptRule("foo")));
        assertEquals(false, isAllowedRule(v, new CardRule("foo")));
        assertEquals(false, isAllowedRule(v, new ContainsRule("foo")));
        assertEquals(false, isAllowedRule(v, new FlagRule("foo")));
        assertEquals(false, isAllowedRule(v, new ObeysRule("foo")));
        assertEquals(false, isAllowedRule(v, new OnlyRule("foo")));
        assertEquals(false, isAllowedRule(v, new BindingRule("foo")));
      }
    }
    @Nested
    @DisplayName("FshCodeSystem")
    class DFshcodesystem20 {
      FshCodeSystem c;
      @BeforeEach
      void beforeAll20() throws Exception {
        c = new FshCodeSystem("Bar");
      }
      @Test
      @DisplayName("isAllowedRule FshCodeSystem should allow valid rules on a FshCodeSystem")
      void t21() throws Exception {
        assertEquals(true, isAllowedRule(c, new CaretValueRule("foo")));
        assertEquals(true, isAllowedRule(c, new ConceptRule("foo")));
      }
      @Test
      @DisplayName("isAllowedRule FshCodeSystem should not allow invalid rules on a FshCodeSystem")
      void t22() throws Exception {
        assertEquals(false, isAllowedRule(c, new AssignmentRule("foo")));
        assertEquals(false, isAllowedRule(c, new MappingRule("foo")));
        assertEquals(false, isAllowedRule(c, new InsertRule("")));
        assertEquals(false, isAllowedRule(c, new CardRule("foo")));
        assertEquals(false, isAllowedRule(c, new ContainsRule("foo")));
        assertEquals(false, isAllowedRule(c, new FlagRule("foo")));
        assertEquals(false, isAllowedRule(c, new ObeysRule("foo")));
        assertEquals(false, isAllowedRule(c, new OnlyRule("foo")));
        assertEquals(false, isAllowedRule(c, new BindingRule("foo")));
        assertEquals(false, isAllowedRule(c, new ValueSetComponentRule(true)));
      }
    }
    @Nested
    @DisplayName("Mapping")
    class DMapping23 {
      Mapping m;
      @BeforeEach
      void beforeAll23() throws Exception {
        m = new Mapping("Bar");
      }
      @Test
      @DisplayName("isAllowedRule Mapping should allow valid rules on a Mapping")
      void t24() throws Exception {
        assertEquals(true, isAllowedRule(m, new MappingRule("foo")));
      }
      @Test
      @DisplayName("isAllowedRule Mapping should not allow invalid rules on a Mapping")
      void t25() throws Exception {
        assertEquals(false, isAllowedRule(m, new CaretValueRule("foo")));
        assertEquals(false, isAllowedRule(m, new AssignmentRule("foo")));
        assertEquals(false, isAllowedRule(m, new ConceptRule("foo")));
        assertEquals(false, isAllowedRule(m, new InsertRule("")));
        assertEquals(false, isAllowedRule(m, new CardRule("foo")));
        assertEquals(false, isAllowedRule(m, new ContainsRule("foo")));
        assertEquals(false, isAllowedRule(m, new FlagRule("foo")));
        assertEquals(false, isAllowedRule(m, new ObeysRule("foo")));
        assertEquals(false, isAllowedRule(m, new OnlyRule("foo")));
        assertEquals(false, isAllowedRule(m, new BindingRule("foo")));
        assertEquals(false, isAllowedRule(m, new ValueSetComponentRule(true)));
      }
    }
    @Nested
    @DisplayName("#RuleSet")
    class DRuleset26 {
      RuleSet r;
      @BeforeEach
      void beforeAll26() throws Exception {
        r = new RuleSet("Bar");
      }
      @Test
      @DisplayName("isAllowedRule #RuleSet should allow valid rules on a RuleSet")
      void t27() throws Exception {
        assertEquals(true, isAllowedRule(r, new AddElementRule("foo")));
        assertEquals(true, isAllowedRule(r, new CardRule("foo")));
        assertEquals(true, isAllowedRule(r, new CaretValueRule("foo")));
        assertEquals(true, isAllowedRule(r, new ContainsRule("foo")));
        assertEquals(true, isAllowedRule(r, new AssignmentRule("foo")));
        assertEquals(true, isAllowedRule(r, new FlagRule("foo")));
        assertEquals(true, isAllowedRule(r, new ObeysRule("foo")));
        assertEquals(true, isAllowedRule(r, new OnlyRule("foo")));
        assertEquals(true, isAllowedRule(r, new BindingRule("foo")));
        assertEquals(true, isAllowedRule(r, new MappingRule("foo")));
        assertEquals(true, isAllowedRule(r, new ValueSetComponentRule(true)));
        assertEquals(true, isAllowedRule(r, new ConceptRule("foo")));
      }
      @Test
      @DisplayName("isAllowedRule #RuleSet should not allow invalid rules on a RuleSet")
      void t28() throws Exception {
        assertEquals(false, isAllowedRule(r, new InsertRule("")));
      }
    }
  }
}
