package org.hl7.fhir.sushi.fshtypes;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;

// port of sushi/src/fshtypes/Instance.ts (SUSHI 3.21.1)
public class Instance extends FshEntity {
  public String name;
  private String _id;
  public String title;
  public String instanceOf;
  public String description;
  /** null = undefined; defaults to Example. */
  public InstanceUsage usage = InstanceUsage.Example;
  /** AssignmentRule | InsertRule | PathRule. Replaceable (applyInsertRules), so not final. */
  public List<Rule> rules = new ArrayList<>();

  public Instance(String name) {
    this.name = name;
    this._id = name; // init same as name
  }

  public String constructorName() {
    return "Instance";
  }

  /** The id assigned by a {@code * id = "..."} rule if any, else the initial id (TS getter). */
  public String getId() {
    Object assignedId = Common.getNonInstanceValueFromRules(this, "id", "", "id");
    if (assignedId instanceof String s) {
      return s;
    }
    return _id;
  }

  public void setId(String id) {
    this._id = id;
  }

  public String metadataToFSH() {
    List<String> resultLines = new ArrayList<>();
    resultLines.add("Instance: " + name);
    resultLines.add("InstanceOf: " + Common.jsString(instanceOf));
    if (Common.truthy(title)) {
      resultLines.add("Title: \"" + Common.fshifyString(title) + "\"");
    }
    if (Common.truthy(description)) {
      resultLines.add("Description: " + Common.quoted(description));
    }
    if (usage != null) {
      resultLines.add("Usage: #" + usage.name().toLowerCase(java.util.Locale.ROOT));
    }
    return String.join(Common.EOL, resultLines);
  }

  public String toFSH() {
    return Common.toFSH(metadataToFSH(), rules);
  }
}
