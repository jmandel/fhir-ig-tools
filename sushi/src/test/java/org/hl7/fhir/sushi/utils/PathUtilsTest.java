package org.hl7.fhir.sushi.utils;

import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fhirtypes.InstanceDefinition;
import org.hl7.fhir.sushi.fhirtypes.PathPart;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.common.js.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/utils/PathUtils.test.ts (SUSHI 3.21.1)
@DisplayName("PathUtils")
class PathUtilsTest {
  @BeforeEach
  void reset() {
    loggerSpy.reset();
  }

  static List<Rule> rules(String... paths) {
    List<Rule> l = new ArrayList<>();
    for (String p : paths) l.add(new Rule(p));
    return l;
  }

  static List<String> paths(List<? extends Rule> rules) {
    List<String> l = new ArrayList<>();
    for (Rule r : rules) l.add(r.path);
    return l;
  }

  static List<String> caretPaths(List<? extends Rule> rules) {
    List<String> l = new ArrayList<>();
    for (Rule r : rules) l.add(r instanceof CaretValueRule c ? c.caretPath : null);
    return l;
  }

  static CaretValueRule caret(String path, String caretPath) {
    CaretValueRule c = new CaretValueRule(path);
    c.caretPath = caretPath;
    return c;
  }

  @Nested
  @DisplayName("#resolveSoftIndexing")
  class ResolveSoftIndexing {
    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve simple (non-nested) soft indexing")
    void simple() {
      List<Rule> rules = rules("name[+]", "name[=]", "name[=]", "name[+]", "name[=]");
      PathUtils.resolveSoftIndexing(rules);
      assertEquals(List.of("name[0]", "name[0]", "name[0]", "name[1]", "name[1]"), paths(rules));
    }

    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve nested soft indexing w/ recursive paths")
    void nestedRecursive() {
      List<Rule> rules = rules("item[+].item[+].item[+]", "item[=].item[+].item[+]", "item[=].item[=].item[+]",
          "item[=].item[=].item[=]");
      PathUtils.resolveSoftIndexing(rules);
      assertEquals(List.of("item[0].item[0].item[0]", "item[0].item[1].item[0]", "item[0].item[1].item[1]",
          "item[0].item[1].item[1]"), paths(rules));
    }

    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve soft indexing mixed w/ numeric indexes")
    void mixedNumeric() {
      List<Rule> rules = rules("item[+].item[+].item[0]", "item[0].item[+].item[+]", "item[=].item[2].item[+]",
          "item[=].item[=].item[1]");
      PathUtils.resolveSoftIndexing(rules);
      assertEquals(List.of("item[0].item[0].item[0]", "item[0].item[1].item[0]", "item[0].item[2].item[0]",
          "item[0].item[2].item[1]"), paths(rules));
    }

    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve soft indexing on caret paths")
    void caretPathsOnly() {
      List<CaretValueRule> rules = List.of(caret("", "contact[+].name"), caret("", "contact[=].phone"),
          caret("", "contact[=].email"), caret("", "contact[+].phone"));
      PathUtils.resolveSoftIndexing(rules);
      assertEquals(List.of("contact[0].name", "contact[0].phone", "contact[0].email", "contact[1].phone"),
          caretPaths(rules));
    }

    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve soft indexing on caret paths and regular paths")
    void caretAndRegular() {
      List<CaretValueRule> rules = List.of(caret("item[+]", "contact[+].name"), caret("item[=]", "contact[=].phone"),
          caret("item[0]", "contact[+].email"), caret("item[+]", "contact[+].phone"));
      PathUtils.resolveSoftIndexing(rules);
      assertEquals(List.of("item[0]", "item[0]", "item[0]", "item[1]"), paths(rules));
      assertEquals(List.of("contact[0].name", "contact[0].phone", "contact[1].email", "contact[0].phone"),
          caretPaths(rules));
    }

    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve soft indexing on regular paths with slicing")
    void slicing() {
      List<Rule> rules = rules("item[Slice1][+]", "item[Slice1][=]", "item[Slice2][+]", "item[Slice2][+]");
      PathUtils.resolveSoftIndexing(rules);
      assertEquals(List.of("item[Slice1][0]", "item[Slice1][0]", "item[Slice2][0]", "item[Slice2][1]"),
          paths(rules));
    }

    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve nested soft indexing w/ similar nested path items")
    void similarNested() {
      List<Rule> rules = rules("compose.include[+].concept[+]", "compose.include[=].concept[+]",
          "compose.exclude[+].concept[+]", "compose.exclude[=].concept[+]");
      PathUtils.resolveSoftIndexing(rules);
      assertEquals(List.of("compose.include[0].concept[0]", "compose.include[0].concept[1]",
          "compose.exclude[0].concept[0]", "compose.exclude[0].concept[1]"), paths(rules));
    }

    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve improper soft indexing, but log an error")
    void improper() {
      List<Rule> rules = rules("name[=]", "name[=]", "name[=]", "name[+]", "name[=]");
      PathUtils.resolveSoftIndexing(rules);
      assertEquals(List.of("name[0]", "name[0]", "name[0]", "name[1]", "name[1]"), paths(rules));
      assertTrue(loggerSpy.getLastMessage("error").contains(
          "The first index in a Soft Indexing sequence must be \"+\", an actual index of \"0\" has been assumed"));
    }

    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve soft indexing beginning with a non-zero index")
    void nonZeroStart() {
      List<Rule> rules = rules("name[2]", "name[=]", "name[=]", "name[+]", "name[=]");
      PathUtils.resolveSoftIndexing(rules);
      assertEquals(List.of("name[2]", "name[2]", "name[2]", "name[3]", "name[3]"), paths(rules));
    }

    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve strict soft indexing beginning with a named slice")
    void strictNamedSlice() {
      List<Rule> rules = rules("component[Bread]", "component[+]", "component[=]", "component[+]");
      PathUtils.resolveSoftIndexing(rules, true);
      assertEquals(List.of("component[Bread]", "component[1]", "component[1]", "component[2]"), paths(rules));
    }

    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve strict soft indexing that contains multiple named slices")
    void strictMultipleNamedSlices() {
      List<Rule> rules = rules("component[Bread]", "component[+]", "component[=]", "component[Toast]",
          "component[+]");
      PathUtils.resolveSoftIndexing(rules, true);
      assertEquals(List.of("component[Bread]", "component[1]", "component[1]", "component[Toast]", "component[3]"),
          paths(rules));
    }

    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve strict soft indexing that applies to both an element and its slices")
    void strictElementAndSlices() {
      List<Rule> rules = rules("component[+]", "component[=]", "component[Bread][+]", "component[Bread][=]",
          "component[Bread][+]", "component[+]");
      PathUtils.resolveSoftIndexing(rules, true);
      assertEquals(List.of("component[0]", "component[0]", "component[Bread][0]", "component[Bread][0]",
          "component[Bread][1]", "component[3]"), paths(rules));
    }

    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve strict soft indexing that applies to an element, its slices, and its reslices")
    void strictReslices() {
      List<Rule> rules = rules("component[Bread][Rye][+]", "component[Bread][+]", "component[+]",
          "component[Bread][+]", "component[Bread][=]", "component[+]");
      PathUtils.resolveSoftIndexing(rules, true);
      assertEquals(List.of("component[Bread][Rye][0]", "component[Bread][1]", "component[2]", "component[Bread][2]",
          "component[Bread][2]", "component[4]"), paths(rules));
    }

    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve strict soft indexing with named slices and numbered indices")
    void strictNumbered() {
      List<Rule> rules = rules("component[+]", "component[=]", "component[Bread][1]", "component[Bread][=]",
          "component[Bread][+]", "component[+]");
      PathUtils.resolveSoftIndexing(rules, true);
      assertEquals(List.of("component[0]", "component[0]", "component[Bread][1]", "component[Bread][1]",
          "component[Bread][2]", "component[4]"), paths(rules));
    }

    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve strict soft indexing with named slices and out-of-order numbered indices")
    void strictOutOfOrder() {
      List<Rule> rules = rules("component[+]", "component[=]", "component[Bread][1]", "component[Bread][0]",
          "component[+]");
      PathUtils.resolveSoftIndexing(rules, true);
      assertEquals(List.of("component[0]", "component[0]", "component[Bread][1]", "component[Bread][0]",
          "component[3]"), paths(rules));
    }

    @Test
    @DisplayName("PathUtils #resolveSoftIndexing should resolve strict soft indexing with named slices and numbered indices that also have caret paths")
    void strictWithCaret() {
      List<Rule> rules = new ArrayList<>();
      rules.add(caret("item[+]", "contact[+].name"));
      rules.add(caret("item[Bread][+]", "contact[+].name"));
      rules.add(new AssignmentRule("item[Bread][=]"));
      rules.add(caret("item[Bread][=]", "contact[+].name"));
      rules.add(new AssignmentRule("item[+]"));
      rules.add(caret("item[Bread][+]", "contact[+].email"));
      PathUtils.resolveSoftIndexing(rules, true);
      assertEquals(List.of("item[0]", "item[Bread][0]", "item[Bread][0]", "item[Bread][0]", "item[2]",
          "item[Bread][1]"), paths(rules));
      assertEquals(Arrays.asList("contact[0].name", "contact[0].name", null, "contact[1].name", null,
          "contact[0].email"), caretPaths(rules));
    }
  }

  static PathPart pp(String base, List<String> brackets, List<String> slices) {
    PathPart p = new PathPart(base);
    p.brackets = brackets == null ? null : new ArrayList<>(brackets);
    p.slices = slices == null ? null : new ArrayList<>(slices);
    return p;
  }

  @Nested
  @DisplayName("#parseFSHPath")
  class ParseFSHPath {
    @Test
    @DisplayName("PathUtils #parseFSHPath should properly seperate path elements into PathParts")
    void simple() {
      List<PathPart> pathParts = PathUtils.parseFSHPath("item1.item2.item3");
      assertTrue(loggerSpy.getAllLogs("error").isEmpty());
      assertEquals(List.of(pp("item1", null, null), pp("item2", null, null), pp("item3", null, null)), pathParts);
    }

    @Test
    @DisplayName("PathUtils #parseFSHPath should properly seperate path elements with brackets into PathParts")
    void brackets() {
      List<PathPart> pathParts = PathUtils.parseFSHPath("item1[0].item2[0].item3[0]");
      assertTrue(loggerSpy.getAllLogs("error").isEmpty());
      assertEquals(List.of(pp("item1", List.of("0"), null), pp("item2", List.of("0"), null),
          pp("item3", List.of("0"), null)), pathParts);
    }

    @Test
    @DisplayName("PathUtils #parseFSHPath should properly seperate path elements with multi-digit brackets into PathParts")
    void multiDigit() {
      List<PathPart> pathParts = PathUtils.parseFSHPath("item1[10].item2[11].item3[12]");
      assertTrue(loggerSpy.getAllLogs("error").isEmpty());
      assertEquals(List.of(pp("item1", List.of("10"), null), pp("item2", List.of("11"), null),
          pp("item3", List.of("12"), null)), pathParts);
    }

    @Test
    @DisplayName("PathUtils #parseFSHPath should properly seperate path elements with slice names into PathParts")
    void sliceNames() {
      List<PathPart> pathParts = PathUtils.parseFSHPath("item1[10][Slice1].item2[11][Slice2].item3[12][Slice3]");
      assertTrue(loggerSpy.getAllLogs("error").isEmpty());
      assertEquals(List.of(pp("item1", List.of("10", "Slice1"), List.of("Slice1")),
          pp("item2", List.of("11", "Slice2"), List.of("Slice1", "Slice2")),
          pp("item3", List.of("12", "Slice3"), List.of("Slice1", "Slice2", "Slice3"))), pathParts);
    }

    @Test
    @DisplayName("PathUtils #parseFSHPath should properly seperate path elements with slice names including brackets into PathParts")
    void sliceNamesWithBrackets() {
      List<PathPart> pathParts = PathUtils.parseFSHPath("item1[10][foo[x]].item2[11][bar[x]][baz[x]].value[x]");
      assertTrue(loggerSpy.getAllLogs("error").isEmpty());
      assertEquals(List.of(pp("item1", List.of("10", "foo[x]"), List.of("foo[x]")),
          pp("item2", List.of("11", "bar[x]", "baz[x]"), List.of("foo[x]", "bar[x]", "baz[x]")),
          pp("value[x]", null, null)), pathParts);
    }

    @Test
    @DisplayName("PathUtils #parseFSHPath should properly seperate path elements with slice names on choice elements into PathParts")
    void choiceSlice() {
      List<PathPart> pathParts = PathUtils.parseFSHPath("value[x][foo]");
      assertTrue(loggerSpy.getAllLogs("error").isEmpty());
      assertEquals(List.of(pp("value[x]", List.of("foo"), List.of("foo"))), pathParts);
    }

    @Test
    @DisplayName("PathUtils #parseFSHPath should properly detect syntax errors in path elements (drawn from real world example)")
    void syntaxErrors() {
      SourceInfo sourceInfo = new SourceInfo("testfile.fsh", new TextLocation(5, 0, 5, 19));
      List<PathPart> pathParts = PathUtils.parseFSHPath("item1[+}.value[x]", sourceInfo);
      assertTrue(loggerSpy.getLastMessage("error")
          .contains("Error processing path due to unmatched brackets: item1[+}.value[x]"));
      assertTrue(Pattern.compile("File: testfile\\.fsh.*Line: 5\\D*", Pattern.DOTALL)
          .matcher(loggerSpy.getLastMessage("error")).find());
      assertEquals(List.of(pp("item1", List.of(), null), pp("value[x]", null, null)), pathParts);
    }
  }

  static void assertJsEqual(Object expected, Object actual) {
    assertTrue(Js.isEqual(expected, actual), () -> "expected " + Json.stringify(expected) + " but was "
        + Json.stringify(actual));
  }

  static Object json(String text) {
    return Json.parse(text);
  }

  @Nested
  @DisplayName("#collectValuesAtElementIdOrPath")
  class CollectValuesAtElementIdOrPath {
    InstanceDefinition object;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void load() throws Exception {
      Path p = Path.of(org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.tsDir(), "test", "testhelpers",
          "testdefs", "r4-definitions", "package", "Observation-20minute-apgar-score.json");
      assumeTrue(Files.exists(p), "TS checkout not available: " + p);
      object = InstanceDefinition.fromJSON((Map<String, Object>) Json.parseFile(Files.readString(p)));
    }

    PathUtils.CollectedValues collect(String path) {
      return PathUtils.collectValuesAtElementIdOrPath(path, object);
    }

    @SuppressWarnings("unchecked")
    List<Object> list(Object o, String key) {
      return (List<Object>) ((Map<String, Object>) o).get(key);
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect simple singular properties with resourceType prefix and resourceType")
    void t1() {
      var results = collect("Observation.status");
      assertJsEqual(List.of("final"), results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect simple singular properties with resourceType prefix and no resourceType")
    void t2() {
      object.remove("resourceType");
      var results = collect("Observation.status");
      assertJsEqual(List.of("final"), results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect simple singular properties without resourceType prefix but with resourceType")
    void t3() {
      var results = collect("status");
      assertJsEqual(List.of("final"), results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect simple singular properties without resourceType prefix and without resourceType")
    void t4() {
      object.remove("resourceType");
      var results = collect("status");
      assertJsEqual(List.of("final"), results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should NOT collect simple singular properties with resourceType prefix and wrong resourceType")
    void t5() {
      var results = collect("Condition.status");
      assertJsEqual(List.of(), results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect simple singular properties with wrong resourceType prefix and no resourceType")
    void t6() {
      object.remove("resourceType");
      var results = collect("Condition.status");
      assertJsEqual(List.of("final"), results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect nested singular properties")
    void t7() {
      var results = collect("Observation.code.text");
      assertJsEqual(List.of("20 minute Apgar Score"), results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect simple array properties with a single value")
    void t8() {
      var results = collect("Observation.performer");
      assertJsEqual(json("[{\"reference\": \"Practitioner/example\"}]"), results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect nested array properties with a single value")
    void t9() {
      var results = collect("Observation.code.coding");
      assertJsEqual(json("[{\"system\": \"http://snomed.info/sct\", \"code\": \"443849008\","
          + " \"display\": \"Apgar score at 20 minutes\"}]"), results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect single properties that are nested in arrays with a single value")
    void t10() {
      var results = collect("Observation.category.coding.code");
      assertJsEqual(List.of("survey"), results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect simple array properties with multiple values")
    void t11() {
      list(object, "performer").add(new JsObject().with("reference", "Practitioner/example2"));
      var results = collect("Observation.performer");
      assertJsEqual(json("[{\"reference\": \"Practitioner/example\"}, {\"reference\": \"Practitioner/example2\"}]"),
          results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect nested array properties with multiple values")
    void t12() {
      list(object.get("code"), "coding").add(json(
          "{\"system\": \"http://example.org\", \"code\": \"apgar\", \"display\": \"Apgar score\"}"));
      var results = collect("Observation.code.coding");
      assertJsEqual(json("[{\"system\": \"http://snomed.info/sct\", \"code\": \"443849008\","
          + " \"display\": \"Apgar score at 20 minutes\"},"
          + " {\"system\": \"http://example.org\", \"code\": \"apgar\", \"display\": \"Apgar score\"}]"),
          results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect properties that are nested in arrays with multiple elements in the leaf node")
    void t13() {
      list(list(object, "category").get(0), "coding").add(json(
          "{\"system\": \"http://example.org\", \"code\": \"example\", \"display\": \"Example\"}"));
      var results = collect("Observation.category.coding");
      assertJsEqual(json("[{\"system\": \"http://terminology.hl7.org/CodeSystem/observation-category\","
          + " \"code\": \"survey\", \"display\": \"Survey\"},"
          + " {\"system\": \"http://example.org\", \"code\": \"example\", \"display\": \"Example\"}]"),
          results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect properties that are nested in arrays with multiple elements in an intermediate node")
    void t14() {
      list(object, "category").add(json("{\"coding\": {\"system\": \"http://example.org\", \"code\": \"example\","
          + " \"display\": \"Example\"}, \"text\": \"Example\"}"));
      var results = collect("Observation.category.coding");
      assertJsEqual(json("[{\"system\": \"http://terminology.hl7.org/CodeSystem/observation-category\","
          + " \"code\": \"survey\", \"display\": \"Survey\"},"
          + " {\"system\": \"http://example.org\", \"code\": \"example\", \"display\": \"Example\"}]"),
          results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect nested singular properties embedded in multiple arrays")
    void t15() {
      var results = collect("Observation.component.code.coding.code");
      assertJsEqual(List.of("249227004", "249223000", "249226008", "249225007", "249224006"), results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect properties from choice types when a specific choice is specified")
    void t16() {
      var results = collect("Observation.value[x]:valueQuantity.value");
      assertJsEqual(List.of(10.0), results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should collect properties from choice types when a specific choice is not specified")
    void t17() {
      var results = collect("Observation.value[x].value");
      assertJsEqual(List.of(10.0), results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should not collect properties from choice types when a non-matching type is specified")
    void t18() {
      var results = collect("Observation.value[x]:valueString.value");
      assertJsEqual(List.of(), results.values());
      assertEquals(List.of(), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should ignore slicenames in the path, but remember what it ignored")
    void t19() {
      var results = collect("Observation.component:foo.code.coding:bar.code");
      assertJsEqual(List.of("249227004", "249223000", "249226008", "249225007", "249224006"), results.values());
      assertEquals(List.of("Observation.component:foo", "Observation.component:foo.code.coding:bar"),
          results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should ignore resliced slicenames in the path, but remember what it ignored")
    void t20() {
      var results = collect("Observation.component:foo/bar.code.coding.code");
      assertJsEqual(List.of("249227004", "249223000", "249226008", "249225007", "249224006"), results.values());
      assertEquals(List.of("Observation.component:foo/bar"), results.ignoredSliceRequirements());
    }

    @Test
    @DisplayName("PathUtils #collectValuesAtElementIdOrPath should ignore resliced slicenames in choice types, but remember what it ignored")
    void t21() {
      var results = collect("Observation.value[x]:valueQuantity/foo.value");
      assertJsEqual(List.of(10.0), results.values());
      assertEquals(List.of("Observation.value[x]:valueQuantity/foo"), results.ignoredSliceRequirements());
    }
  }
}
