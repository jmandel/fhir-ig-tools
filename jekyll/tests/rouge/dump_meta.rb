# Dumps Rouge 4.7.0's lexer registry metadata (Lexer.all order, tags, aliases, filenames, mimetypes,
# detectable?) and the name->class registry, for LexerRegistry.java.
require 'rouge'
require 'json'
all = Rouge::Lexer.all.map do |l|
  { 'class' => l.name.sub('Rouge::Lexers::', ''), 'tag' => l.tag, 'aliases' => l.aliases,
    'filenames' => l.filenames || [], 'mimetypes' => l.mimetypes || [], 'detectable' => l.detectable?,
    'title' => l.title }
end
reg = Rouge::Lexer.send(:registry).map { |k, v| [k, v.name.sub('Rouge::Lexers::', '')] }
puts JSON.pretty_generate({ 'version' => Rouge.version, 'all' => all, 'registry' => reg })
