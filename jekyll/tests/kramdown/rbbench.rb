require 'json'; require 'jekyll'
cases = File.readlines(ARGV[0]).map { |l| JSON.parse(l) }
conv = {}
3.times do |r|
  t = Time.now; ct = Process.clock_gettime(Process::CLOCK_THREAD_CPUTIME_ID)
  cases.each do |c|
    cfg = c['config']
    k = (conv[cfg] ||= begin
      site_cfg = Jekyll::Configuration::DEFAULTS.dup
      site_cfg['kramdown'] = Jekyll::Utils.deep_merge_hashes(site_cfg['kramdown'], cfg || {})
      Jekyll::Converters::Markdown::KramdownParser.new(site_cfg)
    end)
    k.convert(c['text'])
  end
  puts "ruby round #{r}: #{((Time.now - t) * 1000).round} ms wall, #{((Process.clock_gettime(Process::CLOCK_THREAD_CPUTIME_ID) - ct) * 1000).round} ms cpu"
end
