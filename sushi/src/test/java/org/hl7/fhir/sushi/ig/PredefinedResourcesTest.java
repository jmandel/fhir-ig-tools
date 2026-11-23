package org.hl7.fhir.sushi.ig;

import static org.hl7.fhir.sushi.ig.PredefinedResources.PREDEFINED_PACKAGE_NAME;
import static org.hl7.fhir.sushi.ig.PredefinedResources.PREDEFINED_PACKAGE_VERSION;
import static org.hl7.fhir.sushi.ig.PredefinedResources.getPredefinedResourcePaths;
import static org.hl7.fhir.sushi.ig.PredefinedResources.loadPredefinedResources;
import static org.hl7.fhir.sushi.testhelpers.NodeTestEnv.*;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getTestFHIRDefinitions;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirdefs.FindResourceInfoOptions;
import org.hl7.fhir.sushi.fhirdefs.LoadStatus;
import org.hl7.fhir.common.js.JsObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/ig/predefinedResources.test.ts (SUSHI 3.21.1)
class PredefinedResourcesTest {
  static final String __dirname = dirname("ig");

  static List<Map<String, Object>> params(String... codeValue) {
    List<Map<String, Object>> l = new java.util.ArrayList<>();
    for (int i = 0; i < codeValue.length; i += 2) {
      l.add(new JsObject().with("code", codeValue[i]).with("value", codeValue[i + 1]));
    }
    return l;
  }

  @Nested
  @DisplayName("#getPredefinedResourcePaths")
  class GetPredefinedResourcePaths {
    @Test
    @DisplayName("#getPredefinedResourcePaths should return all typical IG resource paths when they all exist")
    void allTypical() {
      String inputDir = path.join(__dirname, "fixtures", "customized-ig-with-resources", "input");
      List<String> paths = getPredefinedResourcePaths(inputDir);
      assertEquals(List.of(path.join(inputDir, "capabilities"), path.join(inputDir, "extensions"),
          path.join(inputDir, "models"), path.join(inputDir, "operations"), path.join(inputDir, "profiles"),
          path.join(inputDir, "resources"), path.join(inputDir, "vocabulary"), path.join(inputDir, "examples")),
          paths);
    }

    @Test
    @DisplayName("#getPredefinedResourcePaths should return only the subset of typical IG resource paths that exist")
    void subset() {
      String inputDir = path.join(__dirname, "fixtures", "customized-ig-with-logical-model-example", "input");
      List<String> paths = getPredefinedResourcePaths(inputDir);
      assertEquals(List.of(path.join(inputDir, "models"), path.join(inputDir, "examples")), paths);
    }

    @Test
    @DisplayName("#getPredefinedResourcePaths should return no paths when none of the typical IG paths exist")
    void none() {
      String inputDir = path.join(__dirname, "fixtures", "customized-ig", "input");
      List<String> paths = getPredefinedResourcePaths(inputDir);
      assertTrue(paths.isEmpty());
    }

    @Test
    @DisplayName("#getPredefinedResourcePaths should ignore non-standard paths when no path-resource param is passed in")
    void ignoreNonStandard() {
      String inputDir = path.join(__dirname, "fixtures", "customized-ig-with-non-standard-resource-path", "input");
      List<String> paths = getPredefinedResourcePaths(inputDir);
      assertEquals(List.of(path.join(inputDir, "resources")), paths);
    }

    @Test
    @DisplayName("#getPredefinedResourcePaths should return non-standard paths that are specified by path-resource param")
    void nonStandardParam() {
      String projectDir = path.join(__dirname, "fixtures", "customized-ig-with-non-standard-resource-path");
      String inputDir = path.join(projectDir, "input");
      List<String> paths = getPredefinedResourcePaths(inputDir, projectDir, params("path-resource", "input/stuff"));
      assertEquals(List.of(path.join(inputDir, "resources"), path.join(inputDir, "stuff")), paths);
    }

    @Test
    @DisplayName("#getPredefinedResourcePaths should not return nested folders when no path-resource param is passed in")
    void noNested() {
      String inputDir = path.join(__dirname, "fixtures", "customized-ig-with-nested-resources", "input");
      List<String> paths = getPredefinedResourcePaths(inputDir);
      assertEquals(List.of(path.join(inputDir, "resources"), path.join(inputDir, "examples")), paths);
    }

    @Test
    @DisplayName("#getPredefinedResourcePaths should return nested folders specifically listed in the path-resource params")
    void nestedListed() {
      String projectDir = path.join(__dirname, "fixtures", "customized-ig-with-nested-resources");
      String inputDir = path.join(projectDir, "input");
      List<String> paths = getPredefinedResourcePaths(inputDir, projectDir,
          params("path-resource", "input/resources/nested1", "path-resource", "input/resources/nested2"));
      assertEquals(List.of(path.join(inputDir, "resources"), path.join(inputDir, "examples"),
          path.join(inputDir, "resources", "nested1"), path.join(inputDir, "resources", "nested2")), paths);
    }

    @Test
    @DisplayName("#getPredefinedResourcePaths should return all nested folders matching a wildcard directory in the path-resource params")
    void wildcard() {
      String projectDir = path.join(__dirname, "fixtures", "customized-ig-with-nested-resources");
      String inputDir = path.join(projectDir, "input");
      List<String> paths = getPredefinedResourcePaths(inputDir, projectDir,
          params("path-resource", "input/resources/*"));
      assertEquals(List.of(path.join(inputDir, "resources"), path.join(inputDir, "examples"),
          path.join(inputDir, "resources", "nested1"), path.join(inputDir, "resources", "nested2"),
          path.join(inputDir, "resources", "path-resource-double-nest"),
          path.join(inputDir, "resources", "path-resource-double-nest", "jack"),
          path.join(inputDir, "resources", "path-resource-double-nest", "jack", "examples"),
          path.join(inputDir, "resources", "path-resource-double-nest", "john"),
          path.join(inputDir, "resources", "path-resource-nest")), paths);
    }

    @Test
    @DisplayName("#getPredefinedResourcePaths should ignore invalid directories in the path-resource params")
    void invalidDirs() {
      String projectDir = path.join(__dirname, "fixtures", "customized-ig-with-nested-resources");
      String inputDir = path.join(projectDir, "input");
      List<String> paths = getPredefinedResourcePaths(inputDir, projectDir,
          params("path-resource", "input/i-dont-exist"));
      assertEquals(List.of(path.join(inputDir, "resources"), path.join(inputDir, "examples")), paths);
    }
  }

  @Nested
  @DisplayName("#loadPredefinedResources")
  class LoadPredefined {
    FHIRDefinitions defs;

    @BeforeEach
    void beforeEach() {
      defs = getTestFHIRDefinitions();
    }

    List<String> all() {
      return defs.findResourceInfos("*", FindResourceInfoOptions.NONE).stream()
          .map(info -> info.resourceType + "-" + info.id).toList();
    }

    @Test
    @DisplayName("#loadPredefinedResources should load all the resources in the typical resource paths for IGs")
    void loadAll() {
      String inputDir = path.join(__dirname, "fixtures", "customized-ig-with-resources", "input");
      LoadStatus status = loadPredefinedResources(defs, inputDir);
      assertEquals(LoadStatus.LOADED, status);
      assertEquals(LoadStatus.LOADED, defs.getPackageLoadStatus(PREDEFINED_PACKAGE_NAME, PREDEFINED_PACKAGE_VERSION));
      assertEquals(List.of("CapabilityStatement-MyCS", "Goal-GoalWithDescription", "Patient-BarPatient",
          "Patient-MetaExtensionPatient", "Patient-FooPatient", "TestScript-MyTestScript",
          "StructureDefinition-patient-birthPlace", "StructureDefinition-patient-birthPlaceXML",
          "StructureDefinition-MyLM", "OperationDefinition-AnotherOD", "OperationDefinition-MyOD",
          "StructureDefinition-MyPatient", "StructureDefinition-MyTitlePatient", "Patient-null", "Unknown-Patient",
          "Unknown-null", "Patient-MetaExtensionNotExamplePatient", "Patient-BazPatient", "ValueSet-MyVS"), all());
    }

    @Test
    @DisplayName("#loadPredefinedResources should load nested resources by default so it matches legacy behavior")
    void loadNested() {
      String inputDir = path.join(__dirname, "fixtures", "customized-ig-with-nested-resources", "input");
      LoadStatus status = loadPredefinedResources(defs, inputDir);
      assertEquals(LoadStatus.LOADED, status);
      assertEquals(LoadStatus.LOADED, defs.getPackageLoadStatus(PREDEFINED_PACKAGE_NAME, PREDEFINED_PACKAGE_VERSION));
      assertEquals(List.of("Patient-BarPatient", "StructureDefinition-MyPatient",
          "StructureDefinition-MyTitlePatient", "ValueSet-MyVS", "Patient-Jack", "Patient-John",
          "StructureDefinition-MyCorrectlyNestedPatient"), all());
    }

    @Test
    @DisplayName("#loadPredefinedResources should ignore non-standard paths when no path-resource param is passed in")
    void ignoreNonStandard() {
      String inputDir = path.join(__dirname, "fixtures", "customized-ig-with-non-standard-resource-path", "input");
      LoadStatus status = loadPredefinedResources(defs, inputDir);
      assertEquals(LoadStatus.LOADED, status);
      assertEquals(LoadStatus.LOADED, defs.getPackageLoadStatus(PREDEFINED_PACKAGE_NAME, PREDEFINED_PACKAGE_VERSION));
      assertEquals(List.of("StructureDefinition-MyPatient"), all());
    }

    @Test
    @DisplayName("#loadPredefinedResources should load non-standard paths that are specified by path-resource param")
    void loadNonStandardParam() {
      String projectDir = path.join(__dirname, "fixtures", "customized-ig-with-non-standard-resource-path");
      String inputDir = path.join(projectDir, "input");
      LoadStatus status = loadPredefinedResources(defs, inputDir, projectDir, params("path-resource", "input/stuff"));
      assertEquals(LoadStatus.LOADED, status);
      assertEquals(LoadStatus.LOADED, defs.getPackageLoadStatus(PREDEFINED_PACKAGE_NAME, PREDEFINED_PACKAGE_VERSION));
      assertEquals(List.of("StructureDefinition-MyPatient", "Patient-BarPatientInStuff"), all());
    }
  }
}
