package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.Map;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;
import org.hl7.fhir.jekyll.rouge.RegexLexer;

/** Port of rouge-4.7.0/lib/rouge/lexers/java.rb */
public class Java extends RegexLexer {
  public static final LexerClass CLASS = Lexer.byClassName("Java");
  static final Definition DEF = new Definition(null);

  /** the method-signature rule's regex (java.rb), see {@link JavaSignaturePrecheck} */
  public static final R SIGNATURE = re("\n"
      + "          (\\s*(?:[a-zA-Z_][a-zA-Z0-9_.\\[\\]<>]*\\s+)+?) # return arguments\n"
      + "          ([a-zA-Z_][a-zA-Z0-9_]*)                  # method name\n"
      + "          (\\s*)(\\()                                 # signature start\n"
      + "        ", "mx");

  static {
    final String[] keywords = Words.w(
        "assert break case catch continue default do else finally for"
        + " if goto instanceof new return switch this throw try while");

    final String[] declarations = Words.w(
        "abstract const enum extends final implements native private protected"
        + " public static strictfp super synchronized throws transient volatile");

    final String[] types = Words.w("boolean byte char double float int long short var void");

    final R id = re("[[:alpha:]_][[:word:]]*");
    final R constName = re("[[:upper:]][[:upper:][:digit:]_]*\\b");
    final R className = re("[[:upper:]][[:alnum:]]*\\b");

    DEF.state("root", s -> {
      s.rule("[^\\S\\n]+", Text);
      s.rule("//.*?$", Comment_Single);
      s.rule(re("/\\*.*?\\*/", "m"), Comment_Multiline);
      // keywords: go before method names to avoid lexing "throw new XYZ"
      // as a method signature
      s.rule("(?:" + String.join("|", keywords) + ")\\b", Keyword);

      // JavaSignaturePrecheck: skips this regex where it provably cannot match (it is quadratic on
      // long identifier runs otherwise); the regex itself is unchanged.
      s.rule(SIGNATURE, JavaSignaturePrecheck.INSTANCE, (l, m) -> {
        // TODO: do this better, this shouldn't need a delegation
        l.delegate(CLASS, m.get(1));
        l.token(Name_Function, m.get(2));
        l.token(Text, m.get(3));
        l.token(Operator, m.get(4));
      });

      s.rule("@" + id, Name_Decorator);
      s.rule("(?:" + String.join("|", declarations) + ")\\b", Keyword_Declaration);
      s.rule("(?:" + String.join("|", types) + ")\\b", Keyword_Type);
      s.rule("(?:true|false|null)\\b", Keyword_Constant);
      s.rule("(?:class|interface|record)\\b", Keyword_Declaration, "class");
      s.rule("(?:import|package)\\b", Keyword_Namespace, "import");
      s.rule(re("\"\"\"\\s*\\n.*?(?<!\\\\)\"\"\"", "m"), Str_Heredoc);
      s.rule("\"(\\\\\\\\|\\\\\"|[^\"])*\"", Str);
      s.rule("'(?:\\\\.|[^\\\\]|\\\\u[0-9a-f]{4})'", Str_Char);
      s.rule("(\\.)(" + id + ")", (l, m) -> {
        l.groups(Operator, Name_Attribute);
      });

      s.rule(id + ":", Name_Label);
      s.rule(constName, Name_Constant);
      s.rule(className, Name_Class);
      s.rule("\\$?" + id, Name);
      s.rule("[~^*!%&\\[\\](){}<>\\|+=:;,.\\/?-]", Operator);

      R digit = re("[0-9]_+[0-9]|[0-9]");
      R binDigit = re("[01]_+[01]|[01]");
      R octDigit = re("[0-7]_+[0-7]|[0-7]");
      R hexDigit = re("[0-9a-f]_+[0-9a-f]|[0-9a-f]", "i");
      s.rule(digit + "+\\." + digit + "+([eE]" + digit + "+)?[fd]?", Num_Float);
      s.rule(re("0b" + binDigit + "+", "i"), Num_Bin);
      s.rule(re("0x" + hexDigit + "+", "i"), Num_Hex);
      s.rule("0" + octDigit + "+", Num_Oct);
      s.rule(digit + "+L?", Num_Integer);
      s.rule("\\n", Text);
    });

    DEF.state("class", s -> {
      s.rule(re("\\s+", "m"), Text);
      s.rule(id, Name_Class, POP);
    });

    DEF.state("import", s -> {
      s.rule(re("\\s+", "m"), Text);
      s.rule(re("[a-z0-9_.]+\\*?", "i"), Name_Namespace, POP);
    });
  }

  public Java(Map<String, ?> opts) {
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
