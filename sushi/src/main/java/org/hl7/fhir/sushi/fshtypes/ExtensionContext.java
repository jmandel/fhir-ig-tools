package org.hl7.fhir.sushi.fshtypes;

// port of sushi/src/fshtypes/Extension.ts type ExtensionContext (SUSHI 3.21.1)
public class ExtensionContext {
  public String value;
  public boolean isQuoted;
  /** optional */
  public SourceInfo sourceInfo;

  public ExtensionContext(String value, boolean isQuoted) {
    this.value = value;
    this.isQuoted = isQuoted;
  }

  public ExtensionContext(String value, boolean isQuoted, SourceInfo sourceInfo) {
    this(value, isQuoted);
    this.sourceInfo = sourceInfo;
  }
}
