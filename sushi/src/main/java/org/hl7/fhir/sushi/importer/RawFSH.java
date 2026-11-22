package org.hl7.fhir.sushi.importer;

// port of sushi/src/import/RawFSH.ts (SUSHI 3.21.1)
/** FSH source text and the path it came from ("" when none). */
public final class RawFSH {
  public final String content;
  public final String path;

  public RawFSH(String content) {
    this(content, null);
  }

  public RawFSH(String content, String path) {
    this.content = content;
    this.path = path != null ? path : "";
  }
}
