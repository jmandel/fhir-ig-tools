package org.hl7.fhir.sushi.importer;

import static org.hl7.fhir.sushi.testhelpers.Asserts.*;
import static org.hl7.fhir.sushi.testhelpers.ImportSingleText.importSingleText;
import static org.hl7.fhir.sushi.testhelpers.LeftAlign.leftAlign;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/import/FSHImporter.SDRules.test.ts (SUSHI 3.21.1)
class FSHImporterSDRulesTest {
  /** os.EOL */
  private static final String EOL = System.lineSeparator();

  /** expect(msg).toMatch(/re/s) */
  private static void assertMatchS(String re, String msg) {
    assertNotNull(msg);
    assertTrue(Pattern.compile(re, Pattern.DOTALL).matcher(msg).find(), () -> "/" + re + "/s did not match: " + msg);
  }

  /** JSON.stringify([...parts]) */
  private static String key(String... parts) {
    return Json.stringify(Arrays.asList((Object[]) parts));
  }

  private static SourceInfo srcInfo(String file, int startLine, int startColumn, int endLine, int endColumn) {
    return new SourceInfo(file, new TextLocation(startLine, startColumn, endLine, endColumn));
  }

  private static FshCode ucum(String code, int[] loc) {
    return new FshCode(code, "http://unitsofmeasure.org").withLocation(loc).withFile("");
  }

  private static ContainsRuleItem item(String name, String type) {
    return new ContainsRuleItem(name, type);
  }

  private static AddElementArgs args(double min, String max, Flags flags, List<OnlyRuleType> types, Defs defs) {
    return new AddElementArgs(min, max, flags, types, defs);
  }

  private static Defs defs(String short_, String definition) {
    return new Defs(short_, definition, null);
  }

  private static OnlyRuleType ortNotRef(String type) {
    OnlyRuleType t = new OnlyRuleType(type);
    t.isReference = false;
    return t;
  }

  private static OnlyRuleType ortNotCanonical(String type) {
    OnlyRuleType t = new OnlyRuleType(type);
    t.isCanonical = false;
    return t;
  }

  private static OnlyRuleType ortNotCodeableRef(String type) {
    OnlyRuleType t = new OnlyRuleType(type);
    t.isCodeableReference = false;
    return t;
  }

  @Nested
  @DisplayName("FSHImporter")
  class FSHImporterTests {
    @BeforeEach void reset() {
      loggerSpy.reset();
    }

    @Nested
    @DisplayName("SD Rules")
    class SDRules {
      // These rules are shared across all StructureDefinition entities:
      //   Profile, Extension, Resource, Logical
      // The intent is that the comprehensive testing of these common rules occurs here
      // while in each StructureDefinition entity test suite, these rules will have simple
      // smoke tests.
      @Nested
      @DisplayName("#cardRule")
      class CardRuleTests {
        @Test @DisplayName("FSHImporter SD Rules #cardRule should parse simple card rules")
        void simpleCardRules() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category 1..5
              * value[x] 1..1
              * component 2..*
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(3, profile.rules.size());
          assertCardRule(profile.rules.get(0), "category", 1, 5);
          assertCardRule(profile.rules.get(1), "value[x]", 1, 1);
          assertCardRule(profile.rules.get(2), "component", 2, "*");
        }

        @Test @DisplayName("FSHImporter SD Rules #cardRule should parse card rule with only min")
        void onlyMin() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category 1..
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertCardRule(profile.rules.get(0), "category", 1, ""); // Unspecified max
        }

        @Test @DisplayName("FSHImporter SD Rules #cardRule should parse card rule with only max")
        void onlyMax() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category ..5
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertCardRule(profile.rules.get(0), "category", Double.NaN, "5"); // Unspecified min
        }

        @Test @DisplayName("FSHImporter SD Rules #cardRule should log an error if neither side is specified")
        void neitherSide() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category ..
              """);

          FSHDocument result = importSingleText(input, "BadCard.fsh");
          Profile profile = result.profiles.get("ObservationProfile");
          // Rule is still set and element's current cardinalities will be used at export
          assertEquals(1, profile.rules.size());
          assertMatchS("Neither side of the cardinality was specified on path \"category\". A min, max, or both need "
              + "to be specified.\\D*", loggerSpy.getLastMessage("error"));
          assertMatchS("File: BadCard\\.fsh.*Line: 4\\D*", loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("FSHImporter SD Rules #cardRule should parse card rules w/ flags")
        void cardRulesWithFlags() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category 1..5 MS
              * value[x] 1..1 ?!
              * component 2..* SU
              * interpretation 1..* TU
              * note 0..11 N
              * bodySite 1..1 D
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(12, profile.rules.size());
          assertCardRule(profile.rules.get(0), "category", 1, 5);
          assertFlagRule(profile.rules.get(1), "category", true, null, null, null, null, null);
          assertCardRule(profile.rules.get(2), "value[x]", 1, 1);
          assertFlagRule(profile.rules.get(3), "value[x]", null, null, true, null, null, null);
          assertCardRule(profile.rules.get(4), "component", 2, "*");
          assertFlagRule(profile.rules.get(5), "component", null, true, null, null, null, null);
          assertCardRule(profile.rules.get(6), "interpretation", 1, "*");
          assertFlagRule(profile.rules.get(7), "interpretation", null, null, null, true, null, null);
          assertCardRule(profile.rules.get(8), "note", 0, "11");
          assertFlagRule(profile.rules.get(9), "note", null, null, null, null, true, null);
          assertCardRule(profile.rules.get(10), "bodySite", 1, "1");
          assertFlagRule(profile.rules.get(11), "bodySite", null, null, null, null, null, true);
        }

        @Test @DisplayName("FSHImporter SD Rules #cardRule should parse card rules w/ multiple flags")
        void cardRulesWithMultipleFlags() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category 1..5 MS ?! TU
              * value[x] 1..1 ?! SU N
              * component 2..* SU MS D
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(6, profile.rules.size());
          assertCardRule(profile.rules.get(0), "category", 1, 5);
          assertFlagRule(profile.rules.get(1), "category", true, null, true, true, null, null);
          assertCardRule(profile.rules.get(2), "value[x]", 1, 1);
          assertFlagRule(profile.rules.get(3), "value[x]", null, true, true, null, true, null);
          assertCardRule(profile.rules.get(4), "component", 2, "*");
          assertFlagRule(profile.rules.get(5), "component", true, true, null, null, null, true);
        }
      }

      @Nested
      @DisplayName("#flagRule")
      class FlagRuleTests {
        @Test @DisplayName("FSHImporter SD Rules #flagRule should parse single-path single-value flag rules")
        void singlePathSingleValue() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category MS
              * value[x] ?!
              * component SU
              * interpretation TU
              * note N
              * bodySite D
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(6, profile.rules.size());
          assertFlagRule(profile.rules.get(0), "category", true, null, null, null, null, null);
          assertFlagRule(profile.rules.get(1), "value[x]", null, null, true, null, null, null);
          assertFlagRule(profile.rules.get(2), "component", null, true, null, null, null, null);
          assertFlagRule(profile.rules.get(3), "interpretation", null, null, null, true, null, null);
          assertFlagRule(profile.rules.get(4), "note", null, null, null, null, true, null);
          assertFlagRule(profile.rules.get(5), "bodySite", null, null, null, null, null, true);
        }

        @Test @DisplayName("FSHImporter SD Rules #flagRule should parse single-path multi-value flag rules")
        void singlePathMultiValue() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category MS ?! N
              * value[x] ?! SU D
              * component MS SU ?! TU
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(3, profile.rules.size());
          assertFlagRule(profile.rules.get(0), "category", true, null, true, null, true, null);
          assertFlagRule(profile.rules.get(1), "value[x]", null, true, true, null, null, true);
          assertFlagRule(profile.rules.get(2), "component", true, true, true, true, null, null);
        }

        @Test @DisplayName("FSHImporter SD Rules #flagRule should parse multi-path single-value flag rules")
        void multiPathSingleValue() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category and value[x] and component MS
              * subject and focus ?!
              * interpretation and note N
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(7, profile.rules.size());
          assertFlagRule(profile.rules.get(0), "category", true, null, null, null, null, null);
          assertFlagRule(profile.rules.get(1), "value[x]", true, null, null, null, null, null);
          assertFlagRule(profile.rules.get(2), "component", true, null, null, null, null, null);
          assertFlagRule(profile.rules.get(3), "subject", null, null, true, null, null, null);
          assertFlagRule(profile.rules.get(4), "focus", null, null, true, null, null, null);
          assertFlagRule(profile.rules.get(5), "interpretation", null, null, null, null, true, null);
          assertFlagRule(profile.rules.get(6), "note", null, null, null, null, true, null);
        }

        @Test @DisplayName("FSHImporter SD Rules #flagRule should parse multi-path multi-value flag rules")
        void multiPathMultiValue() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category and value[x] and component MS SU N
              * subject and focus ?! SU TU
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(5, profile.rules.size());
          assertFlagRule(profile.rules.get(0), "category", true, true, null, null, true, null);
          assertFlagRule(profile.rules.get(1), "value[x]", true, true, null, null, true, null);
          assertFlagRule(profile.rules.get(2), "component", true, true, null, null, true, null);
          assertFlagRule(profile.rules.get(3), "subject", null, true, true, true, null, null);
          assertFlagRule(profile.rules.get(4), "focus", null, true, true, true, null, null);
        }

        @Test @DisplayName("FSHImporter SD Rules #flagRule should log an error when paths are listed with commas")
        void pathsWithCommas() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category, value[x] , component MS SU N
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");

          assertEquals(0, profile.rules.size());
          assertMatchS("Using ',' to list items is no longer supported", loggerSpy.getLastMessage("error"));
        }
      }

      @Nested
      @DisplayName("#BindingRule")
      class BindingRuleTests {
        @Test @DisplayName("FSHImporter SD Rules #BindingRule should parse value set rules w/ names and strengths")
        void namesAndStrengths() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category from CategoryValueSet (required)
              * code from CodeValueSet (extensible)
              * valueCodeableConcept from ValueValueSet (preferred)
              * component.code from ComponentCodeValueSet (example)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(4, profile.rules.size());
          assertBindingRule(profile.rules.get(0), "category", "CategoryValueSet", "required");
          assertBindingRule(profile.rules.get(1), "code", "CodeValueSet", "extensible");
          assertBindingRule(profile.rules.get(2), "valueCodeableConcept", "ValueValueSet", "preferred");
          assertBindingRule(profile.rules.get(3), "component.code", "ComponentCodeValueSet", "example");
        }

        @Test @DisplayName("FSHImporter SD Rules #BindingRule should parse value set rules w/ urls and strengths")
        void urlsAndStrengths() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category from http://example.org/fhir/ValueSet/CategoryValueSet (required)
              * code from http://example.org/fhir/ValueSet/CodeValueSet (extensible)
              * valueCodeableConcept from http://example.org/fhir/ValueSet/ValueValueSet (preferred)
              * component.code from http://example.org/fhir/ValueSet/ComponentCodeValueSet (example)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(4, profile.rules.size());
          assertBindingRule(profile.rules.get(0), "category", "http://example.org/fhir/ValueSet/CategoryValueSet",
              "required");
          assertBindingRule(profile.rules.get(1), "code", "http://example.org/fhir/ValueSet/CodeValueSet",
              "extensible");
          assertBindingRule(profile.rules.get(2), "valueCodeableConcept",
              "http://example.org/fhir/ValueSet/ValueValueSet", "preferred");
          assertBindingRule(profile.rules.get(3), "component.code",
              "http://example.org/fhir/ValueSet/ComponentCodeValueSet", "example");
        }

        @Test @DisplayName("FSHImporter SD Rules #BindingRule should accept and translate aliases for value set URLs")
        void aliasesForValueSetUrls() {
          String input = leftAlign("""

              Alias: CAT = http://example.org/fhir/ValueSet/CategoryValueSet
              Alias: CODE = http://example.org/fhir/ValueSet/CodeValueSet
              Alias: VALUE = http://example.org/fhir/ValueSet/ValueValueSet
              Alias: COMP = http://example.org/fhir/ValueSet/ComponentCodeValueSet

              Profile: ObservationProfile
              Parent: Observation
              * category from CAT (required)
              * code from CODE (extensible)
              * valueCodeableConcept from VALUE (preferred)
              * component.code from COMP (example)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(4, profile.rules.size());
          assertBindingRule(profile.rules.get(0), "category", "http://example.org/fhir/ValueSet/CategoryValueSet",
              "required");
          assertBindingRule(profile.rules.get(1), "code", "http://example.org/fhir/ValueSet/CodeValueSet",
              "extensible");
          assertBindingRule(profile.rules.get(2), "valueCodeableConcept",
              "http://example.org/fhir/ValueSet/ValueValueSet", "preferred");
          assertBindingRule(profile.rules.get(3), "component.code",
              "http://example.org/fhir/ValueSet/ComponentCodeValueSet", "example");
        }

        @Test @DisplayName("FSHImporter SD Rules #BindingRule should parse value set rules w/ numeric names")
        void numericNames() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category from 123 (required)
              * code from 456 (extensible)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(2, profile.rules.size());
          assertBindingRule(profile.rules.get(0), "category", "123", "required");
          assertBindingRule(profile.rules.get(1), "code", "456", "extensible");
        }

        @Test @DisplayName("FSHImporter SD Rules #BindingRule should parse value set rules w/ no strength and default to required")
        void noStrength() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category from CategoryValueSet
              * code from http://example.org/fhir/ValueSet/CodeValueSet
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(2, profile.rules.size());
          assertBindingRule(profile.rules.get(0), "category", "CategoryValueSet", "required");
          assertBindingRule(profile.rules.get(1), "code", "http://example.org/fhir/ValueSet/CodeValueSet",
              "required");
        }

        @Test
        @DisplayName("FSHImporter SD Rules #BindingRule should ignore the units keyword and log an error when parsing value set rules on Quantity")
        void unitsKeyword() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueQuantity units from http://unitsofmeasure.org
              """);

          FSHDocument result = importSingleText(input, "UselessQuant.fsh");
          Profile profile = result.profiles.get("ObservationProfile");

          assertEquals(0, profile.rules.size());
          assertMatchS("The 'units' keyword is no longer supported.*File: UselessQuant\\.fsh.*Line: 4\\D*",
              loggerSpy.getLastMessage("error"));
        }
      }

      @Nested
      @DisplayName("#assignmentRule")
      class AssignmentRuleTests {
        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value boolean rule")
        void booleanRule() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueBoolean = true
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertAssignmentRule(profile.rules.get(0), "valueBoolean", true, false, false, "true");
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value boolean rule with (exactly) modifier")
        void booleanRuleExactly() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueBoolean = true (exactly)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertAssignmentRule(profile.rules.get(0), "valueBoolean", true, true, false, "true");
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value number (decimal) rule")
        void decimalRule() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueDecimal = 1.23
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertAssignmentRule(profile.rules.get(0), "valueDecimal", 1.23, false, false, "1.23");
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value number (integer) rule")
        void integerRule() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueInteger = 12E4
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertAssignmentRule(profile.rules.get(0), "valueInteger", BigInteger.valueOf(120000), false, false,
              "12E4");
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value string rule")
        void stringRule() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueString = "hello world"
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertAssignmentRule(profile.rules.get(0), "valueString", "hello world");
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value multi-line string rule")
        void multiLineStringRule() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueString = \"""
                  hello
                  world
                  \"""
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertAssignmentRule(profile.rules.get(0), "valueString", "hello\nworld");
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value date rule")
        void dateRule() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueDateTime = 2019-11-01T12:30:01.999Z
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          // For now, treating dates like strings
          assertAssignmentRule(profile.rules.get(0), "valueDateTime", "2019-11-01T12:30:01.999Z");
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value time rule")
        void timeRule() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueTime = 12:30:01.999-05:00
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          // For now, treating dates like strings
          assertAssignmentRule(profile.rules.get(0), "valueTime", "12:30:01.999-05:00");
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value code rule")
        void codeRule() {
          String input = leftAlign("""

              Alias: LOINC = http://loinc.org

              Profile: ObservationProfile
              Parent: Observation
              * status = #final
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshCode expectedCode = new FshCode("final").withLocation(new int[] {6, 12, 6, 17}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "status", expectedCode);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value CodeableConcept rule")
        void codeableConceptRule() {
          String input = leftAlign("""

              Alias: LOINC = http://loinc.org

              Profile: ObservationProfile
              Parent: Observation
              * valueCodeableConcept = LOINC#718-7 "Hemoglobin [Mass/volume] in Blood"
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshCode expectedCode = new FshCode("718-7", "http://loinc.org", "Hemoglobin [Mass/volume] in Blood")
              .withLocation(new int[] {6, 26, 6, 72}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueCodeableConcept", expectedCode);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value CodeableConcept rule with (exactly) modifier")
        void codeableConceptRuleExactly() {
          String input = leftAlign("""

              Alias: LOINC = http://loinc.org

              Profile: ObservationProfile
              Parent: Observation
              * valueCodeableConcept = LOINC#718-7 "Hemoglobin [Mass/volume] in Blood" (exactly)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshCode expectedCode = new FshCode("718-7", "http://loinc.org", "Hemoglobin [Mass/volume] in Blood")
              .withLocation(new int[] {6, 26, 6, 72}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueCodeableConcept", expectedCode, true);
        }

        @Test
        @DisplayName("FSHImporter SD Rules #assignmentRule should ignore the units keyword and log a warning when parsing an assigned value FSHCode rule with units on Quantity")
        void unitsKeywordAssignment() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueQuantity units = http://unitsofmeasure.org#cGy
              """);

          FSHDocument result = importSingleText(input, "UselessUnits.fsh");
          Profile profile = result.profiles.get("ObservationProfile");

          assertEquals(0, profile.rules.size());
          assertMatchS("The 'units' keyword is no longer supported.*File: UselessUnits\\.fsh.*Line: 4\\D*",
              loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value Quantity rule")
        void quantityRule() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * valueQuantity = 1.5 'mm'
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshQuantity expectedQuantity = new FshQuantity(1.5, ucum("mm", new int[] {5, 23, 5, 26}))
              .withLocation(new int[] {5, 19, 5, 26}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueQuantity", expectedQuantity);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value Quantity rule with unit display")
        void quantityRuleWithUnitDisplay() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * valueQuantity = 155.0 '[lb_av]' "lb"
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshQuantity expectedQuantity = new FshQuantity(155.0,
              new FshCode("[lb_av]", "http://unitsofmeasure.org", "lb").withLocation(new int[] {5, 25, 5, 33})
                  .withFile(""))
              .withLocation(new int[] {5, 19, 5, 38}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueQuantity", expectedQuantity);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value Quantity rule with UCUM units and no value")
        void quantityRuleNoValue() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * valueQuantity = 'mm'
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshQuantity expectedQuantity = new FshQuantity(null, ucum("mm", new int[] {5, 19, 5, 22}))
              .withLocation(new int[] {5, 19, 5, 22}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueQuantity", expectedQuantity);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value Quantity rule with UCUM units, display, and no value")
        void quantityRuleDisplayNoValue() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * valueQuantity = '[lb_av]' "lb"
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshQuantity expectedQuantity = new FshQuantity(null,
              new FshCode("[lb_av]", "http://unitsofmeasure.org", "lb").withLocation(new int[] {5, 19, 5, 27})
                  .withFile(""))
              .withLocation(new int[] {5, 19, 5, 32}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueQuantity", expectedQuantity);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value Ratio rule")
        void ratioRule() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * valueRatio = 130 'mg' : 1 'dL'
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshRatio expectedRatio = new FshRatio(
              new FshQuantity(130.0, ucum("mg", new int[] {5, 20, 5, 23})).withLocation(new int[] {5, 16, 5, 23})
                  .withFile(""),
              new FshQuantity(1.0, ucum("dL", new int[] {5, 29, 5, 32})).withLocation(new int[] {5, 27, 5, 32})
                  .withFile(""))
              .withLocation(new int[] {5, 16, 5, 32}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueRatio", expectedRatio);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value Ratio rule w/ numeric numerator")
        void ratioNumericNumerator() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * valueRatio = 130 : 1 'dL'
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshRatio expectedRatio = new FshRatio(
              new FshQuantity(130.0).withLocation(new int[] {5, 16, 5, 18}).withFile(""),
              new FshQuantity(1.0, ucum("dL", new int[] {5, 24, 5, 27})).withLocation(new int[] {5, 22, 5, 27})
                  .withFile(""))
              .withLocation(new int[] {5, 16, 5, 27}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueRatio", expectedRatio);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value Ratio rule w/ numeric denominator")
        void ratioNumericDenominator() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * valueRatio = 130 'mg' : 1
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshRatio expectedRatio = new FshRatio(
              new FshQuantity(130.0, ucum("mg", new int[] {5, 20, 5, 23})).withLocation(new int[] {5, 16, 5, 23})
                  .withFile(""),
              new FshQuantity(1.0).withLocation(new int[] {5, 27, 5, 27}).withFile(""))
              .withLocation(new int[] {5, 16, 5, 27}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueRatio", expectedRatio);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value Ratio rule w/ numeric numerator and denominator")
        void ratioNumericBoth() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * valueRatio = 130 : 1
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshRatio expectedRatio = new FshRatio(
              new FshQuantity(130.0).withLocation(new int[] {5, 16, 5, 18}).withFile(""),
              new FshQuantity(1.0).withLocation(new int[] {5, 22, 5, 22}).withFile(""))
              .withLocation(new int[] {5, 16, 5, 22}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueRatio", expectedRatio);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value Ratio rule with non-numeric numerator")
        void ratioNonNumericNumerator() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * valueRatio = 'mg' : 1 'dL'
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshRatio expectedRatio = new FshRatio(
              new FshQuantity(null, ucum("mg", new int[] {5, 16, 5, 19})).withLocation(new int[] {5, 16, 5, 19})
                  .withFile(""),
              new FshQuantity(1.0, ucum("dL", new int[] {5, 25, 5, 28})).withLocation(new int[] {5, 23, 5, 28})
                  .withFile(""))
              .withLocation(new int[] {5, 16, 5, 28}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueRatio", expectedRatio);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value Ratio rule with non-numeric denominator")
        void ratioNonNumericDenominator() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * valueRatio = 130 'mg' : 'dL'
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshRatio expectedRatio = new FshRatio(
              new FshQuantity(130.0, ucum("mg", new int[] {5, 20, 5, 23})).withLocation(new int[] {5, 16, 5, 23})
                  .withFile(""),
              new FshQuantity(null, ucum("dL", new int[] {5, 27, 5, 30})).withLocation(new int[] {5, 27, 5, 30})
                  .withFile(""))
              .withLocation(new int[] {5, 16, 5, 30}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueRatio", expectedRatio);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value Ratio rule with non-numeric numerator and denominator")
        void ratioNonNumericBoth() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * valueRatio = 'mg' : 'dL'
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshRatio expectedRatio = new FshRatio(
              new FshQuantity(null, ucum("mg", new int[] {5, 16, 5, 19})).withLocation(new int[] {5, 16, 5, 19})
                  .withFile(""),
              new FshQuantity(null, ucum("dL", new int[] {5, 23, 5, 26})).withLocation(new int[] {5, 23, 5, 26})
                  .withFile(""))
              .withLocation(new int[] {5, 16, 5, 26}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueRatio", expectedRatio);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value Reference rule")
        void referenceRule() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * basedOn = Reference(fooProfile)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());

          FshReference expectedReference = new FshReference("fooProfile").withLocation(new int[] {5, 13, 5, 33})
              .withFile("");
          assertAssignmentRule(profile.rules.get(0), "basedOn", expectedReference);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value Reference rules while allowing and translating aliases")
        void referenceRuleAliases() {
          String input = leftAlign("""

              Alias: FOO = http://hl7.org/fhir/StructureDefinition/Foo

              Profile: ObservationProfile
              Parent: Observation
              * basedOn = Reference(FOO) "bar"
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());

          FshReference expectedReference = new FshReference("http://hl7.org/fhir/StructureDefinition/Foo", "bar")
              .withLocation(new int[] {6, 13, 6, 32}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "basedOn", expectedReference);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value Reference rule with a display string")
        void referenceRuleDisplay() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * basedOn = Reference(fooProfile) "bar"
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());

          FshReference expectedReference = new FshReference("fooProfile", "bar")
              .withLocation(new int[] {5, 13, 5, 39}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "basedOn", expectedReference);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value Reference rule with whitespace")
        void referenceRuleWhitespace() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * basedOn = Reference(   fooProfile   )
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());

          FshReference expectedReference = new FshReference("fooProfile").withLocation(new int[] {5, 13, 5, 39})
              .withFile("");
          assertAssignmentRule(profile.rules.get(0), "basedOn", expectedReference);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should log an error when an assigned value Reference rule has a choice of references")
        void referenceRuleChoice() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * basedOn = Reference(cakeProfile or pieProfile)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());

          FshReference expectedReference = new FshReference("cakeProfile").withLocation(new int[] {5, 13, 5, 48})
              .withFile("");
          assertAssignmentRule(profile.rules.get(0), "basedOn", expectedReference);
          assertMatchS("Multiple choices of references are not allowed when setting a value.*Line: 5\\D*",
              loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value using Canonical")
        void canonical() {
          String input = leftAlign("""

              CodeSystem: Example
              * #first
              * #second

              Profile: ObservationProfile
              Parent: Observation
              * code.coding.system = Canonical(Example)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());

          FshCanonical expectedCanonical = new FshCanonical("Example").withLocation(new int[] {8, 24, 8, 41})
              .withFile("");
          assertAssignmentRule(profile.rules.get(0), "code.coding.system", expectedCanonical);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value using Canonical with spaces around entity name")
        void canonicalSpaces() {
          String input = leftAlign("""

              CodeSystem: SpaceyExample
              * #first
              * #second

              Profile: ObservationProfile
              Parent: Observation
              * code.coding.system = Canonical(   SpaceyExample )
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());

          // No spaces are included in the entityName
          FshCanonical expectedCanonical = new FshCanonical("SpaceyExample").withLocation(new int[] {8, 24, 8, 51})
              .withFile("");
          assertAssignmentRule(profile.rules.get(0), "code.coding.system", expectedCanonical);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value using Canonical with a version")
        void canonicalVersion() {
          String input = leftAlign("""

              CodeSystem: Example
              * #first
              * #second

              Profile: ObservationProfile
              Parent: Observation
              * code.coding.system = Canonical(Example|1.2.3)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());

          FshCanonical expectedCanonical = new FshCanonical("Example").withLocation(new int[] {8, 24, 8, 47})
              .withFile("");
          expectedCanonical.version = "1.2.3";
          assertAssignmentRule(profile.rules.get(0), "code.coding.system", expectedCanonical);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned value using Canonical with a version which contains a |")
        void canonicalVersionWithPipe() {
          String input = leftAlign("""

              CodeSystem: Example
              * #first
              * #second

              Profile: ObservationProfile
              Parent: Observation
              * code.coding.system = Canonical(  Example|1.2.3|aWeirdVersion  )
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());

          FshCanonical expectedCanonical = new FshCanonical("Example").withLocation(new int[] {8, 24, 8, 65})
              .withFile("");
          expectedCanonical.version = "1.2.3|aWeirdVersion";
          assertAssignmentRule(profile.rules.get(0), "code.coding.system", expectedCanonical);
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse assigned values that are an alias")
        void aliasValue() {
          String input = leftAlign("""

              Alias: EXAMPLE = http://example.org

              Profile: PatientProfile
              Parent: Patient
              * identifier.system = EXAMPLE
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("PatientProfile");
          assertEquals(1, profile.rules.size());
          assertAssignmentRule(profile.rules.get(0), "identifier.system", "http://example.org");
        }

        @Test @DisplayName("FSHImporter SD Rules #assignmentRule should parse an assigned value Resource rule")
        void resourceRule() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * contained[0] = SomeInstance
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertAssignmentRule(profile.rules.get(0), "contained[0]", "SomeInstance", false, true);
        }
      }

      @Nested
      @DisplayName("#onlyRule")
      class OnlyRuleTests {
        @Test @DisplayName("FSHImporter SD Rules #onlyRule should parse an only rule with one type")
        void oneType() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * value[x] only Quantity
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertOnlyRule(profile.rules.get(0), "value[x]", ort("Quantity"));
        }

        @Test @DisplayName("FSHImporter SD Rules #onlyRule should parse an only rule with multiple types")
        void multipleTypes() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * value[x] only Quantity or CodeableConcept or string
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertOnlyRule(profile.rules.get(0), "value[x]", ort("Quantity"), ort("CodeableConcept"), ort("string"));
        }

        @Test @DisplayName("FSHImporter SD Rules #onlyRule should parse an only rule with one numeric type name")
        void oneNumericType() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * value[x] only 123
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertOnlyRule(profile.rules.get(0), "value[x]", ort("123"));
        }

        @Test @DisplayName("FSHImporter SD Rules #onlyRule should parse an only rule with multiple numeric type names")
        void multipleNumericTypes() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * value[x] only 123 or 456 or 789
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertOnlyRule(profile.rules.get(0), "value[x]", ort("123"), ort("456"), ort("789"));
        }

        @Test @DisplayName("FSHImporter SD Rules #onlyRule should parse an only rule with a reference to one type")
        void referenceOneType() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * performer only Reference(Practitioner)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertOnlyRule(profile.rules.get(0), "performer", ortRef("Practitioner"));
        }

        @Test @DisplayName("FSHImporter SD Rules #onlyRule should parse an only rule with a reference to multiple types")
        void referenceMultipleTypes() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * performer only Reference(Organization or CareTeam)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertOnlyRule(profile.rules.get(0), "performer", ortRef("Organization"), ortRef("CareTeam"));
        }

        @Test @DisplayName("FSHImporter SD Rules #onlyRule should parse an only rule with a reference to multiple types with whitespace")
        void referenceMultipleTypesWhitespace() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * performer only Reference(   Organization    or  CareTeam)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertOnlyRule(profile.rules.get(0), "performer", ortRef("Organization"), ortRef("CareTeam"));
        }

        @Test @DisplayName("FSHImporter SD Rules #onlyRule should parse an only rule with a CodeableReference to one type")
        void codeableReferenceOneType() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * performer only CodeableReference(Practitioner)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertOnlyRule(profile.rules.get(0), "performer", ortCodeableRef("Practitioner"));
        }

        @Test @DisplayName("FSHImporter SD Rules #onlyRule should parse an only rule with a CodeableReference to multiple types")
        void codeableReferenceMultipleTypes() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * performer only CodeableReference(Organization or CareTeam)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertOnlyRule(profile.rules.get(0), "performer", ortCodeableRef("Organization"),
              ortCodeableRef("CareTeam"));
        }

        @Test @DisplayName("FSHImporter SD Rules #onlyRule should parse an only rule with a CodeableReference to multiple types with whitespace")
        void codeableReferenceMultipleTypesWhitespace() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * performer only CodeableReference(   Organization    or  CareTeam      )
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertOnlyRule(profile.rules.get(0), "performer", ortCodeableRef("Organization"),
              ortCodeableRef("CareTeam"));
        }

        @Test @DisplayName("FSHImporter SD Rules #onlyRule should allow and translate aliases for only types")
        void aliasesForOnlyTypes() {
          String input = leftAlign("""

              Alias: QUANTITY = http://hl7.org/fhir/StructureDefinition/Quantity
              Alias: CODING = http://hl7.org/fhir/StructureDefinition/Coding

              Profile: ObservationProfile
              Parent: Observation
              * value[x] only CodeableConcept or CODING or string or QUANTITY
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertOnlyRule(profile.rules.get(0), "value[x]", ort("CodeableConcept"),
              ort("http://hl7.org/fhir/StructureDefinition/Coding"), ort("string"),
              ort("http://hl7.org/fhir/StructureDefinition/Quantity"));
        }

        @Test @DisplayName("FSHImporter SD Rules #onlyRule should log an error when references are listed with pipes")
        void referencesWithPipes() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * performer only Reference(Organization | CareTeam)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          // Following is correct expectation. Error prevents the proper resolution of multiple references
          assertOnlyRule(profile.rules.get(0), "performer", ort("Reference(Organization"));
          assertMatchS("Using '|' to list references is no longer supported\\..*Line: 4\\D*",
              loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("FSHImporter SD Rules #onlyRule should log an error when references are listed with pipes with whitespace")
        void referencesWithPipesWhitespace() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * performer only Reference(   Organization  |   CareTeam)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          // Following is correct expectation. Error prevents the proper resolution of multiple references
          assertOnlyRule(profile.rules.get(0), "performer", ort("Reference("));
          assertMatchS("Using '|' to list references is no longer supported\\..*Line: 4\\D*",
              loggerSpy.getLastMessage("error"));
        }
      }

      @Nested
      @DisplayName("#containsRule")
      class ContainsRuleTests {
        @Test @DisplayName("FSHImporter SD Rules #containsRule should parse contains rule with one item")
        void oneItem() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * component contains SystolicBP 1..1
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(2, profile.rules.size());
          assertContainsRule(profile.rules.get(0), "component", "SystolicBP");
          assertCardRule(profile.rules.get(1), "component[SystolicBP]", 1, 1);
        }

        @Test @DisplayName("FSHImporter SD Rules #containsRule should parse contains rule with one item declaring an aliased type")
        void oneItemAliasedType() {
          String input = leftAlign("""

              Alias: OffsetExtension = http://hl7.org/fhir/StructureDefinition/observation-timeOffset
              Profile: ObservationProfile
              Parent: Observation
              * component.extension contains OffsetExtension named offset 0..1
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(2, profile.rules.size());
          assertContainsRule(profile.rules.get(0), "component.extension",
              item("offset", "http://hl7.org/fhir/StructureDefinition/observation-timeOffset"));
          assertCardRule(profile.rules.get(1), "component.extension[offset]", 0, 1);
        }

        @Test @DisplayName("FSHImporter SD Rules #containsRule should parse contains rule with one item declaring an FSH extension type")
        void oneItemFshExtensionType() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * component.extension contains ComponentExtension named compext 0..1

              Extension: ComponentExtension
              Id: component-extension
              * value[x] only CodeableConcept
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(2, profile.rules.size());
          assertContainsRule(profile.rules.get(0), "component.extension", item("compext", "ComponentExtension"));
          assertCardRule(profile.rules.get(1), "component.extension[compext]", 0, 1);
        }

        @Test @DisplayName("FSHImporter SD Rules #containsRule should parse contains rules with multiple items")
        void multipleItems() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * component contains SystolicBP 1..1 and DiastolicBP 2..*
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(3, profile.rules.size());
          assertContainsRule(profile.rules.get(0), "component", "SystolicBP", "DiastolicBP");
          assertCardRule(profile.rules.get(1), "component[SystolicBP]", 1, 1);
          assertCardRule(profile.rules.get(2), "component[DiastolicBP]", 2, "*");
        }

        @Test @DisplayName("FSHImporter SD Rules #containsRule should parse contains rule with mutliple items, some declaring types")
        void multipleItemsSomeTypes() {
          String input = leftAlign("""

              Alias: FocusCodeExtension = http://hl7.org/fhir/StructureDefinition/observation-focusCode
              Alias: PreconditionExtension = http://hl7.org/fhir/StructureDefinition/observation-precondition
              Profile: ObservationProfile
              Parent: Observation
              * extension contains
                  foo 0..1 and
                  FocusCodeExtension named focus 1..1 and
                  bar 0..* and
                  PreconditionExtension named pc 1..*
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(5, profile.rules.size());
          assertContainsRule(profile.rules.get(0), "extension", "foo",
              item("focus", "http://hl7.org/fhir/StructureDefinition/observation-focusCode"), "bar",
              item("pc", "http://hl7.org/fhir/StructureDefinition/observation-precondition"));
          assertCardRule(profile.rules.get(1), "extension[foo]", 0, 1);
          assertCardRule(profile.rules.get(2), "extension[focus]", 1, 1);
          assertCardRule(profile.rules.get(3), "extension[bar]", 0, "*");
          assertCardRule(profile.rules.get(4), "extension[pc]", 1, "*");
        }

        @Test @DisplayName("FSHImporter SD Rules #containsRule should parse contains rules with flags")
        void withFlags() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * component contains SystolicBP 1..1 MS D and DiastolicBP 2..* MS SU
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(5, profile.rules.size());
          assertContainsRule(profile.rules.get(0), "component", "SystolicBP", "DiastolicBP");
          assertCardRule(profile.rules.get(1), "component[SystolicBP]", 1, 1);
          assertFlagRule(profile.rules.get(2), "component[SystolicBP]", true, null, null, null, null, true);
          assertCardRule(profile.rules.get(3), "component[DiastolicBP]", 2, "*");
          assertFlagRule(profile.rules.get(4), "component[DiastolicBP]", true, true, null, null, null, null);
        }

        @Test @DisplayName("FSHImporter SD Rules #containsRule should parse contains rule with item declaring a type and flags")
        void typeAndFlags() {
          String input = leftAlign("""

              Alias: OffsetExtension = http://hl7.org/fhir/StructureDefinition/observation-timeOffset
              Profile: ObservationProfile
              Parent: Observation
              * component.extension contains OffsetExtension named offset 0..1 MS TU
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(3, profile.rules.size());
          assertContainsRule(profile.rules.get(0), "component.extension",
              item("offset", "http://hl7.org/fhir/StructureDefinition/observation-timeOffset"));
          assertCardRule(profile.rules.get(1), "component.extension[offset]", 0, 1);
          assertFlagRule(profile.rules.get(2), "component.extension[offset]", true, null, null, true, null, null);
        }

        @Test @DisplayName("FSHImporter SD Rules #containsRule should parse contains rule on a choice with two items")
        void choiceWithTwoItems() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * value[x] contains valueString 0..1 and valueOther 0..1
              * value[x][valueString] only string
              * value[x][valueOther] only Quantity or CodeableConcept
              * value[x][valueOther].extension 1..1
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(6, profile.rules.size());
          assertContainsRule(profile.rules.get(0), "value[x]", "valueString", "valueOther");
          assertCardRule(profile.rules.get(1), "value[x][valueString]", 0, 1);
          assertCardRule(profile.rules.get(2), "value[x][valueOther]", 0, 1);
          assertOnlyRule(profile.rules.get(3), "value[x][valueString]", ort("string"));
          assertOnlyRule(profile.rules.get(4), "value[x][valueOther]", ort("Quantity"), ort("CodeableConcept"));
          assertCardRule(profile.rules.get(5), "value[x][valueOther].extension", 1, 1);
        }
      }

      @Nested
      @DisplayName("#caretValueRule")
      class CaretValueRuleTests {
        @Test @DisplayName("FSHImporter SD Rules #caretValueRule should parse caret value rules with no path")
        void noPath() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * ^description = "foo"
              * ^experimental = false
              * ^keyword[0] = foo#bar "baz"
              """);
          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertCaretValueRule(profile.rules.get(0), "", "description", "foo", false);
          assertCaretValueRule(profile.rules.get(1), "", "experimental", false, false);
          assertCaretValueRule(profile.rules.get(2), "", "keyword[0]",
              new FshCode("bar", "foo", "baz").withLocation(new int[] {6, 17, 6, 29}).withFile(""), false);
        }

        @Test @DisplayName("FSHImporter SD Rules #caretValueRule should parse caret value rules with a path")
        void withPath() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * status ^short = "foo"
              * status ^sliceIsConstraining = false
              * status ^code[0] = foo#bar "baz"
              """);
          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertCaretValueRule(profile.rules.get(0), "status", "short", "foo", false);
          assertCaretValueRule(profile.rules.get(1), "status", "sliceIsConstraining", false, false);
          assertCaretValueRule(profile.rules.get(2), "status", "code[0]",
              new FshCode("bar", "foo", "baz").withLocation(new int[] {6, 21, 6, 33}).withFile(""), false);
        }

        @Test @DisplayName("FSHImporter SD Rules #caretValueRule should not include non-breaking spaces as part of the caret path")
        void nonBreakingSpaces() {
          String input = leftAlign("\n"
              + "        Profile: ObservationProfile\n"
              + "        Parent: Observation\n"
              + "        * status ^short = \"Non-breaking\"\n"
              + "        ");
          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertCaretValueRule(profile.rules.get(0), "status", "short", "Non-breaking", false);
        }

        @Test @DisplayName("FSHImporter SD Rules #caretValueRule should add resources to the contained array using a CaretValueRule")
        void containedResource() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * ^contained = myResource
              """);
          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertCaretValueRule(profile.rules.get(0), "", "contained", "myResource", true);
        }

        @Test @DisplayName("FSHImporter SD Rules #caretValueRule should keep the raw value of a caret value rule when the value is a number or boolean")
        void rawValue() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * ^extension[0].valueAddress = 0064
              * ^extension[1].valueAddress = false
              """);
          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertCaretValueRule(profile.rules.get(0), "", "extension[0].valueAddress", BigInteger.valueOf(64), false,
              null, "0064");
          assertCaretValueRule(profile.rules.get(1), "", "extension[1].valueAddress", false, false, null, "false");
        }
      }

      @Nested
      @DisplayName("#obeysRule")
      class ObeysRuleTests {
        @Test @DisplayName("FSHImporter SD Rules #obeysRule should parse an obeys rule with one invariant and no path")
        void oneInvariantNoPath() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * obeys SomeInvariant
              """);
          FSHDocument result = importSingleText(input, "Obeys.fsh");
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertObeysRule(profile.rules.get(0), "", "SomeInvariant");
        }

        @Test @DisplayName("FSHImporter SD Rules #obeysRule should parse an obeys rule with one invariant and a path")
        void oneInvariantPath() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category obeys SomeInvariant
              """);
          FSHDocument result = importSingleText(input, "Obeys.fsh");
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertObeysRule(profile.rules.get(0), "category", "SomeInvariant");
        }

        @Test @DisplayName("FSHImporter SD Rules #obeysRule should parse an obeys rule with multiple invariants and no path")
        void multipleInvariantsNoPath() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * obeys SomeInvariant and ThisInvariant and ThatInvariant
              """);
          FSHDocument result = importSingleText(input, "Obeys.fsh");
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(3, profile.rules.size());
          assertObeysRule(profile.rules.get(0), "", "SomeInvariant");
          assertObeysRule(profile.rules.get(1), "", "ThisInvariant");
          assertObeysRule(profile.rules.get(2), "", "ThatInvariant");
        }

        @Test @DisplayName("FSHImporter SD Rules #obeysRule should parse an obeys rule with multiple invariants and a path")
        void multipleInvariantsPath() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category obeys SomeInvariant and ThisInvariant and ThatInvariant
              """);
          FSHDocument result = importSingleText(input, "Obeys.fsh");
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(3, profile.rules.size());
          assertObeysRule(profile.rules.get(0), "category", "SomeInvariant");
          assertObeysRule(profile.rules.get(1), "category", "ThisInvariant");
          assertObeysRule(profile.rules.get(2), "category", "ThatInvariant");
        }

        @Test @DisplayName("FSHImporter SD Rules #obeysRule should parse an obeys rule with a numeric invariant name")
        void numericInvariant() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * obeys 123
              """);
          FSHDocument result = importSingleText(input, "Obeys.fsh");
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertObeysRule(profile.rules.get(0), "", "123");
        }
      }

      @Nested
      @DisplayName("#insertRule")
      class InsertRuleTests {
        FSHImporter importer;
        final FshLogger.Stats stats = FshLogger.logger.stats;

        private ParamRuleSet prs(String name, int[] location, List<String> parameters, String contents) {
          ParamRuleSet ruleSet = new ParamRuleSet(name).withFile("RuleSet.fsh").withLocation(location);
          ruleSet.parameters = parameters;
          ruleSet.contents = contents;
          importer.paramRuleSets.put(ruleSet.name, ruleSet);
          return ruleSet;
        }

        @BeforeEach
        void setUp() {
          // To explain a bit about why stats are used here and not in other tests:
          // When parsing generated documents, the logging level is temporarily raised
          // in order to suppress console output. However, messages still go to the logger
          // and therefore are detected by loggerSpy. However, the stats should provide an
          // accurate reflection of the number of times that errors and warnings were logged
          // while the logger is at the normal level.
          loggerSpy.reset();
          stats.reset();
          importer = new FSHImporter();
          // RuleSet: OneParamRuleSet (val)
          // * status = {val}
          prs("OneParamRuleSet", new int[] {1, 12, 2, 27}, List.of("val"), "* status = {val}");
          // RuleSet: MultiParamRuleSet (status, value, maxNote)
          // * status = {status}
          // * valueString = {value}
          // * note 0..{maxNote}
          prs("MultiParamRuleSet", new int[] {4, 12, 7, 30}, List.of("status", "value", "maxNote"),
              String.join(EOL, "* status = {status}", "* valueString = {value}", "* note 0..{maxNote}"));
          // RuleSet: EntryRules (continuation)
          // * insert {continuation}Rules (5)
          prs("EntryRules", new int[] {9, 12, 10, 43}, List.of("continuation"), "* insert {continuation}Rules (5)");
          // RuleSet: RecursiveRules (value)
          // * interpretation 0..{value}
          // * insert EntryRules (BaseCase)
          prs("RecursiveRules", new int[] {12, 12, 14, 41}, List.of("value"),
              String.join(EOL, "* interpretation 0..{value}", "* insert EntryRules (BaseCase)"));
          // RuleSet: BaseCaseRules (value)
          // * note 0..{value}
          prs("BaseCaseRules", new int[] {16, 12, 17, 28}, List.of("value"), "* note 0..{value}");
          // RuleSet: CardRuleSet (path, min, max)
          // * {path} {min}..{max}
          // * note {min}..{max}
          prs("CardRuleSet", new int[] {19, 12, 21, 30}, List.of("path", "min", "max"),
              String.join(EOL, "* {path} {min}..{max}", "* note {min}..{max}"));
          // RuleSet: FirstRiskyRuleSet (value)
          // * note ={value}
          // * insert SecondRiskyRuleSet({value})
          prs("FirstRiskyRuleSet", new int[] {23, 12, 25, 47}, List.of("value"),
              String.join(EOL, "* note ={value}", "* insert SecondRiskyRuleSet({value})"));
          // RuleSet: SecondRiskyRuleSet(value)
          // * status ={value}
          prs("SecondRiskyRuleSet", new int[] {27, 12, 28, 28}, List.of("value"), "* status ={value}");
          // RuleSet: WarningRuleSet(value)
          // * focus[0] only Reference(Patient | {value})
          // * focus[1] only Reference(Group | {value})
          prs("WarningRuleSet", new int[] {30, 12, 32, 53}, List.of("value"), String.join(EOL,
              "* focus[0] only Reference(Patient | {value})", "* focus[1] only Reference(Group | {value})"));
          // clean up contents for all ParamRuleSets since the parser now includes a newline at the beginning of
          // contents
          importer.paramRuleSets.forEach((name, prs) -> prs.contents = EOL + prs.contents);
        }

        @Test @DisplayName("FSHImporter SD Rules #insertRule should parse an insert rule with a single RuleSet")
        void singleRuleSet() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert MyRuleSet
              """);
          FSHDocument result = importSingleText(input, "Insert.fsh");
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "MyRuleSet");
        }

        @Test @DisplayName("FSHImporter SD Rules #insertRule should parse an insert rule with a RuleSet with one parameter")
        void oneParameter() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert OneParamRuleSet (#final)
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "OneParamRuleSet", List.of("#final"));
          RuleSet appliedRuleSet = doc.appliedRuleSets.get(key("OneParamRuleSet", "#final"));
          assertNotNull(appliedRuleSet);
          assertToEqual(srcInfo("RuleSet.fsh", 1, 12, 2, 27), appliedRuleSet.sourceInfo);
          assertToEqual(srcInfo("RuleSet.fsh", 2, 1, 2, 17), appliedRuleSet.rules.get(0).sourceInfo);
        }

        @Test @DisplayName("FSHImporter SD Rules #insertRule should parse an insert rule with a RuleSet with multiple parameters")
        void multipleParameters() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert MultiParamRuleSet (#preliminary, "this is a string value\\, right?", 4)
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "MultiParamRuleSet",
              List.of("#preliminary", "\"this is a string value, right?\"", "4"));
          RuleSet appliedRuleSet = doc.appliedRuleSets.get(
              key("MultiParamRuleSet", "#preliminary", "\"this is a string value, right?\"", "4"));
          assertNotNull(appliedRuleSet);
          assertToEqual(srcInfo("RuleSet.fsh", 4, 12, 7, 30), appliedRuleSet.sourceInfo);
          assertEquals(3, appliedRuleSet.rules.size());
          assertAssignmentRule(appliedRuleSet.rules.get(0), "status",
              new FshCode("preliminary").withFile("Insert.fsh").withLocation(new int[] {2, 12, 2, 23}), false,
              false);
          assertEquals("RuleSet.fsh", appliedRuleSet.rules.get(0).sourceInfo.file);
          assertEquals(5, appliedRuleSet.rules.get(0).sourceInfo.location.startLine);
          assertEquals(5, appliedRuleSet.rules.get(0).sourceInfo.location.endLine);
          assertAssignmentRule(appliedRuleSet.rules.get(1), "valueString", "this is a string value, right?", false,
              false);
          assertEquals("RuleSet.fsh", appliedRuleSet.rules.get(1).sourceInfo.file);
          assertEquals(6, appliedRuleSet.rules.get(1).sourceInfo.location.startLine);
          assertEquals(6, appliedRuleSet.rules.get(1).sourceInfo.location.endLine);
          assertCardRule(appliedRuleSet.rules.get(2), "note", 0, "4");
          assertEquals("RuleSet.fsh", appliedRuleSet.rules.get(2).sourceInfo.file);
          assertEquals(7, appliedRuleSet.rules.get(2).sourceInfo.location.startLine);
          assertEquals(7, appliedRuleSet.rules.get(2).sourceInfo.location.endLine);
        }

        @Test @DisplayName("FSHImporter SD Rules #insertRule should parse an insert rule with a parameter that contains right parenthesis")
        void rightParenthesis() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert OneParamRuleSet (#final "(Final\\)")
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "OneParamRuleSet", List.of("#final \"(Final)\""));
          RuleSet appliedRuleSet = doc.appliedRuleSets.get(key("OneParamRuleSet", "#final \"(Final)\""));
          assertNotNull(appliedRuleSet);
          assertEquals(1, appliedRuleSet.rules.size());
          assertToEqual(srcInfo("RuleSet.fsh", 1, 12, 2, 27), appliedRuleSet.sourceInfo);
          assertAssignmentRule(appliedRuleSet.rules.get(0), "status",
              new FshCode("final", null, "(Final)").withFile("Insert.fsh").withLocation(new int[] {2, 12, 2, 27}),
              false, false);
        }

        @Test
        @DisplayName("FSHImporter SD Rules #insertRule should parse an insert rule with parameters that contain newline, tab, or backslash characters")
        void newlineTabBackslash() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert MultiParamRuleSet (#final, "very\\nstrange\\rvalue\\\\\\tindeed", 1)
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "MultiParamRuleSet",
              List.of("#final", "\"very\\nstrange\\rvalue\\\\\\tindeed\"", "1"));
          RuleSet appliedRuleSet = doc.appliedRuleSets.get(
              key("MultiParamRuleSet", "#final", "\"very\\nstrange\\rvalue\\\\\\tindeed\"", "1"));
          assertNotNull(appliedRuleSet);
          assertToEqual(srcInfo("RuleSet.fsh", 4, 12, 7, 30), appliedRuleSet.sourceInfo);
          assertEquals(3, appliedRuleSet.rules.size());
          assertAssignmentRule(appliedRuleSet.rules.get(0), "status",
              new FshCode("final").withFile("Insert.fsh").withLocation(new int[] {2, 12, 2, 17}), false, false);
          assertAssignmentRule(appliedRuleSet.rules.get(1), "valueString", "very\nstrange\rvalue\\\tindeed", false,
              false);
          assertCardRule(appliedRuleSet.rules.get(2), "note", 0, "1");
        }

        @Test @DisplayName("FSHImporter SD Rules #insertRule should parse an insert rule that separates its parameters onto multiple lines")
        void parametersOnMultipleLines() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert MultiParamRuleSet (
                #final,
                "string value",
                7
              )
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "MultiParamRuleSet",
              List.of("#final", "\"string value\"", "7"));
          RuleSet appliedRuleSet = doc.appliedRuleSets.get(
              key("MultiParamRuleSet", "#final", "\"string value\"", "7"));
          assertNotNull(appliedRuleSet);
          assertToEqual(srcInfo("RuleSet.fsh", 4, 12, 7, 30), appliedRuleSet.sourceInfo);
          assertEquals(3, appliedRuleSet.rules.size());
          assertAssignmentRule(appliedRuleSet.rules.get(0), "status",
              new FshCode("final").withFile("Insert.fsh").withLocation(new int[] {2, 12, 2, 17}), false, false);
          assertAssignmentRule(appliedRuleSet.rules.get(1), "valueString", "string value", false, false);
          assertCardRule(appliedRuleSet.rules.get(2), "note", 0, "7");
        }

        @Test
        @DisplayName("FSHImporter SD Rules #insertRule should generate a RuleSet only once when inserted with the same parameters multiple times in the same document")
        @Disabled("TS-only: jest.spyOn(importer, 'visitDoc') call count; FSHImporter.visitDoc is private")
        void generateRuleSetOnce() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert MultiParamRuleSet (#preliminary, "something", 3)
              * insert MultiParamRuleSet (#preliminary, "something", 3)
              """);
          // const visitDocSpy = jest.spyOn(importer, 'visitDoc');
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          assertEquals(1, doc.appliedRuleSets.size());
          // expect one call to visitDoc for the Profile, and one for the generated RuleSet
          // expect(visitDocSpy).toHaveBeenCalledTimes(2);  -- not portable (private method)
          // ensure the insert rules are still there (once upon a time, a bug caused the repeated rules to be omitted)
          Profile profile = doc.profiles.get("ObservationProfile");
          assertNotNull(profile);
          assertEquals(2, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "MultiParamRuleSet",
              List.of("#preliminary", "\"something\"", "3"));
          assertInsertRule(profile.rules.get(1), "", "MultiParamRuleSet",
              List.of("#preliminary", "\"something\"", "3"));
        }

        @Test
        @DisplayName("FSHImporter SD Rules #insertRule should parse an insert rule with parameters that will use the same RuleSet more than once with different parameters each time")
        void sameRuleSetDifferentParameters() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert EntryRules (Recursive)
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          assertEquals(4, doc.appliedRuleSets.size());

          RuleSet firstEntryRules = doc.appliedRuleSets.get(key("EntryRules", "Recursive"));
          assertNotNull(firstEntryRules);
          assertToEqual(srcInfo("RuleSet.fsh", 9, 12, 10, 43), firstEntryRules.sourceInfo);
          assertEquals(1, firstEntryRules.rules.size());
          assertInsertRule(firstEntryRules.rules.get(0), "", "RecursiveRules", List.of("5"));

          RuleSet recursiveRules = doc.appliedRuleSets.get(key("RecursiveRules", "5"));
          assertNotNull(recursiveRules);
          assertToEqual(srcInfo("RuleSet.fsh", 12, 12, 14, 41), recursiveRules.sourceInfo);
          assertEquals(2, recursiveRules.rules.size());
          assertCardRule(recursiveRules.rules.get(0), "interpretation", 0, "5");
          assertInsertRule(recursiveRules.rules.get(1), "", "EntryRules", List.of("BaseCase"));

          RuleSet secondEntryRules = doc.appliedRuleSets.get(key("EntryRules", "BaseCase"));
          assertToEqual(srcInfo("RuleSet.fsh", 9, 12, 10, 43), secondEntryRules.sourceInfo);
          assertEquals(1, secondEntryRules.rules.size());
          assertInsertRule(secondEntryRules.rules.get(0), "", "BaseCaseRules", List.of("5"));

          RuleSet baseCaseRules = doc.appliedRuleSets.get(key("BaseCaseRules", "5"));
          assertNotNull(baseCaseRules);
          assertToEqual(srcInfo("RuleSet.fsh", 16, 12, 17, 28), baseCaseRules.sourceInfo);
          assertEquals(1, baseCaseRules.rules.size());
          assertCardRule(baseCaseRules.rules.get(0), "note", 0, "5");
        }

        @Test
        @DisplayName("FSHImporter SD Rules #insertRule should log an error and not add a rule when an insert rule has the wrong number of parameters")
        void wrongNumberOfParameters() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert OneParamRuleSet (#final, "Final")
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(0, profile.rules.size());
          assertMatchS("Incorrect number of parameters applied to RuleSet", loggerSpy.getLastMessage("error"));
          assertMatchS("File: Insert\\.fsh.*Line: 4", loggerSpy.getLastMessage("error"));
        }

        @Test
        @DisplayName("FSHImporter SD Rules #insertRule should log an error and not add a rule when an insert rule with parameters refers to an undefined parameterized RuleSet")
        void undefinedParameterizedRuleSet() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert MysteriousRuleSet ("mystery")
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(0, profile.rules.size());
          assertMatchS("Could not find parameterized RuleSet named MysteriousRuleSet",
              loggerSpy.getLastMessage("error"));
          assertMatchS("File: Insert\\.fsh.*Line: 4", loggerSpy.getLastMessage("error"));
        }

        @Test
        @DisplayName("FSHImporter SD Rules #insertRule should log an error when an insert rule with parameters results in a parser error in the generated RuleSet")
        void parserErrorInGeneratedRuleSet() {
          String input = leftAlign("""

              Profile: MyObservation
              Parent: Observation
              * insert CardRuleSet(path with spaces, 1, *)
              """);
          importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));

          assertEquals(1, stats.numError);
          assertMatchS("Error parsing insert rule with parameterized RuleSet CardRuleSet",
              loggerSpy.getLastMessage("error"));
          assertMatchS("File: Insert\\.fsh.*Line: 4", loggerSpy.getLastMessage("error"));
        }

        @Test
        @DisplayName("FSHImporter SD Rules #insertRule should log one error when nested insert rules with parameters result in multiple parser errors in the generated RuleSets")
        void nestedParserErrors() {
          String input = leftAlign("""

              Profile: MyObservation
              Parent: Observation
              * note 0..1
              * insert FirstRiskyRuleSet("Observation.id")
              """);
          importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));

          assertEquals(1, stats.numError);
          assertMatchS("Error parsing insert rule with parameterized RuleSet FirstRiskyRuleSet",
              loggerSpy.getLastMessage("error"));
          assertMatchS("Assignment rules must include at least one space both before and after the '=' sign.*"
              + "File: Insert\\.fsh.*Line: 5", loggerSpy.getLastMessage("error"));
        }

        @Test
        @DisplayName("FSHImporter SD Rules #insertRule should not log an error when an insert rule with parameters results in rules that are syntactically correct but semantically invalid")
        void semanticallyInvalid() {
          String input = leftAlign("""

              Profile: MyObservation
              Parent: Observation
              * insert CardRuleSet(nonExistentPath, 7, 4)
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);

          assertEquals(1, doc.appliedRuleSets.size());
          RuleSet appliedRuleSet = doc.appliedRuleSets.get(key("CardRuleSet", "nonExistentPath", "7", "4"));
          assertNotNull(appliedRuleSet);
          assertToEqual(srcInfo("RuleSet.fsh", 19, 12, 21, 30), appliedRuleSet.sourceInfo);
          // This rule is nonsense, of course, but figuring that out is the job of the exporter, not the importer.
          assertCardRule(appliedRuleSet.rules.get(0), "nonExistentPath", 7, "4");
          assertCardRule(appliedRuleSet.rules.get(1), "note", 7, "4");
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        @Test @DisplayName("FSHImporter SD Rules #insertRule should log one error when an insert rule with parameters results in warnings")
        void resultsInWarnings() {
          String input = leftAlign("""

              Profile: MyObservation
              Parent: Observation
              * insert WarningRuleSet(Device)
              """);
          importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));

          assertEquals(1, stats.numError);
          assertMatchS("Error parsing insert rule with parameterized RuleSet WarningRuleSet",
              loggerSpy.getLastMessage("error"));
          assertMatchS("Using '|' to list references is no longer supported\\. Use 'or' to list multiple references.*"
              + "File: Insert\\.fsh.*Line: 4", loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("FSHImporter SD Rules #insertRule should log one error when an insert rule with parameters results in non-parser errors")
        void nonParserErrors() {
          String input = leftAlign("""

              Profile: MyObservation
              Parent: Observation
              * insert CardRuleSet(nonExistentPath, , )
              """);
          importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));

          assertEquals(1, stats.numError);
          assertMatchS("Errors parsing insert rule with parameterized RuleSet CardRuleSet",
              loggerSpy.getLastMessage("error"));
          assertMatchS("File: Insert\\.fsh.*Line: 4", loggerSpy.getLastMessage("error"));
        }
      }
    }

    @Nested
    @DisplayName("LR Rules")
    class LRRules {
      // These rules are shared across these StructureDefinition entities:
      //   Resource, Logical
      @Nested
      @DisplayName("#addElementRule")
      class AddElementRuleTests {
        @Test @DisplayName("FSHImporter LR Rules #addElementRule should parse basic addElement rules with defaulted definition")
        void defaultedDefinition() {
          String input = leftAlign("""

              Resource: TestResource
              * isValid 1..1 boolean "short boolean"
              * stuff 0..* string "short string"
              * address 1..* Address "short Address"
              * person 0..1 Reference(Patient) "short Reference"
              * medication 0..1 Canonical(Medication) "short Canonical"
              * immunization 0..1 CodeableReference(Immunization) "short CodeableReference"
              """);

          FSHDocument result = importSingleText(input);
          Resource resource = result.resources.get("TestResource");
          assertEquals(6, resource.rules.size());
          assertAddElementRule(resource.rules.get(0), "isValid",
              args(1, "1", null, List.of(ort("boolean")), defs("short boolean", "short boolean")));
          assertAddElementRule(resource.rules.get(1), "stuff",
              args(0, "*", null, List.of(ort("string")), defs("short string", "short string")));
          assertAddElementRule(resource.rules.get(2), "address",
              args(1, "*", null, List.of(ort("Address")), defs("short Address", "short Address")));
          assertAddElementRule(resource.rules.get(3), "person",
              args(0, "1", null, List.of(ortRef("Patient")), defs("short Reference", "short Reference")));
          assertAddElementRule(resource.rules.get(4), "medication",
              args(0, "1", null, List.of(ortCanonical("Medication")), defs("short Canonical", "short Canonical")));
          assertAddElementRule(resource.rules.get(5), "immunization", args(0, "1", null,
              List.of(ortCodeableRef("Immunization")), defs("short CodeableReference", "short CodeableReference")));
        }

        @Test @DisplayName("FSHImporter LR Rules #addElementRule should parse basic addElement rules with specified definition")
        void specifiedDefinition() {
          String input = leftAlign("""

              Resource: TestResource
              * isValid 1..1 boolean "short boolean" "definition boolean"
              * stuff 0..* string "short string" "definition string"
              * address 1..* Address "short Address" "definition Address"
              * person 0..1 Reference(Patient) "short Reference" "definition Reference"
              * medication 0..1 Canonical(Medication|4.0.1) "short Canonical" "definition Canonical"
              * immunization 0..1 CodeableReference(Immunization) "short CodeableReference" "definition CodeableReference"
              """);

          FSHDocument result = importSingleText(input);
          Resource resource = result.resources.get("TestResource");
          assertEquals(6, resource.rules.size());
          assertAddElementRule(resource.rules.get(0), "isValid",
              args(1, "1", null, List.of(ort("boolean")), defs("short boolean", "definition boolean")));
          assertAddElementRule(resource.rules.get(1), "stuff",
              args(0, "*", null, List.of(ort("string")), defs("short string", "definition string")));
          assertAddElementRule(resource.rules.get(2), "address",
              args(1, "*", null, List.of(ort("Address")), defs("short Address", "definition Address")));
          assertAddElementRule(resource.rules.get(3), "person",
              args(0, "1", null, List.of(ortRef("Patient")), defs("short Reference", "definition Reference")));
          assertAddElementRule(resource.rules.get(4), "medication", args(0, "1", null,
              List.of(ortCanonical("Medication|4.0.1")), defs("short Canonical", "definition Canonical")));
          assertAddElementRule(resource.rules.get(5), "immunization", args(0, "1", null,
              List.of(ortCodeableRef("Immunization")),
              defs("short CodeableReference", "definition CodeableReference")));
        }

        @Test @DisplayName("FSHImporter LR Rules #addElementRule should parse addElement rules with multiple targetTypes")
        void multipleTargetTypes() {
          String input = leftAlign("""

              Resource: TestResource
              * isValid 1..1 boolean or number "short boolean"
              * stuff 0..* string or markdown "short string"
              * address 1..* Address "short Address"
              * person 0..1 HumanName or Reference(Patient or RelatedPerson) "short multi-type"
              * medication 0..1 CodeableConcept or Canonical(Medication or Immunization) "short multi-type 2"
              * immunization 0..1 CodeableConcept or CodeableReference(Medication or Immunization) "short multi-type 3"
              """);

          FSHDocument result = importSingleText(input);
          Resource resource = result.resources.get("TestResource");
          assertEquals(6, resource.rules.size());
          assertAddElementRule(resource.rules.get(0), "isValid",
              args(1, "1", null, List.of(ort("boolean"), ort("number")), defs("short boolean", "short boolean")));
          assertAddElementRule(resource.rules.get(1), "stuff",
              args(0, "*", null, List.of(ort("string"), ort("markdown")), defs("short string", "short string")));
          assertAddElementRule(resource.rules.get(2), "address",
              args(1, "*", null, List.of(ort("Address")), defs("short Address", "short Address")));
          assertAddElementRule(resource.rules.get(3), "person", args(0, "1", null,
              List.of(ortNotRef("HumanName"), ortRef("Patient"), ortRef("RelatedPerson")),
              defs("short multi-type", "short multi-type")));
          assertAddElementRule(resource.rules.get(4), "medication", args(0, "1", null,
              List.of(ortNotCanonical("CodeableConcept"), ortCanonical("Medication"), ortCanonical("Immunization")),
              defs("short multi-type 2", "short multi-type 2")));
          assertAddElementRule(resource.rules.get(5), "immunization", args(0, "1", null,
              List.of(ortNotCodeableRef("CodeableConcept"), ortCodeableRef("Medication"),
                  ortCodeableRef("Immunization")),
              defs("short multi-type 3", "short multi-type 3")));
        }

        @Test @DisplayName("FSHImporter LR Rules #addElementRule should parse addElement rules with flags")
        void withFlags() {
          String input = leftAlign("""

              Resource: TestResource
              * isValid 1..1 MS ?! boolean "short boolean"
              * stuff 0..* MS SU string "short string"
              * address 1..* N Address "short Address"
              * person 0..1 D TU Reference(Patient) "short Reference"
              """);

          FSHDocument result = importSingleText(input);
          Resource resource = result.resources.get("TestResource");
          assertEquals(4, resource.rules.size());
          assertAddElementRule(resource.rules.get(0), "isValid",
              args(1, "1", new Flags(true, null, true, null, null, null), List.of(ort("boolean")),
                  defs("short boolean", "short boolean")));
          assertAddElementRule(resource.rules.get(1), "stuff",
              args(0, "*", new Flags(true, true, null, null, null, null), List.of(ort("string")),
                  defs("short string", "short string")));
          assertAddElementRule(resource.rules.get(2), "address",
              args(1, "*", new Flags(null, null, null, null, true, null), List.of(ort("Address")),
                  defs("short Address", "short Address")));
          assertAddElementRule(resource.rules.get(3), "person",
              args(0, "1", new Flags(null, null, null, true, null, true), List.of(ortRef("Patient")),
                  defs("short Reference", "short Reference")));
        }

        @Test @DisplayName("FSHImporter LR Rules #addElementRule should parse addElement rules with docs")
        void withDocs() {
          String input = leftAlign("""

              Resource: TestResource
              * isValid 1..1 MS boolean "is it valid?"
              * stuff 0..* string "just stuff" "a list of some stuff"
              * address 1..* N Address "current addresses" "at least one address is required"
              * person 0..1 D TU Reference(Patient) "an associated patient" "added for TRIAL USE"
              """);

          FSHDocument result = importSingleText(input);
          Resource resource = result.resources.get("TestResource");
          assertEquals(4, resource.rules.size());
          assertAddElementRule(resource.rules.get(0), "isValid",
              args(1, "1", new Flags(true, null, null, null, null, null), List.of(ort("boolean")),
                  defs("is it valid?", "is it valid?")));
          assertAddElementRule(resource.rules.get(1), "stuff",
              args(0, "*", null, List.of(ort("string")), defs("just stuff", "a list of some stuff")));
          assertAddElementRule(resource.rules.get(2), "address",
              args(1, "*", new Flags(null, null, null, null, true, null), List.of(ort("Address")),
                  defs("current addresses", "at least one address is required")));
          assertAddElementRule(resource.rules.get(3), "person",
              args(0, "1", new Flags(null, null, null, true, null, true), List.of(ortRef("Patient")),
                  defs("an associated patient", "added for TRIAL USE")));
        }

        @Test @DisplayName("FSHImporter LR Rules #addElementRule should log an error for missing path")
        void missingPath() {
          String input = leftAlign("""

              Resource: TestResource
              * 1..1 boolean "short boolean"
              * stuff 0..* string "short string"
              * address 1..* Address "short Address"
              * person 0..1 Reference(Patient) "short Reference"
              """);

          FSHDocument result = importSingleText(input, "BadPath.fsh");
          Resource resource = result.resources.get("TestResource");
          assertMatchS("no viable alternative at input.*File: BadPath\\.fsh.*Line: 3\\D*",
              loggerSpy.getLastMessage("error"));
          // Error results in excluding the rule with the error, hence length of 3 rather than 4
          assertEquals(3, resource.rules.size());
        }

        @Test @DisplayName("FSHImporter LR Rules #addElementRule should log an error for missing cardinality")
        void missingCardinality() {
          String input = leftAlign("""

              Resource: TestResource
              * isValid 1..1 boolean "short boolean"
              * stuff string "short string"
              * address 1..* Address "short Address"
              * person 0..1 Reference(Patient) "short Reference"
              """);

          FSHDocument result = importSingleText(input, "BadCard.fsh");
          Resource resource = result.resources.get("TestResource");
          assertMatchS("extraneous input 'string' expecting \\{.*\\}.*File: BadCard\\.fsh.*Line: 4\\D*",
              loggerSpy.getLastMessage("error"));
          // Error results in excluding the rule with the error and subsequent rules,
          // hence length of 1 rather than 4
          assertEquals(1, resource.rules.size());
        }

        @Test @DisplayName("FSHImporter LR Rules #addElementRule should log an error when min cardinality is not specified")
        void minNotSpecified() {
          String input = leftAlign("""

              Logical: LogicalModel
              * isInValid ..* string "short string"
              """);

          FSHDocument result = importSingleText(input, "Invalid.fsh");
          Logical logical = result.logicals.get("LogicalModel");

          assertEquals(1, logical.rules.size());
          assertAddElementRule(logical.rules.get(0), "isInValid",
              args(Double.NaN, "*", null, List.of(ort("string")), defs("short string", "short string")));
          assertMatchS("The 'min' cardinality attribute in AddElementRule", loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("FSHImporter LR Rules #addElementRule should log an error when max cardinality is not specified")
        void maxNotSpecified() {
          String input = leftAlign("""

              Logical: LogicalModel
              * isInValid 0.. string "short string"
              """);

          FSHDocument result = importSingleText(input, "Invalid.fsh");
          Logical logical = result.logicals.get("LogicalModel");

          assertEquals(1, logical.rules.size());
          assertAddElementRule(logical.rules.get(0), "isInValid",
              args(0, "", null, List.of(ort("string")), defs("short string", "short string")));
          assertMatchS("The 'max' cardinality attribute in AddElementRule", loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("FSHImporter LR Rules #addElementRule should log an error for extra docs/strings")
        void extraDocs() {
          String input = leftAlign("""

              Resource: TestResource
              * isValid 1..1 MS boolean "is it valid?"
              * stuff 0..* string "just stuff" "a list of some stuff" "invalid additional string"
              * address 1..* N Address "current addresses" "at least one address is required"
              * person 0..1 D TU Reference(Patient) "an associated patient" "added for TRIAL USE"
              """);

          FSHDocument result = importSingleText(input, "BadDocs.fsh)");
          Resource resource = result.resources.get("TestResource");
          assertMatchS("extraneous input.*File: BadDocs\\.fsh.*Line: 4\\D*", loggerSpy.getLastMessage("error"));
          // Error results in excluding the following rules, hence length of 2 rather than 4
          assertEquals(2, resource.rules.size());
        }

        @Test @DisplayName("FSHImporter LR Rules #addElementRule should log an error for missing short")
        void missingShort() {
          String input = leftAlign("""

              Resource: TestResource
              * isValid 1..1 boolean
              * stuff 0..* string "short string"
              * address 1..* Address "short Address"
              * person 0..1 Reference(Patient) "short Reference"
              """);

          FSHDocument result = importSingleText(input, "BadDefs.fsh");
          Resource resource = result.resources.get("TestResource");
          assertMatchS("The 'short' attribute in AddElementRule for path 'isValid' must be specified.*"
              + "File: BadDefs\\.fsh.*Line: 3\\D*", loggerSpy.getLastMessage("error"));
          // Error results in element not having defs defined
          assertEquals(4, resource.rules.size());
          assertAddElementRule(resource.rules.get(0), "isValid", args(1, "1", null, List.of(ort("boolean")), null));
        }

        @Test @DisplayName("FSHImporter LR Rules #addElementRule should log an error for missing targetType with docs")
        void missingTargetTypeWithDocs() {
          String input = leftAlign("""

              Resource: TestResource
              * isValid 1..1 boolean "is it valid?"
              * stuff 0..* string "just stuff" "a list of some stuff"
              * address 1..* "current addresses" "at least one address is required"
              * person 0..1 Reference(Patient) "an associated patient" "added for TRIAL USE"
              """);

          FSHDocument result = importSingleText(input, "BadType.fsh");
          Resource resource = result.resources.get("TestResource");
          assertMatchS("extraneous input.*File: BadType\\.fsh.*Line: 5\\D*", loggerSpy.getLastMessage("error"));
          // Error results in excluding the following rules, hence length of 3 rather than 5
          assertEquals(3, resource.rules.size());
        }

        @Test @DisplayName("FSHImporter LR Rules #addElementRule should log a warning for missing targetType with flags and docs")
        void missingTargetTypeWithFlagsAndDocs() {
          String input = leftAlign("""

              Resource: TestResource
              * isValid 1..1 MS ?! boolean "is it valid?"
              * stuff 0..* MS SU string "just stuff" "a list of some stuff"
              * address 1..* N TU "current addresses" "at least one address is required"
              * person 0..1 D TU Reference(Patient) "an associated patient" "added for TRIAL USE"
              """);

          FSHDocument result = importSingleText(input, "BadType.fsh");
          Resource resource = result.resources.get("TestResource");
          assertMatchS("appears to be a flag value.*File: BadType\\.fsh.*Line: 5\\D*",
              loggerSpy.getLastMessage("warn"));
          assertEquals(4, resource.rules.size());
          assertAddElementRule(resource.rules.get(0), "isValid",
              args(1, "1", new Flags(true, null, true, null, null, null), List.of(ort("boolean")),
                  defs("is it valid?", null)));
          assertAddElementRule(resource.rules.get(1), "stuff",
              args(0, "*", new Flags(true, true, null, null, null, null), List.of(ort("string")),
                  defs("just stuff", "a list of some stuff")));
          assertAddElementRule(resource.rules.get(2), "address",
              args(1, "*", new Flags(null, null, null, null, true, null), List.of(ort("TU")),
                  defs("current addresses", "at least one address is required")));
          assertAddElementRule(resource.rules.get(3), "person",
              args(0, "1", new Flags(null, null, null, true, null, true), List.of(ortRef("Patient")),
                  defs("an associated patient", "added for TRIAL USE")));
        }

        @Test @DisplayName("FSHImporter LR Rules #addElementRule should log a error for missing targetType with modifier flag and docs")
        void missingTargetTypeWithModifierFlag() {
          String input = leftAlign("""

              Resource: TestResource
              * isValid 1..1 MS ?! boolean "is it valid?"
              * stuff 0..* MS SU string "just stuff" "a list of some stuff"
              * address 1..* N ?! "current addresses" "at least one address is required"
              * person 0..1 D TU Reference(Patient) "an associated patient" "added for TRIAL USE"
              """);

          FSHDocument result = importSingleText(input, "BadType.fsh");
          Resource resource = result.resources.get("TestResource");
          assertMatchS("extraneous input.*File: BadType\\.fsh.*Line: 5\\D*", loggerSpy.getLastMessage("error"));
          // The 'address' rule results in a CardRule and a FlagRule rather tha an AddElementRule.
          // Due to the error, the 'person' rule is not processed.
          assertEquals(4, resource.rules.size());
        }

        @Test @DisplayName("FSHImporter LR Rules #addElementRule should parse rules according to rule patterns for CardRule and AddElementRule")
        void cardRuleVsAddElementRule() {
          String input = leftAlign("""

              Resource: TestResource
              * isValid 1..1 boolean "is it valid?"
              * stuff 0..* string "just stuff" "a list of some stuff"
              * address 1..* "current addresses" "at least one address is required"
              * person 0..1 Reference(Patient) "an associated patient"
              """);

          FSHDocument result = importSingleText(input, "BadType.fsh");
          Resource resource = result.resources.get("TestResource");
          assertMatchS("extraneous input.*File: BadType\\.fsh.*Line: 5\\D*", loggerSpy.getLastMessage("error"));

          assertEquals(3, resource.rules.size());
          assertAddElementRule(resource.rules.get(0), "isValid", args(1, "1", null, List.of(ort("boolean")), null));
          assertAddElementRule(resource.rules.get(1), "stuff", args(0, "*", null, List.of(ort("string")), null));
          // There is no way to distinguish between a valid CardRule
          // and an invalid AddElementRule with a missing targetType,
          // so the CardRule wins.
          assertCardRule(resource.rules.get(2), "address", 1, "*");
          // Due to the error, the 'person' rule is not processed.
        }
      }
    }
  }
}
