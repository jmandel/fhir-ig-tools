package org.hl7.fhir.jekyll.liquid;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/** An inclusive Ruby integer {@code Range} ({@code start..end}), as produced by Liquid range literals. */
public final class RubyRange {
  public final long first;
  public final long last;

  public RubyRange(long first, long last) {
    this.first = first;
    this.last = last;
  }

  public long size() {
    return Math.max(0, last - first + 1);
  }

  /** Ruby {@code Range#to_a}. */
  public List<Object> toList() {
    long n = size();
    if (n > 10_000_000) {
      // keep it lazy; Ruby would allocate, we just avoid exhausting memory
      return new AbstractList<Object>() {
        @Override
        public Object get(int i) {
          return first + i;
        }

        @Override
        public int size() {
          return (int) Math.min(Integer.MAX_VALUE, n);
        }
      };
    }
    List<Object> l = new ArrayList<>((int) n);
    for (long k = first; k <= last; k++) {
      l.add(k);
    }
    return l;
  }

  public boolean includes(Object v) {
    if (!Values.isNumeric(v)) {
      return false;
    }
    return Values.numCompare(first, v) <= 0 && Values.numCompare(v, last) <= 0;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof RubyRange && ((RubyRange) o).first == first && ((RubyRange) o).last == last;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(first) * 31 + Long.hashCode(last);
  }

  @Override
  public String toString() {
    return first + ".." + last;
  }
}
