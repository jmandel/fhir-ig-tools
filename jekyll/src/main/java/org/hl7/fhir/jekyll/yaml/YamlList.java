package org.hl7.fhir.jekyll.yaml;

import java.util.ArrayList;

/**
 * A loaded YAML sequence: a plain {@code ArrayList<Object>} with recursion-safe
 * {@code hashCode}/{@code equals} (see {@link YamlMap}).
 */
public final class YamlList extends ArrayList<Object> {
  private static final long serialVersionUID = 1L;

  YamlList() {}

  @Override
  public int hashCode() {
    return YamlMap.Recursion.hash(this);
  }

  @Override
  public boolean equals(Object o) {
    return YamlMap.Recursion.equal(this, o);
  }
}
