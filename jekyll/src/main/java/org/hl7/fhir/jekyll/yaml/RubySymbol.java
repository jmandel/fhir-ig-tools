package org.hl7.fhir.jekyll.yaml;

/** A Ruby Symbol; only produced if SafeYAML's {@code deserialize_symbols} option were on (Jekyll: off). */
public final class RubySymbol {
  private final String name;

  RubySymbol(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof RubySymbol && name.equals(((RubySymbol) o).name);
  }

  @Override
  public int hashCode() {
    return name.hashCode() ^ 0x5379;
  }
}
