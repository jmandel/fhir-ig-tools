package org.hl7.fhir.sushi.importer;

import static org.hl7.fhir.sushi.testhelpers.Asserts.*;
import static org.hl7.fhir.sushi.testhelpers.ImportSingleText.importSingleText;
import static org.hl7.fhir.sushi.testhelpers.LeftAlign.leftAlign;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/import/FSHImporter.ValueSet.test.ts (SUSHI 3.21.1)
class FSHImporterValueSetTest {
  @Nested
  @DisplayName("FSHImporter")
  class FSHImporterTests {
    @AfterEach void aftereach() {
      loggerSpy.reset();
    }

    @Nested
    @DisplayName("ValueSet")
    class ValueSetTests {
      @Nested
      @DisplayName("#vsMetadata")
      class VsMetadataTests {
        @Test
        @DisplayName("FSHImporter ValueSet #vsMetadata should parse a value set with additional metadata")
        void parseValueSetAdditionalMetadata() {
          var input = leftAlign("\n"
                                     + "        ValueSet: SimpleVS\n"
                                     + "        Id: SimpleVS_ID\n"
                                     + "        Title: \"Simple Value Set\"\n"
                                     + "        Description: \"A simple value set for testing metadata\"\n"
                                     + "        ");
          var result = importSingleText(input, "Simple.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("SimpleVS");
          assertEquals("SimpleVS", valueSet.name);
          assertEquals("SimpleVS_ID", valueSet.getId());
          assertEquals("Simple Value Set", valueSet.title);
          assertEquals("A simple value set for testing metadata", valueSet.description);
          assertToEqual(new TextLocation(2, 1, 5, 54), valueSet.sourceInfo.location);
          assertEquals("Simple.fsh", valueSet.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter ValueSet #vsMetadata should parse a value set with numeric name and id")
        void parseValueSetNumericNameId() {
          var input = leftAlign("\n"
                                     + "        ValueSet: 123\n"
                                     + "        Id: 456\n"
                                     + "        ");
          var result = importSingleText(input, "Simple.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("123");
          assertEquals("123", valueSet.name);
          assertEquals("456", valueSet.getId());
        }

        @Test
        @DisplayName("FSHImporter ValueSet #vsMetadata should only apply each metadata attribute the first time it is declared")
        void onlyApplyEachMetadataAttributeFirst() {
          var input = leftAlign("\n"
                                     + "        ValueSet: SimpleVS\n"
                                     + "        Id: SimpleVS_ID\n"
                                     + "        Title: \"Simple Value Set\"\n"
                                     + "        Description: \"A simple value set for testing metadata\"\n"
                                     + "        Id: DuplicateVS_ID\n"
                                     + "        Title: \"Duplicate Value Set\"\n"
                                     + "        Description: \"A duplicate value set for testing metadata\"\n"
                                     + "        ");
          var result = importSingleText(input, "Dupe.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("SimpleVS");
          assertEquals("SimpleVS", valueSet.name);
          assertEquals("SimpleVS_ID", valueSet.getId());
          assertEquals("Simple Value Set", valueSet.title);
          assertEquals("A simple value set for testing metadata", valueSet.description);
          assertToEqual(new TextLocation(2, 1, 8, 57), valueSet.sourceInfo.location);
          assertEquals("Dupe.fsh", valueSet.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter ValueSet #vsMetadata should log an error when encountering a duplicate metadata attribute")
        void logErrorEncounteringDuplicateMetadataAttribute() {
          var input = leftAlign("\n"
                                     + "        ValueSet: SimpleVS\n"
                                     + "        Id: SimpleVS_ID\n"
                                     + "        Title: \"Simple Value Set\"\n"
                                     + "        Description: \"A simple value set for testing metadata\"\n"
                                     + "        Id: DuplicateVS_ID\n"
                                     + "        Title: \"Duplicate Value Set\"\n"
                                     + "        Description: \"A duplicate value set for testing metadata\"\n"
                                     + "        ");
          importSingleText(input, "Dupe.fsh");
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 6\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-3, "error")));
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 7\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(-2, "error")));
          assertTrue(matches(Pattern.compile("File: Dupe\\.fsh.*Line: 8\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #vsMetadata should log an error and skip the value set when encountering a value set with a name used by another value set")
        void logErrorSkipValueSetEncountering() {
          var input = leftAlign("\n"
                                     + "        ValueSet: SimpleVS\n"
                                     + "        Title: \"First Value Set\"\n"
                                     + "\n"
                                     + "        ValueSet: SimpleVS\n"
                                     + "        Title: \"Second Value Set\"\n"
                                     + "        ");
          var result = importSingleText(input, "SameName.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("SimpleVS");
          assertEquals("First Value Set", valueSet.title);
          assertTrue(matches(Pattern.compile("ValueSet named SimpleVS already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: SameName\\.fsh.*Line: 5 - 6\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #vsMetadata should log an error and skip the value set when encountering an value set with a name used by another value set in another file")
        void logErrorSkipValueSetEncountering2() {
          var input1 = "\n"
                            + "          ValueSet: SimpleVS\n"
                            + "          Title: \"First Value Set\"\n"
                            + "        ";
          var input2 = "\n"
                            + "          ValueSet: SimpleVS\n"
                            + "          Title: \"Second Value Set\"\n"
                            + "        ";
          var result = ImportText.importText(List.of(new RawFSH(input1, "File1.fsh"),
            new RawFSH(input2, "File2.fsh")));
          assertEquals(1, result.stream().mapToInt(d2 -> d2.valueSets.size()).sum());
          var v = result.get(0).valueSets.get("SimpleVS");
          assertEquals("First Value Set", v.title);
          assertTrue(matches(Pattern.compile("ValueSet named SimpleVS already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: File2\\.fsh.*Line: 2 - 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

      }

      @Nested
      @DisplayName("#ValueSetConceptComponent")
      class ValueSetConceptComponentTests {
        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetConceptComponent should parse a value set with a concept specified as SYSTEM#code")
        void parseValueSetConceptSpecifiedAs() {
          var input = leftAlign("\n"
                                     + "        ValueSet: SimpleVS\n"
                                     + "        * ZOO#bear\n"
                                     + "        ");
          var result = importSingleText(input, "Simple.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("SimpleVS");
          assertEquals("SimpleVS", valueSet.name);
          assertEquals("SimpleVS", valueSet.getId());
          assertNull(valueSet.description);
          assertEquals(1, valueSet.rules.size());
          assertValueSetConceptComponent(valueSet.rules.get(0), "ZOO", null, List.of(new FshCode("bear", "ZOO").withLocation(new int[] {3, 3, 3, 10}).withFile("Simple.fsh")));
          assertToEqual(new TextLocation(2, 1, 3, 10), valueSet.sourceInfo.location);
          assertEquals("Simple.fsh", valueSet.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetConceptComponent should ignore optional include for code components")
        void ignoreOptionalIncludeForCodeComponents() {
          var input = leftAlign("\n"
                                     + "        ValueSet: SimpleVS\n"
                                     + "        * include ZOO#bear\n"
                                     + "        ");
          var result = importSingleText(input, "Simple.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("SimpleVS");
          assertEquals("SimpleVS", valueSet.name);
          assertEquals("SimpleVS", valueSet.getId());
          assertNull(valueSet.description);
          assertEquals(1, valueSet.rules.size());
          assertValueSetConceptComponent(valueSet.rules.get(0), "ZOO", null, List.of(new FshCode("bear", "ZOO").withLocation(new int[] {3, 11, 3, 18}).withFile("Simple.fsh")));
          assertToEqual(new TextLocation(2, 1, 3, 18), valueSet.sourceInfo.location);
          assertEquals("Simple.fsh", valueSet.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetConceptComponent should parse a value set with a concept specified as #code from SYSTEM")
        void parseValueSetConceptSpecifiedAs2() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * #hippo \"Hippopotamus\" from system ZOO\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetConceptComponent(valueSet.rules.get(0), "ZOO", null, List.of(new FshCode("hippo", "ZOO", "Hippopotamus")
              .withLocation(new int[] {3, 3, 3, 23})
              .withFile("Zoo.fsh")));
          assertToEqual(new TextLocation(2, 1, 3, 39), valueSet.sourceInfo.location);
          assertEquals("Zoo.fsh", valueSet.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetConceptComponent should parse a value set with a concept specified from an aliased system")
        void parseValueSetConceptSpecifiedFrom() {
          var input = leftAlign("\n"
                                     + "        Alias: AQ = http://aquarium.org\n"
                                     + "\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * #octopus \"Octopus\" from system AQ\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetConceptComponent(valueSet.rules.get(0), "http://aquarium.org", null, List.of(new FshCode("octopus", "http://aquarium.org", "Octopus")
              .withLocation(new int[] {5, 3, 5, 20})
              .withFile("Zoo.fsh")));
          assertToEqual(new TextLocation(4, 1, 5, 35), valueSet.sourceInfo.location);
          assertEquals("Zoo.fsh", valueSet.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetConceptComponent should parse a value set with a concept specified as #code from numeric named SYSTEM")
        void parseValueSetConceptSpecifiedAs3() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * #hippo \"Hippopotamus\" from system 123\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetConceptComponent(valueSet.rules.get(0), "123", null, List.of(new FshCode("hippo", "123", "Hippopotamus")
              .withLocation(new int[] {3, 3, 3, 23})
              .withFile("Zoo.fsh")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetConceptComponent should merge concept rules when possible")
        void mergeConceptRulesPossible() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * ZOO#hippo \"Hippopotamus\"\n"
                                     + "        * #crocodile \"Crocodile\" from system ZOO\n"
                                     + "        * ZOO#alligator \"Alligator\" from valueset ReptileVS\n"
                                     + "        * CRYPTID#jackalope \"Jackalope\"\n"
                                     + "        * exclude ZOO#lion \"Lion\"\n"
                                     + "        * exclude #cobra \"Cobra\" from system ZOO\n"
                                     + "        * exclude ZOO#monitor \"Monitor lizard\" from valueset ReptileVS\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(5, valueSet.rules.size());
          assertValueSetConceptComponent(valueSet.rules.get(0), "ZOO", null, List.of(new FshCode("hippo", "ZOO", "Hippopotamus")
              .withLocation(new int[] {3, 3, 3, 26})
              .withFile("Zoo.fsh"),
            new FshCode("crocodile", "ZOO", "Crocodile")
              .withLocation(new int[] {4, 3, 4, 24})
              .withFile("Zoo.fsh")));
          assertValueSetConceptComponent(
            valueSet.rules.get(1),
            "ZOO",
            List.of("ReptileVS"),
            List.of(new FshCode("alligator", "ZOO", "Alligator")
                .withLocation(new int[] {5, 3, 5, 27})
                .withFile("Zoo.fsh"))
          );
          assertValueSetConceptComponent(valueSet.rules.get(2), "CRYPTID", null, List.of(new FshCode("jackalope", "CRYPTID", "Jackalope")
              .withLocation(new int[] {6, 3, 6, 31})
              .withFile("Zoo.fsh")));
          assertValueSetConceptComponent(
            valueSet.rules.get(3),
            "ZOO",
            null,
            List.of(new FshCode("lion", "ZOO", "Lion").withLocation(new int[] {7, 11, 7, 25}).withFile("Zoo.fsh"),
              new FshCode("cobra", "ZOO", "Cobra").withLocation(new int[] {8, 11, 8, 24}).withFile("Zoo.fsh")),
            false
          );
          assertValueSetConceptComponent(
            valueSet.rules.get(4),
            "ZOO",
            List.of("ReptileVS"),
            List.of(new FshCode("monitor", "ZOO", "Monitor lizard")
                .withLocation(new int[] {9, 11, 9, 38})
                .withFile("Zoo.fsh")),
            false
          );
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetConceptComponent should log an error when a concept component with one concept does not have a system")
        void logErrorConceptComponentOneConcept() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * #hippo\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetConceptComponent(valueSet.rules.get(0), null, null, List.of());
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetConceptComponent should log an error when a concept component has a system specified more than once")
        void logErrorConceptComponentHasSystem() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * ZOO#hippo from system ZOO\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetConceptComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetConceptComponent should log an error when both include and exclude are used on the same line")
        void logErrorBothIncludeExcludeAre() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * include exclude ZOO#bear\n"
                                     + "        ");
          importSingleText(input, "Zoo.fsh");
          assertTrue(matches(Pattern.compile("no viable alternative", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

      }

      @Nested
      @DisplayName("#ValueSetFilterComponent")
      class ValueSetFilterComponentTests {
        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that includes all codes from a system")
        void parseValueSetIncludesAllCodes() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from system ZOO\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertToEqual(new TextLocation(2, 1, 3, 23), valueSet.sourceInfo.location);
          assertEquals("Zoo.fsh", valueSet.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should ignore optional include for filter components")
        void ignoreOptionalIncludeForFilterComponents() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * include codes from system ZOO\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertToEqual(new TextLocation(2, 1, 3, 31), valueSet.sourceInfo.location);
          assertEquals("Zoo.fsh", valueSet.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that includes all codes from other value sets")
        void parseValueSetIncludesAllCodes2() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from valueset FirstZooVS\n"
                                     + "        * codes from valueset SecondZooVS and ThirdZooVS\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(2, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), null, List.of("FirstZooVS"), List.of());
          assertValueSetFilterComponent(
            valueSet.rules.get(1),
            null,
            List.of("SecondZooVS", "ThirdZooVS"),
            List.of()
          );
          assertToEqual(new TextLocation(2, 1, 4, 48), valueSet.sourceInfo.location);
          assertEquals("Zoo.fsh", valueSet.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that includes all codes from an aliased value set")
        void parseValueSetIncludesAllCodes3() {
          var input = leftAlign("\n"
                                     + "        Alias: Z1 = FirstZooVS\n"
                                     + "\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from valueset Z1\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), null, List.of("FirstZooVS"), List.of());
          assertToEqual(new TextLocation(4, 1, 5, 24), valueSet.sourceInfo.location);
          assertEquals("Zoo.fsh", valueSet.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that includes all codes from numeric named value sets")
        void parseValueSetIncludesAllCodes4() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from valueset 123\n"
                                     + "        * codes from valueset 456 and 789\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(2, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), null, List.of("123"), List.of());
          assertValueSetFilterComponent(valueSet.rules.get(1), null, List.of("456", "789"), List.of());
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that includes all codes from a system and other value sets")
        void parseValueSetIncludesAllCodes5() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from system ZOO and valueset NorthZooVS and SouthZooVS\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", List.of("NorthZooVS", "SouthZooVS"), List.of());
          assertToEqual(new TextLocation(2, 1, 3, 62), valueSet.sourceInfo.location);
          assertEquals("Zoo.fsh", valueSet.sourceInfo.file);
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should log an error when valuesets are listed with commas")
        void logErrorValuesetsAreListedCommas() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from valueset FirstZooVS, SecondZooVS\n"
                                     + "        ");
          var result = importSingleText(input, "Deprecated.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertNotNull(valueSet);
          assertTrue(matches(Pattern.compile("Using ',' to list items is no longer supported", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: Deprecated\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator = with string value")
        void parseValueSetUsesFilterOperator() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from system ZOO where version = \"2.0\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("version", VsOperator.EQUALS, "2.0")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator = with code value")
        void parseValueSetUsesFilterOperator2() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from system ZOO where version = #two\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("version", VsOperator.EQUALS, new FshCode("two").withLocation(new int[] {3, 41, 3, 44}).withFile("Zoo.fsh"))));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator with numeric property name")
        void parseValueSetUsesFilterOperator3() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from system ZOO where 123 = \"2.0\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("123", VsOperator.EQUALS, "2.0")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should log an error when the = filter has a non-string and non-code value")
        void logErrorFilterHasNonString() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from system ZOO where version = /[1-9].*/\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertTrue(matches(Pattern.compile("\"=\".*code or string"), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator is-a with a code value")
        void parseValueSetUsesFilterOperator4() {
          var input = leftAlign("\n"
                                     + "        ValueSet: AllUrsinesVS\n"
                                     + "        * codes from system ZOO where concept is-a #bear \"Bear\"\n"
                                     + "        ");
          var result = importSingleText(input, "Ursines.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("AllUrsinesVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("concept", VsOperator.IS_A, new FshCode("bear", null, "Bear")
                .withLocation(new int[] {3, 44, 3, 55})
                .withFile("Ursines.fsh"))));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator is-a with a string value")
        void parseValueSetUsesFilterOperator5() {
          var input = leftAlign("\n"
                                     + "        ValueSet: AllUrsinesVS\n"
                                     + "        * codes from system ZOO where concept is-a \"Bear\"\n"
                                     + "        ");
          var result = importSingleText(input, "Ursines.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("AllUrsinesVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("concept", VsOperator.IS_A, "Bear")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should log an error when the is-a filter has a non-code and non-string value")
        void logErrorFilterHasNonCode() {
          var input = leftAlign("\n"
                                     + "        ValueSet: AllUrsinesVS\n"
                                     + "        * codes from system ZOO where concept is-a /([Bb]ear)/\n"
                                     + "        ");
          var result = importSingleText(input, "Ursines.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("AllUrsinesVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertTrue(matches(Pattern.compile("\"is-a\".*code or string"), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: Ursines\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator descendent-of with a code value")
        void parseValueSetUsesFilterOperator6() {
          var input = leftAlign("\n"
                                     + "        ValueSet: AllFelinesVS\n"
                                     + "        * codes from system ZOO where concept descendent-of ZOO#cat\n"
                                     + "        ");
          var result = importSingleText(input, "Felines.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("AllFelinesVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("concept", VsOperator.DESCENDENT_OF, new FshCode("cat", "ZOO", null)
                .withLocation(new int[] {3, 53, 3, 59})
                .withFile("Felines.fsh"))));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator descendant-of, which is the same as descendent-of, but spelled correctly")
        void parseValueSetUsesFilterOperator7() {
          var input = leftAlign("\n"
                                     + "        ValueSet: AllFelinesVS\n"
                                     + "        * codes from system ZOO where concept descendant-of ZOO#cat\n"
                                     + "        ");
          var result = importSingleText(input, "Felines.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("AllFelinesVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("concept", VsOperator.DESCENDENT_OF, new FshCode("cat", "ZOO", null)
                .withLocation(new int[] {3, 53, 3, 59})
                .withFile("Felines.fsh"))));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator descendent-of with a string value")
        void parseValueSetUsesFilterOperator8() {
          var input = leftAlign("\n"
                                     + "        ValueSet: AllFelinesVS\n"
                                     + "        * codes from system ZOO where concept descendent-of \"Cat\"\n"
                                     + "        ");
          var result = importSingleText(input, "Felines.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("AllFelinesVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("concept", VsOperator.DESCENDENT_OF, "Cat")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should log an error when the descendent-of filter has a non-code and non-string value")
        void logErrorDescendentFilterHasNon() {
          var input = leftAlign("\n"
                                     + "        ValueSet: AllFelinesVS\n"
                                     + "        * codes from system ZOO where concept descendent-of /([Cc]at)/\n"
                                     + "        ");
          var result = importSingleText(input, "Felines.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("AllFelinesVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertTrue(matches(Pattern.compile("\"descendent-of\".*code or string"), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: Felines\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator is-not-a with a code value")
        void parseValueSetUsesFilterOperator9() {
          var input = leftAlign("\n"
                                     + "        ValueSet: NonCanineVS\n"
                                     + "        * codes from system ZOO where concept is-not-a #dog\n"
                                     + "        ");
          var result = importSingleText(input, "NonCanine.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("NonCanineVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("concept", VsOperator.IS_NOT_A, new FshCode("dog", null, null)
                .withLocation(new int[] {3, 48, 3, 51})
                .withFile("NonCanine.fsh"))));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator is-not-a with a string value")
        void parseValueSetUsesFilterOperator10() {
          var input = leftAlign("\n"
                                     + "        ValueSet: NonCanineVS\n"
                                     + "        * codes from system ZOO where concept is-not-a \"dog\"\n"
                                     + "        ");
          var result = importSingleText(input, "NonCanine.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("NonCanineVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("concept", VsOperator.IS_NOT_A, "dog")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should log an error when the is-not-a filter has a non-code and non-string value")
        void logErrorNotFilterHasNon() {
          var input = leftAlign("\n"
                                     + "        ValueSet: NonCanineVS\n"
                                     + "        * codes from system ZOO where concept is-not-a /([Dd]og)/\n"
                                     + "        ");
          var result = importSingleText(input, "NonCanine.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("NonCanineVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertTrue(matches(Pattern.compile("\"is-not-a\".*code or string"), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: NonCanine\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator regex with a regex value")
        void parseValueSetUsesFilterOperator11() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ProbablyDogsVS\n"
                                     + "        * codes from system ZOO where display regex /([Dd]og)|([Cc]anine)/\n"
                                     + "        ");
          var result = importSingleText(input, "MostlyDogs.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ProbablyDogsVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("display", VsOperator.REGEX, Pattern.compile("([Dd]og)|([Cc]anine)"))));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator regex with a string value")
        void parseValueSetUsesFilterOperator12() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ProbablyDogsVS\n"
                                     + "        * codes from system ZOO where display regex \"Dog|Canine\"\n"
                                     + "        ");
          var result = importSingleText(input, "MostlyDogs.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ProbablyDogsVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("display", VsOperator.REGEX, "Dog|Canine")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should log an error when the regex filter has a non-regex and non-string value")
        void logErrorRegexFilterHasNon() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ProbablyDogsVS\n"
                                     + "        * codes from system ZOO where display regex #Dog|Canine\n"
                                     + "        ");
          var result = importSingleText(input, "MostlyDogs.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ProbablyDogsVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertTrue(matches(Pattern.compile("\"regex\".*regex or string"), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: MostlyDogs\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator in with a string value")
        void parseValueSetUsesFilterOperator13() {
          var input = leftAlign("\n"
                                     + "        ValueSet: CatAndDogVS\n"
                                     + "        * codes from system ZOO where concept in \"#cat, #dog\"\n"
                                     + "        ");
          var result = importSingleText(input, "CatDog.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("CatAndDogVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("concept", VsOperator.IN, "#cat, #dog")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that use filter operator in with a code value")
        void parseValueSetUseFilterOperator() {
          var input = leftAlign("\n"
                                     + "        ValueSet: CatAndDogVS\n"
                                     + "        * codes from system ZOO where concept in ZOO#cat\n"
                                     + "        ");
          var result = importSingleText(input, "CatDog.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("CatAndDogVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("concept", VsOperator.IN, new FshCode("cat", "ZOO").withLocation(new int[] {3, 42, 3, 48}).withFile("CatDog.fsh"))));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should log an error when the in filter has a non-string or non-code value")
        void logErrorFilterHasNonString2() {
          var input = leftAlign("\n"
                                     + "        ValueSet: CatAndDogVS\n"
                                     + "        * codes from system ZOO where concept in true\n"
                                     + "        ");
          var result = importSingleText(input, "CatDog.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("CatAndDogVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertTrue(matches(Pattern.compile("\"in\".*code or string"), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: CatDog\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator not-in with a string value")
        void parseValueSetUsesFilterOperator14() {
          var input = leftAlign("\n"
                                     + "        ValueSet: NoGooseVS\n"
                                     + "        * codes from system ZOO where concept not-in \"#goose\"\n"
                                     + "        ");
          var result = importSingleText(input, "NoGoose.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("NoGooseVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("concept", VsOperator.NOT_IN, "#goose")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator not-in with a code value")
        void parseValueSetUsesFilterOperator15() {
          var input = leftAlign("\n"
                                     + "        ValueSet: NoGooseVS\n"
                                     + "        * codes from system ZOO where concept not-in #goose\n"
                                     + "        ");
          var result = importSingleText(input, "NoGoose.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("NoGooseVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("concept", VsOperator.NOT_IN, new FshCode("goose").withLocation(new int[] {3, 46, 3, 51}).withFile("NoGoose.fsh"))));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should log an error when the not-in filter has a non-string and non-code value")
        void logErrorNotFilterHasNon2() {
          var input = leftAlign("\n"
                                     + "        ValueSet: NoGooseVS\n"
                                     + "        * codes from system ZOO where concept not-in /duck|goose/\n"
                                     + "        ");
          var result = importSingleText(input, "NoGoose.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("NoGooseVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertTrue(matches(Pattern.compile("\"not-in\".*code or string"), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: NoGoose\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator generalizes with a code value")
        void parseValueSetUsesFilterOperator16() {
          var input = leftAlign("\n"
                                     + "        ValueSet: MustelidVS\n"
                                     + "        * codes from system ZOO where concept generalizes #mustela-nivalis \"least weasel\"\n"
                                     + "        ");
          var result = importSingleText(input, "Mustelids.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("MustelidVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("concept", VsOperator.GENERALIZES, new FshCode("mustela-nivalis", null, "least weasel")
                .withLocation(new int[] {3, 51, 3, 81})
                .withFile("Mustelids.fsh"))));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator generalizes with a string value")
        void parseValueSetUsesFilterOperator17() {
          var input = leftAlign("\n"
                                     + "        ValueSet: MustelidVS\n"
                                     + "        * codes from system ZOO where concept generalizes \"least weasel\"\n"
                                     + "        ");
          var result = importSingleText(input, "Mustelids.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("MustelidVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("concept", VsOperator.GENERALIZES, "least weasel")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should log an error when the generalizes filter has a non-code value")
        void logErrorGeneralizesFilterHasNon() {
          var input = leftAlign("\n"
                                     + "        ValueSet: MustelidVS\n"
                                     + "        * codes from system ZOO where concept generalizes /least weasel/\n"
                                     + "        ");
          var result = importSingleText(input, "Mustelids.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("MustelidVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertTrue(matches(Pattern.compile("\"generalizes\".*code or string"), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: Mustelids\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator exists with a boolean value")
        void parseValueSetUsesFilterOperator18() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from system ZOO where display exists true\n"
                                     + "        * codes from system ZOO where version exists\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(2, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("display", VsOperator.EXISTS, true)));
          assertValueSetFilterComponent(valueSet.rules.get(1), "ZOO", null, List.of(new ValueSetFilter("version", VsOperator.EXISTS, true)));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that chains filter operators")
        void parseValueSetChainsFilterOperators() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from system ZOO where\n"
                                     + "          display exists true and\n"
                                     + "          variant exists false and\n"
                                     + "          extension exists true\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("display", VsOperator.EXISTS, true),
            new ValueSetFilter("variant", VsOperator.EXISTS, false),
            new ValueSetFilter("extension", VsOperator.EXISTS, true)));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses filter operator exists with a string value that looks like a boolean")
        void parseValueSetUsesFilterOperator19() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from system ZOO where display exists \"true\"\n"
                                     + "        * codes from system ZOO where version exists \"false\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(2, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("display", VsOperator.EXISTS, "true")));
          assertValueSetFilterComponent(valueSet.rules.get(1), "ZOO", null, List.of(new ValueSetFilter("version", VsOperator.EXISTS, "false")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should log an error when the exists filter has a string value that does not look like a boolean")
        void logErrorExistsFilterHasString() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from system ZOO where display exists \"display\"\n"
                                     + "        * codes from system ZOO where version exists \"True\"\n"
                                     + "        * codes from system ZOO where variant exists \"FALSE\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          // All three of these string values cause errors
          assertEquals(3, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertEquals(3, loggerSpy.getAllMessages("error").size());
          assertTrue(matches(Pattern.compile("\"exists\".*boolean value or a string value of \"true\" or \"false\""), loggerSpy.getMessageAtIndex(0, "error")));
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(0, "error")));
          assertTrue(matches(Pattern.compile("\"exists\".*boolean value or a string value of \"true\" or \"false\""), loggerSpy.getMessageAtIndex(1, "error")));
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 4\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(1, "error")));
          assertTrue(matches(Pattern.compile("\"exists\".*boolean value or a string value of \"true\" or \"false\""), loggerSpy.getMessageAtIndex(2, "error")));
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 5\\D*", Pattern.DOTALL), loggerSpy.getMessageAtIndex(2, "error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should log an error when the exists filter has a non-boolean and non-string value")
        void logErrorExistsFilterHasNon() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from system ZOO where display exists /true/\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertTrue(matches(Pattern.compile("\"exists\".*boolean value or a string value of \"true\" or \"false\""), loggerSpy.getLastMessage("error")));
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set that uses multiple filters on a single component")
        void parseValueSetUsesMultipleFilters() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooTwoVS\n"
                                     + "        * codes from system ZOO where version regex /2\\./ and display exists\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooTwoVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of(new ValueSetFilter("version", VsOperator.REGEX, Pattern.compile("2\\.")),
            new ValueSetFilter("display", VsOperator.EXISTS, true)));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should parse a value set with an excluded component")
        void parseValueSetExcludedComponent() {
          var input = leftAlign("\n"
                                     + "        ValueSet: AvailableVS\n"
                                     + "        * codes from system ZOO\n"
                                     + "        * exclude codes from valueset UnavailableAnimalVS\n"
                                     + "        ");
          var result = importSingleText(input, "Available.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("AvailableVS");
          assertEquals(2, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertValueSetFilterComponent(
            valueSet.rules.get(1),
            null,
            List.of("UnavailableAnimalVS"),
            List.of(),
            false
          );
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should log an error when a filter has an invalid operator")
        void logErrorFilterHasInvalidOperator() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from system ZOO where display resembles \"cat\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should log an error when a filter (other than the exists filter) has no value")
        void logErrorFilterOtherThanExists() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * codes from system ZOO where display regex\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), "ZOO", null, List.of());
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetFilterComponent should log an error when a filter component has at least one filter, but no system")
        void logErrorFilterComponentHasAt() {
          var input = leftAlign("\n"
                                     + "          ValueSet: ZooVS\n"
                                     + "          * codes from valueset OtherZooVS where version = \"2.0\"\n"
                                     + "          ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetFilterComponent(valueSet.rules.get(0), null, List.of("OtherZooVS"), List.of());
          assertTrue(matches(Pattern.compile("File: Zoo\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

      }

      @Nested
      @DisplayName("#ValueSetCaretValueRule")
      class ValueSetCaretValueRuleTests {
        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetCaretValueRule should parse a value set that uses a CaretValueRule")
        void parseValueSetUsesCaretValueRule() {
          var input = leftAlign("\n"
                                     + "          ValueSet: ZooVS\n"
                                     + "          * ^publisher = \"foo\"\n"
                                     + "          ");
          var result = importSingleText(input);
          var valueSet = result.valueSets.get("ZooVS");
          assertCaretValueRule(((Rule) valueSet.rules.get(0)), "", "publisher", "foo", false);
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetCaretValueRule should parse a value set that uses CaretValueRules alongside rules")
        void parseValueSetUsesCaretValueRulesAlongside() {
          var input = leftAlign("\n"
                                     + "        ValueSet: SimpleVS\n"
                                     + "        * ZOO#bear\n"
                                     + "        * ^publisher = \"foo\"\n"
                                     + "        ");
          var result = importSingleText(input, "Simple.fsh");
          var valueSet = result.valueSets.get("SimpleVS");
          assertValueSetConceptComponent(valueSet.rules.get(0), "ZOO", null, List.of(new FshCode("bear", "ZOO").withLocation(new int[] {3, 3, 3, 10}).withFile("Simple.fsh")));
          assertCaretValueRule(((Rule) valueSet.rules.get(1)), "", "publisher", "foo", false);
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetCaretValueRule should log an error when a CaretValueRule contains a path before ^")
        void logErrorCaretValueRuleContainsPathBefore() {
          var input = leftAlign("\n"
                                     + "        ValueSet: SimpleVS\n"
                                     + "        * mypath ^publisher = \"foo\"\n"
                                     + "        ");
          var result = importSingleText(input, "Simple.fsh");
          var valueSet = result.valueSets.get("SimpleVS");
          assertEquals(0, valueSet.rules.size());
          assertTrue(matches(Pattern.compile("File: Simple\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

      }

      @Nested
      @DisplayName("#ValueSetCodeCaretRule")
      class ValueSetCodeCaretRuleTests {
        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetCodeCaretRule should parse a value set with a code caret rule")
        void parseValueSetCodeCaretRule() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * ZOO#hippo \"Hippopotamus\"\n"
                                     + "        * ZOO#hippo ^designation.value = \"hipopótamo\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(2, valueSet.rules.size());
          assertValueSetConceptComponent(valueSet.rules.get(0), "ZOO", null, List.of(new FshCode("hippo", "ZOO", "Hippopotamus")
              .withLocation(new int[] {3, 3, 3, 26})
              .withFile("Zoo.fsh")));
          assertCaretValueRule(valueSet.rules.get(1), "", "designation.value", "hipopótamo", false, List.of("ZOO#hippo"));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        @Test
        @DisplayName("FSHImporter ValueSet #ValueSetCodeCaretRule should log an error when a caret rule has more than one concept")
        void logErrorCaretRuleHasMore() {
          // ValueSet: ZooVS
          // * ZOO#hippo "Hippopotamus"
          // * ZOO#rhino "Rhinoceros"
          // * ZOO#hippo ZOO#rhino ^designation.value = "hipopótamo"
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * ZOO#hippo \"Hippopotamus\"\n"
                                     + "        * ZOO#rhino \"Rhinoceros\"\n"
                                     + "        * ZOO#hippo ZOO#rhino ^designation.value = \"hipopótamo\"\n"
                                     + "        ");
          var result = importSingleText(input, "Zoo.fsh");
          assertEquals(1, result.valueSets.size());
          var valueSet = result.valueSets.get("ZooVS");
          assertEquals(1, valueSet.rules.size());
          assertValueSetConceptComponent(valueSet.rules.get(0), "ZOO", null, List.of(new FshCode("hippo", "ZOO", "Hippopotamus")
              .withLocation(new int[] {3, 3, 3, 26})
              .withFile("Zoo.fsh"),
            new FshCode("rhino", "ZOO", "Rhinoceros").withLocation(new int[] {4, 3, 4, 24}).withFile("Zoo.fsh")));
          assertEquals(1, loggerSpy.getAllMessages("error").size());
          assertTrue(matches(Pattern.compile("Only one concept may be listed before a caret rule on a ValueSet\\..*File: Zoo\\.fsh.*Line: 5\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

      }

      @Nested
      @DisplayName("#insertRule")
      class InsertRuleTests {
        @Test
        @DisplayName("FSHImporter ValueSet #insertRule should parse an insert rule with a single RuleSet")
        void parseInsertRuleSingleRuleSet() {
          var input = leftAlign("\n"
                                     + "        ValueSet: MyVS\n"
                                     + "        * insert MyRuleSet\n"
                                     + "        ");
          var result = importSingleText(input, "Insert.fsh");
          var vs = result.valueSets.get("MyVS");
          assertEquals(1, vs.rules.size());
          assertInsertRule(vs.rules.get(0), "", "MyRuleSet");
        }

        @Test
        @DisplayName("FSHImporter ValueSet #insertRule should parse an insert rule at a concept")
        void parseInsertRuleAtConcept() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * ZOO#hippo\n"
                                     + "        * ZOO#hippo insert DesignationRules\n"
                                     + "        ");
          var result = importSingleText(input, "Insert.fsh");
          var vs = result.valueSets.get("ZooVS");
          assertEquals(2, vs.rules.size());
          assertValueSetConceptComponent(vs.rules.get(0), "ZOO", null, List.of(new FshCode("hippo", "ZOO").withFile("Insert.fsh").withLocation(new int[] {3, 3, 3, 11})));
          assertInsertRule(vs.rules.get(1), "", "DesignationRules", List.of(), List.of("ZOO#hippo"));
          assertEquals(0, loggerSpy.getAllMessages("error").size());
        }

        @Test
        @DisplayName("FSHImporter ValueSet #insertRule should log an error when an insert rule has more than one concept")
        void logErrorInsertRuleHasMore() {
          var input = leftAlign("\n"
                                     + "        ValueSet: ZooVS\n"
                                     + "        * ZOO#hippo ZOO#big-hippo insert DesignationRules\n"
                                     + "        ");
          var result = importSingleText(input, "Insert.fsh");
          var vs = result.valueSets.get("ZooVS");
          assertEquals(0, vs.rules.size());
          assertEquals(1, loggerSpy.getAllMessages("error").size());
          assertTrue(matches(Pattern.compile("Only one concept may be listed before an insert rule on a ValueSet\\..*File: Insert\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        }

      }

    }

  }


  static boolean matches(Pattern p, String s) {
    return s != null && p.matcher(s).find();
  }

  static ValueSetComponentFrom from(String system, List<String> valueSets) {
    ValueSetComponentFrom f = new ValueSetComponentFrom();
    f.system = system;
    f.valueSets = valueSets;
    return f;
  }
}
