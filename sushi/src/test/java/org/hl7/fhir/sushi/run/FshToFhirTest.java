package org.hl7.fhir.sushi.run;

import static org.hl7.fhir.sushi.run.FshToFhir.fshToFhir;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getLocalVirtualPackages;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fhirdefs.DiskBasedVirtualPackage;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirdefs.InMemoryVirtualPackage;
import org.hl7.fhir.sushi.fhirdefs.R5DefsForR4;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.hl7.fhir.sushi.utils.Processing;
import org.junit.jupiter.api.*;

// port of sushi/test/run/FshToFhir.test.ts (SUSHI 3.21.1)
@DisplayName("#FshToFhir")
class FshToFhirTest {

  /** jest.spyOn(processing, 'loadExternalDependencies') stand-in, installed as FshToFhir.dependencyLoader. */
  static final class LoadSpy implements BiConsumer<FHIRDefinitions, Configuration> {
    final List<Object[]> calls = new ArrayList<>();
    BiConsumer<FHIRDefinitions, Configuration> impl = (d, c) -> {};
    final Deque<BiConsumer<FHIRDefinitions, Configuration>> once = new ArrayDeque<>();

    @Override
    public void accept(FHIRDefinitions defs, Configuration config) {
      calls.add(new Object[] {defs, config});
      BiConsumer<FHIRDefinitions, Configuration> fn = once.isEmpty() ? impl : once.poll();
      fn.accept(defs, config);
    }

    void mockClear() {
      calls.clear();
    }

    void mockImplementation(BiConsumer<FHIRDefinitions, Configuration> fn) {
      impl = fn;
    }

    void mockImplementationOnce(BiConsumer<FHIRDefinitions, Configuration> fn) {
      once.add(fn);
    }
  }

  static LoadSpy loadSpy;
  static Object defaultConfig;

  @BeforeAll
  static void beforeAll() {
    loadSpy = new LoadSpy(); // mockResolvedValue(): no-op
    FshToFhir.dependencyLoader = loadSpy;
    defaultConfig = json("""
        {
          "canonical": "http://example.org",
          "FSHOnly": true,
          "fhirVersion": ["4.0.1"]
        }
        """);
  }

  @AfterAll
  static void afterAllTop() {
    FshToFhir.dependencyLoader = Processing::loadExternalDependencies;
  }

  @BeforeEach
  void beforeEach() {
    loadSpy.mockClear();
    loggerSpy.reset();
  }

  // ------------------------------------------------------------------------------------------------- helpers

  static String logLevel() {
    return FshLogger.logger.level();
  }

  static FshToFhir.Options opts(String logLevel) {
    FshToFhir.Options o = new FshToFhir.Options();
    o.logLevel = logLevel;
    return o;
  }

  static FshToFhir.Options snapshotOpt(boolean snapshot) {
    FshToFhir.Options o = new FshToFhir.Options();
    o.snapshot = snapshot;
    return o;
  }

  /** The config passed to loadExternalDependencies as a JS object (non-null public fields; toEqual skips undefined). */
  static Object configJson(Object config) {
    JsObject o = new JsObject();
    for (Field f : Configuration.class.getFields()) {
      if (Modifier.isStatic(f.getModifiers())) continue;
      try {
        Object v = f.get(config);
        if (v != null) o.put(f.getName(), v);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    }
    return o;
  }

  static void assertEmptyResult(FshToFhir.Result r) {
    assertEquals(List.of(), r.errors());
    assertEquals(List.of(), r.warnings());
    assertEquals(List.of(), r.fhir());
  }

  /** TS test/utils/leftAlign.ts: strip the first lettered line's indentation from every line. */
  private static String leftAlign(String inputFSH) {
    String[] lineArray = inputFSH.split("\\r?\\n", -1);
    int offsetAmount = -1;
    Pattern letterRegex = Pattern.compile("[a-z]", Pattern.CASE_INSENSITIVE);
    for (String line : lineArray) {
      if (letterRegex.matcher(line).find()) {
        var m = Pattern.compile("\\S").matcher(line);
        offsetAmount = m.find() ? m.start() : -1;
        break;
      }
    }
    List<String> out = new ArrayList<>();
    for (String l : lineArray) {
      // JS: l.length >= undefined is false (no lettered line) -> keep the line
      out.add(offsetAmount >= 0 && l.length() >= offsetAmount ? l.substring(offsetAmount) : l);
    }
    return String.join("\n", out);
  }

  static Object findById(List<Object> fhir, String id) {
    return find(fhir, r -> Objects.equals(at(r, "id"), id));
  }

  // --------------------------------------------------------------------------------------------------- tests

  @Test
  @DisplayName("#FshToFhir should use the \"info\" logging level by default")
  void shouldUseTheInfoLoggingLevelByDefault() {
    assertEmptyResult(fshToFhir(""));
    assertEquals("info", logLevel());
  }

  @Test
  @DisplayName("#FshToFhir should use a higher logging level when specified")
  void shouldUseAHigherLoggingLevelWhenSpecified() {
    loadSpy.mockImplementationOnce((d, c) -> {
      assertEquals("error", logLevel());
      assertFalse(FshToFhir.loggerSilent());
    });
    var results = fshToFhir("Bad FSH", opts("error"));
    assertEquals(1, results.errors().size());
    assertEquals(new TextLocation(1, 1, 1, 3), results.errors().get(0).location());
    assertMatches("mismatched input 'Bad'", results.errors().get(0).message());
    assertEquals(0, results.warnings().size());
    assertEquals(List.of(), results.fhir());
    assertEquals("info", logLevel());
    assertFalse(FshToFhir.loggerSilent());
  }

  @Test
  @DisplayName("#FshToFhir should mute the logger when \"silent\" is specified")
  void shouldMuteTheLoggerWhenSilentIsSpecified() {
    loadSpy.mockImplementationOnce((d, c) -> assertTrue(FshToFhir.loggerSilent()));
    var results = fshToFhir("Bad FSH", opts("silent"));
    assertEquals(1, results.errors().size());
    assertEquals(new TextLocation(1, 1, 1, 3), results.errors().get(0).location());
    // errors are still tracked, even when the logger is silent
    assertMatches("mismatched input 'Bad'", results.errors().get(0).message());
    assertEquals(0, results.warnings().size());
    assertEquals(List.of(), results.fhir());
    assertFalse(FshToFhir.loggerSilent());
  }

  @Test
  @DisplayName("#FshToFhir should quit and return an error when an invalid logLevel is specified")
  void shouldQuitAndReturnAnErrorWhenAnInvalidLogLevelIsSpecified() {
    var results = fshToFhir("Bad FSH", opts("11"));
    assertEquals(1, results.errors().size());
    assertMatches(
        "Invalid logLevel: 11. Valid levels include: silly, debug, verbose, http, info, warn, error, silent",
        results.errors().get(0).message());
    assertEquals(0, results.warnings().size());
    assertNull(results.fhir());
  }

  @Nested
  @DisplayName("#LoggerIsolation")
  class LoggerIsolation {

    @Test
    @DisplayName("#FshToFhir #LoggerIsolation should not stay silent when a later call uses a non-silent logLevel")
    void shouldNotStaySilentWhenALaterCallUsesANonSilentLogLevel() {
      fshToFhir("", opts("silent"));
      assertFalse(FshToFhir.loggerSilent());

      loadSpy.mockImplementationOnce((d, c) -> {
        assertFalse(FshToFhir.loggerSilent());
        assertEquals("error", logLevel());
      });
      fshToFhir("", opts("error"));
      assertFalse(FshToFhir.loggerSilent());
      assertEquals("info", logLevel());
    }

    @Test
    @DisplayName("#FshToFhir #LoggerIsolation should not keep a previous logLevel when a later call omits logLevel")
    void shouldNotKeepAPreviousLogLevelWhenALaterCallOmitsLogLevel() {
      fshToFhir("", opts("error"));

      loadSpy.mockImplementationOnce((d, c) -> {
        assertEquals("info", logLevel());
        assertFalse(FshToFhir.loggerSilent());
      });
      fshToFhir("");
      assertEquals("info", logLevel());
    }

    @Test
    @DisplayName("#FshToFhir #LoggerIsolation should restore the logger when processing fails")
    void shouldRestoreTheLoggerWhenProcessingFails() {
      loadSpy.mockImplementationOnce((d, c) -> {
        throw new RuntimeException("dependency load failed");
      });
      RuntimeException e = assertThrows(RuntimeException.class, () -> fshToFhir("", opts("silent")));
      assertTrue(e.getMessage().contains("dependency load failed"), e.getMessage());
      assertFalse(FshToFhir.loggerSilent());
      assertEquals("info", logLevel());
    }
  }

  @Test
  @DisplayName("#FshToFhir should replace configuration options when specified")
  void shouldReplaceConfigurationOptionsWhenSpecified() {
    FshToFhir.Options o = new FshToFhir.Options();
    o.canonical = "http://mycanonical.org";
    o.dependencies = new ArrayList<>(List.of((JsObject) json("""
        { "packageId": "hl7.fhir.test.core", "version": "1.2.3" }
        """)));
    o.fhirVersion = "4.5.0";
    o.version = "3.2.1";
    assertEmptyResult(fshToFhir("", o));
    assertJsonEqual("""
        {
          "FSHOnly": true,
          "canonical": "http://mycanonical.org",
          "dependencies": [{ "packageId": "hl7.fhir.test.core", "version": "1.2.3" }],
          "fhirVersion": ["4.5.0"],
          "version": "3.2.1"
        }
        """, configJson(loadSpy.calls.get(0)[1]));
  }

  @Test
  @DisplayName("#FshToFhir should load external dependencies")
  void shouldLoadExternalDependencies() {
    fshToFhir("");
    assertEquals(1, loadSpy.calls.size());
    assertEquals(2, loadSpy.calls.get(0).length);
    assertInstanceOf(FHIRDefinitions.class, loadSpy.calls.get(0)[0]);
    assertJsEqual(defaultConfig, configJson(loadSpy.calls.get(0)[1]));
  }

  @Nested
  @DisplayName("#Conversion")
  class Conversion {

    // TS beforeAll/afterAll on the shared spy: per-test here (same effect, the outer beforeEach only clears calls)
    @BeforeEach
    void beforeAll() {
      loadSpy.mockImplementation((defs, c) -> {
        List<DiskBasedVirtualPackage> vps = getLocalVirtualPackages(
            testDefsPath("r4-definitions", "package", "StructureDefinition-StructureDefinition.json"),
            testDefsPath("r4-definitions", "package", "StructureDefinition-Patient.json"));
        for (var vp : vps) {
          defs.loadVirtualPackage(vp);
        }
      });
    }

    @AfterEach
    void afterAll() {
      loadSpy.mockImplementation((d, c) -> {});
    }

    @Test
    @DisplayName("#FshToFhir #Conversion should convert valid FSH into FHIR with a single input")
    void shouldConvertValidFSHIntoFHIRWithASingleInput() {
      var results = fshToFhir(leftAlign("""

          Profile: MyPatient
          Parent: Patient
          * name MS
           """));
      assertEquals(0, results.errors().size());
      assertEquals(0, results.warnings().size());

      assertEquals(1, results.fhir().size());
      assertEquals("MyPatient", at(results.fhir(), "[0].id"));
      Object name = find(at(results.fhir(), "[0].differential.element"), e -> Objects.equals(at(e, "id"), "Patient.name"));
      assertEquals(true, at(name, "mustSupport"));
      assertNull(at(results.fhir(), "[0].snapshot"));
    }

    @Test
    @DisplayName("#FshToFhir #Conversion should convert valid FSH into FHIR with several inputs")
    void shouldConvertValidFSHIntoFHIRWithSeveralInputs() {
      var results = fshToFhir(List.of(
          leftAlign("""

              Profile: MyPatient1
              Parent: Patient
              * name MS
               """),
          leftAlign("""

              Profile: MyPatient2
              Parent: Patient
              * gender MS
               """)), null);
      assertEquals(0, results.errors().size());
      assertEquals(0, results.warnings().size());
      assertEquals(2, results.fhir().size());
      assertEquals("MyPatient1", at(results.fhir(), "[0].id"));
      Object name = find(at(results.fhir(), "[0].differential.element"), e -> Objects.equals(at(e, "id"), "Patient.name"));
      assertEquals(true, at(name, "mustSupport"));
      assertNull(at(results.fhir(), "[0].snapshot"));
      assertEquals("MyPatient2", at(results.fhir(), "[1].id"));
      Object gender = find(at(results.fhir(), "[1].differential.element"), e -> Objects.equals(at(e, "id"), "Patient.gender"));
      assertEquals(true, at(gender, "mustSupport"));
      assertNull(at(results.fhir(), "[1].snapshot"));
    }

    @Test
    @DisplayName("#FshToFhir #Conversion should throw error when converting FSH into FHIR with several inputs of different entity types with duplicate names")
    void shouldThrowErrorWhenConvertingFSHIntoFHIRWithSeveralInputsOfDifferentEntityTypes() {
      var results = fshToFhir(List.of(
          leftAlign("""

              Profile: MyPatient1
              Parent: Patient
              * name MS
               """),
          leftAlign("""

              Instance: MyPatient1
              InstanceOf: MyPatient1
               """),
          leftAlign("""

              Profile: MyPatient2
              Parent: Patient
              * name MS
                """),
          leftAlign("""

              Instance: MyPatient2
              InstanceOf: MyPatient2
               """),
          leftAlign("""

              Profile: MyPatient3
              Parent: Patient
              * name MS
                """),
          leftAlign("""

              Instance: MyPatient4
              InstanceOf: MyPatient3
                """),
          leftAlign("""

              Instance: MyPatient5
              InstanceOf: MyPatient3
             """)), null);
      assertEquals(0, results.errors().size());
      assertEquals(1, results.warnings().size());
      assertEquals(7, results.fhir().size());
      assertEquals("MyPatient1", at(results.fhir(), "[0].id"));
      assertEquals("MyPatient2", at(results.fhir(), "[1].id"));
      assertEquals("MyPatient3", at(results.fhir(), "[2].id"));
      assertEquals("MyPatient1", at(results.fhir(), "[3].id"));
      assertEquals("MyPatient2", at(results.fhir(), "[4].id"));
      assertEquals("MyPatient4", at(results.fhir(), "[5].id"));
      assertEquals("MyPatient5", at(results.fhir(), "[6].id"));
      assertContainsText("Detected FSH entity definitions with duplicate names. While FSH allows for duplicate "
          + "names across entity types, they can lead to ambiguous results when referring to these "
          + "entities by name elsewhere (e.g., in references). Consider using unique names in FSH "
          + "declarations and assigning duplicated names using caret assignment rules instead. "
          + "Detected duplicate names: MyPatient1, MyPatient2.", results.warnings().get(0).message());
    }

    @Test
    @DisplayName("#FshToFhir #Conversion should not throw error when converting FSH into FHIR with several inputs of different entity types with different names")
    void shouldNotThrowErrorWhenConvertingFSHIntoFHIRWithSeveralInputsOfDifferentEntityTy() {
      var results = fshToFhir(List.of(
          leftAlign("""

              Profile: MyPatient1
              Parent: Patient
              * name MS
               """),
          leftAlign("""

              Instance: MyPatient2
              InstanceOf: MyPatient1
               """)), null);
      assertEquals(0, results.errors().size());
      assertEquals(0, results.warnings().size());
      assertEquals(2, results.fhir().size());
      assertEquals("MyPatient1", at(results.fhir(), "[0].id"));
      assertEquals("MyPatient2", at(results.fhir(), "[1].id"));
    }

    @Test
    @DisplayName("#FshToFhir #Conversion should trace errors back to the originating input when multiple inputs are given")
    void shouldTraceErrorsBackToTheOriginatingInputWhenMultipleInputsAreGiven() {
      var results = fshToFhir(List.of(
          leftAlign("""

              Profile: MyPatient1
              Parent: FakeProfile
              * name MS
               """),
          leftAlign("""

              Profile: MyPatient2
              Parent: AlsoFakeProfile
              * gender MS
               """)), null);
      assertEquals(2, results.errors().size());
      assertMatches("Parent FakeProfile not found", results.errors().get(0).message());
      assertEquals("Input_0", results.errors().get(0).input());
      assertMatches("Parent AlsoFakeProfile not found", results.errors().get(1).message());
      assertEquals("Input_1", results.errors().get(1).input());
      assertEquals(0, results.warnings().size());
      assertEquals(0, results.fhir().size());
      assertEquals(List.of(), results.fhir());
    }

    @Test
    @DisplayName("#FshToFhir #Conversion should honor snapshot option = true when converting valid FSH into FHIR")
    void shouldHonorSnapshotOptionTrueWhenConvertingValidFSHIntoFHIR() {
      var results = fshToFhir(leftAlign("""

          Profile: MyPatient
          Parent: Patient
          * name MS
           """), snapshotOpt(true));
      assertEquals(0, results.errors().size());
      assertEquals(0, results.warnings().size());
      assertEquals(1, results.fhir().size());
      assertEquals("MyPatient", at(results.fhir(), "[0].id"));
      assertNotNull(at(results.fhir(), "[0].snapshot"));
      Object nameSnap = find(at(results.fhir(), "[0].snapshot.element"), e -> Objects.equals(at(e, "id"), "Patient.name"));
      assertEquals(true, at(nameSnap, "mustSupport"));
    }

    @Test
    @DisplayName("#FshToFhir #Conversion should honor snapshot option = false when converting valid FSH into FHIR")
    void shouldHonorSnapshotOptionFalseWhenConvertingValidFSHIntoFHIR() {
      var results = fshToFhir(leftAlign("""

          Profile: MyPatient
          Parent: Patient
          * name MS
           """), snapshotOpt(false));
      assertEquals(0, results.errors().size());
      assertEquals(0, results.warnings().size());
      assertEquals(1, results.fhir().size());
      assertEquals("MyPatient", at(results.fhir(), "[0].id"));
      assertNull(at(results.fhir(), "[0].snapshot"));
    }
  }

  @Nested
  @DisplayName("#EndToEnd")
  class EndToEnd {
    final String allEntityTypesFSH = leftAlign("""

          Resource: MyResource
          * code 0..1 code "Code" "A code for the resource"

          Logical: MyLogical
          * name 1..1 string "Name" "The name of the thing"

          CodeSystem: MyCodeSystem
          * #a "A"
          * #b "B"

          ValueSet: MyValueSet
          * include codes from system MyCodeSystem

          Instance: MyPatientInstance
          InstanceOf: MyPatient
          Usage: #example
          * name.family = "Smith"

          Extension: MyExtension
          * value[x] only string

          Profile: MyPatient
          Parent: Patient
          * name MS
          """);

    @BeforeEach
    void beforeAll() {
      loadSpy.mockImplementation((defs, c) -> {
        // Mirror loadExternalDependencies, which provides the R5 definitions that R4 projects
        // need (such as Base for logical models), then load the full set of R4 test definitions.
        Map<String, Object> r5ForR4 = new LinkedHashMap<>();
        R5DefsForR4.R5_DEFINITIONS_NEEDED_IN_R4.forEach(def -> r5ForR4.put((String) def.get("id"), def));
        defs.loadVirtualPackage(new InMemoryVirtualPackage(
            InMemoryVirtualPackage.packageJSON("sushi-r5forR4", "1.0.0"), r5ForR4, TestFHIRDefinitions.LOG_MESSAGE,
            false));
        for (var vp : getLocalVirtualPackages(testDefsPath("r4-definitions"))) {
          defs.loadVirtualPackage(vp);
        }
      });
    }

    @AfterEach
    void afterAll() {
      loadSpy.mockImplementation((d, c) -> {});
    }

    @Test
    @DisplayName("#FshToFhir #EndToEnd should convert every exportable FSH entity type into FHIR")
    void shouldConvertEveryExportableFSHEntityTypeIntoFHIR() {
      var results = fshToFhir(allEntityTypesFSH);
      assertEquals(0, results.errors().size());
      // Resources outside the core FHIR namespace always produce a conformance warning
      assertEquals(1, results.warnings().size());
      assertMatches("non-conformant Resource definitions", results.warnings().get(0).message());
      assertEquals(7, results.fhir().size());

      Object profile = findById(results.fhir(), "MyPatient");
      assertEquals("StructureDefinition", at(profile, "resourceType"));
      assertEquals("http://example.org/StructureDefinition/MyPatient", at(profile, "url"));
      assertEquals("resource", at(profile, "kind"));
      assertEquals("Patient", at(profile, "type"));
      assertEquals("constraint", at(profile, "derivation"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Patient", at(profile, "baseDefinition"));

      Object extension = findById(results.fhir(), "MyExtension");
      assertEquals("StructureDefinition", at(extension, "resourceType"));
      assertEquals("http://example.org/StructureDefinition/MyExtension", at(extension, "url"));
      assertEquals("complex-type", at(extension, "kind"));
      assertEquals("Extension", at(extension, "type"));
      assertEquals("constraint", at(extension, "derivation"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Extension", at(extension, "baseDefinition"));
      Object extensionValue = find(at(extension, "differential.element"), e -> Objects.equals(at(e, "id"), "Extension.value[x]"));
      assertJsonEqual("[{ \"code\": \"string\" }]", at(extensionValue, "type"));

      Object logical = findById(results.fhir(), "MyLogical");
      assertEquals("StructureDefinition", at(logical, "resourceType"));
      assertEquals("http://example.org/StructureDefinition/MyLogical", at(logical, "url"));
      assertEquals("logical", at(logical, "kind"));
      assertEquals("http://example.org/StructureDefinition/MyLogical", at(logical, "type"));
      assertEquals("specialization", at(logical, "derivation"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/Base", at(logical, "baseDefinition"));

      Object resource = findById(results.fhir(), "MyResource");
      assertEquals("StructureDefinition", at(resource, "resourceType"));
      assertEquals("http://example.org/StructureDefinition/MyResource", at(resource, "url"));
      assertEquals("resource", at(resource, "kind"));
      assertEquals("MyResource", at(resource, "type"));
      assertEquals("specialization", at(resource, "derivation"));
      assertEquals("http://hl7.org/fhir/StructureDefinition/DomainResource", at(resource, "baseDefinition"));

      assertJsonEqual("""
          {
            "resourceType": "Patient",
            "id": "MyPatientInstance",
            "meta": { "profile": ["http://example.org/StructureDefinition/MyPatient"] },
            "name": [{ "family": "Smith" }]
          }
          """, findById(results.fhir(), "MyPatientInstance"));

      Object valueSet = findById(results.fhir(), "MyValueSet");
      assertEquals("ValueSet", at(valueSet, "resourceType"));
      assertEquals("http://example.org/ValueSet/MyValueSet", at(valueSet, "url"));
      assertJsonEqual("[{ \"system\": \"http://example.org/CodeSystem/MyCodeSystem\" }]",
          at(valueSet, "compose.include"));

      Object codeSystem = findById(results.fhir(), "MyCodeSystem");
      assertEquals("CodeSystem", at(codeSystem, "resourceType"));
      assertEquals("http://example.org/CodeSystem/MyCodeSystem", at(codeSystem, "url"));
      assertEquals("complete", at(codeSystem, "content"));
      assertJsonEqual("""
          [
            { "code": "a", "display": "A" },
            { "code": "b", "display": "B" }
          ]
          """, at(codeSystem, "concept"));
    }

    @Test
    @DisplayName("#FshToFhir #EndToEnd should apply the canonical and version options to the exported artifacts")
    void shouldApplyTheCanonicalAndVersionOptionsToTheExportedArtifacts() {
      FshToFhir.Options o = new FshToFhir.Options();
      o.canonical = "http://custom.org/fhir";
      o.version = "1.2.3";
      var results = fshToFhir(allEntityTypesFSH, o);
      assertEquals(0, results.errors().size());

      for (String id : List.of("MyPatient", "MyExtension", "MyLogical", "MyResource")) {
        Object structDef = findById(results.fhir(), id);
        assertEquals("http://custom.org/fhir/StructureDefinition/" + id, at(structDef, "url"));
        assertEquals("1.2.3", at(structDef, "version"));
      }
      Object valueSet = findById(results.fhir(), "MyValueSet");
      assertEquals("http://custom.org/fhir/ValueSet/MyValueSet", at(valueSet, "url"));
      assertEquals("1.2.3", at(valueSet, "version"));
      assertJsonEqual("[{ \"system\": \"http://custom.org/fhir/CodeSystem/MyCodeSystem\" }]",
          at(valueSet, "compose.include"));
      Object codeSystem = findById(results.fhir(), "MyCodeSystem");
      assertEquals("http://custom.org/fhir/CodeSystem/MyCodeSystem", at(codeSystem, "url"));
      assertEquals("1.2.3", at(codeSystem, "version"));
      assertJsonEqual("[\"http://custom.org/fhir/StructureDefinition/MyPatient\"]",
          at(findById(results.fhir(), "MyPatientInstance"), "meta.profile"));
    }

    @Test
    @DisplayName("#FshToFhir #EndToEnd should generate snapshots for every kind of StructureDefinition when the snapshot option is true")
    void shouldGenerateSnapshotsForEveryKindOfStructureDefinitionWhenTheSnapshotOptionIsT() {
      var results = fshToFhir(allEntityTypesFSH, snapshotOpt(true));
      assertEquals(0, results.errors().size());
      for (String id : List.of("MyPatient", "MyExtension", "MyLogical", "MyResource")) {
        Object structDef = findById(results.fhir(), id);
        assertTrue(len(at(structDef, "snapshot.element")) > 0, id);
      }
    }

    @Test
    @DisplayName("#FshToFhir #EndToEnd should apply Invariants and Mappings to profiles without exporting them as separate artifacts")
    void shouldApplyInvariantsAndMappingsToProfilesWithoutExportingThemAsSeparateArtifact() {
      var results = fshToFhir(leftAlign("""

          Invariant: my-inv
          Description: "Must have a name"
          Expression: "name.exists()"
          Severity: #error

          Mapping: MyMapping
          Id: my-map
          Source: MyPatient
          Target: "http://example.org/target"
          * name -> "PID-5"

          Profile: MyPatient
          Parent: Patient
          * obeys my-inv
          """));
      assertEquals(0, results.errors().size());
      assertEquals(0, results.warnings().size());
      assertEquals(1, results.fhir().size());
      Object profile = results.fhir().get(0);
      assertEquals("MyPatient", at(profile, "id"));
      assertJsonEqual("[{ \"identity\": \"my-map\", \"uri\": \"http://example.org/target\" }]", at(profile, "mapping"));
      Object root = find(at(profile, "differential.element"), e -> Objects.equals(at(e, "id"), "Patient"));
      assertJsonEqual("""
          [
            {
              "key": "my-inv",
              "severity": "error",
              "human": "Must have a name",
              "expression": "name.exists()",
              "source": "http://example.org/StructureDefinition/MyPatient"
            }
          ]
          """, at(root, "constraint"));
      Object name = find(at(profile, "differential.element"), e -> Objects.equals(at(e, "id"), "Patient.name"));
      assertJsonEqual("[{ \"identity\": \"my-map\", \"map\": \"PID-5\" }]", at(name, "mapping"));
    }

    @Test
    @DisplayName("#FshToFhir #EndToEnd should resolve Aliases and RuleSets that are defined in a different input")
    void shouldResolveAliasesAndRuleSetsThatAreDefinedInADifferentInput() {
      var results = fshToFhir(List.of(
          leftAlign("""

              Alias: $SCT = http://snomed.info/sct

              RuleSet: ActiveStatus
              * ^status = #active
              """),
          leftAlign("""

              Profile: MyObservation
              Parent: Observation
              * insert ActiveStatus
              * code = $SCT#12345 "Example code"
              """)), null);
      assertEquals(0, results.errors().size());
      assertEquals(0, results.warnings().size());
      assertEquals(1, results.fhir().size());
      Object profile = results.fhir().get(0);
      assertEquals("active", at(profile, "status"));
      Object code = find(at(profile, "differential.element"), e -> Objects.equals(at(e, "id"), "Observation.code"));
      assertJsonEqual("""
          {
            "coding": [
              { "system": "http://snomed.info/sct", "code": "12345", "display": "Example code" }
            ]
          }
          """, at(code, "patternCodeableConcept"));
    }

    @Test
    @DisplayName("#FshToFhir #EndToEnd should export the valid inputs when another input contains syntax errors")
    void shouldExportTheValidInputsWhenAnotherInputContainsSyntaxErrors() {
      var results = fshToFhir(List.of(
          leftAlign("""

              Profile: BrokenPatient
              Parent Patient
              """),
          leftAlign("""

              Profile: MyPatient
              Parent: Patient
              * name MS
              """)), null);
      assertEquals(2, results.errors().size());
      assertMatches("mismatched input 'Parent'", results.errors().get(0).message());
      assertEquals("Input_0", results.errors().get(0).input());
      assertMatches("The definition for BrokenPatient does not include a Parent", results.errors().get(1).message());
      assertEquals("Input_0", results.errors().get(1).input());
      assertJsonEqual("[\"MyPatient\"]", map(results.fhir(), r -> at(r, "id")));
    }

    @Test
    @DisplayName("#FshToFhir #EndToEnd should not carry errors or warnings over from a previous call")
    void shouldNotCarryErrorsOrWarningsOverFromAPreviousCall() {
      var failed = fshToFhir("Profile: MyPatient\nParent: FakeProfile");
      assertEquals(1, failed.errors().size());

      var results = fshToFhir("Profile: MyPatient\nParent: Patient");
      assertEquals(0, results.errors().size());
      assertEquals(0, results.warnings().size());
      assertJsonEqual("[\"MyPatient\"]", map(results.fhir(), r -> at(r, "id")));
    }
  }
}
