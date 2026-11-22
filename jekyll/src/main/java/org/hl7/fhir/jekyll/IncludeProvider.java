package org.hl7.fhir.jekyll;

/**
 * Supplies `_includes` content from memory instead of disk, so a caller (the IG Publisher) can
 * generate a fragment only when a page first includes it.
 *
 * <p>Consulted by `{% include name %}` (and by `{% include_relative %}` when the resolved path lies
 * inside the site's `_includes` folder) before the disk: a non-null result is used as the file's
 * content (exactly what the file would contain, e.g. the publisher's `{% raw %}…{% endraw %}`
 * wrapping; a leading BOM is dropped as for files); null means "not mine", and the include is then
 * resolved on disk as Jekyll does, failing the build with Jekyll's "Could not locate the included
 * file" error when it isn't there either.
 *
 * <p>Contract: the shim asks for each name at most once per build and keeps the returned content for
 * later uses. Calls come from the render threads, so different names may be requested concurrently;
 * an implementation backed by code that isn't thread-safe must serialize itself. An exception fails
 * the build (as a {@link ShimException}).
 *
 * <p>Includes supplied this way are not files, so they never appear in `site.static_files` or
 * `site.pages` (Jekyll excludes `_includes` from both anyway).
 */
@FunctionalInterface
public interface IncludeProvider {
  /**
   * @param name the path below `_includes`, '/'-separated, as resolved by the include tag (e.g.
   *     "StructureDefinition-foo-snapshot.xhtml")
   * @return the include's content, or null when this provider has no include of that name
   */
  String include(String name) throws Exception;
}
