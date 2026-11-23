package org.hl7.fhir.sushi.testhelpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Objects;
import org.hl7.fhir.common.js.Js;
import org.hl7.fhir.sushi.utils.Metadata;

/** Jest toEqual on utils.Metadata (undefined properties ignored, as Jest's toEqual does = Java null). */
public final class MetadataAssert {
  private MetadataAssert() {}

  /** A Metadata built like a TS object literal: md("id", "x", "name", "y", ...); TS `abstract` = "_abstract". */
  public static Metadata md(Object... kv) {
    Metadata m = new Metadata();
    for (int i = 0; i < kv.length; i += 2) {
      try {
        Metadata.class.getField((String) kv[i]).set(m, kv[i + 1]);
      } catch (ReflectiveOperationException e) {
        throw new IllegalArgumentException((String) kv[i], e);
      }
    }
    return m;
  }

  public static boolean metadataEquals(Metadata a, Metadata b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    for (var f : Metadata.class.getFields()) {
      try {
        Object x = f.get(a), y = f.get(b);
        if (x instanceof List<?> || y instanceof List<?>) {
          if (!Js.isEqual(x, y)) return false;
        } else if (!Objects.equals(x, y)) {
          return false;
        }
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    }
    return true;
  }

  public static void assertMetadataEquals(Metadata expected, Metadata actual) {
    if (!metadataEquals(expected, actual)) {
      fail("expected: " + show(expected) + "\n but was: " + show(actual));
    }
  }

  public static void assertMetadatasEqual(List<Metadata> expected, List<Metadata> actual) {
    assertEquals(expected.size(), actual.size(),
        () -> "lengths differ; actual: " + actual.stream().map(MetadataAssert::show).toList());
    for (int i = 0; i < expected.size(); i++) {
      assertMetadataEquals(expected.get(i), actual.get(i));
    }
  }

  public static String show(Metadata m) {
    if (m == null) return "undefined";
    StringBuilder sb = new StringBuilder("{");
    for (var f : Metadata.class.getFields()) {
      try {
        Object v = f.get(m);
        if (v != null) sb.append(f.getName()).append(": ").append(v).append(", ");
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    }
    return sb.append("}").toString();
  }
}
