package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RRegex;
import org.hl7.fhir.jekyll.rouge.RegexLexer;
import org.hl7.fhir.jekyll.rouge.RubyStrings;
import org.hl7.fhir.jekyll.rouge.Token;

/**
 * Port of rouge-4.7.0/lib/rouge/lexers/ruby.rb (detect? is in Detectors). Not used by IGs, but it
 * is the language of kramdown's and Jekyll's own highlighting tests.
 */
public class Ruby extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("Ruby");
  static final Definition DEF = new Definition(null);

  /** @heredoc_queue: [tolerant, name] */
  final List<Object[]> heredocQueue = new ArrayList<>();

  /** Ruby String#rstrip (whitespace and NUL) */
  static String rstrip(String s) {
    int b = s.length();
    while (b > 0) {
      char c = s.charAt(b - 1);
      if (c == ' ' || c == '\t' || c == '\n' || c == 0x0B || c == '\f' || c == '\r' || c == 0) {
        b--;
      } else {
        break;
      }
    }
    return s.substring(0, b);
  }

  static {
    DEF.state("symbols", s -> {
      // symbols
      s.rule(re("\n"
          + "          :  # initial :\n"
          + "          @{0,2} # optional ivar, for :@foo and :@@foo\n"
          + "          [\\p{Ll}_]\\p{Word}*[!?]? # the symbol\n"
          + "        ", "xi"), Str_Symbol);

      // special symbols
      s.rule(":(?:\\*\\*|[-+]@|[/\\%&\\|^`~]|\\[\\]=?|<<|>>|<=?>|<=?|===?)",
          Str_Symbol);

      s.rule(":'(\\\\\\\\|\\\\'|[^'])*'", Str_Symbol);
      s.rule(":\"", Str_Symbol, "simple_sym");
    });

    DEF.state("sigil_strings", s -> {
      // %-sigiled strings
      // %(abc), %[abc], %<abc>, %.abc., %r.abc., etc
      s.rule("%([rqswQWxiI])?([^\\p{Word}\\s])", (l, m) -> {
        String d = m.get(2);
        String closeDelim = d.equals("{") ? "}" : d.equals("[") ? "]" : d.equals("(") ? ")" : d.equals("<") ? ">" : d;
        String open = RRegex.escape(d);
        String close = RRegex.escape(closeDelim);
        boolean interp = m.get(1) == null || "rQWxI".contains(m.get(1));
        Token toktype = m.get(1) != null && m.get(1).equals("r") ? Str_Regex : Str_Other;

        // regexes
        if ("r".equals(m.get(1))) {
          l.push("regex_flags");
        }

        l.token(toktype);

        l.push(st -> {
          String uniqChars = open.equals(close) ? open : open + close;
          if (open.equals(close) && open.equals("\\#")) {
            uniqChars = "";
          }
          st.rule("\\\\[#" + uniqChars + "\\\\]", Str_Escape);
          // nesting rules only with asymmetric delimiters
          if (!open.equals(close)) {
            st.rule(open, (l2, m2) -> {
              l2.token(toktype);
              l2.push();
            });
          }
          st.rule(close, toktype, POP);

          if (interp) {
            st.mixin("string_intp_escaped");
            st.rule("#", toktype);
          } else {
            st.rule("[\\\\#]", toktype);
          }

          st.rule(re("[^#" + uniqChars + "\\\\]+", "m"), toktype);
        });
      });
    });

    DEF.state("strings", s -> {
      s.mixin("symbols");
      s.rule("\\b[\\p{Ll}_]\\p{Word}*?[?!]?:\\s+", Str_Symbol, "expr_start");
      s.rule("'(\\\\\\\\|\\\\'|[^'])*'", Str_Single);
      s.rule("\"", Str_Double, "simple_string");
      s.rule("(?<!\\.)`", Str_Backtick, "simple_backtick");
    });

    DEF.state("regex_flags", s -> {
      s.rule("[mixounse]*", Str_Regex, POP);
    });

    // double-quoted string and symbol
    Object[][] simple = {{"string", Str_Double, "\""}, {"sym", Str_Symbol, "\""}, {"backtick", Str_Backtick, "`"}};
    for (Object[] sp : simple) {
      String name = (String) sp[0];
      Token tok = (Token) sp[1];
      String fin = (String) sp[2];
      DEF.state("simple_" + name, s -> {
        s.mixin("string_intp_escaped");
        s.rule(re("[^\\\\" + fin + "#]+", "m"), tok);
        s.rule("[\\\\#]", tok);
        s.rule(fin, tok, POP);
      });
    }

    final String[] keywords = Words.w(
        "BEGIN END alias begin break case defined\\? do else elsif end"
        + " ensure for if in next redo rescue raise retry return super then"
        + " undef unless until when while yield");

    final String[] keywordsPseudo = Words.w(
        "loop include extend raise"
        + " alias_method attr catch throw private module_function"
        + " public protected true false nil __FILE__ __LINE__");

    final String[] builtinsG = Words.w(
        "attr_reader attr_writer attr_accessor"
        + " __id__ __send__ abort ancestors at_exit autoload binding callcc"
        + " caller catch chomp chop class_eval class_variables clone"
        + " const_defined\\? const_get const_missing const_set constants"
        + " display dup eval exec exit extend fail fork format freeze"
        + " getc gets global_variables gsub hash id included_modules"
        + " inspect instance_eval instance_method instance_methods"
        + " instance_variable_get instance_variable_set instance_variables"
        + " lambda load local_variables loop method method_missing"
        + " methods module_eval name object_id open p print printf"
        + " private_class_method private_instance_methods private_methods proc"
        + " protected_instance_methods protected_methods public_class_method"
        + " public_instance_methods public_methods putc puts raise rand"
        + " readline readlines require require_relative scan select self send set_trace_func"
        + " singleton_methods sleep split sprintf srand sub syscall system"
        + " taint test throw to_a to_s trace_var trap untaint untrace_var warn");

    final String[] builtinsQ = Words.w(
        "autoload block_given const_defined eql equal frozen"
        + " include instance_of is_a iterator kind_of method_defined"
        + " nil private_method_defined protected_method_defined"
        + " public_method_defined respond_to tainted");

    final String[] builtinsB = Words.w("chomp chop exit gsub sub");

    // Rouge loads ruby.rb twice (once from the lexers/*.rb glob, once via `load_lexer 'ruby.rb'` from
    // erb.rb/haml.rb/..., keyed differently), so this start block is registered twice and every lex
    // starts with the stack [:root, :expr_start, :expr_start]. Reproduced as is.
    for (int load = 0; load < 2; load++) {
      DEF.start(l -> {
        l.push("expr_start");
        ((Ruby) l).heredocQueue.clear();
      });
    }

    DEF.state("whitespace", s -> {
      s.mixin("inline_whitespace");
      s.rule(re("\\n\\s*", "m"), Text, "expr_start");
      s.rule("#.*$", Comment_Single);

      s.rule(re("=begin\\b.*?\\n=end\\b", "m"), Comment_Multiline);
    });

    DEF.state("inline_whitespace", s -> {
      s.rule("[ \\t\\r]+", Text);
    });

    DEF.state("root", s -> {
      s.mixin("whitespace");
      s.rule("__END__", Comment_Preproc, "end_part");

      s.rule("0_?[0-7]+(?:_[0-7]+)*", Num_Oct);
      s.rule("0x[0-9A-Fa-f]+(?:_[0-9A-Fa-f]+)*", Num_Hex);
      s.rule("0b[01]+(?:_[01]+)*", Num_Bin);

      R decimal = re("[\\d]+(?:_\\d+)*");
      R exp = re("e[+-]?\\d+", "i");
      s.rule(decimal + "(?:\\." + decimal + "" + exp + "?|" + exp + ")", Num_Float);
      s.rule(decimal, Num_Integer);

      // names
      s.rule(re("@@[\\p{Ll}_]\\p{Word}*", "i"), Name_Variable_Class);
      s.rule(re("@[\\p{Ll}_]\\p{Word}*", "i"), Name_Variable_Instance);
      s.rule("\\$\\p{Word}+", Name_Variable_Global);
      s.rule("\\$[!@&`'+~=/\\\\,;.<>_*\\$?:\"]", Name_Variable_Global);
      s.rule("\\$-[0adFiIlpvw]", Name_Variable_Global);
      s.rule("::", Operator);

      s.mixin("strings");

      s.rule("(?:" + String.join("|", keywords) + ")(?=\\W|$)", Keyword, "expr_start");
      s.rule("(?:" + String.join("|", keywordsPseudo) + ")\\b", Keyword_Pseudo, "expr_start");
      s.rule("(not|and|or)\\b", Operator_Word, "expr_start");

      s.rule(re("\n"
          + "          (module)\n"
          + "          (\\s+)\n"
          + "          ([\\p{L}_][\\p{L}0-9_]*(::[\\p{L}_][\\p{L}0-9_]*)*)\n"
          + "        ", "x"), (l, m) -> {
        l.groups(Keyword, Text, Name_Namespace);
      });

      s.rule("(def\\b)(\\s*)", (l, m) -> {
        l.groups(Keyword, Text);
        l.push("funcname");
      });

      s.rule("(class\\b)(\\s*)", (l, m) -> {
        l.groups(Keyword, Text);
        l.push("classname");
      });

      s.rule("(?:" + String.join("|", builtinsQ) + ")[?]", Name_Builtin, "expr_start");
      s.rule("(?:" + String.join("|", builtinsB) + ")!", Name_Builtin, "expr_start");
      s.rule("(?<!\\.)(?:" + String.join("|", builtinsG) + ")\\b",
          Name_Builtin, "method_call");

      s.mixin("has_heredocs");

      // `..` and `...` for ranges must have higher priority than `.`
      // Otherwise, they will be parsed as :method_call
      s.rule("\\.{2,3}", Operator, "expr_start");

      s.rule("[\\p{Lu}][\\p{L}0-9_]*", Name_Constant, "method_call");
      s.rule("(\\.|::)(\\s*)([\\p{Ll}_]\\p{Word}*[!?]?|[*%&^`~+-\\/\\[<>=])", (l, m) -> {
        l.groups(Punctuation, Text, Name_Function);
        l.push("method_call");
      });

      s.rule("[\\p{L}_]\\p{Word}*[?!]", Name, "expr_start");
      s.rule("[\\p{L}_]\\p{Word}*", Name, "method_call");
      s.rule("\\*\\*|<<?|>>?|>=|<=|<=>|=~|={3}|!~|&&?|\\|\\||\\.",
          Operator, "expr_start");
      s.rule("[-+\\/*%=<>&!^|~]=?", Operator, "expr_start");
      s.rule("[?]", (l, m) -> {
        l.token(Punctuation);
        l.push("ternary");
        l.push("expr_start");
      });
      s.rule("[\\[({,:\\\\;/]", Punctuation, "expr_start");
      s.rule("[\\])}]", Punctuation);
    });

    DEF.state("has_heredocs", s -> {
      s.rule("(?<!\\p{Word})(<<[-~]?)([\"`']?)([\\p{L}_]\\p{Word}*)(\\2)", (l, m) -> {
        Ruby r = (Ruby) l;
        l.token(Operator, m.get(1));
        l.token(Name_Constant, nz(m.get(2)) + nz(m.get(3)) + nz(m.get(4)));
        r.heredocQueue.add(new Object[] {m.get(1).equals("<<-") || m.get(1).equals("<<~"), m.get(3)});
        if (!l.stateP("heredoc_queue")) {
          l.push("heredoc_queue");
        }
      });

      s.rule("(<<[-~]?)([\"'])(\\2)", (l, m) -> {
        Ruby r = (Ruby) l;
        l.token(Operator, m.get(1));
        l.token(Name_Constant, nz(m.get(2)) + nz(m.get(3)) + nz(m.get(4)));
        r.heredocQueue.add(new Object[] {m.get(1).equals("<<-") || m.get(1).equals("<<~"), ""});
        if (!l.stateP("heredoc_queue")) {
          l.push("heredoc_queue");
        }
      });
    });

    DEF.state("heredoc_queue", s -> {
      s.rule("(?=\\n)", (l, m) -> {
        l.gotoState("resolve_heredocs");
      });

      s.mixin("root");
    });

    DEF.state("resolve_heredocs", s -> {
      s.mixin("string_intp_escaped");

      s.rule("\\n", Str_Heredoc, "test_heredoc");
      s.rule("[#\\\\\\n]", Str_Heredoc);
      s.rule("[^#\\\\\\n]+", Str_Heredoc);
    });

    DEF.state("test_heredoc", s -> {
      s.rule("[^#\\\\\\n]*$", (l, m) -> {
        Ruby r = (Ruby) l;
        if (r.heredocQueue.isEmpty()) {
          // `tolerant, heredoc_name = nil`: check == nil is false
          l.token(Str_Heredoc);
          l.pop();
          return;
        }
        Object[] first = r.heredocQueue.get(0);
        boolean tolerant = (Boolean) first[0];
        String heredocName = (String) first[1];
        String check = tolerant ? RubyStrings.strip(m.get(0)) : rstrip(m.get(0));

        // check if we found the end of the heredoc
        if (check.equals(heredocName)) {
          r.heredocQueue.remove(0);
          // if there's no more, we're done looking.
          if (r.heredocQueue.isEmpty()) {
            l.pop();
          }
          l.token(Name_Constant);
        } else {
          l.token(Str_Heredoc);
        }

        l.pop();
      });

      s.rule("", (l, m) -> l.pop());
    });

    DEF.state("funcname", s -> {
      s.rule("\\s+", Text);
      s.rule("\\(", Punctuation, "defexpr");
      s.rule(re("\n"
          + "          (?:([\\p{L}_]\\p{Word}*)(\\.))?\n"
          + "          (\n"
          + "            [\\p{L}_]\\p{Word}*[!?]? |\n"
          + "            \\*\\*? | [-+]@? | [/%&\\|^`~] | \\[\\]=? |\n"
          + "            <=>? | <<? | >>? | >= | ===?\n"
          + "          )\n"
          + "        ", "x"), (l, m) -> {
        l.groups(Name_Class, Operator, Name_Function);
        l.pop();
      });

      s.rule("", (l, m) -> l.pop());
    });

    DEF.state("classname", s -> {
      s.rule("\\s+", Text);
      s.rule("\\p{Word}+(::\\p{Word}+)+", Name_Class);

      s.rule("\\(", (l, m) -> {
        l.token(Punctuation);
        l.push("defexpr");
        l.push("expr_start");
      });

      // class << expr
      s.rule("<<", (l, m) -> {
        l.token(Operator);
        l.gotoState("expr_start");
      });

      s.rule("[\\p{Lu}_]\\p{Word}*", Name_Class, POP);

      s.rule("", (l, m) -> l.pop());
    });

    DEF.state("ternary", s -> {
      s.rule("(:)(\\s+)", (l, m) -> {
        l.groups(Punctuation, Text);
        l.gotoState("expr_start");
      });

      s.rule(":(?![^#\\n]*?[:\\\\])", (l, m) -> {
        l.token(Punctuation);
        l.gotoState("expr_start");
      });

      s.mixin("root");
    });

    DEF.state("defexpr", s -> {
      s.rule("(\\))(\\.|::)?", (l, m) -> {
        l.groups(Punctuation, Operator);
        l.pop();
      });
      s.rule("\\(", (l, m) -> {
        l.token(Punctuation);
        l.push("defexpr");
        l.push("expr_start");
      });

      s.mixin("root");
    });

    DEF.state("in_interp", s -> {
      s.rule("}", Str_Interpol, POP);
      s.mixin("root");
    });

    DEF.state("string_intp", s -> {
      s.rule("[#][{]", Str_Interpol, "in_interp");
      s.rule(re("#(@@?|\\$)[\\p{Ll}_]\\p{Word}*", "i"), Str_Interpol);
    });

    DEF.state("string_intp_escaped", s -> {
      s.mixin("string_intp");
      s.rule("\\\\([\\\\abefnrstv#\"']|x[a-fA-F0-9]{1,2}|[0-7]{1,3})",
          Str_Escape);
      s.rule("\\\\.", Str_Escape);
    });

    DEF.state("method_call", s -> {
      s.rule("/|%", (l, m) -> {
        l.token(Operator);
        l.gotoState("expr_start");
      });

      s.rule("(?=\\n)", (l, m) -> l.pop());

      s.rule("", (l, m) -> l.gotoState("method_call_spaced"));
    });

    DEF.state("method_call_spaced", s -> {
      s.mixin("whitespace");

      s.rule("[%/]=", (l, m) -> {
        l.token(Operator);
        l.gotoState("expr_start");
      });

      s.rule("(/)(?=\\S|\\s*/)", (l, m) -> {
        l.token(Str_Regex);
        l.gotoState("slash_regex");
      });

      s.mixin("sigil_strings");

      s.rule("(?=\\s*/)", (l, m) -> l.pop());

      s.rule("\\s+", (l, m) -> {
        l.token(Text);
        l.gotoState("expr_start");
      });
      s.rule("", (l, m) -> l.pop());
    });

    DEF.state("expr_start", s -> {
      s.mixin("inline_whitespace");

      s.rule("/", (l, m) -> {
        l.token(Str_Regex);
        l.gotoState("slash_regex");
      });

      // char operator.  ?x evaulates to "x", unless there's a digit
      // beforehand like x>=0?n[x]:""
      s.rule(re("\n"
          + "          [?](\\\\[MC]-)*     # modifiers\n"
          + "          (\\\\([\\\\abefnrstv\\#\"']|x[a-fA-F0-9]{1,2}|[0-7]{1,3})|\\S)\n"
          + "          (?!\\p{Word})\n"
          + "        ", "x"), Str_Char, POP);

      // special case for using a single space.  Ruby demands that
      // these be in a single line, otherwise it would make no sense.
      s.rule("(\\s*)(%[rqswQWxiI]? \\S* )", (l, m) -> {
        l.groups(Text, Str_Other);
        l.pop();
      });

      s.mixin("sigil_strings");

      s.rule("", (l, m) -> l.pop());
    });

    DEF.state("slash_regex", s -> {
      s.mixin("string_intp");
      s.rule("\\\\\\\\", Str_Regex);
      s.rule("\\\\/", Str_Regex);
      s.rule("[\\\\#]", Str_Regex);
      s.rule(re("[^\\\\/#]+", "m"), Str_Regex);
      s.rule("/", (l, m) -> {
        l.token(Str_Regex);
        l.gotoState("regex_flags");
      });
    });

    DEF.state("end_part", s -> {
      // eat up the rest of the stream as Comment::Preproc
      s.rule(re(".+", "m"), Comment_Preproc, POP);
    });
  }

  static String nz(String s) {
    return s == null ? "" : s;
  }

  public Ruby(Map<String, ?> opts) {
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
