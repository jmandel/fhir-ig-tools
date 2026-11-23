package org.hl7.fhir.sushi.fshtypes.rules;

// port of sushi/src/fshtypes/rules/PathRule.ts (SUSHI 3.21.1)
/** A bare path ({@code * path}): only sets context for following indented rules / creates an element. */
public class PathRule extends Rule {
  public PathRule(String path) {
    super(path);
  }

  @Override public String constructorName() {
    return "PathRule";
  }

  @Override public String toFSH() {
    return "* " + path;
  }
}
