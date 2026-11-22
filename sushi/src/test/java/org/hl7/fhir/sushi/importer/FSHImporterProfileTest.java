package org.hl7.fhir.sushi.importer;

import static org.hl7.fhir.sushi.importer.ImportText.importText;
import static org.hl7.fhir.sushi.testhelpers.Asserts.*;
import static org.hl7.fhir.sushi.testhelpers.ImportSingleText.importSingleText;
import static org.hl7.fhir.sushi.testhelpers.LeftAlign.leftAlign;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.FshCanonical;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshQuantity;
import org.hl7.fhir.sushi.fshtypes.FshRatio;
import org.hl7.fhir.sushi.fshtypes.FshReference;
import org.hl7.fhir.sushi.fshtypes.ParamRuleSet;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.RuleSet;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.common.js.Json;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/import/FSHImporter.Profile.test.ts (SUSHI 3.21.1)
class FSHImporterProfileTest {
  private static final String EOL = "\n";

  /** expect(s).toMatch(/re/s). */
  private static void assertMatchS(String re, String s) {
    assertNotNull(s, "no message for /" + re + "/");
    assertTrue(Pattern.compile(re, Pattern.DOTALL).matcher(s).find(), () -> "/" + re + "/s did not match: " + s);
  }

  /** JSON.stringify([...]) for the appliedRuleSets key. */
  private static String key(String... parts) {
    return Json.stringify(List.of(parts));
  }

  private static SourceInfo si(String file, int a, int b, int c, int d) {
    return new SourceInfo(file, new TextLocation(a, b, c, d));
  }

  @Nested
  @DisplayName("FSHImporter")
  class FSHImporterTests {
    @BeforeEach void resetSpy() {
      loggerSpy.reset();
    }

    @Nested
    @DisplayName("Profile")
    class ProfileTests {
      @Nested
      @DisplayName("#sdMetadata")
      class SdMetadata {
        @Test @DisplayName("should parse the simplest possible profile")
        void simplest() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              """);

          FSHDocument result = importSingleText(input, "Simple.fsh");
          assertEquals(1, result.profiles.size());
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals("ObservationProfile", profile.name);
          assertEquals("Observation", profile.parent);
          // if no id is explicitly set, should default to name
          assertEquals("ObservationProfile", profile.getId());
          assertToEqual(new TextLocation(2, 1, 3, 19), profile.sourceInfo.location);
          assertEquals("Simple.fsh", profile.sourceInfo.file);
        }

        @Test @DisplayName("should parse profile with name matching various possible tokens recognized as name")
        void nameTokens() {
          // This basically exercises all the tokens we accept for name:
          // SEQUENCE | NUMBER | KW_MS | KW_SU | KW_TU | KW_NORMATIVE | KW_DRAFT | KW_CODES | KW_VSREFERENCE | KW_SYSTEM;
          testToken("MyProfile"); // SEQUENCE
          testToken("123"); // NUMBER
          testToken("MS"); // KW_MS
          testToken("SU"); // KW_SU
          testToken("TU"); // KW_TU
          testToken("N"); // KW_NORMATIVE
          testToken("D"); // KW_DRAFT
          testToken("codes"); // KW_CODES
          testToken("valueset"); // KW_VSREFERENCE
          testToken("system"); // KW_SYSTEM
        }

        private void testToken(String token) {
          String input = leftAlign("""

              Profile: %s
              Parent: Observation
              * value[x] only boolean
              """.formatted(token));
          FSHDocument result = importSingleText(input);
          assertEquals(0, loggerSpy.getAllLogs("error").size());
          assertNotNull(result);
          assertEquals(1, result.profiles.size());
          Profile profile = result.profiles.get(token);
          assertNotNull(profile);
          assertEquals(token, profile.name);
        }

        @Test @DisplayName("should parse profile with additional metadata properties")
        void additionalMetadata() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              Id: observation-profile
              Title: "An Observation Profile"
              Description: "A profile on Observation"
              """);

          FSHDocument result = importSingleText(input);
          assertEquals(1, result.profiles.size());
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals("ObservationProfile", profile.name);
          assertEquals("Observation", profile.parent);
          assertEquals("observation-profile", profile.getId());
          assertEquals("An Observation Profile", profile.title);
          assertEquals("A profile on Observation", profile.description);
          assertToEqual(new TextLocation(2, 1, 6, 39), profile.sourceInfo.location);
        }

        @Test @DisplayName("should parse profile with numeric name, parent, and id")
        void numericNameParentId() {
          String input = leftAlign("""

              Profile: 123
              Parent: 456
              Id: 789
              """);

          FSHDocument result = importSingleText(input);
          assertEquals(1, result.profiles.size());
          Profile profile = result.profiles.get("123");
          assertEquals("123", profile.name);
          assertEquals("456", profile.parent);
          assertEquals("789", profile.getId());
        }

        @Test @DisplayName("should properly parse a multi-string description")
        void multiStringDescription() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              Description:
                \"""
                This is a multi-string description
                with a couple of paragraphs.

                This is the second paragraph.  It has bullet points w/ indentation:

                * Bullet 1
                  * Bullet A
                  * Bullet B
                    * Bullet i
                  * Bullet C
                * Bullet 2
                \"""
              """);

          FSHDocument result = importSingleText(input);
          assertEquals(1, result.profiles.size());
          Profile profile = result.profiles.get("ObservationProfile");
          List<String> expectedDescriptionLines = List.of(
              "This is a multi-string description",
              "with a couple of paragraphs.",
              "",
              "This is the second paragraph.  It has bullet points w/ indentation:",
              "",
              "* Bullet 1",
              "  * Bullet A",
              "  * Bullet B",
              "    * Bullet i",
              "  * Bullet C",
              "* Bullet 2");
          assertEquals(String.join("\n", expectedDescriptionLines), profile.description);
        }

        @Test @DisplayName("should accept and translate an alias for the parent")
        void aliasParent() {
          String input = leftAlign("""

              Alias: OBS = http://hl7.org/fhir/StructureDefinition/Observation

              Profile: ObservationProfile
              Parent: OBS
              """);

          FSHDocument result = importSingleText(input);
          assertEquals(1, result.profiles.size());
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals("ObservationProfile", profile.name);
          assertEquals("http://hl7.org/fhir/StructureDefinition/Observation", profile.parent);
        }

        @Test @DisplayName("should only apply each metadata attribute the first time it is declared")
        void firstMetadataOnly() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              Id: observation-profile
              Title: "An Observation Profile"
              Description: "A profile on Observation"
              Parent: DuplicateObservation
              Id: duplicate-observation-profile
              Title: "Duplicate Observation Profile"
              Description: "A duplicated profile on Observation"
              """);

          FSHDocument result = importSingleText(input);
          assertEquals(1, result.profiles.size());
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals("ObservationProfile", profile.name);
          assertEquals("observation-profile", profile.getId());
          assertEquals("An Observation Profile", profile.title);
          assertEquals("A profile on Observation", profile.description);
        }

        @Test @DisplayName("should log an error when encountering a duplicate metadata attribute")
        void duplicateMetadataError() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              Id: observation-profile
              Title: "An Observation Profile"
              Description: "A profile on Observation"
              Title: "Duplicate Observation Profile"
              Description: "A duplicated profile on Observation"
              """);

          importSingleText(input, "Dupe.fsh");
          assertMatchS("File: Dupe\\.fsh.*Line: 7\\D*", loggerSpy.getMessageAtIndex(-2, "error"));
          assertMatchS("File: Dupe\\.fsh.*Line: 8\\D*", loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("should log an error and skip the profile when encountering a profile with a name used by another profile")
        void duplicateName() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              Title: "First Observation Profile"

              Profile: ObservationProfile
              Parent: Observation
              Title: "Second Observation Profile"
              """);
          FSHDocument result = importSingleText(input, "SameName.fsh");
          assertEquals(1, result.profiles.size());
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals("First Observation Profile", profile.title);
          assertMatchS("Profile named ObservationProfile already exists", loggerSpy.getLastMessage("error"));
          assertMatchS("File: SameName\\.fsh.*Line: 6 - 8\\D*", loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("should log an error and skip the profile when encountering an profile with a name used by another profile in another file")
        void duplicateNameOtherFile() {
          String input1 = "\n"
              + "        Profile: SameProfile\n"
              + "        Title: \"First Profile\"\n"
              + "        Parent: Observation\n"
              + "      ";

          String input2 = "\n"
              + "        Profile: SameProfile\n"
              + "        Title: \"Second Profile\"\n"
              + "        Parent: Observation\n"
              + "      ";

          List<FSHDocument> result = importText(List.of(
              new RawFSH(input1, "File1.fsh"),
              new RawFSH(input2, "File2.fsh")));
          assertEquals(1, result.stream().mapToInt(d2 -> d2.profiles.size()).sum());
          Profile p = result.get(0).profiles.get("SameProfile");
          assertEquals("First Profile", p.title);
          assertMatchS("Profile named SameProfile already exists", loggerSpy.getLastMessage("error"));
          assertMatchS("File: File2\\.fsh.*Line: 2 - 4\\D*", loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("should log an error when the deprecated Mixins keyword is used")
        void deprecatedMixins() {
          String input = leftAlign("""

              Profile: SomeProfile
              Parent: Observation
              Mixins: RuleSet1 and RuleSet2
              """);

          FSHDocument result = importSingleText(input, "Deprecated.fsh");
          assertEquals(1, result.profiles.size());
          Profile extension = result.profiles.get("SomeProfile");
          assertEquals("SomeProfile", extension.name);
          assertMatchS("The 'Mixins' keyword is no longer supported\\.", loggerSpy.getLastMessage("error"));
          assertMatchS("File: Deprecated\\.fsh.*Line: 4\\D*", loggerSpy.getLastMessage("error"));
        }
      }

      @Nested
      @DisplayName("#cardRule")
      class CardRuleTests {
        @Test @DisplayName("should parse simple card rules")
        void simpleCard() {
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

        @Test @DisplayName("should parse card rule with only min")
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

        @Test @DisplayName("should parse card rule with only max")
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

        @Test @DisplayName("should log an error if neither side is specified")
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
          assertMatchS("Neither side of the cardinality was specified on path \\\"category\\\". A min, max, or both need"
              + " to be specified.\\D*", loggerSpy.getLastMessage("error"));
          assertMatchS("File: BadCard\\.fsh.*Line: 4\\D*", loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("should parse card rules w/ flags")
        void cardWithFlags() {
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

        @Test @DisplayName("should parse card rules w/ multiple flags")
        void cardWithMultipleFlags() {
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
        @Test @DisplayName("should parse single-path single-value flag rules")
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

        @Test @DisplayName("should parse single-path multi-value flag rules")
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

        @Test @DisplayName("should parse multi-path single-value flag rules")
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

        @Test @DisplayName("should parse multi-path multi-value flag rules")
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

        @Test @DisplayName("should log an error when paths are listed with commas")
        void commaPaths() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * category, value[x] , component MS SU N
              """);

          FSHDocument result = importSingleText(input, "Deprecated.fsh");
          Profile profile = result.profiles.get("ObservationProfile");
          assertNotNull(profile);
          assertMatchS("Using ',' to list items is no longer supported", loggerSpy.getLastMessage("error"));
          assertMatchS("File: Deprecated\\.fsh.*Line: 4\\D*", loggerSpy.getLastMessage("error"));
        }
      }

      @Nested
      @DisplayName("#BindingRule")
      class BindingRuleTests {
        @Test @DisplayName("should parse value set rules w/ names and strengths")
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

        @Test @DisplayName("should parse value set rules w/ urls and strengths")
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

        @Test @DisplayName("should accept and translate aliases for value set URLs")
        void aliasUrls() {
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

        @Test @DisplayName("should parse value set rules w/ numeric names")
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

        @Test @DisplayName("should parse value set rules w/ no strength and default to required")
        void defaultRequired() {
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

        @Test @DisplayName("should parse value set rules on Quantity")
        void onQuantity() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueQuantity from http://unitsofmeasure.org
              """);

          FSHDocument result = importSingleText(input, "UselessQuant.fsh");
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertBindingRule(profile.rules.get(0), "valueQuantity", "http://unitsofmeasure.org", "required");
        }

        @Test @DisplayName("should log an error when parsing value set rules using the unit keyword")
        void unitsKeyword() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueQuantity units from http://unitsofmeasure.org
              """);

          FSHDocument result = importSingleText(input, "Deprecated.fsh");
          Profile profile = result.profiles.get("ObservationProfile");
          assertNotNull(profile);
          assertMatchS("The 'units' keyword is no longer supported.*File: Deprecated\\.fsh.*Line: 4\\D*",
              loggerSpy.getLastMessage("error"));
        }
      }

      @Nested
      @DisplayName("#assignmentRule")
      class AssignmentRuleTests {
        @Test @DisplayName("should parse assigned value boolean rule")
        void booleanRule() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueBoolean = true
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertAssignmentRule(profile.rules.get(0), "valueBoolean", true);
        }

        @Test @DisplayName("should parse assigned value boolean rule with (exactly) modifier")
        void booleanExactly() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueBoolean = true (exactly)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertAssignmentRule(profile.rules.get(0), "valueBoolean", true, true);
        }

        @Test @DisplayName("should parse assigned value number (decimal) rule")
        void decimalRule() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueDecimal = 1.23
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertAssignmentRule(profile.rules.get(0), "valueDecimal", 1.23);
        }

        @Test @DisplayName("should parse assigned value number (integer) rule")
        void integerRule() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueInteger = 123
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertAssignmentRule(profile.rules.get(0), "valueInteger", BigInteger.valueOf(123));
        }

        @Test @DisplayName("should parse assigned value string rule")
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

        @Test @DisplayName("should parse assigned value multi-line string rule")
        void multilineStringRule() {
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

        @Test @DisplayName("should parse assigned value date rule")
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

        @Test @DisplayName("should parse assigned value time rule")
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

        @Test @DisplayName("should parse assigned value code rule")
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

        @Test @DisplayName("should parse assigned value CodeableConcept rule")
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

        @Test @DisplayName("should parse assigned value CodeableConcept rule with (exactly) modifier")
        void codeableConceptExactly() {
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

        @Test @DisplayName("should parse an assigned value FSHCode rule with units on Quantity")
        void codeOnQuantity() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueQuantity = http://unitsofmeasure.org#cGy
              """);

          FSHDocument result = importSingleText(input, "UselessUnits.fsh");
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshCode expectedCode = new FshCode("cGy", "http://unitsofmeasure.org")
              .withLocation(new int[] {4, 19, 4, 47}).withFile("UselessUnits.fsh");
          assertAssignmentRule(profile.rules.get(0), "valueQuantity", expectedCode);
        }

        @Test @DisplayName("should log an error when parsing an assigned value FSHCode rule using the unit keyword")
        void codeUnitsKeyword() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * valueQuantity units = http://unitsofmeasure.org#cGy
              """);

          FSHDocument result = importSingleText(input, "Deprecated.fsh");
          Profile profile = result.profiles.get("ObservationProfile");
          assertNotNull(profile);
          assertMatchS("The 'units' keyword is no longer supported.*File: Deprecated\\.fsh.*Line: 4\\D*",
              loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("should parse assigned value Quantity rule")
        void quantityRule() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * valueQuantity = 1.5 'mm'
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshQuantity expectedQuantity = new FshQuantity(1.5,
              new FshCode("mm", "http://unitsofmeasure.org").withLocation(new int[] {5, 23, 5, 26}).withFile(""))
              .withLocation(new int[] {5, 19, 5, 26}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueQuantity", expectedQuantity);
        }

        @Test @DisplayName("should parse assigned value Quantity rule with unit display")
        void quantityUnitDisplay() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * valueQuantity = 155.0 '[lb_av]' "lb"
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          FshQuantity expectedQuantity = new FshQuantity(155.0,
              new FshCode("[lb_av]", "http://unitsofmeasure.org", "lb")
                  .withLocation(new int[] {5, 25, 5, 33}).withFile(""))
              .withLocation(new int[] {5, 19, 5, 38}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueQuantity", expectedQuantity);
        }

        @Test @DisplayName("should parse assigned value Ratio rule")
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
              new FshQuantity(130.0,
                  new FshCode("mg", "http://unitsofmeasure.org").withLocation(new int[] {5, 20, 5, 23}).withFile(""))
                  .withLocation(new int[] {5, 16, 5, 23}).withFile(""),
              new FshQuantity(1.0,
                  new FshCode("dL", "http://unitsofmeasure.org").withLocation(new int[] {5, 29, 5, 32}).withFile(""))
                  .withLocation(new int[] {5, 27, 5, 32}).withFile(""))
              .withLocation(new int[] {5, 16, 5, 32}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueRatio", expectedRatio);
        }

        @Test @DisplayName("should parse assigned value Ratio rule w/ numeric numerator")
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
              new FshQuantity(1.0,
                  new FshCode("dL", "http://unitsofmeasure.org").withLocation(new int[] {5, 24, 5, 27}).withFile(""))
                  .withLocation(new int[] {5, 22, 5, 27}).withFile(""))
              .withLocation(new int[] {5, 16, 5, 27}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueRatio", expectedRatio);
        }

        @Test @DisplayName("should parse assigned value Ratio rule w/ numeric denominator")
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
              new FshQuantity(130.0,
                  new FshCode("mg", "http://unitsofmeasure.org").withLocation(new int[] {5, 20, 5, 23}).withFile(""))
                  .withLocation(new int[] {5, 16, 5, 23}).withFile(""),
              new FshQuantity(1.0).withLocation(new int[] {5, 27, 5, 27}).withFile(""))
              .withLocation(new int[] {5, 16, 5, 27}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "valueRatio", expectedRatio);
        }

        @Test @DisplayName("should parse assigned value Ratio rule w/ numeric numerator and denominator")
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

        @Test @DisplayName("should parse assigned value Reference rule")
        void referenceRule() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * basedOn = Reference(fooProfile)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());

          FshReference expectedReference = new FshReference("fooProfile")
              .withLocation(new int[] {5, 13, 5, 33}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "basedOn", expectedReference);
        }

        @Test @DisplayName("should parse assigned value Reference rules while allowing and translating aliases")
        void referenceAlias() {
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

        @Test @DisplayName("should parse assigned value Reference rule with a display string")
        void referenceDisplay() {
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

        @Test @DisplayName("should parse assigned value Reference rule with whitespace")
        void referenceWhitespace() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * basedOn = Reference(   fooProfile   )
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());

          FshReference expectedReference = new FshReference("fooProfile")
              .withLocation(new int[] {5, 13, 5, 39}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "basedOn", expectedReference);
        }

        @Test @DisplayName("should log an error when an assigned value Reference rule has a choice of references")
        void referenceChoiceError() {
          String input = leftAlign("""


              Profile: ObservationProfile
              Parent: Observation
              * basedOn = Reference(cakeProfile or pieProfile)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());

          FshReference expectedReference = new FshReference("cakeProfile")
              .withLocation(new int[] {5, 13, 5, 48}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "basedOn", expectedReference);
          assertMatchS("Multiple choices of references are not allowed when setting a value.*Line: 5\\D*",
              loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("should parse assigned value using Canonical")
        void canonicalRule() {
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

          FshCanonical expectedCanonical = new FshCanonical("Example")
              .withLocation(new int[] {8, 24, 8, 41}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "code.coding.system", expectedCanonical);
        }

        @Test @DisplayName("should parse assigned value using Canonical with spaces around entity name")
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

          FshCanonical expectedCanonical = new FshCanonical("SpaceyExample") // No spaces are included in the entityName
              .withLocation(new int[] {8, 24, 8, 51}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "code.coding.system", expectedCanonical);
        }

        @Test @DisplayName("should parse assigned value using Canonical with a version")
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

          FshCanonical expectedCanonical = new FshCanonical("Example")
              .withLocation(new int[] {8, 24, 8, 47}).withFile("");
          expectedCanonical.version = "1.2.3";
          assertAssignmentRule(profile.rules.get(0), "code.coding.system", expectedCanonical);
        }

        @Test @DisplayName("should parse assigned value using Canonical with a version which contains a |")
        void canonicalVersionPipe() {
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

          FshCanonical expectedCanonical = new FshCanonical("Example")
              .withLocation(new int[] {8, 24, 8, 65}).withFile("");
          expectedCanonical.version = "1.2.3|aWeirdVersion";
          assertAssignmentRule(profile.rules.get(0), "code.coding.system", expectedCanonical);
        }

        @Test @DisplayName("should log an error when an assigned value Canonical rule has a choice of canonicals")
        void canonicalChoiceError() {
          String input = leftAlign("""

              CodeSystem: Example
              * #first
              * #second

              Profile: ObservationProfile
              Parent: Observation
              * code.coding.system = Canonical(Example or OtherExample)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());

          FshCanonical expectedCanonical = new FshCanonical("Example")
              .withLocation(new int[] {8, 24, 8, 57}).withFile("");
          assertAssignmentRule(profile.rules.get(0), "code.coding.system", expectedCanonical);
          assertMatchS("Multiple choices of canonicals are not allowed when setting a value.*Line: 8\\D*",
              loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("should parse assigned values that are an alias")
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

        @Test @DisplayName("should parse an assigned value Resource rule")
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
        @Test @DisplayName("should parse an only rule with one type")
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

        @Test @DisplayName("should parse an only rule with multiple types")
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

        @Test @DisplayName("should parse an only rule with one numeric type name")
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

        @Test @DisplayName("should parse an only rule with multiple numeric type names")
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

        @Test @DisplayName("should parse an only rule with a reference to one type")
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

        @Test @DisplayName("should parse an only rule with a reference to multiple types")
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

        @Test @DisplayName("should parse an only rule with a reference to multiple types with whitespace")
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

        @Test @DisplayName("should parse an only rule with a canonical to one type")
        void canonicalOneType() {
          String input = leftAlign("""

              Profile: PlanDefinitionProfile
              Parent: PlanDefinition
              * action.definition[x] only Canonical(ActivityDefinition)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("PlanDefinitionProfile");
          assertEquals(1, profile.rules.size());
          assertOnlyRule(profile.rules.get(0), "action.definition[x]", ortCanonical("ActivityDefinition"));
        }

        @Test @DisplayName("should parse an only rule with a canonical to multiple types")
        void canonicalMultipleTypes() {
          String input = leftAlign("""

              Profile: PlanDefinitionProfile
              Parent: PlanDefinition
              * action.definition[x] only Canonical(ActivityDefinition or PlanDefinition)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("PlanDefinitionProfile");
          assertEquals(1, profile.rules.size());
          assertOnlyRule(profile.rules.get(0), "action.definition[x]", ortCanonical("ActivityDefinition"),
              ortCanonical("PlanDefinition"));
        }

        @Test @DisplayName("should parse an only rule with a canonical to multiple types with whitespace")
        void canonicalMultipleTypesWhitespace() {
          String input = leftAlign("""

              Profile: PlanDefinitionProfile
              Parent: PlanDefinition
              * action.definition[x] only Canonical(     ActivityDefinition   or    PlanDefinition    )
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("PlanDefinitionProfile");
          assertEquals(1, profile.rules.size());
          assertOnlyRule(profile.rules.get(0), "action.definition[x]", ortCanonical("ActivityDefinition"),
              ortCanonical("PlanDefinition"));
        }

        @Test @DisplayName("should parse an only rule with a canonical to multiple types with versions")
        void canonicalMultipleTypesVersions() {
          String input = leftAlign("""

              Profile: PlanDefinitionProfile
              Parent: PlanDefinition
              * action.definition[x] only Canonical(ActivityDefinition|4.0.1 or PlanDefinition|4.0.1)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("PlanDefinitionProfile");
          assertEquals(1, profile.rules.size());
          assertOnlyRule(profile.rules.get(0), "action.definition[x]", ortCanonical("ActivityDefinition|4.0.1"),
              ortCanonical("PlanDefinition|4.0.1"));
        }

        @Test @DisplayName("should allow and translate aliases for only types")
        void aliasTypes() {
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

        @Test @DisplayName("should log an error when references are listed with pipes")
        void pipesError() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * performer only Reference(Organization | CareTeam)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertNotNull(profile);
          assertMatchS("Using '\\|' to list references is no longer supported\\..*Line: 4\\D*",
              loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("should log an error when references are listed with pipes with whitespace")
        void pipesWhitespaceError() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * performer only Reference(   Organization  |   CareTeam)
              """);

          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertNotNull(profile);
          assertMatchS("Using '\\|' to list references is no longer supported\\..*Line: 4\\D*",
              loggerSpy.getLastMessage("error"));
        }
      }

      @Nested
      @DisplayName("#containsRule")
      class ContainsRuleTests {
        @Test @DisplayName("should parse contains rule with one item")
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

        @Test @DisplayName("should parse contains rule with one item declaring an aliased type")
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
              new ContainsRuleItem("offset", "http://hl7.org/fhir/StructureDefinition/observation-timeOffset"));
          assertCardRule(profile.rules.get(1), "component.extension[offset]", 0, 1);
        }

        @Test @DisplayName("should parse contains rule with one item declaring an FSH extension type")
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
          assertContainsRule(profile.rules.get(0), "component.extension",
              new ContainsRuleItem("compext", "ComponentExtension"));
          assertCardRule(profile.rules.get(1), "component.extension[compext]", 0, 1);
        }

        @Test @DisplayName("should parse contains rules with multiple items")
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

        @Test @DisplayName("should parse contains rule with mutliple items, some declaring types")
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
          assertContainsRule(profile.rules.get(0), "extension",
              "foo",
              new ContainsRuleItem("focus", "http://hl7.org/fhir/StructureDefinition/observation-focusCode"),
              "bar",
              new ContainsRuleItem("pc", "http://hl7.org/fhir/StructureDefinition/observation-precondition"));
          assertCardRule(profile.rules.get(1), "extension[foo]", 0, 1);
          assertCardRule(profile.rules.get(2), "extension[focus]", 1, 1);
          assertCardRule(profile.rules.get(3), "extension[bar]", 0, "*");
          assertCardRule(profile.rules.get(4), "extension[pc]", 1, "*");
        }

        @Test @DisplayName("should parse contains rules with flags")
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

        @Test @DisplayName("should parse contains rule with item declaring a type and flags")
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
              new ContainsRuleItem("offset", "http://hl7.org/fhir/StructureDefinition/observation-timeOffset"));
          assertCardRule(profile.rules.get(1), "component.extension[offset]", 0, 1);
          assertFlagRule(profile.rules.get(2), "component.extension[offset]", true, null, null, true, null, null);
        }
      }

      @Nested
      @DisplayName("#caretValueRule")
      class CaretValueRuleTests {
        @Test @DisplayName("should parse caret value rules with no path")
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

        @Test @DisplayName("should parse caret value rules with a . path")
        void dotPath() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * . ^definition = "foo"
              """);
          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertCaretValueRule(profile.rules.get(0), ".", "definition", "foo", false, List.of("."));
        }

        @Test @DisplayName("should parse caret value rules with a path")
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

        @Test @DisplayName("should not include non-breaking spaces as part of the caret path")
        void nonBreakingSpaces() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * status ^short = "Non-breaking"
              """);
          FSHDocument result = importSingleText(input);
          Profile profile = result.profiles.get("ObservationProfile");
          assertCaretValueRule(profile.rules.get(0), "status", "short", "Non-breaking", false);
        }

        @Test @DisplayName("should add resources to the contained array using a CaretValueRule")
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
      }

      @Nested
      @DisplayName("#obeysRule")
      class ObeysRuleTests {
        @Test @DisplayName("should parse an obeys rule with one invariant and no path")
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

        @Test @DisplayName("should parse an obeys rule with one invariant and a path")
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

        @Test @DisplayName("should parse an obeys rule with multiple invariants and no path")
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

        @Test @DisplayName("should parse an obeys rule with multiple invariants and a path")
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

        @Test @DisplayName("should parse an obeys rule with a numeric invariant name")
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
      @DisplayName("#pathRule")
      class PathRuleTests {
        @Test @DisplayName("should parse a pathRule")
        void pathRule() {
          String input = leftAlign("""

              Profile: PatientProfile
              Parent: Patient
              * name
              """);
          FSHDocument result = importSingleText(input, "Path.fsh");
          Profile profile = result.profiles.get("PatientProfile");
          assertEquals(0, profile.rules.size());
        }
      }

      @Nested
      @DisplayName("#insertRule")
      class InsertRuleTests {
        FSHImporter importer;

        private void addParamRuleSet(String name, int[] location, List<String> parameters, String contents) {
          ParamRuleSet prs = new ParamRuleSet(name).withFile("RuleSet.fsh").withLocation(location);
          prs.parameters = new java.util.ArrayList<>(parameters);
          prs.contents = contents;
          importer.paramRuleSets.put(prs.name, prs);
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
          FshLogger.logger.stats.reset();
          importer = new FSHImporter();
          // RuleSet: OneParamRuleSet (val)
          // * status = {val}
          addParamRuleSet("OneParamRuleSet", new int[] {1, 12, 2, 27}, List.of("val"), "* status = {val}");
          // RuleSet: MultiParamRuleSet (status, value, maxNote)
          // * status = {status}
          // * valueString = {value}
          // * note 0..{maxNote}
          addParamRuleSet("MultiParamRuleSet", new int[] {4, 12, 7, 30}, List.of("status", "value", "maxNote"),
              String.join(EOL, "* status = {status}", "* valueString = {value}", "* note 0..{maxNote}"));
          // RuleSet: EntryRules (continuation)
          // * insert {continuation}Rules (5)
          addParamRuleSet("EntryRules", new int[] {9, 12, 10, 43}, List.of("continuation"),
              "* insert {continuation}Rules (5)");
          // RuleSet: RecursiveRules (value)
          // * interpretation 0..{value}
          // * insert EntryRules (BaseCase)
          addParamRuleSet("RecursiveRules", new int[] {12, 12, 14, 41}, List.of("value"),
              String.join(EOL, "* interpretation 0..{value}", "* insert EntryRules (BaseCase)"));
          // RuleSet: BaseCaseRules (value)
          // * note 0..{value}
          addParamRuleSet("BaseCaseRules", new int[] {16, 12, 17, 28}, List.of("value"), "* note 0..{value}");
          // RuleSet: CardRuleSet (path, min, max)
          // * {path} {min}..{max}
          // * note {min}..{max}
          addParamRuleSet("CardRuleSet", new int[] {19, 12, 21, 30}, List.of("path", "min", "max"),
              String.join(EOL, "* {path} {min}..{max}", "* note {min}..{max}"));
          // RuleSet: FirstRiskyRuleSet (value)
          // * note ={value}
          // * insert SecondRiskyRuleSet({value})
          addParamRuleSet("FirstRiskyRuleSet", new int[] {23, 12, 25, 47}, List.of("value"),
              String.join(EOL, "* note ={value}", "* insert SecondRiskyRuleSet({value})"));
          // RuleSet: SecondRiskyRuleSet(value)
          // * status ={value}
          addParamRuleSet("SecondRiskyRuleSet", new int[] {27, 12, 28, 28}, List.of("value"), "* status ={value}");
          // RuleSet: WarningRuleSet(value)
          // * focus[0] only Reference(Patient | {value})
          // * focus[1] only Reference(Group | {value})
          // NOTE: This now causes ERRORS (not warnings), so associated test is skipped!
          addParamRuleSet("WarningRuleSet", new int[] {30, 12, 32, 53}, List.of("value"),
              String.join(EOL, "* focus[0] only Reference(Patient | {value})",
                  "* focus[1] only Reference(Group | {value})"));
          // RuleSet: TopLevelRules (status, value, identifier)
          // * status = {status}
          // * insert NestedRules([[{value}]])
          // * identifier.value = {identifier}
          addParamRuleSet("TopLevelRules", new int[] {34, 12, 37, 44}, List.of("status", "value", "identifier"),
              String.join(EOL, "* status = {status}", "* insert NestedRules([[{value}]])",
                  "* identifier.value = {identifier}"));
          // RuleSet: NestedRules(value)
          // * valueString = {value}
          addParamRuleSet("NestedRules", new int[] {39, 12, 40, 34}, List.of("value"), "* valueString = {value}");
          // RuleSet: QuotedValueRules(value)
          // * valueString = "START{value}END"
          addParamRuleSet("QuotedValueRules", new int[] {42, 12, 43, 44}, List.of("value"),
              "* valueString = \"START{value}END\"");
          // RuleSet: AboveQuotedRules(value)
          // * insert QuotedValueRules([[ begin here {value} end here ]])
          addParamRuleSet("AboveQuotedRules", new int[] {45, 12, 46, 61}, List.of("value"),
              "* insert QuotedValueRules([[ begin here {value} end here ]])");
          // RuleSet: IndentedRules(use, value)
          // * identifier
          //   * use = {use}
          //   * value = {value}
          addParamRuleSet("IndentedRules", new int[] {48, 12, 51, 30}, List.of("use", "value"),
              String.join(EOL, "* identifier", "  * use = {use}", "  * value = {value}"));
          // clean up contents for all ParamRuleSets since the parser now includes a newline at the beginning of contents
          importer.paramRuleSets.forEach((n, prs) -> prs.contents = EOL + prs.contents);
        }

        @Test @DisplayName("should parse an insert rule with a single RuleSet")
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

        @Test @DisplayName("should parse an insert rule with a single RuleSet and a path")
        void singleRuleSetPath() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * code insert MyRuleSet
              """);
          FSHDocument result = importSingleText(input, "Insert.fsh");
          Profile profile = result.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "code", "MyRuleSet");
        }

        @Test @DisplayName("should parse an insert rule with a RuleSet with one parameter")
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
          assertToEqual(si("RuleSet.fsh", 1, 12, 2, 27), appliedRuleSet.sourceInfo);
          assertToEqual(si("RuleSet.fsh", 2, 1, 2, 17), appliedRuleSet.rules.get(0).sourceInfo);
        }

        @Test @DisplayName("should parse an insert rule with a RuleSet with one parameter and a path")
        void oneParameterPath() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * code insert OneParamRuleSet (#final)
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "code", "OneParamRuleSet", List.of("#final"));
          RuleSet appliedRuleSet = doc.appliedRuleSets.get(key("OneParamRuleSet", "#final"));
          assertNotNull(appliedRuleSet);
          assertToEqual(si("RuleSet.fsh", 1, 12, 2, 27), appliedRuleSet.sourceInfo);
          assertToEqual(si("RuleSet.fsh", 2, 1, 2, 17), appliedRuleSet.rules.get(0).sourceInfo);
        }

        @Test @DisplayName("should parse an insert rule with a RuleSet with multiple parameters")
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
          assertToEqual(si("RuleSet.fsh", 4, 12, 7, 30), appliedRuleSet.sourceInfo);
          assertEquals(3, appliedRuleSet.rules.size());
          assertAssignmentRule(appliedRuleSet.rules.get(0), "status",
              new FshCode("preliminary").withFile("Insert.fsh").withLocation(new int[] {2, 12, 2, 23}), false, false);
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

        @Test @DisplayName("should parse an insert rule with a parameter that contains right parenthesis")
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
          assertToEqual(si("RuleSet.fsh", 1, 12, 2, 27), appliedRuleSet.sourceInfo);
          assertAssignmentRule(appliedRuleSet.rules.get(0), "status",
              new FshCode("final", null, "(Final)").withFile("Insert.fsh").withLocation(new int[] {2, 12, 2, 27}),
              false, false);
        }

        @Test @DisplayName("should parse an insert rule with parameters that contain newline, tab, or backslash characters")
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
          assertToEqual(si("RuleSet.fsh", 4, 12, 7, 30), appliedRuleSet.sourceInfo);
          assertEquals(3, appliedRuleSet.rules.size());
          assertAssignmentRule(appliedRuleSet.rules.get(0), "status",
              new FshCode("final").withFile("Insert.fsh").withLocation(new int[] {2, 12, 2, 17}), false, false);
          assertAssignmentRule(appliedRuleSet.rules.get(1), "valueString", "very\nstrange\rvalue\\\tindeed", false,
              false);
          assertCardRule(appliedRuleSet.rules.get(2), "note", 0, "1");
        }

        @Test @DisplayName("should parse an insert rule with parameters in double square brackets that contain space after the closing brackets")
        void doubleBracketsTrailingSpace() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert MultiParamRuleSet (
                [[#final]] ,
                [["regular value"]]  ,
                [[5]]
              )
              * insert MultiParamRuleSet (
                [[#final]]   ,
                [["different value"]],
                [[1]]
              )
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(2, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "MultiParamRuleSet",
              List.of("#final", "\"regular value\"", "5"));
          assertInsertRule(profile.rules.get(1), "", "MultiParamRuleSet",
              List.of("#final", "\"different value\"", "1"));
        }

        @Test @DisplayName("should parse an insert rule with parameters surrounded by double square brackets")
        void doubleBrackets() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert TopLevelRules(#final, [["fruits (apples, oranges, etc.)"]], [["identify me"]])
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "TopLevelRules",
              List.of("#final", "\"fruits (apples, oranges, etc.)\"", "\"identify me\""));
          RuleSet appliedTopLevelRules = doc.appliedRuleSets.get(
              key("TopLevelRules", "#final", "\"fruits (apples, oranges, etc.)\"", "\"identify me\""));
          assertNotNull(appliedTopLevelRules);
          assertEquals(3, appliedTopLevelRules.rules.size());
          assertAssignmentRule(appliedTopLevelRules.rules.get(0), "status",
              new FshCode("final").withFile("Insert.fsh").withLocation(new int[] {2, 12, 2, 17}), false, false);
          assertInsertRule(appliedTopLevelRules.rules.get(1), "", "NestedRules",
              List.of("\"fruits (apples, oranges, etc.)\""));
          assertAssignmentRule(appliedTopLevelRules.rules.get(2), "identifier.value", "identify me", false, false);
          RuleSet appliedNestedRules = doc.appliedRuleSets.get(
              key("NestedRules", "\"fruits (apples, oranges, etc.)\""));
          assertNotNull(appliedNestedRules);
          assertEquals(1, appliedNestedRules.rules.size());
          assertAssignmentRule(appliedNestedRules.rules.get(0), "valueString", "fruits (apples, oranges, etc.)",
              false, false);
        }

        @Test @DisplayName("should parse an insert rule with parameters containing a literal backslash followed by a comma, surrounded by double square brackets")
        void backslashCommaDoubleBrackets() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert TopLevelRules(#final, [["this is strange]]\\\\, please understand"]], [["identify me"]])
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "TopLevelRules",
              List.of("#final", "\"this is strange]]\\\\, please understand\"", "\"identify me\""));
          RuleSet appliedTopLevelRules = doc.appliedRuleSets.get(
              key("TopLevelRules", "#final", "\"this is strange]]\\\\, please understand\"", "\"identify me\""));
          assertNotNull(appliedTopLevelRules);
          assertEquals(3, appliedTopLevelRules.rules.size());
          assertAssignmentRule(appliedTopLevelRules.rules.get(0), "status",
              new FshCode("final").withFile("Insert.fsh").withLocation(new int[] {2, 12, 2, 17}), false, false);
          assertInsertRule(appliedTopLevelRules.rules.get(1), "", "NestedRules",
              List.of("\"this is strange]]\\\\, please understand\""));
          assertAssignmentRule(appliedTopLevelRules.rules.get(2), "identifier.value", "identify me", false, false);
          RuleSet appliedNestedRules = doc.appliedRuleSets.get(
              key("NestedRules", "\"this is strange]]\\\\, please understand\""));
          assertNotNull(appliedNestedRules);
          assertEquals(1, appliedNestedRules.rules.size());
          assertAssignmentRule(appliedNestedRules.rules.get(0), "valueString",
              "this is strange]]\\, please understand", false, false);
        }

        @Test @DisplayName("should parse an insert rule with parameters containing double closing square brackets surrounded by double square brackets")
        void doubleClosingBrackets() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert MultiParamRuleSet(#final, [["this is strange]]\\, please understand"]], 7)
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "MultiParamRuleSet",
              List.of("#final", "\"this is strange]], please understand\"", "7"));
          RuleSet appliedRuleSet = doc.appliedRuleSets.get(
              key("MultiParamRuleSet", "#final", "\"this is strange]], please understand\"", "7"));
          assertNotNull(appliedRuleSet);
          assertEquals(3, appliedRuleSet.rules.size());
          assertAssignmentRule(appliedRuleSet.rules.get(0), "status",
              new FshCode("final").withFile("Insert.fsh").withLocation(new int[] {2, 12, 2, 17}), false, false);
          assertAssignmentRule(appliedRuleSet.rules.get(1), "valueString", "this is strange]], please understand",
              false, false);
          assertCardRule(appliedRuleSet.rules.get(2), "note", 0, "7");
        }

        @Test @DisplayName("should parse an insert rule with parameters containing a literal backslash surrounded by square brackets")
        void literalBackslashBrackets() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert MultiParamRuleSet(#final, [["a literal\\\\backslash character, really?"]], 7)
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "MultiParamRuleSet",
              List.of("#final", "\"a literal\\\\backslash character, really?\"", "7"));
          RuleSet appliedRuleSet = doc.appliedRuleSets.get(
              key("MultiParamRuleSet", "#final", "\"a literal\\\\backslash character, really?\"", "7"));
          assertNotNull(appliedRuleSet);
          assertEquals(3, appliedRuleSet.rules.size());
          assertAssignmentRule(appliedRuleSet.rules.get(0), "status",
              new FshCode("final").withFile("Insert.fsh").withLocation(new int[] {2, 12, 2, 17}), false, false);
          assertAssignmentRule(appliedRuleSet.rules.get(1), "valueString", "a literal\\backslash character, really?",
              false, false);
          assertCardRule(appliedRuleSet.rules.get(2), "note", 0, "7");
        }

        @Test @DisplayName("should parse an insert rule with parameters containing a literal backslash followed by a comma, surrounded by square brackets")
        void literalBackslashCommaBrackets() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert MultiParamRuleSet(#final, [["a literal\\\\,backslash character, really?"]], 7)
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "MultiParamRuleSet",
              List.of("#final", "\"a literal\\\\,backslash character, really?\"", "7"));
          RuleSet appliedRuleSet = doc.appliedRuleSets.get(
              key("MultiParamRuleSet", "#final", "\"a literal\\\\,backslash character, really?\"", "7"));
          assertNotNull(appliedRuleSet);
          assertEquals(3, appliedRuleSet.rules.size());
          assertAssignmentRule(appliedRuleSet.rules.get(0), "status",
              new FshCode("final").withFile("Insert.fsh").withLocation(new int[] {2, 12, 2, 17}), false, false);
          assertAssignmentRule(appliedRuleSet.rules.get(1), "valueString",
              "a literal\\,backslash character, really?", false, false);
          assertCardRule(appliedRuleSet.rules.get(2), "note", 0, "7");
        }

        @Test @DisplayName("should apply the correct value to a nested insert rule when the substitution token is surrounded by double brackets")
        void nestedDoubleBrackets() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert TopLevelRules(#final, [["will need [[to escape]]\\, again"]], "identify me")
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "TopLevelRules",
              List.of("#final", "\"will need [[to escape]], again\"", "\"identify me\""));
          RuleSet appliedTopLevelRules = doc.appliedRuleSets.get(
              key("TopLevelRules", "#final", "\"will need [[to escape]], again\"", "\"identify me\""));
          assertNotNull(appliedTopLevelRules);
          assertEquals(3, appliedTopLevelRules.rules.size());
          assertInsertRule(appliedTopLevelRules.rules.get(1), "", "NestedRules",
              List.of("\"will need [[to escape]], again\""));
          assertAssignmentRule(appliedTopLevelRules.rules.get(2), "identifier.value", "identify me", false, false);
          RuleSet appliedNestedRules = doc.appliedRuleSets.get(
              key("NestedRules", "\"will need [[to escape]], again\""));
          assertNotNull(appliedNestedRules);
          assertEquals(1, appliedNestedRules.rules.size());
          assertAssignmentRule(appliedNestedRules.rules.get(0), "valueString", "will need [[to escape]], again",
              false, false);
        }

        @Test @DisplayName("should apply the correct value to a nested insert rule when the substitution token is with other contents inside double brackets")
        void nestedOtherContents() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert AboveQuotedRules([[(ah]]\\), yes]]\\, good]]\\)!]])
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "AboveQuotedRules", List.of("(ah]]), yes]], good]])!"));
          RuleSet appliedAboveQuotedRules = doc.appliedRuleSets.get(key("AboveQuotedRules", "(ah]]), yes]], good]])!"));
          assertNotNull(appliedAboveQuotedRules);
          assertEquals(1, appliedAboveQuotedRules.rules.size());
          assertInsertRule(appliedAboveQuotedRules.rules.get(0), "", "QuotedValueRules",
              List.of(" begin here (ah]]), yes]], good]])! end here "));
          RuleSet appliedQuotedValueRules = doc.appliedRuleSets.get(
              key("QuotedValueRules", " begin here (ah]]), yes]], good]])! end here "));
          assertNotNull(appliedQuotedValueRules);
          assertEquals(1, appliedQuotedValueRules.rules.size());
          assertAssignmentRule(appliedQuotedValueRules.rules.get(0), "valueString",
              "START begin here (ah]]), yes]], good]])! end here END", false, false);
        }

        @Test @DisplayName("should keep starting and ending space characters inside brackets when applying an insert rule")
        void keepSpaces() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert QuotedValueRules([[ and then, ]])
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "QuotedValueRules", List.of(" and then, "));
          RuleSet appliedRuleSet = doc.appliedRuleSets.get(key("QuotedValueRules", " and then, "));
          assertNotNull(appliedRuleSet);
          assertEquals(1, appliedRuleSet.rules.size());
          assertAssignmentRule(appliedRuleSet.rules.get(0), "valueString", "START and then, END");
        }

        @Test @DisplayName("should parse an insert rule that separates its parameters onto multiple lines")
        void multiLineParameters() {
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
          assertInsertRule(profile.rules.get(0), "", "MultiParamRuleSet", List.of("#final", "\"string value\"", "7"));
          RuleSet appliedRuleSet = doc.appliedRuleSets.get(
              key("MultiParamRuleSet", "#final", "\"string value\"", "7"));
          assertNotNull(appliedRuleSet);
          assertToEqual(si("RuleSet.fsh", 4, 12, 7, 30), appliedRuleSet.sourceInfo);
          assertEquals(3, appliedRuleSet.rules.size());
          assertAssignmentRule(appliedRuleSet.rules.get(0), "status",
              new FshCode("final").withFile("Insert.fsh").withLocation(new int[] {2, 12, 2, 17}), false, false);
          assertAssignmentRule(appliedRuleSet.rules.get(1), "valueString", "string value", false, false);
          assertCardRule(appliedRuleSet.rules.get(2), "note", 0, "7");
        }

        @Test @DisplayName("should generate a RuleSet only once when inserted with the same parameters multiple times in the same document")
        @Disabled("TS-only: jest.spyOn(importer, 'visitDoc') counts calls to a private method")
        void generateOnce() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert MultiParamRuleSet (#preliminary, "something", 3)
              * insert MultiParamRuleSet (#preliminary, "something", 3)
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          assertEquals(1, doc.appliedRuleSets.size());
          // TS: expect(visitDocSpy).toHaveBeenCalledTimes(2) -- not portable (visitDoc is private)
          // ensure the insert rules are still there (once upon a time, a bug caused the repeated rules to be omitted)
          Profile profile = doc.profiles.get("ObservationProfile");
          assertNotNull(profile);
          assertEquals(2, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "MultiParamRuleSet",
              List.of("#preliminary", "\"something\"", "3"));
          assertInsertRule(profile.rules.get(1), "", "MultiParamRuleSet",
              List.of("#preliminary", "\"something\"", "3"));
        }

        @Test @DisplayName("should parse an insert rule with parameters that will use the same RuleSet more than once with different parameters each time")
        void sameRuleSetDifferentParams() {
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
          assertToEqual(si("RuleSet.fsh", 9, 12, 10, 43), firstEntryRules.sourceInfo);
          assertEquals(1, firstEntryRules.rules.size());
          assertInsertRule(firstEntryRules.rules.get(0), "", "RecursiveRules", List.of("5"));

          RuleSet recursiveRules = doc.appliedRuleSets.get(key("RecursiveRules", "5"));
          assertNotNull(recursiveRules);
          assertToEqual(si("RuleSet.fsh", 12, 12, 14, 41), recursiveRules.sourceInfo);
          assertEquals(2, recursiveRules.rules.size());
          assertCardRule(recursiveRules.rules.get(0), "interpretation", 0, "5");
          assertInsertRule(recursiveRules.rules.get(1), "", "EntryRules", List.of("BaseCase"));

          RuleSet secondEntryRules = doc.appliedRuleSets.get(key("EntryRules", "BaseCase"));
          assertToEqual(si("RuleSet.fsh", 9, 12, 10, 43), secondEntryRules.sourceInfo);
          assertEquals(1, secondEntryRules.rules.size());
          assertInsertRule(secondEntryRules.rules.get(0), "", "BaseCaseRules", List.of("5"));

          RuleSet baseCaseRules = doc.appliedRuleSets.get(key("BaseCaseRules", "5"));
          assertNotNull(baseCaseRules);
          assertToEqual(si("RuleSet.fsh", 16, 12, 17, 28), baseCaseRules.sourceInfo);
          assertEquals(1, baseCaseRules.rules.size());
          assertCardRule(baseCaseRules.rules.get(0), "note", 0, "5");
        }

        @Test @DisplayName("should parse an insert rule with parameters where the rule set has indented rules")
        void indentedRules() {
          String input = leftAlign("""

              Profile: ObservationProfile
              Parent: Observation
              * insert IndentedRules(#usual, [["my value"]])
              """);
          List<FSHDocument> allDocs = importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
          assertEquals(1, allDocs.size());
          FSHDocument doc = allDocs.get(0);
          Profile profile = doc.profiles.get("ObservationProfile");
          assertEquals(1, profile.rules.size());
          assertInsertRule(profile.rules.get(0), "", "IndentedRules", List.of("#usual", "\"my value\""));
          RuleSet appliedTopLevelRules = doc.appliedRuleSets.get(key("IndentedRules", "#usual", "\"my value\""));
          assertNotNull(appliedTopLevelRules);
          assertEquals(2, appliedTopLevelRules.rules.size());
          assertAssignmentRule(appliedTopLevelRules.rules.get(0), "identifier.use",
              new FshCode("usual").withFile("Insert.fsh").withLocation(new int[] {3, 11, 3, 16}), false, false);
          assertAssignmentRule(appliedTopLevelRules.rules.get(1), "identifier.value", "my value", false, false);
        }

        @Test @DisplayName("should log an error and not add a rule when an insert rule has the wrong number of parameters")
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

        @Test @DisplayName("should log an error and not add a rule when an insert rule with parameters refers to an undefined parameterized RuleSet")
        void undefinedParamRuleSet() {
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

        @Test @DisplayName("should log an error when an insert rule with parameters results in a parser error in the generated RuleSet")
        void generatedParserError() {
          String input = leftAlign("""

              Profile: MyObservation
              Parent: Observation
              * insert CardRuleSet(path with spaces, 1, *)
              """);
          importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));

          assertEquals(1, FshLogger.logger.stats.numError);
          assertMatchS("Error parsing insert rule with parameterized RuleSet CardRuleSet",
              loggerSpy.getLastMessage("error"));
          assertMatchS("File: Insert\\.fsh.*Line: 4", loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("should log one error when nested insert rules with parameters result in multiple parser errors in the generated RuleSets")
        void nestedParserErrors() {
          String input = leftAlign("""

              Profile: MyObservation
              Parent: Observation
              * note 0..1
              * insert FirstRiskyRuleSet("Observation.id")
              """);
          importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));
          assertEquals(1, FshLogger.logger.stats.numError);
          assertMatchS("Error parsing insert rule with parameterized RuleSet FirstRiskyRuleSet",
              loggerSpy.getLastMessage("error"));
          assertMatchS("File: Insert\\.fsh.*Line: 5", loggerSpy.getLastMessage("error"));
        }

        @Test @DisplayName("should not log an error when an insert rule with parameters results in rules that are syntactically correct but semantically invalid")
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
          assertToEqual(si("RuleSet.fsh", 19, 12, 21, 30), appliedRuleSet.sourceInfo);
          // This rule is nonsense, of course, but figuring that out is the job of the exporter, not the importer.
          assertCardRule(appliedRuleSet.rules.get(0), "nonExistentPath", 7, "4");
          assertCardRule(appliedRuleSet.rules.get(1), "note", 7, "4");
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        // Skipping the following rule because SUSHI no longer has any warnings associated w/ parsing rules.
        // All deprecated syntaxes are now errors (not warnings).  We can re-enable if/when the importer
        // produces warnings on rules.
        @Test @DisplayName("should log one warning when an insert rule with parameters results in warnings")
        @Disabled("it.skip in TS")
        void oneWarning() {
          String input = leftAlign("""

              Profile: MyObservation
              Parent: Observation
              * insert WarningRuleSet(Device)
              """);
          importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));

          assertEquals(1, FshLogger.logger.stats.numWarn);
          assertMatchS("Warnings parsing insert rule with parameterized RuleSet WarningRuleSet",
              loggerSpy.getLastMessage("warn"));
          assertMatchS("File: Insert\\.fsh.*Line: 4", loggerSpy.getLastMessage("warn"));
        }

        @Test @DisplayName("should log one error when an insert rule with parameters results in non-parser errors")
        void nonParserErrors() {
          String input = leftAlign("""

              Profile: MyObservation
              Parent: Observation
              * insert CardRuleSet(nonExistentPath, , )
              """);
          importer.importFsh(List.of(new RawFSH(input, "Insert.fsh")));

          assertEquals(1, FshLogger.logger.stats.numError);
          assertMatchS("Errors parsing insert rule with parameterized RuleSet CardRuleSet",
              loggerSpy.getLastMessage("error"));
          assertMatchS("File: Insert\\.fsh.*Line: 4", loggerSpy.getLastMessage("error"));
        }
      }
    }
  }
}
