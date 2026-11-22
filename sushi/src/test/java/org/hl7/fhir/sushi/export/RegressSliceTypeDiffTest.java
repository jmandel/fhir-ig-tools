package org.hl7.fhir.sushi.export;

import static org.hl7.fhir.sushi.testhelpers.ImportSingleText.importSingleText;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Configuration;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.js.Json;
import org.hl7.fhir.sushi.testhelpers.MinimalConfig;
import org.hl7.fhir.sushi.testhelpers.TestFisher;
import org.junit.jupiter.api.Test;

/** Corpus fut-infrastructure__implementation-guide: a slice of an element whose type got ^type.aggregation. */
class RegressSliceTypeDiffTest {
  @SuppressWarnings("unchecked")
  @Test void sliceOfElementWithCaretTypeAggregationHasNoTypeInDiff() {
    var doc = importSingleText("""
        Profile: MiniPatient
        Parent: Patient
        * generalPractitioner 0..1
        * generalPractitioner ^type.aggregation = #referenced
        * generalPractitioner ^slicing.discriminator.type = #value
        * generalPractitioner ^slicing.discriminator.path = "identifier.system"
        * generalPractitioner ^slicing.rules = #open
        * generalPractitioner contains YderNummer 0..1
        """, "Mini.fsh");
    Configuration config = MinimalConfig.minimalConfig();
    FSHTank tank = new FSHTank(new ArrayList<>(List.of(doc)), config);
    Package pkg = new Package(config);
    var exporter = new StructureDefinitionExporter(tank, pkg, new TestFisher(tank, SdeTestBase.r4Defs(), pkg));
    exporter.export();
    StructureDefinition sd = pkg.profiles.get(0);
    ElementDefinition slice = sd.elements.stream().filter(e -> "Patient.generalPractitioner:YderNummer".equals(e.getId())).findFirst().get();
    Map<String, Object> diffEl = slice.calculateDiff().toJSON();
    assertFalse(diffEl.containsKey("type"), Json.stringify(diffEl));
  }

  /** Corpus WHO smart-trust (KD-3): an AddElement rule with an unknown type crashes with V8's TypeError text. */
  @Test void logicalAddElementWithUnknownTypeLogsV8TypeError() {
    org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy.reset();
    var doc = importSingleText("""
        Logical: Mini
        * a 0..1 NoSuchTypeAnywhere "a" "a"
        """, "Mini.fsh");
    Configuration config = MinimalConfig.minimalConfig();
    FSHTank tank = new FSHTank(new ArrayList<>(List.of(doc)), config);
    Package pkg = new Package(config);
    var exporter = new StructureDefinitionExporter(tank, pkg, new TestFisher(tank, SdeTestBase.r4Defs(), pkg));
    exporter.export();
    assertTrue(org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy.getAllMessages("error").stream()
        .anyMatch(m -> m.startsWith("Cannot read properties of undefined (reading 'sdType')")),
        String.valueOf(org.hl7.fhir.sushi.testhelpers.LoggerSpy.loggerSpy.getAllMessages("error")));
  }
}
