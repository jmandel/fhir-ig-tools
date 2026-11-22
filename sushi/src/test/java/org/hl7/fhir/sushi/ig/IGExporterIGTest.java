package org.hl7.fhir.sushi.ig;

import static org.hl7.fhir.sushi.testhelpers.TsTestUtil.*;
import static org.hl7.fhir.sushi.testhelpers.JsAssert.*;
import static org.hl7.fhir.sushi.testhelpers.NodeTestEnv.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.getTestFHIRDefinitions;
import static org.hl7.fhir.sushi.testhelpers.TestFHIRDefinitions.testDefsPath;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.errors.*;
import org.hl7.fhir.sushi.export.Package;
import org.hl7.fhir.sushi.export.Package.FshMapEntry;
import org.hl7.fhir.sushi.importer.config.ConfigurationJs;
import org.hl7.fhir.sushi.fhirdefs.DiskBasedVirtualPackage;
import org.hl7.fhir.sushi.fhirdefs.FHIRDefinitions;
import org.hl7.fhir.sushi.fhirdefs.InMemoryVirtualPackage;
import org.hl7.fhir.sushi.fhirtypes.*;
import org.hl7.fhir.sushi.fshtypes.*;
import org.hl7.fhir.sushi.fshtypes.rules.*;
import org.hl7.fhir.sushi.importer.FSHDocument;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.js.*;
import org.hl7.fhir.sushi.testhelpers.*;
import org.hl7.fhir.sushi.utils.*;
import org.junit.jupiter.api.*;

// port of sushi/test/ig/IGExporter.IG.test.ts (SUSHI 3.21.1)
@DisplayName("IGExporter")
class IGExporterIGTest {

  static final String __dirname = dirname("ig");

  // TS assigns plain objects to typed Configuration fields; convert them the way importConfiguration's output is typed.
  @SuppressWarnings("unchecked")
  static Map<String, Object> jm(Object o) { return (Map<String, Object>) o; }
  @SuppressWarnings("unchecked")
  static List<JsObject> jsList(Object l) { return (List<JsObject>) l; }
  static DiskBasedVirtualPackage diskPkg(Map<String, Object> packageJSON) { return new DiskBasedVirtualPackage(packageJSON, new ArrayList<>()); }
  static Configuration minimalConfigWithMenu = minimalConfigWithMenu();
  static Configuration minimalConfigWithMenu() {
    Configuration c = MinimalConfig.minimalConfig();
    c.menu = menu(JsAssert.json("""
      [
        { "name": "Home", "url": "index.html" },
        { "name": "Artifacts", "url": "artifacts.html" }
      ]
      """));
    return c;
  }
  static List<Configuration.ConfigurationGroup> groups(Object js) { return ConfigurationJs.fromJs(obj("groups", js)).groups; }
  static List<Configuration.ConfigurationMenuItem> menu(Object js) { return ConfigurationJs.fromJs(obj("menu", js)).menu; }
  static Configuration.ConfigurationHistory history(Object js) { return ConfigurationJs.fromJs(obj("history", js)).history; }
  static Configuration.ConfigurationDefinition definition(Object js) { return ConfigurationJs.fromJs(obj("definition", js)).definition; }
  @DisplayName("#simple-ig")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class SimpleIg {
    Package pkg;
    IGExporter exporter;
    String tempOut;
    String fixtures;
    Configuration config;
    FHIRDefinitions defs;
    List<StructureDefinition> pkgProfiles = new ArrayList<>(Arrays.asList());
    List<StructureDefinition> pkgExtensions = new ArrayList<>(Arrays.asList());
    List<InstanceDefinition> pkgInstances = new ArrayList<>(Arrays.asList());
    List<CodeSystem> pkgCodeSystems = new ArrayList<>(Arrays.asList());

    @BeforeAll
    void beforeAll() {
      defs = getTestFHIRDefinitions(true, testDefsPath("fhir.no.ig.package#1.0.1"), testDefsPath("r4-definitions"));
      fixtures = path.join(__dirname, "fixtures", "simple-ig");
      var profiles = path.join(fixtures, "profiles");
      for (String f : fs.readdirSync(profiles)) {
  if (Js.truthy(f.endsWith(".json"))) {
    var sd = StructureDefinition.fromJSON(jm(fs.readJSONSync(path.join(profiles, f))));
    pkgProfiles.add(sd);
  }
      }
      var extensions = path.join(fixtures, "extensions");
      for (String f : fs.readdirSync(extensions)) {
  if (Js.truthy(f.endsWith(".json"))) {
    var sd = StructureDefinition.fromJSON(jm(fs.readJSONSync(path.join(extensions, f))));
    pkgExtensions.add(sd);
  }
      }
      var examples = path.join(fixtures, "examples");
      for (String f : fs.readdirSync(examples)) {
  if (Js.truthy(f.endsWith(".json"))) {
    var instanceDef = InstanceDefinition.fromJSON(jm(fs.readJSONSync(path.join(examples, f))));
    // since instance meta isn't encoded in the JSON, add some here (usually done in the FSH import)
    if (Js.truthy(Objects.equals(at(instanceDef, "id"), "patient-example-two"))) {
      setProp(at(instanceDef, "_instanceMeta"), "title", "Another Patient Example");
      setProp(at(instanceDef, "_instanceMeta"), "description", "Another example of a Patient");
      setProp(at(instanceDef, "_instanceMeta"), "usage", "Example");
      setProp(at(instanceDef, "_instanceMeta"), "instanceOfUrl", "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient");
    }
    if (Js.truthy(Objects.equals(at(instanceDef, "id"), "patient-example-three"))) {
      setProp(at(instanceDef, "_instanceMeta"), "usage", "Inline");
      setProp(at(instanceDef, "_instanceMeta"), "instanceOfUrl", "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient");
    }
    if (Js.truthy(Objects.equals(at(instanceDef, "id"), "capability-statement-example"))) {
      setProp(at(instanceDef, "_instanceMeta"), "usage", "Definition");
    }
    if (Js.truthy(Objects.equals(at(instanceDef, "id"), "patient-example"))) {
      setProp(at(instanceDef, "_instanceMeta"), "usage", "Example");
      setProp(at(instanceDef, "_instanceMeta"), "instanceOfUrl", "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient");
    }
    pkgInstances.add(instanceDef);
  }
      }
      // Add CodeSystem directly because there is no fromJSON method on the class
      CodeSystem codeSystemDef = new CodeSystem();
      setProp(codeSystemDef, "id", "sample-code-system");
      setProp(codeSystemDef, "name", "SampleCodeSystem");
      setProp(codeSystemDef, "description", "A code system description");
      pkgCodeSystems.add(codeSystemDef);
    }

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      config = ConfigurationJs.fromJs(obj("filePath", path.join(fixtures, "sushi-config.yml"), "id", "sushi-test", "canonical", "http://hl7.org/fhir/sushi-test", "url", "http://hl7.org/fhir/sushi-test/ImplementationGuide/FSHTestIG", "version", "0.1.0", "name", "FSHTestIG", "title", "FSH Test IG", "description", "Provides a simple example of how FSH can be used to create an IG", "dependencies", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.core",
          "version": "3.1.0"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.uv.sdc",
          "version": "3.0.x"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.uv.vhdir",
          "version": "current"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.mcode",
          "uri": "http://hl7.org/fhir/us/mcode/ImplementationGuide/hl7.fhir.us.mcode",
          "id": "mcode",
          "version": "1.0.0"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.package.with.extension",
          "version": "1.0.0",
          "id": "package.with.extension",
          "extension": [
            {
              "url": "http://example.org/fake/StructureDefinition/bar",
              "valueString": "bar"
            }
          ]
        }
        """))), "definition", JsAssert.json("""
        {
          "extension": [
            {
              "url": "http://example.org/example/ig-definition-ext",
              "valueBoolean": true
            }
          ]
        }
        """), "resources", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example"
          },
          "name": "Patient Example",
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/implementationguide-resource-format",
              "valueCode": "text/plain"
            }
          ]
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/sample-patient"
          },
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/implementationguide-resource-format",
              "valueCode": "text/plain"
            }
          ]
        }
        """))), "status", "active", "fhirVersion", new ArrayList<>(Arrays.asList("4.0.1")), "language", "en", "publisher", "James Tuna", "contact", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "name": "Bill Cod",
          "telecom": [
            {
              "system": "url",
              "value": "https://capecodfishermen.org/"
            },
            {
              "system": "email",
              "value": "cod@reef.gov"
            }
          ]
        }
        """))), "license", "CC0-1.0", "parameters", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "code": "copyrightyear",
          "value": "2020+"
        }
        """), JsAssert.json("""
        {
          "code": "releaselabel",
          "value": "CI Build"
        }
        """)))));
      pkg = new Package(config);
      pkg.profiles.addAll(pkgProfiles);
      pkg.extensions.addAll(pkgExtensions);
      pkg.instances.addAll(pkgInstances);
      pkg.codeSystems.addAll(pkgCodeSystems);
      exporter = new IGExporter(pkg, defs, fixtures);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #simple-ig should generate an implementation guide for simple-ig")
    void shouldGenerateAnImplementationGuideForSimpleIg() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      assertJsonEqual("""
        {
          "resourceType": "ImplementationGuide",
          "id": "sushi-test",
          "language": "en",
          "url": "http://hl7.org/fhir/sushi-test/ImplementationGuide/FSHTestIG",
          "version": "0.1.0",
          "name": "FSHTestIG",
          "title": "FSH Test IG",
          "status": "active",
          "publisher": "James Tuna",
          "contact": [
            {
              "name": "Bill Cod",
              "telecom": [
                {
                  "system": "url",
                  "value": "https://capecodfishermen.org/"
                },
                {
                  "system": "email",
                  "value": "cod@reef.gov"
                }
              ]
            }
          ],
          "description": "Provides a simple example of how FSH can be used to create an IG",
          "packageId": "sushi-test",
          "license": "CC0-1.0",
          "fhirVersion": [
            "4.0.1"
          ],
          "dependsOn": [
            {
              "id": "hl7_fhir_us_core",
              "uri": "http://hl7.org/fhir/us/core/ImplementationGuide/hl7.fhir.us.core",
              "packageId": "hl7.fhir.us.core",
              "version": "3.1.0"
            },
            {
              "id": "hl7_fhir_uv_sdc",
              "uri": "http://hl7.org/fhir/uv/sdc/ImplementationGuide/hl7.fhir.uv.sdc",
              "packageId": "hl7.fhir.uv.sdc",
              "version": "3.0.x"
            },
            {
              "id": "hl7_fhir_uv_vhdir",
              "uri": "http://hl7.org/fhir/uv/vhdir/ImplementationGuide/hl7.core.uv.vhdir",
              "packageId": "hl7.fhir.uv.vhdir",
              "version": "current"
            },
            {
              "id": "mcode",
              "uri": "http://hl7.org/fhir/us/mcode/ImplementationGuide/hl7.fhir.us.mcode",
              "packageId": "hl7.fhir.us.mcode",
              "version": "1.0.0"
            },
            {
              "id": "package.with.extension",
              "uri": "http://fhir.org/packages/hl7.package.with.extension/ImplementationGuide/hl7.package.with.extension",
              "packageId": "hl7.package.with.extension",
              "version": "1.0.0",
              "extension": [
                {
                  "url": "http://example.org/fake/StructureDefinition/bar",
                  "valueString": "bar"
                }
              ]
            }
          ],
          "definition": {
            "extension": [
              {
                "url": "http://example.org/example/ig-definition-ext",
                "valueBoolean": true
              }
            ],
            "resource": [
              {
                "reference": {
                  "reference": "Patient/patient-example-two"
                },
                "name": "Another Patient Example",
                "description": "Another example of a Patient",
                "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient"
              },
              {
                "reference": {
                  "reference": "CapabilityStatement/capability-statement-example"
                },
                "name": "capability-statement-example",
                "description": "Test description",
                "exampleBoolean": false
              },
              {
                "reference": {
                  "reference": "Patient/patient-example"
                },
                "name": "Patient Example",
                "extension": [
                  {
                    "url": "http://hl7.org/fhir/StructureDefinition/implementationguide-resource-format",
                    "valueCode": "text/plain"
                  }
                ],
                "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient"
              },
              {
                "reference": {
                  "reference": "CodeSystem/sample-code-system"
                },
                "name": "SampleCodeSystem",
                "description": "A code system description",
                "exampleBoolean": false
              },
              {
                "reference": {
                  "reference": "StructureDefinition/sample-complex-extension"
                },
                "name": "SampleComplexExtension",
                "description": "Base StructureDefinition for Extension Type: Optional Extension Element - found in all resources.",
                "exampleBoolean": false
              },
              {
                "reference": {
                  "reference": "StructureDefinition/sample-observation"
                },
                "name": "SampleObservation",
                "description": "Measurements and simple assertions made about a patient, device or other subject.",
                "exampleBoolean": false
              },
              {
                "reference": {
                  "reference": "StructureDefinition/sample-patient"
                },
                "extension": [
                  {
                    "url": "http://hl7.org/fhir/StructureDefinition/implementationguide-resource-format",
                    "valueCode": "text/plain"
                  }
                ],
                "name": "SamplePatient",
                "description": "Demographics and other administrative information about an individual or animal receiving care or other health-related services.",
                "exampleBoolean": false
              },
              {
                "reference": {
                  "reference": "StructureDefinition/sample-value-extension"
                },
                "name": "SampleValueExtension",
                "description": "Base StructureDefinition for Extension Type: Optional Extension Element - found in all resources.",
                "exampleBoolean": false
              }
            ],
            "page": {
              "nameUrl": "toc.html",
              "title": "Table of Contents",
              "generation": "html",
              "page": []
            },
            "parameter": [
              {
                "code": "copyrightyear",
                "value": "2020+"
              },
              {
                "code": "releaselabel",
                "value": "CI Build"
              },
              {
                "code": "path-history",
                "value": "http://hl7.org/fhir/sushi-test/history.html"
              }
            ]
          }
        }
        """, content);
    }

    @Test
    @DisplayName("IGExporter #simple-ig should convert the old-style virtual extension package dependencies to official xver package dependencies")
    void shouldConvertTheOldStyleVirtualExtensionPackageDependenciesToOfficialXverPackage() {
      config.dependencies = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.core",
          "version": "3.1.0"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.extensions.r2",
          "version": "4.0.1"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.extensions.r3",
          "version": "4.0.1"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.extensions.r4",
          "version": "4.0.1"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.extensions.r5",
          "version": "4.0.1"
        }
        """))));
      // add xver package.jsons used for resolving latest
      defs.loadVirtualPackage(diskPkg(jm(JsAssert.json("""
        {
          "name": "hl7.fhir.uv.xver-r2.r4",
          "version": "0.1.0",
          "fhirVersions": [
            "4.0.1"
          ]
        }
        """))));
      defs.loadVirtualPackage(diskPkg(jm(JsAssert.json("""
        {
          "name": "hl7.fhir.uv.xver-r3.r4",
          "version": "0.2.0",
          "fhirVersions": [
            "4.0.1"
          ]
        }
        """))));
      defs.loadVirtualPackage(diskPkg(jm(JsAssert.json("""
        {
          "name": "hl7.fhir.uv.xver-r4.r4",
          "version": "0.3.0",
          "fhirVersions": [
            "4.0.1"
          ]
        }
        """))));
      defs.loadVirtualPackage(diskPkg(jm(JsAssert.json("""
        {
          "name": "hl7.fhir.uv.xver-r5.r4",
          "version": "0.4.0",
          "fhirVersions": [
            "4.0.1"
          ]
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      var dependencies = at(content, "dependsOn");
      // Ensure US Core is exported and old fhir extension packages are converted to xver
      assertJsonEqual("""
        [
          {
            "id": "hl7_fhir_us_core",
            "uri": "http://hl7.org/fhir/us/core/ImplementationGuide/hl7.fhir.us.core",
            "packageId": "hl7.fhir.us.core",
            "version": "3.1.0"
          },
          {
            "id": "hl7_fhir_uv_xver_r2_r4",
            "packageId": "hl7.fhir.uv.xver-r2.r4",
            "uri": "http://hl7.org/fhir/uv/xver/ImplementationGuide/hl7.fhir.uv.xver-r2.r4",
            "version": "0.1.0"
          },
          {
            "id": "hl7_fhir_uv_xver_r3_r4",
            "packageId": "hl7.fhir.uv.xver-r3.r4",
            "uri": "http://hl7.org/fhir/uv/xver/ImplementationGuide/hl7.fhir.uv.xver-r3.r4",
            "version": "0.2.0"
          },
          {
            "id": "hl7_fhir_uv_xver_r4_r4",
            "packageId": "hl7.fhir.uv.xver-r4.r4",
            "uri": "http://hl7.org/fhir/uv/xver/ImplementationGuide/hl7.fhir.uv.xver-r4.r4",
            "version": "0.3.0"
          },
          {
            "id": "hl7_fhir_uv_xver_r5_r4",
            "packageId": "hl7.fhir.uv.xver-r5.r4",
            "uri": "http://hl7.org/fhir/uv/xver/ImplementationGuide/hl7.fhir.uv.xver-r5.r4",
            "version": "0.4.0"
          }
        ]
        """, dependencies);
      assertEquals(0, loggerSpy.getAllLogs("error").size());
    }

    @Test
    @DisplayName("IGExporter #simple-ig should get a canonical from package.json when a dependency has no implementation guide")
    void shouldGetACanonicalFromPackageJsonWhenADependencyHasNoImplementationGuide() {
      config.dependencies = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "packageId": "fhir.no.ig.package",
          "version": "1.0.1"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.core",
          "version": "3.1.0"
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      var dependencies = at(content, "dependsOn");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      // ensure both packages are in the dependencies
      assertJsonEqual("""
        [
          {
            "id": "fhir_no_ig_package",
            "uri": "http://example.org/fhir/no/ig/package",
            "packageId": "fhir.no.ig.package",
            "version": "1.0.1"
          },
          {
            "id": "hl7_fhir_us_core",
            "uri": "http://hl7.org/fhir/us/core/ImplementationGuide/hl7.fhir.us.core",
            "packageId": "hl7.fhir.us.core",
            "version": "3.1.0"
          }
        ]
        """, dependencies);
    }

    // See: https://chat.fhir.org/#narrow/channel/215610-shorthand/topic/ImplementationGuide.20URL.20missing/near/576162330
    @Test
    @DisplayName("IGExporter #simple-ig should fall back to release-agnostic IG when it cannot find a release-specific dependency implementation guide")
    void shouldFallBackToReleaseAgnosticIGWhenItCannotFindAReleaseSpecificDependencyImple() {
      config.dependencies = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "packageId": "hl7.fhir.eu.extensions.r4",
          "version": "1.2.0"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.core",
          "version": "3.1.0"
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      var dependencies = at(content, "dependsOn");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      // ensure both packages are in the dependencies
      assertJsonEqual("""
        [
          {
            "id": "hl7_fhir_eu_extensions_r4",
            "uri": "http://hl7.eu/fhir/extensions/ImplementationGuide/hl7.fhir.eu.extensions",
            "packageId": "hl7.fhir.eu.extensions.r4",
            "version": "1.2.0"
          },
          {
            "id": "hl7_fhir_us_core",
            "uri": "http://hl7.org/fhir/us/core/ImplementationGuide/hl7.fhir.us.core",
            "packageId": "hl7.fhir.us.core",
            "version": "3.1.0"
          }
        ]
        """, dependencies);
    }

    @Test
    @DisplayName("IGExporter #simple-ig should use a default url format when a dependency url cannot be inferred")
    void shouldUseADefaultUrlFormatWhenADependencyUrlCannotBeInferred() {
      config.dependencies = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.mcode",
          "version": "1.0.0"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.something.r4",
          "version": "2.0.0"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.core",
          "version": "3.1.0"
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      var dependencies = at(content, "dependsOn");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      // Ensure US Core is exported but mCODE is not
      assertJsonEqual("""
        [
          {
            "id": "hl7_fhir_us_mcode",
            "uri": "http://fhir.org/packages/hl7.fhir.us.mcode/ImplementationGuide/hl7.fhir.us.mcode",
            "packageId": "hl7.fhir.us.mcode",
            "version": "1.0.0"
          },
          {
            "id": "hl7_fhir_us_something_r4",
            "uri": "http://fhir.org/packages/hl7.fhir.us.something.r4/ImplementationGuide/hl7.fhir.us.something.r4",
            "packageId": "hl7.fhir.us.something.r4",
            "version": "2.0.0"
          },
          {
            "id": "hl7_fhir_us_core",
            "uri": "http://hl7.org/fhir/us/core/ImplementationGuide/hl7.fhir.us.core",
            "packageId": "hl7.fhir.us.core",
            "version": "3.1.0"
          }
        ]
        """, dependencies);
    }

    @Test
    @DisplayName("IGExporter #simple-ig should issue an error when a dependency version is not provided")
    void shouldIssueAnErrorWhenADependencyVersionIsNotProvided() {
      config.dependencies = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.mcode",
          "uri": "http://hl7.org/fhir/us/core/ImplementationGuide/hl7.fhir.us.core"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.core",
          "version": "3.1.0"
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      var dependencies = at(content, "dependsOn");
      assertMatches("Failed to add hl7\\.fhir\\.us\\.mcode to ImplementationGuide instance .* no version", "", loggerSpy.getLastMessage("error"));
      // Ensure US Core is exported but mCODE is not
      assertJsonEqual("""
        [
          {
            "id": "hl7_fhir_us_core",
            "uri": "http://hl7.org/fhir/us/core/ImplementationGuide/hl7.fhir.us.core",
            "packageId": "hl7.fhir.us.core",
            "version": "3.1.0"
          }
        ]
        """, dependencies);
    }

    @Test
    @DisplayName("IGExporter #simple-ig should override generated resource attributes with configured attributes")
    void shouldOverrideGeneratedResourceAttributesWithConfiguredAttributes() {
      config.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/sample-observation"
          },
          "name": "ConfiguredSampleObservation",
          "description": "This is a specially configured description"
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      var sampleObservation = find(at(content, "definition.resource"), (r) -> Objects.equals(at(r, "reference.reference"), "StructureDefinition/sample-observation"));
      assertJsonEqual("""
        {
          "reference": {
            "reference": "StructureDefinition/sample-observation"
          },
          "name": "ConfiguredSampleObservation",
          "description": "This is a specially configured description",
          "exampleBoolean": false
        }
        """, sampleObservation);
    }

    @Test
    @DisplayName("IGExporter #simple-ig should omit resources that are configured to be omitted")
    void shouldOmitResourcesThatAreConfiguredToBeOmitted() {
      config.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/sample-observation"
          },
          "omit": true
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      var sampleObservation = find(at(content, "definition.resource"), (r) -> Objects.equals(at(r, "reference.reference"), "StructureDefinition/sample-observation"));
      assertNull(sampleObservation);
    }

    @Test
    @DisplayName("IGExporter #simple-ig should add resources in the order in the configuration when all generated resources are in the configuration")
    void shouldAddResourcesInTheOrderInTheConfigurationWhenAllGeneratedResourcesAreInTheC() {
      config.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/sample-observation"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/sample-patient"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example"
          },
          "name": "Patient Example"
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example-two"
          },
          "name": "Patient Example The Second"
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "CapabilityStatement/capability-statement-example"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/sample-complex-extension"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/sample-value-extension"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "CodeSystem/sample-code-system"
          }
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "reference": {
              "reference": "StructureDefinition/sample-observation"
            },
            "name": "SampleObservation",
            "description": "Measurements and simple assertions made about a patient, device or other subject.",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "StructureDefinition/sample-patient"
            },
            "name": "SamplePatient",
            "description": "Demographics and other administrative information about an individual or animal receiving care or other health-related services.",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "Patient/patient-example"
            },
            "name": "Patient Example",
            "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient"
          },
          {
            "reference": {
              "reference": "Patient/patient-example-two"
            },
            "name": "Patient Example The Second",
            "description": "Another example of a Patient",
            "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient"
          },
          {
            "reference": {
              "reference": "CapabilityStatement/capability-statement-example"
            },
            "name": "capability-statement-example",
            "description": "Test description",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "StructureDefinition/sample-complex-extension"
            },
            "name": "SampleComplexExtension",
            "description": "Base StructureDefinition for Extension Type: Optional Extension Element - found in all resources.",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "StructureDefinition/sample-value-extension"
            },
            "name": "SampleValueExtension",
            "description": "Base StructureDefinition for Extension Type: Optional Extension Element - found in all resources.",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "CodeSystem/sample-code-system"
            },
            "name": "SampleCodeSystem",
            "description": "A code system description",
            "exampleBoolean": false
          }
        ]
        """, at(content, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #simple-ig should add resources that are only present in configuration")
    void shouldAddResourcesThatAreOnlyPresentInConfiguration() {
      config.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/config-only-observation"
          },
          "name": "ConfigOnlyObservation",
          "description": "This resource is only in the configuration."
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      var configOnlyObservation = find(at(content, "definition.resource"), (r) -> Objects.equals(at(r, "reference.reference"), "StructureDefinition/config-only-observation"));
      assertJsonEqual("""
        {
          "reference": {
            "reference": "StructureDefinition/config-only-observation"
          },
          "name": "ConfigOnlyObservation",
          "description": "This resource is only in the configuration."
        }
        """, configOnlyObservation);
    }

    @Test
    @DisplayName("IGExporter #simple-ig should create groups based on configured resource groupingId values")
    void shouldCreateGroupsBasedOnConfiguredResourceGroupingIdValues() {
      config.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/sample-patient"
          },
          "groupingId": "MyPatientGroup"
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example"
          },
          "groupingId": "MyPatientGroup"
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/sample-observation"
          },
          "groupingId": "MyObservationGroup"
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      assertEquals(2, len(at(content, "definition.grouping")));
      assertContainsEqual(JsAssert.json("""
        {
          "id": "MyPatientGroup",
          "name": "MyPatientGroup"
        }
        """), at(content, "definition.grouping"));
      assertContainsEqual(JsAssert.json("""
        {
          "id": "MyObservationGroup",
          "name": "MyObservationGroup"
        }
        """), at(content, "definition.grouping"));
    }

    @Test
    @DisplayName("IGExporter #simple-ig should create groups for each configured group")
    void shouldCreateGroupsForEachConfiguredGroup() {
      config.groups = groups(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "id": "MyPatientGroup",
          "name": "My Patient Group",
          "description": "Group for some patient-related things.",
          "resources": [
            "StructureDefinition/sample-patient",
            "Patient/patient-example"
          ]
        }
        """), JsAssert.json("""
        {
          "id": "MyObservationGroup",
          "name": "My Observation Group",
          "description": "Group for some observation-related things.",
          "resources": [
            "StructureDefinition/sample-observation"
          ]
        }
        """), JsAssert.json("""
        {
          "id": "MyEmptyGroup",
          "name": "My Empty Group",
          "description": "A group for when nothing is better than something."
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "id": "MyPatientGroup",
          "name": "My Patient Group",
          "description": "Group for some patient-related things."
        }
        """), at(content, "definition.grouping"));
      assertContainsEqual(JsAssert.json("""
        {
          "id": "MyObservationGroup",
          "name": "My Observation Group",
          "description": "Group for some observation-related things."
        }
        """), at(content, "definition.grouping"));
      assertContainsEqual(JsAssert.json("""
        {
          "id": "MyEmptyGroup",
          "name": "My Empty Group",
          "description": "A group for when nothing is better than something."
        }
        """), at(content, "definition.grouping"));
      var samplePatient = find(at(content, "definition.resource"), (r) -> Objects.equals(at(r, "reference.reference"), "StructureDefinition/sample-patient"));
      assertEquals("MyPatientGroup", at(samplePatient, "groupingId"));
      var examplePatient = find(at(content, "definition.resource"), (r) -> Objects.equals(at(r, "reference.reference"), "Patient/patient-example"));
      assertEquals("MyPatientGroup", at(examplePatient, "groupingId"));
      var sampleObservation = find(at(content, "definition.resource"), (r) -> Objects.equals(at(r, "reference.reference"), "StructureDefinition/sample-observation"));
      assertEquals("MyObservationGroup", at(sampleObservation, "groupingId"));
    }

    @Test
    @DisplayName("IGExporter #simple-ig should add resources in the order given in the configured groups when all resources are listed in configured groups")
    void shouldAddResourcesInTheOrderGivenInTheConfiguredGroupsWhenAllResourcesAreListedI() {
      config.groups = groups(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "id": "PatientGroup",
          "name": "Patient Group",
          "description": "Has patient-related resources.",
          "resources": [
            "StructureDefinition/sample-patient",
            "Patient/patient-example",
            "Patient/patient-example-two"
          ]
        }
        """), JsAssert.json("""
        {
          "id": "ExtensionGroup",
          "name": "Extension Group",
          "description": "Has extensions of all varieties.",
          "resources": [
            "StructureDefinition/sample-complex-extension",
            "StructureDefinition/sample-value-extension"
          ]
        }
        """), JsAssert.json("""
        {
          "id": "CatawumpusGroup",
          "name": "Catawumpus Group",
          "description": "Has everything else.",
          "resources": [
            "StructureDefinition/sample-observation",
            "CapabilityStatement/capability-statement-example",
            "CodeSystem/sample-code-system"
          ]
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "reference": {
              "reference": "StructureDefinition/sample-patient"
            },
            "name": "SamplePatient",
            "extension": [
              {
                "url": "http://hl7.org/fhir/StructureDefinition/implementationguide-resource-format",
                "valueCode": "text/plain"
              }
            ],
            "description": "Demographics and other administrative information about an individual or animal receiving care or other health-related services.",
            "exampleBoolean": false,
            "groupingId": "PatientGroup"
          },
          {
            "reference": {
              "reference": "Patient/patient-example"
            },
            "name": "Patient Example",
            "extension": [
              {
                "url": "http://hl7.org/fhir/StructureDefinition/implementationguide-resource-format",
                "valueCode": "text/plain"
              }
            ],
            "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient",
            "groupingId": "PatientGroup"
          },
          {
            "reference": {
              "reference": "Patient/patient-example-two"
            },
            "name": "Another Patient Example",
            "description": "Another example of a Patient",
            "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient",
            "groupingId": "PatientGroup"
          },
          {
            "reference": {
              "reference": "StructureDefinition/sample-complex-extension"
            },
            "name": "SampleComplexExtension",
            "description": "Base StructureDefinition for Extension Type: Optional Extension Element - found in all resources.",
            "exampleBoolean": false,
            "groupingId": "ExtensionGroup"
          },
          {
            "reference": {
              "reference": "StructureDefinition/sample-value-extension"
            },
            "name": "SampleValueExtension",
            "description": "Base StructureDefinition for Extension Type: Optional Extension Element - found in all resources.",
            "exampleBoolean": false,
            "groupingId": "ExtensionGroup"
          },
          {
            "reference": {
              "reference": "StructureDefinition/sample-observation"
            },
            "name": "SampleObservation",
            "description": "Measurements and simple assertions made about a patient, device or other subject.",
            "exampleBoolean": false,
            "groupingId": "CatawumpusGroup"
          },
          {
            "reference": {
              "reference": "CapabilityStatement/capability-statement-example"
            },
            "name": "capability-statement-example",
            "description": "Test description",
            "exampleBoolean": false,
            "groupingId": "CatawumpusGroup"
          },
          {
            "reference": {
              "reference": "CodeSystem/sample-code-system"
            },
            "name": "SampleCodeSystem",
            "description": "A code system description",
            "exampleBoolean": false,
            "groupingId": "CatawumpusGroup"
          }
        ]
        """, at(content, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #simple-ig should log an error when a group is configured with a nonexistent resource")
    void shouldLogAnErrorWhenAGroupIsConfiguredWithANonexistentResource() {
      config.groups = groups(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "id": "BananaGroup",
          "name": "Banana Group",
          "description": "Holds all the bananas.",
          "resources": [
            "StructureDefinition/sample-banana"
          ]
        }
        """))));
      exporter.export(tempOut);
      assertMatches("BananaGroup configured with nonexistent resource StructureDefinition\\/sample-banana", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("IGExporter #simple-ig should log an error when a resource has a groupingId, but a different group claims it")
    void shouldLogAnErrorWhenAResourceHasAGroupingIdButADifferentGroupClaimsIt() {
      config.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/sample-patient"
          },
          "groupingId": "Montagues"
        }
        """))));
      config.groups = groups(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "id": "Capulets",
          "name": "Capulets",
          "resources": [
            "StructureDefinition/sample-patient"
          ]
        }
        """))));
      exporter.export(tempOut);
      assertMatches("StructureDefinition\\/sample-patient configured with groupingId Montagues.*member of group Capulets", "s", loggerSpy.getLastMessage("error"));
    }

    @Test
    @DisplayName("IGExporter #simple-ig should log a warning when a resource is a member of a group and has a redundant groupingId")
    void shouldLogAWarningWhenAResourceIsAMemberOfAGroupAndHasARedundantGroupingId() {
      config.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/sample-patient"
          },
          "groupingId": "JustOneGroup"
        }
        """))));
      config.groups = groups(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "id": "JustOneGroup",
          "name": "Just One Group",
          "resources": [
            "StructureDefinition/sample-patient"
          ]
        }
        """))));
      exporter.export(tempOut);
      assertMatches("StructureDefinition\\/sample-patient is listed as a member of group JustOneGroup.*does not need a groupingId", "s", loggerSpy.getFirstMessage("warn"));
    }

  }
  @DisplayName("#simple-ig-plus")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class SimpleIgPlus {
    Package pkg;
    IGExporter exporter;
    String tempOut;
    String fixtures;
    Configuration config;
    FHIRDefinitions defs;
    List<StructureDefinition> pkgProfiles = new ArrayList<>(Arrays.asList());
    List<StructureDefinition> pkgExtensions = new ArrayList<>(Arrays.asList());
    List<StructureDefinition> pkgLogicals = new ArrayList<>(Arrays.asList());
    List<StructureDefinition> pkgResources = new ArrayList<>(Arrays.asList());

    @BeforeAll
    void beforeAll() {
      defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
      fixtures = path.join(__dirname, "fixtures", "simple-ig-plus");
      var profiles = path.join(fixtures, "profiles");
      for (String f : fs.readdirSync(profiles)) {
  if (Js.truthy(f.endsWith(".json"))) {
    var sd = StructureDefinition.fromJSON(jm(fs.readJSONSync(path.join(profiles, f))));
    pkgProfiles.add(sd);
  }
      }
      var extensions = path.join(fixtures, "extensions");
      for (String f : fs.readdirSync(extensions)) {
  if (Js.truthy(f.endsWith(".json"))) {
    var sd = StructureDefinition.fromJSON(jm(fs.readJSONSync(path.join(extensions, f))));
    pkgExtensions.add(sd);
  }
      }
      var logicals = path.join(fixtures, "logicals");
      for (String f : fs.readdirSync(logicals)) {
  if (Js.truthy(f.endsWith(".json"))) {
    var sd = StructureDefinition.fromJSON(jm(fs.readJSONSync(path.join(logicals, f))));
    pkgLogicals.add(sd);
  }
      }
      var resources = path.join(fixtures, "resources");
      for (String f : fs.readdirSync(resources)) {
  if (Js.truthy(f.endsWith(".json"))) {
    var sd = StructureDefinition.fromJSON(jm(fs.readJSONSync(path.join(resources, f))));
    pkgResources.add(sd);
  }
      }
    }

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      config = ConfigurationJs.fromJs(obj("filePath", path.join(fixtures, "sushi-config.yml"), "id", "sushi-test", "canonical", "http://hl7.org/fhir/sushi-test", "url", "http://hl7.org/fhir/sushi-test/ImplementationGuide/FSHTestIG", "version", "0.1.0", "name", "FSHTestIG", "title", "FSH Test IG", "description", "Provides a simple example of how FSH can be used to create an IG", "dependencies", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.core",
          "version": "3.1.0"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.uv.vhdir",
          "version": "current"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.mcode",
          "uri": "http://hl7.org/fhir/us/mcode/ImplementationGuide/hl7.fhir.us.mcode",
          "id": "mcode",
          "version": "1.0.0"
        }
        """))), "status", "active", "template", "fhir2.base.template", "fhirVersion", new ArrayList<>(Arrays.asList("4.0.1")), "language", "en", "publisher", "James Tuna", "contact", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "name": "Bill Cod",
          "telecom": [
            {
              "system": "url",
              "value": "https://capecodfishermen.org/"
            },
            {
              "system": "email",
              "value": "cod@reef.gov"
            }
          ]
        }
        """))), "license", "CC0-1.0", "parameters", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "code": "copyrightyear",
          "value": "2020+"
        }
        """), JsAssert.json("""
        {
          "code": "releaselabel",
          "value": "CI Build"
        }
        """))), "history", JsAssert.json("{}")));
      pkg = new Package(config);
      pkg.profiles.addAll(pkgProfiles);
      pkg.extensions.addAll(pkgExtensions);
      pkg.logicals.addAll(pkgLogicals);
      pkg.resources.addAll(pkgResources);
      exporter = new IGExporter(pkg, defs, fixtures);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #simple-ig-plus should generate an implementation guide for simple-ig with package containing logical model and custom resource and also ignore custom resource and its instance(s)")
    void shouldGenerateAnImplementationGuideForSimpleIgWithPackageContainingLogicalModelA() {
      // Add instance of custom resource and base resource
      InstanceDefinition customResourceInstance = new InstanceDefinition();
      setProp(customResourceInstance, "resourceType", "CustomResource");
      setProp(customResourceInstance, "id", "FooResource");
      customResourceInstance._instanceMeta.description = "CustomResource Description";
      customResourceInstance._instanceMeta.name = "CustomResource name";
      customResourceInstance._instanceMeta.usage = "Example";
      InstanceDefinition patientInstance = new InstanceDefinition();
      setProp(patientInstance, "resourceType", "Patient");
      setProp(patientInstance, "id", "FooPatient");
      patientInstance._instanceMeta.description = "Sample description";
      patientInstance._instanceMeta.name = "Sample name";
      patientInstance._instanceMeta.usage = "Example";
      pkg.instances.addAll(Arrays.asList(customResourceInstance, patientInstance));
      // Export
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      // Expectations:
      // - resource array contains object for 'StructureDefinition/CustomLogicalModel'
      // - resource array DOES NOT contain object for 'StructureDefinition/CustomResource'
      //   = It is not possible to include custom resources in IGs
      // - parameter array contains object for 'autoload-resources'
      //   = Because custom resources are included in the package being exported,
      //     this parameter is automatically injected and set to false
      assertJsonEqual("""
        {
          "resourceType": "ImplementationGuide",
          "id": "sushi-test",
          "language": "en",
          "url": "http://hl7.org/fhir/sushi-test/ImplementationGuide/FSHTestIG",
          "version": "0.1.0",
          "name": "FSHTestIG",
          "title": "FSH Test IG",
          "status": "active",
          "publisher": "James Tuna",
          "contact": [
            {
              "name": "Bill Cod",
              "telecom": [
                {
                  "system": "url",
                  "value": "https://capecodfishermen.org/"
                },
                {
                  "system": "email",
                  "value": "cod@reef.gov"
                }
              ]
            }
          ],
          "description": "Provides a simple example of how FSH can be used to create an IG",
          "packageId": "sushi-test",
          "license": "CC0-1.0",
          "fhirVersion": [
            "4.0.1"
          ],
          "dependsOn": [
            {
              "id": "hl7_fhir_us_core",
              "uri": "http://hl7.org/fhir/us/core/ImplementationGuide/hl7.fhir.us.core",
              "packageId": "hl7.fhir.us.core",
              "version": "3.1.0"
            },
            {
              "id": "hl7_fhir_uv_vhdir",
              "uri": "http://hl7.org/fhir/uv/vhdir/ImplementationGuide/hl7.core.uv.vhdir",
              "packageId": "hl7.fhir.uv.vhdir",
              "version": "current"
            },
            {
              "id": "mcode",
              "uri": "http://hl7.org/fhir/us/mcode/ImplementationGuide/hl7.fhir.us.mcode",
              "packageId": "hl7.fhir.us.mcode",
              "version": "1.0.0"
            }
          ],
          "definition": {
            "resource": [
              {
                "reference": {
                  "reference": "StructureDefinition/CustomLogicalModel"
                },
                "name": "Custom Logical Model Defined with FSH",
                "description": "This is an example of a custom logical model defined using FSH with parent of Element",
                "exampleBoolean": false
              },
              {
                "reference": {
                  "reference": "Patient/FooPatient"
                },
                "name": "Sample name",
                "description": "Sample description",
                "exampleBoolean": true
              },
              {
                "reference": {
                  "reference": "StructureDefinition/sample-complex-extension"
                },
                "name": "SampleComplexExtension",
                "description": "Base StructureDefinition for Extension Type: Optional Extension Element - found in all resources.",
                "exampleBoolean": false
              },
              {
                "reference": {
                  "reference": "StructureDefinition/sample-observation"
                },
                "name": "SampleObservation",
                "description": "Measurements and simple assertions made about a patient, device or other subject.",
                "exampleBoolean": false
              }
            ],
            "page": {
              "nameUrl": "toc.html",
              "title": "Table of Contents",
              "generation": "html",
              "page": []
            },
            "parameter": [
              {
                "code": "copyrightyear",
                "value": "2020+"
              },
              {
                "code": "releaselabel",
                "value": "CI Build"
              },
              {
                "code": "path-history",
                "value": "http://hl7.org/fhir/sushi-test/history.html"
              },
              {
                "code": "autoload-resources",
                "value": "false"
              }
            ]
          }
        }
        """, content);
    }

    @Test
    @DisplayName("IGExporter #simple-ig-plus should generate an implementation guide for simple-ig with package containing logical model and custom resource and logical model instance")
    void shouldGenerateAnImplementationGuideForSimpleIgWithPackageContainingLogicalModelA2() {
      // Remove custom resources
      setProp(pkg.resources, "length", 0);
      // Add instance of logical model
      InstanceDefinition customLogicalInstance = new InstanceDefinition();
      setProp(customLogicalInstance, "resourceType", "http://hl7.org/fhir/sushi-test/StructureDefinition/CustomLogicalModel");
      setProp(customLogicalInstance, "id", "Bob");
      setProp(customLogicalInstance, "username", "bob");
      setProp(customLogicalInstance, "userId", 1);
      customLogicalInstance._instanceMeta.description = "Example of CustomLogicalModel";
      customLogicalInstance._instanceMeta.name = "Bob";
      customLogicalInstance._instanceMeta.usage = "Example";
      customLogicalInstance._instanceMeta.instanceOfUrl = "http://hl7.org/fhir/sushi-test/StructureDefinition/CustomLogicalModel";
      customLogicalInstance._instanceMeta.sdType = "http://hl7.org/fhir/sushi-test/StructureDefinition/CustomLogicalModel";
      customLogicalInstance._instanceMeta.sdKind = "logical";
      pkg.instances.add(customLogicalInstance);
      // Export
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      // Expectations:
      // - resource array contains object for 'StructureDefinition/CustomLogicalModel'
      // - resource array contains object for 'Binary/Bob' with appropriate extension
      assertJsonEqual("""
        [
          {
            "reference": {
              "reference": "Binary/Bob"
            },
            "name": "Bob",
            "description": "Example of CustomLogicalModel",
            "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/CustomLogicalModel",
            "extension": [
              {
                "url": "http://hl7.org/fhir/tools/StructureDefinition/implementationguide-resource-format",
                "valueCode": "application/fhir+json"
              }
            ]
          },
          {
            "reference": {
              "reference": "StructureDefinition/CustomLogicalModel"
            },
            "name": "Custom Logical Model Defined with FSH",
            "description": "This is an example of a custom logical model defined using FSH with parent of Element",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "StructureDefinition/sample-complex-extension"
            },
            "name": "SampleComplexExtension",
            "description": "Base StructureDefinition for Extension Type: Optional Extension Element - found in all resources.",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "StructureDefinition/sample-observation"
            },
            "name": "SampleObservation",
            "description": "Measurements and simple assertions made about a patient, device or other subject.",
            "exampleBoolean": false
          }
        ]
        """, at(content, "definition.resource"));
    }

  }
  @DisplayName("#simple-ig-meta-profile/package")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class SimpleIgMetaProfilePackage {
    Package pkg;
    IGExporter exporter;
    String tempOut;
    String fixtures;
    Configuration config;
    FHIRDefinitions defs;
    List<StructureDefinition> pkgProfiles = new ArrayList<>(Arrays.asList());
    List<InstanceDefinition> pkgInstances = new ArrayList<>(Arrays.asList());

    @BeforeAll
    void beforeAll() {
      defs = getTestFHIRDefinitions(true, testDefsPath("fhir.no.ig.package#1.0.1"), testDefsPath("r4-definitions"));
      fixtures = path.join(__dirname, "fixtures", "simple-ig-meta-profile", "input");
      var profiles = path.join(fixtures, "profiles");
      for (String f : fs.readdirSync(profiles)) {
  if (Js.truthy(f.endsWith(".json"))) {
    var sd = StructureDefinition.fromJSON(jm(fs.readJSONSync(path.join(profiles, f))));
    pkgProfiles.add(sd);
  }
      }
      var examples = path.join(fixtures, "examples");
      List<Object> exampleIds = new ArrayList<>(Arrays.asList("patient-example", "patient-example-two", "patient-example-three", "patient-example-four", "patient-example-five", "patient-example-six"));
      for (String f : fs.readdirSync(examples)) {
  if (Js.truthy(f.endsWith(".json"))) {
    var instanceDef = InstanceDefinition.fromJSON(jm(fs.readJSONSync(path.join(examples, f))));
    // since instance meta isn't encoded in the JSON, add some here (usually done in the FSH import)
    if (Js.truthy(exampleIds.contains(at(instanceDef, "id")))) {
      setProp(at(instanceDef, "_instanceMeta"), "usage", "Example");
    }
    if (Js.truthy(Objects.equals(at(instanceDef, "id"), "patient-example-two"))) {
      setProp(at(instanceDef, "_instanceMeta"), "title", "Another Patient Example");
      setProp(at(instanceDef, "_instanceMeta"), "description", "Another example of a Patient");
    }
    pkgInstances.add(instanceDef);
  }
      }
    }

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      config = ConfigurationJs.fromJs(obj("filePath", path.join(fixtures, "sushi-config.yml"), "id", "sushi-test", "canonical", "http://hl7.org/fhir/sushi-test", "url", "http://hl7.org/fhir/sushi-test/ImplementationGuide/FSHTestIG", "version", "0.1.0", "name", "FSHTestIG", "title", "FSH Test IG", "description", "Provides a simple example of how FSH can be used to create an IG", "dependencies", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.core",
          "version": "3.1.0"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.uv.vhdir",
          "version": "current"
        }
        """), JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.mcode",
          "uri": "http://hl7.org/fhir/us/mcode/ImplementationGuide/hl7.fhir.us.mcode",
          "id": "mcode",
          "version": "1.0.0"
        }
        """))), "status", "active", "fhirVersion", new ArrayList<>(Arrays.asList("4.0.1")), "language", "en", "publisher", "James Tuna", "contact", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "name": "Bill Cod",
          "telecom": [
            {
              "system": "url",
              "value": "https://capecodfishermen.org/"
            },
            {
              "system": "email",
              "value": "cod@reef.gov"
            }
          ]
        }
        """))), "license", "CC0-1.0", "parameters", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "code": "copyrightyear",
          "value": "2020+"
        }
        """), JsAssert.json("""
        {
          "code": "releaselabel",
          "value": "CI Build"
        }
        """)))));
      pkg = new Package(config);
      pkg.profiles.addAll(pkgProfiles);
      pkg.instances.addAll(pkgInstances);
      exporter = new IGExporter(pkg, defs, fixtures);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #simple-ig-meta-profile/package should set exampleCanonical when meta.profile on an example Instance is a correctly versioned URL")
    void shouldSetExampleCanonicalWhenMetaProfileOnAnExampleInstanceIsACorrectlyVersioned() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      assertNotNull(at(content, "definition.resource"));
      assertJsonEqual("""
        [
          {
            "reference": {
              "reference": "Patient/patient-example-two"
            },
            "name": "Another Patient Example",
            "description": "Another example of a Patient",
            "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient"
          },
          {
            "reference": {
              "reference": "Patient/patient-example"
            },
            "name": "patient-example",
            "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient"
          },
          {
            "reference": {
              "reference": "Patient/patient-example-five"
            },
            "name": "patient-example-five",
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "Patient/patient-example-four"
            },
            "name": "patient-example-four",
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "Patient/patient-example-six"
            },
            "name": "patient-example-six",
            "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient-no-version"
          },
          {
            "reference": {
              "reference": "Patient/patient-example-three"
            },
            "name": "patient-example-three",
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "StructureDefinition/sample-patient"
            },
            "name": "SamplePatient",
            "description": "Demographics and other administrative information about an individual or animal receiving care or other health-related services.",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "StructureDefinition/sample-patient-no-version"
            },
            "name": "SamplePatientNoVersion",
            "description": "A sample patient profile, but without a version.",
            "exampleBoolean": false
          }
        ]
        """, at(content, "definition.resource"));
    }

  }
  @DisplayName("#simple-ig-meta-profile/predefined")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class SimpleIgMetaProfilePredefined {
    Package pkg;
    IGExporter exporter;
    String tempOut;
    String fixtures;
    Configuration config;
    TestFHIRDefinitions defs;

    @BeforeAll
    void beforeAll() {
      defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
      fixtures = path.join(__dirname, "fixtures", "simple-ig-meta-profile");
      defs.loadCustomResources(path.join(fixtures, "input"));
    }

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      config = MinimalConfig.minimalConfig();
      pkg = new Package(config);
      // Add a patient to the package that will be overwritten
      TestFisher fisher = new TestFisher(null, defs, pkg);
      StructureDefinition patient = fisher.fishForStructureDefinition("Patient");
      patient.setId("sample-patient");
      patient.setProperty("name", "SamplePatient");
      patient.setProperty("description", "This should go away");
      pkg.profiles.add(patient);
      exporter = new IGExporter(pkg, defs, fixtures);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #simple-ig-meta-profile/predefined should set exampleCanonical when meta.profile on an example Instance is a correctly versioned URL")
    void shouldSetExampleCanonicalWhenMetaProfileOnAnExampleInstanceIsACorrectlyVersioned() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var content = fs.readJSONSync(igPath);
      assertNotNull(at(content, "definition.resource"));
      assertJsonEqual("""
        [
          {
            "reference": {
              "reference": "Patient/patient-example"
            },
            "name": "patient-example",
            "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient"
          },
          {
            "reference": {
              "reference": "Patient/patient-example-five"
            },
            "name": "patient-example-five",
            "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient-no-version"
          },
          {
            "reference": {
              "reference": "Patient/patient-example-four"
            },
            "name": "patient-example-four",
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "Patient/patient-example-six"
            },
            "name": "patient-example-six",
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "Patient/patient-example-three"
            },
            "name": "patient-example-three",
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "Patient/patient-example-two"
            },
            "name": "patient-example-two",
            "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient"
          },
          {
            "reference": {
              "reference": "StructureDefinition/sample-patient"
            },
            "name": "SamplePatient",
            "description": "Demographics and other administrative information about an individual or animal receiving care or other health-related services.",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "StructureDefinition/sample-patient-no-version"
            },
            "name": "SamplePatientNoVersion",
            "description": "A sample patient profile, but without a version.",
            "exampleBoolean": false
          }
        ]
        """, at(content, "definition.resource"));
    }

  }
  @DisplayName("#customized-ig")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class CustomizedIg {
    Package pkg;
    IGExporter exporter;
    String tempOut;
    String fixtures;
    Configuration config;
    FHIRDefinitions defs;

    @BeforeAll
    void beforeAll() {
      fixtures = path.join(__dirname, "fixtures", "customized-ig");
      defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
    }

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      config = MinimalConfig.minimalConfig();
      pkg = new Package(config);
      exporter = new IGExporter(pkg, defs, fixtures);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #customized-ig should provide a default path-history for an HL7 IG")
    void shouldProvideADefaultPathHistoryForAnHL7IG() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "code": "path-history",
          "value": "http://hl7.org/fhir/us/minimal/history.html"
        }
        """), at(igContent, "definition.parameter"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig should not provide a default path-history for a non-HL7 IG")
    void shouldNotProvideADefaultPathHistoryForANonHL7IG() {
      config.canonical = "http://different-domain.org/fhir/fhir.us.minimal";
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      // expect(...).not.toContainEqual(expect.objectContaining({ code: 'path-history' }))
      assertFalse(some(at(igContent, "definition.parameter"), (p) -> Js.isMatch(p, JsAssert.json("""
        {
          "code": "path-history"
        }
        """))));
    }

    @Test
    @DisplayName("IGExporter #customized-ig should use configured pages when provided")
    void shouldUseConfiguredPagesWhenProvided() {
      config.pages = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "nameUrl": "index.md",
          "title": "Home",
          "generation": "markdown"
        }
        """), JsAssert.json("""
        {
          "nameUrl": "other-page.md",
          "title": "Some Other Page",
          "generation": "markdown"
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "nameUrl": "index.html",
            "title": "Home",
            "generation": "markdown"
          },
          {
            "nameUrl": "other-page.html",
            "title": "Some Other Page",
            "generation": "markdown"
          }
        ]
        """, at(igContent, "definition.page.page"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig should provide default title and generation for configured pages when no title is provided")
    void shouldProvideDefaultTitleAndGenerationForConfiguredPagesWhenNoTitleIsProvided() {
      config.pages = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "nameUrl": "index.md",
          "title": "Home",
          "generation": "markdown"
        }
        """), JsAssert.json("""
        {
          "nameUrl": "other-page.md"
        }
        """), JsAssert.json("""
        {
          "nameUrl": "something-special.xml",
          "generation": "xml"
        }
        """), JsAssert.json("""
        {
          "nameUrl": "unsupported.html"
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "nameUrl": "index.html",
            "title": "Home",
            "generation": "markdown"
          },
          {
            "nameUrl": "other-page.html",
            "title": "Other Page",
            "generation": "markdown"
          },
          {
            "nameUrl": "something-special.html",
            "title": "Something Special",
            "generation": "xml"
          },
          {
            "nameUrl": "unsupported.html",
            "title": "Unsupported",
            "generation": "html"
          }
        ]
        """, at(igContent, "definition.page.page"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig should maintain page hierarchy for configured pages")
    void shouldMaintainPageHierarchyForConfiguredPages() {
      config.pages = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "nameUrl": "index.md",
          "title": "Home",
          "generation": "markdown",
          "page": [
            {
              "nameUrl": "something-special.xml",
              "title": "Something Special",
              "generation": "xml"
            },
            {
              "nameUrl": "other-page.md",
              "title": "Other Page",
              "generation": "markdown",
              "page": [
                {
                  "nameUrl": "unsupported.html",
                  "title": "Unsupported",
                  "generation": "html"
                }
              ]
            }
          ]
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "nameUrl": "index.html",
            "title": "Home",
            "generation": "markdown",
            "page": [
              {
                "nameUrl": "something-special.html",
                "title": "Something Special",
                "generation": "xml"
              },
              {
                "nameUrl": "other-page.html",
                "title": "Other Page",
                "generation": "markdown",
                "page": [
                  {
                    "nameUrl": "unsupported.html",
                    "title": "Unsupported",
                    "generation": "html"
                  }
                ]
              }
            ]
          }
        ]
        """, at(igContent, "definition.page.page"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig should not log an error when no file exists for a configured page")
    void shouldNotLogAnErrorWhenNoFileExistsForAConfiguredPage() {
      config.pages = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "nameUrl": "index.md",
          "title": "Home",
          "generation": "markdown"
        }
        """), JsAssert.json("""
        {
          "nameUrl": "nothing.md",
          "title": "Nothing",
          "generation": "markdown"
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "nameUrl": "index.html",
            "title": "Home",
            "generation": "markdown"
          },
          {
            "nameUrl": "nothing.html",
            "title": "Nothing",
            "generation": "markdown"
          }
        ]
        """, at(igContent, "definition.page.page"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
    }

    @Test
    @DisplayName("IGExporter #customized-ig should copy over the templates property")
    void shouldCopyOverTheTemplatesProperty() {
      config.templates = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "code": "foo",
          "source": "bar",
          "scope": "mouthwash"
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "code": "foo",
            "source": "bar",
            "scope": "mouthwash"
          }
        ]
        """, at(igContent, "definition.template"));
    }

  }
  @DisplayName("#customized-ig-with-resources")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class CustomizedIgWithResources {
    Package pkg;
    IGExporter exporter;
    String tempOut;
    String fixtures;
    Configuration config;
    TestFHIRDefinitions defs;
    InstanceDefinition testScriptInstance;

    @BeforeAll
    void beforeAll() {
      defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
      fixtures = path.join(__dirname, "fixtures", "customized-ig-with-resources");
      defs.loadCustomResources(path.join(fixtures, "input"));
    }

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      config = minimalConfigWithMenu();
      config.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/BazPatient"
          },
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/implementationguide-resource-format",
              "valueCode": "text/plain"
            }
          ]
        }
        """))));
      pkg = new Package(config);
      // Add a patient to the package that will be overwritten
      TestFisher fisher = new TestFisher(null, defs, pkg);
      StructureDefinition patient = fisher.fishForStructureDefinition("Patient");
      patient.setId("MyPatient");
      patient.setProperty("name", "MyPatient");
      patient.setProperty("description", "This should go away");
      pkg.profiles.add(patient);
      InstanceDefinition patientInstance = new InstanceDefinition();
      setProp(patientInstance, "resourceType", "Patient");
      setProp(patientInstance, "id", "FooPatient");
      patientInstance._instanceMeta.description = "This should stay";
      patientInstance._instanceMeta.name = "StayName";
      patientInstance._instanceMeta.usage = "Example";
      pkg.instances.add(patientInstance);
      testScriptInstance = new InstanceDefinition();
      setProp(testScriptInstance, "resourceType", "TestScript");
      setProp(testScriptInstance, "id", "AnotherTestScript");
      testScriptInstance._instanceMeta.name = "AnotherTestScript";
      testScriptInstance._instanceMeta.usage = "Example";
      pkg.instances.add(testScriptInstance);
      exporter = new IGExporter(pkg, defs, fixtures);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should not copy over resource files")
    void shouldNotCopyOverResourceFiles() {
      exporter.export(tempOut);
      List<String> dirNames = new ArrayList<>(Arrays.asList("capabilities", "extensions", "models", "operations", "profiles", "resources", "vocabulary", "examples"));
      for (var dirName : dirNames) {
        // No provided resources are copied to output
        assertFalse(Js.truthy(fs.existsSync(path.join(tempOut, "input", dirName))));
      }
      assertTrue(Js.truthy(fs.existsSync(path.join(tempOut, "fsh-generated", "resources"))));
      var pageContentFiles = fs.readdirSync(path.join(tempOut, "fsh-generated", "resources"));
      assertEquals(1, len(pageContentFiles));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should add basic resource references to the ImplementationGuide resource")
    void shouldAddBasicResourceReferencesToTheImplementationGuideResource() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/MyLM"
          },
          "name": "MyLM",
          "exampleBoolean": false
        }
        """), at(igContent, "definition.resource"));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "OperationDefinition/MyOD"
          },
          "name": "Populate Questionnaire",
          "exampleBoolean": false
        }
        """), at(igContent, "definition.resource"));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "OperationDefinition/AnotherOD"
          },
          "name": "Title for Populate Another Questionnaire",
          "exampleBoolean": false
        }
        """), at(igContent, "definition.resource"));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/BazPatient"
          },
          "name": "BazPatient",
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/implementationguide-resource-format",
              "valueCode": "text/plain"
            }
          ],
          "exampleBoolean": false
        }
        """), at(igContent, "definition.resource"));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "ValueSet/MyVS"
          },
          "name": "Yes/No/Don't Know",
          "exampleBoolean": false
        }
        """), at(igContent, "definition.resource"));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/patient-birthPlace"
          },
          "name": "Birth Place",
          "exampleBoolean": false
        }
        """), at(igContent, "definition.resource"));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/patient-birthPlaceXML"
          },
          "name": "Birth Place",
          "exampleBoolean": false
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should overwrite existing resource references in the ImplementationGuide resource")
    void shouldOverwriteExistingResourceReferencesInTheImplementationGuideResource() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      // Should only have one copy of MyPatient
      assertEquals(1, filter(at(igContent, "definition.resource"), (r) -> Objects.equals(at(r, "reference.reference"), "StructureDefinition/MyPatient")).size());
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/MyPatient"
          },
          "name": "MyPatient",
          "exampleBoolean": false
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should add resource references with a description to the ImplementationGuide resource")
    void shouldAddResourceReferencesWithADescriptionToTheImplementationGuideResource() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "CapabilityStatement/MyCS"
          },
          "name": "Base FHIR Capability Statement (Empty)",
          "description": "Test description",
          "exampleBoolean": false
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should not include a description for non-conformance resources when adding them to the ImplementationGuide resource")
    void shouldNotIncludeADescriptionForNonConformanceResourcesWhenAddingThemToTheImpleme() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Goal/GoalWithDescription"
          },
          "name": "GoalWithDescription",
          "exampleBoolean": true
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should add example references to the ImplementationGuide resource")
    void shouldAddExampleReferencesToTheImplementationGuideResource() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/FooPatient"
          },
          "description": "This should stay",
          "name": "StayName",
          "exampleBoolean": true
        }
        """), at(igContent, "definition.resource"));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/BarPatient"
          },
          "name": "BarPatient",
          "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/MyPatient"
        }
        """), at(igContent, "definition.resource"));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Goal/GoalWithDescription"
          },
          "name": "GoalWithDescription",
          "exampleBoolean": true
        }
        """), at(igContent, "definition.resource"));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "TestScript/MyTestScript"
          },
          "name": "Test script title",
          "description": "This is the description of my nice test script.",
          "exampleBoolean": true
        }
        """), at(igContent, "definition.resource"));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "TestScript/AnotherTestScript"
          },
          "name": "AnotherTestScript",
          "exampleBoolean": true
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should add an example reference with a title attribute to the ImplementationGuide resource")
    void shouldAddAnExampleReferenceWithATitleAttributeToTheImplementationGuideResource() {
      setProp(testScriptInstance, "title", "Another good title");
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "TestScript/AnotherTestScript"
          },
          "name": "Another good title",
          "exampleBoolean": true
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should add an example reference with a description attribute to the ImplementationGuide resource")
    void shouldAddAnExampleReferenceWithADescriptionAttributeToTheImplementationGuideReso() {
      setProp(testScriptInstance, "description", "This is the description for another test script.");
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "TestScript/AnotherTestScript"
          },
          "name": "AnotherTestScript",
          "description": "This is the description for another test script.",
          "exampleBoolean": true
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should add resource references with a title to the ImplementationGuide resource")
    void shouldAddResourceReferencesWithATitleToTheImplementationGuideResource() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/MyTitlePatient"
          },
          "name": "This patient has a title",
          "exampleBoolean": false
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should add resource references with a description and name extension to the ImplementationGuide resource")
    void shouldAddResourceReferencesWithADescriptionAndNameExtensionToTheImplementationGu() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/MetaExtensionPatient"
          },
          "name": "MetaExtension Patient Name",
          "description": "MetaExtension Patient Description",
          "exampleBoolean": true
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should prefer configured names and descriptions to extensions on resource references")
    void shouldPreferConfiguredNamesAndDescriptionsToExtensionsOnResourceReferences() {
      config.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/MetaExtensionPatient"
          },
          "name": "Configured Name",
          "description": "Configured Description"
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/MetaExtensionPatient"
          },
          "name": "Configured Name",
          "description": "Configured Description",
          "exampleBoolean": true
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should add non example resource references with a description and name extension to the ImplementationGuide resource")
    void shouldAddNonExampleResourceReferencesWithADescriptionAndNameExtensionToTheImplem() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/MetaExtensionNotExamplePatient"
          },
          "name": "MetaExtensionNotExample Patient Name",
          "description": "MetaExtensionNotExample Patient Description",
          "exampleBoolean": false
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should prefer non example configured names and descriptions to extensions on resource references")
    void shouldPreferNonExampleConfiguredNamesAndDescriptionsToExtensionsOnResourceRefere() {
      config.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/MetaExtensionNotExamplePatient"
          },
          "name": "Configured Name",
          "description": "Configured Description"
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/MetaExtensionNotExamplePatient"
          },
          "name": "Configured Name",
          "description": "Configured Description",
          "exampleBoolean": false
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should omit predefined resources that are configured to be omitted")
    void shouldOmitPredefinedResourcesThatAreConfiguredToBeOmitted() {
      config.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/MyPatient"
          },
          "omit": true
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      var myPatient = find(at(igContent, "definition.resource"), (r) -> Objects.equals(at(r, "reference.reference"), "StructureDefinition/MyPatient"));
      assertNull(myPatient);
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should add resources in the order in the configuration when all generated and predefined resources are in the configuration")
    void shouldAddResourcesInTheOrderInTheConfigurationWhenAllGeneratedAndPredefinedResou() {
      config.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "CapabilityStatement/MyCS"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/MetaExtensionPatient"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/patient-birthPlace"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/patient-birthPlaceXML"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/MyPatient"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "ValueSet/MyVS"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "OperationDefinition/MyOD"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "OperationDefinition/AnotherOD"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/MyTitlePatient"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/MyLM"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/BazPatient"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/MetaExtensionNotExamplePatient"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/FooPatient"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/BarPatient"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "TestScript/MyTestScript"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "TestScript/AnotherTestScript"
          }
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Goal/GoalWithDescription"
          }
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "reference": {
              "reference": "CapabilityStatement/MyCS"
            },
            "name": "Base FHIR Capability Statement (Empty)",
            "description": "Test description",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "Patient/MetaExtensionPatient"
            },
            "name": "MetaExtension Patient Name",
            "description": "MetaExtension Patient Description",
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "StructureDefinition/patient-birthPlace"
            },
            "name": "Birth Place",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "StructureDefinition/patient-birthPlaceXML"
            },
            "name": "Birth Place",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "StructureDefinition/MyPatient"
            },
            "name": "MyPatient",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "ValueSet/MyVS"
            },
            "name": "Yes/No/Don't Know",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "OperationDefinition/MyOD"
            },
            "name": "Populate Questionnaire",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "OperationDefinition/AnotherOD"
            },
            "name": "Title for Populate Another Questionnaire",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "StructureDefinition/MyTitlePatient"
            },
            "name": "This patient has a title",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "StructureDefinition/MyLM"
            },
            "name": "MyLM",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "Patient/BazPatient"
            },
            "name": "BazPatient",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "Patient/MetaExtensionNotExamplePatient"
            },
            "name": "MetaExtensionNotExample Patient Name",
            "description": "MetaExtensionNotExample Patient Description",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "Patient/FooPatient"
            },
            "description": "This should stay",
            "name": "StayName",
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "Patient/BarPatient"
            },
            "name": "BarPatient",
            "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/MyPatient"
          },
          {
            "reference": {
              "reference": "TestScript/MyTestScript"
            },
            "name": "Test script title",
            "description": "This is the description of my nice test script.",
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "TestScript/AnotherTestScript"
            },
            "name": "AnotherTestScript",
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "Goal/GoalWithDescription"
            },
            "name": "GoalWithDescription",
            "exampleBoolean": true
          }
        ]
        """, at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should add resources in group order when all generated and predefined resources are in the group configuration")
    void shouldAddResourcesInGroupOrderWhenAllGeneratedAndPredefinedResourcesAreInTheGrou() {
      config.groups = groups(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "id": "FirstGroup",
          "name": "First Group",
          "description": "The first group of resources",
          "resources": [
            "CapabilityStatement/MyCS",
            "OperationDefinition/MyOD",
            "OperationDefinition/AnotherOD",
            "Goal/GoalWithDescription"
          ]
        }
        """), JsAssert.json("""
        {
          "id": "PatientThings",
          "name": "Patient Things",
          "description": "Things related to patients",
          "resources": [
            "StructureDefinition/MyPatient",
            "StructureDefinition/patient-birthPlaceXML",
            "StructureDefinition/patient-birthPlace",
            "Patient/MetaExtensionPatient",
            "StructureDefinition/MyTitlePatient",
            "Patient/FooPatient",
            "Patient/BarPatient",
            "Patient/BazPatient",
            "Patient/MetaExtensionNotExamplePatient"
          ]
        }
        """), JsAssert.json("""
        {
          "id": "OtherThings",
          "name": "Other Things",
          "description": "This group has other things",
          "resources": [
            "StructureDefinition/MyLM",
            "ValueSet/MyVS",
            "TestScript/MyTestScript",
            "TestScript/AnotherTestScript"
          ]
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "reference": {
              "reference": "CapabilityStatement/MyCS"
            },
            "name": "Base FHIR Capability Statement (Empty)",
            "description": "Test description",
            "exampleBoolean": false,
            "groupingId": "FirstGroup"
          },
          {
            "reference": {
              "reference": "OperationDefinition/MyOD"
            },
            "name": "Populate Questionnaire",
            "exampleBoolean": false,
            "groupingId": "FirstGroup"
          },
          {
            "reference": {
              "reference": "OperationDefinition/AnotherOD"
            },
            "name": "Title for Populate Another Questionnaire",
            "exampleBoolean": false,
            "groupingId": "FirstGroup"
          },
          {
            "reference": {
              "reference": "Goal/GoalWithDescription"
            },
            "name": "GoalWithDescription",
            "exampleBoolean": true,
            "groupingId": "FirstGroup"
          },
          {
            "reference": {
              "reference": "StructureDefinition/MyPatient"
            },
            "name": "MyPatient",
            "exampleBoolean": false,
            "groupingId": "PatientThings"
          },
          {
            "reference": {
              "reference": "StructureDefinition/patient-birthPlaceXML"
            },
            "name": "Birth Place",
            "exampleBoolean": false,
            "groupingId": "PatientThings"
          },
          {
            "reference": {
              "reference": "StructureDefinition/patient-birthPlace"
            },
            "name": "Birth Place",
            "exampleBoolean": false,
            "groupingId": "PatientThings"
          },
          {
            "reference": {
              "reference": "Patient/MetaExtensionPatient"
            },
            "name": "MetaExtension Patient Name",
            "description": "MetaExtension Patient Description",
            "exampleBoolean": true,
            "groupingId": "PatientThings"
          },
          {
            "reference": {
              "reference": "StructureDefinition/MyTitlePatient"
            },
            "name": "This patient has a title",
            "exampleBoolean": false,
            "groupingId": "PatientThings"
          },
          {
            "reference": {
              "reference": "Patient/FooPatient"
            },
            "description": "This should stay",
            "name": "StayName",
            "exampleBoolean": true,
            "groupingId": "PatientThings"
          },
          {
            "reference": {
              "reference": "Patient/BarPatient"
            },
            "name": "BarPatient",
            "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/MyPatient",
            "groupingId": "PatientThings"
          },
          {
            "reference": {
              "reference": "Patient/BazPatient"
            },
            "name": "BazPatient",
            "exampleBoolean": false,
            "extension": [
              {
                "url": "http://hl7.org/fhir/StructureDefinition/implementationguide-resource-format",
                "valueCode": "text/plain"
              }
            ],
            "groupingId": "PatientThings"
          },
          {
            "reference": {
              "reference": "Patient/MetaExtensionNotExamplePatient"
            },
            "name": "MetaExtensionNotExample Patient Name",
            "description": "MetaExtensionNotExample Patient Description",
            "exampleBoolean": false,
            "groupingId": "PatientThings"
          },
          {
            "reference": {
              "reference": "StructureDefinition/MyLM"
            },
            "name": "MyLM",
            "exampleBoolean": false,
            "groupingId": "OtherThings"
          },
          {
            "reference": {
              "reference": "ValueSet/MyVS"
            },
            "name": "Yes/No/Don't Know",
            "exampleBoolean": false,
            "groupingId": "OtherThings"
          },
          {
            "reference": {
              "reference": "TestScript/MyTestScript"
            },
            "name": "Test script title",
            "description": "This is the description of my nice test script.",
            "exampleBoolean": true,
            "groupingId": "OtherThings"
          },
          {
            "reference": {
              "reference": "TestScript/AnotherTestScript"
            },
            "name": "AnotherTestScript",
            "exampleBoolean": true,
            "groupingId": "OtherThings"
          }
        ]
        """, at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should add resources sorted by name, title, or id when not all generated or predefined resources are in the configuration")
    void shouldAddResourcesSortedByNameTitleOrIdWhenNotAllGeneratedOrPredefinedResourcesA() {
      // when no specific sort is defined, resources are sorted by the "name" attribute in the IG.
      // if this attribute is not present, use "reference.reference" as backup.
      // this should only happen for configured resources.
      // add an extra config-only resource
      config.resources.add((JsObject) JsAssert.json("""
        {
          "reference": {
            "reference": "Observation/ConfigOnlyObservation"
          },
          "exampleBoolean": true
        }
        """));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "reference": {
              "reference": "TestScript/AnotherTestScript"
            },
            "name": "AnotherTestScript",
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "Patient/BarPatient"
            },
            "name": "BarPatient",
            "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/MyPatient"
          },
          {
            "reference": {
              "reference": "CapabilityStatement/MyCS"
            },
            "name": "Base FHIR Capability Statement (Empty)",
            "description": "Test description",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "Patient/BazPatient"
            },
            "name": "BazPatient",
            "exampleBoolean": false,
            "extension": [
              {
                "url": "http://hl7.org/fhir/StructureDefinition/implementationguide-resource-format",
                "valueCode": "text/plain"
              }
            ]
          },
          {
            "reference": {
              "reference": "StructureDefinition/patient-birthPlace"
            },
            "name": "Birth Place",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "StructureDefinition/patient-birthPlaceXML"
            },
            "name": "Birth Place",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "Goal/GoalWithDescription"
            },
            "name": "GoalWithDescription",
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "Patient/MetaExtensionPatient"
            },
            "name": "MetaExtension Patient Name",
            "description": "MetaExtension Patient Description",
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "Patient/MetaExtensionNotExamplePatient"
            },
            "name": "MetaExtensionNotExample Patient Name",
            "description": "MetaExtensionNotExample Patient Description",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "StructureDefinition/MyLM"
            },
            "name": "MyLM",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "StructureDefinition/MyPatient"
            },
            "name": "MyPatient",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "Observation/ConfigOnlyObservation"
            },
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "OperationDefinition/MyOD"
            },
            "name": "Populate Questionnaire",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "Patient/FooPatient"
            },
            "description": "This should stay",
            "name": "StayName",
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "TestScript/MyTestScript"
            },
            "name": "Test script title",
            "description": "This is the description of my nice test script.",
            "exampleBoolean": true
          },
          {
            "reference": {
              "reference": "StructureDefinition/MyTitlePatient"
            },
            "name": "This patient has a title",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "OperationDefinition/AnotherOD"
            },
            "name": "Title for Populate Another Questionnaire",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "ValueSet/MyVS"
            },
            "name": "Yes/No/Don't Know",
            "exampleBoolean": false
          }
        ]
        """, at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-resources should log a warning for input files missing resourceType or id")
    void shouldLogAWarningForInputFilesMissingResourceTypeOrId() {
      exporter.export(tempOut);
      assertEquals(3, loggerSpy.getAllMessages("warn").size());
      assertMatches(".*InvalidPatient\\.json is missing id", "", loggerSpy.getMessageAtIndex(0, "warn"));
      assertMatches(".*InvalidPatient2\\.json is missing resourceType", "", loggerSpy.getMessageAtIndex(1, "warn"));
      assertMatches(".*InvalidPatient3\\.json is missing resourceType and id", "", loggerSpy.getMessageAtIndex(2, "warn"));
    }

  }
  @DisplayName("#customized-ig-with-nested-resources")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class CustomizedIgWithNestedResources {
    IGExporter exporter;
    String tempOut;
    String fixtures;
    TestFHIRDefinitions defs;
    Configuration config;

    @BeforeAll
    void beforeAll() {
      defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
      fixtures = path.join(__dirname, "fixtures", "customized-ig-with-nested-resources");
      defs.loadCustomResources(path.join(fixtures, "input"));
    }

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      config = minimalConfigWithMenu();
      config.parameters = jsList(new ArrayList<>(Arrays.asList()));
      config.parameters.add((JsObject) JsAssert.json("""
        {
          "code": "path-resource",
          "value": "input/resources/path-resource-nest"
        }
        """));
      Package pkg = new Package(config);
      exporter = new IGExporter(pkg, defs, path.resolve(fixtures));
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-nested-resources should add only non-nested resource references to the ImplementationGuide resource")
    void shouldAddOnlyNonNestedResourceReferencesToTheImplementationGuideResource() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertEquals(3, len(at(igContent, "definition.resource")));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/BarPatient"
          },
          "name": "BarPatient",
          "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/MyPatient"
        }
        """), at(igContent, "definition.resource"));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/MyPatient"
          },
          "name": "MyPatient",
          "exampleBoolean": false
        }
        """), at(igContent, "definition.resource"));
      assertContainsEqual(JsAssert.json("""
        {
          "exampleBoolean": false,
          "name": "MyCorrectlyNestedPatient",
          "reference": {
            "reference": "StructureDefinition/MyCorrectlyNestedPatient"
          }
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-nested-resources should warn on deeply nested resources")
    void shouldWarnOnDeeplyNestedResources() {
      exporter.export(tempOut);
      String warning = loggerSpy.getFirstMessage("warn");
      assertTrue(warning.contains("The following files were not added to the ImplementationGuide JSON"));
      assertTrue(warning.contains(path.join("nested1", "StructureDefinition-MyTitlePatient.json")));
      assertTrue(warning.contains(path.join("nested2", "ValueSet-MyVS.json")));
      assertTrue(warning.contains(path.join("path-resource-double-nest", "john", "Patient-John.json")));
      assertTrue(warning.contains(path.join("path-resource-double-nest", "jack", "examples", "Patient-Jack.json")));
      assertFalse(warning.contains("Patient-BarPatient.json"));
      assertFalse(warning.contains("StructureDefinition-MyPatient.json"));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-nested-resources should not warn on deeply nested resources when implicated by the path-resource parameter")
    void shouldNotWarnOnDeeplyNestedResourcesWhenImplicatedByThePathResourceParameter() {
      defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
      fixtures = path.join(__dirname, "fixtures", "customized-ig-with-nested-resources");
      defs.loadCustomResources(path.join(fixtures, "input"));
      exporter.export(tempOut);
      String warning = loggerSpy.getFirstMessage("warn");
      assertTrue(warning.contains("The following files were not added to the ImplementationGuide JSON"));
      assertTrue(warning.contains(path.join("nested1", "StructureDefinition-MyTitlePatient.json")));
      assertTrue(warning.contains(path.join("nested2", "ValueSet-MyVS.json")));
      // path-resource-double-nest is not included in config
      assertTrue(warning.contains(path.join("path-resource-double-nest", "john", "Patient-John.json")));
      assertTrue(warning.contains(path.join("path-resource-double-nest", "jack", "examples", "Patient-Jack.json")));
      // path-resource-nest is included in config
      assertFalse(warning.contains(path.join("path-resource-nest", "StructureDefinition-MyCorrectlyNestedPatient.json")));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-nested-resources should not warn on deeply nested resources that are included in the path-resource parameter with a directory and wildcard")
    void shouldNotWarnOnDeeplyNestedResourcesThatAreIncludedInThePathResourceParameterWit() {
      var config = minimalConfigWithMenu();
      config.parameters = new ArrayList<>();
      config.parameters.add((JsObject) JsAssert.json("""
        {
          "code": "path-resource",
          "value": "input/resources/path-resource-double-nest/*"
        }
        """));
      Package pkg = new Package(config);
      exporter = new IGExporter(pkg, defs, path.resolve(fixtures));
      exporter.export(tempOut);
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      String warning = loggerSpy.getFirstMessage("warn");
      assertFalse(warning.contains(path.join("path-resource-double-nest", "john", "Patient-John.json")));
      assertFalse(warning.contains(path.join("path-resource-double-nest", "jack", "examples", "Patient-Jack.json")));
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-nested-resources should warn on deeply nested resources that are included in the path-resource parameter with a directory but NO wildcard")
    void shouldWarnOnDeeplyNestedResourcesThatAreIncludedInThePathResourceParameterWithAD() {
      var config = minimalConfigWithMenu();
      config.parameters = new ArrayList<>();
      config.parameters.add((JsObject) JsAssert.json("""
        {
          "code": "path-resource",
          "value": "input/resources/path-resource-double-nest"
        }
        """));
      Package pkg = new Package(config);
      exporter = new IGExporter(pkg, defs, path.resolve(fixtures));
      exporter.export(tempOut);
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      String warning = loggerSpy.getFirstMessage("warn");
      var warningLines = warning.split("\n");
      List<Object> johnLine = filter(warningLines, (w) -> Js.truthy(((String) w).contains(path.join("path-resource-double-nest", "john", "Patient-John.json"))));
      List<Object> jackLine = filter(warningLines, (w) -> Js.truthy(((String) w).contains(path.join("path-resource-double-nest", "john", "Patient-John.json"))));
      // Check that both nested files are logged in the warning, but check that they're only there once
      assertEquals(1, johnLine.size());
      assertEquals(1, jackLine.size());
    }

  }
  @DisplayName("#customized-ig-with-logical-model-example")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class CustomizedIgWithLogicalModelExample {
    Package pkg;
    IGExporter exporter;
    String tempOut;
    String fixtures;
    Configuration config;
    TestFHIRDefinitions defs;

    @BeforeAll
    void beforeAll() {
      defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
      fixtures = path.join(__dirname, "fixtures", "customized-ig-with-logical-model-example");
      defs.loadCustomResources(path.join(fixtures, "input"));
    }

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      config = minimalConfigWithMenu();
      config.resources = jsList(new ArrayList<>(Arrays.asList(obj("reference", JsAssert.json("""
        {
          "reference": "Binary/example-logical-model-json"
        }
        """), "extension", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "url": "http://hl7.org/fhir/tools/StructureDefinition/implementationguide-resource-format",
          "valueCode": "application/json"
        }
        """))), "name", "Example of LM JSON", "exampleCanonical", "" + Js.toStr(config.canonical) + "/StructureDefinition/MyLM"), obj("reference", JsAssert.json("""
        {
          "reference": "Binary/second-example-logical-model-json"
        }
        """), "extension", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "url": "http://hl7.org/fhir/StructureDefinition/implementationguide-resource-format",
          "valueCode": "application/json"
        }
        """))), "name", "Another Example of LM JSON", "exampleCanonical", "" + Js.toStr(config.canonical) + "/StructureDefinition/MyLM"), obj("reference", JsAssert.json("""
        {
          "reference": "Binary/canonical-url-resourceType-example-logical-model"
        }
        """), "extension", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "url": "http://hl7.org/fhir/tools/StructureDefinition/implementationguide-resource-format",
          "valueCode": "application/json"
        }
        """))), "name", "Example of LM JSON with the full canonical URL as the resourceType", "exampleCanonical", "" + Js.toStr(config.canonical) + "/StructureDefinition/MyLM"), obj("reference", JsAssert.json("""
        {
          "reference": "Binary/example-logical-model-xml"
        }
        """), "extension", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "url": "http://hl7.org/fhir/tools/StructureDefinition/implementationguide-resource-format",
          "valueCode": "application/xml"
        }
        """))), "name", "Example of LM XML", "exampleCanonical", "" + Js.toStr(config.canonical) + "/StructureDefinition/MyLM"))));
      pkg = new Package(config);
      exporter = new IGExporter(pkg, defs, fixtures);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-logical-model-example should add logical model and example resource references (manually listed in config) to the ImplementationGuide resource")
    void shouldAddLogicalModelAndExampleResourceReferencesManuallyListedInConfigToTheImpl() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertEquals(5, len(at(igContent, "definition.resource")));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/MyLM"
          },
          "name": "MyLM",
          "exampleBoolean": false
        }
        """), at(igContent, "definition.resource"));
      assertContainsEqual(obj("reference", JsAssert.json("""
        {
          "reference": "Binary/example-logical-model-json"
        }
        """), "extension", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "url": "http://hl7.org/fhir/tools/StructureDefinition/implementationguide-resource-format",
          "valueCode": "application/json"
        }
        """))), "name", "Example of LM JSON", "exampleCanonical", "" + Js.toStr(config.canonical) + "/StructureDefinition/MyLM"), at(igContent, "definition.resource"));
      assertContainsEqual(obj("reference", JsAssert.json("""
        {
          "reference": "Binary/second-example-logical-model-json"
        }
        """), "extension", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "url": "http://hl7.org/fhir/StructureDefinition/implementationguide-resource-format",
          "valueCode": "application/json"
        }
        """))), "name", "Another Example of LM JSON", "exampleCanonical", "" + Js.toStr(config.canonical) + "/StructureDefinition/MyLM"), at(igContent, "definition.resource"));
      assertContainsEqual(obj("reference", JsAssert.json("""
        {
          "reference": "Binary/canonical-url-resourceType-example-logical-model"
        }
        """), "extension", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "url": "http://hl7.org/fhir/tools/StructureDefinition/implementationguide-resource-format",
          "valueCode": "application/json"
        }
        """))), "name", "Example of LM JSON with the full canonical URL as the resourceType", "exampleCanonical", "" + Js.toStr(config.canonical) + "/StructureDefinition/MyLM"), at(igContent, "definition.resource"));
      assertContainsEqual(obj("reference", JsAssert.json("""
        {
          "reference": "Binary/example-logical-model-xml"
        }
        """), "extension", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "url": "http://hl7.org/fhir/tools/StructureDefinition/implementationguide-resource-format",
          "valueCode": "application/xml"
        }
        """))), "name", "Example of LM XML", "exampleCanonical", "" + Js.toStr(config.canonical) + "/StructureDefinition/MyLM"), at(igContent, "definition.resource"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("extension .* has been deprecated\\. Update the configuration for Binary\\/second-example-logical-model-json.*", "", loggerSpy.getLastMessage("warn"));
    }

  }
  @DisplayName("#customized-ig-with-binary-example")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class CustomizedIgWithBinaryExample {
    Package pkg;
    IGExporter exporter;
    String tempOut;
    String fixtures;
    Configuration config;
    TestFHIRDefinitions defs;

    @BeforeAll
    void beforeAll() {
      defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
      fixtures = path.join(__dirname, "fixtures", "customized-ig-with-binary-example");
      defs.loadCustomResources(path.join(fixtures, "input"));
    }

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      config = minimalConfigWithMenu();
      config.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "Binary/MissingResourceTypeAndId"
          },
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/implementationguide-resource-format",
              "valueCode": "application/json"
            }
          ]
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Binary/MissingId"
          },
          "extension": [
            {
              "url": "http://hl7.org/fhir/tools/StructureDefinition/implementationguide-resource-format",
              "valueCode": "application/fhir+json"
            }
          ]
        }
        """))));
      pkg = new Package(config);
      exporter = new IGExporter(pkg, defs, fixtures);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #customized-ig-with-binary-example should allow binary resources that do not have resourceType or id but are specified correctly in config")
    void shouldAllowBinaryResourcesThatDoNotHaveResourceTypeOrIdButAreSpecifiedCorrectlyI() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      // resource entry is unchanged
      assertEquals(2, len(at(igContent, "definition.resource")));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Binary/MissingResourceTypeAndId"
          },
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/implementationguide-resource-format",
              "valueCode": "application/json"
            }
          ]
        }
        """), at(igContent, "definition.resource"));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Binary/MissingId"
          },
          "extension": [
            {
              "url": "http://hl7.org/fhir/tools/StructureDefinition/implementationguide-resource-format",
              "valueCode": "application/fhir+json"
            }
          ]
        }
        """), at(igContent, "definition.resource"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertMatches("extension .* has been deprecated\\. Update the configuration for Binary\\/MissingResourceTypeAndId.*", "", loggerSpy.getLastMessage("warn"));
    }

  }
  @DisplayName("#ref-by-id-or-name")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class RefByIdOrName {
    Package pkg;
    String tempOut;
    FHIRDefinitions defs;
    Object doResourceTest(String profile, String extension, String codeSystem, String example) { return doResourceTest(profile, extension, codeSystem, example, false); }
    Object doResourceTest(String profile, String extension, String codeSystem, String example, boolean skipExpectations) {
  // Setup resources
  pkg.config.resources = jsList(new ArrayList<>(Arrays.asList(obj("reference", obj("reference", profile), "name", "A Very Special Profile"), obj("reference", obj("reference", extension), "name", "A Very Special Extension"), obj("reference", obj("reference", codeSystem), "name", "A Very Special CodeSystem"), obj("reference", obj("reference", example), "name", "A Very Special Example"))));
  // Export and check
  var fixtures = path.join(__dirname, "fixtures", "simple-ig");
  IGExporter exporter = new IGExporter(pkg, defs, fixtures);
  exporter.export(tempOut);
  var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
  assertTrue(Js.truthy(fs.existsSync(igPath)));
  var igJSON = fs.readJSONSync(igPath);
  if (!skipExpectations) {
    assertEquals(0, loggerSpy.getAllMessages("warn").size());
    assertEquals(0, loggerSpy.getAllMessages("error").size());
    assertJsonEqual("""
        [
          {
            "reference": {
              "reference": "StructureDefinition/sample-patient"
            },
            "name": "A Very Special Profile",
            "description": "Demographics and other administrative information about an individual or animal receiving care or other health-related services.",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "StructureDefinition/sample-value-extension"
            },
            "name": "A Very Special Extension",
            "description": "Base StructureDefinition for Extension Type: Optional Extension Element - found in all resources.",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "CodeSystem/sample-code-system"
            },
            "name": "A Very Special CodeSystem",
            "description": "A code system description",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "Patient/patient-example"
            },
            "name": "A Very Special Example",
            "exampleBoolean": true
          }
        ]
        """, at(igJSON, "definition.resource"));
  }
  return igJSON;
    }
    Object doExampleCanonicalTest(String exampleCanonical) { return doExampleCanonicalTest(exampleCanonical, false); }
    Object doExampleCanonicalTest(String exampleCanonical, boolean skipExpectations) {
  // Remove extension and code system since we don't need them
  pkg.extensions.clear();
  pkg.codeSystems.clear();
  // Setup resources
  pkg.config.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/sample-patient"
          }
        }
        """), obj("reference", JsAssert.json("""
        {
          "reference": "Patient/patient-example"
        }
        """), "exampleCanonical", exampleCanonical))));
  // Export and check
  var fixtures = path.join(__dirname, "fixtures", "simple-ig");
  IGExporter exporter = new IGExporter(pkg, defs, fixtures);
  exporter.export(tempOut);
  var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
  assertTrue(Js.truthy(fs.existsSync(igPath)));
  var igJSON = fs.readJSONSync(igPath);
  if (!skipExpectations) {
    assertEquals(0, loggerSpy.getAllMessages("warn").size());
    assertEquals(0, loggerSpy.getAllMessages("error").size());
    assertJsonEqual("""
        [
          {
            "reference": {
              "reference": "StructureDefinition/sample-patient"
            },
            "name": "SamplePatient",
            "description": "Demographics and other administrative information about an individual or animal receiving care or other health-related services.",
            "exampleBoolean": false
          },
          {
            "reference": {
              "reference": "Patient/patient-example"
            },
            "name": "patient-example",
            "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient"
          }
        ]
        """, at(igJSON, "definition.resource"));
  }
  return igJSON;
    }
    Object doGroupTest(String profile, String extension, String codeSystem, String example) { return doGroupTest(profile, extension, codeSystem, example, false); }
    Object doGroupTest(String profile, String extension, String codeSystem, String example, boolean skipExpectations) {
  pkg.config.groups = groups(groups(arr(obj("id", "MyPatientGroup", "name", "My Patient Group", "description", "Group of patient things.", "resources", new ArrayList<>(Arrays.asList(profile, example))), obj("id", "MyOtherGroup", "name", "My Other Group", "description", "Group of other things.", "resources", new ArrayList<>(Arrays.asList(extension, codeSystem))))));
  var fixtures = path.join(__dirname, "fixtures", "simple-ig");
  IGExporter exporter = new IGExporter(pkg, defs, fixtures);
  exporter.export(tempOut);
  var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
  assertTrue(Js.truthy(fs.existsSync(igPath)));
  var igJSON = fs.readJSONSync(igPath);
  if (!skipExpectations) {
    assertContainsEqual(JsAssert.json("""
        {
          "id": "MyPatientGroup",
          "name": "My Patient Group",
          "description": "Group of patient things."
        }
        """), at(igJSON, "definition.grouping"));
    assertContainsEqual(JsAssert.json("""
        {
          "id": "MyOtherGroup",
          "name": "My Other Group",
          "description": "Group of other things."
        }
        """), at(igJSON, "definition.grouping"));
    assertEquals("MyPatientGroup", at(find(at(igJSON, "definition.resource"), (r) -> Objects.equals(at(r, "reference.reference"), "StructureDefinition/sample-patient")), "groupingId"));
    assertEquals("MyPatientGroup", at(find(at(igJSON, "definition.resource"), (r) -> Objects.equals(at(r, "reference.reference"), "Patient/patient-example")), "groupingId"));
    assertEquals("MyOtherGroup", at(find(at(igJSON, "definition.resource"), (r) -> Objects.equals(at(r, "reference.reference"), "StructureDefinition/sample-value-extension")), "groupingId"));
    assertEquals("MyOtherGroup", at(find(at(igJSON, "definition.resource"), (r) -> Objects.equals(at(r, "reference.reference"), "CodeSystem/sample-code-system")), "groupingId"));
  }
  return igJSON;
    }
    Object doGlobalProfileTest(String profile) { return doGlobalProfileTest(profile, false); }
    Object doGlobalProfileTest(String profile, boolean skipExpectations) {
  // Remove extension, code system, and example since we don't need them
  pkg.extensions.clear();
  pkg.codeSystems.clear();
  pkg.instances.clear();
  // Setup global profile
  pkg.config.global = jsList(new ArrayList<>(Arrays.asList(obj("type", "Patient", "profile", profile))));
  // Export and check
  var fixtures = path.join(__dirname, "fixtures", "simple-ig");
  IGExporter exporter = new IGExporter(pkg, defs, fixtures);
  exporter.export(tempOut);
  var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-sushi-test.json");
  assertTrue(Js.truthy(fs.existsSync(igPath)));
  var igJSON = fs.readJSONSync(igPath);
  if (!skipExpectations) {
    assertEquals(0, loggerSpy.getAllMessages("warn").size());
    assertEquals(0, loggerSpy.getAllMessages("error").size());
    assertJsonEqual("""
        [
          {
            "type": "Patient",
            "profile": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient"
          }
        ]
        """, at(igJSON, "global"));
  }
  return igJSON;
    }

    @BeforeAll
    void beforeAll() {
      defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
    }

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
      var fixtures = path.join(__dirname, "fixtures", "simple-ig");
      tempOut = temp.mkdirSync("sushi-test");
      Configuration config = ConfigurationJs.fromJs(obj("filePath", path.join(fixtures, "sushi-config.yml"), "id", "sushi-test", "canonical", "http://hl7.org/fhir/sushi-test", "name", "FSHTestIG", "status", "active", "fhirVersion", new ArrayList<>(Arrays.asList("4.0.1")), "parameters", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "code": "copyrightyear",
          "value": "2020+"
        }
        """), JsAssert.json("""
        {
          "code": "releaselabel",
          "value": "CI Build"
        }
        """))), "menu", new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "name": "Home",
          "url": "index.html"
        }
        """), JsAssert.json("""
        {
          "name": "Artifacts",
          "url": "artifacts.html"
        }
        """)))));
      pkg = new Package(config);
      // Profile: StructureDefinition/sample-patient
      pkg.profiles.add(StructureDefinition.fromJSON(jm(fs.readJSONSync(path.join(fixtures, "profiles", "StructureDefinition-sample-patient.json")))));
      // Extension: StructureDefinition/sample-value-extension
      pkg.extensions.add(StructureDefinition.fromJSON(jm(fs.readJSONSync(path.join(fixtures, "extensions", "StructureDefinition-sample-value-extension.json")))));
      // CodeSystem: StructureDefinition/sample-code-system
      CodeSystem codeSystemDef = new CodeSystem();
      setProp(codeSystemDef, "id", "sample-code-system");
      setProp(codeSystemDef, "name", "SampleCodeSystem");
      setProp(codeSystemDef, "url", "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-code-system");
      setProp(codeSystemDef, "description", "A code system description");
      pkg.codeSystems.add(codeSystemDef);
      // Example: Patient/patient-example
      var instanceDef = InstanceDefinition.fromJSON(jm(fs.readJSONSync(path.join(fixtures, "examples", "Patient-example.json"))));
      setProp(at(instanceDef, "_instanceMeta"), "usage", "Example");
      pkg.instances.add(instanceDef);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should properly convert resource ids to relative URLs")
    void shouldProperlyConvertResourceIdsToRelativeURLs() {
      doResourceTest("sample-patient", "sample-value-extension", "sample-code-system", "patient-example");
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should properly convert resource names to relative URLs")
    void shouldProperlyConvertResourceNamesToRelativeURLs() {
      doResourceTest("SamplePatient", "SampleValueExtension", "SampleCodeSystem", "Patient/patient-example"); // patients don't have a formal name
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should properly retain relative resource URLs")
    void shouldProperlyRetainRelativeResourceURLs() {
      doResourceTest("StructureDefinition/sample-patient", "StructureDefinition/sample-value-extension", "CodeSystem/sample-code-system", "Patient/patient-example");
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should log a warning and use reference as-is when a name/id cannot be resolved")
    void shouldLogAWarningAndUseReferenceAsIsWhenANameIdCannotBeResolved() {
      var igJSON = doResourceTest("invalid-id", "sample-value-extension", "sample-code-system", "patient-example", true);
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertContainsText("Cannot determine relative URL for \"invalid-id\" referenced in sushi-config.yaml.", loggerSpy.getLastMessage("warn"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(5, len(at(igJSON, "definition.resource")));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "invalid-id"
          },
          "name": "A Very Special Profile"
        }
        """), at(igJSON, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should properly convert exampleCanonical id to canonical")
    void shouldProperlyConvertExampleCanonicalIdToCanonical() {
      doExampleCanonicalTest("sample-patient");
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should properly convert exampleCanonical name to canonical")
    void shouldProperlyConvertExampleCanonicalNameToCanonical() {
      doExampleCanonicalTest("SamplePatient");
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should properly retain canonical exampleCanonical")
    void shouldProperlyRetainCanonicalExampleCanonical() {
      doExampleCanonicalTest("http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient");
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should properly convert exampleCanonical for external profiles too")
    void shouldProperlyConvertExampleCanonicalForExternalProfilesToo() {
      var igJSON = doExampleCanonicalTest("us-core-patient", true);
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example"
          },
          "name": "patient-example",
          "exampleCanonical": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient"
        }
        """), at(igJSON, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should log a warning and use reference as-is when a name/id cannot be resolved")
    void shouldLogAWarningAndUseReferenceAsIsWhenANameIdCannotBeResolved2() {
      var igJSON = doExampleCanonicalTest("invalid-example-canonical-id", true);
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertContainsText("Cannot determine canonical for \"invalid-example-canonical-id\" referenced in sushi-config.yaml.", loggerSpy.getLastMessage("warn"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertEquals(2, len(at(igJSON, "definition.resource")));
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example"
          },
          "name": "patient-example",
          "exampleCanonical": "invalid-example-canonical-id"
        }
        """), at(igJSON, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should properly convert resource ids to relative URLs")
    void shouldProperlyConvertResourceIdsToRelativeURLs2() {
      doGroupTest("sample-patient", "sample-value-extension", "sample-code-system", "patient-example");
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should properly convert resource names to relative URLs")
    void shouldProperlyConvertResourceNamesToRelativeURLs2() {
      doGroupTest("SamplePatient", "SampleValueExtension", "SampleCodeSystem", "Patient/patient-example"); // patients don't have a formal name
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should properly retain relative resource URLs")
    void shouldProperlyRetainRelativeResourceURLs2() {
      doGroupTest("StructureDefinition/sample-patient", "StructureDefinition/sample-value-extension", "CodeSystem/sample-code-system", "Patient/patient-example");
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should log a warning and an error when a name/id cannot be resolved")
    void shouldLogAWarningAndAnErrorWhenANameIdCannotBeResolved() {
      var igJSON = doGroupTest("invalid-id", "sample-value-extension", "sample-code-system", "patient-example", true);
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertContainsText("Cannot determine relative URL for \"invalid-id\" referenced in sushi-config.yaml.", loggerSpy.getLastMessage("warn"));
      assertEquals(1, loggerSpy.getAllMessages("error").size());
      assertContainsText("Group MyPatientGroup configured with nonexistent resource invalid-id", loggerSpy.getLastMessage("error"));
      assertEquals(2, len(at(igJSON, "definition.grouping")));
      assertEquals(4, len(at(igJSON, "definition.resource")));
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should properly convert global profile id to canonical")
    void shouldProperlyConvertGlobalProfileIdToCanonical() {
      doGlobalProfileTest("sample-patient");
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should properly convert global profile name to canonical")
    void shouldProperlyConvertGlobalProfileNameToCanonical() {
      doGlobalProfileTest("SamplePatient");
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should properly retain global profile canonical")
    void shouldProperlyRetainGlobalProfileCanonical() {
      doGlobalProfileTest("http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient");
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should properly convert global profile for external profiles too")
    void shouldProperlyConvertGlobalProfileForExternalProfilesToo() {
      var igJSON = doGlobalProfileTest("us-core-patient", true);
      assertEquals(0, loggerSpy.getAllMessages("warn").size());
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertJsonEqual("""
        [
          {
            "type": "Patient",
            "profile": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-patient"
          }
        ]
        """, at(igJSON, "global"));
    }

    @Test
    @DisplayName("IGExporter #ref-by-id-or-name should log a warning and use reference as-is when a name/id cannot be resolved")
    void shouldLogAWarningAndUseReferenceAsIsWhenANameIdCannotBeResolved3() {
      var igJSON = doGlobalProfileTest("invalid-global-canonical-id", true);
      assertEquals(1, loggerSpy.getAllMessages("warn").size());
      assertContainsText("Cannot determine canonical for \"invalid-global-canonical-id\" referenced in sushi-config.yaml.", loggerSpy.getLastMessage("warn"));
      assertEquals(0, loggerSpy.getAllMessages("error").size());
      assertJsonEqual("""
        [
          {
            "type": "Patient",
            "profile": "invalid-global-canonical-id"
          }
        ]
        """, at(igJSON, "global"));
    }

  }
  @DisplayName("#pages-folder-ig")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class PagesFolderIg {
    Package pkg;
    IGExporter exporter;
    String tempOut;
    String fixtures;
    Configuration config;
    FHIRDefinitions defs;

    @BeforeAll
    void beforeAll() {
      fixtures = path.join(__dirname, "fixtures", "pages-folder-ig");
      defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
    }

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      config = MinimalConfig.minimalConfig();
      pkg = new Package(config);
      exporter = new IGExporter(pkg, defs, fixtures);
    }

    @AfterEach
    void afterEach() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #pages-folder-ig should use all available page content when pages are not configured")
    void shouldUseAllAvailablePageContentWhenPagesAreNotConfigured() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      // Pages are added to IG content but nothing is copied
      assertJsonEqual("""
        [
          {
            "nameUrl": "index.html",
            "title": "Home",
            "generation": "markdown"
          },
          {
            "nameUrl": "extra.html",
            "title": "Extra",
            "generation": "html"
          },
          {
            "nameUrl": "other-page.html",
            "title": "Other Page",
            "generation": "markdown"
          }
        ]
        """, at(igContent, "definition.page.page"));
    }

    @Test
    @DisplayName("IGExporter #pages-folder-ig should include only configured pages when provided")
    void shouldIncludeOnlyConfiguredPagesWhenProvided() {
      config.pages = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "nameUrl": "index.md",
          "title": "Home Page",
          "generation": "markdown"
        }
        """), JsAssert.json("""
        {
          "nameUrl": "extra.xml",
          "title": "Extra!",
          "generation": "html"
        }
        """))));
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      // Only the index.md and extra.xml pages were configured, so they are included.
      // The other-page.md and other-page-notes.md are left alone.
      assertJsonEqual("""
        [
          {
            "nameUrl": "index.html",
            "title": "Home Page",
            "generation": "markdown"
          },
          {
            "nameUrl": "extra.html",
            "title": "Extra!",
            "generation": "html"
          }
        ]
        """, at(igContent, "definition.page.page"));
    }

  }
  @DisplayName("#invalid-pages-folder-ig")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class InvalidPagesFolderIg {
    Package pkg;
    IGExporter exporter;
    String tempOut;
    String fixtures;
    Configuration config;
    FHIRDefinitions defs;

    @BeforeAll
    void beforeAll() {
      fixtures = path.join(__dirname, "fixtures", "invalid-pages-folder-ig");
      defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
    }

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      config = MinimalConfig.minimalConfig();
      pkg = new Package(config);
      exporter = new IGExporter(pkg, defs, fixtures);
    }

    @AfterEach
    void afterEach() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #invalid-pages-folder-ig should log a warning for invalid page types")
    void shouldLogAWarningForInvalidPageTypes() {
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "nameUrl": "index.html",
            "title": "Home",
            "generation": "markdown"
          }
        ]
        """, at(igContent, "definition.page.page"));
      // Check for log messages indicating invalid input
      assertMatches("Files not in the supported file types \\(\\.md and \\.xml\\) were detected\\..*File: .*[\\/\\\\]invalid-pages-folder-ig[\\/\\\\]input[\\/\\\\]pagecontent", "s", loggerSpy.getFirstMessage("warn"));
    }

  }
  @DisplayName("#sorted-pages-ig")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class SortedPagesIg {
    String tempOut;
    String fixtures;
    FHIRDefinitions defs;
    Package pkg;

    @BeforeAll
    void beforeAll() {
      tempOut = temp.mkdirSync("sushi-test");
      fixtures = path.join(__dirname, "fixtures", "sorted-pages-ig");
      defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
      pkg = new Package(MinimalConfig.minimalConfig());
    }

    @BeforeEach
    void beforeEach() {
      loggerSpy.reset();
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @DisplayName("IG Publisher mode")
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class IGPublisherMode {

      @BeforeAll
      void beforeAll() {
        IGExporter exporter = new IGExporter(pkg, defs, fixtures);
        // No need to regenerate the IG on every test -- generate it once and inspect what you
        // need to in the tests
        exporter.export(tempOut);
      }

      @AfterAll
      void afterAll() {
        temp.cleanupSync();
      }

      @Test
      @DisplayName("IGExporter #sorted-pages-ig IG Publisher mode should configure user-provided pages in numeric then alphabetic order")
      void shouldConfigureUserProvidedPagesInNumericThenAlphabeticOrder() {
        var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
        assertTrue(Js.truthy(fs.existsSync(igPath)));
        var igContent = fs.readJSONSync(igPath);
        assertEquals(9, len(at(igContent, "definition.page.page")));
        assertJsonEqual("""
        [
          {
            "nameUrl": "index.html",
            "title": "Home",
            "generation": "html"
          },
          {
            "nameUrl": "1_oranges.html",
            "title": "Oranges",
            "generation": "markdown"
          },
          {
            "nameUrl": "2_apples.html",
            "title": "Apples",
            "generation": "markdown"
          },
          {
            "nameUrl": "3_bananas.html",
            "title": "Bananas",
            "generation": "markdown"
          },
          {
            "nameUrl": "4_pears.html",
            "title": "Pears",
            "generation": "markdown"
          },
          {
            "nameUrl": "7_left.html",
            "title": "Left",
            "generation": "markdown"
          },
          {
            "nameUrl": "7_right.html",
            "title": "Right",
            "generation": "markdown"
          },
          {
            "nameUrl": "100_big.html",
            "title": "Big",
            "generation": "markdown"
          },
          {
            "nameUrl": "pasta.html",
            "title": "Pasta",
            "generation": "markdown"
          }
        ]
        """, at(igContent, "definition.page.page"));
      }

      @Test
      @DisplayName("IGExporter #sorted-pages-ig IG Publisher mode should not copy files")
      void shouldNotCopyFiles() {
        var pageContentPath = path.join(tempOut, "input", "pagecontent");
        assertFalse(Js.truthy(fs.existsSync(pageContentPath)));
        pageContentPath = path.join(tempOut, "fsh-generated", "input", "pagecontent");
        assertFalse(Js.truthy(fs.existsSync(pageContentPath)));
      }

    }
  }
  @DisplayName("#name-collision-ig")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class NameCollisionIg {
    String tempOut;

    @BeforeAll
    void beforeAll() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      var fixtures = path.join(__dirname, "fixtures", "name-collision-ig");
      var defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
      Package pkg = new Package(MinimalConfig.minimalConfig());
      IGExporter exporter = new IGExporter(pkg, defs, fixtures);
      // No need to regenerate the IG on every test -- generate it once and inspect what you
      // need to in the tests
      exporter.export(tempOut);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #name-collision-ig should not remove numeric prefixes from page names when doing so would cause name collisions")
    void shouldNotRemoveNumericPrefixesFromPageNamesWhenDoingSoWouldCauseNameCollisions() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertEquals(5, len(at(igContent, "definition.page.page")));
      assertJsonEqual("""
        [
          {
            "nameUrl": "index.html",
            "title": "Home",
            "generation": "markdown"
          },
          {
            "nameUrl": "1_rocks.html",
            "title": "Rocks",
            "generation": "markdown"
          },
          {
            "nameUrl": "2_rocks.html",
            "title": "Rocks",
            "generation": "markdown"
          },
          {
            "nameUrl": "3_index.html",
            "title": "Index",
            "generation": "markdown"
          },
          {
            "nameUrl": "4_2_rocks.html",
            "title": "2 Rocks",
            "generation": "markdown"
          }
        ]
        """, at(igContent, "definition.page.page"));
      assertMatches("Duplicate file index.xml will be ignored. Please rename to avoid collisions", "", loggerSpy.getLastMessage("error"));
    }

  }
  @DisplayName("#devious-id-ig")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class DeviousIdIg {
    String tempOut;

    @BeforeAll
    void beforeAll() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      var fixtures = path.join(__dirname, "fixtures", "simple-ig");
      var defs = getTestFHIRDefinitions(true, testDefsPath("r4-definitions"));
      Configuration deviousConfig = MinimalConfig.minimalConfig();
      deviousConfig.id = "/../../../arenticlever";
      Package pkg = new Package(deviousConfig);
      IGExporter exporter = new IGExporter(pkg, defs, fixtures);
      exporter.export(tempOut);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #devious-id-ig should not allow devious characters in the IG file name  ")
    void shouldNotAllowDeviousCharactersInTheIGFileName() {
      var deviousPath = path.join(tempOut, "arenticlever.json");
      assertEquals(false, fs.existsSync(deviousPath));
      var angelicPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide--..-..-..-arenticlever.json");
      assertTrue(Js.truthy(fs.existsSync(angelicPath)));
    }

  }
  @DisplayName("#r5-ig-format")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class R5IgFormat {
    String tempOut;

    @BeforeAll
    void beforeAll() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      var fixtures = path.join(__dirname, "fixtures", "simple-ig");
      // r5-definitions contains the guide-parameter-code CodeSystem, which was originally included in 5.0.0-ballot
      var defs = getTestFHIRDefinitions(false, testDefsPath("r5-definitions"));
      Configuration r5config = MinimalConfig.minimalConfig();
      r5config.fhirVersion = new ArrayList<>(Arrays.asList("5.0.0-ballot"));
      r5config.copyrightLabel = "Shorty Fsh 2022+";
      r5config.dependencies = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.ocean",
          "uri": "http://example.org/ocean",
          "id": "ocean",
          "version": "1.0.0",
          "reason": "The ocean is essential because it has fsh"
        }
        """))));
      r5config.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example-two"
          },
          "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient"
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example-three"
          },
          "profile": [
            "http://example.org/StructureDefiniton/first",
            "http://example.org/StructureDefiniton/second"
          ]
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example-four"
          },
          "isExample": false
        }
        """))));
      r5config.pages = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "nameUrl": "index.md",
          "title": "Home",
          "generation": "markdown",
          "sourceMarkdown": "source markdown for index"
        }
        """), JsAssert.json("""
        {
          "nameUrl": "other-page.md",
          "title": "Some Other Page",
          "generation": "markdown",
          "sourceUrl": "http://example.org",
          "page": [
            {
              "nameUrl": "nested-page.md",
              "sourceString": "source string for nested page"
            },
            {
              "nameUrl": "second-nested-page-url.md",
              "name": "second-nested-page-name.html"
            }
          ]
        }
        """))));
      r5config.parameters = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "code": "copyrightyear",
          "value": "2020+"
        }
        """), JsAssert.json("""
        {
          "code": "generate-xml",
          "value": "true"
        }
        """), JsAssert.json("""
        {
          "code": "http://example.org/parameters#special-code",
          "value": "sparkles"
        }
        """), JsAssert.json("""
        {
          "code": "http://example.org/CodeSystem/sample-code-system#url-code",
          "value": "true"
        }
        """), JsAssert.json("""
        {
          "code": "SampleCodeSystem#name-code",
          "value": "true"
        }
        """), JsAssert.json("""
        {
          "code": "sample-code-system#id-code",
          "value": "true"
        }
        """))));
      Package pkg = new Package(r5config);
      // Profile: StructureDefinition/sample-patient
      pkg.profiles.add(StructureDefinition.fromJSON(jm(fs.readJSONSync(path.join(fixtures, "profiles", "StructureDefinition-sample-patient.json")))));
      // Example: Patient/patient-example
      var instanceDef = InstanceDefinition.fromJSON(jm(fs.readJSONSync(path.join(fixtures, "examples", "Patient-example.json"))));
      setProp(at(instanceDef, "_instanceMeta"), "usage", "Example");
      pkg.instances.add(instanceDef);
      // Example: Patient/patient-example-two
      var instanceDef2 = InstanceDefinition.fromJSON(jm(fs.readJSONSync(path.join(fixtures, "examples", "Patient-example-two.json"))));
      setProp(at(instanceDef2, "_instanceMeta"), "usage", "Example");
      pkg.instances.add(instanceDef2);
      // Example: Patient/patient-example-three
      var instanceDef3 = InstanceDefinition.fromJSON(jm(fs.readJSONSync(path.join(fixtures, "examples", "Patient-example-three.json"))));
      setProp(at(instanceDef3, "_instanceMeta"), "usage", "Example");
      pkg.instances.add(instanceDef3);
      // Example: Patient/patient-example-four
      var instanceDef4 = InstanceDefinition.fromJSON(jm(fs.readJSONSync(path.join(fixtures, "examples", "Patient-example-three.json"))));
      setProp(instanceDef4, "id", "patient-example-four");
      setProp(at(instanceDef4, "_instanceMeta"), "name", "patient-example-four");
      setProp(at(instanceDef4, "_instanceMeta"), "usage", "Example");
      pkg.instances.add(instanceDef4);
      // CodeSystem: CodeSystem/sample-code-system
      CodeSystem codeSystemDef = new CodeSystem();
      setProp(codeSystemDef, "id", "sample-code-system");
      setProp(codeSystemDef, "name", "SampleCodeSystem");
      setProp(codeSystemDef, "url", "http://example.org/CodeSystem/sample-code-system");
      pkg.codeSystems.add(codeSystemDef);
      IGExporter exporter = new IGExporter(pkg, defs, fixtures);
      // No need to regenerate the IG on every test -- generate it once and inspect what you
      // need to in the tests
      exporter.export(tempOut);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #r5-ig-format should replace a definition.resource.exampleBoolean set to true with isExample")
    void shouldReplaceADefinitionResourceExampleBooleanSetToTrueWithIsExample() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example"
          },
          "name": "patient-example",
          "isExample": true
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #r5-ig-format should replace an definition.resource.exampleBoolean set to false with isExample")
    void shouldReplaceAnDefinitionResourceExampleBooleanSetToFalseWithIsExample() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "StructureDefinition/sample-patient"
          },
          "name": "SamplePatient",
          "description": "Demographics and other administrative information about an individual or animal receiving care or other health-related services.",
          "isExample": false
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #r5-ig-format should replace an definition.resource.exampleCanonical with isExample and profile")
    void shouldReplaceAnDefinitionResourceExampleCanonicalWithIsExampleAndProfile() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example-two"
          },
          "name": "patient-example-two",
          "isExample": true,
          "profile": [
            "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient"
          ]
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #r5-ig-format should use the definition.resource.isExample boolean in configuration if provided")
    void shouldUseTheDefinitionResourceIsExampleBooleanInConfigurationIfProvided() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example-four"
          },
          "name": "patient-example-four",
          "isExample": false
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #r5-ig-format should use the definition.resource.profile array in configuration if provided")
    void shouldUseTheDefinitionResourceProfileArrayInConfigurationIfProvided() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example-three"
          },
          "name": "patient-example-three",
          "isExample": true,
          "profile": [
            "http://example.org/StructureDefiniton/first",
            "http://example.org/StructureDefiniton/second"
          ]
        }
        """), at(igContent, "definition.resource"));
    }

    @Test
    @DisplayName("IGExporter #r5-ig-format should replace definition.page.nameUrl and any nested pages nameUrls with name and support source[x]")
    void shouldReplaceDefinitionPageNameUrlAndAnyNestedPagesNameUrlsWithNameAndSupportSou() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        {
          "title": "Table of Contents",
          "generation": "html",
          "name": "toc.html",
          "sourceUrl": "toc.html",
          "page": [
            {
              "name": "index.html",
              "title": "Home",
              "generation": "markdown",
              "sourceMarkdown": "source markdown for index"
            },
            {
              "name": "other-page.html",
              "title": "Some Other Page",
              "generation": "markdown",
              "sourceUrl": "http://example.org",
              "page": [
                {
                  "name": "nested-page.html",
                  "title": "Nested Page",
                  "generation": "markdown",
                  "sourceString": "source string for nested page"
                },
                {
                  "name": "second-nested-page-name.html",
                  "title": "Second Nested Page Name",
                  "generation": "markdown",
                  "sourceUrl": "second-nested-page-url.html"
                }
              ]
            }
          ]
        }
        """, at(igContent, "definition.page"));
    }

    @Test
    @DisplayName("IGExporter #r5-ig-format should replace definition.parameter code with a Coding and include the system if the value is in the bound VS")
    void shouldReplaceDefinitionParameterCodeWithACodingAndIncludeTheSystemIfTheValueIsIn() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "code": {
            "code": "generate-xml",
            "system": "http://hl7.org/fhir/guide-parameter-code"
          },
          "value": "true"
        }
        """), at(igContent, "definition.parameter"));
    }

    @Test
    @DisplayName("IGExporter #r5-ig-format should replace definition.parameter code with a Coding and set the default system if the value is not in the bound VS")
    void shouldReplaceDefinitionParameterCodeWithACodingAndSetTheDefaultSystemIfTheValueI() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "code": {
            "code": "copyrightyear",
            "system": "http://hl7.org/fhir/tools/CodeSystem/ig-parameters"
          },
          "value": "2020+"
        }
        """), at(igContent, "definition.parameter"));
    }

    @Test
    @DisplayName("IGExporter #r5-ig-format should set the definition.parameter system and code if provided")
    void shouldSetTheDefinitionParameterSystemAndCodeIfProvided() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "code": {
            "code": "special-code",
            "system": "http://example.org/parameters"
          },
          "value": "sparkles"
        }
        """), at(igContent, "definition.parameter"));
    }

    @Test
    @DisplayName("IGExporter #r5-ig-format should support referencing system by name, id, or full url when setting a definition.parameter code with a system")
    void shouldSupportReferencingSystemByNameIdOrFullUrlWhenSettingADefinitionParameterCo() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "code": {
            "code": "url-code",
            "system": "http://example.org/CodeSystem/sample-code-system"
          },
          "value": "true"
        }
        """), at(igContent, "definition.parameter"));
      assertContainsEqual(JsAssert.json("""
        {
          "code": {
            "code": "name-code",
            "system": "http://example.org/CodeSystem/sample-code-system"
          },
          "value": "true"
        }
        """), at(igContent, "definition.parameter"));
      assertContainsEqual(JsAssert.json("""
        {
          "code": {
            "code": "id-code",
            "system": "http://example.org/CodeSystem/sample-code-system"
          },
          "value": "true"
        }
        """), at(igContent, "definition.parameter"));
    }

    @Test
    @DisplayName("IGExporter #r5-ig-format should set copyrightLabel when provided in configuration")
    void shouldSetCopyrightLabelWhenProvidedInConfiguration() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("\"Shorty Fsh 2022+\"", at(igContent, "copyrightLabel"));
    }

    @Test
    @DisplayName("IGExporter #r5-ig-format should set versionAlgorithmString when provided in configuration")
    void shouldSetVersionAlgorithmStringWhenProvidedInConfiguration() {
      // Export IG in this test so can test all variations of versionAlgorithm[x]
      Configuration configWithVersionAlgorithm = MinimalConfig.minimalConfig();
      configWithVersionAlgorithm.fhirVersion = new ArrayList<>(Arrays.asList("5.0.0-ballot"));
      configWithVersionAlgorithm.versionAlgorithmString = "date";
      var fixtures = path.join(__dirname, "fixtures", "simple-ig");
      var defs = getTestFHIRDefinitions();
      Package pkg = new Package(configWithVersionAlgorithm);
      IGExporter exporter = new IGExporter(pkg, defs, fixtures);
      var tempOut = temp.mkdirSync("sushi-test-version-alg");
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("\"date\"", at(igContent, "versionAlgorithmString"));
    }

    @Test
    @DisplayName("IGExporter #r5-ig-format should set versionAlgorithmCoding when provided as FSH Code in configuration")
    void shouldSetVersionAlgorithmCodingWhenProvidedAsFSHCodeInConfiguration() {
      // Export IG in this test so can test all variations of versionAlgorithm[x]
      Configuration configWithVersionAlgorithm = MinimalConfig.minimalConfig();
      configWithVersionAlgorithm.fhirVersion = new ArrayList<>(Arrays.asList("5.0.0-ballot"));
      configWithVersionAlgorithm.versionAlgorithmCoding = (JsObject) JsAssert.json("""
        {
          "system": "http://example.org",
          "code": "semver"
        }
        """);
      var fixtures = path.join(__dirname, "fixtures", "simple-ig");
      var defs = getTestFHIRDefinitions();
      Package pkg = new Package(configWithVersionAlgorithm);
      IGExporter exporter = new IGExporter(pkg, defs, fixtures);
      var tempOut = temp.mkdirSync("sushi-test-version-alg");
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        {
          "system": "http://example.org",
          "code": "semver"
        }
        """, at(igContent, "versionAlgorithmCoding"));
    }

    @Test
    @DisplayName("IGExporter #r5-ig-format should support dependsOn.reason when provided in configuration")
    void shouldSupportDependsOnReasonWhenProvidedInConfiguration() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "packageId": "hl7.fhir.us.ocean",
            "uri": "http://example.org/ocean",
            "id": "ocean",
            "version": "1.0.0",
            "reason": "The ocean is essential because it has fsh"
          }
        ]
        """, at(igContent, "dependsOn"));
    }

  }
  @DisplayName("#r5-properties-on-r4-igs")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class R5PropertiesOnR4Igs {
    String tempOut;

    @BeforeAll
    void beforeAll() {
      loggerSpy.reset();
      tempOut = temp.mkdirSync("sushi-test");
      var fixtures = path.join(__dirname, "fixtures", "simple-ig");
      // r5-definitions contains the guide-parameter-code CodeSystem, which was originally included in 5.0.0-ballot
      var defs = getTestFHIRDefinitions(false, testDefsPath("r5-definitions"));
      Configuration r4WithR5propsConfig = MinimalConfig.minimalConfig();
      r4WithR5propsConfig.copyrightLabel = "Shorty Fsh 2022+";
      r4WithR5propsConfig.dependencies = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.ocean",
          "uri": "http://example.org/ocean",
          "id": "ocean",
          "version": "1.0.0",
          "reason": "The ocean is essential because it has fsh"
        }
        """))));
      r4WithR5propsConfig.resources = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example"
          },
          "profile": [
            "http://example.org/StructureDefinition/solo"
          ]
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example-two"
          },
          "profile": [
            "http://example.org/StructureDefiniton/first",
            "http://example.org/StructureDefiniton/second"
          ]
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example-three"
          },
          "isExample": false
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example-four"
          },
          "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient",
          "profile": [
            "http://example.org/StructureDefinition/solo"
          ]
        }
        """), JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example-five"
          },
          "exampleCanonical": "http://example.org/StructureDefinition/solo",
          "profile": [
            "http://example.org/StructureDefinition/solo"
          ]
        }
        """))));
      r4WithR5propsConfig.parameters = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "code": "generate-xml",
          "value": "true"
        }
        """), JsAssert.json("""
        {
          "code": "http://example.org/parameters#special-code",
          "value": "sparkles"
        }
        """))));
      Package pkg = new Package(r4WithR5propsConfig);
      // Example: Patient/patient-example
      var instanceDef = InstanceDefinition.fromJSON(jm(fs.readJSONSync(path.join(fixtures, "examples", "Patient-example.json"))));
      setProp(at(instanceDef, "_instanceMeta"), "usage", "Example");
      pkg.instances.add(instanceDef);
      // Example: Patient/patient-example-two
      var instanceDef2 = InstanceDefinition.fromJSON(jm(fs.readJSONSync(path.join(fixtures, "examples", "Patient-example-two.json"))));
      setProp(at(instanceDef2, "_instanceMeta"), "usage", "Example");
      pkg.instances.add(instanceDef2);
      // Example: Patient/patient-example-three
      var instanceDef3 = InstanceDefinition.fromJSON(jm(fs.readJSONSync(path.join(fixtures, "examples", "Patient-example-three.json"))));
      setProp(at(instanceDef3, "_instanceMeta"), "usage", "Example");
      pkg.instances.add(instanceDef3);
      // Example: Patient/patient-example-four
      var instanceDef4 = InstanceDefinition.fromJSON(jm(fs.readJSONSync(path.join(fixtures, "examples", "Patient-example-three.json"))));
      setProp(instanceDef4, "id", "patient-example-four");
      setProp(at(instanceDef4, "_instanceMeta"), "name", "patient-example-four");
      setProp(at(instanceDef4, "_instanceMeta"), "usage", "Example");
      pkg.instances.add(instanceDef4);
      // Example: Patient/patient-example-five
      var instanceDef5 = InstanceDefinition.fromJSON(jm(fs.readJSONSync(path.join(fixtures, "examples", "Patient-example-three.json"))));
      setProp(instanceDef5, "id", "patient-example-five");
      setProp(at(instanceDef5, "_instanceMeta"), "name", "patient-example-five");
      setProp(at(instanceDef5, "_instanceMeta"), "usage", "Example");
      pkg.instances.add(instanceDef5);
      IGExporter exporter = new IGExporter(pkg, defs, fixtures);
      // No need to regenerate the IG on every test -- generate it once and inspect what you
      // need to in the tests
      exporter.export(tempOut);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @DisplayName("definition.resource.profile")
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class DefinitionResourceProfile {

      @DisplayName("exampleCanonical is not set")
      @Nested
      @TestInstance(TestInstance.Lifecycle.PER_CLASS)
      class ExampleCanonicalIsNotSet {

        @Test
        @DisplayName("IGExporter #r5-properties-on-r4-igs definition.resource.profile exampleCanonical is not set should add configured profile array to exampleCanonical and no extension if length ===  1")
        void shouldAddConfiguredProfileArrayToExampleCanonicalAndNoExtensionIfLength1() {
          var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
          assertTrue(Js.truthy(fs.existsSync(igPath)));
          var igContent = fs.readJSONSync(igPath);
          assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example"
          },
          "name": "patient-example",
          "exampleCanonical": "http://example.org/StructureDefinition/solo"
        }
        """), at(igContent, "definition.resource"));
        }

        @Test
        @DisplayName("IGExporter #r5-properties-on-r4-igs definition.resource.profile exampleCanonical is not set should add configured profile array to exampleCanonical and add an extension if length > 1")
        void shouldAddConfiguredProfileArrayToExampleCanonicalAndAddAnExtensionIfLength1() {
          var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
          assertTrue(Js.truthy(fs.existsSync(igPath)));
          var igContent = fs.readJSONSync(igPath);
          assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example-two"
          },
          "name": "patient-example-two",
          "exampleCanonical": "http://example.org/StructureDefiniton/first",
          "extension": [
            {
              "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.resource.profile",
              "valueCanonical": [
                "http://example.org/StructureDefiniton/first",
                "http://example.org/StructureDefiniton/second"
              ]
            }
          ]
        }
        """), at(igContent, "definition.resource"));
        }

        @Test
        @DisplayName("IGExporter #r5-properties-on-r4-igs definition.resource.profile exampleCanonical is not set should add exampleBoolean if isExample is set and no profile array configured")
        void shouldAddExampleBooleanIfIsExampleIsSetAndNoProfileArrayConfigured() {
          var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
          assertTrue(Js.truthy(fs.existsSync(igPath)));
          var igContent = fs.readJSONSync(igPath);
          assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example-three"
          },
          "name": "patient-example-three",
          "exampleBoolean": false
        }
        """), at(igContent, "definition.resource"));
        }

      }
      @DisplayName("exampleCanonical is set")
      @Nested
      @TestInstance(TestInstance.Lifecycle.PER_CLASS)
      class ExampleCanonicalIsSet {

        @Test
        @DisplayName("IGExporter #r5-properties-on-r4-igs definition.resource.profile exampleCanonical is set should add configured profile array to an extension and not change exampleCanonical if they differ")
        void shouldAddConfiguredProfileArrayToAnExtensionAndNotChangeExampleCanonicalIfTheyDi() {
          var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
          assertTrue(Js.truthy(fs.existsSync(igPath)));
          var igContent = fs.readJSONSync(igPath);
          assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example-four"
          },
          "name": "patient-example-four",
          "exampleCanonical": "http://hl7.org/fhir/sushi-test/StructureDefinition/sample-patient",
          "extension": [
            {
              "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.resource.profile",
              "valueCanonical": [
                "http://example.org/StructureDefinition/solo"
              ]
            }
          ]
        }
        """), at(igContent, "definition.resource"));
        }

        @Test
        @DisplayName("IGExporter #r5-properties-on-r4-igs definition.resource.profile exampleCanonical is set should not add any extension if the configured profile array matches the configured exampleCanonical")
        void shouldNotAddAnyExtensionIfTheConfiguredProfileArrayMatchesTheConfiguredExampleCa() {
          var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
          assertTrue(Js.truthy(fs.existsSync(igPath)));
          var igContent = fs.readJSONSync(igPath);
          assertContainsEqual(JsAssert.json("""
        {
          "reference": {
            "reference": "Patient/patient-example-five"
          },
          "name": "patient-example-five",
          "exampleCanonical": "http://example.org/StructureDefinition/solo"
        }
        """), at(igContent, "definition.resource"));
        }

      }
    }
    @DisplayName("definition.page.source[x] and definition.page.name")
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class DefinitionPageSourceXAndDefinitionPageName {
      String tempOutPages;
      String fixtures;
      FHIRDefinitions defs;

      @BeforeAll
      void beforeAll() {
        loggerSpy.reset();
        tempOutPages = temp.mkdirSync("sushi-test-pages");
        fixtures = path.join(__dirname, "fixtures", "simple-ig");
        // r5-definitions contains the guide-parameter-code CodeSystem, which was originally included in 5.0.0-ballot
        defs = getTestFHIRDefinitions(false, testDefsPath("r5-definitions"));
      }

      @Test
      @DisplayName("IGExporter #r5-properties-on-r4-igs definition.page.source[x] and definition.page.name should set sourceUrl to extension if it differs from nameUrl")
      void shouldSetSourceUrlToExtensionIfItDiffersFromNameUrl() {
        Configuration r4WithR5pagesConfig = MinimalConfig.minimalConfig();
        r4WithR5pagesConfig.pages = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "nameUrl": "nameUrl.md",
          "title": "Name Url",
          "sourceUrl": "different-sourceUrl.md",
          "generation": "markdown",
          "page": [
            {
              "nameUrl": "nested-nameUrl.md",
              "title": "Nested Name Url",
              "sourceUrl": "nested-different-sourceUrl.md",
              "generation": "markdown"
            }
          ]
        }
        """))));
        Package pkg = new Package(r4WithR5pagesConfig);
        IGExporter exporter = new IGExporter(pkg, defs, fixtures);
        exporter.export(tempOutPages);
        var igPath = path.join(tempOutPages, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
        assertTrue(Js.truthy(fs.existsSync(igPath)));
        var igContent = fs.readJSONSync(igPath);
        assertJsonEqual("""
        [
          {
            "nameUrl": "nameUrl.html",
            "generation": "markdown",
            "title": "Name Url",
            "extension": [
              {
                "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.page.source",
                "valueUrl": "different-sourceUrl.md"
              }
            ],
            "page": [
              {
                "nameUrl": "nested-nameUrl.html",
                "generation": "markdown",
                "title": "Nested Name Url",
                "extension": [
                  {
                    "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.page.source",
                    "valueUrl": "nested-different-sourceUrl.md"
                  }
                ]
              }
            ]
          }
        ]
        """, at(igContent, "definition.page.page"));
      }

      @Test
      @DisplayName("IGExporter #r5-properties-on-r4-igs definition.page.source[x] and definition.page.name should not add an extension if sourceUrl and nameUrl match")
      void shouldNotAddAnExtensionIfSourceUrlAndNameUrlMatch() {
        Configuration r4WithR5pagesConfig = MinimalConfig.minimalConfig();
        r4WithR5pagesConfig.pages = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "nameUrl": "matching-nameUrl.md",
          "title": "Name Url",
          "sourceUrl": "matching-nameUrl.md",
          "generation": "markdown",
          "page": [
            {
              "nameUrl": "nested-matching-nameUrl.md",
              "title": "Nested Name Url",
              "sourceUrl": "nested-matching-nameUrl.md",
              "generation": "markdown"
            }
          ]
        }
        """))));
        Package pkg = new Package(r4WithR5pagesConfig);
        IGExporter exporter = new IGExporter(pkg, defs, fixtures);
        exporter.export(tempOutPages);
        var igPath = path.join(tempOutPages, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
        assertTrue(Js.truthy(fs.existsSync(igPath)));
        var igContent = fs.readJSONSync(igPath);
        assertJsonEqual("""
        [
          {
            "nameUrl": "matching-nameUrl.html",
            "generation": "markdown",
            "title": "Name Url",
            "page": [
              {
                "nameUrl": "nested-matching-nameUrl.html",
                "generation": "markdown",
                "title": "Nested Name Url"
              }
            ]
          }
        ]
        """, at(igContent, "definition.page.page"));
      }

      @Test
      @DisplayName("IGExporter #r5-properties-on-r4-igs definition.page.source[x] and definition.page.name should add name to an extension if sourceUrl is defined")
      void shouldAddNameToAnExtensionIfSourceUrlIsDefined() {
        Configuration r4WithR5pagesConfig = MinimalConfig.minimalConfig();
        r4WithR5pagesConfig.pages = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "nameUrl": "name-and-sourceUrl.md",
          "title": "Name and SourceUrl",
          "sourceUrl": "sourceUrl.md",
          "name": "name.md",
          "generation": "markdown",
          "page": [
            {
              "nameUrl": "nested-name-and-sourceUrl.md",
              "title": "Nested Name and SourceUrl",
              "sourceUrl": "nested-sourceUrl.md",
              "name": "nested-name.md",
              "generation": "markdown"
            }
          ]
        }
        """))));
        Package pkg = new Package(r4WithR5pagesConfig);
        IGExporter exporter = new IGExporter(pkg, defs, fixtures);
        exporter.export(tempOutPages);
        var igPath = path.join(tempOutPages, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
        assertTrue(Js.truthy(fs.existsSync(igPath)));
        var igContent = fs.readJSONSync(igPath);
        assertJsonEqual("""
        [
          {
            "nameUrl": "name-and-sourceUrl.html",
            "title": "Name and SourceUrl",
            "generation": "markdown",
            "extension": [
              {
                "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.page.name",
                "valueUrl": "name.md"
              },
              {
                "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.page.source",
                "valueUrl": "sourceUrl.md"
              }
            ],
            "page": [
              {
                "nameUrl": "nested-name-and-sourceUrl.html",
                "title": "Nested Name and SourceUrl",
                "generation": "markdown",
                "extension": [
                  {
                    "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.page.name",
                    "valueUrl": "nested-name.md"
                  },
                  {
                    "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.page.source",
                    "valueUrl": "nested-sourceUrl.md"
                  }
                ]
              }
            ]
          }
        ]
        """, at(igContent, "definition.page.page"));
      }

      @Test
      @DisplayName("IGExporter #r5-properties-on-r4-igs definition.page.source[x] and definition.page.name should add name to an extension if name differs from nameUrl when sourceUrl is not define")
      void shouldAddNameToAnExtensionIfNameDiffersFromNameUrlWhenSourceUrlIsNotDefine() {
        Configuration r4WithR5pagesConfig = MinimalConfig.minimalConfig();
        r4WithR5pagesConfig.pages = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "nameUrl": "name.md",
          "title": "Name",
          "name": "different-name.md",
          "generation": "markdown",
          "page": [
            {
              "nameUrl": "nested-name.md",
              "title": "Nested Name",
              "name": "different-nested-name.md",
              "generation": "markdown"
            }
          ]
        }
        """))));
        Package pkg = new Package(r4WithR5pagesConfig);
        IGExporter exporter = new IGExporter(pkg, defs, fixtures);
        exporter.export(tempOutPages);
        var igPath = path.join(tempOutPages, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
        assertTrue(Js.truthy(fs.existsSync(igPath)));
        var igContent = fs.readJSONSync(igPath);
        assertJsonEqual("""
        [
          {
            "nameUrl": "name.html",
            "title": "Name",
            "generation": "markdown",
            "extension": [
              {
                "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.page.name",
                "valueUrl": "different-name.md"
              }
            ],
            "page": [
              {
                "nameUrl": "nested-name.html",
                "title": "Nested Name",
                "generation": "markdown",
                "extension": [
                  {
                    "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.page.name",
                    "valueUrl": "different-nested-name.md"
                  }
                ]
              }
            ]
          }
        ]
        """, at(igContent, "definition.page.page"));
      }

      @Test
      @DisplayName("IGExporter #r5-properties-on-r4-igs definition.page.source[x] and definition.page.name should not add name to an extension if name and nameUrl match")
      void shouldNotAddNameToAnExtensionIfNameAndNameUrlMatch() {
        Configuration r4WithR5pagesConfig = MinimalConfig.minimalConfig();
        r4WithR5pagesConfig.pages = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "nameUrl": "matching-name.md",
          "title": "Matching NameUrl and Name",
          "name": "matching-name.md",
          "generation": "markdown",
          "page": [
            {
              "nameUrl": "nested-matching-name.md",
              "title": "Nested Matching NameUrl and Name",
              "name": "nested-matching-name.md",
              "generation": "markdown"
            }
          ]
        }
        """))));
        Package pkg = new Package(r4WithR5pagesConfig);
        IGExporter exporter = new IGExporter(pkg, defs, fixtures);
        exporter.export(tempOutPages);
        var igPath = path.join(tempOutPages, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
        assertTrue(Js.truthy(fs.existsSync(igPath)));
        var igContent = fs.readJSONSync(igPath);
        assertJsonEqual("""
        [
          {
            "nameUrl": "matching-name.html",
            "title": "Matching NameUrl and Name",
            "generation": "markdown",
            "page": [
              {
                "nameUrl": "nested-matching-name.html",
                "title": "Nested Matching NameUrl and Name",
                "generation": "markdown"
              }
            ]
          }
        ]
        """, at(igContent, "definition.page.page"));
      }

      @Test
      @DisplayName("IGExporter #r5-properties-on-r4-igs definition.page.source[x] and definition.page.name should add sourceString to an extension")
      void shouldAddSourceStringToAnExtension() {
        Configuration r4WithR5pagesConfig = MinimalConfig.minimalConfig();
        r4WithR5pagesConfig.pages = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "nameUrl": "sourceString.md",
          "title": "SourceString",
          "sourceString": "sourceString for page",
          "generation": "markdown",
          "page": [
            {
              "nameUrl": "nested-sourceString.md",
              "title": "Nested SourceString",
              "sourceString": "nested sourceString for page",
              "generation": "markdown"
            }
          ]
        }
        """))));
        Package pkg = new Package(r4WithR5pagesConfig);
        IGExporter exporter = new IGExporter(pkg, defs, fixtures);
        exporter.export(tempOutPages);
        var igPath = path.join(tempOutPages, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
        assertTrue(Js.truthy(fs.existsSync(igPath)));
        var igContent = fs.readJSONSync(igPath);
        assertJsonEqual("""
        [
          {
            "nameUrl": "sourceString.html",
            "title": "SourceString",
            "generation": "markdown",
            "extension": [
              {
                "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.page.source",
                "valueString": "sourceString for page"
              }
            ],
            "page": [
              {
                "nameUrl": "nested-sourceString.html",
                "title": "Nested SourceString",
                "generation": "markdown",
                "extension": [
                  {
                    "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.page.source",
                    "valueString": "nested sourceString for page"
                  }
                ]
              }
            ]
          }
        ]
        """, at(igContent, "definition.page.page"));
      }

      @Test
      @DisplayName("IGExporter #r5-properties-on-r4-igs definition.page.source[x] and definition.page.name should add sourceMarkdown to an extension")
      void shouldAddSourceMarkdownToAnExtension() {
        Configuration r4WithR5pagesConfig = MinimalConfig.minimalConfig();
        r4WithR5pagesConfig.pages = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "nameUrl": "sourceMarkdown.md",
          "title": "SourceMarkdown",
          "sourceMarkdown": "sourceMarkdown for page",
          "generation": "markdown",
          "page": [
            {
              "nameUrl": "nested-sourceMarkdown.md",
              "title": "Nested SourceMarkdown",
              "sourceMarkdown": "nested sourceMarkdown for page",
              "generation": "markdown"
            }
          ]
        }
        """))));
        Package pkg = new Package(r4WithR5pagesConfig);
        IGExporter exporter = new IGExporter(pkg, defs, fixtures);
        exporter.export(tempOutPages);
        var igPath = path.join(tempOutPages, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
        assertTrue(Js.truthy(fs.existsSync(igPath)));
        var igContent = fs.readJSONSync(igPath);
        assertJsonEqual("""
        [
          {
            "nameUrl": "sourceMarkdown.html",
            "title": "SourceMarkdown",
            "generation": "markdown",
            "extension": [
              {
                "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.page.source",
                "valueMarkdown": "sourceMarkdown for page"
              }
            ],
            "page": [
              {
                "nameUrl": "nested-sourceMarkdown.html",
                "title": "Nested SourceMarkdown",
                "generation": "markdown",
                "extension": [
                  {
                    "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.page.source",
                    "valueMarkdown": "nested sourceMarkdown for page"
                  }
                ]
              }
            ]
          }
        ]
        """, at(igContent, "definition.page.page"));
      }

    }
    @Test
    @DisplayName("IGExporter #r5-properties-on-r4-igs should add definition.parameter.code system and code information to an extension if provided")
    void shouldAddDefinitionParameterCodeSystemAndCodeInformationToAnExtensionIfProvided() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertEquals(3, len(at(igContent, "definition.parameter")));
      assertJsonEqual("""
        {
          "code": "generate-xml",
          "value": "true"
        }
        """, at(igContent, "definition.parameter[0]"));
      assertJsonEqual("""
        {
          "code": "special-code",
          "value": "sparkles",
          "extension": [
            {
              "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.definition.resource.parameter.code",
              "valueCoding": {
                "code": "special-code",
                "system": "http://example.org/parameters"
              }
            }
          ]
        }
        """, at(igContent, "definition.parameter[1]"));
    }

    @Test
    @DisplayName("IGExporter #r5-properties-on-r4-igs should add copyrightLabel information to an extension if provided")
    void shouldAddCopyrightLabelInformationToAnExtensionIfProvided() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertContainsEqual(JsAssert.json("""
        {
          "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.copyrightLabel",
          "valueString": "Shorty Fsh 2022+"
        }
        """), at(igContent, "extension"));
    }

    @Test
    @DisplayName("IGExporter #r5-properties-on-r4-igs should add versionAlgorithmString to an extension if provided")
    void shouldAddVersionAlgorithmStringToAnExtensionIfProvided() {
      // Export IG in this test so can test all variations of versionAlgorithm[x]
      Configuration configWithVersionAlgorithm = MinimalConfig.minimalConfig();
      configWithVersionAlgorithm.versionAlgorithmString = "date";
      var fixtures = path.join(__dirname, "fixtures", "simple-ig");
      var defs = getTestFHIRDefinitions();
      Package pkg = new Package(configWithVersionAlgorithm);
      IGExporter exporter = new IGExporter(pkg, defs, fixtures);
      var tempOut = temp.mkdirSync("sushi-test-version-alg");
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.versionAlgorithm",
            "valueString": "date"
          }
        ]
        """, at(igContent, "extension"));
    }

    @Test
    @DisplayName("IGExporter #r5-properties-on-r4-igs should add versionAlgorithmCoding to an extension if provided")
    void shouldAddVersionAlgorithmCodingToAnExtensionIfProvided() {
      // Export IG in this test so can test all variations of versionAlgorithm[x]
      Configuration configWithVersionAlgorithm = MinimalConfig.minimalConfig();
      configWithVersionAlgorithm.versionAlgorithmCoding = (JsObject) JsAssert.json("""
        {
          "system": "http://example.org",
          "code": "semver"
        }
        """);
      var fixtures = path.join(__dirname, "fixtures", "simple-ig");
      var defs = getTestFHIRDefinitions();
      Package pkg = new Package(configWithVersionAlgorithm);
      IGExporter exporter = new IGExporter(pkg, defs, fixtures);
      var tempOut = temp.mkdirSync("sushi-test-version-alg");
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.versionAlgorithm",
            "valueCoding": {
              "system": "http://example.org",
              "code": "semver"
            }
          }
        ]
        """, at(igContent, "extension"));
    }

    @Test
    @DisplayName("IGExporter #r5-properties-on-r4-igs should add dependsOn.reason to an extension if provided")
    void shouldAddDependsOnReasonToAnExtensionIfProvided() {
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      var igContent = fs.readJSONSync(igPath);
      assertJsonEqual("""
        [
          {
            "packageId": "hl7.fhir.us.ocean",
            "uri": "http://example.org/ocean",
            "id": "ocean",
            "version": "1.0.0",
            "extension": [
              {
                "url": "http://hl7.org/fhir/5.0/StructureDefinition/extension-ImplementationGuide.dependsOn.reason",
                "valueMarkdown": "The ocean is essential because it has fsh"
              }
            ]
          }
        ]
        """, at(igContent, "dependsOn"));
    }

  }
  @DisplayName("#resolve-latest")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class ResolveLatest {
    Object ig;

    @BeforeAll
    void beforeAll() {
      loggerSpy.reset();
      var tempOut = temp.mkdirSync("sushi-test");
      var fixtures = path.join(__dirname, "fixtures", "simple-ig");
      // r4-definitions contains ImplementationGuide-hl7.fhir.us.core.json used for resolution
      var defs = getTestFHIRDefinitions(false, testDefsPath("r4-definitions"));
      // add de.medizininformatikinitiative.kerndatensatz.consent package.json used for resolution
      defs.loadVirtualPackage(diskPkg(jm(JsAssert.json("""
        {
          "name": "de.medizininformatikinitiative.kerndatensatz.consent",
          "version": "1.0.6",
          "description": "Put a description here",
          "author": "sebastianstubert",
          "fhirVersions": [
            "4.0.1"
          ],
          "dependencies": {
            "de.einwilligungsmanagement": "1.0.1"
          }
        }
        """))));
      Configuration config = MinimalConfig.minimalConfig();
      config.dependencies = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.core",
          "version": "latest"
        }
        """), JsAssert.json("""
        {
          "packageId": "de.medizininformatikinitiative.kerndatensatz.consent",
          "version": "latest"
        }
        """))));
      Package pkg = new Package(config);
      IGExporter exporter = new IGExporter(pkg, defs, fixtures);
      // No need to regenerate the IG on every test -- generate it once and inspect what you
      // need to in the tests
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      ig = fs.readJSONSync(igPath);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #resolve-latest should use the resolved version of a package from IG JSON when a dependency version is \"latest\"")
    void shouldUseTheResolvedVersionOfAPackageFromIGJSONWhenADependencyVersionIsLatest() {
      var dependencies = at(ig, "dependsOn");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      // Ensure US Core is exported with the resolved version
      assertContainsEqual(JsAssert.json("""
        {
          "id": "hl7_fhir_us_core",
          "uri": "http://hl7.org/fhir/us/core/ImplementationGuide/hl7.fhir.us.core",
          "packageId": "hl7.fhir.us.core",
          "version": "3.1.0"
        }
        """), dependencies);
    }

    @Test
    @DisplayName("IGExporter #resolve-latest should use the resolved version of a package from package.json when a dependency version is \"latest\"")
    void shouldUseTheResolvedVersionOfAPackageFromPackageJsonWhenADependencyVersionIsLate() {
      // See: https://chat.fhir.org/#narrow/stream/215610-shorthand/topic/automatically.20get.20latest.20dependencies/near/419245939
      var dependencies = at(ig, "dependsOn");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      // Ensure consent is exported with the resolved version
      assertContainsEqual(JsAssert.json("""
        {
          "id": "de_medizininformatikinitiative_kerndatensatz_consent",
          "uri": "http://fhir.org/packages/de.medizininformatikinitiative.kerndatensatz.consent/ImplementationGuide/de.medizininformatikinitiative.kerndatensatz.consent",
          "packageId": "de.medizininformatikinitiative.kerndatensatz.consent",
          "version": "1.0.6"
        }
        """), dependencies);
    }

  }
  @DisplayName("#npm-aliases")
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class NpmAliases {
    Object ig;

    @BeforeAll
    void beforeAll() {
      loggerSpy.reset();
      var tempOut = temp.mkdirSync("sushi-test");
      var fixtures = path.join(__dirname, "fixtures", "simple-ig");
      // r4-definitions contains ImplementationGuide-hl7.fhir.us.core.json used for resolution
      var defs = getTestFHIRDefinitions(false, testDefsPath("r4-definitions"));
      // Add the additional US Core package that will be aliased (along with minimal ImplementationGuide)
      Map<String, Object> additionalUSCoreMap = new LinkedHashMap<>();
      additionalUSCoreMap.put("ImplementationGuide-hl7.fhir.us.core", JsAssert.json("""
        {
          "resourceType": "ImplementationGuide",
          "id": "hl7.fhir.us.core",
          "url": "http://hl7.org/fhir/us/core/ImplementationGuide/hl7.fhir.us.core",
          "version": "7.0.0",
          "name": "USCore",
          "title": "US Core",
          "status": "active",
          "packageId": "hl7.fhir.us.core",
          "fhirVersion": [
            "4.0.1"
          ]
        }
        """));
      InMemoryVirtualPackage additionalUSCorePackage = new InMemoryVirtualPackage(jm(JsAssert.json("""
        {
          "name": "hl7.fhir.us.core",
          "version": "7.0.0"
        }
        """)), additionalUSCoreMap);
      defs.loadVirtualPackage(additionalUSCorePackage);
      Configuration config = MinimalConfig.minimalConfig();
      config.dependencies = jsList(new ArrayList<>(Arrays.asList(JsAssert.json("""
        {
          "packageId": "hl7.fhir.us.core",
          "version": "7.0.0"
        }
        """), JsAssert.json("""
        {
          "packageId": "v310@npm:hl7.fhir.us.core",
          "version": "3.1.0"
        }
        """))));
      Package pkg = new Package(config);
      IGExporter exporter = new IGExporter(pkg, defs, fixtures);
      // No need to regenerate the IG on every test -- generate it once and inspect what you
      // need to in the tests
      exporter.export(tempOut);
      var igPath = path.join(tempOut, "fsh-generated", "resources", "ImplementationGuide-fhir.us.minimal.json");
      assertTrue(Js.truthy(fs.existsSync(igPath)));
      ig = fs.readJSONSync(igPath);
    }

    @AfterAll
    void afterAll() {
      temp.cleanupSync();
    }

    @Test
    @DisplayName("IGExporter #npm-aliases should pass along the aliased package as-is, but detect correct IG URL and generate id w/ alias")
    void shouldPassAlongTheAliasedPackageAsIsButDetectCorrectIGURLAndGenerateIdWAlias() {
      var dependencies = at(ig, "dependsOn");
      assertEquals(0, loggerSpy.getAllLogs("error").size());
      // Ensure US Core is exported with the resolved version
      assertContainsEqual(JsAssert.json("""
        {
          "id": "hl7_fhir_us_core",
          "uri": "http://hl7.org/fhir/us/core/ImplementationGuide/hl7.fhir.us.core",
          "packageId": "hl7.fhir.us.core",
          "version": "7.0.0"
        }
        """), dependencies);
      assertContainsEqual(JsAssert.json("""
        {
          "id": "hl7_fhir_us_core_v310",
          "uri": "http://hl7.org/fhir/us/core/ImplementationGuide/hl7.fhir.us.core",
          "packageId": "v310@npm:hl7.fhir.us.core",
          "version": "3.1.0"
        }
        """), dependencies);
    }

  }
}
