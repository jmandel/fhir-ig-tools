package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;
import org.hl7.fhir.jekyll.rouge.RougeError;
import org.hl7.fhir.jekyll.rouge.RubyStrings;

/** Port of rouge-4.7.0/lib/rouge/lexers/python.rb (detect? is in Detectors) */
public class Python extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("Python");
  static final Definition DEF = new Definition(null);

  static final List<String> KEYWORDS = List.of(Words.w(
      "assert break continue del elif else except exec"
      + " finally for global if lambda pass print raise"
      + " return try while yield as with from import"
      + " async await nonlocal"));

  static final List<String> BUILTINS = List.of(Words.w(
      "__import__ abs aiter all anext any apply ascii"
      + " basestring bin bool buffer breakpoint bytearray bytes"
      + " callable chr classmethod cmp coerce compile complex"
      + " delattr dict dir divmod enumerate eval exec execfile exit"
      + " file filter float format frozenset getattr globals"
      + " hasattr hash help hex"
      + " id input int intern isinstance issubclass iter len list locals long"
      + " map max memoryview min next object oct open ord pow print property"
      + " range raw_input reduce reload repr reversed round set setattr slice"
      + " sorted staticmethod str sum super tuple type unichr unicode vars"
      + " xrange zip"));

  static final List<String> BUILTINS_PSEUDO = List.of(Words.w("None Ellipsis NotImplemented False True"));

  static final List<String> EXCEPTIONS = List.of(Words.w(
      "ArithmeticError AssertionError AttributeError BaseException"
      + " BaseExceptionGroup BlockingIOError BrokenPipeError BufferError"
      + " BytesWarning ChildProcessError ConnectionAbortedError ConnectionError"
      + " ConnectionRefusedError ConnectionResetError DeprecationWarning"
      + " EOFError EnvironmentError EncodingWarning Exception ExceptionGroup"
      + " FileExistsError FileNotFoundError FloatingPointError FutureWarning"
      + " GeneratorExit IOError ImportError ImportWarning IndentationError"
      + " IndexError InterruptedError IsADirectoryError"
      + " KeyError KeyboardInterrupt LookupError"
      + " MemoryError ModuleNotFoundError"
      + " NameError NotADirectoryError NotImplemented NotImplementedError"
      + " OSError OverflowError OverflowWarning PendingDeprecationWarning"
      + " PermissionError ProcessLookupError PythonFinalizationError"
      + " RecursionError ReferenceError ResourceWarning RuntimeError RuntimeWarning"
      + " StandardError StopAsyncIteration StopIteration SyntaxError SyntaxWarning"
      + " SystemError SystemExit TabError TimeoutError TypeError"
      + " UnboundLocalError UnicodeDecodeError UnicodeEncodeError UnicodeError"
      + " UnicodeTranslateError UnicodeWarning UserWarning ValueError VMSError"
      + " Warning WindowsError"
      + " ZeroDivisionError"));

  /** Python::StringRegister (an Array of [type, delim]) */
  static final class StringRegister extends ArrayList<String[]> {
    private String[] last() {
      if (isEmpty()) {
        throw new RougeError("NoMethodError: undefined method `[]' for nil:NilClass");
      }
      return get(size() - 1);
    }

    boolean delimP(String delim) {
      return last()[1].equals(delim);
    }

    void register(String type, String delim) {
      add(new String[] {type, delim});
    }

    void remove() {
      if (!isEmpty()) {
        remove(size() - 1);
      }
    }

    boolean typeP(String type) {
      return last()[0].contains(type);
    }
  }

  StringRegister stringRegister;

  StringRegister currentString() {
    if (stringRegister == null) {
      stringRegister = new StringRegister();
    }
    return stringRegister;
  }

  static {
    final R identifier = re("[[:alpha:]_][[:alnum:]_]*");
    final R dottedIdentifier = re("[[:alpha:]_.][[:alnum:]_.]*");

    DEF.state("root", s -> {
      s.rule(re("\\n+", "m"), Text);
      s.rule(re("^(:)(\\s*)([ru]{,2}\"\"\".*?\"\"\")", "mi"), (l, m) -> {
        l.groups(Punctuation, Text, Str_Doc);
      });

      s.rule("\\.\\.\\.\\B$", Name_Builtin_Pseudo);

      s.rule("[^\\S\\n]+", Text);
      s.rule("#(.*)?\\n?", Comment_Single);
      s.rule("[\\[\\]{}:(),;.]", Punctuation);
      s.rule("\\\\\\n", Text);
      s.rule("\\\\", Text);

      s.rule(re("@" + dottedIdentifier, "i"), Name_Decorator);

      s.rule("(in|is|and|or|not)\\b", Operator_Word);
      s.rule("(<<|>>|\\/\\/|\\*\\*)=?", Operator);
      s.rule("[-~+\\/*%=<>&^|@]=?|!=", Operator);

      s.rule("(from)((?:\\\\\\s|\\s)+)(" + dottedIdentifier + ")((?:\\\\\\s|\\s)+)(import)", (l, m) -> {
        l.groups(Keyword_Namespace,
            Text,
            Name,
            Text,
            Keyword_Namespace);
      });

      s.rule("(import)(\\s+)(" + dottedIdentifier + ")", (l, m) -> {
        l.groups(Keyword_Namespace, Text, Name);
      });

      s.rule("(def)((?:\\s|\\\\\\s)+)", (l, m) -> {
        l.groups(Keyword, Text);
        l.push("funcname");
      });

      s.rule("(class)((?:\\s|\\\\\\s)+)", (l, m) -> {
        l.groups(Keyword, Text);
        l.push("classname");
      });

      s.rule(re("([a-z_]\\w*)[ \\t]*(?=(\\(.*\\)))", "m"), Name_Function);
      s.rule(re("([A-Z_]\\w*)[ \\t]*(?=(\\(.*\\)))", "m"), Name_Class);

      // TODO: not in python 3
      s.rule("`.*?`", Str_Backtick);
      s.rule(re("([rtfbu]{0,2})('''|\"\"\"|['\"])", "i"), (l, m) -> {
        l.groups(Str_Affix, Str_Heredoc);
        ((Python) l).currentString().register(RubyStrings.downcase(m.get(1)), m.get(2));
        l.push("generic_string");
      });

      s.mixin("soft_keywords");

      // using negative lookbehind so we don't match property names
      s.rule("(?<!\\.)" + identifier, (l, m) -> {
        String v = m.get(0);
        if (KEYWORDS.contains(v)) {
          l.token(Keyword);
        } else if (EXCEPTIONS.contains(v)) {
          l.token(Name_Builtin);
        } else if (BUILTINS.contains(v)) {
          l.token(Name_Builtin);
        } else if (BUILTINS_PSEUDO.contains(v)) {
          l.token(Name_Builtin_Pseudo);
        } else {
          l.token(Name);
        }
      });

      s.rule(identifier, Name);

      R digits = re("[0-9](_?[0-9])*");
      R decimal = re("((" + digits + ")?\\." + digits + "|" + digits + "\\.)");
      R exponent = re("e[+-]?" + digits, "i");
      s.rule(re(decimal + "(" + exponent + ")?j?", "i"), Num_Float);
      s.rule(re(digits + "" + exponent + "j?", "i"), Num_Float);
      s.rule(re(digits + "j", "i"), Num_Float);

      s.rule(re("0b(_?[0-1])+", "i"), Num_Bin);
      s.rule(re("0o(_?[0-7])+", "i"), Num_Oct);
      s.rule(re("0x(_?[a-f0-9])+", "i"), Num_Hex);
      s.rule("\\d+L", Num_Integer_Long);
      s.rule("([1-9](_?[0-9])*|0(_?0)*)", Num_Integer);
    });

    DEF.state("funcname", s -> {
      s.rule(identifier, Name_Function, POP);
    });

    DEF.state("classname", s -> {
      s.rule(identifier, Name_Class, POP);
    });

    DEF.state("soft_keywords", s -> {
      s.rule(re("\n"
          + "          (^[ \\t]*)\n"
          + "          (match|case)\\b\n"
          + "          (?![ \\t]*\n"
          + "            (?:[:,;=^&|@~)\\]}] |\n"
          + "              (?:" + String.join("|", KEYWORDS) + ")\\b))\n"
          + "        ", "x"), (l, m) -> {
        l.token(Text_Whitespace, m.get(1));
        l.token(Keyword, m.get(2));
        l.push("soft_keywords_inner");
      });
    });

    DEF.state("soft_keywords_inner", s -> {
      s.rule("(\\s+)([^\\n_]*)(_\\b)", (l, m) -> {
        l.groups(Text_Whitespace, Text, Keyword);
      });

      s.rule("", (l, m) -> l.pop());
    });

    DEF.state("raise", s -> {
      s.rule("from\\b", Keyword);
      s.rule("raise\\b", Keyword);
      s.rule("yield\\b", Keyword);
      s.rule("\\n", Text, POP);
      s.rule(";", Punctuation, POP);
      s.mixin("root");
    });

    DEF.state("yield", s -> {
      s.mixin("raise");
    });

    DEF.state("generic_string", s -> {
      s.rule("^\\s*(>>>|\\.\\.\\.)\\B", Generic_Prompt, "doctest");
      s.rule("[^'\"\\\\{]+?", Str);
      s.rule("{{", Str);

      s.rule("'''|\"\"\"|['\"]", (l, m) -> {
        l.token(Str_Heredoc);
        StringRegister cs = ((Python) l).currentString();
        if (cs.delimP(m.get(0))) {
          cs.remove();
          l.pop();
        }
      });

      s.rule("(?=\\\\)", Str, "generic_escape");

      s.rule("{", (l, m) -> {
        if (((Python) l).currentString().typeP("f")) {
          l.token(Str_Interpol);
          l.push("generic_interpol");
        } else {
          l.token(Str);
        }
      });
    });

    DEF.state("generic_escape", s -> {
      s.rule(re("\\\\\n"
          + "          ( [\\\\abfnrtv\"']\n"
          + "          | \\n\n"
          + "          | newline\n"
          + "          | N{[a-zA-Z][a-zA-Z ]+[a-zA-Z]}\n"
          + "          | u[a-fA-F0-9]{4}\n"
          + "          | U[a-fA-F0-9]{8}\n"
          + "          | x[a-fA-F0-9]{2}\n"
          + "          | [0-7]{1,3}\n"
          + "          )\n"
          + "        ", "x"), (l, m) -> {
        if (((Python) l).currentString().typeP("r")) {
          l.token(Str);
        } else {
          l.token(Str_Escape);
        }
        l.pop();
      });

      s.rule("\\\\.", Str, POP);
    });

    DEF.state("doctest", s -> {
      s.rule("\\n\\n", Text, POP);

      s.rule("'''|\"\"\"", (l, m) -> {
        l.token(Str_Heredoc);
        if (l.inState("generic_string")) {
          l.pop(2); // pop :doctest and :generic_string
        }
      });

      s.mixin("root");
    });

    DEF.state("generic_interpol", s -> {
      s.rule("[^{}!:]+", (l, m) -> {
        l.recurse(m.get(0));
      });
      s.rule("![asr]", Str_Interpol);
      s.rule(":", Str_Interpol);
      s.rule("{", Str_Interpol, "generic_interpol");
      s.rule("}", Str_Interpol, POP);
    });
  }

  public Python(Map<String, ?> opts) {
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
