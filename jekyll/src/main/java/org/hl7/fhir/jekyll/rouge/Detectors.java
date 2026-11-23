package org.hl7.fhir.jekyll.rouge;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The <code>self.detect?(text)</code> methods of all 38 detectable Rouge 4.7.0 lexers (keyed by
 * lexer class name), used by {@link Guessers.Source}. Ported for every lexer, including ones whose
 * token rules are not ported, so that guessing picks the same lexer class as Ruby.
 */
final class Detectors {
  private Detectors() {}

  static final Map<String, Predicate<TextAnalyzer>> DETECT = new HashMap<>();

  private static Pattern re(String src, String flags) {
    return RRegex.compile(src, flags);
  }

  private static void d(String cls, Predicate<TextAnalyzer> p) {
    DETECT.put(cls, p);
  }

  private static void shebang(String cls, String... names) {
    d(cls, t -> {
      for (String n : names) {
        if (t.shebangP(n)) {
          return true;
        }
      }
      return false;
    });
  }

  static {
    Pattern biml = re("<\\s*Biml\\b", "");
    Pattern diff1 = re("\\Adiff[^\\n]*?\\ba/[^\\n]*\\bb/", "");
    Pattern diff2 = re("---.*?\\n[+][+][+]", "");
    Pattern diff3 = re("[+][+][+].*?\\n---", "");
    Pattern hack1 = re("<\\?hh", ""), hack2 = re("async function [a-zA-Z]", ""), hack3 = re("\\): Awaitable<", "");
    Pattern htmlDt = re("\\bhtml\\b", "i"), xmlDecl = re("\\A<\\?xml\\b", ""), htmlTag = re("<\\s*html\\b", "");
    Pattern lasso = re("\\A.*?<\\?(lasso(script)?|=)", "");
    Pattern phpHh = re("^<\\?hh", ""), phpTag = re("^<\\?php", "");
    Pattern ps = re("^%!", "");
    Pattern racket = re("\\A#lang\\s*(.*?)$", ""), racketLang = re("racket|scribble", "");
    Pattern tex = re("\\A\\s*\\\\(documentclass|input|documentstyle|relax|ProvidesPackage|ProvidesClass)", "");
    Pattern xmlHtml = re("html", "");
    Pattern yaml = re("\\A\\s*%YAML", "m");

    shebang("Awk", "awk");
    d("BIML", t -> t.matches(biml));
    shebang("Coffeescript", "coffee");
    shebang("Crystal", "crystal");
    d("Diff", t -> t.startsWith("Index: ") || t.matches(diff1) || t.matches(diff2) || t.matches(diff3));
    shebang("Elixir", "elixir");
    shebang("Factor", "factor");
    shebang("Gherkin", "cucumber");
    d("Groovy", t -> t.shebangP("groovy", "") || t.shebangP("nextflow", ""));
    d("Hack", t -> t.matches(hack1) || t.shebangP("hhvm") || t.matches(hack2) || t.matches(hack3));
    shebang("Haxe", "haxe");
    shebang("Haskell", "runhaskell");
    d("HTML", t -> {
      if (t.doctypeP(htmlDt)) {
        return true;
      }
      if (t.matches(xmlDecl)) {
        return false;
      }
      return t.matches(htmlTag);
    });
    shebang("IO", "io");
    shebang("Javascript", "node", "jsc");
    shebang("Julia", "julia");
    d("Lasso", t -> t.shebangP("lasso9") || t.matches(lasso));
    shebang("Lua", "lua");
    shebang("Livescript", "lsc");
    d("Mojo", t -> t.shebangP("mojow?(?:[23](?:\\.\\d+)?)?", ""));
    shebang("Moonscript", "moon");
    d("PHP", t -> {
      if (t.shebangP("php")) {
        return true;
      }
      if (t.matches(phpHh)) {
        return false;
      }
      return t.matches(phpTag);
    });
    shebang("Perl", "perl");
    shebang("Puppet", "puppet-apply", "puppet");
    d("PostScript", t -> t.matches(ps));
    shebang("R", "Rscript");
    d("Python", t -> t.shebangP("pythonw?(?:[23](?:\\.\\d+)?)?", ""));
    shebang("Praat", "praat");
    shebang("Rust", "rustc");
    d("Racket", t -> {
      java.util.regex.Matcher m = racket.matcher(t.text);
      String lang = m.find() ? m.group(1) : null;
      if (lang == null) {
        return false;
      }
      return racketLang.matcher(lang).find();
    });
    shebang("Ruby", "ruby");
    shebang("Sed", "sed");
    d("Shell", t -> t.shebangP("(ba|z|k)?sh", "") || t.startsWith("#compdef", "#autoload"));
    d("TeX", t -> t.matches(tex));
    shebang("TCL", "tclsh", "wish", "jimsh");
    shebang("Tulip", "tulip");
    d("XML", t -> {
      if (t.doctypeP(xmlHtml)) {
        return false;
      }
      if (t.matches(xmlDecl)) {
        return true;
      }
      return t.doctypeP();
    });
    d("YAML", t -> t.matches(yaml));
  }
}
