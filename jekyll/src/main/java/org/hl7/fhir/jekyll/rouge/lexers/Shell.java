package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;

/** Port of rouge-4.7.0/lib/rouge/lexers/shell.rb (detect? is in Detectors) */
public class Shell extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("Shell");
  static final Definition DEF = new Definition(null);

  static final String KEYWORDS = String.join("|", Words.w("if fi else while do done for then return function select continue until esac elif in"));

  static final String BUILTINS = String.join("|", Words.w("alias bg bind break builtin caller cd command compgen "
      + " complete declare dirs disown enable eval exec exit "
      + " export false fc fg getopts hash help history jobs let "
      + " local logout mapfile popd pushd pwd read readonly set "
      + " shift shopt source suspend test time times trap true type "
      + " typeset ulimit umask unalias unset wait "
      + "  "
      + " cat tac nl od base32 base64 fmt pr fold head tail split csplit "
      + " wc sum cksum b2sum md5sum sha1sum sha224sum sha256sum sha384sum "
      + " sha512sum sort shuf uniq comm ptx tsort cut paste join tr expand "
      + " unexpand ls dir vdir dircolors cp dd install mv rm shred link ln "
      + " mkdir mkfifo mknod readlink rmdir unlink chown chgrp chmod touch "
      + " df du stat sync truncate echo printf yes expr tee basename dirname "
      + " pathchk mktemp realpath pwd stty printenv tty id logname whoami "
      + " groups users who date arch nproc uname hostname hostid uptime chcon "
      + " runcon chroot env nice nohup stdbuf timeout kill sleep factor numfmt "
      + " seq tar grep sudo awk sed gzip gunzip"));

  String heredocstr;

  static {
    DEF.state("basic", s -> {
      s.rule("#.*$", Comment);

      s.rule("\\b(" + KEYWORDS + ")\\s*\\b", Keyword);
      s.rule("\\bcase\\b", Keyword, "case");

      s.rule("\\b(" + BUILTINS + ")\\s*\\b(?!(\\.|-))", Name_Builtin);
      s.rule("[.](?=\\s)", Name_Builtin);

      s.rule("(\\b\\w+)(=)", (l, m) -> {
        l.groups(Name_Variable, Operator);
      });

      s.rule("[\\[\\]{}()!=>]", Operator);
      s.rule("&&|\\|\\|", Operator);

      // here-string
      s.rule("<<<", Operator);

      s.rule("(<<-?)(\\s*)(['\"]?)(\\\\?)(\\w+)(\\3)", (l, m) -> {
        l.groups(Operator, Text, Str_Heredoc, Str_Heredoc, Name_Constant, Str_Heredoc);
        // Regexp.escape of a \w+ word is the word itself
        ((Shell) l).heredocstr = m.get(5);
        l.push("heredoc");
      });
    });

    DEF.state("heredoc", s -> {
      s.rule("\\n", Str_Heredoc, "heredoc_nl");
      s.rule("[^$\\n\\\\]+", Str_Heredoc);
      s.mixin("interp");
      s.rule("[$]", Str_Heredoc);
    });

    DEF.state("heredoc_nl", s -> {
      s.rule("\\s*(\\w+)\\s*\\n", (l, m) -> {
        if (m.get(1).equals(((Shell) l).heredocstr)) {
          l.token(Name_Constant);
          l.pop(2);
        } else {
          l.token(Str_Heredoc);
        }
      });

      s.rule("", (l, m) -> l.pop());
    });


    DEF.state("double_quotes", s -> {
      // NB: "abc$" is literally the string abc$.
      // Here we prevent :interp from interpreting $" as a variable.
      s.rule("(?:\\$#?)?\"", Str_Double, POP);
      s.mixin("interp");
      s.rule("[^\"`\\\\$]+", Str_Double);
    });

    DEF.state("ansi_string", s -> {
      s.rule("\\\\.", Str_Escape);
      s.rule("[^\\\\']+", Str_Single);
      s.mixin("single_quotes");
    });

    DEF.state("single_quotes", s -> {
      s.rule("'", Str_Single, POP);
      s.rule("[^']+", Str_Single);
    });

    DEF.state("data", s -> {
      s.rule("\\s+", Text);
      s.rule("\\\\.", Str_Escape);
      s.rule("\\$?\"", Str_Double, "double_quotes");
      s.rule("\\$'", Str_Single, "ansi_string");

      // single quotes are much easier than double quotes - we can
      // literally just scan until the next single quote.
      // POSIX: Enclosing characters in single-quotes ( '' )
      // shall preserve the literal value of each character within the
      // single-quotes. A single-quote cannot occur within single-quotes.
      s.rule("'", Str_Single, "single_quotes");

      s.rule("\\*", Keyword);

      s.rule(";", Punctuation);

      s.rule("--?[\\w-]+", Name_Tag);
      s.rule("[^=\\*\\s{}()$\"'`;\\\\<]+", Text);
      s.rule("\\d+(?= |\\Z)", Num);
      s.rule("<", Text);
      s.mixin("interp");
    });

    DEF.state("curly", s -> {
      s.rule("}", Keyword, POP);
      s.rule(":-", Keyword);
      s.rule("[a-zA-Z0-9_]+", Name_Variable);
      s.rule("[^}:\"`'$]+", Punctuation);
      s.mixin("root");
    });

    // the state inside $(...)
    DEF.state("paren_interp", s -> {
      s.rule("\\)", Str_Interpol, POP);
      s.rule("\\(", Operator, "paren_inner");
      s.mixin("root");
    });

    // used to balance parentheses inside interpolation
    DEF.state("paren_inner", s -> {
      s.rule("\\(", Operator, PUSH);
      s.rule("\\)", Operator, POP);
      s.mixin("root");
    });

    DEF.state("math", s -> {
      s.rule("\\)\\)", Keyword, POP);
      s.rule("[-+*/%^|&!]|\\*\\*|\\|\\|", Operator);
      s.rule("\\d+(#\\w+)?", Num);
      s.mixin("root");
    });

    DEF.state("case", s -> {
      s.rule("\\besac\\b", Keyword, POP);
      s.rule("\\|", Punctuation);
      s.rule("\\)", Punctuation, "case_stanza");
      s.mixin("root");
    });

    DEF.state("case_stanza", s -> {
      s.rule(";;", Punctuation, POP);
      s.mixin("root");
    });

    DEF.state("backticks", s -> {
      s.rule("`", Str_Backtick, POP);
      s.mixin("root");
    });

    DEF.state("interp", s -> {
      s.rule("\\\\$", Str_Escape); // line continuation
      s.rule("\\\\.", Str_Escape);
      s.rule("\\$\\(\\(", Keyword, "math");
      s.rule("\\$\\(", Str_Interpol, "paren_interp");
      s.rule("\\${#?", Keyword, "curly");
      s.rule("`", Str_Backtick, "backticks");
      s.rule("\\$#?(\\w+|.)", Name_Variable);
      s.rule("\\$[*@]", Name_Variable);
    });

    DEF.state("root", s -> {
      s.mixin("basic");
      s.mixin("data");
    });
  }

  public Shell(Map<String, ?> opts) {
    super(opts);
  }

  @Override
  protected Definition definition() {
    return DEF;
  }

  @Override
  public LexerClass lexerClass() {
    return CLASS;
  }
}
