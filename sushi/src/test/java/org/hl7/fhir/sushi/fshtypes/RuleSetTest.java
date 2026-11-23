package org.hl7.fhir.sushi.fshtypes;

import static org.hl7.fhir.sushi.fshtypes.Common.EOL;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.common.js.*;
import org.junit.jupiter.api.*;

// port of sushi/test/fshtypes/RuleSet.test.ts (SUSHI 3.21.1)
class RuleSetTest {
  @Nested
  @DisplayName("RuleSet")
  class DRuleset1 {
    @Nested
    @DisplayName("#constructor")
    class DConstructor2 {
      @Test
      @DisplayName("RuleSet #constructor should set the properties correctly")
      void t3() throws Exception {
        var r = new RuleSet("MyRuleSet");
        assertEquals("MyRuleSet", r.name);
        assertTrue(r.rules.isEmpty());
      }
    }
  }
}
