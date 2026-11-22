package org.hl7.fhir.sushi.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hl7.fhir.sushi.errors.InvalidFHIRIdError;
import org.hl7.fhir.sushi.fhirtypes.Common;
import org.hl7.fhir.sushi.fhirtypes.ElementDefinition;
import org.hl7.fhir.sushi.fhirtypes.PrimitiveTypes;
import org.hl7.fhir.sushi.fhirtypes.StructureDefinition;
import org.hl7.fhir.sushi.fshtypes.Mapping;
import org.hl7.fhir.sushi.fshtypes.rules.MappingRule;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;
import org.hl7.fhir.sushi.importer.FSHTank;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.common.js.JsObject;
import org.hl7.fhir.sushi.utils.FishableType;
import org.hl7.fhir.sushi.utils.MasterFisher;
import org.hl7.fhir.sushi.utils.PathUtils;

import static org.hl7.fhir.sushi.export.ExporterLog.str;
import static org.hl7.fhir.sushi.utils.FshLogger.logger;

// port of sushi/src/export/MappingExporter.ts (SUSHI 3.21.1) -- owned by WP-7
public class MappingExporter {
  private final FSHTank tank;
  private final Package pkg;
  private final MasterFisher fisher;

  public MappingExporter(FSHTank tank, Package pkg, MasterFisher fisher) {
    this.tank = tank;
    this.pkg = pkg;
    this.fisher = fisher;
  }

  /**
   * Sets the mapping on the StructureDefinition.
   *
   * @throws InvalidFHIRIdError when the id on the fshDefinition is not valid in FHIR
   */
  private void setMetadata(StructureDefinition structDef, Mapping fshDefinition) {
    if (structDef.getMapping() == null) {
      structDef.setMapping(new ArrayList<>());
    }
    // JS RegExp.test(undefined) tests the string "undefined" (which is a valid id)
    if (!PrimitiveTypes.ID_REGEX.matcher(str(fshDefinition.id)).find()) {
      throw new InvalidFHIRIdError(fshDefinition.id);
    }
    JsObject mapping = new JsObject();
    CodeSystemExporter.putOrUndefined(mapping, "identity", fshDefinition.id);
    if (Js.truthy(fshDefinition.title)) mapping.put("name", fshDefinition.title);
    if (Js.truthy(fshDefinition.target)) mapping.put("uri", fshDefinition.target);
    if (Js.truthy(fshDefinition.description)) mapping.put("comment", fshDefinition.description);
    structDef.getMapping().add(mapping);
  }

  /** Applies mapping rules to the individual ElementDefinitions on the SD. */
  private void setMappingRules(StructureDefinition structDef, Mapping fshDefinition) {
    PathUtils.resolveSoftIndexing(fshDefinition.rules);
    for (Rule r : fshDefinition.rules) {
      MappingRule rule = (MappingRule) r;
      ElementDefinition element = structDef.findElementByPath(rule.path, fisher);
      if (element != null) {
        try {
          element.applyMapping(fshDefinition.id, rule.map, rule.comment, rule.language);
        } catch (RuntimeException e) {
          ExporterLog.errorWithStack(e, rule.sourceInfo);
        }
      } else {
        logger.error("No element found at path " + rule.path + " for " + fshDefinition.name + ", skipping rule",
            rule.sourceInfo);
      }
    }
  }

  public void applyInsertRules() {
    for (Mapping mapping : tank.getAllMappings()) {
      Common.applyInsertRules(mapping, tank);
    }
  }

  /** Exports a Mapping by finding the source StructureDefinition and applying rules. */
  @SuppressWarnings("unchecked")
  public void exportMapping(Mapping fshDefinition) {
    StructureDefinition sourceStructDef = (StructureDefinition) pkg.fish(fshDefinition.source,
        FishableType.Profile, FishableType.Extension, FishableType.Resource, FishableType.Logical);
    if (sourceStructDef != null) {
      Object parent = fisher.fishForFHIR(sourceStructDef.getBaseDefinition(), FishableType.Resource, FishableType.Type,
          FishableType.Profile, FishableType.Extension, FishableType.Logical);
      Map<String, Object> matchingParentMapping = null;
      if (parent instanceof Map<?, ?> p && p.get("mapping") instanceof List<?> parentMappings) {
        for (Object m : parentMappings) {
          if (m instanceof Map<?, ?> mm && Objects.equals(mm.get("identity"), fshDefinition.id)) {
            matchingParentMapping = (Map<String, Object>) mm;
            break;
          }
        }
      }
      if (matchingParentMapping != null) {
        boolean isMatchingTitle = !Js.truthy(fshDefinition.title)
            || fshDefinition.title.equals(matchingParentMapping.get("name"));
        boolean isMatchingTarget = !Js.truthy(fshDefinition.target)
            || fshDefinition.target.equals(matchingParentMapping.get("uri"));
        if (!isMatchingTitle || !isMatchingTarget) {
          // If the mapping identity matches one on the parent, all other metadata must also match in order to
          // merge MappingRules
          logger.error("Unable to add Mapping " + fshDefinition.name
              + " because it conflicts with one already on the parent of " + fshDefinition.source + ".",
              fshDefinition.sourceInfo);
          return;
        } else {
          // Update parent mapping with additional or changed metadata (comment is the only property this can be
          // the case for)
          Map<String, Object> inheritedMapping = null;
          for (Object m : sourceStructDef.getMapping()) {
            if (m instanceof Map<?, ?> mm && Objects.equals(mm.get("identity"), fshDefinition.id)) {
              inheritedMapping = (Map<String, Object>) mm;
              break;
            }
          }
          if (Js.truthy(fshDefinition.description)) {
            // TS: a TypeError here if the source has no inherited mapping, caught and logged by export()
            Objects.requireNonNull(inheritedMapping,
                "Cannot set properties of undefined (setting 'comment')").put("comment", fshDefinition.description);
          }
        }
      } else {
        // Only add metadata if it does not already exist on the parent
        setMetadata(sourceStructDef, fshDefinition);
      }
      setMappingRules(sourceStructDef, fshDefinition);
    } else {
      logger.error("Unable to find source \"" + fshDefinition.source + "\".", fshDefinition.sourceInfo);
    }
  }

  public void export() {
    List<Mapping> mappings = tank.getAllMappings();
    for (Mapping mapping : mappings) {
      try {
        exportMapping(mapping);
      } catch (RuntimeException e) {
        ExporterLog.errorWithStack(e, mapping.sourceInfo);
      }
    }
    // The mappings on each Structure Definition should have a unique id
    Map<String, List<Mapping>> groupedMappings = groupBy(mappings, m -> str(m.source));
    for (String sd : Js.ownKeys(groupedMappings)) {
      Map<String, List<Mapping>> byId = groupBy(groupedMappings.get(sd), m -> str(m.id));
      for (String duplicateId : Js.ownKeys(byId)) {
        List<Mapping> duplicates = byId.get(duplicateId);
        // show error for each time the id was used after the first
        for (Mapping m : duplicates.subList(Math.min(1, duplicates.size()), duplicates.size())) {
          logger.error("Multiple mappings on " + sd + " found with id " + str(m.id), m.sourceInfo);
        }
      }
    }
  }

  /** lodash groupBy: keys in first-occurrence order (enumerated with Js.ownKeys, like for..in). */
  private static <T> Map<String, List<T>> groupBy(List<T> items, java.util.function.Function<T, String> key) {
    Map<String, List<T>> grouped = new LinkedHashMap<>();
    for (T item : items) {
      grouped.computeIfAbsent(key.apply(item), k -> new ArrayList<>()).add(item);
    }
    return grouped;
  }
}
