# Pure Liquid 4.0.4 oracle (NO Jekyll loaded): Jekyll globally replaces Liquid's `include` tag and
# overrides `sort`/`where`/`find`, so `type: liquid` cases must not run in a process that has
# required jekyll. Called by oracle.rb; same case/expected formats.
#
# usage: ruby oracle_liquid.rb <cases.jsonl> <expected.jsonl>
require 'json'
require 'liquid'

class MemFS
  def initialize(files) = (@files = files || {})
  def read_template_file(path)
    @files.fetch(path) { raise Liquid::FileSystemError, "No such template '#{path}'" }
  end
end

inp, outp = ARGV
File.open(outp, 'w') do |out|
  File.foreach(inp) do |line|
    next if line.strip.empty?
    c = JSON.parse(line)
    res = { 'id' => c['id'] }
    begin
      mode = (c['error_mode'] || 'lax').to_sym
      t = Liquid::Template.parse(c['template'], error_mode: mode, line_numbers: true)
      res['warnings'] = t.warnings.map(&:to_s) unless t.warnings.empty?
      regs = { file_system: MemFS.new(c['files']) }
      opts = { registers: regs, strict_variables: c['strict_variables'], strict_filters: c['strict_filters'] }
      ctx = Liquid::Context.new([c['assigns'] || {}], {}, regs, c['render_bang'] || false)
      res['output'] = c['render_bang'] ? t.render!(ctx, opts) : t.render(ctx, opts)
      res['render_errors'] = t.errors.map { |e| "#{e.class}: #{e.message}" } unless t.errors.empty?
    rescue StandardError => e
      res['error'] = "#{e.class}: #{e.message}"
    end
    out.puts(res.to_json)
  end
end
