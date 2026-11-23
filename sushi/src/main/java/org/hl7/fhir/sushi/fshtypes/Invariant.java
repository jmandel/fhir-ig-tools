package org.hl7.fhir.sushi.fshtypes;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;

// port of sushi/src/fshtypes/Invariant.ts (SUSHI 3.21.1)
/**
 * Represents ElementDefinition.constraint. Fields map to constraint fields except description -> human and
 * name -> key.
 */
public class Invariant extends FshEntity {
  public String name;
  public String description;
  public String expression;
  public String xpath;
  public FshCode severity;
  /** AssignmentRule | InsertRule. Replaceable (applyInsertRules), so not final. */
  public List<Rule> rules = new ArrayList<>();

  public Invariant(String name) {
    this.name = name;
  }

  public String constructorName() {
    return "Invariant";
  }

  /** Read-only: the name (so everything FSHTank fishes for has an id). */
  public String getId() {
    return name;
  }

  public String metadataToFSH() {
    List<String> resultLines = new ArrayList<>();
    resultLines.add("Invariant: " + name);
    if (Common.truthy(description)) {
      resultLines.add("Description: " + Common.quoted(description));
    }
    if (severity != null) {
      resultLines.add("* severity = " + severity);
    }
    if (Common.truthy(expression)) {
      resultLines.add("* expression = " + Common.quoted(expression));
    }
    if (Common.truthy(xpath)) {
      resultLines.add("* xpath = \"" + Common.fshifyString(xpath) + "\"");
    }
    return String.join(Common.EOL, resultLines);
  }

  public String toFSH() {
    return Common.toFSH(metadataToFSH(), rules);
  }
}
