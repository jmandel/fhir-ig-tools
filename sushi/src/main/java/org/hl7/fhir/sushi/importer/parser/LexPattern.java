package org.hl7.fhir.sushi.importer.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * The regular expression of one lexer rule, written in the notation of FSHLexer.g4 ({@link #seq}, {@link #alt},
 * {@link #star}, {@link #plus}, {@link #opt}, literals and char sets), compiled to a Thompson NFA.
 *
 * <p>For rules without non-greedy loops an ANTLR lexer accepts the longest prefix of the input in the rule's language
 * (and, across rules, the longest match wins with ties going to the earlier rule), so {@link #match} reports the
 * longest accepted prefix and how far the rule stayed alive (which determines the text of "token recognition error"
 * messages). The two non-greedy rules of the FSH grammars are implemented directly in {@link FSHLexer}.
 */
final class LexPattern {
  /** Char predicate over UTF-16 code units (the JS runtime's alphabet). */
  interface CharSet extends IntPredicate {}

  sealed interface Rx permits Chars, Seq, Alt, Rep {}
  record Chars(CharSet set) implements Rx {}
  record Seq(List<Rx> items) implements Rx {}
  record Alt(List<Rx> alts) implements Rx {}
  /** min 0 or 1, max 1 or unbounded (-1). */
  record Rep(Rx body, int min, int max) implements Rx {}

  static Rx seq(Rx... items) { return new Seq(List.of(items)); }
  static Rx alt(Rx... alts) { return new Alt(List.of(alts)); }
  static Rx star(Rx body) { return new Rep(body, 0, -1); }
  static Rx plus(Rx body) { return new Rep(body, 1, -1); }
  static Rx opt(Rx body) { return new Rep(body, 0, 1); }
  static Rx set(CharSet s) { return new Chars(s); }

  /** A literal string ('Alias'). */
  static Rx lit(String s) {
    List<Rx> items = new ArrayList<>();
    for (char c : s.toCharArray()) items.add(new Chars(x -> x == c));
    return items.size() == 1 ? items.get(0) : new Seq(items);
  }

  /** One of the given chars ([...]). */
  static CharSet chars(String cs) {
    return c -> cs.indexOf(c) >= 0;
  }

  /** None of the given chars (~[...]). */
  static CharSet notChars(String cs) {
    return c -> cs.indexOf(c) < 0;
  }

  static CharSet range(char lo, char hi) {
    return c -> c >= lo && c <= hi;
  }

  static final CharSet ANY = c -> true;

  // ---- NFA ----
  private int n;
  private int[][] eps = new int[16][];
  private int[] epsCount = new int[16];
  private CharSet[] pred = new CharSet[16];
  private int[] next = new int[16];
  private final int start;
  private final int accept;
  private final boolean[] firstChars = new boolean[0x10000];

  LexPattern(Rx rx) {
    start = newState();
    accept = newState();
    build(rx, start, accept);
    // first-char filter: chars with a transition out of the start closure
    int[] cl = closure(new int[] {start}, 1);
    for (int c = 0; c < 0x10000; c++) {
      for (int s : cl) {
        if (s < 0) break;
        if (pred[s] != null && pred[s].test(c)) {
          firstChars[c] = true;
          break;
        }
      }
    }
  }

  private int newState() {
    if (n == pred.length) {
      int m = n * 2;
      eps = Arrays.copyOf(eps, m);
      epsCount = Arrays.copyOf(epsCount, m);
      pred = Arrays.copyOf(pred, m);
      next = Arrays.copyOf(next, m);
    }
    next[n] = -1;
    return n++;
  }

  private void epsilon(int from, int to) {
    if (eps[from] == null) eps[from] = new int[2];
    if (epsCount[from] == eps[from].length) eps[from] = Arrays.copyOf(eps[from], epsCount[from] * 2);
    eps[from][epsCount[from]++] = to;
  }

  private void build(Rx rx, int from, int to) {
    switch (rx) {
      case Chars c -> {
        int s = newState();
        epsilon(from, s);
        pred[s] = c.set();
        next[s] = to;
      }
      case Seq q -> {
        int cur = from;
        for (int i = 0; i < q.items().size(); i++) {
          int nxt = i == q.items().size() - 1 ? to : newState();
          build(q.items().get(i), cur, nxt);
          cur = nxt;
        }
        if (q.items().isEmpty()) epsilon(from, to);
      }
      case Alt a -> {
        for (Rx x : a.alts()) build(x, from, to);
      }
      case Rep r -> {
        int in = newState(), out = newState();
        epsilon(from, in);
        build(r.body(), in, out);
        if (r.min() == 0) epsilon(from, to);
        epsilon(out, to);
        if (r.max() < 0) epsilon(out, in);
      }
    }
  }

  /** Epsilon closure; returns the states (terminated by -1 if shorter than the array). */
  private int[] closure(int[] states, int count) {
    boolean[] seen = new boolean[n];
    int[] stack = new int[n];
    int[] out = new int[n];
    int sp = 0, on = 0;
    for (int i = 0; i < count; i++) {
      if (!seen[states[i]]) {
        seen[states[i]] = true;
        stack[sp++] = states[i];
      }
    }
    while (sp > 0) {
      int s = stack[--sp];
      out[on++] = s;
      for (int i = 0; i < epsCount[s]; i++) {
        int t = eps[s][i];
        if (!seen[t]) {
          seen[t] = true;
          stack[sp++] = t;
        }
      }
    }
    if (on < out.length) out[on] = -1;
    return out;
  }

  /** Result of matching at a position: longest accepted length (-1 if none) and chars consumed while alive. */
  record Match(int accepted, int alive) {}

  static final Match NONE = new Match(-1, 0);

  /** A lazily built DFA state (subset of NFA states) with memoised transitions; dead = no NFA state. */
  private final class DState {
    final int[] nfa;
    final boolean accepting;
    final DState[] ascii = new DState[128];
    final java.util.concurrent.ConcurrentHashMap<Character, DState> other = new java.util.concurrent.ConcurrentHashMap<>();

    DState(int[] nfa) {
      this.nfa = nfa;
      boolean a = false;
      for (int s : nfa) if (s == accept) a = true;
      this.accepting = a;
    }

    DState step(char c) {
      DState d = c < 128 ? ascii[c] : other.get(c);
      if (d == null) {
        int[] buf = new int[n];
        int bn = 0;
        for (int s : nfa) if (pred[s] != null && pred[s].test(c)) buf[bn++] = next[s];
        d = bn == 0 ? DEAD : intern(closure(buf, bn));
        if (c < 128) ascii[c] = d;
        else other.put(c, d);
      }
      return d;
    }
  }

  private final java.util.concurrent.ConcurrentHashMap<String, DState> dstates =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final DState DEAD = new DState(new int[0]);
  private volatile DState startState;

  private DState intern(int[] closure) {
    int[] set = Arrays.copyOf(closure, count(closure));
    Arrays.sort(set);
    return dstates.computeIfAbsent(Arrays.toString(set), k -> new DState(set));
  }

  Match match(String input, int pos) {
    int len = input.length();
    if (pos >= len || !firstChars[input.charAt(pos)]) return NONE;
    DState cur = startState;
    if (cur == null) startState = cur = intern(closure(new int[] {start}, 1));
    int accepted = -1;
    int k = 0;
    while (true) {
      if (cur.accepting) accepted = k;
      if (pos + k >= len) return new Match(accepted, k);
      DState nxt = cur.step(input.charAt(pos + k));
      if (nxt == DEAD) return new Match(accepted, k);
      cur = nxt;
      k++;
    }
  }

  private static int count(int[] a) {
    for (int i = 0; i < a.length; i++) if (a[i] < 0) return i;
    return a.length;
  }
}
