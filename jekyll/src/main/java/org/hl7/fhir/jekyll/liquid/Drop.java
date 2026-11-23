package org.hl7.fhir.jekyll.liquid;

/**
 * The capability Liquid needs from a drop (port of the protocol in liquid-4.0.4/lib/liquid/drop.rb):
 * {@code [](key)} / {@code invoke_drop}, {@code key?}, {@code to_liquid}, {@code context=}, and
 * {@code to_s}/{@code inspect} for output. {@link LiquidDrop} is the port of {@code Liquid::Drop}
 * itself; Jekyll-style drops can implement this interface directly.
 */
public interface Drop {
  /** Ruby {@code drop[key]} / {@code invoke_drop(key)}. */
  Object invokeDrop(Object key);

  /** Ruby {@code key?}; Liquid::Drop always answers true. */
  default boolean hasKey(Object key) {
    return true;
  }

  default Object toLiquid() {
    return this;
  }

  /** Ruby {@code context=}; called whenever Liquid hands the drop out of a lookup. */
  default void setContext(Context context) {}

  /** Ruby class name, used by {@code to_s}/{@code inspect} defaults and error messages. */
  default String rubyClassName() {
    return getClass().getSimpleName();
  }

  /** Ruby {@code to_s} (Liquid::Drop: the class name). */
  default String toS() {
    return rubyClassName();
  }

  default String inspect() {
    return rubyClassName();
  }

  /** Ruby {@code drop[key] = value} (used by increment/decrement on an environment drop). */
  default void put(Object key, Object value) {
    throw RubyError.noMethod("[]=", this);
  }
}
