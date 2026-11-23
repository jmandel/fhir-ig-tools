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

// port of sushi/test/import/FSHImporter.ParamRuleSet.test.ts (SUSHI 3.21.1)
class FSHImporterParamRuleSetTest {
  @Nested
  @DisplayName("FSHImporter")
  class FSHImporterTests {
    @BeforeEach void beforeeach() {
      loggerSpy.reset();
    }

    @Nested
    @DisplayName("ParamRuleSet")
    class ParamRuleSetTests {
      @Test
      @DisplayName("FSHImporter ParamRuleSet should parse a ParamRuleSet with a rule")
      void parseParamRuleSetRule() {
        FSHImporter importer = new FSHImporter();
        var input = "\n"
                         + "        RuleSet: MyRuleSet (system, strength)\n"
                         + "        * code from {system} {strength}\n"
                         + "        * pig from egg\n"
                         + "      ";
        importer.importFsh(List.of(new RawFSH(input, "Params.fsh")));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, importer.paramRuleSets.size());
        assertEquals(true, importer.paramRuleSets.containsKey("MyRuleSet"));
        var result = importer.paramRuleSets.get("MyRuleSet");
        assertEquals("MyRuleSet", result.name);
        assertToEqual(List.of("system", "strength"), result.parameters);
        var expectedContents = "\n"
                                    + "        * code from {system} {strength}\n"
                                    + "        * pig from egg";
        assertEquals(expectedContents, result.contents);
      }

      @Test
      @DisplayName("FSHImporter ParamRuleSet should preserve multiline string content ending in a quote when applying a ParamRuleSet")
      void preserveMultilineStringContentEndingQuote() {
        FSHImporter importer = new FSHImporter();
        var input = String.join("\n", "RuleSet: QuotedValueRuleSet(value)",
          "* valueString = \"\"\"{value}\"\"\"\"",
          "",
          "Profile: ObservationProfile",
          "Parent: Observation",
          "* insert QuotedValueRuleSet(ends in a quote)");
        var docs = importer.importFsh(List.of(new RawFSH(input, "Params.fsh")));
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, docs.size());
        var appliedRuleSet = docs.get(0).appliedRuleSets.get(
          org.hl7.fhir.sushi.js.Json.stringify(List.of("QuotedValueRuleSet", "ends in a quote"))
        );
        assertNotNull(appliedRuleSet);
        assertEquals(1, appliedRuleSet.rules.size());
        assertAssignmentRule(appliedRuleSet.rules.get(0), "valueString", "ends in a quote\"");
      }

      @Test
      @DisplayName("FSHImporter ParamRuleSet should parse a ParamRuleSet with a numeric name")
      void parseParamRuleSetNumericName() {
        FSHImporter importer = new FSHImporter();
        var input = "\n"
                         + "        RuleSet: 123 (system, strength)\n"
                         + "        * code from {system} {strength}\n"
                         + "      ";
        importer.importFsh(List.of(new RawFSH(input, "Params.fsh")));
        assertEquals(1, importer.paramRuleSets.size());
        assertEquals(true, importer.paramRuleSets.containsKey("123"));
        var result = importer.paramRuleSets.get("123");
        assertEquals("123", result.name);
        assertToEqual(List.of("system", "strength"), result.parameters);
      }

      @Test
      @DisplayName("FSHImporter ParamRuleSet should parse a ParamRuleSet when there is no space between the ruleset name and parameter list")
      void parseParamRuleSetThereNoSpaceBetween() {
        FSHImporter importer = new FSHImporter();
        var input = "\n"
                         + "        RuleSet: MyRuleSet(system, strength)\n"
                         + "        * code from {system} {strength}\n"
                         + "        * pig from egg\n"
                         + "      ";
        importer.importFsh(List.of(new RawFSH(input, "Params.fsh")));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, importer.paramRuleSets.size());
        assertEquals(true, importer.paramRuleSets.containsKey("MyRuleSet"));
        var result = importer.paramRuleSets.get("MyRuleSet");
        assertEquals("MyRuleSet", result.name);
        assertToEqual(List.of("system", "strength"), result.parameters);
        var expectedContents = "\n"
                                    + "        * code from {system} {strength}\n"
                                    + "        * pig from egg";
        assertEquals(expectedContents, result.contents);
      }

      @Test
      @DisplayName("FSHImporter ParamRuleSet should stop parsing ParamRuleSet contents when the next entity is defined")
      void stopParsingParamRuleSetContentsNextEntity() {
        FSHImporter importer = new FSHImporter();
        var input = "\n"
                         + "        RuleSet: FirstRuleSet (system, strength)\n"
                         + "        * code from http://example.org/{system}/info.html {strength}\n"
                         + "        * pig from egg\n"
                         + "\n"
                         + "        Profile: MyObservation\n"
                         + "        Parent: Observation\n"
                         + "\n"
                         + "        RuleSet: SecondRuleSet (min)\n"
                         + "        * stuff {min}..\n"
                         + "\n"
                         + "        Alias: $Something = http://example.org/Something\n"
                         + "\n"
                         + "        RuleSet: ThirdRuleSet(cookie)\n"
                         + "        * code from {cookie}\n"
                         + "\n"
                         + "        Extension: MyExtension\n"
                         + "\n"
                         + "        RuleSet: FourthRuleSet(toast)\n"
                         + "        * reason ^short = {toast}\n"
                         + "\n"
                         + "        Instance: ExampleObservation\n"
                         + "        InstanceOf: MyObservation\n"
                         + "\n"
                         + "        RuleSet: FifthRuleSet(strength, system)\n"
                         + "        * code from {system} {strength}\n"
                         + "\n"
                         + "        ValueSet: MyValueSet\n"
                         + "\n"
                         + "        RuleSet: SixthRuleSet(content)\n"
                         + "        * ^description = {content}\n"
                         + "\n"
                         + "        Invariant: cat-1\n"
                         + "\n"
                         + "        RuleSet: SeventhRuleSet(even, more)\n"
                         + "        * content[+] = {even}\n"
                         + "        * content[+] = {more}\n"
                         + "        \n"
                         + "        CodeSystem: MyCodeSystem\n"
                         + "\n"
                         + "        RuleSet: EighthRuleSet(continuation)\n"
                         + "        * continuation = {continuation} (exactly)\n"
                         + "\n"
                         + "        Mapping: SomeMapping\n"
                         + "\n"
                         + "        RuleSet: NinthRuleSet(tiring)\n"
                         + "        * code from {tiring}\n"
                         + "\n"
                         + "        Logical: MyLogical\n"
                         + "\n"
                         + "        RuleSet: TenthRuleSet(conclusion)\n"
                         + "        * valueString = {conclusion}\n"
                         + "\n"
                         + "        Resource: MyResource\n"
                         + "      ";
        var docs = importer.importFsh(List.of(new RawFSH(input, "Params.fsh")));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertEquals(1, docs.size());
        assertEquals(true, docs.get(0).profiles.containsKey("MyObservation"));
        assertEquals(true, docs.get(0).aliases.containsKey("$Something"));
        assertEquals(true, docs.get(0).extensions.containsKey("MyExtension"));
        assertEquals(true, docs.get(0).instances.containsKey("ExampleObservation"));
        assertEquals(true, docs.get(0).valueSets.containsKey("MyValueSet"));
        assertEquals(true, docs.get(0).invariants.containsKey("cat-1"));
        assertEquals(true, docs.get(0).codeSystems.containsKey("MyCodeSystem"));
        assertEquals(true, docs.get(0).mappings.containsKey("SomeMapping"));
        assertEquals(true, docs.get(0).logicals.containsKey("MyLogical"));
        assertEquals(true, docs.get(0).resources.containsKey("MyResource"));
        assertEquals(10, importer.paramRuleSets.size());
        assertEquals(true, importer.paramRuleSets.containsKey("FirstRuleSet"));
        var firstRuleSet = importer.paramRuleSets.get("FirstRuleSet");
        assertEquals("FirstRuleSet", firstRuleSet.name);
        assertToEqual(List.of("system", "strength"), firstRuleSet.parameters);
        var firstContents = "\n"
                                 + "        * code from http://example.org/{system}/info.html {strength}\n"
                                 + "        * pig from egg";
        assertEquals(firstContents, firstRuleSet.contents);
        var secondRuleSet = importer.paramRuleSets.get("SecondRuleSet");
        assertEquals("SecondRuleSet", secondRuleSet.name);
        assertToEqual(List.of("min"), secondRuleSet.parameters);
        var secondContents = "\n"
                                  + "        * stuff {min}..";
        assertEquals(secondContents, secondRuleSet.contents);
        var thirdRuleSet = importer.paramRuleSets.get("ThirdRuleSet");
        assertEquals("ThirdRuleSet", thirdRuleSet.name);
        assertToEqual(List.of("cookie"), thirdRuleSet.parameters);
        var thirdContents = "\n"
                                 + "        * code from {cookie}";
        assertEquals(thirdContents, thirdRuleSet.contents);
        var fourthRuleSet = importer.paramRuleSets.get("FourthRuleSet");
        assertEquals("FourthRuleSet", fourthRuleSet.name);
        assertToEqual(List.of("toast"), fourthRuleSet.parameters);
        var fourthContents = "\n"
                                  + "        * reason ^short = {toast}";
        assertEquals(fourthContents, fourthRuleSet.contents);
        var fifthRuleSet = importer.paramRuleSets.get("FifthRuleSet");
        assertEquals("FifthRuleSet", fifthRuleSet.name);
        assertToEqual(List.of("strength", "system"), fifthRuleSet.parameters);
        var fifthContents = "\n"
                                 + "        * code from {system} {strength}";
        assertEquals(fifthContents, fifthRuleSet.contents);
        var sixthRuleSet = importer.paramRuleSets.get("SixthRuleSet");
        assertEquals("SixthRuleSet", sixthRuleSet.name);
        assertToEqual(List.of("content"), sixthRuleSet.parameters);
        var sixthContents = "\n"
                                 + "        * ^description = {content}";
        assertEquals(sixthContents, sixthRuleSet.contents);
        var seventhRuleSet = importer.paramRuleSets.get("SeventhRuleSet");
        assertEquals("SeventhRuleSet", seventhRuleSet.name);
        assertToEqual(List.of("even", "more"), seventhRuleSet.parameters);
        var seventhContents = "\n"
                                   + "        * content[+] = {even}\n"
                                   + "        * content[+] = {more}";
        assertEquals(seventhContents, seventhRuleSet.contents);
        var eighthRuleSet = importer.paramRuleSets.get("EighthRuleSet");
        assertEquals("EighthRuleSet", eighthRuleSet.name);
        assertToEqual(List.of("continuation"), eighthRuleSet.parameters);
        var eighthContents = "\n"
                                  + "        * continuation = {continuation} (exactly)";
        assertEquals(eighthContents, eighthRuleSet.contents);
        var ninthRuleSet = importer.paramRuleSets.get("NinthRuleSet");
        assertEquals("NinthRuleSet", ninthRuleSet.name);
        assertToEqual(List.of("tiring"), ninthRuleSet.parameters);
        var ninthContents = "\n"
                                 + "        * code from {tiring}";
        assertEquals(ninthContents, ninthRuleSet.contents);
        var tenthRuleSet = importer.paramRuleSets.get("TenthRuleSet");
        assertEquals("TenthRuleSet", tenthRuleSet.name);
        assertToEqual(List.of("conclusion"), tenthRuleSet.parameters);
        var tenthContents = "\n"
                                 + "        * valueString = {conclusion}";
        assertEquals(tenthContents, tenthRuleSet.contents);
      }

      @Test
      @DisplayName("FSHImporter ParamRuleSet should log an error and skip the ParamRuleSet when encountering a ParamRuleSet with a name used by another ParamRuleSet")
      void logErrorSkipParamRuleSetEncounteringParamRuleSet() {
        FSHImporter importer = new FSHImporter();
        var input = "\n"
                         + "        RuleSet: MyRuleSet (system, strength)\n"
                         + "        * code from http://example.org/{system}/info.html {strength}\n"
                         + "        * pig from egg\n"
                         + "\n"
                         + "        RuleSet: MyRuleSet (someName)\n"
                         + "        * name = \"Dr. {someName}\"\n"
                         + "      ";
        importer.importFsh(List.of(new RawFSH(input, "Params.fsh")));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertEquals(1, importer.paramRuleSets.size());
        assertEquals(true, importer.paramRuleSets.containsKey("MyRuleSet"));
        var result = importer.paramRuleSets.get("MyRuleSet");
        assertEquals("MyRuleSet", result.name);
        assertToEqual(List.of("system", "strength"), result.parameters);
        var expectedContents = "\n"
                                    + "        * code from http://example.org/{system}/info.html {strength}\n"
                                    + "        * pig from egg";
        assertEquals(expectedContents, result.contents);
        assertTrue(matches(Pattern.compile("RuleSet named MyRuleSet already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        assertTrue(matches(Pattern.compile("File: Params\\.fsh.*Line: 6 - 7", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter ParamRuleSet should log an error and skip the ParamRuleSet when encountering an ParamRuleSet with a name used by another ParamRuleSet in another file")
      void logErrorSkipParamRuleSetEncounteringParamRuleSet2() {
        FSHImporter importer = new FSHImporter();
        var input1 = "\n"
                          + "        RuleSet: MyRuleSet (system, strength)\n"
                          + "        * code from http://example.org/{system}/info.html {strength}\n"
                          + "        * pig from egg\n"
                          + "      ";
        var input2 = "\n"
                          + "        RuleSet: MyRuleSet (someName)\n"
                          + "        * name = \"Dr. {someName}\"\n"
                          + "      ";
        importer.importFsh(List.of(new RawFSH(input1, "File1.fsh"), new RawFSH(input2, "File2.fsh")));
        assertEquals(0, loggerSpy.getAllMessages("warn").size());
        assertEquals(1, loggerSpy.getAllMessages("error").size());
        assertEquals(1, importer.paramRuleSets.size());
        assertEquals(1, importer.paramRuleSets.size());
        assertEquals(true, importer.paramRuleSets.containsKey("MyRuleSet"));
        var result = importer.paramRuleSets.get("MyRuleSet");
        assertEquals("MyRuleSet", result.name);
        assertToEqual(List.of("system", "strength"), result.parameters);
        var expectedContents = "\n"
                                    + "        * code from http://example.org/{system}/info.html {strength}\n"
                                    + "        * pig from egg";
        assertEquals(expectedContents, result.contents);
        assertTrue(matches(Pattern.compile("RuleSet named MyRuleSet already exists", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
        assertTrue(matches(Pattern.compile("File: File2\\.fsh.*Line: 2 - 3", Pattern.DOTALL), loggerSpy.getLastMessage("error")));
      }

      @Test
      @DisplayName("FSHImporter ParamRuleSet should log a warning when a ParamRuleSet has parameters that are not used in the contents")
      void logWarningParamRuleSetHasParametersAre() {
        FSHImporter importer = new FSHImporter();
        var input = "\n"
                         + "        RuleSet: MyRuleSet (system, something, strength, extra)\n"
                         + "        * code from http://example.org/{system}/info.html {strength}\n"
                         + "        * pig from egg\n"
                         + "      ";
        importer.importFsh(List.of(new RawFSH(input, "Params.fsh")));
        assertEquals(1, loggerSpy.getAllMessages("warn").size());
        assertEquals(0, loggerSpy.getAllMessages("error").size());
        assertTrue(matches(Pattern.compile("RuleSet MyRuleSet contains unused parameters: something, extra", Pattern.DOTALL), loggerSpy.getLastMessage("warn")));
        assertTrue(matches(Pattern.compile("File: Params\\.fsh.*Line: 2 - 4", Pattern.DOTALL), loggerSpy.getLastMessage("warn")));
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
