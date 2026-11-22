package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.JsAssert.assertJsEqual;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.at;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.json;
import static org.hl7.fhir.sushi.testhelpers.MetadataAssert.assertMetadataEquals;
import static org.hl7.fhir.sushi.testhelpers.MetadataAssert.assertMetadatasEqual;
import static org.hl7.fhir.sushi.testhelpers.MetadataAssert.md;
import static org.hl7.fhir.sushi.testhelpers.MinimalConfig.minimalConfig;
import static org.hl7.fhir.sushi.utils.FishableType.CodeSystem;
import static org.hl7.fhir.sushi.utils.FishableType.Extension;
import static org.hl7.fhir.sushi.utils.FishableType.Instance;
import static org.hl7.fhir.sushi.utils.FishableType.Logical;
import static org.hl7.fhir.sushi.utils.FishableType.Profile;
import static org.hl7.fhir.sushi.utils.FishableType.Resource;
import static org.hl7.fhir.sushi.utils.FishableType.Type;
import static org.hl7.fhir.sushi.utils.FishableType.ValueSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hl7.fhir.sushi.fhirtypes.Common;
import org.hl7.fhir.sushi.fhirtypes.InstanceDefinition;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.Metadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

// port of sushi/test/export/Package.test.ts (SUSHI 3.21.1)
// TS builds one package in beforeAll and two tests delete pkg.config.url: PER_CLASS + method-name order keeps that.
@DisplayName("Package")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class PackageTest {
  static final Configuration minimalConfig = minimalConfig();
  final Package pkg = new Package(minimalConfig);

  /** TS `obj.k = v` on any of SUSHI's FHIR classes (kv pairs). */
  static <T> T set(T obj, Object... kv) {
    for (int i = 0; i < kv.length; i += 2) {
      Common.setProp(obj, (String) kv[i], kv[i + 1]);
    }
    return obj;
  }

  static StructureDefinition sd(Object... kv) {
    return set(new StructureDefinition(), kv);
  }

  static InstanceDefinition inst(String name, String usage, Object... kv) {
    InstanceDefinition i = new InstanceDefinition();
    i._instanceMeta.name = name;
    i._instanceMeta.usage = usage;
    return set(i, kv);
  }

  @BeforeAll
  void setUpAll() {
    pkg.config.url = "http://not-an-auto-generated-url.org";
    // Profile[0]: Funny / fun-ny / Condition
    pkg.profiles.add(sd("name", "Funny", "id", "fun-ny", "type", "Condition", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/fun-ny", "baseDefinition",
        "http://hl7.org/fhir/StructureDefinition/Condition", "fhirVersion", "4.0.1", "version", "1.0.0"));
    // Profile[1]: Sanguine / san-guine / Condition
    pkg.profiles.add(sd("name", "Sanguine", "id", "san-guine", "type", "Condition", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/san-guine", "baseDefinition",
        "http://hl7.org/fhir/StructureDefinition/Condition", "fhirVersion", "4.0.1", "extension", json("""
            [
              {"url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-imposeProfile",
               "valueCanonical": "http://hl7.org/fhir/us/minimal/StructureDefinition/fun-ny"},
              {"url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-definitelyDoesNotImposeProfile",
               "valueCanonical": "http://hl7.org/fhir/us/minimal/StructureDefinition/ser-rius"},
              {"url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-imposeProfile",
               "valueCanonical": "http://hl7.org/fhir/us/minimal/StructureDefinition/fun-ni-er"}
            ]""")));
    // Extension[0]: PreferredBeatle / preferred-beatle
    pkg.extensions.add(sd("name", "PreferredBeatle", "id", "preferred-beatle", "type", "Extension", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/preferred-beatle", "baseDefinition",
        "http://hl7.org/fhir/StructureDefinition/Extension", "fhirVersion", "4.0.1"));
    // Extension[1]: PoorTaste / poor-taste
    pkg.extensions.add(sd("name", "PoorTaste", "id", "poor-taste", "type", "Extension", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/poor-taste", "baseDefinition",
        "http://hl7.org/fhir/StructureDefinition/Extension", "fhirVersion", "4.0.1", "version", "1.0.0"));
    // Logical[0]: WheatBeer / wheat-beer / wheat-beer
    pkg.logicals.add(sd("name", "WheatBeer", "id", "wheat-beer", "type", "wheat-beer", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/wheat-beer", "baseDefinition",
        "http://hl7.org/fhir/StructureDefinition/Element", "kind", "logical", "derivation", "specialization",
        "fhirVersion", "4.0.1", "version", "1.0.0"));
    // Logical[1]: RedWine / red-wine / red-wine
    pkg.logicals.add(sd("name", "RedWine", "id", "red-wine", "type", "red-wine", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/red-wine", "baseDefinition",
        "http://hl7.org/fhir/StructureDefinition/Basic", "kind", "logical", "derivation", "specialization",
        "fhirVersion", "4.0.1", "version", "1.0.0", "extension", json("""
            [{"url": "http://hl7.org/fhir/tools/StructureDefinition/logical-target", "valueBoolean": true}]""")));
    // Logical[2]: SparklingWater / sparkling-water / sparkling-water
    pkg.logicals.add(sd("name", "SparklingWater", "id", "sparkling-water", "type", "sparkling-water", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/sparkling-water", "baseDefinition",
        "http://hl7.org/fhir/StructureDefinition/Basic", "kind", "logical", "derivation", "specialization",
        "fhirVersion", "4.0.1", "version", "1.0.2", "extension", json("""
            [{"url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics",
              "valueCode": "can-be-target"}]""")));
    // Logical[3]: IcedCoffee / iced-coffee / iced-coffee
    pkg.logicals.add(sd("name", "IcedCoffee", "id", "iced-coffee", "type", "iced-coffee", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/iced-coffee", "baseDefinition",
        "http://hl7.org/fhir/StructureDefinition/Basic", "kind", "logical", "derivation", "specialization",
        "fhirVersion", "4.0.1", "version", "1.0.2", "extension", json("""
            [{"url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics",
              "valueCode": "can-bind"}]""")));
    // Resource[0]: Destination / Destination / Destination
    pkg.resources.add(sd("name", "Destination", "id", "Destination", "type", "Destination", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/Destination", "baseDefinition",
        "http://hl7.org/fhir/StructureDefinition/DomainResource", "fhirVersion", "4.0.1", "version", "1.0.0"));
    // Resource[1]: MovieTheater / MovieTheater / MovieTheater
    pkg.resources.add(sd("name", "MovieTheater", "id", "MovieTheater", "type", "MovieTheater", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/MovieTheater", "baseDefinition",
        "http://hl7.org/fhir/StructureDefinition/DomainResource", "fhirVersion", "4.0.1"));
    // ValueSet[0]: Soups / soup-flavors
    pkg.valueSets.add(set(new org.hl7.fhir.sushi.fhirtypes.ValueSet(), "name", "Soups", "id", "soup-flavors", "url",
        "http://hl7.org/fhir/us/minimal/ValueSet/soup-flavors", "version", "4.0.1"));
    // ValueSet[1]: Cheeses / cheese-flavors
    pkg.valueSets.add(set(new org.hl7.fhir.sushi.fhirtypes.ValueSet(), "name", "Cheeses", "id", "cheese-flavors",
        "url", "http://hl7.org/fhir/us/minimal/ValueSet/cheese-flavors", "version", "4.0.1"));
    // CodeSystem[0]: Letters / alphas
    pkg.codeSystems.add(set(new org.hl7.fhir.sushi.fhirtypes.CodeSystem(), "name", "Letters", "id", "alphas", "url",
        "http://hl7.org/fhir/us/minimal/CodeSystem/alphas", "version", "4.0.1"));
    // CodeSystem[1]: Numbers / numerics
    pkg.codeSystems.add(set(new org.hl7.fhir.sushi.fhirtypes.CodeSystem(), "name", "Numbers", "id", "numerics",
        "url", "http://hl7.org/fhir/us/minimal/CodeSystem/numerics", "version", "4.0.1"));
    // CodeSystem[1]: Cheeses / cheese-flavors
    pkg.codeSystems.add(set(new org.hl7.fhir.sushi.fhirtypes.CodeSystem(), "name", "Cheeses", "id",
        "cheese-flavors", "url", "http://hl7.org/fhir/us/minimal/CodeSystem/cheese-flavors", "version", "4.0.1"));
    // Instance[0]: DrSue / dr-sue / Practitioner
    pkg.instances.add(inst("DrSue", null, "id", "dr-sue", "resourceType", "Practitioner", "gender", "female"));
    // Instance[1]: DrBob / dr-bob / Practitioner
    pkg.instances.add(inst("DrBob", null, "id", "dr-bob", "meta",
        json("{\"profile\": [\"http://unreal.org/StructureDefinition/super-practitioner\"]}"), "resourceType",
        "Practitioner", "gender", "male"));
    // Instance[2]: Thrilling / thrill-ing / Condition
    pkg.instances.add(inst("Thrilling", "Definition", "id", "thrill-ing", "resourceType", "StructureDefinition",
        "derivation", "constraint", "type", "Condition", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/thrill-ing", "baseDefinition",
        "http://hl7.org/fhir/StructureDefinition/Condition", "fhirVersion", "4.0.1", "version", "1.0.0"));
    // Instance[3]: TwoWolves / two-wolves / Extension
    pkg.instances.add(inst("TwoWolves", "Definition", "id", "two-wolves", "resourceType", "StructureDefinition",
        "derivation", "constraint", "type", "Extension", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/two-wolves", "baseDefinition",
        "http://hl7.org/fhir/StructureDefinition/Extension", "fhirVersion", "4.0.1", "version", "1.0.0"));
    // Instance[4]: Spices / spices / CodeSystem
    pkg.instances.add(inst("Spices", "Definition", "id", "spices", "resourceType", "CodeSystem", "url",
        "http://hl7.org/fhir/us/minimal/CodeSystem/spices", "content", "complete", "version", "4.0.1"));
    // Instance[5]: Cookies / cookie-varieties / ValueSet
    pkg.instances.add(inst("Cookies", "Definition", "id", "cookie-varieties", "resourceType", "ValueSet", "url",
        "http://hl7.org/fhir/us/minimal/ValueSet/cookie-varieties", "version", "4.0.1"));
    // Instance[6]: SpiderHouse / spider-house / Logical
    pkg.instances.add(inst("SpiderHouse", "Definition", "id", "spider-house", "resourceType",
        "StructureDefinition", "derivation", "specialization", "kind", "logical", "url",
        "http://hl7.org/fhir/us/minimal/StructureDefinition/spider-house", "baseDefinition",
        "http://hl7.org/fhir/StructureDefinition/Base", "fhirVersion", "4.0.1", "version", "1.0.0"));
    // Instance[7]: Zone / zone / Resource
    pkg.instances.add(inst("Zone", "Definition", "id", "zone", "resourceType", "StructureDefinition", "derivation",
        "specialization", "kind", "resource", "url", "http://hl7.org/fhir/us/minimal/StructureDefinition/zone",
        "baseDefinition", "", "fhirVersion", "4.0.1", "version", "1.0.0"));
  }

  static final String MIN = "http://hl7.org/fhir/us/minimal/";

  /** The repeated "should find X" pattern: by id, then by name and url give an equal result. */
  void assertFinds(String id, String name, String url, FishableType type, String expectedVersionProp,
      String expectedVersionValue) {
    Object byId = pkg.fishForFHIR(id, type);
    assertEquals(url, at(byId, "url"));
    assertEquals(expectedVersionValue, at(byId, expectedVersionProp));
    assertJsEqual(byId, pkg.fishForFHIR(name, type));
    assertJsEqual(byId, pkg.fishForFHIR(url, type));
  }

  void assertFindsWithVersion(String item, String url, FishableType type, String version) {
    Object r = pkg.fishForFHIR(item, type);
    assertEquals(url, at(r, "url"));
    assertEquals(version, at(r, "version"));
  }

  @Nested
  @Order(1)
  @DisplayName("#fishForFHIR()")
  @TestMethodOrder(MethodOrderer.MethodName.class)
  class FishForFHIR {
    @Test
    @DisplayName("Package #fishForFHIR() should find profiles")
    void t01() {
      assertFinds("fun-ny", "Funny", MIN + "StructureDefinition/fun-ny", Profile, "fhirVersion", "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find instances of profiles")
    void t02() {
      assertFinds("thrill-ing", "Thrilling", MIN + "StructureDefinition/thrill-ing", Profile, "fhirVersion",
          "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find profiles when fishing with a version")
    void t03() {
      assertFindsWithVersion("fun-ny|1.0.0", MIN + "StructureDefinition/fun-ny", Profile, "1.0.0");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should not find a profile when fishing with a version that does not match")
    void t04() {
      assertNull(pkg.fishForFHIR("fun-ny|2.0.0", Profile));
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find instances of profiles when fishing with a version")
    void t05() {
      assertFindsWithVersion("thrill-ing|1.0.0", MIN + "StructureDefinition/thrill-ing", Profile, "1.0.0");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find extensions")
    void t06() {
      assertFinds("poor-taste", "PoorTaste", MIN + "StructureDefinition/poor-taste", Extension, "fhirVersion",
          "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find instances of extensions")
    void t07() {
      assertFinds("two-wolves", "TwoWolves", MIN + "StructureDefinition/two-wolves", Extension, "fhirVersion",
          "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find extensions when fishing with a version")
    void t08() {
      assertFindsWithVersion("poor-taste|1.0.0", MIN + "StructureDefinition/poor-taste", Extension, "1.0.0");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should not find an extension when fishing with a version that does not match")
    void t09() {
      assertNull(pkg.fishForFHIR("poor-taste|2.0.0", Extension));
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find instances of extensions when fishing with a version")
    void t10() {
      assertFindsWithVersion("two-wolves|1.0.0", MIN + "StructureDefinition/two-wolves", Extension, "1.0.0");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find logicals")
    void t11() {
      assertFinds("wheat-beer", "WheatBeer", MIN + "StructureDefinition/wheat-beer", Logical, "fhirVersion",
          "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find instances of logicals")
    void t12() {
      assertFinds("spider-house", "SpiderHouse", MIN + "StructureDefinition/spider-house", Logical, "fhirVersion",
          "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find logicals when fishing with a version")
    void t13() {
      assertFindsWithVersion("wheat-beer|1.0.0", MIN + "StructureDefinition/wheat-beer", Logical, "1.0.0");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should not find a logical when fishing with a version that does not match")
    void t14() {
      assertNull(pkg.fishForFHIR("wheat-beer|2.0.0", Logical));
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find instances of logicals when fishing with a version")
    void t15() {
      assertFindsWithVersion("spider-house|1.0.0", MIN + "StructureDefinition/spider-house", Logical, "1.0.0");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find resources")
    void t16() {
      assertFinds("Destination", "Destination", MIN + "StructureDefinition/Destination", Resource, "fhirVersion",
          "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find instances of resources")
    void t17() {
      assertFinds("zone", "Zone", MIN + "StructureDefinition/zone", Resource, "fhirVersion", "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find resources when fishing with a version")
    void t18() {
      assertFindsWithVersion("Destination|1.0.0", MIN + "StructureDefinition/Destination", Resource, "1.0.0");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should not find a resource when fishing with a version that does not match")
    void t19() {
      assertNull(pkg.fishForFHIR("Destination|2.0.0", Resource));
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find instances of resources when fishing with a version")
    void t20() {
      assertFindsWithVersion("zone|1.0.0", MIN + "StructureDefinition/zone", Resource, "1.0.0");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find value sets")
    void t21() {
      // For some reason, value sets don't specify a fhirVersion, but in this case the business version is the FHIR
      // version, so we'll verify that instead
      assertFinds("soup-flavors", "Soups", MIN + "ValueSet/soup-flavors", ValueSet, "version", "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find instances of value sets")
    void t22() {
      assertFinds("cookie-varieties", "Cookies", MIN + "ValueSet/cookie-varieties", ValueSet, "version", "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find value sets when fishing with a version")
    void t23() {
      assertFindsWithVersion("soup-flavors|4.0.1", MIN + "ValueSet/soup-flavors", ValueSet, "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should not find a value set when fishing with a version that does not match")
    void t24() {
      assertNull(pkg.fishForFHIR("soup-flavors|1.1.1", ValueSet));
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find instances of value sets when fishing with a version")
    void t25() {
      assertFindsWithVersion("cookie-varieties|4.0.1", MIN + "ValueSet/cookie-varieties", ValueSet, "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find code systems")
    void t26() {
      assertFinds("numerics", "Numbers", MIN + "CodeSystem/numerics", CodeSystem, "version", "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find instances of code systems")
    void t27() {
      assertFinds("spices", "Spices", MIN + "CodeSystem/spices", CodeSystem, "version", "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find code systems when fishing with a version")
    void t28() {
      assertFindsWithVersion("numerics|4.0.1", MIN + "CodeSystem/numerics", CodeSystem, "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should not find a code system when fishing with a version that does not match")
    void t29() {
      assertNull(pkg.fishForFHIR("numerics|1.1.1", CodeSystem));
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find instances of code systems when fishing with a version")
    void t30() {
      assertFindsWithVersion("spices|4.0.1", MIN + "CodeSystem/spices", CodeSystem, "4.0.1");
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find instances")
    void t31() {
      Object drSueInstanceById = pkg.fishForFHIR("dr-sue", Instance);
      assertEquals("dr-sue", at(drSueInstanceById, "id"));
      assertEquals("female", at(drSueInstanceById, "gender"));
      assertJsEqual(drSueInstanceById, pkg.fishForFHIR("DrSue", Instance));
    }

    @Test
    @DisplayName("Package #fishForFHIR() should find instances that have a version when fishing with a version")
    void t32() {
      Object drSueInstanceById = pkg.fishForFHIR("thrill-ing|1.0.0", Instance);
      assertEquals("thrill-ing", at(drSueInstanceById, "id"));
      assertEquals("1.0.0", at(drSueInstanceById, "version"));
    }

    @Test
    @DisplayName("Package #fishForFHIR() should not find an instance that does not have a version when fishing with a version")
    void t33() {
      assertNull(pkg.fishForFHIR("dr-sue|1.0.0", Instance));
    }

    @Test
    @DisplayName("Package #fishForFHIR() should not find the definition when the type is not requested")
    void t34() {
      assertNull(pkg.fishForFHIR("fun-ny", Resource, Logical, Type, Extension, ValueSet, CodeSystem, Instance));
      assertNull(pkg.fishForFHIR("thrill-ing", Resource, Logical, Type, Extension, ValueSet, CodeSystem));
      assertNull(pkg.fishForFHIR("poor-taste", Resource, Logical, Type, Profile, ValueSet, CodeSystem, Instance));
      assertNull(pkg.fishForFHIR("two-wolves", Resource, Logical, Type, Profile, ValueSet, CodeSystem));
      assertNull(pkg.fishForFHIR("wheat-beer", Resource, Type, Profile, Extension, ValueSet, CodeSystem, Instance));
      assertNull(pkg.fishForFHIR("Destination", Logical, Type, Profile, Extension, ValueSet, CodeSystem, Instance));
      assertNull(pkg.fishForFHIR("soup-flavors", Resource, Logical, Type, Profile, Extension, CodeSystem,
          Instance));
      assertNull(pkg.fishForFHIR("numerics", Resource, Logical, Type, Profile, Extension, ValueSet, Instance));
      assertNull(pkg.fishForFHIR("dr-sue", Resource, Logical, Type, Profile, Extension, ValueSet, CodeSystem));
    }

    void assertGlobal(String id, String name, String url, String prop, String expected) {
      Object byId = pkg.fishForFHIR(id);
      assertEquals(name, at(byId, "name"));
      assertEquals(expected, at(byId, prop));
      assertJsEqual(byId, pkg.fishForFHIR(name));
      assertJsEqual(byId, pkg.fishForFHIR(url));
    }

    @Test
    @DisplayName("Package #fishForFHIR() should globally find any definition")
    void t35() {
      assertGlobal("fun-ny", "Funny", MIN + "StructureDefinition/fun-ny", "fhirVersion", "4.0.1");
      assertGlobal("poor-taste", "PoorTaste", MIN + "StructureDefinition/poor-taste", "fhirVersion", "4.0.1");
      assertGlobal("wheat-beer", "WheatBeer", MIN + "StructureDefinition/wheat-beer", "fhirVersion", "4.0.1");
      assertGlobal("Destination", "Destination", MIN + "StructureDefinition/Destination", "fhirVersion", "4.0.1");
      // For some reason, value sets don't specify a fhirVersion, but in this case the business version is the FHIR
      // version, so we'll verify that instead
      assertGlobal("soup-flavors", "Soups", MIN + "ValueSet/soup-flavors", "version", "4.0.1");
      assertGlobal("numerics", "Numbers", MIN + "CodeSystem/numerics", "version", "4.0.1");

      Object drSueInstanceByID = pkg.fishForFHIR("dr-sue");
      assertEquals("female", at(drSueInstanceByID, "gender"));
      assertJsEqual(drSueInstanceByID, pkg.fishForFHIR("DrSue"));
    }
  }

  static final Metadata FUNNY_MD = md("id", "fun-ny", "name", "Funny", "sdType", "Condition", "url",
      MIN + "StructureDefinition/fun-ny", "parent", "http://hl7.org/fhir/StructureDefinition/Condition",
      "resourceType", "StructureDefinition", "version", "1.0.0");
  static final Metadata POOR_TASTE_MD = md("id", "poor-taste", "name", "PoorTaste", "sdType", "Extension", "url",
      MIN + "StructureDefinition/poor-taste", "parent", "http://hl7.org/fhir/StructureDefinition/Extension",
      "resourceType", "StructureDefinition", "version", "1.0.0");
  static final Metadata WHEAT_BEER_MD = md("id", "wheat-beer", "name", "WheatBeer", "sdType", "wheat-beer", "url",
      MIN + "StructureDefinition/wheat-beer", "parent", "http://hl7.org/fhir/StructureDefinition/Element",
      "resourceType", "StructureDefinition", "version", "1.0.0", "canBeTarget", false, "canBind", false);
  static final Metadata DESTINATION_MD = md("id", "Destination", "name", "Destination", "sdType", "Destination",
      "url", MIN + "StructureDefinition/Destination", "parent",
      "http://hl7.org/fhir/StructureDefinition/DomainResource", "resourceType", "StructureDefinition", "version",
      "1.0.0");
  static final Metadata SOUPS_MD = md("id", "soup-flavors", "name", "Soups", "url", MIN + "ValueSet/soup-flavors",
      "version", "4.0.1", "resourceType", "ValueSet");
  static final Metadata NUMERICS_MD = md("id", "numerics", "name", "Numbers", "url", MIN + "CodeSystem/numerics",
      "version", "4.0.1", "resourceType", "CodeSystem");
  static final Metadata DR_SUE_MD = md("id", "dr-sue", "name", "DrSue", "resourceType", "Practitioner");

  void assertMetadataByIdNameUrl(Metadata expected, String id, String name, String url, FishableType... types) {
    Metadata byId = pkg.fishForMetadata(id, types);
    assertMetadataEquals(expected, byId);
    assertMetadataEquals(byId, pkg.fishForMetadata(name, types));
    if (url != null) {
      assertMetadataEquals(byId, pkg.fishForMetadata(url, types));
    }
  }

  @Nested
  @Order(2)
  @DisplayName("#fishForMetadata()")
  @TestMethodOrder(MethodOrderer.MethodName.class)
  class FishForMetadata {
    @Test
    @DisplayName("Package #fishForMetadata() should find profiles")
    void t01() {
      assertMetadataByIdNameUrl(FUNNY_MD, "fun-ny", "Funny", MIN + "StructureDefinition/fun-ny", Profile);
    }

    @Test
    @DisplayName("Package #fishForMetadata() should find profiles w/ declared imposeProfiles")
    void t02() {
      Metadata expected = md("id", "san-guine", "name", "Sanguine", "sdType", "Condition", "url",
          MIN + "StructureDefinition/san-guine", "parent", "http://hl7.org/fhir/StructureDefinition/Condition",
          "resourceType", "StructureDefinition", "imposeProfiles", List.of(MIN + "StructureDefinition/fun-ny",
              MIN + "StructureDefinition/fun-ni-er"));
      assertMetadataByIdNameUrl(expected, "san-guine", "Sanguine", MIN + "StructureDefinition/san-guine", Profile);
    }

    @Test
    @DisplayName("Package #fishForMetadata() should find extensions")
    void t03() {
      assertMetadataByIdNameUrl(POOR_TASTE_MD, "poor-taste", "PoorTaste", MIN + "StructureDefinition/poor-taste",
          Extension);
    }

    @Test
    @DisplayName("Package #fishForMetadata() should find logicals that can not be a reference target or have bindings")
    void t04() {
      assertMetadataByIdNameUrl(WHEAT_BEER_MD, "wheat-beer", "WheatBeer", MIN + "StructureDefinition/wheat-beer",
          Logical);
    }

    @Test
    @DisplayName("Package #fishForMetadata() should find logicals that can be a reference target using the logical-target extension")
    void t05() {
      Metadata expected = md("id", "red-wine", "name", "RedWine", "sdType", "red-wine", "url",
          MIN + "StructureDefinition/red-wine", "parent", "http://hl7.org/fhir/StructureDefinition/Basic",
          "resourceType", "StructureDefinition", "version", "1.0.0", "canBeTarget", true, "canBind", false);
      assertMetadataByIdNameUrl(expected, "red-wine", "RedWine", MIN + "StructureDefinition/red-wine", Logical);
    }

    @Test
    @DisplayName("Package #fishForMetadata() should find logicals that can be a reference target using the structuredefinition-type-characteristics extension")
    void t06() {
      Metadata expected = md("id", "sparkling-water", "name", "SparklingWater", "sdType", "sparkling-water", "url",
          MIN + "StructureDefinition/sparkling-water", "parent", "http://hl7.org/fhir/StructureDefinition/Basic",
          "resourceType", "StructureDefinition", "version", "1.0.2", "canBeTarget", true, "canBind", false);
      assertMetadataByIdNameUrl(expected, "sparkling-water", "SparklingWater",
          MIN + "StructureDefinition/sparkling-water", Logical);
    }

    @Test
    @DisplayName("Package #fishForMetadata() should find logicals that can have a binding using the can-bind extension")
    void t07() {
      Metadata expected = md("id", "iced-coffee", "name", "IcedCoffee", "sdType", "iced-coffee", "url",
          MIN + "StructureDefinition/iced-coffee", "parent", "http://hl7.org/fhir/StructureDefinition/Basic",
          "resourceType", "StructureDefinition", "version", "1.0.2", "canBeTarget", false, "canBind", true);
      assertMetadataEquals(expected, pkg.fishForMetadata("iced-coffee", Logical));
    }

    @Test
    @DisplayName("Package #fishForMetadata() should find resources")
    void t08() {
      assertMetadataByIdNameUrl(DESTINATION_MD, "Destination", "Destination",
          MIN + "StructureDefinition/Destination", Resource);
    }

    @Test
    @DisplayName("Package #fishForMetadata() should find value sets")
    void t09() {
      assertMetadataByIdNameUrl(SOUPS_MD, "soup-flavors", "Soups", MIN + "ValueSet/soup-flavors", ValueSet);
    }

    @Test
    @DisplayName("Package #fishForMetadata() should find code systems")
    void t10() {
      assertMetadataByIdNameUrl(NUMERICS_MD, "numerics", "Numbers", MIN + "CodeSystem/numerics", CodeSystem);
    }

    @Test
    @DisplayName("Package #fishForMetadata() should find instances")
    void t11() {
      assertMetadataByIdNameUrl(DR_SUE_MD, "dr-sue", "DrSue", null, Instance);
    }

    @Test
    @DisplayName("Package #fishForMetadata() should not find the definition when the type is not requested")
    void t12() {
      assertNull(pkg.fishForMetadata("fun-ny", Resource, Logical, Type, Extension, ValueSet, CodeSystem, Instance));
      assertNull(pkg.fishForMetadata("poor-taste", Resource, Logical, Type, Profile, ValueSet, CodeSystem,
          Instance));
      assertNull(pkg.fishForMetadata("wheat-beer", Resource, Type, Profile, Extension, ValueSet, CodeSystem,
          Instance));
      assertNull(pkg.fishForMetadata("Destination", Logical, Type, Profile, Extension, ValueSet, CodeSystem,
          Instance));
      assertNull(pkg.fishForMetadata("soup-flavors", Resource, Logical, Type, Profile, Extension, CodeSystem,
          Instance));
      assertNull(pkg.fishForMetadata("numerics", Resource, Logical, Type, Profile, Extension, ValueSet, Instance));
      assertNull(pkg.fishForMetadata("dr-sue", Resource, Logical, Type, Profile, Extension, ValueSet, CodeSystem));
    }

    @Test
    @DisplayName("Package #fishForMetadata() should globally find any definition")
    void t13() {
      assertMetadataByIdNameUrl(FUNNY_MD, "fun-ny", "Funny", MIN + "StructureDefinition/fun-ny");
      assertMetadataByIdNameUrl(POOR_TASTE_MD, "poor-taste", "PoorTaste", MIN + "StructureDefinition/poor-taste");
      assertMetadataByIdNameUrl(WHEAT_BEER_MD, "wheat-beer", "WheatBeer", MIN + "StructureDefinition/wheat-beer");
      assertMetadataByIdNameUrl(DESTINATION_MD, "Destination", "Destination",
          MIN + "StructureDefinition/Destination");
      assertMetadataByIdNameUrl(SOUPS_MD, "soup-flavors", "Soups", MIN + "ValueSet/soup-flavors");
      assertMetadataByIdNameUrl(NUMERICS_MD, "numerics", "Numbers", MIN + "CodeSystem/numerics");
      assertMetadataByIdNameUrl(DR_SUE_MD, "dr-sue", "DrSue", null);
    }

    @Test
    @DisplayName("Package #fishForMetadata() should return package metadata when fishing with the package id")
    void t14() {
      Metadata packageMetadata = pkg.fishForMetadata("fhir.us.minimal");
      assertEquals(minimalConfig.id, packageMetadata.id);
      assertEquals(minimalConfig.name, packageMetadata.name);
      assertEquals("http://not-an-auto-generated-url.org", packageMetadata.url);
      assertEquals("ImplementationGuide", packageMetadata.resourceType);
    }

    @Test
    @DisplayName("Package #fishForMetadata() should return package metadata when fishing with the package name")
    void t15() {
      Metadata packageMetadata = pkg.fishForMetadata("MinimalIG");
      assertEquals(minimalConfig.id, packageMetadata.id);
      assertEquals(minimalConfig.name, packageMetadata.name);
      assertEquals("http://not-an-auto-generated-url.org", packageMetadata.url);
      assertEquals("ImplementationGuide", packageMetadata.resourceType);
    }

    @Test
    @DisplayName("Package #fishForMetadata() should return package metadata with an auto-generated url when url is missing")
    void t16() {
      pkg.config.url = null;
      Metadata packageMetadata = pkg.fishForMetadata("MinimalIG");
      assertEquals(minimalConfig.id, packageMetadata.id);
      assertEquals(minimalConfig.name, packageMetadata.name);
      assertEquals(minimalConfig.canonical + "/ImplementationGuide/" + minimalConfig.id, packageMetadata.url);
      assertEquals("ImplementationGuide", packageMetadata.resourceType);
    }
  }

  @Nested
  @Order(3)
  @DisplayName("#fishForMetadatas()")
  @TestMethodOrder(MethodOrderer.MethodName.class)
  class FishForMetadatas {
    @Test
    @DisplayName("Package #fishForMetadatas() should return all matches when there are multiple matches")
    void t1() {
      List<Metadata> cheesesByID = pkg.fishForMetadatas("cheese-flavors");
      assertEquals(2, cheesesByID.size());
      assertMetadatasEqual(List.of(
          md("id", "cheese-flavors", "name", "Cheeses", "url", MIN + "ValueSet/cheese-flavors", "version", "4.0.1",
              "resourceType", "ValueSet"),
          md("id", "cheese-flavors", "name", "Cheeses", "url", MIN + "CodeSystem/cheese-flavors", "version",
              "4.0.1", "resourceType", "CodeSystem")), cheesesByID);
      assertMetadatasEqual(cheesesByID, pkg.fishForMetadatas("Cheeses"));
      assertMetadatasEqual(cheesesByID.subList(0, 1), pkg.fishForMetadatas(MIN + "ValueSet/cheese-flavors"));
      assertMetadatasEqual(cheesesByID.subList(1, 2), pkg.fishForMetadatas(MIN + "CodeSystem/cheese-flavors"));
    }

    @Test
    @DisplayName("Package #fishForMetadatas() should return one match when there is a single match")
    void t2() {
      List<Metadata> funnyProfileByID = pkg.fishForMetadatas("fun-ny");
      assertEquals(1, funnyProfileByID.size());
      assertMetadataEquals(FUNNY_MD, funnyProfileByID.get(0));
      assertMetadatasEqual(funnyProfileByID, pkg.fishForMetadatas("Funny"));
      assertMetadatasEqual(funnyProfileByID, pkg.fishForMetadatas(MIN + "StructureDefinition/fun-ny"));
    }

    @Test
    @DisplayName("Package #fishForMetadatas() should return package metadata with an auto-generated url when url is missing")
    void t3() {
      pkg.config.url = null;
      List<Metadata> packageMetadatas = pkg.fishForMetadatas("MinimalIG");
      assertEquals(1, packageMetadatas.size());
      assertEquals(minimalConfig.id, packageMetadatas.get(0).id);
      assertEquals(minimalConfig.name, packageMetadatas.get(0).name);
      assertEquals(minimalConfig.canonical + "/ImplementationGuide/" + minimalConfig.id, packageMetadatas.get(0).url);
      assertEquals("ImplementationGuide", packageMetadatas.get(0).resourceType);
    }

    @Test
    @DisplayName("Package #fishForMetadatas() should return empty array when there are no matches")
    void t4() {
      assertTrue(pkg.fishForMetadatas("NonExistentThing").isEmpty());
    }
  }
}
