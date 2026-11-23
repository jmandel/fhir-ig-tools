package org.hl7.fhir.jekyll.liquid;

/**
 * Port of liquid-4.0.4/lib/liquid/drop.rb ({@code Liquid::Drop}). Subclasses expose their
 * "public instance methods" by overriding {@link #invokeMethod(String)} and returning
 * {@link #NO_METHOD} for names they don't have.
 */
public abstract class LiquidDrop implements Drop {
  /** Sentinel: the drop has no such method (Ruby {@code invokable?} false). */
  protected static final Object NO_METHOD = new Object();

  protected Context context;

  @Override
  public void setContext(Context context) {
    this.context = context;
  }

  /** Ruby {@code liquid_method_missing}. */
  protected Object liquidMethodMissing(String method) {
    if (context == null || !context.strictVariables()) {
      return null;
    }
    throw new LiquidException(LiquidException.Kind.UNDEFINED_DROP_METHOD, "undefined method " + method);
  }

  /** Invoke a whitelisted method, or return {@link #NO_METHOD}. */
  protected abstract Object invokeMethod(String name);

  @Override
  public Object invokeDrop(Object methodOrKey) {
    String name = methodOrKey == null ? "" : Values.rubyToS(methodOrKey);
    if (name.equals("to_liquid")) {
      return toLiquid();
    }
    Object r = invokeMethod(name);
    if (r == NO_METHOD) {
      return liquidMethodMissing(name);
    }
    return r;
  }

  @Override
  public String rubyClassName() {
    return "Liquid::" + getClass().getSimpleName();
  }
}
