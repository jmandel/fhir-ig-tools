package org.hl7.fhir.sushi.importer;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import org.hl7.fhir.sushi.fshtypes.Common;
import org.hl7.fhir.sushi.fshtypes.FshCodeSystem;
import org.hl7.fhir.sushi.fshtypes.FshEntity;
import org.hl7.fhir.sushi.fshtypes.FshStructure;
import org.hl7.fhir.sushi.fshtypes.FshValueSet;
import org.hl7.fhir.sushi.fshtypes.Instance;

/**
 * TEMPORARY copies of the few sushi/src/fhirtypes/common.ts members the importer uses, until WP-5 publishes
 * fhirtypes.Common (requested in progress/sushi-port-requests.log). Replace every use with fhirtypes.Common then
 * and delete this class.
 */
final class FhirCommonBridge {
  private FhirCommonBridge() {}

  static final Set<String> CONFORMANCE_AND_TERMINOLOGY_RESOURCES = Set.of("CapabilityStatement",
      "CapabilityStatement2", "StructureDefinition", "ImplementationGuide", "SearchParameter", "MessageDefinition",
      "OperationDefinition", "CompartmentDefinition", "StructureMap", "GraphDefinition", "ExampleScenario",
      "CodeSystem", "ValueSet", "ConceptMap", "ConceptMap2", "NamingSystem", "TerminologyCapabilities");

  static final String TYPE_CHARACTERISTICS_EXTENSION =
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-type-characteristics";
  static final String LOGICAL_TARGET_EXTENSION = "http://hl7.org/fhir/tools/StructureDefinition/logical-target";
  static final String IMPOSE_PROFILE_EXTENSION =
      "http://hl7.org/fhir/StructureDefinition/structuredefinition-imposeProfile";

  private static final Pattern PATH_PERIOD = Pattern.compile("\\.(?![^\\[]*\\])");

  /** path.split(/\.(?![^\[]*\])/g): a period that isn't within square brackets; keeps empty parts like JS. */
  static List<String> splitOnPathPeriods(String path) {
    return List.of(PATH_PERIOD.split(path, -1));
  }

  static String getUrlFromFshDefinition(FshEntity fshDefinition, String canonical) {
    Common.ValueFromRules urlFromFshRules = Common.getValueFromRules(fshDefinition, "url", "", "url");
    if (urlFromFshRules != null && urlFromFshRules.value() instanceof String s && !urlFromFshRules.isInstance()) {
      return s;
    }
    String fhirType;
    if (fshDefinition instanceof FshValueSet) {
      fhirType = "ValueSet";
    } else if (fshDefinition instanceof FshCodeSystem) {
      fhirType = "CodeSystem";
    } else if (fshDefinition instanceof Instance i
        && ("ValueSet".equals(i.instanceOf) || "CodeSystem".equals(i.instanceOf))) {
      fhirType = i.instanceOf;
    } else {
      fhirType = "StructureDefinition";
    }
    return Common.jsString(canonical) + "/" + fhirType + "/" + Common.jsString(idOf(fshDefinition));
  }

  static String getVersionFromFshDefinition(FshEntity fshDefinition, String version) {
    Common.ValueFromRules v = Common.getValueFromRules(fshDefinition, "version", "", "version");
    if (v != null && v.value() instanceof String s && !v.isInstance()) {
      return s;
    }
    return version;
  }

  /** Hook for fhirtypes.Common.applyInsertRules(entity, tank); a no-op until WP-5 lands. */
  static BiConsumer<FshEntity, FSHTank> applyInsertRules = (entity, tank) -> {};

  static String idOf(FshEntity e) {
    if (e instanceof FshStructure s) return s.getId();
    if (e instanceof Instance i) return i.getId();
    if (e instanceof FshValueSet v) return v.getId();
    if (e instanceof FshCodeSystem c) return c.getId();
    if (e instanceof org.hl7.fhir.sushi.fshtypes.Invariant i) return i.getId();
    if (e instanceof org.hl7.fhir.sushi.fshtypes.RuleSet r) return r.getId();
    if (e instanceof org.hl7.fhir.sushi.fshtypes.Mapping m) return m.getId();
    return null;
  }
}
