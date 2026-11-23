package org.hl7.fhir.sushi.fshtypes;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;

// port of sushi/src/fshtypes/FshCodeSystem.ts (SUSHI 3.21.1)
public class FshCodeSystem extends FshEntity {
  public String name;
  private String _id;
  public String title;
  public String description;
  /** ConceptRule | CaretValueRule | InsertRule. Replaceable (applyInsertRules), so not final. */
  public List<Rule> rules = new ArrayList<>();

  public FshCodeSystem(String name) {
    this.name = name;
    this._id = name;
  }

  public String constructorName() {
    return "FshCodeSystem";
  }

  /** The id assigned by a {@code * ^id = "..."} caret rule if any, else the initial id (TS getter). */
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
    resultLines.add("CodeSystem: " + name);
    resultLines.add("Id: " + getId());
    if (Common.truthy(title)) {
      resultLines.add("Title: \"" + Common.fshifyString(title) + "\"");
    }
    if (Common.truthy(description)) {
      resultLines.add("Description: " + Common.quoted(description));
    }
    return String.join(Common.EOL, resultLines);
  }

  public String toFSH() {
    return Common.toFSH(metadataToFSH(), rules);
  }
}
