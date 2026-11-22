package org.hl7.fhir.sushi.fshtypes;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;

// port of sushi/src/fshtypes/Mapping.ts (SUSHI 3.21.1)
/** Mapping info for SDs. Unlike the other definitions, TS id is a plain field (not derived from rules). */
public class Mapping extends FshEntity {
  public String name;
  public String id;
  public String source;
  public String target;
  public String description;
  public String title;
  /** MappingRule | InsertRule. Replaceable (applyInsertRules), so not final. */
  public List<Rule> rules = new ArrayList<>();

  public Mapping(String name) {
    this.name = name;
    this.id = name; // init same as name
  }

  public String constructorName() {
    return "Mapping";
  }

  /** Uniform accessor (all fishable FSH definitions have getId()). */
  public String getId() {
    return id;
  }

  public String metadataToFSH() {
    List<String> resultLines = new ArrayList<>();
    resultLines.add("Mapping: " + name);
    resultLines.add("Id: " + id);
    if (Common.truthy(title)) {
      resultLines.add("Title: \"" + Common.fshifyString(title) + "\"");
    }
    if (Common.truthy(description)) {
      resultLines.add("Description: " + Common.quoted(description));
    }
    if (Common.truthy(source)) {
      resultLines.add("Source: " + source);
    }
    if (Common.truthy(target)) {
      resultLines.add("Target: \"" + Common.fshifyString(target) + "\"");
    }
    return String.join(Common.EOL, resultLines);
  }

  public String toFSH() {
    return Common.toFSH(metadataToFSH(), rules);
  }
}
