// Vendored from fhir-ig-jekyll@ecd3c065 shim/src/main/java/org/hl7/fhir/jekyll/yaml/Mark.java (libyaml 0.2.5 event layer),
// unchanged except the package line, until the monorepo move puts it in common (PORTING.md section 7).
package org.hl7.fhir.sushi.yaml;

/** Port of {@code yaml_mark_t} (libyaml-0.2.5/include/yaml.h): a position in the input (characters, 0-based). */
public final class Mark {
  public static final Mark ZERO = new Mark(0, 0, 0);

  public final long index;
  public final long line;
  public final long column;

  public Mark(long index, long line, long column) {
    this.index = index;
    this.line = line;
    this.column = column;
  }

  @Override
  public String toString() {
    return "line " + (line + 1) + " column " + (column + 1);
  }
}
