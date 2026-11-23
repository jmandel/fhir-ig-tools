package org.hl7.fhir.jekyll.site;

/**
 * Renders one page's content to output: Liquid (when the content has a Liquid construct), then the
 * converter (kramdown for Markdown extensions, identity otherwise). Mirrors
 * jekyll-4.4.1/lib/jekyll/renderer.rb#render_document for pages without layouts.
 */
public interface PageRenderer {
  /** Prepares site-wide state (payload, include cache) once before pages are rendered. */
  void prepare(Site site);

  /** Returns the rendered output; throws {@link RenderException} for Liquid/conversion errors. */
  String render(Site site, Site.Page page);

  /** Liquid warnings recorded during parsing (Jekyll prints them as "Liquid Warning:"). */
  default java.util.List<String> warnings() {
    return java.util.List.of();
  }

  final class RenderException extends RuntimeException {
    public RenderException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
