package org.hl7.fhir.jekyll.site;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.jekyll.util.Json;

/**
 * Jekyll 4.4.1 configuration: Jekyll::Configuration::DEFAULTS (dumped from the reference gem) deep
 * merged with _config.yml, then the processing Jekyll.configuration applies (configuration.rb):
 * absolute source/destination, the default "posts" collection, DEFAULT_EXCLUDES appended.
 */
final class JekyllConfig {
  private JekyllConfig() {}

  static final String DEFAULTS_JSON = "{\"source\": \"/home/publisher/ig\", \"destination\": \"/home/publisher/ig/_site\", \"collections_dir\": \"\", \"cache_dir\": \".jekyll-cache\", \"plugins_dir\": \"_plugins\", \"layouts_dir\": \"_layouts\", \"data_dir\": \"_data\", \"includes_dir\": \"_includes\", \"collections\": {}, \"safe\": false, \"include\": [\".htaccess\"], \"exclude\": [], \"keep_files\": [\".git\", \".svn\"], \"encoding\": \"utf-8\", \"markdown_ext\": \"markdown,mkdown,mkdn,mkd,md\", \"strict_front_matter\": false, \"show_drafts\": null, \"limit_posts\": 0, \"future\": false, \"unpublished\": false, \"whitelist\": [], \"plugins\": [], \"markdown\": \"kramdown\", \"highlighter\": \"rouge\", \"lsi\": false, \"excerpt_separator\": \"\\n\\n\", \"incremental\": false, \"detach\": false, \"port\": \"4000\", \"host\": \"127.0.0.1\", \"baseurl\": null, \"show_dir_listing\": false, \"permalink\": \"date\", \"paginate_path\": \"/page:num\", \"timezone\": null, \"quiet\": false, \"verbose\": false, \"defaults\": [], \"liquid\": {\"error_mode\": \"warn\", \"strict_filters\": false, \"strict_variables\": false}, \"kramdown\": {\"auto_ids\": true, \"toc_levels\": [1, 2, 3, 4, 5, 6], \"entity_output\": \"as_char\", \"smart_quotes\": \"lsquo,rsquo,ldquo,rdquo\", \"input\": \"GFM\", \"hard_wrap\": false, \"guess_lang\": true, \"footnote_nr\": 1, \"show_warnings\": false}}";

  @SuppressWarnings("unchecked")
  static Map<String, Object> defaults() {
    return (Map<String, Object>) Json.parse(DEFAULTS_JSON);
  }

  /** Utils.deep_merge_hashes(master, other): other wins; nested hashes merge recursively. */
  @SuppressWarnings("unchecked")
  static Map<String, Object> deepMerge(Map<String, Object> master, Map<String, Object> other) {
    Map<String, Object> out = new LinkedHashMap<>(master);
    for (Map.Entry<String, Object> e : other.entrySet()) {
      Object a = out.get(e.getKey());
      Object b = e.getValue();
      if (a instanceof Map && b instanceof Map) {
        out.put(e.getKey(), deepMerge((Map<String, Object>) a, (Map<String, Object>) b));
      } else {
        out.put(e.getKey(), b);
      }
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> build(Map<String, Object> userConfig, String source, String destination) {
    Map<String, Object> c = deepMerge(defaults(), userConfig == null ? Map.of() : userConfig);
    c.put("source", source);
    c.put("destination", destination);
    Map<String, Object> collections = c.get("collections") instanceof Map
        ? new LinkedHashMap<>((Map<String, Object>) c.get("collections")) : new LinkedHashMap<>();
    if (!collections.containsKey("posts")) {
      Map<String, Object> posts = new LinkedHashMap<>();
      posts.put("output", Boolean.TRUE);
      posts.put("permalink", "/:categories/:year/:month/:day/:title:output_ext");
      collections.put("posts", posts);
    }
    c.put("collections", collections);
    List<Object> exclude = c.get("exclude") instanceof List ? new ArrayList<>((List<Object>) c.get("exclude")) : new ArrayList<>();
    for (String d : Site.DEFAULT_EXCLUDES) {
      if (!exclude.contains(d)) {
        exclude.add(d);
      }
    }
    c.put("exclude", exclude);
    return c;
  }
}
