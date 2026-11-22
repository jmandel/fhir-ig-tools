package org.hl7.fhir.sushi.export;

import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.sushi.utils.MasterFisher;

// port of sushi/src/export/FHIRExporter.ts (SUSHI 3.21.1)
/**
 * FHIRExporter handles the processing of FSH documents, storing the FSH types within them as FHIR types. It runs
 * applyInsertRules on every exporter, then exports StructureDefinitions, CodeSystems, ValueSets, Instances, applies
 * the StructureDefinition exporter's deferred rules, then exports Mappings -- in exactly the TS order.
 */
public class FHIRExporter {
  private final StructureDefinitionExporter structureDefinitionExporter;
  private final InstanceExporter instanceExporter;
  private final ValueSetExporter valueSetExporter;
  private final CodeSystemExporter codeSystemExporter;
  private final MappingExporter mappingExporter;
  private final Package pkg;

  public FHIRExporter(FSHTank tank, Package pkg, MasterFisher fisher) {
    this.pkg = pkg;
    this.structureDefinitionExporter = new StructureDefinitionExporter(tank, pkg, fisher);
    this.valueSetExporter = new ValueSetExporter(tank, pkg, fisher);
    this.codeSystemExporter = new CodeSystemExporter(tank, pkg, fisher);
    this.instanceExporter = new InstanceExporter(tank, pkg, fisher);
    this.mappingExporter = new MappingExporter(tank, pkg, fisher);
  }

  public Package export() {
    structureDefinitionExporter.applyInsertRules();
    codeSystemExporter.applyInsertRules();
    valueSetExporter.applyInsertRules();
    instanceExporter.applyInsertRules();
    mappingExporter.applyInsertRules();

    structureDefinitionExporter.export();
    codeSystemExporter.export();
    valueSetExporter.export();
    instanceExporter.export();
    structureDefinitionExporter.applyDeferredRules();
    mappingExporter.export();

    return pkg;
  }
}
