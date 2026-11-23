# Records liquid-4.0.4's own test suite as oracle cases (type: liquid).
# Patches Template#parse/#render and the test's direct StandardFilters calls; anything whose
# inputs aren't plain JSON (drops, procs, symbols, Time, custom contexts/filters) is skipped.
# usage (in the reference image): ruby record_suite.rb <liquid-src-dir> <out.jsonl> <mode>
require 'json'
SRC, OUT, MODE = ARGV
ENV['LIQUID_PARSER_MODE'] = MODE
$LOAD_PATH.unshift(File.join(SRC, 'test'))
require File.join(SRC, 'test', 'test_helper.rb')

module Rec
  CASES = {}
  def self.json?(v)
    case v
    when String then v.valid_encoding?
    when Integer, TrueClass, FalseClass, NilClass then true
    when Float then v.finite?
    when Array then v.all? { |e| json?(e) }
    when Hash then v.all? { |k, e| k.is_a?(String) && json?(e) }
    else false
    end
  end
  def self.add(c, test)
    key = c.to_json
    return if CASES.key?(key)
    CASES[key] = test
  end
  def self.current_test
    Thread.current[:rec_test] || 'unknown'
  end
end

class Minitest::Test
  alias_method :__rec_run_orig, :run
  def run
    Thread.current[:rec_test] = "#{self.class.name}##{name}"
    __rec_run_orig
  end
end

class Liquid::Template
  alias_method :__rec_parse, :parse
  def parse(source, options = {})
    top = options.is_a?(Hash) && source.is_a?(String)
    if top
      @__rec_src = source
      @__rec_mode = (options[:error_mode] || Liquid::Template.error_mode).to_s
      @__rec_ok = Rec.json?(source) && (options.keys - [:error_mode, :line_numbers]).empty?
    end
    begin
      __rec_parse(source, options)
    rescue Liquid::Error => e
      Rec.add({ 'template' => source, 'error_mode' => @__rec_mode }, Rec.current_test) if top && @__rec_ok
      raise
    end
  end

  alias_method :__rec_render, :render
  def render(*args)
    ok = @__rec_ok && !Thread.current[:rec_in_render]
    assigns = args[0]
    opts = args[1]
    ok &&= assigns.nil? || (assigns.is_a?(Hash) && Rec.json?(assigns))
    ok &&= args.length <= 1 || (opts.is_a?(Hash) && (opts.keys - [:strict_variables, :strict_filters]).empty?)
    ok &&= args.length <= 2
    bang = @rethrow_errors
    Thread.current[:rec_files] = {}
    Thread.current[:rec_in_render] = true
    begin
      __rec_render(*args)
    ensure
      Thread.current[:rec_in_render] = false
      if ok
        c = { 'template' => @__rec_src, 'error_mode' => @__rec_mode }
        c['assigns'] = assigns if assigns && !assigns.empty?
        c['render_bang'] = true if bang
        c['strict_variables'] = true if opts && opts[:strict_variables]
        c['strict_filters'] = true if opts && opts[:strict_filters]
        files = Thread.current[:rec_files]
        c['files'] = files unless files.empty?
        Rec.add(c, Rec.current_test) if files.values.all? { |v| v.is_a?(String) }
      end
    end
  end
end

class Liquid::Include
  alias_method :__rec_read, :read_template_from_file_system
  def read_template_from_file_system(context)
    src = __rec_read(context)
    name = context.evaluate(@template_name_expr)
    (Thread.current[:rec_files] ||= {})[name] = src.is_a?(String) ? src : nil if name.is_a?(String)
    src
  end
end

# Direct filter calls in standard_filter_test.rb -> {{ input | name: a0, a1 }}
module RecFilters
  Liquid::StandardFilters.public_instance_methods(false).each do |m|
    define_method(m) do |*args, &blk|
      if !Thread.current[:rec_in_filter] && Rec.json?(args) && args.length >= 1
        assigns = { 'input' => args[0] }
        argn = args[1..].each_with_index.map { |a, i| assigns["a#{i}"] = a; "a#{i}" }
        t = "{{ input | #{m}#{argn.empty? ? '' : ': ' + argn.join(', ')} }}"
        Rec.add({ 'template' => t, 'error_mode' => 'strict', 'assigns' => assigns, 'render_bang' => true }, Rec.current_test)
      end
      Thread.current[:rec_in_filter] = true
      begin
        super(*args, &blk)
      ensure
        Thread.current[:rec_in_filter] = false
      end
    end
  end
end

Dir[File.join(SRC, 'test', '{integration,unit}', '**', '*_test.rb')].sort.each { |f| require f }
Filters.prepend(RecFilters) if defined?(Filters)

Minitest.after_run do
  n = 0
  File.open(OUT, 'w') do |out|
    Rec::CASES.each do |json, test|
      c = JSON.parse(json)
      n += 1
      c = { 'id' => "suite-#{MODE}-#{n}", 'type' => 'liquid', 'from' => test }.merge(c)
      out.puts(c.to_json)
    end
  end
  warn "recorded #{n} cases -> #{OUT}"
end
