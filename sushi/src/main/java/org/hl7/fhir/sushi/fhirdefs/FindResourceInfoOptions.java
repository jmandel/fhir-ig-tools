package org.hl7.fhir.sushi.fhirdefs;

import java.util.List;

// port of fhir-package-loader 2.2.4 src/package/ResourceInfo.ts `FindResourceInfoOptions` + src/sort/*
/**
 * Options for {@link BasePackageLoader#findResourceInfos}. {@code type}: resourceTypes and/or SD flavors (Extension,
 * Logical, Profile, Resource, Type); null/empty = any. {@code scope}: {@code packageName[|packageVersion]}.
 * {@code sort}: null = load order ascending. {@code limit}: null/0 = no limit.
 */
public record FindResourceInfoOptions(List<String> type, String scope, List<SortBy> sort, Integer limit) {
  public static final FindResourceInfoOptions NONE = new FindResourceInfoOptions(null, null, null, null);

  public static FindResourceInfoOptions ofType(String... types) {
    return new FindResourceInfoOptions(List.of(types), null, null, null);
  }

  public FindResourceInfoOptions withType(List<String> t) { return new FindResourceInfoOptions(t, scope, sort, limit); }
  public FindResourceInfoOptions withScope(String s) { return new FindResourceInfoOptions(type, s, sort, limit); }
  public FindResourceInfoOptions withSort(List<SortBy> s) { return new FindResourceInfoOptions(type, scope, s, limit); }
  public FindResourceInfoOptions withLimit(Integer l) { return new FindResourceInfoOptions(type, scope, sort, l); }

  /** A sort expression: {@code byLoadOrder(ascending)} or {@code byType(types...)}. */
  public sealed interface SortBy permits ByLoadOrder, ByType {}

  public record ByLoadOrder(boolean ascending) implements SortBy {}

  public record ByType(List<String> types) implements SortBy {}

  public static SortBy byLoadOrder(boolean ascending) { return new ByLoadOrder(ascending); }

  public static SortBy byLoadOrder() { return new ByLoadOrder(true); }

  public static SortBy byType(String... types) { return new ByType(List.of(types)); }

  public static SortBy byType(List<String> types) { return new ByType(List.copyOf(types)); }
}
