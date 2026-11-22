package org.hl7.fhir.sushi.fshtypes.rules;

import org.hl7.fhir.sushi.fshtypes.Common;
import org.hl7.fhir.sushi.fshtypes.FshCode;

// port of sushi/src/fshtypes/rules/MappingRule.ts (SUSHI 3.21.1)
public class MappingRule extends Rule {
  public String map;
  public FshCode language;
  public String comment;

  public MappingRule(String path) {
    super(path);
  }

  @Override public String constructorName() {
    return "MappingRule";
  }

  @Override public String toFSH() {
    String p = Common.truthy(path) ? " " + path : "";
    String c = "";
    if (Common.truthy(comment)) {
      c = comment.indexOf('\n') > -1 ? " \"\"\"" + comment + "\"\"\"" : " \"" + Common.fshifyString(comment) + "\"";
    }
    String lang = language != null ? " " + language : "";
    return "*" + p + " -> \"" + Common.fshifyString(map) + "\"" + c + lang;
  }
}
