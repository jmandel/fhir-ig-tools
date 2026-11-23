# P0.12: how does Jekyll's kramdown (as configured by Jekyll) and Liquid scale with line length?
# For each input shape, build one long line of N chars and time conversion; print seconds and the
# growth ratio between successive sizes (≈10 for linear in a 10× step, ≈100 for quadratic).
# usage: ruby longline_bench.rb [max_n]
require 'jekyll'
require 'benchmark'
require 'timeout'
STDOUT.sync = true
CAP = 20

MAX = (ARGV[0] || 1_000_000).to_i
cfg = Jekyll::Configuration::DEFAULTS.dup
cfg['kramdown'] = Jekyll::Utils.deep_merge_hashes(cfg['kramdown'],
  'toc_levels' => '1..3', 'smart_quotes' => %w[apos apos quot quot], 'typographic_symbols' => { 'laquo' => '<<', 'raquo' => '>>' })
KD = Jekyll::Converters::Markdown::KramdownParser.new(cfg)

def rep(unit, n) = unit * (n / unit.size + 1)
SHAPES = {
  'plain words'           => ->(n) { rep('lorem ipsum dolor ', n)[0, n] },
  'html td cells'         => ->(n) { '<table><tr>' + rep('<td>x</td>', n)[0, n] + '</tr></table>' },
  'html in md div=1'      => ->(n) { '<div markdown="1">' + rep('<span>a</span> *b* ', n)[0, n] + '</div>' },
  'pipe table row'        => ->(n) { "| a | b |\n|---|---|\n|" + rep(' x |', n)[0, n] },
  'emphasis chars'        => ->(n) { rep('*a* _b_ ', n)[0, n] },
  'unclosed emphasis'     => ->(n) { rep('*a _b ', n)[0, n] },
  'links'                 => ->(n) { rep('[x](http://e.org/a) ', n)[0, n] },
  'inline code ticks'     => ->(n) { rep('`c` ', n)[0, n] },
  'json in fence'         => ->(n) { "```json\n" + rep('{"a":[1,"x"]},', n)[0, n] + "\n```" },
  'json no fence'         => ->(n) { rep('{"a":[1,"x"],"b":"<y>"},', n)[0, n] },
  'escaped chars'         => ->(n) { rep('\\* \\_ &amp; &lt; ', n)[0, n] },
  'long url autolink'     => ->(n) { '<http://e.org/' + 'a' * n + '>' },
  'one giant word'        => ->(n) { 'a' * n },
}
LIQ = {
  'liquid: plain text'    => ->(n) { rep('lorem ipsum ', n)[0, n] },
  'liquid: many outputs'  => ->(n) { rep('{{ x }} ', n)[0, n] },
  'liquid: raw block'     => ->(n) { '{% raw %}' + rep('<td>x</td>', n)[0, n] + '{% endraw %}' },
  'liquid: lone braces'   => ->(n) { rep('{ } { ', n)[0, n] },
}
sizes = [5_000, 10_000, 20_000, 40_000, 80_000].select { |s| s <= MAX }
def time_it
  Timeout.timeout(CAP) { Benchmark.realtime { yield } }
rescue Timeout::Error
  Float::INFINITY
end
def fmt(t) = t.infinite? ? format('%9s', ">#{CAP}s") : format('%9.3fs', t)
KD.convert('warm *up* `x`')
puts format('%-22s %s', 'shape', sizes.map { |s| format('%10s', "#{s / 1000}k") }.join + '   growth per 2x (2=linear, 4=quadratic)')
SHAPES.each do |name, gen|
  ts = []; sizes.each { |s| ts << (ts.last&.infinite? ? Float::INFINITY : time_it { KD.convert(gen.call(s)) }) }
  growth = ts.each_cons(2).map { |a, b| b.infinite? ? '>' : format('%.0f', b / [a, 1e-6].max) }.join(',')
  puts format('%-22s %s   %s', name, ts.map { |t| fmt(t) }.join(' '), growth)
end
LIQ.each do |name, gen|
  ts = []; sizes.each { |s| src = gen.call(s); ts << (ts.last&.infinite? ? Float::INFINITY : time_it { Liquid::Template.parse(src, error_mode: :warn).render('x' => 1) }) }
  growth = ts.each_cons(2).map { |a, b| b.infinite? ? '>' : format('%.0f', b / [a, 1e-6].max) }.join(',')
  puts format('%-22s %s   %s', name, ts.map { |t| fmt(t) }.join(' '), growth)
end
