package org.hl7.fhir.sushi.importer.config;

import static org.hl7.fhir.sushi.importer.config.TestJs.*;
import static org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.sushi.utils.FshLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// port of sushi/test/import/YAMLConfiguration.test.ts (SUSHI 3.21.1): YAML.parse is org.hl7.fhir.sushi.yaml.Yaml
class YAMLConfigurationTest {
  final Map<String, Object> vars = new HashMap<>();

  static String fixture(String... parts) {
    return ImportConfigurationTest.fixture(parts);
  }
  @Nested
  @DisplayName("YAMLConfiguration")
  class DYamlconfiguration1 {
    @Nested
    @DisplayName("#yaml-parse")
    class DYamlParse2 {
      @Test
      @DisplayName("YAMLConfiguration #yaml-parse should parse the YAML correctly")
      void t3() throws Exception {
        String configYAML = Files.readString(Path.of(fixture("fixtures", "example-config.yaml")));
        Object config = org.hl7.fhir.sushi.yaml.Yaml.parse(configYAML);
        assertToEqual("fhir.us.example", prop(config, "id"));
        assertToEqual("http://hl7.org/fhir/us/example", prop(config, "canonical"));
        assertToEqual("http://hl7.org/fhir/us/example/ImplementationGuide/fhir.us.example", prop(config, "url"));
        assertToEqual("ExampleIG", prop(config, "name"));
        assertToEqual("HL7 FHIR Implementation Guide: Example IG Release 1 - US Realm | STU1", prop(config, "title"));
        assertToEqual("Example IG exercises many of the fields in a SUSHI configuration.", prop(config, "description"));
        assertToEqual("active", prop(config, "status"));
        assertToEqual("CC0-1.0", prop(config, "license"));
        assertToEqual("2020-02-26", prop(config, "date"));
        assertToEqual("1.0.0", prop(config, "version"));
        assertToEqual("4.0.1", prop(config, "fhirVersion"));
        assertToEqual("hl7.fhir.template#0.0.5", prop(config, "template"));
        assertToEqual("2019+", prop(config, "copyrightYear"));
        assertToEqual("STU1", prop(config, "releaseLabel"));
        assertToEqual(js("""
            {
            name: 'HL7 FHIR Management Group',
            url: 'http://www.hl7.org/Special/committees/fhirmg',
            email: 'fmg@lists.HL7.org'
            }""", vars), prop(config, "publisher"));
        assertToEqual(js("""
            [
            {
            name: 'Bob Smith',
            telecom: [
            {
            system: 'email',
            value: 'bobsmith@example.org',
            use: 'work'
            }
            ]
            }
            ]""", vars), prop(config, "contact"));
        assertToEqual("urn:iso:std:iso:3166#US \"United States of America\"", prop(config, "jurisdiction"));
        assertToEqual(js("""
            {
            'hl7.fhir.us.core': '3.1.0',
            'hl7.fhir.us.mcode': {
            id: 'mcode',
            uri: 'http://hl7.org/fhir/us/mcode/ImplementationGuide/hl7.fhir.us.mcode',
            version: '1.0.0'
            },
            'hl7.fhir.us.foo': {
            id: 'foo',
            version: '1.0.0',
            extension: [
            { url: 'http://example.org/fake/StructureDefinition/bar', valueString: 'bar' }
            ]
            }
            }""", vars), prop(config, "dependencies"));
        assertToEqual(js("""
            {
            Patient: 'http://example.org/fhir/StructureDefinition/my-patient-profile',
            Encounter: 'http://example.org/fhir/StructureDefinition/my-encounter-profile'
            }""", vars), prop(config, "global"));
        assertToEqual(js("""
            {
            extension: [
            {
            url: 'http://example.org/example/ig-definition-ext',
            valueBoolean: true
            }
            ]
            }""", vars), prop(config, "definition"));
        assertToEqual(js("""
            {
            'Patient/my-example-patient': {
            name: 'My Example Patient',
            description: 'An example Patient',
            exampleBoolean: true
            },
            'Patient/bad-example': 'omit'
            }""", vars), prop(config, "resources"));
        assertToEqual(js("""
            {
            GroupA: {
            name: 'Group A',
            description: 'The Alpha Group',
            resources: ['StructureDefinition/animal-patient', 'StructureDefinition/arm-procedure']
            },
            GroupB: {
            name: 'Group B',
            description: 'The Beta Group',
            resources: ['StructureDefinition/bark-control', 'StructureDefinition/bee-sting']
            }
            }""", vars), prop(config, "groups"));
        assertToEqual(js("""
            {
            'index.md': {
            title: 'Example Home'
            },
            'implementation.xml': null,
            'examples.xml': {
            title: 'Examples Overview',
            'simpleExamples.xml': null,
            'complexExamples.xml': null,
            extension: [
            {
            url: 'http://example.org/my-extension',
            valueCode: 'example-code'
            }
            ],
            modifierExtension: [
            {
            url: 'http://example.org/my-modifier-extension',
            valueBoolean: true
            }
            ]
            }
            }""", vars), prop(config, "pages"));
        assertToEqual(js("""
            {
            Home: 'index.html',
            Artifacts: {
            Profiles: 'artifacts.html#2',
            Extensions: 'artifacts.html#3',
            'Value Sets': 'artifacts.html#4'
            },
            Downloads: 'downloads.html',
            History: 'http://hl7.org/fhir/us/example/history.html',
            'FHIR Spec': 'new-tab http://hl7.org/fhir/R4/index.html'
            }""", vars), prop(config, "menu"));
        assertToEqual(js("""
            {
            excludettl: true,
            validation: ['allow-any-extensions', 'no-broken-links']
            }""", vars), prop(config, "parameters"));
        assertToEqual(js("""
            {
            current: 'https://build.fhir.org/ig/HL7/example-ig/',
            '1.0.0': {
            fhirversion: '4.0.1',
            date: '2020-03-06',
            desc: 'STU 1 Release',
            path: 'https://hl7.org/fhir/us/example/STU1/',
            status: 'trial-use',
            sequence: 'STU 1',
            current: true
            },
            '0.9.1': {
            fhirversion: '4.0.0',
            date: '2019-06-10',
            desc: 'Initial STU ballot (Sep 2019 Ballot)',
            path: 'https://hl7.org/fhir/us/example/2019Sep/',
            status: 'ballot',
            sequence: 'STU 1'
            }
            }""", vars), prop(config, "history"));
        assertEquals(false, prop(config, "FSHOnly"));
        assertEquals(true, prop(config, "applyExtensionMetadataToRoot"));
        assertToEqual(js("""
            {
            setMetaProfile: 'always',
            setId: 'always',
            manualSliceOrdering: true
            }""", vars), prop(config, "instanceOptions"));
      }
    }
  }
}
