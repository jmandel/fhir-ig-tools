package org.hl7.fhir.sushi.utils;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertContainsText;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsonEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.NodeTestEnv.*;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getTestFHIRDefinitions;
import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.assertContains;
import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.assertContainsEqual;
import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.assertMatchesPattern;
import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.assertNotContains;
import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.assertNotContainsEqual;
import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.assertNotContainsText;
import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.jsRegex;
import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.len;
import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.obj;
import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.setProp;
import static org.hl7.fhir.sushi.utils.Processing.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.export.Package.FshMapEntry;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirdefs.InMemoryVirtualPackage;
import org.hl7.fhir.sushi.fhirtypes.CodeSystem;
import org.hl7.fhir.sushi.fhirtypes.InstanceDefinition;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fhirtypes.ValueSet;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.fshtypes.Extension;
import org.hl7.fhir.sushi.fshtypes.FshCodeSystem;
import org.hl7.fhir.sushi.fshtypes.FshValueSet;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.Invariant;
import org.hl7.fhir.sushi.fshtypes.Logical;
import org.hl7.fhir.sushi.fshtypes.Mapping;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.Resource;
import org.hl7.fhir.sushi.fshtypes.RuleSet;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.importer.RawFSH;
import org.hl7.fhir.sushi.importer.config.ConfigurationJs;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.sushi.testhelpers.JsAssert;
import org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions;
import org.hl7.fhir.sushi.utils.Processing.AutomaticDependency;
import org.hl7.fhir.sushi.utils.Processing.AutomaticDependencyPriority;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

// port of sushi/test/utils/Processing.test.ts (SUSHI 3.21.1)
// Jest mocks map to Processing's seams: readline-sync keyInYNStrict -> Processing.yesNoPrompt; init's readline /
// console / axios -> Processing.initIo; RegistryClient -> a lambda; fs spies -> real files in temp dirs (asserted on
// disk); init's process.cwd() -> user.dir pointed at a temp dir for the test.
@DisplayName("Processing")
class ProcessingTest {
  static final String __dirname = dirname("utils");
  static final String EOL = "\n";

  static {
    // TS imports loggerSpy at module load, so it captures logs from the first beforeAll on; load it (and its tap)
    // here too, before any nested class's @BeforeAll logs (e.g. #writeFHIRResources() IG Publisher mode).
    loggerSpy.reset();
  }

  /** The TS configuration object (readConfig result) as a JS value, for toEqual. */
  static JsObject js(Configuration c) {
    return ConfigurationJs.toJs(c);
  }

  @SuppressWarnings("unchecked")
  static List<JsObject> jsonList(String text) {
    return new ArrayList<>((List<JsObject>) JsAssert.json(text));
  }

  static int indexOf(String s, String sub) {
    return s.indexOf(sub);
  }

  @Nested
  @DisplayName("#isSupportedFHIRVersion")
  class IsSupportedFHIRVersion {
    @Test
    @DisplayName("Processing #isSupportedFHIRVersion should support published version >= 4.0.1")
    void shouldSupportPublishedVersion401() {
      assertEquals(true, isSupportedFHIRVersion("4.0.1"));
      assertEquals(true, isSupportedFHIRVersion("4.2.0"));
      assertEquals(true, isSupportedFHIRVersion("4.4.0"));
      assertEquals(true, isSupportedFHIRVersion("4.5.0"));
    }

    @Test
    @DisplayName("Processing #isSupportedFHIRVersion should support current version")
    void shouldSupportCurrentVersion() {
      assertEquals(true, isSupportedFHIRVersion("current"));
    }

    @Test
    @DisplayName("Processing #isSupportedFHIRVersion should not support published versions < 4.0.1")
    void shouldNotSupportPublishedVersions401() {
      assertEquals(false, isSupportedFHIRVersion("4.0.0"));
      assertEquals(false, isSupportedFHIRVersion("3.0.2"));
      assertEquals(false, isSupportedFHIRVersion("1.0.2"));
      assertEquals(false, isSupportedFHIRVersion("0.0.82"));
    }
  }

  @Nested
  @DisplayName("#findInputDir()")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class FindInputDir {
    String tempRoot;

    @BeforeAll
    void beforeAll() {
      tempRoot = temp.mkdirSync("sushi-test");
      fs.mkdirpSync(path.join(tempRoot, "has-fsh", "fsh"));
      fs.mkdirpSync(path.join(tempRoot, "has-input-fsh", "input", "fsh"));
      fs.mkdirpSync(path.join(tempRoot, "has-input", "input"));
      fs.mkdirpSync(path.join(tempRoot, "has-fsh-and-input-fsh", "fsh"));
      fs.mkdirpSync(path.join(tempRoot, "has-fsh-and-input-fsh", "input", "fsh"));
      fs.mkdirpSync(path.join(tempRoot, "flat-tank", "ig-data"));
      fs.mkdirSync(path.join(tempRoot, "no-fsh"));
      fs.ensureFileSync(path.join(tempRoot, "no-fsh", "notfsh.txt"));
    }

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("Processing #findInputDir() should find a path to the current working directory when no input folder is specified")
    void shouldFindAPathToTheCurrentWorkingDirectoryWhenNoInputFolderIsSpecified() {
      String foundInput = ensureInputDir(null);
      assertEquals(".", foundInput);
    }

    @Test
    @DisplayName("Processing #findInputDir() should find a path to the input/fsh subdirectory if present")
    void shouldFindAPathToTheInputFshSubdirectoryIfPresent() {
      String input = path.join(tempRoot, "has-input-fsh");
      String foundInput = findInputDir(input);
      assertEquals(path.join(tempRoot, "has-input-fsh", "input", "fsh"), foundInput);
    }

    @Test
    @DisplayName("Processing #findInputDir() should use path to input/fsh as input if input/ subdirectory present but no input/fsh present")
    void shouldUsePathToInputFshAsInputIfInputSubdirectoryPresentButNoInputFshPresent() {
      String input = path.join(tempRoot, "has-input");
      String foundInput = findInputDir(input);
      assertEquals(path.join(tempRoot, "has-input", "input", "fsh"), foundInput);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("Processing #findInputDir() should find a path to input/fsh if no fsh files are present, no root level ig-data folder, and no fsh subdirectory (current tank with no fsh files)")
    void shouldFindAPathToInputFshIfNoFshFilesArePresent() {
      String input = path.join(tempRoot, "no-fsh");
      String foundInput = findInputDir(input);
      assertEquals(path.join(tempRoot, "no-fsh", "input", "fsh"), foundInput);
    }

    // TODO: Tests current tank configuration is preferred over legacy. Remove when legacy error handling is removed.
    @Test
    @DisplayName("Processing #findInputDir() should prefer path to input/fsh over fsh/ if both present")
    void shouldPreferPathToInputFshOverFshIfBothPresent() {
      String input = path.join(tempRoot, "has-fsh-and-input-fsh");
      String foundInput = findInputDir(input);
      assertEquals(path.join(tempRoot, "has-fsh-and-input-fsh", "input", "fsh"), foundInput);
    }

    // TODO: Tests legacy case logs error. Remove when error is removed.
    @Test
    @DisplayName("Processing #findInputDir() should log an error and not change input directory if the fsh subdirectory if present (legacy ./fsh/)")
    void shouldLogAnErrorIfTheFshSubdirectoryIsPresent() {
      String input = path.join(tempRoot, "has-fsh");
      String foundInput = findInputDir(input);
      assertEquals(path.join(tempRoot, "has-fsh"), foundInput);
      JsAssert.assertMatches("Use of this folder is NO LONGER SUPPORTED", "s", loggerSpy.getLastMessage("error"));
    }

    // TODO: Tests legacy case logs error. Remove when error is removed.
    @Test
    @DisplayName("Processing #findInputDir() should log an error and not change input directory if the fsh subdirectory is not present and a root ig-data is present (legacy flat tank)")
    void shouldLogAnErrorForALegacyFlatTank() {
      String input = path.join(tempRoot, "flat-tank");
      String foundInput = findInputDir(input);
      assertEquals(input, foundInput);
      JsAssert.assertMatches("Support for other folder structures is NO LONGER SUPPORTED", "s",
          loggerSpy.getLastMessage("error"));
    }
  }

  @Nested
  @DisplayName("#ensureOutputDir()")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class EnsureOutputDir {
    String tempRoot;

    @BeforeAll
    void beforeAll() {
      tempRoot = temp.mkdirSync("sushi-test");
      fs.mkdirSync(path.join(tempRoot, "my-input"));
    }

    // TS spies on fs.emptyDirSync (mockImplementation: no-op) and fakes existsSync for fsh-generated once; the Java
    // port has no fs spy, so each test starts without a fsh-generated folder and creates a real one when needed.
    @BeforeEach
    void beforeEach() {
      fs.removeSync(path.join(tempRoot, "fsh-generated"));
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("Processing #ensureOutputDir() should use and create the output directory when it is provided")
    void shouldUseAndCreateTheOutputDirectoryWhenItIsProvided() {
      String input = path.join(tempRoot, "my-input");
      String output = path.join(tempRoot, "my-output");
      String outputDir = ensureOutputDir(input, output);
      assertEquals(output, outputDir);
      assertTrue(fs.existsSync(output));
    }

    @Test
    @DisplayName("Processing #ensureOutputDir() should default the output directory to the grandparent of the input no output directory provided (e.g. ./input/fsh -> ./)")
    void shouldDefaultTheOutputDirectoryToTheGrandparentOfTheInput() {
      String input = path.join(tempRoot, "my-input", "my-fsh");
      String outputDir = ensureOutputDir(input, null);
      assertEquals(tempRoot, outputDir);
      assertTrue(fs.existsSync(outputDir));
      JsAssert.assertMatches("No output path specified", loggerSpy.getLastMessage("info"));
    }

    @Test
    @DisplayName("Processing #ensureOutputDir() should empty the fsh-generated folder if the output directory contains one")
    void shouldEmptyTheFshGeneratedFolderIfTheOutputDirectoryContainsOne() {
      // TS: emptyDirSpy called once with path.join(tempRoot, 'fsh-generated'); here: a real folder gets emptied
      String fshGenerated = path.join(tempRoot, "fsh-generated");
      fs.outputFileSync(path.join(fshGenerated, "resources", "old.json"), "{}");
      fs.outputFileSync(path.join(fshGenerated, "fsh-index.txt"), "old");
      String input = path.join(tempRoot, "my-input", "my-fsh");
      String outputDir = ensureOutputDir(input, null);
      assertEquals(tempRoot, outputDir);
      assertTrue(fs.existsSync(outputDir));
      assertTrue(fs.existsSync(fshGenerated));
      assertEquals(0, fs.readdirSync(fshGenerated).size());
    }

    @Test
    @DisplayName("Processing #ensureOutputDir() should log an error when emptying the directory fails")
    void shouldLogAnErrorWhenEmptyingTheDirectoryFails() {
      // TS: emptyDirSpy throws Error('foo'). Java: fsh-generated is a plain file, so emptying it fails for real
      // (ENOTDIR); the message after ": " is the real error instead of "foo".
      String fshGenerated = path.join(tempRoot, "fsh-generated");
      fs.outputFileSync(fshGenerated, "not a folder");
      String input = path.join(tempRoot, "my-input", "my-fsh");
      String outputDir = ensureOutputDir(input, null);
      assertEquals(tempRoot, outputDir);
      assertTrue(fs.existsSync(outputDir));
      JsAssert.assertMatches("Unable to empty existing fsh-generated folder.*: .*fsh-generated", "",
          loggerSpy.getLastMessage("error"));
    }
  }

  @Nested
  @DisplayName("#readConfig()")
  class ReadConfig {
    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
    }

    @Test
    @DisplayName("Processing #readConfig() should return the contents of sushi-config.yaml from the input directory")
    void shouldReturnTheContentsOfSushiConfigYamlFromTheInputDirectory() {
      String input = path.join(__dirname, "fixtures", "valid-yaml");
      Configuration config = readConfig(input);
      assertJsEqual(validYamlConfig("0.1.0", "active", "CI Build"), js(config));
    }

    @Test
    @DisplayName("Processing #readConfig() should allow FHIR R5")
    void shouldAllowFHIRR5() {
      String input = path.join(__dirname, "fixtures", "fhir-r5");
      Configuration config = readConfig(input);
      assertJsonEqual("[\"4.5.0\"]", config.fhirVersion);
    }

    @Test
    @DisplayName("Processing #readConfig() should allow FHIR R6 prerelease")
    void shouldAllowFHIRR6Prerelease() {
      String input = path.join(__dirname, "fixtures", "fhir-r6-prerelease");
      Configuration config = readConfig(input);
      assertJsonEqual("[\"6.0.0-ballot2\"]", config.fhirVersion);
    }

    @Test
    @DisplayName("Processing #readConfig() should allow FHIR R6 full release")
    void shouldAllowFHIRR6FullRelease() {
      String input = path.join(__dirname, "fixtures", "fhir-r6");
      Configuration config = readConfig(input);
      assertJsonEqual("[\"6.0.0\"]", config.fhirVersion);
    }

    @Test
    @DisplayName("Processing #readConfig() should allow FHIR current")
    void shouldAllowFHIRCurrent() {
      String input = path.join(__dirname, "fixtures", "fhir-current");
      Configuration config = readConfig(input);
      assertJsonEqual("[\"current\"]", config.fhirVersion);
    }

    @Test
    @DisplayName("Processing #readConfig() should extract a configuration from an ImplementationGuide JSON when sushi-config.yaml is absent")
    void shouldExtractAConfigurationFromAnImplementationGuideJSON() {
      String input = path.join(__dirname, "fixtures", "ig-JSON-only");
      Configuration config = readConfig(input);
      assertJsonEqual("""
          {
            "FSHOnly": true,
            "canonical": "http://example.org",
            "fhirVersion": ["4.0.1"],
            "status": "draft",
            "parameters": [],
            "url": "http://example.org"
          }
          """, js(config));
    }

    @Test
    @DisplayName("Processing #readConfig() should log and throw an error when sushi-config.yaml is not found in the input directory")
    void shouldLogAndThrowAnErrorWhenSushiConfigYamlIsNotFound() {
      String input = path.join(__dirname, "fixtures", "no-package");
      assertThrows(RuntimeException.class, () -> readConfig(input));
      JsAssert.assertMatches("No sushi-config\\.yaml", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("Processing #readConfig() should log and throw an error when the contents of sushi-config.yaml are not valid yaml")
    void shouldLogAndThrowAnErrorWhenTheContentsAreNotValidYaml() {
      String input = path.join(__dirname, "fixtures", "invalid-yaml");
      assertThrows(RuntimeException.class, () -> readConfig(input));
      JsAssert.assertMatches("not a valid YAML object", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("Processing #readConfig() should log and throw an error when the configuration uses an unsupported FHIR version (DSTU2)")
    void shouldLogAndThrowForDSTU2() {
      String input = path.join(__dirname, "fixtures", "fhir-dstu2");
      assertThrows(RuntimeException.class, () -> readConfig(input));
      JsAssert.assertMatches("must specify a supported version of FHIR", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("Processing #readConfig() should log and throw an error when the configuration uses an unsupported FHIR version (4.0.0)")
    void shouldLogAndThrowFor400() {
      String input = path.join(__dirname, "fixtures", "fhir-four-oh-oh");
      assertThrows(RuntimeException.class, () -> readConfig(input));
      JsAssert.assertMatches("must specify a supported version of FHIR", "s", loggerSpy.getLastMessage("error"));
    }
  }

  /** The expected readConfig result for fixtures/valid-yaml (the TS literal, with the values the tests vary). */
  static Object validYamlConfig(String version, String status, String releaseLabel) {
    return obj("filePath", path.join(__dirname, "fixtures", "valid-yaml", "sushi-config.yaml"),
        "id", "sushi-test",
        "packageId", "sushi-test",
        "canonical", "http://hl7.org/fhir/sushi-test",
        "url", "http://hl7.org/fhir/sushi-test/ImplementationGuide/sushi-test",
        "version", version,
        "name", "FSHTestIG",
        "title", "FSH Test IG",
        "status", status,
        "contact", JsAssert.json("""
            [
              {
                "name": "Bill Cod",
                "telecom": [
                  { "system": "url", "value": "https://capecodfishermen.org/" },
                  { "system": "email", "value": "cod@reef.gov" }
                ]
              }
            ]
            """),
        "description", "Provides a simple example of how FSH can be used to create an IG",
        "license", "CC0-1.0",
        "fhirVersion", JsAssert.json("[\"4.0.1\"]"),
        "dependencies", JsAssert.json("""
            [
              { "packageId": "hl7.fhir.us.core", "version": "3.1.0" },
              { "packageId": "hl7.fhir.uv.vhdir", "version": "current" }
            ]
            """),
        "FSHOnly", false,
        "applyExtensionMetadataToRoot", true,
        "instanceOptions", JsAssert.json("""
            { "setMetaProfile": "always", "setId": "always", "manualSliceOrdering": false }
            """),
        "parameters", JsAssert.json("""
            [
              { "code": "copyrightyear", "value": "2020" },
              { "code": "releaselabel", "value": "%s" }
            ]
            """.formatted(releaseLabel)));
  }

  @Nested
  @DisplayName("#updateConfig")
  class UpdateConfig {
    @Test
    @DisplayName("Processing #updateConfig should update the config with the command line options")
    void shouldUpdateTheConfigWithTheCommandLineOptions() {
      String input = path.join(__dirname, "fixtures", "valid-yaml");
      Configuration config = readConfig(input);
      Map<String, String> programConfig = new LinkedHashMap<>();
      programConfig.put("version", "1.2.3");
      programConfig.put("status", "draft");
      programConfig.put("releaselabel", "qa-preview");
      updateConfig(config, programConfig);
      assertJsEqual(validYamlConfig("1.2.3", "draft", "qa-preview"), js(config));
    }

    @Test
    @DisplayName("Processing #updateConfig should ignore unsupported elements")
    void shouldIgnoreUnsupportedElements() {
      String input = path.join(__dirname, "fixtures", "valid-yaml");
      Configuration config = readConfig(input);
      Map<String, String> programConfig = new LinkedHashMap<>();
      programConfig.put("version", "1.2.3");
      programConfig.put("something", "unsupported");
      updateConfig(config, programConfig);
      assertJsEqual(validYamlConfig("1.2.3", "active", "CI Build"), js(config));
    }

    @Test
    @DisplayName("Processing #updateConfig should support values with colons")
    void shouldSupportValuesWithColons() {
      String input = path.join(__dirname, "fixtures", "valid-yaml");
      Configuration config = readConfig(input);
      Map<String, String> programConfig = new LinkedHashMap<>();
      programConfig.put("version", "1.2.3-beta:1");
      updateConfig(config, programConfig);
      assertJsEqual(validYamlConfig("1.2.3-beta:1", "active", "CI Build"), js(config));
    }
  }

  @Nested
  @DisplayName("#updateExternalDependencies")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class UpdateExternalDependencies {
    String tempRoot;
    Configuration config;
    /** keyInSpy.mock.calls (the questions asked) */
    List<String> keyInCalls;
    /** keyInSpy.mockReturnValueOnce */
    Boolean keyInAnswer;
    RegistryClient registryClientMock;
    YesNoPrompt originalPrompt;

    @BeforeAll
    void beforeAll() {
      tempRoot = temp.mkdirSync("sushi-test");
      originalPrompt = Processing.yesNoPrompt;
    }

    @BeforeEach
    void beforeEach() {
      registryClientMock = (name, version) -> {
        if (!"latest".equals(version)) {
          return version;
        }
        return switch (name) {
          case "hl7.fhir.us.core" -> "3.1.0";
          case "hl7.fhir.uv.genomics-reporting" -> "3.5.0";
          case "hl7.fhir.us.mcode" -> "2.1.1";
          default -> version;
        };
      };
      String originalInput = path.join(__dirname, "fixtures", "extra-dependencies", "sushi-config.yaml");
      fs.copyFileSync(originalInput, path.join(tempRoot, "sushi-config.yaml"));
      config = readConfig(tempRoot);
      keyInCalls = new ArrayList<>();
      keyInAnswer = null;
      Processing.yesNoPrompt = question -> {
        keyInCalls.add(question);
        boolean answer = Boolean.TRUE.equals(keyInAnswer);
        keyInAnswer = null;
        return answer;
      };
    }

    @AfterEach
    void afterEach() {
      Processing.yesNoPrompt = originalPrompt;
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("Processing #updateExternalDependencies should update versioned dependencies in the configuration")
    void shouldUpdateVersionedDependenciesInTheConfiguration() {
      keyInAnswer = true;
      boolean result = updateExternalDependencies(config, registryClientMock);
      assertEquals(true, result);
      List<JsObject> updatedDependencies = jsonList("""
          [
            { "packageId": "hl7.fhir.us.core", "version": "3.1.0" },
            { "packageId": "hl7.fhir.uv.vhdir", "version": "current" },
            { "packageId": "hl7.fhir.uv.genomics-reporting", "version": "3.5.0" },
            {
              "packageId": "hl7.fhir.us.mcode",
              "id": "mcode",
              "uri": "http://hl7.org/fhir/us/mcode/ImplementationGuide/hl7.fhir.us.mcode",
              "version": "2.1.1"
            },
            { "packageId": "hl7.fhir.us.davinci-pas", "version": "dev" }
          ]
          """);
      assertJsEqual(updatedDependencies, config.dependencies);
      Configuration configOnDisk = readConfig(tempRoot);
      assertJsEqual(updatedDependencies, configOnDisk.dependencies);
    }

    @Test
    @DisplayName("Processing #updateExternalDependencies should display a list of the available version updates")
    void shouldDisplayAListOfTheAvailableVersionUpdates() {
      keyInAnswer = true;
      boolean result = updateExternalDependencies(config, registryClientMock);
      assertEquals(true, result);
      String displayedMessage = keyInCalls.get(0);
      assertContainsText("- hl7.fhir.uv.genomics-reporting: 3.5.0", displayedMessage);
      assertContainsText("- hl7.fhir.us.mcode: 2.1.1", displayedMessage);
      // packages without updates should not be listed
      assertNotContainsText("hl7.fhir.us.core", displayedMessage);
      assertNotContainsText("hl7.fhir.uv.vhdir", displayedMessage);
      assertNotContainsText("hl7.fhir.us.davinci-pas", displayedMessage);
    }

    @Test
    @DisplayName("Processing #updateExternalDependencies should not update dependencies if the user quits without applying updates")
    void shouldNotUpdateDependenciesIfTheUserQuitsWithoutApplyingUpdates() {
      keyInAnswer = false;
      boolean result = updateExternalDependencies(config, registryClientMock);
      assertEquals(false, result);
      List<JsObject> originalDependencies = jsonList("""
          [
            { "packageId": "hl7.fhir.us.core", "version": "3.1.0" },
            { "packageId": "hl7.fhir.uv.vhdir", "version": "current" },
            { "packageId": "hl7.fhir.uv.genomics-reporting", "version": "2.0.0" },
            {
              "packageId": "hl7.fhir.us.mcode",
              "id": "mcode",
              "uri": "http://hl7.org/fhir/us/mcode/ImplementationGuide/hl7.fhir.us.mcode",
              "version": "2.0.1"
            },
            { "packageId": "hl7.fhir.us.davinci-pas", "version": "dev" }
          ]
          """);
      Configuration configOnDisk = readConfig(tempRoot);
      assertJsEqual(originalDependencies, configOnDisk.dependencies);
    }

    @Test
    @DisplayName("Processing #updateExternalDependencies should return true without requiring input if no dependencies can be updated")
    void shouldReturnTrueWithoutRequiringInputIfNoDependenciesCanBeUpdated() {
      config.dependencies = jsonList("""
          [
            { "packageId": "hl7.fhir.us.core", "version": "3.1.0" },
            { "packageId": "hl7.fhir.uv.vhdir", "version": "current" },
            { "packageId": "hl7.fhir.us.davinci-pas", "version": "dev" }
          ]
          """);
      boolean result = updateExternalDependencies(config, registryClientMock);
      assertEquals(true, result);
      assertEquals(0, keyInCalls.size());
    }

    @Test
    @DisplayName("Processing #updateExternalDependencies should return true without requiring input if the configuration was obtained without a sushi-config.yaml file")
    void shouldReturnTrueWithoutRequiringInputIfNoSushiConfigYamlFile() {
      config.filePath = null;
      boolean result = updateExternalDependencies(config, registryClientMock);
      assertEquals(true, result);
      assertEquals(0, keyInCalls.size());
    }

    @Test
    @DisplayName("Processing #updateExternalDependencies should return true without requiring input if there are no dependencies in the configuration")
    void shouldReturnTrueWithoutRequiringInputIfThereAreNoDependencies() {
      config.dependencies = new ArrayList<>();
      boolean result = updateExternalDependencies(config, registryClientMock);
      assertEquals(true, result);
      assertEquals(0, keyInCalls.size());
    }

    @Test
    @DisplayName("Processing #updateExternalDependencies should log a message when skipping dependencies with NPM aliases")
    void shouldLogAMessageWhenSkippingDependenciesWithNPMAliases() {
      config.dependencies = jsonList("""
          [
            { "packageId": "hl7.fhir.us.core", "version": "3.1.0" },
            { "packageId": "v200@npm:hl7.fhir.us.core", "version": "2.0.0" },
            { "packageId": "hl7.fhir.us.davinci-pas", "version": "dev" }
          ]
          """);
      boolean result = updateExternalDependencies(config, registryClientMock);
      assertEquals(true, result);
      assertEquals(0, keyInCalls.size());
      assertContains("Skipping dependency version check for NPM aliased package: v200@npm:hl7.fhir.us.core",
          loggerSpy.getAllMessages("info"));
      assertNotContains("Skipping dependency version check for NPM aliased package: hl7.fhir.us.core",
          loggerSpy.getAllMessages("info"));
    }
  }

  // ---- from WP-8's ProcessingDependenciesTest (helpers + the two dependency-loading describes, full Jest names)
  static final int NUM_R4_AUTO_DEPENDENCIES = 4;
  static final int NUM_R4_AUTO_DEPENDENCIES_LOW = 3;
  static final int NUM_R4_AUTO_DEPENDENCIES_HIGH = 1;
  static final int NUM_R5_AUTO_DEPENDENCIES = 3;

  /** test/utils/minimalConfig.ts */
  static Configuration minimalConfig() {
    Configuration c = new Configuration();
    c.filePath = "sushi-config.yaml";
    c.id = "fhir.us.minimal";
    c.canonical = "http://hl7.org/fhir/us/minimal";
    c.name = "MinimalIG";
    c.status = "draft";
    c.version = "1.0.0";
    c.fhirVersion = new ArrayList<>(List.of("4.0.1"));
    return c;
  }

  static JsObject dep(String packageId, String version) {
    JsObject o = new JsObject();
    o.put("packageId", packageId);
    if (version != null) o.put("version", version);
    return o;
  }

  static List<JsObject> deps(JsObject... d) {
    return new ArrayList<>(List.of(d));
  }

  static List<String> loadedPackages(FHIRDefinitions defs) {
    return defs.findPackageInfos("*").stream().map(p -> p.name + "#" + p.version).toList();
  }

  static void depMatches(String regex, String actual) {
    assertTrue(actual != null && Pattern.compile(regex, Pattern.DOTALL).matcher(actual).find(),
        () -> "expected /" + regex + "/s to match: " + actual);
  }

  static void depNotMatches(String regex, String actual) {
    assertFalse(actual != null && Pattern.compile(regex, Pattern.DOTALL).matcher(actual).find(),
        () -> "expected /" + regex + "/s not to match: " + actual);
  }

  /** test/testhelpers/asserts.ts assertAutomaticR4Dependencies */
  static void assertAutomaticR4Dependencies(List<String> packages) {
    assertTrue(packages.contains("sushi-r5forR4#1.0.0"), packages::toString);
    for (AutomaticDependency dep : Processing.AUTOMATIC_DEPENDENCIES) {
      if (dep.packageId().equals("hl7.terminology.r4") && dep.version().equals("latest")) {
        assertTrue(packages.contains("hl7.terminology.r4#9.9.9"), packages::toString);
      } else if (dep.packageId().equals("hl7.fhir.uv.extensions.r4") && dep.version().equals("latest")) {
        assertTrue(packages.contains("hl7.fhir.uv.extensions.r4#9.9.9"), packages::toString);
      } else if (dep.packageId().equals("hl7.fhir.uv.tools.r4") && dep.version().equals("latest")) {
        assertTrue(packages.contains("hl7.fhir.uv.tools.r4#9.9.9"), packages::toString);
      } else if (!dep.packageId().endsWith(".r5")) {
        assertTrue(packages.contains(dep.packageId() + "#" + dep.version()), packages::toString);
      }
    }
  }

  /** test/testhelpers/asserts.ts assertAutomaticR5Dependencies */
  static void assertAutomaticR5Dependencies(List<String> packages) {
    for (AutomaticDependency dep : Processing.AUTOMATIC_DEPENDENCIES) {
      if (dep.packageId().equals("hl7.terminology.r5") && dep.version().equals("latest")) {
        assertTrue(packages.contains("hl7.terminology.r5#9.9.9"), packages::toString);
      } else if (dep.packageId().equals("hl7.fhir.uv.extensions.r5") && dep.version().equals("latest")) {
        assertTrue(packages.contains("hl7.fhir.uv.extensions.r5#9.9.9"), packages::toString);
      } else if (dep.packageId().equals("hl7.fhir.uv.tools.r5") && dep.version().equals("latest")) {
        assertTrue(packages.contains("hl7.fhir.uv.tools.r5#9.9.9"), packages::toString);
      } else if (!dep.packageId().endsWith(".r4")) {
        assertTrue(packages.contains(dep.packageId() + "#" + dep.version()), packages::toString);
      }
    }
  }

  // ---------------------------------------------------------------------------------------- jest mock equivalents
  // The only places that touch the fake package provider (TS: defs.<x>ClientMock.<method>.mockReset()
  // .calledWith(...).mockRejectedValue(...)).

  static FHIRDefinitions defsWith(TestFHIRDefinitions.FakePackageProvider provider) {
    FHIRDefinitions.Overrides o = new FHIRDefinitions.Overrides();
    o.packageProvider = provider;
    return FHIRDefinitions.createFHIRDefinitions(o);
  }

  /** TS: defs.registryClientMock.download.calledWith(name, version).mockRejectedValue(new Error(message)) */
  static FHIRDefinitions defsWithFailingDownload(String name, String version, String message) {
    return defsWith(new TestFHIRDefinitions.FakePackageProvider() {
      @java.lang.Override
      public CachedPackage loadPackage(String n, String v) {
        if (n.equals(name) && v.equals(version)) throw new RuntimeException(message);
        return super.loadPackage(n, v);
      }
    });
  }

  /** TS: defs.currentBuildClientMock.downloadCurrentBuild.calledWith(name).mockRejectedValue(new Error()) */
  static FHIRDefinitions defsWithFailingCurrentDownload(String name) {
    return defsWith(new TestFHIRDefinitions.FakePackageProvider() {
      @java.lang.Override
      public CachedPackage loadPackage(String n, String v) {
        if (n.equals(name) && v.startsWith("current")) throw new RuntimeException();
        return super.loadPackage(n, v);
      }
    });
  }

  /** TS: defs.registryClientMock.resolveVersion: reject for (name, 'latest'), otherwise latest -> 9.9.9 */
  static FHIRDefinitions defsWithFailingResolveLatest(String name) {
    return defsWith(new TestFHIRDefinitions.FakePackageProvider() {
      @java.lang.Override
      public String resolveLatest(String n) {
        if (n.equals(name)) throw new RuntimeException();
        return super.resolveLatest(n);
      }
    });
  }

  // ------------------------------------------------------------------------------------------------ tests

  @Nested
  @DisplayName("#loadExternalDependencies()")
  class LoadExternalDependencies {
    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should load specified dependencies")
    void specified() {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("hl7.fhir.us.core", "3.1.0"));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(2 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      assertTrue(loaded.contains("hl7.fhir.r4.core#4.0.1"));
      assertTrue(loaded.contains("hl7.fhir.us.core#3.1.0"));
      assertAutomaticR4Dependencies(loaded);
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should load specified dependencies when NPM aliases are used")
    void npmAliases() {
      // See: https://chat.fhir.org/#narrow/channel/179239-tooling/topic/NPM.20Aliases/near/517985527
      Configuration config = minimalConfig();
      config.dependencies = deps(
          dep("hl7.fhir.us.mcode", "4.0.0"),
          dep("hl7.fhir.us.core", "7.0.0"),
          dep("v610@npm:hl7.fhir.us.core", "6.1.0"),
          dep("hl7.fhir.us.qicore", "6.0.0"),
          dep("v311@npm:hl7.fhir.us.core", "3.1.1"));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(6 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      // Check that specified packages are loaded in correct order after autodependencies.
      // Correct order includes sorting multi-version packages so most recent is last.
      assertEquals(List.of(
          "hl7.fhir.us.mcode#4.0.0",
          "hl7.fhir.us.core#3.1.1",
          "hl7.fhir.us.core#6.1.0",
          "hl7.fhir.us.core#7.0.0",
          "hl7.fhir.us.qicore#6.0.0",
          "hl7.fhir.r4.core#4.0.1"),
          loaded.subList(NUM_R4_AUTO_DEPENDENCIES_LOW, loaded.size() - NUM_R4_AUTO_DEPENDENCIES_HIGH));
      assertAutomaticR4Dependencies(loaded);
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should load automatic dependencies last so they have highest priority")
    void autoLast() {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("hl7.fhir.us.core", "3.1.0"));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(2 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      assertEquals(List.of(
          "sushi-r5forR4#1.0.0",
          "hl7.fhir.uv.tools.r4#9.9.9",
          "hl7.terminology.r4#9.9.9",
          "hl7.fhir.us.core#3.1.0",
          "hl7.fhir.r4.core#4.0.1",
          "hl7.fhir.uv.extensions.r4#9.9.9"), loaded);
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should wait to load user-specified autodepency overrides until the end (with the other automatic dependencies)")
    void overridesAtEnd() {
      Configuration config = minimalConfig();
      config.dependencies = deps(
          dep("hl7.fhir.us.core", "3.1.0"),
          dep("hl7.fhir.uv.extensions.r4", "7.7.7"),
          dep("hl7.fhir.uv.tools.r4", "9.8.7"));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(2 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      assertEquals(List.of(
          "sushi-r5forR4#1.0.0",
          "hl7.fhir.uv.tools.r4#9.8.7",
          "hl7.terminology.r4#9.9.9",
          "hl7.fhir.us.core#3.1.0",
          "hl7.fhir.r4.core#4.0.1",
          "hl7.fhir.uv.extensions.r4#7.7.7"), loaded);
      assertEquals(0, loggerSpy.getAllLogs("warn").size());
    }

    void fhirVersionCase(String fhirVersion, int expectedSize, List<String> contains, boolean preReleaseWarn) {
      Configuration config = minimalConfig();
      config.fhirVersion = new ArrayList<>(List.of(fhirVersion));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(expectedSize, loaded.size(), loaded::toString);
      for (String c : contains) assertTrue(loaded.contains(c), () -> c + " not in " + loaded);
      if (preReleaseWarn) {
        depMatches("support for pre-release versions of FHIR is experimental", loggerSpy.getLastMessage("warn"));
      } else {
        assertEquals(0, loggerSpy.getAllLogs("warn").size());
      }
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should support prerelease FHIR R4B dependencies")
    void r4bPre() {
      fhirVersionCase("4.1.0", 1 + NUM_R4_AUTO_DEPENDENCIES, List.of("hl7.fhir.r4b.core#4.1.0", "sushi-r5forR4#1.0.0",
          "hl7.fhir.uv.tools.r4#9.9.9", "hl7.terminology.r4#9.9.9"), true);
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should support prerelease FHIR R4B snapshot dependencies")
    void r4bSnapshot() {
      fhirVersionCase("4.3.0-snapshot1", 1 + NUM_R4_AUTO_DEPENDENCIES, List.of("hl7.fhir.r4b.core#4.3.0-snapshot1",
          "sushi-r5forR4#1.0.0", "hl7.fhir.uv.tools.r4#9.9.9", "hl7.terminology.r4#9.9.9"), true);
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should support official FHIR R4B dependency")
    void r4b() {
      fhirVersionCase("4.3.0", 1 + NUM_R4_AUTO_DEPENDENCIES, List.of("hl7.fhir.r4b.core#4.3.0", "sushi-r5forR4#1.0.0",
          "hl7.fhir.uv.tools.r4#9.9.9", "hl7.terminology.r4#9.9.9"), false);
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should support prerelease FHIR R5 dependencies")
    void r5Pre() {
      fhirVersionCase("4.5.0", 1 + NUM_R5_AUTO_DEPENDENCIES, List.of("hl7.fhir.r5.core#4.5.0",
          "hl7.fhir.uv.tools.r5#9.9.9", "hl7.terminology.r5#9.9.9"), true);
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should support prerelease FHIR R5 snapshot dependencies")
    void r5Snapshot() {
      fhirVersionCase("5.0.0-snapshot1", 1 + NUM_R5_AUTO_DEPENDENCIES, List.of("hl7.fhir.r5.core#5.0.0-snapshot1",
          "hl7.fhir.uv.tools.r5#9.9.9", "hl7.terminology.r5#9.9.9"), true);
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should support official FHIR R5 dependency")
    void r5() {
      fhirVersionCase("5.0.0", 1 + NUM_R5_AUTO_DEPENDENCIES, List.of("hl7.fhir.r5.core#5.0.0",
          "hl7.fhir.uv.tools.r5#9.9.9", "hl7.terminology.r5#9.9.9", "hl7.fhir.uv.extensions.r5#9.9.9"), false);
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should support FHIR current dependencies")
    void current() {
      fhirVersionCase("current", 1 + NUM_R5_AUTO_DEPENDENCIES, List.of("hl7.fhir.r5.core#current",
          "hl7.fhir.uv.tools.r5#9.9.9", "hl7.terminology.r5#9.9.9", "hl7.fhir.uv.extensions.r5#9.9.9"), true);
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should switch old-style cross-version FHIR extensions package to new xver package")
    void oldXver() {
      Configuration config = minimalConfig();
      config.fhirVersion = new ArrayList<>(List.of("5.0.0"));
      config.dependencies = deps(
          dep("hl7.fhir.extensions.r2", "5.0.0"),
          dep("hl7.fhir.extensions.r3", "5.0.0"),
          dep("hl7.fhir.extensions.r4", "5.0.0"),
          dep("hl7.fhir.extensions.r4b", "5.0.0"));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(5 + NUM_R5_AUTO_DEPENDENCIES, loaded.size(), loaded::toString);
      assertTrue(loaded.contains("hl7.fhir.uv.xver-r2.r5#9.9.9"));
      assertTrue(loaded.contains("hl7.fhir.uv.xver-r3.r5#9.9.9"));
      assertTrue(loaded.contains("hl7.fhir.uv.xver-r4.r5#9.9.9"));
      assertTrue(loaded.contains("hl7.fhir.uv.xver-r4b.r5#9.9.9"));
      assertAutomaticR5Dependencies(loaded);
      depMatches("Found old-style .+ hl7\\.fhir\\.extensions\\.r2\\..+official .+ hl7\\.fhir\\.uv\\.xver-r2\\.r5\\.",
          loggerSpy.getMessageAtIndex(-4, "warn"));
      depMatches("Found old-style .+ hl7\\.fhir\\.extensions\\.r3\\..+official .+ hl7\\.fhir\\.uv\\.xver-r3\\.r5\\.",
          loggerSpy.getMessageAtIndex(-3, "warn"));
      depMatches("Found old-style .+ hl7\\.fhir\\.extensions\\.r4\\..+official .+ hl7\\.fhir\\.uv\\.xver-r4\\.r5\\.",
          loggerSpy.getMessageAtIndex(-2, "warn"));
      depMatches("Found old-style .+ hl7\\.fhir\\.extensions\\.r4b\\..+official .+ hl7\\.fhir\\.uv\\.xver-r4b\\.r5\\.",
          loggerSpy.getMessageAtIndex(-1, "warn"));
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should log a warning when an NPM alias uses a character not valid for FHIR ids")
    void npmAliasInvalid() {
      // See: https://chat.fhir.org/#narrow/channel/179239-tooling/topic/NPM.20Aliases/near/518051795
      Configuration config = minimalConfig();
      config.dependencies = deps(
          dep("hl7.fhir.us.mcode", "4.0.0"),
          dep("hl7.fhir.us.core", "7.0.0"),
          dep("v610!@npm:hl7.fhir.us.core", "6.1.0"),
          dep("hl7.fhir.us.qicore", "6.0.0"),
          dep("v311@npm:hl7.fhir.us.core", "3.1.1"));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(6 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      // Check that the package w/ the invalid alias was still loaded anyway
      assertTrue(loaded.contains("hl7.fhir.us.core#6.1.0"));
      depMatches("NPM aliases should .+\\. Found 'v610!'\\.", loggerSpy.getLastMessage("warn"));
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should log an error when it fails to load a dependency")
    void failsToLoad() {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("hl7.does.not.exist", "current"));
      FHIRDefinitions defs = defsWithFailingCurrentDownload("hl7.does.not.exist");
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(1 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      assertTrue(loaded.contains("hl7.fhir.r4.core#4.0.1"));
      assertAutomaticR4Dependencies(loaded);
      depMatches("Failed to load hl7\\.does\\.not\\.exist#current", loggerSpy.getLastMessage("error"));
      // But don't log the error w/ details about proxies
      depNotMatches("SSL", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should log a more detailed error when it fails to load a dependency due to certificate issue")
    void certificate() {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("self-signed.package", "1.0.0"));
      FHIRDefinitions defs = defsWithFailingDownload("self-signed.package", "1.0.0",
          "self signed certificate in certificate chain");
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(1 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      assertTrue(loaded.contains("hl7.fhir.r4.core#4.0.1"));
      assertAutomaticR4Dependencies(loaded);
      depMatches("Failed to load self-signed\\.package#1\\.0\\.0", loggerSpy.getLastMessage("error"));
      // AND it should log the detailed message about SSL
      depMatches("Sometimes this error occurs in corporate or educational environments that use proxies and/or "
          + "SSL inspection", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("Processing #loadExternalDependencies() should log an error when a dependency has no specified version")
    void noVersion() {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("hl7.fhir.r4.core", null));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadExternalDependencies(defs, config);
      List<String> loaded = loadedPackages(defs);
      assertEquals(1 + NUM_R4_AUTO_DEPENDENCIES, loaded.size());
      assertTrue(loaded.contains("hl7.fhir.r4.core#4.0.1"));
      assertAutomaticR4Dependencies(loaded);
      depMatches("Failed to load hl7\\.fhir\\.r4\\.core: No version specified\\.", loggerSpy.getLastMessage("error"));
      depNotMatches("SSL", loggerSpy.getLastMessage("error"));
    }
  }

  @Nested
  @DisplayName("#loadAutomaticDependencies")
  class LoadAutomaticDependencies {
    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
    }

    void eachAuto(String fhirVersion, String rx, boolean r5forR4) {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("hl7.fhir.us.core", "3.1.0"));
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadAutomaticDependencies(fhirVersion, config.dependencies, defs, AutomaticDependencyPriority.Low);
      List<String> loaded = loadedPackages(defs);
      assertEquals(r5forR4 ? 3 : 2, loaded.size(), loaded::toString);
      if (r5forR4) assertTrue(loaded.contains("sushi-r5forR4#1.0.0"));
      assertTrue(loaded.contains("hl7.fhir.uv.tools." + rx + "#9.9.9"));
      assertTrue(loaded.contains("hl7.terminology." + rx + "#9.9.9"));
      assertFalse(loaded.contains("hl7.fhir.uv.extensions." + rx + "#9.9.9"));
      Processing.loadAutomaticDependencies(fhirVersion, config.dependencies, defs, AutomaticDependencyPriority.High);
      loaded = loadedPackages(defs);
      assertEquals(r5forR4 ? 4 : 3, loaded.size(), loaded.toString());
      assertTrue(loaded.contains("hl7.fhir.uv.extensions." + rx + "#9.9.9"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("Processing #loadAutomaticDependencies should load each automatic dependency for FHIR R4")
    void r4() {
      eachAuto("4.0.1", "r4", true);
    }

    @Test
    @DisplayName("Processing #loadAutomaticDependencies should load each automatic dependency for FHIR R4B")
    void r4b() {
      eachAuto("4.3.0", "r4", true);
    }

    @Test
    @DisplayName("Processing #loadAutomaticDependencies should load each automatic dependency for FHIR R5 Final Draft")
    void r5FinalDraft() {
      eachAuto("5.0.0-draft-final", "r5", false);
    }

    @Test
    @DisplayName("Processing #loadAutomaticDependencies should load each automatic dependency for FHIR R5")
    void r5() {
      eachAuto("5.0.0", "r5", false);
    }

    @Test
    @DisplayName("Processing #loadAutomaticDependencies should load each automatic dependency for FHIR R6 prerelease")
    void r6Pre() {
      eachAuto("6.0.0-ballot2", "r5", false);
    }

    List<String> lowWith(List<JsObject> deps) {
      Configuration config = minimalConfig();
      config.dependencies = deps;
      FHIRDefinitions defs = getTestFHIRDefinitions();
      Processing.loadAutomaticDependencies(config.fhirVersion.get(0), config.dependencies, defs,
          AutomaticDependencyPriority.Low);
      return loadedPackages(defs);
    }

    @Test
    @DisplayName("Processing #loadAutomaticDependencies should load configured versions for dependencies that are present in the config")
    void configured() {
      List<String> loaded = lowWith(deps(dep("hl7.fhir.uv.tools.r4", "2.2.0-test")));
      assertEquals(NUM_R4_AUTO_DEPENDENCIES_LOW, loaded.size());
      assertTrue(loaded.contains("hl7.fhir.uv.tools.r4#2.2.0-test"));
      assertFalse(loaded.contains("hl7.fhir.uv.tools.r4#9.9.9"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("Processing #loadAutomaticDependencies should load multiple configured versions for dependencies that are present in the config")
    void multipleConfigured() {
      List<String> loaded = lowWith(deps(dep("hl7.fhir.uv.tools.r4", "2.0.0"), dep("hl7.fhir.uv.tools.r4", "2.2.0-test")));
      assertEquals(NUM_R4_AUTO_DEPENDENCIES_LOW + 1, loaded.size());
      assertTrue(loaded.contains("hl7.fhir.uv.tools.r4#2.0.0"));
      assertTrue(loaded.contains("hl7.fhir.uv.tools.r4#2.2.0-test"));
      assertFalse(loaded.contains("hl7.fhir.uv.tools.r4#9.9.9"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("Processing #loadAutomaticDependencies should load configured versions for dependencies that are present in the config even if they do not have an r{x} suffix and the auto dependency does")
    void noSuffix() {
      List<String> loaded = lowWith(deps(dep("hl7.terminology", "4.0.0-test")));
      assertEquals(NUM_R4_AUTO_DEPENDENCIES_LOW, loaded.size());
      assertTrue(loaded.contains("hl7.terminology#4.0.0-test"));
      assertFalse(loaded.contains("hl7.terminology.r4#1.2.3-test"));
      assertFalse(loaded.contains("hl7.terminology.r4#latest"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("Processing #loadAutomaticDependencies should load configured versions for dependencies that are present in the config even if they do have an r{x} suffix that does not match the auto dependency r{x} suffix")
    void otherSuffix() {
      List<String> loaded = lowWith(deps(dep("hl7.terminology.r5", "4.0.0-test")));
      assertEquals(NUM_R4_AUTO_DEPENDENCIES_LOW, loaded.size());
      assertTrue(loaded.contains("hl7.terminology.r5#4.0.0-test"));
      assertFalse(loaded.contains("hl7.terminology.r4#1.2.3-test"));
      assertFalse(loaded.contains("hl7.terminology.r4#latest"));
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
    }

    @Test
    @DisplayName("Processing #loadAutomaticDependencies should log a warning when it fails to load an automatic dependency")
    void failsToLoad() {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("hl7.fhir.us.core", "3.1.0"));
      FHIRDefinitions defs = defsWithFailingDownload("hl7.fhir.uv.tools.r4", "9.9.9", null);
      Processing.loadAutomaticDependencies(config.fhirVersion.get(0), config.dependencies, defs,
          AutomaticDependencyPriority.Low);
      List<String> loaded = loadedPackages(defs);
      assertEquals(NUM_R4_AUTO_DEPENDENCIES_LOW - 1, loaded.size(), loaded::toString);
      assertFalse(loaded.contains("hl7.fhir.uv.tools.r4#9.9.9"));
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      depMatches("Failed to load automatically-provided hl7\\.fhir\\.uv\\.tools\\.r4#latest",
          loggerSpy.getLastMessage("warn"));
      // But don't log the warning w/ details about proxies
      depNotMatches("SSL", loggerSpy.getLastMessage("warn"));
      // And don't log an error
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("Processing #loadAutomaticDependencies should log a warning when it fails to find the latest version of an automatic dependency due to an error")
    void failsToResolve() {
      Configuration config = minimalConfig();
      config.dependencies = deps(dep("hl7.fhir.us.core", "3.1.0"));
      FHIRDefinitions defs = defsWithFailingResolveLatest("hl7.terminology.r4");
      Processing.loadAutomaticDependencies(config.fhirVersion.get(0), config.dependencies, defs,
          AutomaticDependencyPriority.Low);
      List<String> loaded = loadedPackages(defs);
      assertEquals(NUM_R4_AUTO_DEPENDENCIES_LOW - 1, loaded.size(), loaded::toString);
      assertFalse(loaded.contains("hl7.terminology.r4#4.0.0"));
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      depMatches("Failed to load automatically-provided hl7\\.terminology\\.r4#latest",
          loggerSpy.getLastMessage("warn"));
      depNotMatches("SSL", loggerSpy.getLastMessage("warn"));
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }
  }

  @Nested
  @DisplayName("#getRawFSHes()")
  class GetRawFSHes {
    static List<JsObject> asJs(List<RawFSH> raws) {
      return raws.stream().map(r -> obj("content", r.content, "path", r.path)).toList();
    }

    @Test
    @DisplayName("Processing #getRawFSHes() should return a RawFSH for each file in the input directory that ends with .fsh")
    void shouldReturnARawFSHForEachFileInTheInputDirectoryThatEndsWithFsh() {
      String input = path.join(__dirname, "fixtures", "fsh-files");
      List<RawFSH> rawFSHes = getRawFSHes(input);
      assertEquals(2, rawFSHes.size());
      assertContainsEqual(obj("content", "// Content of first file", "path", path.join(input, "first.fsh")),
          asJs(rawFSHes));
      assertContainsEqual(obj("content", "// Content of second file", "path", path.join(input, "second.fsh")),
          asJs(rawFSHes));
    }

    @Test
    @DisplayName("Processing #getRawFSHes() should log and throw an error when the input path is invalid")
    void shouldLogAndThrowAnErrorWhenTheInputPathIsInvalid() {
      String input = path.join(__dirname, "fixtures", "wrong-path");
      assertThrows(RuntimeException.class, () -> getRawFSHes(input));
      JsAssert.assertMatches("Invalid path to FSH definition folder\\.", "s", loggerSpy.getLastMessage("error"));
    }

    @Nested
    @DisplayName("#symlinks")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Symlinks {
      final String linkPath = path.join(__dirname, "fixtures", "fsh-files", "myLock.fsh");
      final String linkTarget = path.join(__dirname, "fixtures", "fsh-files", "meaninglessTarget");

      @BeforeAll
      void beforeAll() {
        try {
          fs.remove(linkPath);
        } catch (RuntimeException e) {
          // This will fail if the link doesn't exist, which is fine.
        }
        try {
          fs.symlinkSync(linkTarget, linkPath);
        } catch (RuntimeException e) {
          // This may fail if the user running the test doesn't have permission to create a symbolic link.
        }
      }

      @Test
      @DisplayName("Processing #getRawFSHes() #symlinks should return a RawFSH for each real file in the input directory that ends with .fsh")
      void shouldReturnARawFSHForEachRealFileInTheInputDirectoryThatEndsWithFsh() {
        String input = path.join(__dirname, "fixtures", "fsh-files");
        List<RawFSH> rawFSHes = getRawFSHes(input);
        assertEquals(2, rawFSHes.size());
        assertContainsEqual(obj("content", "// Content of first file", "path", path.join(input, "first.fsh")),
            asJs(rawFSHes));
        assertContainsEqual(obj("content", "// Content of second file", "path", path.join(input, "second.fsh")),
            asJs(rawFSHes));
      }

      @AfterAll
      void afterAll() {
        try {
          fs.remove(linkPath);
        } catch (RuntimeException e) {
          // This will fail if someone removed the link in the middle of the test.
        }
      }
    }
  }

  @Nested
  @DisplayName("#hasFshFiles()")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class HasFshFiles {
    String tempRoot;

    @BeforeAll
    void beforeAll() {
      tempRoot = temp.mkdirSync("sushi-test");
      fs.mkdirSync(path.join(tempRoot, "some-fsh"));
      fs.mkdirSync(path.join(tempRoot, "some-fsh", "nested"));
      fs.ensureFileSync(path.join(tempRoot, "some-fsh", "notfsh.txt"));
      fs.ensureFileSync(path.join(tempRoot, "some-fsh", "nested", "myfsh.fsh"));
      fs.mkdirSync(path.join(tempRoot, "no-fsh"));
      fs.mkdirSync(path.join(tempRoot, "no-fsh", "nested"));
      fs.ensureFileSync(path.join(tempRoot, "no-fsh", "notfsh.txt"));
      fs.ensureFileSync(path.join(tempRoot, "no-fsh", "nested", "notfsh.txt"));
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("Processing #hasFshFiles() should return true if FSH files exist in any subdirectory")
    void shouldReturnTrueIfFSHFilesExistInAnySubdirectory() {
      boolean result = hasFshFiles(path.join(tempRoot, "some-fsh"));
      assertEquals(true, result);
    }

    @Test
    @DisplayName("Processing #hasFshFiles() should return false if there are no FSH files in any subdirectory")
    void shouldReturnFalseIfThereAreNoFSHFilesInAnySubdirectory() {
      boolean result = hasFshFiles(path.join(tempRoot, "no-fsh"));
      assertEquals(false, result);
    }
  }

  @Nested
  @DisplayName("#checkNullValuesOnArray")
  class CheckNullValuesOnArray {
    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
    }

    InstanceDefinition exInstance() {
      InstanceDefinition exInstance = new InstanceDefinition();
      setProp(exInstance, "resourceType", "Profile");
      setProp(exInstance, "id", "example-instance");
      return exInstance;
    }

    @Test
    @DisplayName("Processing #checkNullValuesOnArray should log a warning when a resource includes null values in a top-level array")
    void topLevelArray() {
      InstanceDefinition exInstance = exInstance();
      setProp(exInstance, "name", JsAssert.json("[null, \"John\", null, \"Doe\", null]"));
      checkNullValuesOnArray(exInstance);
      assertEquals(1, loggerSpy.getAllMessages().size());
      assertEquals("The array 'name' in example-instance is missing values at the following indices: 0,2,4",
          loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("Processing #checkNullValuesOnArray should log a warning when a resource includes null values in a nested array")
    void nestedArray() {
      InstanceDefinition exInstance = exInstance();
      Object nameObj = JsAssert.json("{\"testNames\": [null, \"John\", null, \"Doe\", null]}");
      setProp(exInstance, "name", nameObj);
      checkNullValuesOnArray(exInstance);
      assertEquals(1, loggerSpy.getAllMessages().size());
      assertEquals(
          "The array 'name.testNames' in example-instance is missing values at the following indices: 0,2,4",
          loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("Processing #checkNullValuesOnArray should log a warning when a resource includes null values in an array, nested within both objects and arrays ")
    void nestedInObjectsAndArrays() {
      InstanceDefinition exInstance = exInstance();
      Object nameObj = JsAssert.json("""
          { "foo": [ { "coding": [ null, { "system": "http://foo.org", "code": "bar" } ] } ] }
          """);
      setProp(exInstance, "name", nameObj);
      checkNullValuesOnArray(exInstance);
      assertEquals(1, loggerSpy.getAllMessages().size());
      assertEquals(
          "The array 'name.foo[0].coding' in example-instance is missing values at the following indices: 0",
          loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("Processing #checkNullValuesOnArray should ignore null values on primitive arrays, but warn on null values in nested arrays")
    void primitiveArrays() {
      InstanceDefinition exInstance = exInstance();
      Object nameObj = JsAssert.json("""
          { "_foo": [ null, { "extension": [ null, { "url": "http://foo.org", "valueBoolean": true } ] } ] }
          """);
      setProp(exInstance, "name", nameObj);
      checkNullValuesOnArray(exInstance);
      assertEquals(1, loggerSpy.getAllMessages().size());
      assertEquals(
          "The array 'name._foo[1].extension' in example-instance is missing values at the following indices: 0",
          loggerSpy.getLastMessage("warn"));
    }

    @Test
    @DisplayName("Processing #checkNullValuesOnArray should not log a warning when a resource includes null values in an array prefixed with an underscore")
    void underscoreArray() {
      InstanceDefinition exInstance = exInstance();
      setProp(exInstance, "_name", JsAssert.json("[null, \"John\", null, \"Doe\", null]"));
      checkNullValuesOnArray(exInstance);
      assertEquals(0, loggerSpy.getAllMessages().size());
    }

    @Test
    @DisplayName("Processing #checkNullValuesOnArray should log a warning when a resource includes null values in an array nested within an object prefixed with an underscore")
    void arrayInUnderscoreObject() {
      InstanceDefinition exInstance = exInstance();
      Object nameObj = JsAssert.json("{\"testNames\": [null, \"John\", null, \"Doe\", null]}");
      setProp(exInstance, "_name", nameObj);
      checkNullValuesOnArray(exInstance);
      assertEquals(1, loggerSpy.getAllMessages().size());
      assertEquals(
          "The array '_name.testNames' in example-instance is missing values at the following indices: 0,2,4",
          loggerSpy.getLastMessage("warn"));
    }
  }

  @Nested
  @DisplayName("#writeFHIRResources()")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @TestClassOrder(ClassOrderer.OrderAnnotation.class)
  class WriteFHIRResources {
    String tempIGPubRoot;
    Configuration config;
    Package outPackage;
    FHIRDefinitions defs;

    @BeforeAll
    void beforeAll() {
      tempIGPubRoot = temp.mkdirSync("output-ig-dir");
      String input = path.join(__dirname, "fixtures", "valid-yaml");
      config = readConfig(input);
      defs = getTestFHIRDefinitions();

      Map<String, Object> predefinedResourceMap = new LinkedHashMap<>();
      StructureDefinition myPredefinedProfile = new StructureDefinition();
      myPredefinedProfile.setId("my-duplicate-profile");
      myPredefinedProfile.setUrl("http://example.com/StructureDefinition/my-duplicate-profile");
      predefinedResourceMap.put("my-duplicate-profile", myPredefinedProfile.toJSON(true));
      InstanceDefinition myPredefinedInstance = new InstanceDefinition();
      myPredefinedInstance.setId("my-duplicate-instance");
      setProp(myPredefinedInstance, "resourceType", "Patient");
      predefinedResourceMap.put("my-duplicate-instance", myPredefinedInstance.toJSON());
      InMemoryVirtualPackage predefinedPkg = new InMemoryVirtualPackage(
          InMemoryVirtualPackage.packageJSON(FHIRDefinitions.PREDEFINED_PACKAGE_NAME,
              FHIRDefinitions.PREDEFINED_PACKAGE_VERSION),
          predefinedResourceMap, TestFHIRDefinitions.LOG_MESSAGE, true);
      defs.loadVirtualPackage(predefinedPkg);

      outPackage = buildPackage(config, "");
    }

    /**
     * The TS package; idPrefix "" is the original, "/../../devious/" the TS cloneDeep(outPackage) with every id
     * prefixed (StructureDefinition is not DeepCloneable in the Java port, so the copy is built afresh).
     */
    Package buildPackage(Configuration config, String idPrefix) {
      Package pkg = new Package(config);
      StructureDefinition myProfile = new StructureDefinition();
      myProfile.setId(idPrefix + "my-profile");
      StructureDefinition myExtension = new StructureDefinition();
      myExtension.setId(idPrefix + "my-extension");
      StructureDefinition myLogical = new StructureDefinition();
      myLogical.setId(idPrefix + "my-logical");
      StructureDefinition myResource = new StructureDefinition();
      myResource.setId(idPrefix + "my-resource");
      ValueSet myValueSet = new ValueSet();
      myValueSet.setId(idPrefix + "my-value-set");
      CodeSystem myCodeSystem = new CodeSystem();
      myCodeSystem.setId(idPrefix + "my-code-system");

      InstanceDefinition myInlineInstance = new InstanceDefinition();
      myInlineInstance.setId(idPrefix + "my-inline-instance");
      myInlineInstance._instanceMeta.usage = "Inline";
      InstanceDefinition myExampleInstance = new InstanceDefinition();
      myExampleInstance.setId(idPrefix + "my-example");
      setProp(myExampleInstance, "resourceType", "Observation");
      myExampleInstance._instanceMeta.usage = "Example";
      InstanceDefinition myCapabilityStatement = new InstanceDefinition();
      myCapabilityStatement.setId(idPrefix + "my-capabilities");
      setProp(myCapabilityStatement, "resourceType", "CapabilityStatement");
      InstanceDefinition myConceptMap = new InstanceDefinition();
      myConceptMap.setId(idPrefix + "my-concept-map");
      setProp(myConceptMap, "resourceType", "ConceptMap");
      InstanceDefinition myModelInstance = new InstanceDefinition();
      myModelInstance.setId(idPrefix + "my-model");
      setProp(myModelInstance, "resourceType", "StructureDefinition");
      setProp(myModelInstance, "kind", "logical");
      InstanceDefinition myOperationDefinition = new InstanceDefinition();
      myOperationDefinition.setId(idPrefix + "my-operation");
      setProp(myOperationDefinition, "resourceType", "OperationDefinition");
      InstanceDefinition myExtensionInstance = new InstanceDefinition();
      myExtensionInstance.setId(idPrefix + "my-extension-instance");
      setProp(myExtensionInstance, "resourceType", "StructureDefinition");
      setProp(myExtensionInstance, "kind", "resource");
      setProp(myExtensionInstance, "type", "Extension");
      InstanceDefinition myProfileInstance = new InstanceDefinition();
      myProfileInstance.setId(idPrefix + "my-profile-instance");
      setProp(myProfileInstance, "resourceType", "StructureDefinition");
      setProp(myProfileInstance, "kind", "resource");
      setProp(myProfileInstance, "type", "Observation");
      InstanceDefinition myOtherInstance = new InstanceDefinition();
      myOtherInstance.setId(idPrefix + "my-other-instance");
      setProp(myOtherInstance, "resourceType", "Observation");
      InstanceDefinition myInstanceOfLogical = new InstanceDefinition();
      myInstanceOfLogical.setId(idPrefix + "my-instance-of-logical");
      setProp(myInstanceOfLogical, "resourceType", "http://example.com/StructureDefinition/some-logical");
      myInstanceOfLogical._instanceMeta.sdType = "http://example.com/StructureDefinition/some-logical";
      myInstanceOfLogical._instanceMeta.sdKind = "logical";

      // duplicate of predefined resource added earlier
      StructureDefinition myFSHDefinedProfile = new StructureDefinition();
      myFSHDefinedProfile.setId(idPrefix + "my-duplicate-profile");
      myFSHDefinedProfile.setUrl("http://example.com/StructureDefinition/my-duplicate-profile");
      // duplicate of predefined resource added earlier
      InstanceDefinition myFSHDefinedInstance = new InstanceDefinition();
      myFSHDefinedInstance.setId(idPrefix + "my-duplicate-instance");
      setProp(myFSHDefinedInstance, "resourceType", "Patient");

      pkg.profiles.addAll(List.of(myProfile, myFSHDefinedProfile));
      pkg.extensions.add(myExtension);
      pkg.logicals.add(myLogical);
      pkg.resources.add(myResource);
      pkg.valueSets.add(myValueSet);
      pkg.codeSystems.add(myCodeSystem);
      pkg.instances.addAll(List.of(myInlineInstance, myExampleInstance, myCapabilityStatement, myConceptMap,
          myModelInstance, myOperationDefinition, myExtensionInstance, myProfileInstance, myOtherInstance,
          myInstanceOfLogical, myFSHDefinedInstance));
      return pkg;
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Nested
    @Order(1)
    @DisplayName("return value")
    class ReturnValue {
      @AfterEach
      void afterEach() {
        temp.cleanupSync();
      }

      @Test
      @DisplayName("Processing #writeFHIRResources() return value should return a list of filenames for skipped resources")
      void shouldReturnAListOfFilenamesForSkippedResources() {
        List<String> skippedResources = writeFHIRResources(tempIGPubRoot, outPackage, defs, false).skippedResources();
        assertEquals(2, skippedResources.size());
        assertContains("StructureDefinition-my-duplicate-profile.json", skippedResources);
        assertContains("Patient-my-duplicate-instance.json", skippedResources);
      }
    }

    @Nested
    @Order(2)
    @DisplayName("IG Publisher mode")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class IGPublisherMode {
      @BeforeAll
      void beforeAll() {
        writeFHIRResources(tempIGPubRoot, outPackage, defs, false);
      }

      @AfterAll
      void afterAll() {
        temp.cleanupSync();
      }

      @Test
      @Order(1)
      @DisplayName("Processing #writeFHIRResources() IG Publisher mode should write all resources to the \"fsh-generated/resources\" directory")
      void shouldWriteAllResourcesToTheFshGeneratedResourcesDirectory() {
        String generatedPath = path.join(tempIGPubRoot, "fsh-generated", "resources");
        assertTrue(fs.existsSync(generatedPath));
        List<String> allGeneratedFiles = fs.readdirSync(generatedPath);
        assertEquals(15, allGeneratedFiles.size());
        assertContains("StructureDefinition-my-profile.json", allGeneratedFiles);
        assertContains("StructureDefinition-my-profile-instance.json", allGeneratedFiles);
        assertContains("StructureDefinition-my-extension.json", allGeneratedFiles);
        assertContains("StructureDefinition-my-extension-instance.json", allGeneratedFiles);
        assertContains("StructureDefinition-my-logical.json", allGeneratedFiles);
        assertContains("StructureDefinition-my-resource.json", allGeneratedFiles);
        assertContains("ValueSet-my-value-set.json", allGeneratedFiles);
        assertContains("CodeSystem-my-code-system.json", allGeneratedFiles);
        assertContains("ConceptMap-my-concept-map.json", allGeneratedFiles);
        assertContains("Observation-my-example.json", allGeneratedFiles);
        assertContains("CapabilityStatement-my-capabilities.json", allGeneratedFiles);
        assertContains("StructureDefinition-my-model.json", allGeneratedFiles);
        assertContains("OperationDefinition-my-operation.json", allGeneratedFiles);
        assertContains("Observation-my-other-instance.json", allGeneratedFiles);
        // NOTE: Instances of logicals are always output as Binary-{id} regardless of type
        assertContains("Binary-my-instance-of-logical.json", allGeneratedFiles);
        JsAssert.assertMatches("Exported 15 FHIR resources", "s", loggerSpy.getLastMessage("info"));
      }

      @Test
      @Order(2)
      @DisplayName("Processing #writeFHIRResources() IG Publisher mode should not allow devious characters in the resource file names")
      void shouldNotAllowDeviousCharactersInTheResourceFileNames() {
        String tempDeviousIGPubRoot = temp.mkdirSync("output-ig-dir");
        Package deviousOutPackage = buildPackage(config, "/../../devious/");
        writeFHIRResources(tempDeviousIGPubRoot, deviousOutPackage, defs, false);
        // Make sure we didn't create the devious path
        String deviousPath = path.join(tempDeviousIGPubRoot, "fsh-generated", "devious");
        assertEquals(false, fs.existsSync(deviousPath));
        // Make sure we do have all the good file names
        String angelicPath = path.join(tempDeviousIGPubRoot, "fsh-generated", "resources");
        assertTrue(fs.existsSync(angelicPath));
        List<String> allAngelicFiles = fs.readdirSync(angelicPath);
        assertEquals(17, allAngelicFiles.size());
        assertContains("CapabilityStatement--..-..-devious-my-capabilities.json", allAngelicFiles);
        assertContains("CodeSystem--..-..-devious-my-code-system.json", allAngelicFiles);
        assertContains("ConceptMap--..-..-devious-my-concept-map.json", allAngelicFiles);
        assertContains("Observation--..-..-devious-my-example.json", allAngelicFiles);
        assertContains("Observation--..-..-devious-my-other-instance.json", allAngelicFiles);
        // NOTE: Instances of logicals are always output as Binary-{id} regardless of type
        assertContains("Binary--..-..-devious-my-instance-of-logical.json", allAngelicFiles);
        assertContains("OperationDefinition--..-..-devious-my-operation.json", allAngelicFiles);
        assertContains("Patient--..-..-devious-my-duplicate-instance.json", allAngelicFiles);
        assertContains("StructureDefinition--..-..-devious-my-duplicate-profile.json", allAngelicFiles);
        assertContains("StructureDefinition--..-..-devious-my-extension-instance.json", allAngelicFiles);
        assertContains("StructureDefinition--..-..-devious-my-extension.json", allAngelicFiles);
        assertContains("StructureDefinition--..-..-devious-my-logical.json", allAngelicFiles);
        assertContains("StructureDefinition--..-..-devious-my-model.json", allAngelicFiles);
        assertContains("StructureDefinition--..-..-devious-my-profile-instance.json", allAngelicFiles);
        assertContains("StructureDefinition--..-..-devious-my-profile.json", allAngelicFiles);
        assertContains("StructureDefinition--..-..-devious-my-resource.json", allAngelicFiles);
        assertContains("ValueSet--..-..-devious-my-value-set.json", allAngelicFiles);
        JsAssert.assertMatches("Exported 17 FHIR resources", "s", loggerSpy.getLastMessage("info"));
      }

      @Test
      @Order(3)
      @DisplayName("Processing #writeFHIRResources() IG Publisher mode should not write a resource if that resource already exists in the \"input\" folder")
      void shouldNotWriteAResourceIfThatResourceAlreadyExistsInTheInputFolder() {
        assertFalse(fs.existsSync(path.join(tempIGPubRoot, "fsh-generated", "resources",
            "StructureDefinition-my-duplicate-profile.json")));
        assertTrue(loggerSpy.getAllMessages("error").stream()
            .anyMatch(error -> Pattern.compile("Ignoring FSH definition for .*my-duplicate-profile").matcher(error)
                .find()));
        assertFalse(fs.existsSync(path.join(tempIGPubRoot, "fsh-generated", "resources",
            "Patient-my-duplicate-instance.json")));
        assertTrue(loggerSpy.getAllMessages("error").stream()
            .anyMatch(error -> Pattern.compile("Ignoring FSH definition for .*my-duplicate-instance").matcher(error)
                .find()));
      }
    }
  }

  @Nested
  @DisplayName("#writeFSHIndex()")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class WriteFSHIndex {
    String tempIGPubRoot;
    String fshRoot;
    Package outPackage;

    @BeforeAll
    void beforeAll() {
      tempIGPubRoot = temp.mkdirSync("output-ig-dir");
      String input = path.join(__dirname, "fixtures", "valid-yaml");
      fshRoot = path.join(input, "good-ig", "input", "fsh");
      Configuration config = readConfig(input);
      outPackage = new Package(config);
      outPackage.fshMap.put("StructureDefinition-my-profile.json", new FshMapEntry(
          new SourceInfo(path.join(fshRoot, "SomeThings.fsh"), new TextLocation(1, 1, 5, 28)), "MyProfile",
          "Profile"));
      outPackage.fshMap.put("StructureDefinition-my-extension.json", new FshMapEntry(
          new SourceInfo(path.join(fshRoot, "SomeThings.fsh"), new TextLocation(7, 1, 15, 23)), "MyExtension",
          "Extension"));
      outPackage.fshMap.put("Questionnaire-my-questions.json", new FshMapEntry(
          new SourceInfo(path.join(fshRoot, "Questions.fsh"), new TextLocation(2, 1, 28, 28)), "MyQuestions",
          "Instance"));
      outPackage.fshMap.put("Observation-secret-observation.json", new FshMapEntry(
          new SourceInfo(path.join(fshRoot, "secrets", "SecretThings.fsh"), new TextLocation(2, 1, 39, 55)),
          "SecretObservation", "Instance"));
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("Processing #writeFSHIndex() should create text and json index files that contain each resource in the package")
    void shouldCreateTextAndJsonIndexFilesThatContainEachResourceInThePackage() {
      writeFSHIndex(tempIGPubRoot, outPackage, fshRoot, new ArrayList<>());
      String textIndex = path.join(tempIGPubRoot, "fsh-generated", "fsh-index.txt");
      String jsonIndex = path.join(tempIGPubRoot, "fsh-generated", "data", "fsh-index.json");
      assertEquals(true, fs.existsSync(textIndex));
      assertEquals(true, fs.existsSync(jsonIndex));
      String textContents = fs.readFileSync(textIndex, "utf-8");
      JsAssert.assertMatches(
          "StructureDefinition-my-profile\\.json\\s+MyProfile\\s+Profile\\s+SomeThings\\.fsh\\s+1 - 5", "s",
          textContents);
      JsAssert.assertMatches(
          "StructureDefinition-my-extension\\.json\\s+MyExtension\\s+Extension\\s+SomeThings\\.fsh\\s+7 - 15", "s",
          textContents);
      JsAssert.assertMatches(
          "Questionnaire-my-questions\\.json\\s+MyQuestions\\s+Instance\\s+Questions\\.fsh\\s+2 - 28", "s",
          textContents);
      Pattern secretRegex = jsRegex("Observation-secret-observation\\.json\\s+SecretObservation\\s+Instance\\s+secrets\\"
          + path.sep + "SecretThings\\.fsh\\s+2 - 39", "s");
      assertMatchesPattern(secretRegex, textContents);
      Object jsonContents = fs.readJsonSync(jsonIndex);
      assertEquals(4, len(jsonContents));
      assertContainsEqual(JsAssert.json("""
          {
            "fshFile": "SomeThings.fsh",
            "fshName": "MyProfile",
            "fshType": "Profile",
            "startLine": 1,
            "endLine": 5,
            "outputFile": "StructureDefinition-my-profile.json"
          }
          """), jsonContents);
      assertContainsEqual(JsAssert.json("""
          {
            "fshFile": "SomeThings.fsh",
            "fshName": "MyExtension",
            "fshType": "Extension",
            "startLine": 7,
            "endLine": 15,
            "outputFile": "StructureDefinition-my-extension.json"
          }
          """), jsonContents);
      assertContainsEqual(JsAssert.json("""
          {
            "fshFile": "Questions.fsh",
            "fshName": "MyQuestions",
            "fshType": "Instance",
            "startLine": 2,
            "endLine": 28,
            "outputFile": "Questionnaire-my-questions.json"
          }
          """), jsonContents);
      assertContainsEqual(obj("fshFile", path.join("secrets", "SecretThings.fsh"), "fshName", "SecretObservation",
          "fshType", "Instance", "startLine", 2.0, "endLine", 39.0, "outputFile",
          "Observation-secret-observation.json"), jsonContents);
    }

    /**
     * Not in TS's suite (its CI runs on Linux): on Windows TS's path.relative is path.win32's, so the FSH file of a
     * resource in a subfolder prints as secrets\\SecretThings.fsh in both index files (core-divergences.md T6).
     */
    @Test
    @DisplayName("Processing #writeFSHIndex() uses Windows separators on Windows, as path.win32 does")
    void usesWindowsSeparatorsOnWindows() {
      boolean saved = NodePath.WINDOWS;
      NodePath.WINDOWS = true;
      try {
        writeFSHIndex(tempIGPubRoot, outPackage, fshRoot, new ArrayList<>());
      } finally {
        NodePath.WINDOWS = saved;
      }
      String textContents = fs.readFileSync(path.join(tempIGPubRoot, "fsh-generated", "fsh-index.txt"), "utf-8");
      assertContainsText("Instance   secrets\\SecretThings.fsh", textContents);
      assertContainsText("Profile    SomeThings.fsh", textContents);
      Object jsonContents = fs.readJsonSync(path.join(tempIGPubRoot, "fsh-generated", "data", "fsh-index.json"));
      assertContainsEqual(obj("fshFile", "secrets\\SecretThings.fsh", "fshName", "SecretObservation",
          "fshType", "Instance", "startLine", 2.0, "endLine", 39.0, "outputFile",
          "Observation-secret-observation.json"), jsonContents);
      String jsonText = fs.readFileSync(path.join(tempIGPubRoot, "fsh-generated", "data", "fsh-index.json"), "utf-8");
      assertContainsText("\"fshFile\": \"secrets\\\\SecretThings.fsh\"", jsonText);
      // and back on POSIX
      writeFSHIndex(tempIGPubRoot, outPackage, fshRoot, new ArrayList<>());
      assertContainsText("secrets/SecretThings.fsh",
          fs.readFileSync(path.join(tempIGPubRoot, "fsh-generated", "fsh-index.txt"), "utf-8"));
    }

    @Test
    @DisplayName("Processing #writeFSHIndex() should sort the list of resources by output file name")
    void shouldSortTheListOfResourcesByOutputFileName() {
      writeFSHIndex(tempIGPubRoot, outPackage, fshRoot, new ArrayList<>());
      String textIndex = path.join(tempIGPubRoot, "fsh-generated", "fsh-index.txt");
      String jsonIndex = path.join(tempIGPubRoot, "fsh-generated", "data", "fsh-index.json");
      assertEquals(true, fs.existsSync(textIndex));
      assertEquals(true, fs.existsSync(jsonIndex));
      String textContents = fs.readFileSync(textIndex, "utf-8");
      int myProfilePosition = indexOf(textContents, "StructureDefinition-my-profile.json");
      int myExtensionPosition = indexOf(textContents, "StructureDefinition-my-extension.json");
      int myQuestionsPosition = indexOf(textContents, "Questionnaire-my-questions.json");
      int secretObservationPosition = indexOf(textContents, "Observation-secret-observation.json");
      assertTrue(secretObservationPosition < myQuestionsPosition);
      assertTrue(myQuestionsPosition < myExtensionPosition);
      assertTrue(myExtensionPosition < myProfilePosition);
      Object jsonContents = fs.readJsonSync(jsonIndex);
      assertEquals("Observation-secret-observation.json", at(jsonContents, "[0].outputFile"));
      assertEquals("Questionnaire-my-questions.json", at(jsonContents, "[1].outputFile"));
      assertEquals("StructureDefinition-my-extension.json", at(jsonContents, "[2].outputFile"));
      assertEquals("StructureDefinition-my-profile.json", at(jsonContents, "[3].outputFile"));
    }

    @Test
    @DisplayName("Processing #writeFSHIndex() should not include a resource in the package if it is in the list of resources to skip")
    void shouldNotIncludeAResourceInThePackageIfItIsInTheListOfResourcesToSkip() {
      writeFSHIndex(tempIGPubRoot, outPackage, fshRoot,
          new ArrayList<>(Arrays.asList("StructureDefinition-my-extension.json")));
      String textIndex = path.join(tempIGPubRoot, "fsh-generated", "fsh-index.txt");
      String jsonIndex = path.join(tempIGPubRoot, "fsh-generated", "data", "fsh-index.json");
      assertEquals(true, fs.existsSync(textIndex));
      assertEquals(true, fs.existsSync(jsonIndex));
      String textContents = fs.readFileSync(textIndex, "utf-8");
      JsAssert.assertMatches(
          "StructureDefinition-my-profile\\.json\\s+MyProfile\\s+Profile\\s+SomeThings\\.fsh\\s+1 - 5", "s",
          textContents);
      JsAssert.assertMatches(
          "Questionnaire-my-questions\\.json\\s+MyQuestions\\s+Instance\\s+Questions\\.fsh\\s+2 - 28", "s",
          textContents);
      Pattern secretRegex = jsRegex("Observation-secret-observation\\.json\\s+SecretObservation\\s+Instance\\s+secrets\\"
          + path.sep + "SecretThings\\.fsh\\s+2 - 39", "s");
      assertMatchesPattern(secretRegex, textContents);
      assertNotContainsText("MyExtension", textContents);
      Object jsonContents = fs.readJsonSync(jsonIndex);
      assertEquals(3, len(jsonContents));
      assertContainsEqual(JsAssert.json("""
          {
            "fshFile": "SomeThings.fsh",
            "fshName": "MyProfile",
            "fshType": "Profile",
            "startLine": 1,
            "endLine": 5,
            "outputFile": "StructureDefinition-my-profile.json"
          }
          """), jsonContents);
      assertContainsEqual(JsAssert.json("""
          {
            "fshFile": "Questions.fsh",
            "fshName": "MyQuestions",
            "fshType": "Instance",
            "startLine": 2,
            "endLine": 28,
            "outputFile": "Questionnaire-my-questions.json"
          }
          """), jsonContents);
      assertContainsEqual(obj("fshFile", path.join("secrets", "SecretThings.fsh"), "fshName", "SecretObservation",
          "fshType", "Instance", "startLine", 2.0, "endLine", 39.0, "outputFile",
          "Observation-secret-observation.json"), jsonContents);
      assertNotContainsEqual(JsAssert.json("""
          {
            "fshFile": "SomeThings.fsh",
            "fshName": "MyExtension",
            "fshType": "Extension",
            "startLine": 7,
            "endLine": 15,
            "outputFile": "StructureDefinition-my-extension.json"
          }
          """), jsonContents);
    }
  }

  @Nested
  @DisplayName("#writePreprocessedFSH")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class WritePreprocessedFSH {
    FSHTank tank;
    String tempIn;
    String tempOut;

    @BeforeAll
    void beforeAll() {
      tempIn = temp.mkdirSync("input-dir");
      tempOut = temp.mkdirSync("output-dir");
      FSHDocument firstDoc = new FSHDocument(path.join(tempIn, "first.fsh"));
      firstDoc.aliases.put("LOINC", "http://loinc.org");
      firstDoc.profiles.put("MyProfile", new Profile("MyProfile").withLocation(new int[] {3, 0, 15, 7}));
      firstDoc.extensions.put("MyExtension", new Extension("MyExtension").withLocation(new int[] {17, 0, 20, 8}));
      firstDoc.logicals.put("MyLogical", new Logical("MyLogical").withLocation(new int[] {31, 0, 39, 23}));
      FSHDocument secondDoc = new FSHDocument(path.join(tempIn, "second.fsh"));
      secondDoc.instances.put("MyInstance", new Instance("MyInstance").withLocation(new int[] {20, 0, 25, 5}));
      secondDoc.valueSets.put("MyValueSet", new FshValueSet("MyValueSet").withLocation(new int[] {30, 0, 35, 9}));
      secondDoc.codeSystems.put("MyCodeSystem",
          new FshCodeSystem("MyCodeSystem").withLocation(new int[] {10, 0, 15, 18}));
      FSHDocument thirdDoc = new FSHDocument(path.join(tempIn, "extra", "third.fsh"));
      thirdDoc.invariants.put("inv-1", new Invariant("inv-1").withLocation(new int[] {222, 0, 224, 9}));
      thirdDoc.ruleSets.put("MyRuleSet", new RuleSet("MyRuleSet").withLocation(new int[] {33, 0, 39, 15}));
      thirdDoc.mappings.put("MyMapping", new Mapping("MyMapping").withLocation(new int[] {10, 0, 21, 18}));
      thirdDoc.resources.put("MyResource", new Resource("MyResource").withLocation(new int[] {55, 0, 69, 31}));
      FSHDocument ruleSetDoc = new FSHDocument(path.join(tempIn, "extra", "rulesets.fsh"));
      ruleSetDoc.ruleSets.put("OneRuleSet", new RuleSet("OneRuleSet").withLocation(new int[] {8, 0, 18, 35}));
      ruleSetDoc.ruleSets.put("TwoRuleSet", new RuleSet("TwoRuleSet").withLocation(new int[] {20, 0, 35, 21}));
      tank = new FSHTank(new ArrayList<>(List.of(firstDoc, secondDoc, thirdDoc, ruleSetDoc)), null);
      writePreprocessedFSH(tempOut, tempIn, tank);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("Processing #writePreprocessedFSH should produce files in a structure that mirrors the input")
    void shouldProduceFilesInAStructureThatMirrorsTheInput() {
      // TS: expect(fs.existsSync(...)) with no matcher, i.e. no assertion; kept as evaluations only
      fs.existsSync(path.join(tempOut, "_preprocessed", "first.fsh"));
      fs.existsSync(path.join(tempOut, "_preprocessed", "second.fsh"));
      fs.existsSync(path.join(tempOut, "_preprocessed", "extra", "third.fsh"));
      fs.existsSync(path.join(tempOut, "_preprocessed", "extra", "aliases.fsh"));
    }

    @Test
    @DisplayName("Processing #writePreprocessedFSH should write all entities that exist after preprocessing")
    void shouldWriteAllEntitiesThatExistAfterPreprocessing() {
      // first.fsh should contain LOINC (an Alias), MyProfile, and MyExtension
      String firstContents = fs.readFileSync(path.join(tempOut, "_preprocessed", "first.fsh"), "utf-8");
      assertContainsText("Alias: LOINC", firstContents);
      assertContainsText("// Originally defined on lines 3 - 15" + EOL + "Profile: MyProfile", firstContents);
      assertContainsText("// Originally defined on lines 17 - 20" + EOL + "Extension: MyExtension", firstContents);
      assertContainsText("// Originally defined on lines 31 - 39" + EOL + "Logical: MyLogical", firstContents);
      // second.fsh should contain MyCodeSystem, MyInstance, and MyValueSet
      String secondContents = fs.readFileSync(path.join(tempOut, "_preprocessed", "second.fsh"), "utf-8");
      assertContainsText("// Originally defined on lines 10 - 15" + EOL + "CodeSystem: MyCodeSystem", secondContents);
      assertContainsText("// Originally defined on lines 20 - 25" + EOL + "Instance: MyInstance", secondContents);
      assertContainsText("// Originally defined on lines 30 - 35" + EOL + "ValueSet: MyValueSet", secondContents);
      // third.fsh should contain MyMapping and inv-1
      String thirdContents = fs.readFileSync(path.join(tempOut, "_preprocessed", "extra", "third.fsh"), "utf-8");
      assertContainsText("// Originally defined on lines 10 - 21" + EOL + "Mapping: MyMapping", thirdContents);
      assertContainsText("// Originally defined on lines 222 - 224" + EOL + "Invariant: inv-1", thirdContents);
      assertContainsText("// Originally defined on lines 55 - 69" + EOL + "Resource: MyResource", thirdContents);
      // RuleSets do not exist after preprocessing
      assertNotContainsText("RuleSet: MyRuleSet", thirdContents);
    }

    @Test
    @DisplayName("Processing #writePreprocessedFSH should write entities in their original order")
    void shouldWriteEntitiesInTheirOriginalOrder() {
      String secondContents = fs.readFileSync(path.join(tempOut, "_preprocessed", "second.fsh"), "utf-8");
      int instanceLocation = secondContents.indexOf("Instance: MyInstance");
      int valueSetLocation = secondContents.indexOf("ValueSet: MyValueSet");
      int codeSystemLocation = secondContents.indexOf("CodeSystem: MyCodeSystem");
      assertTrue(instanceLocation > -1);
      assertTrue(valueSetLocation > -1);
      assertTrue(codeSystemLocation > -1);
      assertTrue(codeSystemLocation < instanceLocation);
      assertTrue(instanceLocation < valueSetLocation);
    }

    @Test
    @DisplayName("Processing #writePreprocessedFSH should write a comment for a file with no entities after preprocessing")
    void shouldWriteACommentForAFileWithNoEntitiesAfterPreprocessing() {
      // A file with only RuleSet definitions will have no content after preprocessing.
      String ruleSetContent = fs.readFileSync(path.join(tempOut, "_preprocessed", "extra", "rulesets.fsh"), "utf-8");
      assertEquals("// This file has no content after preprocessing.", ruleSetContent);
    }
  }

  @Nested
  @DisplayName("#init()")
  class Init {
    // TS spies: readline-sync question/keyInYN, console.log, axios.get (Processing.initIo here), and
    // fs.writeFileSync/copyFileSync/ensureDirSync (mocked to no-ops). Java init writes real files, so process.cwd()
    // (user.dir) points at a fresh temp dir and the spied fs calls are asserted as files on disk.
    List<String> readlineCalls;
    java.util.function.Function<String, String> readlineImpl;
    List<String> yesNoCalls;
    boolean yesNoAnswer;
    List<String> consoleCalls;
    List<String> getCalls;
    InitIo originalIo;
    String originalCwd;
    String cwd;

    @BeforeEach
    void beforeEach() {
      readlineCalls = new ArrayList<>();
      readlineImpl = q -> "";
      yesNoCalls = new ArrayList<>();
      yesNoAnswer = true;
      consoleCalls = new ArrayList<>();
      getCalls = new ArrayList<>();
      originalIo = Processing.initIo;
      Processing.initIo = new InitIo() {
        @Override public String question(String query) {
          readlineCalls.add(query);
          return readlineImpl.apply(query);
        }

        @Override public boolean keyInYN(String query) {
          yesNoCalls.add(query);
          return yesNoAnswer;
        }

        @Override public void consoleLog(String message) {
          consoleCalls.add(message);
        }

        @Override public String httpGet(String url) {
          getCalls.add(url);
          return url.substring(url.lastIndexOf('/') + 1);
        }
      };
      originalCwd = System.getProperty("user.dir");
      cwd = temp.mkdirSync("sushi-init");
      System.setProperty("user.dir", cwd);
    }

    @AfterEach
    void afterEach() {
      Processing.initIo = originalIo;
      System.setProperty("user.dir", originalCwd);
      temp.cleanupSync();
    }

    static final List<String> ALL_QUESTIONS = List.of(
        "Name (Default: ExampleIG): ",
        "Id (Default: fhir.example): ",
        "Canonical (Default: http://example.org): ",
        "Status (Default: draft): ",
        "Version (Default: 0.1.0): ",
        "Release Label (Default: ci-build): ",
        "Publisher Name (Default: Example Publisher): ",
        "Publisher Url (Default: http://example.org/example-publisher): ");

    /** writeSpy.mock.calls[2][1] vs the fixture, both without line breaks and with ${YEAR} filled in */
    void assertConfigMatchesFixture(String project, String fixture) {
      String written = read(path.join(cwd, project, "sushi-config.yaml"));
      String expected = fs.readFileSync(path.join(__dirname, "fixtures", "init-config", fixture), "utf-8")
          .replaceAll("[\\n\\r]", "")
          .replace("${YEAR}", String.valueOf(java.time.Year.now().getValue()));
      assertEquals(expected, written.replaceAll("[\\n\\r]", ""));
    }

    /** The ensureDirSync / writeFileSync / copyFileSync / axios.get expectations shared by the initializing tests. */
    void assertProjectWritten(String project, String idPattern) {
      String dir = path.join(cwd, project);
      // ensureDirSpy: input/pagecontent, input/fsh
      assertTrue(fs.existsSync(path.join(dir, "input", "pagecontent")));
      assertTrue(fs.existsSync(path.join(dir, "input", "fsh")));
      // writeSpy: index.md, ig.ini, sushi-config.yaml, _build.bat, _build.sh
      JsAssert.assertMatches("# " + project, read(path.join(dir, "input", "pagecontent", "index.md")));
      JsAssert.assertMatches(idPattern, read(path.join(dir, "ig.ini")));
      assertTrue(fs.existsSync(path.join(dir, "sushi-config.yaml")));
      // copyFileSpy: patient.fsh, .gitignore, ignoreWarnings.txt
      assertTrue(fs.existsSync(path.join(dir, "input", "fsh", "patient.fsh")));
      assertTrue(fs.existsSync(path.join(dir, ".gitignore")));
      assertTrue(fs.existsSync(path.join(dir, "input", "ignoreWarnings.txt")));

      assertEquals(2, getCalls.size());
      String base = "https://raw.githubusercontent.com/HL7/ig-publisher-scripts/main/";
      assertEquals(base + "_build.bat", getCalls.get(0));
      assertEquals(base + "_build.sh", getCalls.get(1));
      JsAssert.assertMatches("_build\\.bat", read(path.join(dir, "_build.bat")));
      JsAssert.assertMatches("_build\\.sh", read(path.join(dir, "_build.sh")));
    }

    @Test
    @DisplayName("Processing #init() should initialize a default project when no user input is given")
    void shouldInitializeADefaultProjectWhenNoUserInputIsGiven() {
      init(null, null, false, false);
      assertEquals(ALL_QUESTIONS, readlineCalls);
      assertEquals(1, yesNoCalls.size());
      JsAssert.assertMatches("SUSHI project will be created in .*ExampleIG", yesNoCalls.get(0));
      assertProjectWritten("ExampleIG", "fhir.example");
      assertConfigMatchesFixture("ExampleIG", "default-config.yaml");
    }

    @Test
    @DisplayName("Processing #init() should initialize a project with user input")
    void shouldInitializeAProjectWithUserInput() {
      readlineImpl = question -> {
        if (question.startsWith("Name")) {
          return "MyNonDefaultName";
        } else if (question.startsWith("Id")) {
          return "foo.bar";
        } else if (question.startsWith("Canonical")) {
          return "http://foo.com";
        } else if (question.startsWith("Status")) {
          return "active";
        } else if (question.startsWith("Version")) {
          return "2.0.0";
        } else if (question.startsWith("Release Label")) {
          return "qa-preview";
        } else if (question.startsWith("Publisher Name")) {
          return "SUSHI Chefs";
        } else if (question.startsWith("Publisher Url")) {
          return "http://custom-publisher.org";
        }
        return null;
      };
      init(null, null, false, false);
      assertEquals(ALL_QUESTIONS, readlineCalls);
      assertEquals(1, yesNoCalls.size());
      JsAssert.assertMatches("SUSHI project will be created in .*MyNonDefaultName", yesNoCalls.get(0));
      assertProjectWritten("MyNonDefaultName", "foo.bar");
      assertConfigMatchesFixture("MyNonDefaultName", "user-input-config.yaml");
    }

    @Test
    @DisplayName("Processing #init() should abort initializing a project when the user does not confirm")
    void shouldAbortInitializingAProjectWhenTheUserDoesNotConfirm() {
      yesNoAnswer = false;
      init(null, null, false, false);
      assertEquals(ALL_QUESTIONS, readlineCalls);
      assertEquals(1, yesNoCalls.size());
      JsAssert.assertMatches("SUSHI project will be created in .*ExampleIG", yesNoCalls.get(0));
      // ensureDirSpy, writeSpy, copyFileSpy: no calls -> nothing created
      assertEquals(0, fs.readdirSync(cwd).size());
      assertEquals("\nAborting Initialization.\n", consoleCalls.get(consoleCalls.size() - 1));
    }

    @Test
    @DisplayName("Processing #init() should initialize a project when the user provides all config options on the command line and allows auto-initializing")
    void shouldInitializeAProjectWhenAllConfigOptionsAreOnTheCommandLine() {
      Map<String, String> config = new LinkedHashMap<>();
      config.put("id", "foo.bar.baz");
      config.put("canonical", "http://foo.bar.baz.com");
      config.put("status", "active");
      config.put("version", "2.0.0");
      config.put("releaselabel", "ballot");
      config.put("publisher-name", "Foo Bar Baz Inc.");
      config.put("publisher-url", "http://foo.org");
      init("MyCLIOptionProject", config, false, true);
      assertEquals(0, readlineCalls.size());
      assertEquals(0, yesNoCalls.size());
      assertProjectWritten("MyCLIOptionProject", "foo\\.bar\\.baz");
      assertConfigMatchesFixture("MyCLIOptionProject", "cli-input-config.yaml");
    }

    @Test
    @DisplayName("Processing #init() should prompt for and accept inputs for any option not already set with a command line config option")
    void shouldPromptForAnyOptionNotAlreadySet() {
      readlineImpl = question -> {
        if (question.startsWith("Status")) {
          return "active";
        } else if (question.startsWith("Version")) {
          return "2.0.0";
        } else if (question.startsWith("Release Label")) {
          return "trial-use";
        } else if (question.startsWith("Publisher Name")) {
          return "Foo Two";
        }
        return null;
      };
      Map<String, String> config = new LinkedHashMap<>();
      config.put("id", "foo.bar.baz");
      config.put("canonical", "http://foo.bar.baz.com");
      config.put("publisher-url", "http://foo.org");
      init("MySemiCLIOptionProject", config, false, false);
      // Only prompt for the fields not specified in CLI options
      assertEquals(List.of(
          "Status (Default: draft): ",
          "Version (Default: 0.1.0): ",
          "Release Label (Default: ci-build): ",
          "Publisher Name (Default: Example Publisher): "), readlineCalls);
      // Need to confirm initialization
      assertEquals(1, yesNoCalls.size());
      JsAssert.assertMatches("SUSHI project will be created in .*MySemiCLIOptionProject", yesNoCalls.get(0));
      assertProjectWritten("MySemiCLIOptionProject", "foo\\.bar\\.baz");
      assertConfigMatchesFixture("MySemiCLIOptionProject", "semi-cli-input-config.yaml");
    }

    @Test
    @DisplayName("Processing #init() should accept remaining defaults without prompting for any options not already set with a command line config option when default option is used")
    void shouldAcceptRemainingDefaultsWhenDefaultOptionIsUsed() {
      Map<String, String> config = new LinkedHashMap<>();
      config.put("id", "foo.bar.baz");
      config.put("canonical", "http://foo.bar.baz.com");
      config.put("publisher-url", "http://foo.org");
      init("MyCLIOptionWithDefaultsProject", config, true, true);
      assertEquals(0, readlineCalls.size());
      assertEquals(0, yesNoCalls.size());
      assertProjectWritten("MyCLIOptionWithDefaultsProject", "foo\\.bar\\.baz");
      assertConfigMatchesFixture("MyCLIOptionWithDefaultsProject", "cli-input-with-defaults-config.yaml");
    }
  }

  /** TS mocks child_process.execSync / axios.get; Java substitutes Processing.versionSources. */
  static final class FakeVersionSources implements Processing.VersionSources {
    final java.util.ArrayDeque<java.util.concurrent.Callable<String>> npm = new java.util.ArrayDeque<>();
    final java.util.ArrayDeque<java.util.concurrent.Callable<Object>> http = new java.util.ArrayDeque<>();

    @Override public String npmView() throws Exception {
      return npm.isEmpty() ? null : npm.poll().call();
    }

    @Override public Object registryGet(String url) throws Exception {
      return http.isEmpty() ? null : http.poll().call();
    }
  }

  @Nested
  @DisplayName("#getLatestSushiVersion()")
  class GetLatestSushiVersion {
    Processing.VersionSources original;
    FakeVersionSources fake;

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
      original = Processing.versionSources;
      fake = new FakeVersionSources();
      Processing.versionSources = fake;
    }

    @AfterEach
    void afterEach() {
      Processing.versionSources = original;
    }

    @Test
    @DisplayName("Processing #getLatestSushiVersion() successfully fetches data")
    void successfullyFetchesData() {
      fake.npm.add(() -> "2.2.6\n");
      assertEquals("2.2.6", Processing.getLatestSushiVersion());

      fake.npm.add(() -> "2.2.6");
      assertEquals("2.2.6", Processing.getLatestSushiVersion());
    }

    @Test
    @DisplayName("Processing #getLatestSushiVersion() falls back to URL if primary process fails")
    void fallsBackToURLIfPrimaryProcessFails() {
      fake.npm.add(() -> "npm ERR! code E404");
      fake.http.add(() -> JsAssert.json("""
          {
            "name": "fsh-sushi",
            "dist-tags": {
              "latest": "2.1.6",
              "beta": "2.0.0-beta.3",
              "pre-1.0": "0.16.1",
              "internal": "2.0.0-beta.1-fshonline-hotfix"
            }
          }
          """));
      assertEquals("2.1.6", Processing.getLatestSushiVersion());
    }

    @Test
    @DisplayName("Processing #getLatestSushiVersion() unsuccessfully fetches data if it doesn't look like a semver is not found")
    void unsuccessfullyFetchesDataIfItDoesntLookLikeASemver() {
      fake.npm.add(() -> "npm ERR! code E404");
      // TS: axios resolves {} so res.data is undefined
      fake.http.add(() -> null);
      String version = Processing.getLatestSushiVersion();
      assertNull(version);
      // Loosely match message since it differs slightly between npm 6 and npm 8
      assertTrue(java.util.regex.Pattern.compile("Unable to determine the latest version of sushi: Cannot read .*'dist-tags'.*")
          .matcher(loggerSpy.getLastMessage("warn")).find());
    }
  }

  /** Not in TS: --require-latest checks fhir-ig-tools releases (core-divergences.md T3); the fetch is stubbed. */
  @Nested
  @DisplayName("#checkLatestPortRelease()")
  class CheckLatestPortRelease {
    Processing.ReleaseSource original;
    final java.util.ArrayDeque<java.util.concurrent.Callable<Object>> answers = new java.util.ArrayDeque<>();
    final List<String> urls = new ArrayList<>();

    @BeforeEach
    void beforeEach() {
      original = Processing.releaseSource;
      Processing.releaseSource = url -> {
        urls.add(url);
        return answers.poll().call();
      };
    }

    @AfterEach
    void afterEach() {
      Processing.releaseSource = original;
    }

    Object release(String tag, String... assets) {
      StringBuilder a = new StringBuilder();
      for (String n : assets) a.append(a.length() == 0 ? "" : ",").append("{\"name\":\"").append(n).append("\"}");
      return JsAssert.json("{\"tag_name\":\"" + tag + "\",\"assets\":[" + a + "]}");
    }

    @Test
    void asksGitHubForTheLatestRelease() {
      answers.add(() -> release("v0.2.0", "jekyll.jar", "sushi.jar"));
      assertTrue(Processing.checkLatestPortRelease("0.2.0").ok());
      assertEquals(List.of("https://api.github.com/repos/jmandel/fhir-ig-tools/releases/latest"), urls);
    }

    @Test
    void theLatestReleasePasses() {
      answers.add(() -> release("v0.2.0", "sushi.jar"));
      Processing.PortReleaseCheck c = Processing.checkLatestPortRelease("0.2.0");
      assertEquals(new Processing.PortReleaseCheck(true, "0.2.0", "0.2.0", null), c);
    }

    @Test
    void anOlderReleaseFails() {
      answers.add(() -> release("v0.3.0", "sushi.jar"));
      Processing.PortReleaseCheck c = Processing.checkLatestPortRelease("0.2.0");
      assertFalse(c.ok());
      assertEquals("Current SUSHI version (SUSHI 3.21.1 (Java port, fhir-ig-tools release 0.2.0)) is not the latest"
          + " version: the latest fhir-ig-tools release is 0.3.0. Upgrade to the latest release"
          + " (https://github.com/jmandel/fhir-ig-tools/releases/latest: sushi.jar, or the sushi wrapper, which pins its"
          + " version) or run SUSHI again without the --require-latest flag.", c.message());
    }

    @Test
    void aNewerThanLatestReleasePasses() {
      answers.add(() -> release("v0.2.0", "sushi.jar"));
      assertTrue(Processing.checkLatestPortRelease("0.2.1").ok());
    }

    @Test
    void aDevelopmentBuildFails() {
      answers.add(() -> release("v0.2.0", "sushi.jar"));
      Processing.PortReleaseCheck c = Processing.checkLatestPortRelease("0.3.0-SNAPSHOT");
      assertFalse(c.ok());
      assertContainsText("this sushi.jar is a development build, not a release; the latest fhir-ig-tools release is"
          + " 0.2.0.", c.message());
      answers.add(() -> release("v0.2.0", "sushi.jar"));
      assertFalse(Processing.checkLatestPortRelease("unknown").ok());
    }

    @Test
    void aLatestReleaseWithoutSushiDoesNotCount() {
      answers.add(() -> release("v0.1.0", "jekyll.jar", "jekyll-shim"));
      assertTrue(Processing.checkLatestPortRelease("0.0.9").ok());
    }

    @Test
    void anUnreachableServerFailsAsTSDoesWhenNpmIsUnreachable() {
      answers.add(() -> {
        throw new java.io.IOException("Request failed with status code 403");
      });
      Processing.PortReleaseCheck c = Processing.checkLatestPortRelease("0.2.0");
      assertFalse(c.ok());
      assertNull(c.latest());
      assertContainsText("could not be checked: the latest fhir-ig-tools release is undetermined"
          + " (https://api.github.com/repos/jmandel/fhir-ig-tools/releases/latest: Request failed with status code 403).",
          c.message());
      answers.add(() -> JsAssert.json("{\"message\":\"Not Found\"}"));
      assertContainsText("the answer has no tag_name", Processing.checkLatestPortRelease("0.2.0").message());
    }

    @Test
    void theBuildRecordsItsRelease() {
      // target/classes holds the filtered release.properties: the pom version unless -Dsushi.release was given
      String r = Processing.getPortRelease();
      assertFalse(r.isBlank());
      assertFalse(r.startsWith("${"), r);
    }
  }

  @Nested
  @DisplayName("#checkSushiVersion()")
  class CheckSushiVersion {
    Processing.VersionSources original;
    FakeVersionSources fake;

    @BeforeEach
    void beforeEach() {
      original = Processing.versionSources;
      fake = new FakeVersionSources();
      Processing.versionSources = fake;
    }

    @AfterEach
    void afterEach() {
      Processing.versionSources = original;
    }

    @Test
    @DisplayName("Processing #checkSushiVersion() returns an object with the latest and current sushi versions")
    void returnsAnObjectWithTheLatestAndCurrentSushiVersions() {
      String localVersion = Processing.getLocalSushiVersion();
      String latestVersion = "2.5.0"; // A fake stable version (not a prerelease, like beta)
      fake.npm.add(() -> latestVersion + "\n");
      Processing.SushiVersions versionObj = Processing.checkSushiVersion(true);
      assertEquals(new Processing.SushiVersions(latestVersion, localVersion), versionObj);
    }

    @Test
    @DisplayName("Processing #checkSushiVersion() should return an object with an undefined latest value when latest is not present")
    void shouldReturnAnObjectWithAnUndefinedLatestValueWhenLatestIsNotPresent() {
      String localVersion = Processing.getLocalSushiVersion();
      fake.npm.add(() -> "zsh: command not found: npm\n");
      Processing.SushiVersions versionObj = Processing.checkSushiVersion(true);
      assertEquals(new Processing.SushiVersions(null, localVersion), versionObj);
    }
  }
}
