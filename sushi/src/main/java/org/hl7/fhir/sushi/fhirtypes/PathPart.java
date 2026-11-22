package org.hl7.fhir.sushi.fhirtypes;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.common.js.Js;

// port of the PathPart type in sushi/src/fhirtypes/StructureDefinition.ts (SUSHI 3.21.1)
/**
 * One dot-separated part of a parsed FSH path. TS optional properties map to {@code null} (absent): {@code brackets}
 * is null when the part has no brackets (not an empty list), {@code primitive} is null until validateValueAtPath sets
 * it. Mutable, like the TS object literal: several algorithms rewrite brackets in place.
 */
public class PathPart implements Js.DeepCloneable {
  public String base;
  public List<String> brackets;
  public Boolean primitive;
  public List<String> slices;
  public String prefix;

  public PathPart() {}

  public PathPart(String base) {
    this.base = base;
  }

  public PathPart(String base, List<String> brackets) {
    this.base = base;
    this.brackets = brackets == null ? null : new ArrayList<>(brackets);
  }

  /** Test/fixture helper: {@code new PathPart("a").withBrackets("0")}. */
  public PathPart withBrackets(String... brackets) {
    this.brackets = new ArrayList<>(List.of(brackets));
    return this;
  }

  public PathPart withSlices(String... slices) {
    this.slices = new ArrayList<>(List.of(slices));
    return this;
  }

  public PathPart withPrimitive(Boolean primitive) {
    this.primitive = primitive;
    return this;
  }

  public boolean isPrimitive() {
    return primitive != null && primitive;
  }

  /** lodash cloneDeep. */
  public PathPart copy() {
    PathPart p = new PathPart(base);
    p.brackets = brackets == null ? null : new ArrayList<>(brackets);
    p.primitive = primitive;
    p.slices = slices == null ? null : new ArrayList<>(slices);
    p.prefix = prefix;
    return p;
  }

  @Override public Object cloneDeep(IdentityHashMap<Object, Object> seen) {
    return copy();
  }

  /** lodash isEqual (an absent property only equals an absent property). */
  @Override public boolean equals(Object o) {
    return o instanceof PathPart p && Objects.equals(base, p.base) && Objects.equals(brackets, p.brackets)
        && Objects.equals(primitive, p.primitive) && Objects.equals(slices, p.slices)
        && Objects.equals(prefix, p.prefix);
  }

  @Override public int hashCode() {
    return Objects.hash(base, brackets, primitive, slices, prefix);
  }

  @Override public String toString() {
    StringBuilder sb = new StringBuilder("{base: ").append(base);
    if (brackets != null) sb.append(", brackets: ").append(brackets);
    if (primitive != null) sb.append(", primitive: ").append(primitive);
    if (slices != null) sb.append(", slices: ").append(slices);
    if (prefix != null) sb.append(", prefix: ").append(prefix);
    return sb.append('}').toString();
  }

  /** lodash cloneDeep of a PathPart[]. */
  public static List<PathPart> copyAll(List<PathPart> parts) {
    List<PathPart> out = new ArrayList<>(parts.size());
    for (PathPart p : parts) out.add(p.copy());
    return out;
  }
}
