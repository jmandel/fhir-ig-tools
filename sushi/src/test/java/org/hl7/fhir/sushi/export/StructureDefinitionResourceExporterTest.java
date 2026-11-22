package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertContainsText;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsonEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertMatches;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.MinimalConfig.minimalConfig;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.errors.ElementAlreadyDefinedError;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.FshCode;
import org.hl7.fhir.sushi.fshtypes.FshValueSet;
import org.hl7.fhir.sushi.fshtypes.Invariant;
import org.hl7.fhir.sushi.fshtypes.Profile;
import org.hl7.fhir.sushi.fshtypes.Resource;
import org.hl7.fhir.sushi.fshtypes.SourceInfo;
import org.hl7.fhir.sushi.fshtypes.TextLocation;
import org.hl7.fhir.sushi.fshtypes.rules.AddElementRule;
import org.hl7.fhir.sushi.fshtypes.rules.AssignmentRule;
import org.hl7.fhir.sushi.fshtypes.rules.BindingRule;
import org.hl7.fhir.sushi.fshtypes.rules.CardRule;
import org.hl7.fhir.sushi.fshtypes.rules.CaretValueRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRule;
import org.hl7.fhir.sushi.fshtypes.rules.ContainsRuleItem;
import org.hl7.fhir.sushi.fshtypes.rules.FlagRule;
import org.hl7.fhir.sushi.fshtypes.rules.ObeysRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRule;
import org.hl7.fhir.sushi.fshtypes.rules.OnlyRuleType;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// port of sushi/test/export/StructureDefinition.ResourceExporter.test.ts (SUSHI 3.21.1), describe 'ResourceExporter'
// The TS beforeEach is the same as SdeTestBase's; only the beforeAll defs differ (r4-definitions only).
@DisplayName("ResourceExporter")
class StructureDefinitionResourceExporterTest extends SdeTestBase {

  @Override
  TestFHIRDefinitions loadDefs() {
    return defs(true, testDefsPath("r4-definitions"));
  }

  private static List<OnlyRuleType> types(String type) {
    return new ArrayList<>(List.of(new OnlyRuleType(type)));
  }

  @Test
  @DisplayName("ResourceExporter should output empty results with empty input")
  void emptyInput() {
    List<StructureDefinition> exported = exporter.export().resources;
    assertJsEqual(List.of(), exported);
  }

  @Test
  @DisplayName("ResourceExporter should export a single resource")
  void singleResource() {
    Resource resource = new Resource("Foo");
    doc.resources.put(resource.name, resource);
    List<StructureDefinition> exported = exporter.export().resources;
    assertEquals(1, exported.size());
  }

  @Test
  @DisplayName("ResourceExporter should add source info for the exported resource to the package")
  void sourceInfoInFshMap() {
    Resource resource = new Resource("Tree").<Resource>withFile("Treesource.fsh").withLocation(68, 4, 72, 15);
    doc.resources.put(resource.name, resource);
    List<StructureDefinition> exported = exporter.export().resources;
    assertEquals(1, exported.size());
    assertEquals(
        new Package.FshMapEntry(new SourceInfo("Treesource.fsh", new TextLocation(68, 4, 72, 15)), "Tree",
            "Resource"),
        pkg.fshMap.get("StructureDefinition-Tree.json"));
  }

  @Test
  @DisplayName("ResourceExporter should export multiple resources")
  void multipleResources() {
    Resource resourceFoo = new Resource("Foo");
    Resource resourceBar = new Resource("Bar");
    doc.resources.put(resourceFoo.name, resourceFoo);
    doc.resources.put(resourceBar.name, resourceBar);
    List<StructureDefinition> exported = exporter.export().resources;
    assertEquals(2, exported.size());
  }

  @Test
  @DisplayName("ResourceExporter should still export resources if one fails")
  void stillExportIfOneFails() {
    Resource resourceFoo = new Resource("Foo");
    resourceFoo.parent = "Baz"; // invalid parent cause failure
    Resource resourceBar = new Resource("Bar");
    doc.resources.put(resourceFoo.name, resourceFoo);
    doc.resources.put(resourceBar.name, resourceBar);
    List<StructureDefinition> exported = exporter.export().resources;
    assertEquals(1, exported.size());
    assertEquals("Bar", at(exported.get(0), "name"));
  }

  @Test
  @DisplayName("ResourceExporter should export resource with Resource parent by id")
  void resourceParentById() {
    Resource resource = new Resource("Foo");
    resource.parent = "Resource";
    doc.resources.put(resource.name, resource);
    List<StructureDefinition> exported = exporter.export().resources;
    assertEquals(1, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("http://hl7.org/fhir/StructureDefinition/Resource", at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("ResourceExporter should export resource with Resource parent by url")
  void resourceParentByUrl() {
    Resource resource = new Resource("Foo");
    resource.parent = "http://hl7.org/fhir/StructureDefinition/Resource";
    doc.resources.put(resource.name, resource);
    List<StructureDefinition> exported = exporter.export().resources;
    assertEquals(1, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("http://hl7.org/fhir/StructureDefinition/Resource", at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("ResourceExporter should export resource with DomainResource parent by id")
  void domainResourceParentById() {
    Resource resource = new Resource("Foo");
    resource.parent = "DomainResource";
    doc.resources.put(resource.name, resource);
    List<StructureDefinition> exported = exporter.export().resources;
    assertEquals(1, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("http://hl7.org/fhir/StructureDefinition/DomainResource", at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("ResourceExporter should export resource with DomainResource parent by url")
  void domainResourceParentByUrl() {
    Resource resource = new Resource("Foo");
    resource.parent = "http://hl7.org/fhir/StructureDefinition/DomainResource";
    doc.resources.put(resource.name, resource);
    List<StructureDefinition> exported = exporter.export().resources;
    assertEquals(1, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("http://hl7.org/fhir/StructureDefinition/DomainResource", at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("ResourceExporter should export resource with DomainResource parent when parent not specified")
  void domainResourceParentDefault() {
    Resource resource = new Resource("Foo");
    doc.resources.put(resource.name, resource);
    List<StructureDefinition> exported = exporter.export().resources;
    assertEquals(1, exported.size());
    assertEquals("Foo", at(exported.get(0), "name"));
    assertEquals("http://hl7.org/fhir/StructureDefinition/DomainResource", at(exported.get(0), "baseDefinition"));
  }

  @Test
  @DisplayName("ResourceExporter should log an error with source information when the parent is invalid")
  void parentInvalid() {
    Resource resource = new Resource("BadParent").<Resource>withFile("BadParent.fsh").withLocation(2, 9, 4, 23);
    resource.parent = "Basic";
    doc.resources.put(resource.name, resource);
    exporter.export();
    assertMatches("File: BadParent\\.fsh.*Line: 2 - 4\\D*", "s", loggerSpy.getLastMessage("error"));
    assertMatches("The parent of a resource must be Resource or DomainResource.", "s",
        loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ResourceExporter should log an error with source information when the parent is not found")
  void parentNotFound() {
    Resource resource = new Resource("Bogus").<Resource>withFile("Bogus.fsh").withLocation(2, 9, 4, 23);
    resource.parent = "BogusParent";
    doc.resources.put(resource.name, resource);
    exporter.export();
    assertMatches("File: Bogus\\.fsh.*Line: 2 - 4\\D*", "s", loggerSpy.getLastMessage("error"));
    assertMatches("Parent BogusParent not found for Bogus", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ResourceExporter should log an error when an inline extension is used")
  void inlineExtension() {
    Resource resource = new Resource("MyResource");
    AddElementRule addElementRule = new AddElementRule("myExtension");
    addElementRule.min = 0.0;
    addElementRule.max = "*";
    addElementRule.types = types("Extension");
    addElementRule.short_ = "short definition";
    resource.rules.add(addElementRule);
    ContainsRule containsRule = new ContainsRule("myExtension").<ContainsRule>withFile("MyResource.fsh")
        .withLocation(3, 8, 3, 25);
    containsRule.items.add(new ContainsRuleItem("SomeExtension"));
    resource.rules.add(containsRule);
    doc.resources.put(resource.name, resource);
    exporter.export();

    assertMatches("File: MyResource\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("error"));
    assertMatches("Use of 'ContainsRule' is not permitted for 'Resource'", "s", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ResourceExporter should allow constraints on newly added elements and sub-elements")
  void constraintsOnNewElements() {
    Resource resource = new Resource("ExampleResource");
    resource.setId("ExampleResource");

    AddElementRule addElementRule = new AddElementRule("name");
    addElementRule.min = 0.0;
    addElementRule.max = "*";
    addElementRule.types = types("HumanName");
    addElementRule.short_ = "A person's full name";
    resource.rules.add(addElementRule);

    CardRule topLevelCardRule = new CardRule("name");
    topLevelCardRule.min = 1.0;
    topLevelCardRule.max = "1";
    resource.rules.add(topLevelCardRule);

    CardRule subElementCardRule = new CardRule("name.given");
    subElementCardRule.min = 1.0;
    subElementCardRule.max = "1";
    resource.rules.add(subElementCardRule);

    doc.resources.put(resource.name, resource);
    exporter.export();
    List<String> logs = loggerSpy.getAllMessages("error");
    assertEquals(0, logs.size());
  }

  @Test
  @DisplayName("ResourceExporter should allow constraints on root elements")
  void constraintsOnRootElements() {
    Resource resource = new Resource("ExampleResource");
    resource.setId("ExampleResource");

    CaretValueRule rootElementRule = new CaretValueRule(".");
    rootElementRule.caretPath = "alias";
    rootElementRule.value = "ExampleAlias";

    resource.rules.add(rootElementRule);

    doc.resources.put(resource.name, resource);
    exporter.export();
    List<String> logs = loggerSpy.getAllMessages("error");
    assertEquals(0, logs.size());
  }

  @Test
  @DisplayName("ResourceExporter should allow constraints on inherited elements")
  void constraintsOnInheritedElements() {
    Resource resource = new Resource("MyTestResource");
    // Parent defaults to DomainResource
    resource.setId("MyResource");

    AddElementRule addElementRule1 = new AddElementRule("backboneProp");
    addElementRule1.min = 0.0;
    addElementRule1.max = "*";
    addElementRule1.types = types("BackboneElement");
    addElementRule1.short_ = "short of backboneProp";
    resource.rules.add(addElementRule1);

    AddElementRule addElementRule2 = new AddElementRule("backboneProp.name");
    addElementRule2.min = 1.0;
    addElementRule2.max = "1";
    addElementRule2.types = types("HumanName");
    addElementRule2.short_ = "short of backboneProp.name";
    resource.rules.add(addElementRule2);

    AddElementRule addElementRule3 = new AddElementRule("backboneProp.address");
    addElementRule3.min = 0.0;
    addElementRule3.max = "*";
    addElementRule3.types = types("Address");
    addElementRule3.short_ = "short of backboneProp.address";
    resource.rules.add(addElementRule3);

    // FlagRule
    FlagRule flagRule1 = new FlagRule("extension");
    flagRule1.summary = true;
    resource.rules.add(flagRule1);

    // CardRule
    CardRule cardRule1 = new CardRule("extension");
    cardRule1.min = 1.0;
    cardRule1.max = "1";
    resource.rules.add(cardRule1);

    // OnlyRule
    Profile profiledMeta = new Profile("MyMetaProfile");
    profiledMeta.parent = "Meta";
    doc.profiles.put(profiledMeta.name, profiledMeta);
    OnlyRule onlyRule = new OnlyRule("meta");
    onlyRule.types = types(profiledMeta.name);
    resource.rules.add(onlyRule);

    // BindingRule
    FshValueSet myValueSet = new FshValueSet("MyValueSet");
    doc.valueSets.put(myValueSet.name, myValueSet);
    BindingRule bindingRule = new BindingRule("language");
    bindingRule.valueSet = myValueSet.name;
    bindingRule.strength = "required";
    resource.rules.add(bindingRule);

    // AssignmentRule
    AssignmentRule assignmentRule = new AssignmentRule("text.status");
    assignmentRule.value = new FshCode("additional");
    assignmentRule.exactly = true;
    resource.rules.add(assignmentRule);

    // ObeysRule
    Invariant myInvariant = new Invariant("MyInvariant");
    myInvariant.severity = new FshCode("error");
    myInvariant.description = "Has a Patient";
    doc.invariants.put(myInvariant.name, myInvariant);
    ObeysRule obeysRule = new ObeysRule("contained");
    obeysRule.invariant = myInvariant.name;
    resource.rules.add(obeysRule);

    // CaretValueRule
    CaretValueRule caretRule = new CaretValueRule("implicitRules");
    caretRule.caretPath = "comment";
    caretRule.value = "Not explicit";
    resource.rules.add(caretRule);

    FlagRule flagRule2 = new FlagRule("backboneProp.address");
    flagRule2.summary = true;
    resource.rules.add(flagRule2);

    CardRule cardRule2 = new CardRule("backboneProp.address");
    cardRule2.min = 1.0;
    cardRule2.max = "100";
    resource.rules.add(cardRule2);

    doc.resources.put(resource.name, resource);

    StructureDefinition exported = exporter.export().resources.get(0);

    assertEquals(0, loggerSpy.getAllMessages("error").size());

    assertEquals("MyTestResource", at(exported, "name"));
    assertEquals("MyResource", at(exported, "id"));
    assertEquals("MyResource", at(exported, "type"));
    assertEquals("http://hl7.org/fhir/StructureDefinition/DomainResource", at(exported, "baseDefinition"));

    // FlagRule
    ElementDefinition extension = exported.findElement("MyResource.extension");
    assertEquals(true, at(extension, "isSummary"));

    // CardRule
    assertJsEqual(1, at(extension, "min"));
    assertEquals("1", at(extension, "max"));

    // OnlyRule
    ElementDefinition meta = exported.findElement("MyResource.meta");
    assertEquals("Meta", meta.getType().get(0).getCode());
    assertJsonEqual("""
        ["http://hl7.org/fhir/us/minimal/StructureDefinition/MyMetaProfile"]""",
        meta.getType().get(0).getProfile());

    // BindingRule
    ElementDefinition language = exported.findElement("MyResource.language");
    assertEquals("http://hl7.org/fhir/us/minimal/ValueSet/MyValueSet", at(language, "binding.valueSet"));
    assertEquals("required", at(language, "binding.strength"));

    // AssignmentRule
    ElementDefinition textStatus = exported.findElement("MyResource.text.status");
    assertEquals("additional", at(textStatus, "fixedCode"));

    // ObeysRule
    ElementDefinition contained = exported.findElement("MyResource.contained");
    assertEquals("MyInvariant", at(contained, "constraint[0].key"));
    assertEquals("error", at(contained, "constraint[0].severity"));
    assertEquals("Has a Patient", at(contained, "constraint[0].human"));

    // CaretValueRule
    ElementDefinition implicitRules = exported.findElement("MyResource.implicitRules");
    assertEquals("Not explicit", at(implicitRules, "comment"));

    // 9 DomainResource elements + + 4 expanded Narrative elements + 3 added elements
    assertEquals(16, exported.elements.size());
  }

  @Test
  @DisplayName("ResourceExporter should log an error when slicing an inherited element")
  void slicingInheritedElement() {
    Resource resource = new Resource("MyResource");
    // Parent defaults to DomainResource
    ContainsRule containsRule = new ContainsRule("contained").<ContainsRule>withFile("MyResource.fsh")
        .withLocation(3, 8, 3, 25);
    containsRule.items.add(new ContainsRuleItem("conditions"));
    resource.rules.add(containsRule);
    doc.resources.put(resource.name, resource);
    StructureDefinition exported = exporter.export().resources.get(0);

    assertMatches("File: MyResource\\.fsh.*Line: 3\\D*", "s", loggerSpy.getLastMessage("error"));
    assertMatches("Use of 'ContainsRule' is not permitted for 'Resource'", "s", loggerSpy.getLastMessage("error"));
    ElementDefinition contained = exported.findElement("MyResource.contained");
    assertNull(at(contained, "slicing"));
  }

  @Test
  @DisplayName("ResourceExporter should log an error when adding an element with the same path as an inherited element")
  void addElementSamePathAsInherited() {
    Resource resource = new Resource("MyResource");
    AddElementRule addElementRule = new AddElementRule("extension");
    addElementRule.min = 0.0;
    addElementRule.max = "1";
    resource.rules.add(addElementRule);
    doc.resources.put(resource.name, resource);
    List<StructureDefinition> exported = exporter.export().resources;

    assertEquals(1, exported.size());
    assertEquals(1,
        exported.get(0).elements.stream().filter(r -> "MyResource.extension".equals(r.getId())).count());

    assertEquals("MyResource", at(exported.get(0), "name"));
    assertEquals("MyResource", at(exported.get(0), "id"));
    assertEquals("MyResource", at(exported.get(0), "type"));
    assertEquals("http://hl7.org/fhir/StructureDefinition/DomainResource", at(exported.get(0), "baseDefinition"));
    assertEquals(9, exported.get(0).elements.size());

    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertContainsText("Cannot define element " + addElementRule.path + " on " + resource.name
        + " because it has already been defined", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ResourceExporter should log an error when two rules add a new element with the same path")
  void twoRulesAddSamePath() {
    Resource resource = new Resource("MyResource");
    AddElementRule addElementRule = new AddElementRule("testElement");
    addElementRule.min = 0.0;
    addElementRule.max = "1";
    AddElementRule addElementRule2 = new AddElementRule("testElement");
    addElementRule2.min = 0.0;
    addElementRule2.max = "1";
    resource.rules.add(addElementRule);
    resource.rules.add(addElementRule2);
    doc.resources.put(resource.name, resource);
    List<StructureDefinition> exported = exporter.export().resources;

    assertEquals(1, exported.size());
    assertEquals(1,
        exported.get(0).elements.stream().filter(r -> "MyResource.testElement".equals(r.getId())).count());

    assertEquals("MyResource", at(exported.get(0), "name"));
    assertEquals("MyResource", at(exported.get(0), "id"));
    assertEquals("MyResource", at(exported.get(0), "type"));
    assertEquals("http://hl7.org/fhir/StructureDefinition/DomainResource", at(exported.get(0), "baseDefinition"));
    assertEquals(10, exported.get(0).elements.size());

    assertEquals(1, loggerSpy.getAllMessages("error").size());
    assertContainsText("Cannot define element " + addElementRule.path + " on " + resource.name
        + " because it has already been defined", loggerSpy.getLastMessage("error"));
  }

  @Test
  @DisplayName("ResourceExporter should log an error when a rule with the same path is added by directly calling newElement")
  void newElementSamePath() {
    doc = new FSHDocument("fileName");
    FSHTank input = new FSHTank(new ArrayList<>(List.of(doc)), minimalConfig());
    pkg = new Package(input.config);
    TestFisher fisher = new TestFisher(input, defs, pkg);
    StructureDefinition alternateIdentification = fisher.fishForStructureDefinition("AlternateIdentification");

    AddElementRule addElementRule = new AddElementRule("elem1");
    addElementRule.min = 0.0;
    addElementRule.max = "1";
    AddElementRule addElementRule2 = new AddElementRule("elem1");
    addElementRule2.min = 0.0;
    addElementRule2.max = "1";
    alternateIdentification.newElement(addElementRule.path);
    ElementAlreadyDefinedError e = assertThrows(ElementAlreadyDefinedError.class,
        () -> alternateIdentification.newElement(addElementRule2.path));
    assertContainsText(
        "Cannot define element elem1 on AlternateIdentification because it has already been defined",
        e.getMessage());
  }

  @Test
  @DisplayName("ResourceExporter should not log a warning when exporting a conformant resource")
  void conformantResourceNoWarning() {
    Resource resource = new Resource("Foo");
    CaretValueRule caretRule = new CaretValueRule("");
    caretRule.caretPath = "url";
    caretRule.value = "http://hl7.org/fhir/StructureDefinition/Foo";
    resource.rules.add(caretRule);
    doc.resources.put(resource.name, resource);
    List<StructureDefinition> exported = exporter.export().resources;
    assertEquals(1, exported.size());
    assertEquals(0, loggerSpy.getAllMessages("warn").size());
  }

  @Test
  @DisplayName("ResourceExporter should log a warning when exporting a non-conformant resource")
  void nonConformantResourceWarning() {
    Resource resource = new Resource("Foo");
    doc.resources.put(resource.name, resource);
    List<StructureDefinition> exported = exporter.export().resources;
    assertEquals(1, exported.size());
    assertMatches("non-conformant Resource.*- Foo", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("ResourceExporter should log a warning when exporting a multiple non-conformant resources")
  void multipleNonConformantResourcesWarning() {
    Resource resource1 = new Resource("Foo");
    Resource resource2 = new Resource("Bar");
    doc.resources.put(resource1.name, resource1);
    doc.resources.put(resource2.name, resource2);
    List<StructureDefinition> exported = exporter.export().resources;
    assertEquals(2, exported.size());
    assertMatches("non-conformant Resource.*- Foo.*- Bar", "s", loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("ResourceExporter should log a warning and truncate the name when exporting a non-conformant resource with a long name")
  void nonConformantLongNameTruncated() {
    Resource resource = new Resource(
        "SupercalifragilisticexpialidociousIsSurprisinglyNotEvenLongEnoughOnItsOwn");
    doc.resources.put(resource.name, resource);
    List<StructureDefinition> exported = exporter.export().resources;
    assertEquals(1, exported.size());
    assertMatches("non-conformant Resource.*- SupercalifragilisticexpialidociousIsSurprisinglyNotEvenLon\\.\\.\\.", "s",
        loggerSpy.getLastMessage("warn"));
  }

  @Test
  @DisplayName("ResourceExporter should create Resource root element with short equal to title if short not available AND definition equal to description if definition not available")
  void rootShortFromTitleDefinitionFromDescription() {
    Resource resource = new Resource("MyTestModel");
    resource.setId("MyModel");
    resource.title = "MyTestModel title is here";
    resource.description = "MyTestModel description is here";

    doc.resources.put(resource.name, resource);
    exporter.exportStructDef(resource);
    StructureDefinition exported = pkg.resources.get(0);

    assertEquals(resource.title, at(exported.elements.get(0), "short"));
    assertEquals(resource.description, at(exported.elements.get(0), "definition"));
  }

  @Test
  @DisplayName("ResourceExporter should create Resource root element with short equal to name if short and title not available AND definition equal to name if description and definition not available")
  void rootShortAndDefinitionFromName() {
    Resource resource = new Resource("MyTestModel");
    resource.setId("MyModel");

    doc.resources.put(resource.name, resource);
    exporter.exportStructDef(resource);
    StructureDefinition exported = pkg.resources.get(0);

    assertEquals(resource.name, at(exported.elements.get(0), "short"));
    assertEquals(resource.name, at(exported.elements.get(0), "definition"));
  }

  @Test
  @DisplayName("ResourceExporter should create Resource root element with short equal to title if short not available AND definition equal to short if description and definition not available")
  void rootShortFromTitleDefinitionFromShort() {
    Resource resource = new Resource("MyTestModel");
    resource.setId("MyModel");
    resource.title = "MyTestModel title is here";

    doc.resources.put(resource.name, resource);
    exporter.exportStructDef(resource);
    StructureDefinition exported = pkg.resources.get(0);

    assertEquals(resource.title, at(exported.elements.get(0), "short"));
    assertEquals(at(exported.elements.get(0), "short"), at(exported.elements.get(0), "definition"));
  }

  @Test
  @DisplayName("ResourceExporter should create Resource root element with short equal short caret rule AND definition equal to definition caret rule")
  void rootShortAndDefinitionFromCaretRules() {
    Resource resource = new Resource("MyTestModel");
    resource.setId("MyModel");
    resource.title = "MyTestModel title is here";
    resource.description = "MyTestModel description is here";

    CaretValueRule caretValueRule = new CaretValueRule(".");
    caretValueRule.caretPath = "short";
    caretValueRule.value = "Caret short value is here";
    resource.rules.add(caretValueRule);

    CaretValueRule caretValueRuleTwo = new CaretValueRule(".");
    caretValueRuleTwo.caretPath = "definition";
    caretValueRuleTwo.value = "Caret definition value is here";
    resource.rules.add(caretValueRuleTwo);

    doc.resources.put(resource.name, resource);
    exporter.exportStructDef(resource);
    StructureDefinition exported = pkg.resources.get(0);

    assertEquals("Caret short value is here", at(exported.elements.get(0), "short"));
    assertEquals("Caret definition value is here", at(exported.elements.get(0), "definition"));
  }
}
