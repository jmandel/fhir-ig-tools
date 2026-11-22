package org.hl7.fhir.jekyll.liquid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Port of liquid-4.0.4/lib/liquid/variable_lookup.rb. */
public class VariableLookup implements Evaluable {
  private static final Pattern SQUARE_BRACKETED = Pattern.compile("(?s)\\A\\[(.*)\\]\\z", Pattern.UNIX_LINES);

  private final Object name;
  private final List<Object> lookups;
  private final long commandFlags;

  public static VariableLookup parse(String markup) {
    return new VariableLookup(markup);
  }

  public VariableLookup(String markup) {
    List<String> scanned = Liquid.scan(Liquid.VariableParser, markup);
    Object nm = scanned.isEmpty() ? null : scanned.remove(0);
    if (nm != null) {
      Matcher m = SQUARE_BRACKETED.matcher((String) nm);
      if (m.find()) {
        nm = Expression.parse(m.group(1));
      }
    }
    this.name = nm;
    List<Object> ls = new ArrayList<>(scanned.size());
    long flags = 0;
    for (int i = 0; i < scanned.size(); i++) {
      String lookup = scanned.get(i);
      Matcher m = SQUARE_BRACKETED.matcher(lookup);
      if (m.find()) {
        ls.add(Expression.parse(m.group(1)));
      } else {
        ls.add(lookup);
        if (i < 64 && (lookup.equals("size") || lookup.equals("first") || lookup.equals("last"))) {
          flags |= 1L << i;
        }
      }
    }
    this.lookups = ls;
    this.commandFlags = flags;
  }

  public Object name() {
    return name;
  }

  public List<Object> lookups() {
    return lookups;
  }

  @Override
  public Object evaluate(Context context) {
    Object nm = context.evaluate(name);
    Object object = context.findVariable(nm);

    for (int i = 0; i < lookups.size(); i++) {
      Object key = context.evaluate(lookups.get(i));

      // If object is a hash- or array-like object we look for the
      // presence of the key and if its available we return it
      if (Values.respondsToIndex(object)
          && ((object instanceof Map && ((Map<?, ?>) object).containsKey(key))
              || (object instanceof Drop && ((Drop) object).hasKey(key))
              || ((object instanceof List || object instanceof Map) && Values.isInteger(key)))) {
        // if its a proc we will replace the entry with the proc
        Object res = context.lookupAndEvaluate(object, key);
        object = Values.toLiquid(res);
      } else if (i < 64 && (commandFlags & (1L << i)) != 0 && Values.respondsTo(object, (String) key)) {
        // Some special cases. If the part wasn't in square brackets and
        // no key with the same name was found we interpret following calls
        // as commands and call them on the current object
        object = Values.toLiquid(Values.send(object, (String) key));
      } else {
        // No key was present with the desired value and it wasn't one of the directly supported
        // keywords either. The only thing we got left is to return nil or
        // raise an exception if `strict_variables` option is set to true
        if (!context.strictVariables()) {
          return null;
        }
        throw new LiquidException(LiquidException.Kind.UNDEFINED_VARIABLE, "undefined variable " + Values.rubyToS(key));
      }

      // If we are dealing with a drop here we have to
      if (object instanceof Drop) {
        ((Drop) object).setContext(context);
      }
    }
    return object;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof VariableLookup)) {
      return false;
    }
    VariableLookup v = (VariableLookup) o;
    return java.util.Objects.equals(name, v.name) && lookups.equals(v.lookups) && commandFlags == v.commandFlags;
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(name, lookups, commandFlags);
  }

  @Override
  public String toString() {
    return "VariableLookup(" + name + ", " + lookups + ")";
  }
}
