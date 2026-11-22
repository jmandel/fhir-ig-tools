package org.hl7.fhir.sushi.fshtypes;

import java.util.Objects;

// port of sushi/src/fshtypes/FshEntity.ts type SourceInfo (SUSHI 3.21.1)
/** All fields optional (null = undefined). Value semantics for test comparisons. */
public final class SourceInfo {
  public String file;
  public TextLocation location;
  public String appliedFile;
  public TextLocation appliedLocation;

  public SourceInfo() {}

  public SourceInfo(String file, TextLocation location) {
    this.file = file;
    this.location = location;
  }

  /** Deep copy (TS cloneDeep of a sourceInfo). */
  public SourceInfo copy() {
    SourceInfo s = new SourceInfo();
    s.file = file;
    s.location = location == null ? null : location.copy();
    s.appliedFile = appliedFile;
    s.appliedLocation = appliedLocation == null ? null : appliedLocation.copy();
    return s;
  }

  @Override public boolean equals(Object o) {
    return o instanceof SourceInfo s && Objects.equals(file, s.file) && Objects.equals(location, s.location)
        && Objects.equals(appliedFile, s.appliedFile) && Objects.equals(appliedLocation, s.appliedLocation);
  }

  @Override public int hashCode() {
    return Objects.hash(file, location, appliedFile, appliedLocation);
  }
}
