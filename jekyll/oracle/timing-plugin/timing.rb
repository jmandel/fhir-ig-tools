# Jekyll plugin (load with `jekyll build --plugins <dir-containing-only-this>`): times every
# markdownify call and every page render, recording input size and longest line, to find inputs
# where Jekyll is pathologically slow (P0.12: "long lines are orders of magnitude slower").
# Output: $TIMING_OUT (JSONL): {kind, page, bytes, lines, max_line, seconds, sha}
require 'json'
require 'digest'

module ShimTiming
  def self.record(kind, page, text, secs)
    t = text.to_s
    lens = t.each_line.map(&:length)
    File.open(ENV.fetch('TIMING_OUT'), 'a') do |f|
      f.puts({ kind: kind, page: page, bytes: t.bytesize, lines: lens.size, max_line: lens.max || 0,
               seconds: secs.round(5), sha: Digest::SHA1.hexdigest(t)[0, 12] }.to_json)
    end
  end
end

module MarkdownifyTiming
  def markdownify(input)
    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    out = super
    page = (@context.registers[:page] || {})['path']
    ShimTiming.record('markdownify', page, input, Process.clock_gettime(Process::CLOCK_MONOTONIC) - t0)
    out
  end
end
Jekyll::Filters.prepend(MarkdownifyTiming)

module RenderTiming
  def render_document
    t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    out = super
    ShimTiming.record('page', document.relative_path, document.content, Process.clock_gettime(Process::CLOCK_MONOTONIC) - t0)
    out
  end
end
Jekyll::Renderer.prepend(RenderTiming)
