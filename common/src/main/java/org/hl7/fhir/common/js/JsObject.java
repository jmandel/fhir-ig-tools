package org.hl7.fhir.common.js;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A plain JS object in the shared JS value model (see {@link Json}). Insertion-ordered like a JS object, EXCEPT that
 * JS orders "array index" keys ("0", "1", ... up to 2^32-2) first, ascending: always enumerate with
 * {@link Js#ownKeys(Map)} when porting {@code Object.keys / Object.entries / for..in}, and {@link Json#stringify} does.
 * Any {@code Map<String,Object>} is accepted wherever the value model is expected; this subclass only names it.
 */
public class JsObject extends LinkedHashMap<String, Object> {
  private static final long serialVersionUID = 1L;

  public JsObject() {}

  public JsObject(Map<String, Object> m) {
    super(m);
  }

  /** Fluent put, for building fixtures: {@code new JsObject().with("a", 1.0).with("b", "x")}. */
  public JsObject with(String key, Object value) {
    put(key, value);
    return this;
  }
}
