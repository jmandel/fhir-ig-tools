package org.hl7.fhir.jekyll.site;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The merge sort that Ruby's Array#sort / sort! actually run on the reference stack: ruby_qsort →
 * glibc qsort_r → msort_with_tmp (verified stable on glibc 2.39 and 2.41; TRACKER key facts).
 * Reproducing the exact split/merge order keeps results identical even when a comparator is not
 * a total order (Jekyll's sort_input falls back from <=> to to_s comparison), where Java's TimSort
 * could throw or order differently.
 */
final class RubySort {
  private RubySort() {}

  static <T> void msort(List<T> list, Comparator<? super T> cmp) {
    int n = list.size();
    if (n <= 1) {
      return;
    }
    @SuppressWarnings("unchecked")
    T[] a = (T[]) list.toArray();
    @SuppressWarnings("unchecked")
    T[] tmp = (T[]) new Object[n];
    msort(a, tmp, 0, n, cmp);
    for (int i = 0; i < n; i++) {
      list.set(i, a[i]);
    }
  }

  /** glibc msort_with_tmp: n1 = n/2, n2 = n - n1; sort both halves, merge taking left when cmp <= 0. */
  private static <T> void msort(T[] a, T[] tmp, int lo, int n, Comparator<? super T> cmp) {
    if (n <= 1) {
      return;
    }
    int n1 = n / 2;
    int n2 = n - n1;
    int b1 = lo, b2 = lo + n1;
    msort(a, tmp, b1, n1, cmp);
    msort(a, tmp, b2, n2, cmp);
    int t = 0;
    while (n1 > 0 && n2 > 0) {
      if (cmp.compare(a[b1], a[b2]) <= 0) {
        tmp[t++] = a[b1++];
        n1--;
      } else {
        tmp[t++] = a[b2++];
        n2--;
      }
    }
    while (n1 > 0) {
      tmp[t++] = a[b1++];
      n1--;
    }
    // remaining n2 elements are already in place
    System.arraycopy(tmp, 0, a, lo, t);
  }

  static <T> List<T> sorted(List<T> in, Comparator<? super T> cmp) {
    List<T> out = new ArrayList<>(in);
    msort(out, cmp);
    return out;
  }
}
