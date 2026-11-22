# Jekyll plugin (load with `jekyll build --plugins <dir>`): records every markdownify call as an
# oracle case + expected pair, so the kramdown port can be tested on real production inputs.
# Output: $CAPTURE_DIR/md-cases.jsonl and md-expected.jsonl (deduplicated by input).
require 'json'
require 'digest'

module MarkdownifyCapture
  SEEN = {}
  def markdownify(input)
    out = super
    text = input.to_s
    key = Digest::SHA1.hexdigest(text)
    unless SEEN[key]
      SEEN[key] = true
      dir = ENV.fetch('CAPTURE_DIR')
      site = @context.registers[:site]
      cfg = site.config['kramdown'].reject { |k, _| %w[syntax_highlighter syntax_highlighter_opts coderay].include?(k) }
      id = "#{ENV.fetch('CAPTURE_TAG', 'site')}:#{key[0, 12]}"
      File.open(File.join(dir, 'md-cases.jsonl'), 'a') { |f| f.puts({ id: id, type: 'kramdown', text: text, config: cfg }.to_json) }
      File.open(File.join(dir, 'md-expected.jsonl'), 'a') { |f| f.puts({ id: id, output: out }.to_json) }
    end
    out
  end
end
Jekyll::Filters.prepend(MarkdownifyCapture)
