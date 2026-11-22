package org.hl7.fhir.sushi.fshtypes;

// port of sushi/src/fshtypes/FshEntity.ts (SUSHI 3.21.1)
/**
 * Abstract class to contain elements common to all FSH types.
 *
 * <p>Fluent setters return {@code this} through an unchecked generic, so both
 * {@code Profile p = new Profile("P").withFile("a.fsh").withLocation(1, 1, 3, 5);} and
 * {@code new CardRule("x").<CardRule>withFile("f")} read like the TS. The cast is safe because the method
 * always returns {@code this}; assigning to an unrelated subtype throws ClassCastException at the call site.
 */
public abstract class FshEntity {
  public final SourceInfo sourceInfo = new SourceInfo();

  /** Rule lookup cache for definitions with rules (see Common.RuleLookupCache); not part of the TS shape. */
  Common.RuleLookupCache ruleLookupCache;

  /**
   * The TS class name, for messages and AllowedRules. TS defines the getter on definitions and rules only; the
   * default here is the Java simple name, which equals the TS name for every fshtypes class.
   */
  public String constructorName() {
    return getClass().getSimpleName();
  }

  @SuppressWarnings("unchecked")
  public <T extends FshEntity> T withLocation(TextLocation location) {
    this.sourceInfo.location = location;
    return (T) this;
  }

  /** TS withLocation([startLine, startColumn, endLine, endColumn]). */
  public <T extends FshEntity> T withLocation(int[] location) {
    return withLocation(new TextLocation(location[0], location[1], location[2], location[3]));
  }

  public <T extends FshEntity> T withLocation(int startLine, int startColumn, int endLine, int endColumn) {
    return withLocation(new TextLocation(startLine, startColumn, endLine, endColumn));
  }

  @SuppressWarnings("unchecked")
  public <T extends FshEntity> T withFile(String file) {
    this.sourceInfo.file = file;
    return (T) this;
  }

  @SuppressWarnings("unchecked")
  public <T extends FshEntity> T withAppliedLocation(TextLocation location) {
    this.sourceInfo.appliedLocation = location;
    return (T) this;
  }

  public <T extends FshEntity> T withAppliedLocation(int[] location) {
    return withAppliedLocation(new TextLocation(location[0], location[1], location[2], location[3]));
  }

  @SuppressWarnings("unchecked")
  public <T extends FshEntity> T withAppliedFile(String file) {
    this.sourceInfo.appliedFile = file;
    return (T) this;
  }

  /** Copies another entity's sourceInfo fields into this one (TS spreads / Object.assign of sourceInfo). */
  protected void copySourceInfoFrom(FshEntity other) {
    SourceInfo c = other.sourceInfo.copy();
    sourceInfo.file = c.file;
    sourceInfo.location = c.location;
    sourceInfo.appliedFile = c.appliedFile;
    sourceInfo.appliedLocation = c.appliedLocation;
  }
}
