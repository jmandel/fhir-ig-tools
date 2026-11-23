package org.hl7.fhir.jekyll.yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of safe_yaml-1.0.5/lib/safe_yaml/psych_handler.rb ({@code SafeYAML::PsychHandler}), the
 * streaming handler Jekyll's {@code SafeYAML.load} uses (no whitelisted tags). Quirks kept as-is:
 *
 * <ul>
 *   <li>one {@code current_key} for the whole handler (not per mapping), and "no key yet" is
 *       {@code nil}: a key that loads as nil ({@code ~}, {@code null}, empty) is dropped and the
 *       following value becomes the key;
 *   <li>{@code <<} merges with {@code Hash#merge!} at the moment the value is added: an alias to a
 *       finished map merges its entries; an inline map merges while still empty (nothing); anything
 *       else (a sequence of maps, a scalar, nil) raises TypeError;
 *   <li>an alias re-runs scalar typing on the anchored value ({@code &a "123"} then {@code *a} gives
 *       Integer 123); an unknown alias gives nil;
 *   <li>the result is the first document; an empty stream gives {@code false}.
 * </ul>
 *
 * Containers are {@link YamlList} ({@code ArrayList<Object>}) and {@link YamlMap}
 * ({@code LinkedHashMap<Object,Object>}); keys are Strings unless the YAML key typed to something
 * else (see {@link Yaml}).
 */
final class PsychHandler {
  private final Map<String, Object> anchors = new HashMap<>();
  private final List<Object> stack = new ArrayList<>();
  private Object currentStructure;
  private Object currentKey;
  private Object result;
  private boolean begun;
  private boolean finished;

  /** result: {@code @begun ? @result : false} */
  Object result() {
    return begun ? result : Boolean.FALSE;
  }

  boolean finished() {
    return finished;
  }

  @SuppressWarnings("unchecked")
  private void addToCurrentStructure(Object value, String anchor, boolean quoted, String tag) {
    value = Transform.toProperType(value, quoted, tag);
    if (anchor != null) {
      anchors.put(anchor, value);
    }
    if (!begun) {
      begun = true;
      result = value;
      currentStructure = result;
      return;
    }
    if (currentStructure instanceof List) {
      ((List<Object>) currentStructure).add(value);
    } else if (currentStructure instanceof Map) {
      Map<Object, Object> map = (Map<Object, Object>) currentStructure;
      if (currentKey == null) {
        currentKey = value;
      } else {
        if ("<<".equals(currentKey)) {
          map.putAll(toHash(value));
        } else {
          map.put(currentKey, value);
        }
        currentKey = null;
      }
    } else {
      throw new YamlException("RuntimeError", "Don't know how to add to a " + RubyValues.className(currentStructure) + "!");
    }
  }

  /** Hash#merge!'s implicit conversion (rb_to_hash_type) */
  @SuppressWarnings("unchecked")
  private static Map<Object, Object> toHash(Object value) {
    if (value instanceof Map) {
      // merge!(self) is a no-op; copy first so LinkedHashMap.putAll(this) cannot misbehave
      return new LinkedHashMap<>((Map<Object, Object>) value);
    }
    String what = value == null ? "nil" : value instanceof Boolean ? value.toString() : RubyValues.className(value);
    throw new YamlException("TypeError", "no implicit conversion of " + what + " into Hash");
  }

  private void endCurrentStructure() {
    stack.remove(stack.size() - 1);
    currentStructure = stack.isEmpty() ? null : stack.get(stack.size() - 1);
  }

  // ---- event handlers ----

  void alias(String anchor) {
    addToCurrentStructure(anchors.get(anchor), null, false, null);
  }

  void scalar(String value, String anchor, String tag, boolean plain, boolean quoted, int style) {
    addToCurrentStructure(value, anchor, quoted, tag);
  }

  void endDocument(boolean implicit) {
    // @block.call(@result): SafeYAML.load returns @result here
    finished = true;
  }

  void startMapping(String anchor, String tag, boolean implicit, int style) {
    Map<Object, Object> map = new YamlMap();
    addToCurrentStructure(map, anchor, false, null);
    currentStructure = map;
    stack.add(map);
  }

  void endMapping() {
    endCurrentStructure();
  }

  void startSequence(String anchor, String tag, boolean implicit, int style) {
    List<Object> seq = new YamlList();
    addToCurrentStructure(seq, anchor, false, null);
    currentStructure = seq;
    stack.add(seq);
  }

  void endSequence() {
    endCurrentStructure();
  }
}
