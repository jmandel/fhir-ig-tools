package org.hl7.fhir.jekyll.rouge;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of rouge-4.7.0/lib/rouge/regex_lexer.rb (Rouge::RegexLexer): a stateful lexer driven by
 * named states of (regex, callback) rules, with mixins, prepended/appended states, start procs,
 * and delegation to other lexers.
 *
 * <p>Ruby keeps the state definitions on the lexer class; here each lexer class has a static
 * {@link Definition} (inheriting its parent's definitions like Ruby's InheritableHash), returned by
 * {@link #definition()}. Rule blocks are Java lambdas {@code (l, m) -> ...} receiving the lexer
 * instance (Ruby's <code>instance_exec</code> self) and the current match.
 *
 * <p>Ruby's StringScanner is reproduced by {@link Scanner}: every scan matches at the current
 * position with the string conceptually starting there (strscan's default, non-fixed-anchor
 * mode), i.e. a Java matcher region with anchoring and opaque bounds.
 */
public abstract class RegexLexer extends Lexer {
  protected RegexLexer(Map<String, ?> opts) {
    super(opts);
  }

  /** The class-level state definitions of this lexer's class. */
  protected abstract Definition definition();

  // ================================================================== class-level definitions

  /** Next-state markers (Ruby <code>:pop!</code> / <code>:push</code>). */
  public static final String POP = ":pop!";
  public static final String PUSH = ":push";

  /** A Ruby regex literal: source + option letters. toString() is Regexp#to_s (for interpolation). */
  public static final class R {
    public final String source;
    public final String flags;
    /** an equivalent source used for matching (see {@link #eq}), or null */
    final String matchSource;
    private volatile Pattern pattern;

    public R(String source, String flags) {
      this(source, flags, null);
    }

    private R(String source, String flags, String matchSource) {
      this.source = source;
      this.flags = flags;
      this.matchSource = matchSource;
    }

    /**
     * The same regex, matched with an <em>equivalent</em> source (same matches, same groups used by
     * the rule) that java.util.regex runs without deep recursion, e.g. a lazy alternation loop whose
     * units tokenize deterministically rewritten with possessive loops. Each use carries its proof.
     */
    public R eq(String equivalentSource) {
      return new R(source, flags, equivalentSource);
    }

    public Pattern pattern() {
      Pattern p = pattern;
      if (p == null) {
        pattern = p = RRegex.compile(matchSource != null ? matchSource : source, flags);
      }
      return p;
    }

    @Override
    public String toString() {
      return RRegex.toS(source, flags);
    }
  }

  /** %r/source/ */
  public static R re(String source) {
    return new R(source, "");
  }

  /** %r/source/flags */
  public static R re(String source, String flags) {
    return new R(source, flags);
  }

  @FunctionalInterface
  public interface Callback {
    void call(RegexLexer l, Match m);
  }

  /**
   * A performance-only necessary condition for a rule's regex: when it returns false the regex
   * provably cannot match at <code>pos</code>, so the scan is skipped (same result, no regex run).
   * <code>cache</code> is per scanned string, for precomputed positions.
   */
  @FunctionalInterface
  public interface Precheck {
    boolean mayMatch(String str, int pos, Map<Object, Object> cache);
  }

  /** RegexLexer::Rule */
  public static final class Rule {
    public final R re;
    final Pattern pattern;
    final Callback callback;
    final boolean beginningOfLine;
    final Precheck precheck;

    Rule(R re, Callback callback) {
      this(re, callback, null);
    }

    Rule(R re, Callback callback, Precheck precheck) {
      this.re = re;
      this.pattern = re.pattern();
      this.callback = callback;
      this.precheck = precheck;
      this.beginningOfLine = !re.source.isEmpty() && re.source.charAt(0) == '^';
    }

    @Override
    public String toString() {
      return "#<Rule /" + re.source + "/" + re.flags + ">";
    }
  }

  /** RegexLexer::State: a named list of rules and mixed-in states. */
  public static final class State {
    public final String name;
    final Object[] rules; // Rule | State

    State(String name, Object[] rules) {
      this.name = name;
      this.rules = rules;
    }

    @Override
    public String toString() {
      return "#<State :" + name + ">";
    }
  }

  /** The receiver of a state block (Ruby's StateDSL instance_eval). */
  public static final class Rules {
    final List<Object> rules = new ArrayList<>(); // Rule | String (mixin)

    public void rule(R re, Token tok) {
      rules.add(new Rule(re, (l, m) -> l.output(tok, m.get(0))));
    }

    public void rule(String re, Token tok) {
      rule(re(re), tok);
    }

    /** rule(re, tok, next_state): next_state is a state name, {@link #POP} or {@link #PUSH}. */
    public void rule(R re, Token tok, String next) {
      Callback cb;
      if (next.equals(POP)) {
        cb = (l, m) -> {
          l.output(tok, m.get(0));
          if (l.stack.isEmpty()) {
            throw new RougeError("empty stack!");
          }
          l.stack.remove(l.stack.size() - 1);
        };
      } else if (next.equals(PUSH)) {
        cb = (l, m) -> {
          l.output(tok, m.get(0));
          l.stack.add(l.stack.get(l.stack.size() - 1));
        };
      } else {
        cb = (l, m) -> {
          l.output(tok, m.get(0));
          l.stack.add(l.getState(next));
        };
      }
      rules.add(new Rule(re, cb));
    }

    public void rule(String re, Token tok, String next) {
      rule(re(re), tok, next);
    }

    /** rule(re, tok, [states...]) */
    public void rule(R re, Token tok, String[] next) {
      rules.add(new Rule(re, (l, m) -> {
        l.output(tok, m.get(0));
        for (String n : next) {
          if (n.equals(POP)) {
            if (!l.stack.isEmpty()) {
              l.stack.remove(l.stack.size() - 1);
            }
          } else if (n.equals(PUSH)) {
            l.stack.add(l.stack.get(l.stack.size() - 1));
          } else {
            l.stack.add(l.getState(n));
          }
        }
      }));
    }

    public void rule(R re, Callback cb) {
      rules.add(new Rule(re, cb));
    }

    /** rule(re) { ... } with a performance-only {@link Precheck} (see its contract). */
    public void rule(R re, Precheck precheck, Callback cb) {
      rules.add(new Rule(re, cb, precheck));
    }

    public void rule(String re, Callback cb) {
      rule(re(re), cb);
    }

    /** mixin(state): the named state's rules are tried here first. */
    public void mixin(String state) {
      rules.add(state);
    }
  }

  /** RegexLexer::StateDSL: a state name and its definition blocks (prepend/append add blocks). */
  public static final class StateDSL {
    final String name;
    final List<Consumer<Rules>> defns;

    StateDSL(String name, List<Consumer<Rules>> defns) {
      this.name = name;
      this.defns = defns;
    }

    StateDSL prepended(Consumer<Rules> defn) {
      List<Consumer<Rules>> l = new ArrayList<>();
      l.add(defn);
      l.addAll(defns);
      return new StateDSL(name, l);
    }

    StateDSL appended(Consumer<Rules> defn) {
      List<Consumer<Rules>> l = new ArrayList<>(defns);
      l.add(defn);
      return new StateDSL(name, l);
    }

    State toState(Definition lexerClass) {
      Rules r = new Rules();
      for (Consumer<Rules> d : defns) {
        d.accept(r);
      }
      Object[] rules = new Object[r.rules.size()];
      for (int k = 0; k < rules.length; k++) {
        Object o = r.rules.get(k);
        rules[k] = o instanceof String ? lexerClass.getState((String) o) : o;
      }
      return new State(name, rules);
    }
  }

  /**
   * The class-level data of a RegexLexer subclass: state_definitions (inherited from the parent
   * class), the states cache, and start_procs (inherited).
   */
  public static final class Definition {
    final Map<String, StateDSL> stateDefinitions = new LinkedHashMap<>();
    final Map<String, State> states = new LinkedHashMap<>();
    final List<Consumer<RegexLexer>> startProcs = new ArrayList<>();

    public Definition(Definition parent) {
      if (parent != null) {
        stateDefinitions.putAll(parent.stateDefinitions);
        startProcs.addAll(parent.startProcs);
      }
    }

    /** state(name) { ... } */
    public Definition state(String name, Consumer<Rules> defn) {
      List<Consumer<Rules>> l = new ArrayList<>();
      l.add(defn);
      stateDefinitions.put(name, new StateDSL(name, l));
      states.remove(name);
      return this;
    }

    /** prepend(name) { ... } */
    public Definition prepend(String name, Consumer<Rules> defn) {
      StateDSL dsl = stateDefinitions.get(name);
      if (dsl == null) {
        throw new IllegalStateException("no such state :" + name);
      }
      stateDefinitions.put(name, dsl.prepended(defn));
      states.remove(name);
      return this;
    }

    /** append(name) { ... } */
    public Definition append(String name, Consumer<Rules> defn) {
      StateDSL dsl = stateDefinitions.get(name);
      if (dsl == null) {
        throw new IllegalStateException("no such state :" + name);
      }
      stateDefinitions.put(name, dsl.appended(defn));
      states.remove(name);
      return this;
    }

    /** start { ... } */
    public Definition start(Consumer<RegexLexer> proc) {
      startProcs.add(proc);
      return this;
    }

    /** get_state(name) */
    public synchronized State getState(String name) {
      State s = states.get(name);
      if (s == null) {
        StateDSL defn = stateDefinitions.get(name);
        if (defn == null) {
          throw new RougeError("unknown state: :" + name);
        }
        s = defn.toState(this);
        states.put(name, s);
      }
      return s;
    }
  }

  // ================================================================== StringScanner

  /** The match data of the last successful scan (Ruby <code>stream[i]</code>). */
  public static final class Match {
    private final String str;
    private final int[] se;

    Match(String str, Matcher m) {
      this.str = str;
      int n = m.groupCount() + 1;
      se = new int[n * 2];
      for (int k = 0; k < n; k++) {
        se[2 * k] = m.start(k);
        se[2 * k + 1] = m.end(k);
      }
    }

    /** stream[i]: null when the group did not participate (or does not exist). */
    public String get(int i) {
      if (i < 0 || 2 * i >= se.length || se[2 * i] < 0) {
        return null;
      }
      return str.substring(se[2 * i], se[2 * i + 1]);
    }

    public String group(int i) {
      return get(i);
    }
  }

  /** The subset of Ruby's StringScanner used by RegexLexer. */
  static final class Scanner {
    final String str;
    int pos;
    Match last;
    private final IdentityHashMap<Pattern, Matcher> matchers = new IdentityHashMap<>();
    /** per-string data of {@link Precheck}s */
    final Map<Object, Object> precheckCache = new java.util.HashMap<>();

    Scanner(String str) {
      this.str = str;
    }

    boolean eos() {
      return pos >= str.length();
    }

    boolean beginningOfLine() {
      return pos == 0 || str.charAt(pos - 1) == '\n';
    }

    /** skip(re): match at the current position; returns the match length, or -1. */
    int skip(Pattern p) {
      Matcher m = matchers.get(p);
      if (m == null) {
        m = p.matcher(str);
        m.useAnchoringBounds(true);
        m.useTransparentBounds(false);
        matchers.put(p, m);
      }
      m.region(pos, str.length());
      boolean found;
      try {
        found = m.lookingAt();
      } catch (StackOverflowError e) {
        found = BigStack.lookingAt(m);
      }
      if (!found) {
        return -1;
      }
      last = new Match(str, m);
      int len = m.end() - pos;
      pos = m.end();
      return len;
    }

    /** getch: one character (code point) */
    String getch() {
      int cp = str.codePointAt(pos);
      int n = Character.charCount(cp);
      String s = str.substring(pos, pos + n);
      pos += n;
      return s;
    }
  }

  // ================================================================== instance state

  ArrayList<State> stack;
  Scanner currentStream;
  Tok.Sink outputStream;
  int nullSteps;

  /** The number of successive scans permitted without consuming the input stream. */
  static final int MAX_NULL_SCANS = 5;

  public State getState(String name) {
    return definition().getState(name);
  }

  /** The state stack, initially [:root]. */
  public List<State> stack() {
    if (stack == null) {
      stack = new ArrayList<>();
      stack.add(getState("root"));
    }
    return stack;
  }

  /** The current state (top of the stack). */
  public State state() {
    List<State> s = stack();
    if (s.isEmpty()) {
      throw new RougeError("empty stack!");
    }
    return s.get(s.size() - 1);
  }

  /** reset!: clear the stack and run the start procs. */
  @Override
  public void reset() {
    stack = null;
    currentStream = null;
    for (Consumer<RegexLexer> pr : definition().startProcs) {
      pr.accept(this);
    }
  }

  @Override
  protected void streamTokens(String str, Tok.Sink b) {
    Scanner stream = new Scanner(str);
    currentStream = stream;
    outputStream = b;
    nullSteps = 0;
    stack();
    while (!stream.eos()) {
      boolean success = step(state(), stream);
      if (!success) {
        b.token(Error, stream.getch());
      }
    }
  }

  /** step(state, stream): try the rules of a state (and its mixins) until one matches. */
  boolean step(State state, Scanner stream) {
    for (Object r : state.rules) {
      if (r instanceof State) {
        if (step((State) r, stream)) {
          return true;
        }
      } else {
        Rule rule = (Rule) r;
        // StringScanner's ^ matches at every scan position (bugs.ruby-lang.org/issues/7092)
        if (rule.beginningOfLine && !stream.beginningOfLine()) {
          continue;
        }
        if (rule.precheck != null && !rule.precheck.mayMatch(stream.str, stream.pos, stream.precheckCache)) {
          continue;
        }
        int size = stream.skip(rule.pattern);
        if (size >= 0) {
          rule.callback.call(this, stream.last);
          if (size == 0) {
            nullSteps++;
            if (nullSteps > MAX_NULL_SCANS) {
              return false;
            }
          } else {
            nullSteps = 0;
          }
          return true;
        }
      }
    }
    return false;
  }

  // ================================================================== callback API

  /** @output_stream.call(tok, val) (default rule callbacks; empty values are dropped by continue_lex) */
  void output(Token tok, String val) {
    outputStream.token(tok, val);
  }

  /** The current match, @current_stream */
  public Match match() {
    return currentStream.last;
  }

  /** token(tok): yield the whole last match. */
  public void token(Token tok) {
    yieldToken(tok, currentStream.last.get(0));
  }

  /** token(tok, val) */
  public void token(Token tok, String val) {
    yieldToken(tok, val);
  }

  /** groups(*tokens): yield each match group with its token. */
  public void groups(Token... tokens) {
    for (int k = 0; k < tokens.length; k++) {
      yieldToken(tokens[k], currentStream.last.get(k + 1));
    }
  }

  /** delegate(lexer, text=nil): continue_lex on another lexer instance (keeps its state). */
  public void delegate(Lexer lexer, String text) {
    if (text == null) {
      text = currentStream.last.get(0);
    }
    lexer.continueLex(text, this::yieldToken);
  }

  public void delegate(Lexer lexer) {
    delegate(lexer, null);
  }

  /** delegate(LexerClass, text): the class-level continue_lex, i.e. a fresh lex. */
  public void delegate(LexerClass lexer, String text) {
    if (text == null) {
      text = currentStream.last.get(0);
    }
    lexer.continueLex(text, this::yieldToken);
  }

  public void delegate(LexerClass lexer) {
    delegate(lexer, null);
  }

  /** recurse(text=nil): delegate(self.class, text) */
  public void recurse(String text) {
    delegate(lexerClass(), text);
  }

  /** push(state_name) */
  public void push(String stateName) {
    stack().add(getState(stateName));
  }

  /** push { ... }: a dynamically defined state */
  public void push(Consumer<Rules> b) {
    stack().add(new StateDSL(b.toString(), List.of(b)).toState(definition()));
  }

  /** push (no argument): push the current state again */
  public void push() {
    State s = state();
    stack().add(s);
  }

  /** pop!(times=1) */
  public void pop(int times) {
    List<State> s = stack();
    if (s.isEmpty()) {
      throw new RougeError("empty stack!");
    }
    for (int k = 0; k < times && !s.isEmpty(); k++) {
      s.remove(s.size() - 1);
    }
  }

  public void pop() {
    pop(1);
  }

  /** goto(state_name): replace the head of the stack */
  public void gotoState(String stateName) {
    List<State> s = stack();
    if (s.isEmpty()) {
      throw new RougeError("empty stack!");
    }
    s.set(s.size() - 1, getState(stateName));
  }

  /** reset_stack */
  public void resetStack() {
    List<State> s = stack();
    s.clear();
    s.add(getState("root"));
  }

  /** in_state?(state_name) */
  public boolean inState(String stateName) {
    for (State s : stack()) {
      if (s.name.equals(stateName)) {
        return true;
      }
    }
    return false;
  }

  /** state?(state_name) */
  public boolean stateP(String stateName) {
    return stateName.equals(state().name);
  }

  private void yieldToken(Token tok, String val) {
    if (val == null || val.isEmpty()) {
      return;
    }
    outputStream.token(tok, val);
  }
}
