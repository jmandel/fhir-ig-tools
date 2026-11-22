package org.hl7.fhir.sushi.importer.parser;

// port of the antlr4 JS runtime 4.13.2 InputStream (UTF-16 indexing, no code point decoding), as used by SUSHI 3.21.1
/** The parser input, indexed by UTF-16 code unit like SUSHI's JS runtime. */
public final class InputText {
  private final String data;

  public InputText(String data) {
    this.data = data;
  }

  public int size() { return data.length(); }

  public String getData() { return data; }

  /** JS InputStream.getText(start, stop): inclusive stop, clamped to the end; "" when start is past the end. */
  public String getText(int start, int stop) {
    if (stop >= data.length()) stop = data.length() - 1;
    if (start >= data.length()) return "";
    if (stop < start) return "";
    return data.substring(start, stop + 1);
  }

  @Override public String toString() { return data; }
}
