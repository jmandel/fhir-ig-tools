package org.hl7.fhir.jekyll.rouge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of rouge-4.7.0/lib/rouge/guesser.rb and guessers/{mimetype,modeline,source,util}.rb (the
 * guessers reachable from kramdown/Jekyll: <code>Lexer.guess(mimetype:)</code> and
 * <code>find_fancy('guess', code)</code>). Filename/glob/disambiguation guessers need a filename,
 * which neither caller passes.
 */
final class Guessers {
  private Guessers() {}

  interface Guesser {
    List<LexerClass> filter(List<LexerClass> lexers);
  }

  /** Guesser.guess(guessers, lexers) */
  static List<LexerClass> guess(List<Guesser> guessers, List<LexerClass> lexers) {
    int originalSize = lexers.size();
    for (Guesser g : guessers) {
      List<LexerClass> n = g.filter(lexers);
      lexers = n != null && !n.isEmpty() ? n : lexers;
    }
    return lexers.size() < originalSize ? lexers : List.of();
  }

  /** guessers/util.rb SourceNormalizer.normalize */
  static String normalize(String source) {
    if (source.startsWith("﻿")) {
      source = source.substring(1);
    }
    return source.replace("\r\n", "\n");
  }

  static final class Mimetype implements Guesser {
    final String mimetype;

    Mimetype(String mimetype) {
      this.mimetype = mimetype;
    }

    @Override
    public List<LexerClass> filter(List<LexerClass> lexers) {
      List<LexerClass> out = new ArrayList<>();
      for (LexerClass l : lexers) {
        if (l.mimetypes.contains(mimetype)) {
          out.add(l);
        }
      }
      return out;
    }
  }

  static final class Modeline implements Guesser {
    static final Pattern EMACS_MODELINE = RRegex.compile(
        "-\\*-\\s*(?:(?!mode)[\\w-]+\\s*:\\s*(?:[\\w+-]+)\\s*;?\\s*)*(?:mode\\s*:)?\\s*([\\w+-]+)\\s*(?:;\\s*(?!mode)[\\w-]+\\s*:\\s*[\\w+-]+\\s*)*;?\\s*-\\*-",
        "i");
    static final Pattern VIM_MODELINE_1 = RRegex.compile("(?:vim|vi|ex):\\s*(?:ft|filetype|syntax)=(\\w+)\\s?", "i");
    static final Pattern VIM_MODELINE_2 = RRegex.compile("(?:vim|vi|Vim|ex):\\s*se(?:t)?.*\\s(?:ft|filetype|syntax)=(\\w+)\\s?.*:", "i");
    static final Pattern[] MODELINES = {EMACS_MODELINE, VIM_MODELINE_1, VIM_MODELINE_2};

    final String source;
    final int lines = 5;

    Modeline(String source) {
      this.source = source;
    }

    @Override
    public List<LexerClass> filter(List<LexerClass> lexers) {
      if (lexers.size() == 1) {
        return lexers;
      }
      String text = normalize(source);
      List<String> ls = RubyStrings.split(text, "\n");
      List<String> space = new ArrayList<>(ls.subList(0, Math.min(lines, ls.size())));
      space.addAll(ls.subList(Math.max(0, ls.size() - lines), ls.size()));
      String searchSpace = String.join("\n", space);
      Set<String> matchSet = new HashSet<>();
      boolean any = false;
      for (Pattern re : MODELINES) {
        Matcher m = re.matcher(searchSpace);
        if (m.find()) {
          any = true;
          matchSet.add(m.group(1));
        }
      }
      if (!any) {
        return lexers;
      }
      List<LexerClass> out = new ArrayList<>();
      for (LexerClass l : lexers) {
        boolean hit = matchSet.contains(l.tag);
        for (String a : l.aliases) {
          hit |= matchSet.contains(a);
        }
        if (hit) {
          out.add(l);
        }
      }
      return out;
    }
  }

  static final class Source implements Guesser {
    final String source;

    Source(String source) {
      this.source = source;
    }

    @Override
    public List<LexerClass> filter(List<LexerClass> lexers) {
      if (lexers.size() == 1) {
        return lexers;
      }
      TextAnalyzer text = new TextAnalyzer(normalize(source));
      // collect_best(lexers) { detectable? && detect? ? 1 : nil }: every detected lexer scores 1
      List<LexerClass> best = new ArrayList<>();
      for (LexerClass l : lexers) {
        if (l.detectable && l.detect(text)) {
          best.add(l);
        }
      }
      return best;
    }
  }
}
