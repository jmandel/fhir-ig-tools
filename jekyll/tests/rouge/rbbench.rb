# Times each "rouge" case with real Rouge (warm-up pass first). usage: ruby rbbench.rb <cases.jsonl> [top]
require 'json'; require 'rouge'
cases = File.foreach(ARGV[0]).map { |l| JSON.parse(l) }
run = lambda do |c|
  lexer = Rouge::Lexer.find_fancy(c['lang'], c['text']) || Rouge::Lexers::PlainText
  Rouge::Formatters::HTML.new.format(lexer.lex(c['text']))
end
cases.each { |c| run.(c) }
times = cases.map { |c| t = Process.clock_gettime(Process::CLOCK_MONOTONIC); run.(c); [Process.clock_gettime(Process::CLOCK_MONOTONIC) - t, c['id'], c['text'].size] }
printf("total %.1f ms over %d cases\n", times.sum { |t| t[0] } * 1000, cases.size)
times.sort_by { |t| -t[0] }.first((ARGV[1] || 20).to_i).each { |t| printf("%9.1f ms  %8d chars  %s\n", t[0] * 1000, t[2], t[1]) }
