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

// port of sushi/test/import/FSHImporter.Mapping.test.ts (SUSHI 3.21.1)
class FSHImporterMappingTest {
  @Nested
  @DisplayName("FSHImporter")
  class FSHImporterTests {
    @BeforeEach void beforeall() {
      loggerSpy.reset();
    }

    @Nested
    @DisplayName("Mapping")
    class MappingTests {
      @Test
      @DisplayName("FSHImporter Mapping should parse the simplest possible Mapping")
      void parseSimplestPossibleMapping() {
        var input = "\n"
                         + "        Mapping: MyMapping\n"
                         + "        ";
        var result = importSingleText(input, "Mapping.fsh");
        assertEquals(1, result.mappings.size());
        var mapping = result.mappings.get("MyMapping");
        assertEquals("MyMapping", mapping.name);
        assertEquals("MyMapping", mapping.id);
        assertToEqual(new TextLocation(2, 9, 2, 26), mapping.sourceInfo.location);
        assertEquals("Mapping.fsh", mapping.sourceInfo.file);
      }

      @Test
      @DisplayName("FSHImporter Mapping should parse a Mapping with additional metadata properties")
      void parseMappingAdditionalMetadataProperties() {
        var input = "\n"
                         + "        Mapping: MyMapping\n"
                         + "        Id: my-map\n"
                         + "        Source: Patient\n"
                         + "        Target: \"http://some.com/mappedTo\"\n"
                         + "        Description: \"This is a description\"\n"
                         + "        Title: \"This is a title\"\n"
                         + "        ";
        var result = importSingleText(input, "Mapping.fsh");
        assertEquals(1, result.mappings.size());
        var mapping = result.mappings.get("MyMapping");
        assertEquals("MyMapping", mapping.name);
        assertEquals("my-map", mapping.id);
        assertEquals("Patient", mapping.source);
        assertEquals("http://some.com/mappedTo", mapping.target);
        assertEquals("This is a description", mapping.description);
        assertEquals("This is a title", mapping.title);
        assertToEqual(new TextLocation(2, 9, 7, 32), mapping.sourceInfo.location);
        assertEquals("Mapping.fsh", mapping.sourceInfo.file);
      }

      @Test
      @DisplayName("FSHImporter Mapping should parse numeric Mapping name, id, and source")
      void parseNumericMappingNameIdSource() {
        // NOT recommended, but possible
        var input = "\n"
                         + "        Mapping: 123\n"
                         + "        Id: 456\n"
                         + "        Source: 789\n"
                         + "        ";
        var result = importSingleText(input, "Mapping.fsh");
        assertEquals(1, result.mappings.size());
        var mapping = result.mappings.get("123");
        assertEquals("123", mapping.name);
        assertEquals("456", mapping.id);
        assertEquals("789", mapping.source);
      }

      @Test
      @DisplayName("FSHImporter Mapping should only apply each metadata attribute the first time it is declared")
      void onlyApplyEachMetadataAttributeFirst() {
        var input = "\n"
                         + "        Mapping: MyMapping\n"
                         + "        Id: my-map\n"
                         + "        Source: Patient\n"
                         + "        Target: \"http://some.com/mappedTo\"\n"
                         + "        Title: \"This is a title\"\n"
                         + "        Description: \"This is a description\"\n"
                         + "        Id: my-map-2\n"
                         + "        Source: Patient2\n"
                         + "        Target: \"http://some.com/mappedTo2\"\n"
                         + "        Title: \"This is a title 2\"\n"
                         + "        Description: \"This is a description 2\"\n"
                         + "        ";
        var result = importSingleText(input, "Mapping.fsh");
        assertEquals(1, result.mappings.size());
        var mapping = result.mappings.get("MyMapping");
        assertEquals("MyMapping", mapping.name);
        assertEquals("my-map", mapping.id);
        assertEquals("Patient", mapping.source);
        assertEquals("http://some.com/mappedTo", mapping.target);
        assertEquals("This is a description", mapping.description);
        assertTrue(matches(Pattern.compile("This is a description.*File: Mapping\\.fsh.*Line: 12\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter Mapping should accept and translate an alias for the Source")
      void acceptTranslateAliasForSource() {
        var input = "\n"
                         + "        Alias: OBS = http://hl7.org/fhir/StructureDefinition/Observation\n"
                         + "\n"
                         + "        Mapping: MyMapping\n"
                         + "        Source: OBS\n"
                         + "        ";
        var result = importSingleText(input, "Mapping.fsh");
        assertEquals(1, result.mappings.size());
        var mapping = result.mappings.get("MyMapping");
        assertEquals("MyMapping", mapping.name);
        assertEquals("http://hl7.org/fhir/StructureDefinition/Observation", mapping.source);
        assertToEqual(new TextLocation(4, 9, 5, 19), mapping.sourceInfo.location);
        assertEquals("Mapping.fsh", mapping.sourceInfo.file);
      }

      @Test
      @DisplayName("FSHImporter Mapping should log an error and skip the mapping when encountering a mapping with a name used by another mapping")
      void logErrorSkipMappingEncounteringMapping() {
        var input = "\n"
                         + "      Mapping: SameMapping\n"
                         + "      Title: \"First Mapping\"\n"
                         + "\n"
                         + "      Mapping: SameMapping\n"
                         + "      Title: \"Second Mapping\"\n"
                         + "      ";
        var result = importSingleText(input, "SameName.fsh");
        assertEquals(1, result.mappings.size());
        var mapping = result.mappings.get("SameMapping");
        assertEquals("First Mapping", mapping.title);
        assertTrue(matches(Pattern.compile("Mapping named SameMapping already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        assertTrue(matches(Pattern.compile("File: SameName\\.fsh.*Line: 5 - 6\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter Mapping should log an error and skip the mapping when encountering an mapping with a name used by another mapping in another file")
      void logErrorSkipMappingEncounteringMapping2() {
        var input1 = "\n"
                          + "        Mapping: SameMapping\n"
                          + "        Title: \"First Mapping\"\n"
                          + "      ";
        var input2 = "\n"
                          + "        Mapping: SameMapping\n"
                          + "        Title: \"Second Mapping\"\n"
                          + "      ";
        var result = ImportText.importText(List.of(new RawFSH(input1, "File1.fsh"), new RawFSH(input2, "File2.fsh")));
        assertEquals(1, result.stream().mapToInt(d2 -> d2.mappings.size()).sum());
        var m = result.get(0).mappings.get("SameMapping");
        assertEquals("First Mapping", m.title);
        assertTrue(matches(Pattern.compile("Mapping named SameMapping already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        assertTrue(matches(Pattern.compile("File: File2\\.fsh.*Line: 2 - 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Nested
      @DisplayName("#mappingRule")
      class MappingRuleTests {
        @Test
        @DisplayName("FSHImporter Mapping #mappingRule should parse a simple mapping rule")
        void parseSimpleMappingRule() {
          var input = "\n"
                           + "          Mapping: MyMapping\n"
                           + "          * identifier -> \"Patient.identifier\"\n"
                           + "          ";
          var result = importSingleText(input);
          var mapping = result.mappings.get("MyMapping");
          assertEquals(1, mapping.rules.size());
          assertMappingRule(
            mapping.rules.get(0),
            "identifier",
            "Patient.identifier",
            null,
            null
          );
        }

        @Test
        @DisplayName("FSHImporter Mapping #mappingRule should parse a mapping rule with no path")
        void parseMappingRuleNoPath() {
          var input = "\n"
                           + "          Mapping: MyMapping\n"
                           + "          * -> \"Patient\"\n"
                           + "          ";
          var result = importSingleText(input);
          var mapping = result.mappings.get("MyMapping");
          assertEquals(1, mapping.rules.size());
          assertMappingRule(mapping.rules.get(0), "", "Patient", null, null);
        }

        @Test
        @DisplayName("FSHImporter Mapping #mappingRule should parse a mapping rule with a comment")
        void parseMappingRuleComment() {
          var input = "\n"
                           + "          Mapping: MyMapping\n"
                           + "          * identifier -> \"Patient.identifier\" \"some comment\"\n"
                           + "          ";
          var result = importSingleText(input);
          var mapping = result.mappings.get("MyMapping");
          assertEquals(1, mapping.rules.size());
          assertMappingRule(
            mapping.rules.get(0),
            "identifier",
            "Patient.identifier",
            "some comment",
            null
          );
        }

        @Test
        @DisplayName("FSHImporter Mapping #mappingRule should parse a mapping rule with a language")
        void parseMappingRuleLanguage() {
          var input = "\n"
                           + "          Mapping: MyMapping\n"
                           + "          * identifier -> \"Patient.identifier\" #lang\n"
                           + "          ";
          var result = importSingleText(input);
          var mapping = result.mappings.get("MyMapping");
          assertEquals(1, mapping.rules.size());
          assertMappingRule(
            mapping.rules.get(0),
            "identifier",
            "Patient.identifier",
            null,
            new FshCode("lang").withLocation(new int[] {3, 48, 3, 52}).withFile("")
          );
        }

        @Test
        @DisplayName("FSHImporter Mapping #mappingRule should parse a mapping rule with comment and language")
        void parseMappingRuleCommentLanguage() {
          var input = "\n"
                           + "          Mapping: MyMapping\n"
                           + "          * identifier -> \"Patient.identifier\" \"some comment\" #lang\n"
                           + "          ";
          var result = importSingleText(input);
          var mapping = result.mappings.get("MyMapping");
          assertEquals(1, mapping.rules.size());
          assertMappingRule(
            mapping.rules.get(0),
            "identifier",
            "Patient.identifier",
            "some comment",
            new FshCode("lang").withLocation(new int[] {3, 63, 3, 67}).withFile("")
          );
        }

        @Test
        @DisplayName("FSHImporter Mapping #mappingRule should log an error when a language has a system")
        void logErrorLanguageHasSystem() {
          var input = "\n"
                           + "          Mapping: MyMapping\n"
                           + "          * identifier -> \"Patient.identifier\" sys#lang\n"
                           + "          ";
          var result = importSingleText(input, "Mapping.fsh");
          var mapping = result.mappings.get("MyMapping");
          assertEquals(1, mapping.rules.size());
          assertMappingRule(
            mapping.rules.get(0),
            "identifier",
            "Patient.identifier",
            null,
            new FshCode("lang", "sys").withLocation(new int[] {3, 48, 3, 55}).withFile("Mapping.fsh")
          );
          assertTrue(matches(Pattern.compile("Do not specify a system for mapping language.*File: Mapping\\.fsh.*Line: 3\\D*", Pattern.DOTALL), loggerSpy.getLastMessage("warn")));
        }

        @Test
        @DisplayName("FSHImporter Mapping #mappingRule should parse a mapping rule with a multiline comment")
        void parseMappingRuleMultilineComment() {
          var input = "\n"
                           + "          Mapping: MyMapping\n"
                           + "          * identifier -> \"Patient.identifier\" \"\"\"\n"
                           + "          This is a multiline comment\n"
                           + "          that spans multiple lines\n"
                           + "          \"\"\"\n"
                           + "          ";
          var result = importSingleText(input);
          var mapping = result.mappings.get("MyMapping");
          assertEquals(1, mapping.rules.size());
          assertMappingRule(
            mapping.rules.get(0),
            "identifier",
            "Patient.identifier",
            "This is a multiline comment\nthat spans multiple lines",
            null
          );
        }

        @Test
        @DisplayName("FSHImporter Mapping #mappingRule should parse a mapping rule with a multiline comment and language")
        void parseMappingRuleMultilineCommentLanguage() {
          var input = "\n"
                           + "          Mapping: MyMapping\n"
                           + "          * identifier -> \"Patient.identifier\" \"\"\"\n"
                           + "          This is a multiline comment\n"
                           + "          with a language code\n"
                           + "          \"\"\" #lang\n"
                           + "          ";
          var result = importSingleText(input);
          var mapping = result.mappings.get("MyMapping");
          assertEquals(1, mapping.rules.size());
          assertMappingRule(
            mapping.rules.get(0),
            "identifier",
            "Patient.identifier",
            "This is a multiline comment\nwith a language code",
            new FshCode("lang").withLocation(new int[] {6, 15, 6, 19}).withFile("")
          );
        }

      }

      @Nested
      @DisplayName("#insertRule")
      class InsertRuleTests {
        @Test
        @DisplayName("FSHImporter Mapping #insertRule should parse an insert rule with a single RuleSet")
        void parseInsertRuleSingleRuleSet() {
          var input = "\n"
                           + "        Mapping: MyMapping\n"
                           + "        * insert MyRuleSet\n"
                           + "        ";
          var result = importSingleText(input, "Insert.fsh");
          var mapping = result.mappings.get("MyMapping");
          assertEquals(1, mapping.rules.size());
          assertInsertRule(mapping.rules.get(0), "", "MyRuleSet");
        }

      }

      @Nested
      @DisplayName("#pathRule")
      class PathRuleTests {
        @Test
        @DisplayName("FSHImporter Mapping #pathRule should parse a pathRule")
        void parsePathRule() {
          var input = "\n"
                           + "        Mapping: MyMapping\n"
                           + "        Source: Patient1\n"
                           + "        Target: http://example.org/target\n"
                           + "        * name\n"
                           + "        ";
          var result = importSingleText(input, "Path.fsh");
          var mapping = result.mappings.get("MyMapping");
          assertEquals(0, mapping.rules.size());
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
