package org.hl7.fhir.jekyll.rouge.lexers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.jekyll.rouge.Lexer;
import org.hl7.fhir.jekyll.rouge.LexerClass;

/**
 * Port of rouge-4.7.0/lib/rouge/lexers/typescript.rb and typescript/common.rb (TypescriptCommon,
 * whose <code>self.extended(base)</code> prepends rules to :root and :statement).
 */
public class Typescript extends Javascript {
  public static final LexerClass CLASS = Lexer.byClassName("Typescript");
  static final Definition DEF = new Definition(Javascript.DEF);

  static final Set<String> KEYWORDS = union(Javascript.KEYWORDS, Set.of(
      "is", "namespace", "static", "private", "protected", "public",
      "implements", "readonly"));

  static final Set<String> DECLARATIONS = union(Javascript.DECLARATIONS, Set.of(
      "type", "abstract"));

  static final Set<String> RESERVED = union(Javascript.RESERVED, Set.of(
      "string", "any", "void", "number", "namespace", "module",
      "declare", "default", "interface", "keyof"));

  static final List<String> BUILTINS = concat(Javascript.BUILTINS, List.of(
      "Capitalize", "ConstructorParameters", "Exclude", "Extract", "InstanceType",
      "Lowercase", "NonNullable", "Omit", "OmitThisParameter", "Parameters",
      "Partial", "Pick", "Readonly", "Record", "Required",
      "ReturnType", "ThisParameterType", "ThisType", "Uncapitalize", "Uppercase"));

  static Set<String> union(Set<String> a, Set<String> b) {
    Set<String> s = new LinkedHashSet<>(a);
    s.addAll(b);
    return s;
  }

  static List<String> concat(List<String> a, List<String> b) {
    List<String> l = new ArrayList<>(a);
    l.addAll(b);
    return l;
  }

  @Override
  public Set<String> keywords() {
    return KEYWORDS;
  }

  @Override
  public Set<String> declarations() {
    return DECLARATIONS;
  }

  @Override
  public Set<String> reserved() {
    return RESERVED;
  }

  @Override
  public List<String> builtins() {
    return BUILTINS;
  }

  /** TypescriptCommon.extended(base) */
  static void extended(Definition base) {
    base.prepend("root", s -> {
      s.rule("[?][.]", Punctuation);
      s.rule("[?]{2}", Operator);

      // Positive Examples:
      // const cat = { name: "Garfield" } satisfies Person;
      // import {something as thingy} from 'module'
      // export { foo as default }
      // ...spreadOperator as const;
      // Negative Example:
      // cy.get('kitten').as('friend')
      s.rule("(?<![_$[:alnum:]])(?:(?<=\\.\\.\\.)|(?<!\\.))(?:(as)|(satisfies))\\s+", Keyword_Declaration);
    });

    base.prepend("statement", s -> {
      s.rule("(" + Javascript.idRegex() + ")(\\??)(\\s*)(:)", (l, m) -> {
        l.groups(Name_Label, Punctuation, Text, Punctuation);
        l.push("expr_start");
      });
    });
  }

  static {
    extended(DEF);
  }

  public Typescript(Map<String, ?> opts) {
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
