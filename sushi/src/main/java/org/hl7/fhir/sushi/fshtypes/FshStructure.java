package org.hl7.fhir.sushi.fshtypes;

import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.sushi.fshtypes.rules.Rule;

// port of sushi/src/fshtypes/FshStructure.ts (SUSHI 3.21.1)
/**
 * Base of Profile, Extension, Logical and Resource. {@code rules} is typed {@code List<Rule>} here; TS narrows it per
 * subclass (SdRule[] / LrRule[]), which Java can't express on a field, so the narrowing is documentation only
 * (see {@link org.hl7.fhir.sushi.fshtypes.rules.SdRule}, {@link org.hl7.fhir.sushi.fshtypes.rules.LrRule}).
 */
public abstract class FshStructure extends FshEntity {
  public String name;
  private String _id;
  public String parent;
  public String title;
  public String description;
  /** Replaceable (applyInsertRules assigns a new list), so not final. */
  public List<Rule> rules = new ArrayList<>();

  protected FshStructure(String name) {
    this.name = name;
    this._id = name; // init same as name
  }

  public String constructorName() {
    return "FshStructure";
  }

  /** The id assigned by a {@code * ^id = "..."} caret rule if any, else the declared/initial id (TS getter). */
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
    resultLines.add(constructorName() + ": " + name);
    if (Common.truthy(parent)) {
      resultLines.add("Parent: " + parent);
    }
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
