package org.hl7.fhir.sushi.fshtypes;

import java.util.Objects;

// port of sushi/src/fshtypes/FshEntity.ts type TextLocation (SUSHI 3.21.1)
/** 1-based lines, columns as reported by the ANTLR tokens (see FSHImporter). Value semantics (TS tests use toEqual). */
public final class TextLocation implements org.hl7.fhir.common.js.Js.DeepCloneable {
  public int startLine;
  public int startColumn;
  public int endLine;
  public int endColumn;

  public TextLocation(int startLine, int startColumn, int endLine, int endColumn) {
    this.startLine = startLine;
    this.startColumn = startColumn;
    this.endLine = endLine;
    this.endColumn = endColumn;
  }

  public TextLocation copy() {
    return new TextLocation(startLine, startColumn, endLine, endColumn);
  }

  @Override public boolean equals(Object o) {
    return o instanceof TextLocation t && t.startLine == startLine && t.startColumn == startColumn
        && t.endLine == endLine && t.endColumn == endColumn;
  }

  @Override public int hashCode() {
    return Objects.hash(startLine, startColumn, endLine, endColumn);
  }

  @Override public String toString() {
    return "{startLine:" + startLine + ",startColumn:" + startColumn + ",endLine:" + endLine + ",endColumn:" + endColumn + "}";
  }

  @Override
  public Object cloneDeep(java.util.IdentityHashMap<Object, Object> seen) {
    return copy();
  }
}
