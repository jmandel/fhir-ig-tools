# Differential-testing oracle: renders test cases with the REAL Ruby gems (liquid 4.0.4,
# kramdown 2.5.2 + kramdown-parser-gfm 1.1.0, configured exactly as Jekyll 4.4.1 does).
#
# usage (via oracle.sh): ruby oracle.rb <cases.jsonl> <expected.jsonl>
#
# Case formats (one JSON object per line, "id" required):
#   {"id":..,"type":"liquid","template":"..","assigns":{..},"error_mode":"lax|warn|strict",
#    "strict_variables":false,"strict_filters":false,"render_bang":false,"files":{"name":"src"}}
#      -> {"id":..,"output":".."} | {"id":..,"error":"Class: message"} ; plus "warnings":[..]
#      PURE Liquid 4.0.4 semantics: run in a separate process with only `liquid` loaded
#      (oracle_liquid.rb), because requiring jekyll replaces Liquid's include tag and overrides
#      sort/where/find. "files" backs Liquid's own {% include %} via a memory file system.
#   {"id":..,"type":"yaml","text":".."} -> {"id":..,"output":"<JSON.generate of SafeYAML.load>"}
#   {"id":..,"type":"yaml-typed","text":".."} / {"id":..,"type":"yaml-typed-file","path":".."}
#      -> {"id":..,"output":"<typed JSON of SafeYAML.load / load_file>" | "ERR <class>: <message>"}
#      (see yaml_typed below; used by the YAML port's conformance/typing/fuzz suites)
#   {"id":..,"type":"kramdown","text":"..","config":{..}}
#      -> {"id":..,"output":".."}  (exactly Jekyll's markdownify/converter: Jekyll defaults merged
#      with "config", which is the site's _config.yml `kramdown:` block, string keys)
#   {"id":..,"type":"jekyll-liquid","template":"..","assigns":{..},"config":{..},
#    "includes":{"name.html":"src"},"page":{"path":"a.html",..},"data":{..}}
#      -> Liquid as Jekyll renders a page: Jekyll's tags and filters, warn error mode, render!,
#      a real Jekyll::Site built from "config" over a temp source dir containing "includes"
#      under _includes/ and "data" as site.data. assigns are merged over {"site","page"}.
#   {"id":..,"type":"rouge","lang":"json","text":"..","formatter":"html|html_legacy|html_table",
#    "mark_lines":[1,3]}
#      -> {"id":..,"output":"<formatted HTML>","tag":"<lexer tag>"} : Rouge 4.7.0 with Jekyll's lexer
#      resolution (Rouge::Lexer.find_fancy(lang, text) || PlainText; lang "guess" guesses, "x?k=v"
#      passes lexer options). "html" = Formatters::HTML (Jekyll's {% highlight %}), "html_legacy" =
#      HTMLLegacy(css_class: 'highlight') (kramdown's default), "html_table" = HTMLTable(HTML,
#      css_class/gutter_class/code_class as Jekyll's linenos). "mark_lines" wraps the HTML formatter
#      in HTMLLineHighlighter(highlight_lines:) first, as Jekyll's mark_lines option does.
require 'json'
require 'tmpdir'
require 'fileutils'

inp, outp = ARGV
cases = File.foreach(inp).reject { |l| l.strip.empty? }.map { |l| JSON.parse(l) }
results = {}

liquid_cases = cases.select { |c| c['type'] == 'liquid' }
unless liquid_cases.empty?
  Dir.mktmpdir do |d|
    ci = File.join(d, 'cases.jsonl')
    co = File.join(d, 'out.jsonl')
    File.write(ci, liquid_cases.map(&:to_json).join("\n") + "\n")
    system('ruby', File.join(__dir__, 'oracle_liquid.rb'), ci, co, exception: true)
    File.foreach(co) { |l| r = JSON.parse(l); results[r['id']] = r }
  end
end

other = cases.reject { |c| c['type'] == 'liquid' }
unless other.empty?
  require 'jekyll'

  def kramdown_converter(cfg)
    @conv_cache ||= {}
    @conv_cache[cfg] ||= begin
      site_cfg = Jekyll::Configuration::DEFAULTS.dup
      site_cfg['kramdown'] = Jekyll::Utils.deep_merge_hashes(site_cfg['kramdown'], cfg || {})
      Jekyll::Converters::Markdown::KramdownParser.new(site_cfg)
    end
  end

  def yaml_typed(v, path = {}.compare_by_identity)
    case v
    when nil then 'n'
    when true then 'T'
    when false then 'F'
    when String then v.valid_encoding? ? ['s', v] : ['b', v.unpack1('H*')]
    when Integer then ['i', v.to_s]
    when Float then ['f', v.to_s]
    when Symbol then ['y', v.to_s]
    when Date then ['d', v.to_s, v.jd]
    when Time then ['t', v.strftime('%Y-%m-%d %H:%M:%S.%N %z'), v.utc?]
    when Array, Hash
      return ['rec'] if path[v]
      path[v] = true
      r = v.is_a?(Array) ? ['a', *v.map { |x| yaml_typed(x, path) }] : ['h', *v.map { |k, x| [yaml_typed(k, path), yaml_typed(x, path)] }]
      path.delete(v)
      r
    else ['?', v.class.name, v.inspect]
    end
  end

  def jekyll_render(c)
    Dir.mktmpdir do |src|
      (c['includes'] || {}).each do |name, body|
        path = File.join(src, '_includes', name)
        FileUtils.mkdir_p(File.dirname(path))
        File.write(path, body)
      end
      cfg = Jekyll.configuration((c['config'] || {}).merge('source' => src, 'destination' => File.join(src, '_site'), 'quiet' => true))
      site = Jekyll::Site.new(cfg)
      site.data = c['data'] || {}
      page = c['page'] || { 'path' => 'index.html' }
      payload = site.site_payload
      payload['page'] = page
      (c['assigns'] || {}).each { |k, v| payload[k] = v }
      lq = site.config["liquid"] || {}  # as Jekyll::Renderer#render_document (liquid_options)
      info = { registers: { site: site, page: page }, strict_filters: lq["strict_filters"], strict_variables: lq["strict_variables"] }
      t = site.liquid_renderer.file(page['path']).parse(c['template'])
      r = { 'output' => t.render!(payload, info) }
      r['warnings'] = t.warnings.map(&:to_s) unless t.warnings.empty?
      r
    end
  end

  other.each do |c|
    res = { 'id' => c['id'] }
    begin
      case c['type']
      when 'kramdown'
        res['output'] = kramdown_converter(c['config']).convert(c['text'])
      when 'data-file'
        # exactly Jekyll's DataReader#read_data_file for a JSON/YAML file (SafeYAML.load_file)
        res['output'] = JSON.generate(SafeYAML.load_file(c['path']))
      when 'yaml'
        # how Jekyll loads _config.yml and _data/*.{yml,yaml,json}; output = canonical JSON of the data
        res['output'] = JSON.generate(SafeYAML.load(c['text']))
      when 'yaml-typed', 'yaml-typed-file'
        # Typed encoding of SafeYAML.load / load_file (distinguishes Date/Time/Integer/Float/String/
        # nil and keeps non-string hash keys); errors become output "ERR <class>: <message>" so the
        # message text is compared too. Encoding (yaml_typed): nil "n", true "T", false "F",
        # ["s",str] | ["b",hex] (invalid UTF-8), ["i",to_s], ["f",to_s], ["y",sym], ["d",to_s,jd],
        # ["t","%Y-%m-%d %H:%M:%S.%N %z",utc?], ["a",items..], ["h",[k,v]..], ["rec"] (cycle).
        res['output'] = begin
          v = c['type'] == 'yaml-typed' ? SafeYAML.load(c['text']) : SafeYAML.load_file(c['path'])
          JSON.generate(yaml_typed(v), max_nesting: false)
        rescue StandardError => e
          "ERR #{e.class}: #{e.message}"
        end
      when 'jekyll-liquid'
        res.merge!(jekyll_render(c))
      when 'rouge'
        require 'rouge'
        lexer = Rouge::Lexer.find_fancy(c['lang'], c['text']) || Rouge::Lexers::PlainText
        fmt = Rouge::Formatters::HTML.new
        case c['formatter'] || 'html'
        when 'html_legacy'
          fmt = Rouge::Formatters::HTMLLegacy.new(css_class: 'highlight')
        when 'html', 'html_table'
          fmt = Rouge::Formatters::HTMLLineHighlighter.new(fmt, highlight_lines: c['mark_lines']) if c['mark_lines']
          if c['formatter'] == 'html_table'
            fmt = Rouge::Formatters::HTMLTable.new(fmt, css_class: 'highlight', gutter_class: 'gutter', code_class: 'code')
          end
        else
          raise "unknown rouge formatter #{c['formatter']}"
        end
        res['output'] = fmt.format(lexer.lex(c['text']))
        res['tag'] = lexer.tag
      else
        res['error'] = "unknown case type #{c['type']}"
      end
    rescue StandardError => e
      res['error'] = "#{e.class}: #{e.message}"
    end
    results[c['id']] = res
  end
end

File.open(outp, 'w') do |out|
  cases.each { |c| out.puts(results.fetch(c['id']).to_json) }
end
