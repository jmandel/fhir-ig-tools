package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertContainsText;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsonEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Extension;
import org.hl7.fhir.sushi.fshtypes.ExtensionContext;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.Instance;
import org.hl7.fhir.sushi.fshtypes.InstanceUsage;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/export/StructureDefinition.ExtensionExporter.test.ts (SUSHI 3.21.1), describe blocks
// 'ExtensionExporter', '#context', '#withCustomResource'
// The TS beforeEach is the same as SdeTestBase's; only the beforeAll defs differ (r4-definitions + mvc-extension).
@DisplayName("ExtensionExporter")
class StructureDefinitionExtensionExporterTest extends SdeTestBase {

  @Override
  TestFHIRDefinitions loadDefs() {
    return defs(true, testDefsPath("r4-definitions"), testDefsPath("mvc-extension.json"));
  }

  /** TS `extension.contexts = [{ value, isQuoted }, ...]`. */
  private static List<ExtensionContext> contexts(ExtensionContext... cs) {
    return new ArrayList<>(List.of(cs));
  }

  @Test
  @DisplayName("ExtensionExporter should output empty results with empty input")
  void emptyInput() {
    List<StructureDefinition> exported = exporter.export().extensions;
    assertJsEqual(List.of(), exported);
  }

  @Test
  @DisplayName("ExtensionExporter should export a single extension")
  void singleExtension() {
    Extension extension = new Extension("Foo");
    doc.extensions.put(extension.name, extension);
    List<StructureDefinition> exported = exporter.export().extensions;
    assertEquals(1, exported.size());
  }

  @Test
  @DisplayName("ExtensionExporter should add source info for the exported extension to the package")
  void sourceInfoInFshMap() {
    Extension extension = new Extension("Foo").<Extension>withFile("GoodExtensions.fsh").withLocation(38, 4, 49, 15);
    doc.extensions.put(extension.name, extension);
    List<StructureDefinition> exported = exporter.export().extensions;
    assertEquals(1, exported.size());
    assertEquals(
        new Package.FshMapEntry(new SourceInfo("GoodExtensions.fsh", new TextLocation(38, 4, 49, 15)), "Foo",
            "Extension"),
        pkg.fshMap.get("StructureDefinition-Foo.json"));
  }

  @Test
  @DisplayName("ExtensionExporter should export multiple extensions")
  void multipleExtensions() {
    Extension extensionFoo = new Extension("Foo");
    Extension extensionBar = new Extension("Bar");
    doc.extensions.put(extensionFoo.name, extensionFoo);
    doc.extensions.put(extensionBar.name, extensionBar);
    List<StructureDefinition> exported = exporter.export().extensions;
    assertEquals(2, exported.size());
  }

  @Test
  @DisplayName("ExtensionExporter should still export extensions if one fails")
  void stillExportIfOneFails() {
    Extension extensionFoo = new Extension("Foo");
    extensionFoo.parent = "Baz";
    Extension extensionBar = new Extension("Bar");
    doc.extensions.put(extensionFoo.name, extensionFoo);
    doc.extensions.put(extensionBar.name, extensionBar);
    List<StructureDefinition> exported = exporter.export().extensions;
    assertEquals(1, exported.size());
    assertEquals("Bar", at(exported.get(0), "name"));
  }

  @Test
  @DisplayName("ExtensionExporter should log a message with source information when the parent is not found")
  void parentNotFound() {
    Extension extension = new Extension("Wrong").<Extension>withFile("Wrong.fsh").withLocation(14, 8, 24, 17);
    extension.parent = "DoesNotExist";
    doc.extensions.put(extension.name, extension);
    exporter.export();
    assertMatches("File: Wrong\\.fsh.*Line: 14 - 24\\D*", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ExtensionExporter should log a message with source information when the parent is not an extension")
  void parentNotAnExtension() {
    Extension extension = new Extension("Wrong").<Extension>withFile("Wrong.fsh").withLocation(14, 8, 24, 17);
    extension.parent = "Patient";
    doc.extensions.put(extension.name, extension);
    exporter.export();
    assertMatches(
        "The parent of an extension must be the base Extension or another defined extension.*File: Wrong\\.fsh.*Line: 14 - 24\\D*",
        "s", loggerSpy.getLastMessage("error"));
  }

  // Note: some TS tests below write `expect(a.baseDefinition === b.url);` without a matcher, which asserts nothing;
  // those lines are kept as comments.

  @Test
  @DisplayName("ExtensionExporter should export extensions with FSHy parents")
  void fshyParents() {
    Extension extensionFoo = new Extension("Foo");
    Extension extensionBar = new Extension("Bar");
    extensionBar.parent = "Foo";
    doc.extensions.put(extensionFoo.name, extensionFoo);
    doc.extensions.put(extensionBar.name, extensionBar);
    List<StructureDefinition> exported = exporter.export().extensions;
    assertEquals(2, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("Bar", at(exported.get(1), "name"));
    assertEquals(at(exported.get(0), "url"), at(exported.get(1), "baseDefinition"));
    Object parentFixedUri = at(exported.get(0).findElement("Extension.url"), "fixedUri");
    Object childFixedUri = at(exported.get(1).findElement("Extension.url"), "fixedUri");
    assertEquals(childFixedUri, parentFixedUri);
  }

  @Test
  @DisplayName("ExtensionExporter should export extensions with the same FSHy parents")
  void sameFshyParents() {
    Extension extensionFoo = new Extension("Foo");
    Extension extensionBar = new Extension("Bar");
    extensionBar.parent = "Foo";
    Extension extensionBaz = new Extension("Baz");
    extensionBaz.parent = "Foo";
    doc.extensions.put(extensionFoo.name, extensionFoo);
    doc.extensions.put(extensionBar.name, extensionBar);
    doc.extensions.put(extensionBaz.name, extensionBaz);
    List<StructureDefinition> exported = exporter.export().extensions;
    assertEquals(3, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("Bar", at(exported.get(1), "name"));
    assertEquals("Baz", at(exported.get(2), "name"));
    // TS: expect(exported[1].baseDefinition === exported[0].url);
    // TS: expect(exported[2].baseDefinition === exported[0].url);
  }

  @Test
  @DisplayName("ExtensionExporter should export extensions with deep FSHy parents")
  void deepFshyParents() {
    Extension extensionFoo = new Extension("Foo");
    Extension extensionBar = new Extension("Bar");
    extensionBar.parent = "Foo";
    Extension extensionBaz = new Extension("Baz");
    extensionBaz.parent = "Bar";
    doc.extensions.put(extensionFoo.name, extensionFoo);
    doc.extensions.put(extensionBar.name, extensionBar);
    doc.extensions.put(extensionBaz.name, extensionBaz);
    List<StructureDefinition> exported = exporter.export().extensions;
    assertEquals(3, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("Bar", at(exported.get(1), "name"));
    assertEquals("Baz", at(exported.get(2), "name"));
    // TS: expect(exported[1].baseDefinition === exported[0].url);
    // TS: expect(exported[2].baseDefinition === exported[1].url);
  }

  @Test
  @DisplayName("ExtensionExporter should export extensions with out-of-order FSHy parents")
  void outOfOrderFshyParents() {
    Extension extensionFoo = new Extension("Foo");
    extensionFoo.parent = "Bar";
    Extension extensionBar = new Extension("Bar");
    extensionBar.parent = "Baz";
    Extension extensionBaz = new Extension("Baz");
    doc.extensions.put(extensionFoo.name, extensionFoo);
    doc.extensions.put(extensionBar.name, extensionBar);
    doc.extensions.put(extensionBaz.name, extensionBaz);
    List<StructureDefinition> exported = exporter.export().extensions;
    assertEquals(3, exported.size());
    assertEquals("Baz", at(exported.get(0), "name"));
    assertEquals("Bar", at(exported.get(1), "name"));
    assertEquals("Foo", at(exported.get(2), "name"));
    // TS: expect(exported[1].baseDefinition === exported[0].url);
    // TS: expect(exported[2].baseDefinition === exported[1].url);
  }

  @Test
  @DisplayName("ExtensionExporter should not log an error when an inline extension is used")
  void inlineExtensionNoError() {
    loggerSpy.reset();
    Extension extension = new Extension("MyExtension");
    ContainsRule containsRule = new ContainsRule("extension").<ContainsRule>withFile("MyExtension.fsh")
        .withLocation(3, 8, 3, 25);
    containsRule.items.add(new ContainsRuleItem("SomeExtension"));
    extension.rules.add(containsRule);
    doc.extensions.put(extension.name, extension);
    exporter.export();

    assertEquals(0, loggerSpy.getAllLogs("error").size());
  }

  @Test
  @DisplayName("ExtensionExporter should export extensions with extension instance parents")
  void extensionInstanceParents() {
    Instance parentExtensionInstance = new Instance("ParentExtension");
    parentExtensionInstance.instanceOf = "StructureDefinition";
    parentExtensionInstance.usage = InstanceUsage.Definition;
    AssignmentRule parentName = new AssignmentRule("name");
    parentName.value = "ParentExtension";
    AssignmentRule parentStatus = new AssignmentRule("status");
    parentStatus.value = new FshCode("active");
    AssignmentRule parentKind = new AssignmentRule("kind");
    parentKind.value = new FshCode("resource");
    AssignmentRule parentAbstract = new AssignmentRule("abstract");
    parentAbstract.value = false;
    AssignmentRule parentType = new AssignmentRule("type");
    parentType.value = "Extension";
    AssignmentRule parentDerivation = new AssignmentRule("derivation");
    parentDerivation.value = new FshCode("constraint");
    AssignmentRule parentBaseDefinition = new AssignmentRule("baseDefinition");
    parentBaseDefinition.value = "http://hl7.org/fhir/StructureDefinition/Extension";
    AssignmentRule parentElementId = new AssignmentRule("snapshot.element[0].id");
    parentElementId.value = "Extension";
    AssignmentRule parentElementPath = new AssignmentRule("snapshot.element[0].path");
    parentElementPath.value = "Extension";
    AssignmentRule parentUrlId = new AssignmentRule("snapshot.element[1].id");
    parentUrlId.value = "Extension.url";
    AssignmentRule parentUrlPath = new AssignmentRule("snapshot.element[1].path");
    parentUrlPath.value = "Extension.url";
    parentExtensionInstance.rules.addAll(List.of(parentName, parentStatus, parentKind, parentAbstract, parentType,
        parentDerivation, parentBaseDefinition, parentElementId, parentElementPath, parentUrlId, parentUrlPath));
    doc.instances.put(parentExtensionInstance.name, parentExtensionInstance);

    Extension childExtension = new Extension("ChildExtension");
    childExtension.parent = "ParentExtension";
    doc.extensions.put(childExtension.name, childExtension);
    List<StructureDefinition> exported = exporter.export().extensions;
    assertEquals(1, exported.size());
    assertEquals("ChildExtension", at(exported.get(0), "name"));
    assertEquals("http://hl7.org/fhir/us/minimal/StructureDefinition/ParentExtension",
        at(exported.get(0), "baseDefinition"));
    assertEquals(0, loggerSpy.getAllMessages("error").size());
  }

  @Nested
  @DisplayName("#context")
  class Context {

    @Test
    @DisplayName("ExtensionExporter #context should set extension context by a quoted string")
    void quotedString() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(new ExtensionContext("some.fhirpath.expression", true));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "fhirpath",
              "expression": "some.fhirpath.expression"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context for an extension by url")
    void extensionByUrl() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(
          new ExtensionContext("http://hl7.org/fhir/StructureDefinition/cqf-library", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "extension",
              "expression": "http://hl7.org/fhir/StructureDefinition/cqf-library"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context for an extension by name")
    void extensionByName() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(new ExtensionContext("library", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "extension",
              "expression": "http://hl7.org/fhir/StructureDefinition/cqf-library"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context for an extension by id")
    void extensionById() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(new ExtensionContext("cqf-library", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "extension",
              "expression": "http://hl7.org/fhir/StructureDefinition/cqf-library"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context for a base resource root element by id/name")
    void baseResourceRootByIdOrName() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(new ExtensionContext("Observation", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "element",
              "expression": "Observation"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context for a base resource root element by url")
    void baseResourceRootByUrl() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(
          new ExtensionContext("http://hl7.org/fhir/StructureDefinition/Observation", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "element",
              "expression": "Observation"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context for a base resource by id with a FSH path")
    void baseResourceByIdWithPath() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(new ExtensionContext("Observation.component.valueQuantity", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "element",
              "expression": "Observation.component.value[x]:valueQuantity"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context to itself by url")
    void itselfByUrl() {
      Extension extension = new Extension("MyExtension");
      extension.setId("my-extension");
      extension.contexts = contexts(
          new ExtensionContext("http://hl7.org/fhir/us/minimal/StructureDefinition/my-extension", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "extension",
              "expression": "http://hl7.org/fhir/us/minimal/StructureDefinition/my-extension"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context to itself by name")
    void itselfByName() {
      Extension extension = new Extension("MyExtension");
      extension.setId("my-extension");
      extension.contexts = contexts(new ExtensionContext("MyExtension", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "extension",
              "expression": "http://hl7.org/fhir/us/minimal/StructureDefinition/my-extension"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context to itself by id")
    void itselfById() {
      Extension extension = new Extension("MyExtension");
      extension.setId("my-extension");
      extension.contexts = contexts(new ExtensionContext("my-extension", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "extension",
              "expression": "http://hl7.org/fhir/us/minimal/StructureDefinition/my-extension"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context for a base resource by url with a FSH path")
    void baseResourceByUrlWithPath() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(new ExtensionContext(
          "http://hl7.org/fhir/StructureDefinition/Observation#component.valueQuantity", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "element",
              "expression": "Observation.component.value[x]:valueQuantity"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context for a base resource (with no derivation) root element by id/name")
    void noDerivationRootByIdOrName() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(new ExtensionContext("Resource", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "element",
              "expression": "Resource"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context for a base resource (with no derivation) root element by url")
    void noDerivationRootByUrl() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(new ExtensionContext("http://hl7.org/fhir/StructureDefinition/Element", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "element",
              "expression": "Element"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context for a base resource (with no derivation) by id with a FSH path")
    void noDerivationByIdWithPath() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(new ExtensionContext("Resource.language", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "element",
              "expression": "Resource.language"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context for a base resource (with no derivation) by url with a FSH path")
    void noDerivationByUrlWithPath() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(new ExtensionContext("http://hl7.org/fhir/StructureDefinition/Element#id", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "element",
              "expression": "Element.id"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context with type \"extension\" when the path is part of a complex extension by name")
    void complexExtensionPartByName() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(new ExtensionContext("proficiency.extension[level]", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "extension",
              "expression": "http://hl7.org/fhir/StructureDefinition/patient-proficiency#level"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context with type \"extension\" when the path is part of a complex extension by url")
    void complexExtensionPartByUrl() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(new ExtensionContext(
          "http://hl7.org/fhir/StructureDefinition/patient-proficiency#extension[level]", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "extension",
              "expression": "http://hl7.org/fhir/StructureDefinition/patient-proficiency#level"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context with type \"extension\" when the path is its own sub-extension by name")
    void ownSubExtensionByName() {
      Extension extension = new Extension("MyExtension");
      extension.setId("my-extension");
      extension.contexts = contexts(new ExtensionContext("MyExtension.extension[foo]", false));
      ContainsRule containsRule = new ContainsRule("extension");
      containsRule.items = new ArrayList<>(List.of(new ContainsRuleItem("foo")));
      extension.rules.add(containsRule);
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "extension",
              "expression": "http://hl7.org/fhir/us/minimal/StructureDefinition/my-extension#foo"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context with type \"extension\" when the path is is its own sub-extension by url")
    void ownSubExtensionByUrl() {
      Extension extension = new Extension("MyExtension");
      extension.setId("my-extension");
      extension.contexts = contexts(new ExtensionContext(
          "http://hl7.org/fhir/us/minimal/StructureDefinition/my-extension#extension[foo]", false));
      ContainsRule containsRule = new ContainsRule("extension");
      containsRule.items = new ArrayList<>(List.of(new ContainsRuleItem("foo")));
      extension.rules.add(containsRule);
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "extension",
              "expression": "http://hl7.org/fhir/us/minimal/StructureDefinition/my-extension#foo"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context with type \"extension\" when the path is a deep part of a complex extension by name")
    void deepComplexExtensionPartByName() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(
          new ExtensionContext("MyVeryComplexExtension#extension[foo].extension[bigFoo]", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "extension",
              "expression": "http://example.org/StructureDefinition/mvc-extension#foo.bigFoo"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context with type \"element\" when the path is a deep part of a complex extension, but contains non-extension elements")
    void deepComplexExtensionPartWithNonExtensionElements() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(
          new ExtensionContext("MyVeryComplexExtension#extension[bar].value[x].extension[secretBar]", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "element",
              "expression": "http://example.org/StructureDefinition/mvc-extension#Extension.extension:bar.value[x].extension:secretBar"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should set extension context when an alias is used for a resource URL")
    void aliasForResourceUrl() {
      doc.aliases.put("$PROF", "http://hl7.org/fhir/StructureDefinition/patient-proficiency");
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(new ExtensionContext("$PROF#extension[level]", false));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertJsonEqual("""
          [
            {
              "type": "extension",
              "expression": "http://hl7.org/fhir/StructureDefinition/patient-proficiency#level"
            }
          ]
          """, at(exported, "context"));
    }

    @Test
    @DisplayName("ExtensionExporter #context should log an error when no extension or resource can be found with the provided value")
    void contextNotFound() {
      Extension extension = new Extension("MyExtension");
      extension.contexts = contexts(new ExtensionContext("MysteryExtension", false,
          new SourceInfo("Context.fsh", new TextLocation(8, 7, 8, 25))));
      doc.extensions.put(extension.name, extension);
      StructureDefinition exported = exporter.exportStructDef(extension);
      assertNull(at(exported, "context"));
      assertContainsText("Could not find resource MysteryExtension to use as extension context.",
          loggerSpy.getLastMessage("error"));
      assertMatches("File: Context\\.fsh.*Line: 8\\D*", "s", loggerSpy.getLastMessage("error"));
    }

    @Nested
    @DisplayName("#withCustomResource")
    class WithCustomResource {
      Profile myObservation;

      @BeforeEach
      void setUp() {
        myObservation = new Profile("MyObservation");
        myObservation.parent = "Observation";
        myObservation.setId("my-obs");
        doc.profiles.put(myObservation.name, myObservation);
      }

      @Test
      @DisplayName("ExtensionExporter #context #withCustomResource should set extension context for a custom resource root element by id")
      void customRootById() {
        Extension extension = new Extension("MyExtension");
        extension.contexts = contexts(new ExtensionContext("my-obs", false));
        doc.extensions.put(extension.name, extension);
        StructureDefinition exported = exporter.exportStructDef(extension);
        assertJsonEqual("""
            [
              {
                "type": "element",
                "expression": "http://hl7.org/fhir/us/minimal/StructureDefinition/my-obs#Observation"
              }
            ]
            """, at(exported, "context"));
      }

      @Test
      @DisplayName("ExtensionExporter #context #withCustomResource should set extension context for a custom resource root element by name")
      void customRootByName() {
        Extension extension = new Extension("MyExtension");
        extension.contexts = contexts(new ExtensionContext("MyObservation", false));
        doc.extensions.put(extension.name, extension);
        StructureDefinition exported = exporter.exportStructDef(extension);
        assertJsonEqual("""
            [
              {
                "type": "element",
                "expression": "http://hl7.org/fhir/us/minimal/StructureDefinition/my-obs#Observation"
              }
            ]
            """, at(exported, "context"));
      }

      @Test
      @DisplayName("ExtensionExporter #context #withCustomResource should set extension context for a custom resource root element by url")
      void customRootByUrl() {
        Extension extension = new Extension("MyExtension");
        extension.contexts = contexts(
            new ExtensionContext("http://hl7.org/fhir/us/minimal/StructureDefinition/my-obs", false));
        doc.extensions.put(extension.name, extension);
        StructureDefinition exported = exporter.exportStructDef(extension);
        assertJsonEqual("""
            [
              {
                "type": "element",
                "expression": "http://hl7.org/fhir/us/minimal/StructureDefinition/my-obs#Observation"
              }
            ]
            """, at(exported, "context"));
      }

      @Test
      @DisplayName("ExtensionExporter #context #withCustomResource should set extension context for a custom resource by id with a FSH path")
      void customByIdWithPath() {
        Extension extension = new Extension("MyExtension");
        extension.contexts = contexts(new ExtensionContext("my-obs.component.valueQuantity", false));
        doc.extensions.put(extension.name, extension);
        StructureDefinition exported = exporter.exportStructDef(extension);
        assertJsonEqual("""
            [
              {
                "type": "element",
                "expression": "http://hl7.org/fhir/us/minimal/StructureDefinition/my-obs#Observation.component.value[x]:valueQuantity"
              }
            ]
            """, at(exported, "context"));
      }

      @Test
      @DisplayName("ExtensionExporter #context #withCustomResource should set extension context for a custom resource by name with a FSH path")
      void customByNameWithPath() {
        Extension extension = new Extension("MyExtension");
        extension.contexts = contexts(new ExtensionContext("MyObservation.component.valueQuantity", false));
        doc.extensions.put(extension.name, extension);
        StructureDefinition exported = exporter.exportStructDef(extension);
        assertJsonEqual("""
            [
              {
                "type": "element",
                "expression": "http://hl7.org/fhir/us/minimal/StructureDefinition/my-obs#Observation.component.value[x]:valueQuantity"
              }
            ]
            """, at(exported, "context"));
      }

      @Test
      @DisplayName("ExtensionExporter #context #withCustomResource should set extension context for a custom resource by url with a FSH path")
      void customByUrlWithPath() {
        Extension extension = new Extension("MyExtension");
        extension.contexts = contexts(new ExtensionContext(
            "http://hl7.org/fhir/us/minimal/StructureDefinition/my-obs#component.valueQuantity", false));
        doc.extensions.put(extension.name, extension);
        StructureDefinition exported = exporter.exportStructDef(extension);
        assertJsonEqual("""
            [
              {
                "type": "element",
                "expression": "http://hl7.org/fhir/us/minimal/StructureDefinition/my-obs#Observation.component.value[x]:valueQuantity"
              }
            ]
            """, at(exported, "context"));
      }

      @Test
      @DisplayName("ExtensionExporter #context #withCustomResource should set extension context for a custom resource by url when the url contains a # character")
      void customByUrlContainingHash() {
        // add a rule to our profile to set the url
        CaretValueRule observationUrl = new CaretValueRule("");
        observationUrl.caretPath = "url";
        observationUrl.value = "http://hl7.org/fhir/us/minimal/StructureDefinition/my-profiles#obs";
        myObservation.rules.add(observationUrl);
        Extension extension = new Extension("MyExtension");
        extension.contexts = contexts(new ExtensionContext(
            "http://hl7.org/fhir/us/minimal/StructureDefinition/my-profiles#obs#component.valueQuantity", false));
        doc.extensions.put(extension.name, extension);
        StructureDefinition exported = exporter.exportStructDef(extension);
        assertJsonEqual("""
            [
              {
                "type": "element",
                "expression": "http://hl7.org/fhir/us/minimal/StructureDefinition/my-profiles#obs#Observation.component.value[x]:valueQuantity"
              }
            ]
            """, at(exported, "context"));
      }

      @Test
      @DisplayName("ExtensionExporter #context #withCustomResource should log an error when a custom resource element is specified with an invalid FSH path")
      void customInvalidPath() {
        Extension extension = new Extension("MyExtension");
        extension.contexts = contexts(new ExtensionContext("MyObservation.component.valueToast", false,
            new SourceInfo("Context.fsh", new TextLocation(19, 40, 19, 77))));
        doc.extensions.put(extension.name, extension);
        StructureDefinition exported = exporter.exportStructDef(extension);
        assertNull(at(exported, "context"));
        assertContainsText(
            "Could not find element component.valueToast on resource MyObservation to use as extension context.",
            loggerSpy.getLastMessage("error"));
        assertMatches("File: Context\\.fsh.*Line: 19\\D*", "s", loggerSpy.getLastMessage("error"));
      }
    }
  }
}
