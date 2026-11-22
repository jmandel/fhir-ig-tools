package org.hl7.fhir.jekyll.liquid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Port of liquid-4.0.4/lib/liquid/context.rb.
 *
 * <p>Scopes are kept in Ruby order: {@code scopes().get(0)} is the innermost scope,
 * {@code scopes().get(size-1)} the outermost one (where {@code assign}/{@code capture} write).
 * Environments are Maps (or {@link Drop}s); the first one is where {@code increment} counters live.
 * Registers use String keys ("for", "cycle", "file_system", "site", "page", ...).
 */
public class Context {
  private final List<Object> environments;
  private final List<Map<String, Object>> scopes;
  private final Map<String, Object> registers;
  private final List<LiquidException> errors = new ArrayList<>();
  private final ResourceLimits resourceLimits;
  private final Environment environment;
  private final List<Interrupt> interrupts = new ArrayList<>();
  private final List<LiquidException> warnings = new ArrayList<>();
  private Map<String, Filter> localFilters;
  private boolean partial;
  private boolean strictVariables;
  private boolean strictFilters;
  private boolean thisStackUsed;
  private boolean rethrowErrors;
  private String templateName;
  private Function<Object, Object> globalFilter;
  private Function<LiquidException, Object> exceptionRenderer = e -> e;

  /** Ruby {@code Context.new(environments, outer_scope, registers, rethrow_errors, resource_limits)}. */
  @SuppressWarnings("unchecked")
  public Context(List<?> environments, Map<String, Object> outerScope, Map<String, Object> registers, boolean rethrowErrors,
      ResourceLimits resourceLimits, Environment environment) {
    this.environments = new ArrayList<>();
    if (environments != null) {
      for (Object e : environments) {
        if (e instanceof List) {
          this.environments.addAll((List<Object>) e); // [environments].flatten
        } else if (e != null) {
          this.environments.add(e);
        }
      }
    }
    this.scopes = new ArrayList<>();
    this.scopes.add(outerScope != null ? outerScope : new LinkedHashMap<>());
    this.registers = registers != null ? registers : new HashMap<>();
    this.resourceLimits = resourceLimits != null ? resourceLimits : new ResourceLimits();
    this.environment = environment != null ? environment : Environment.defaultEnvironment();
    squashInstanceAssignsWithEnvironments();
    this.rethrowErrors = rethrowErrors;
  }

  /** Convenience: one environment hash (the assigns), fresh outer scope. */
  public Context(Map<String, Object> assigns, Map<String, Object> registers, Environment environment) {
    this(Arrays.asList(assigns != null ? assigns : new LinkedHashMap<String, Object>()), new LinkedHashMap<>(), registers, false, null, environment);
  }

  public Context(Map<String, Object> assigns) {
    this(assigns, null, null);
  }

  // ---------------------------------------------------------------- accessors

  public List<Map<String, Object>> scopes() {
    return scopes;
  }

  public List<Object> environments() {
    return environments;
  }

  public Map<String, Object> registers() {
    return registers;
  }

  public List<LiquidException> errors() {
    return errors;
  }

  public List<LiquidException> warnings() {
    return warnings;
  }

  public ResourceLimits resourceLimits() {
    return resourceLimits;
  }

  public Environment environment() {
    return environment;
  }

  public boolean strictVariables() {
    return strictVariables;
  }

  public Context strictVariables(boolean b) {
    this.strictVariables = b;
    return this;
  }

  public boolean strictFilters() {
    return strictFilters;
  }

  public Context strictFilters(boolean b) {
    this.strictFilters = b;
    return this;
  }

  public boolean partial() {
    return partial;
  }

  public void partial(boolean b) {
    this.partial = b;
  }

  public String templateName() {
    return templateName;
  }

  public void templateName(String n) {
    this.templateName = n;
  }

  public Function<Object, Object> globalFilter() {
    return globalFilter;
  }

  public void globalFilter(Function<Object, Object> f) {
    this.globalFilter = f;
  }

  /** Ruby {@code exception_renderer=}; receives the Liquid error, returns what to output. */
  public void exceptionRenderer(Function<LiquidException, Object> r) {
    this.exceptionRenderer = r;
  }

  /** Errors are re-raised instead of rendered (Ruby {@code exception_renderer = ->(e) { raise }}). */
  public boolean rethrowErrors() {
    return rethrowErrors;
  }

  public void rethrowErrors(boolean b) {
    this.rethrowErrors = b;
  }

  Map<String, Filter> localFilters() {
    return localFilters;
  }

  /** Ruby {@code add_filters}: filters local to this context. */
  public void addFilter(String name, Filter f) {
    if (localFilters == null) {
      localFilters = new HashMap<>();
    }
    localFilters.put(name, f);
  }

  public Object applyGlobalFilter(Object obj) {
    return globalFilter == null ? obj : globalFilter.apply(obj);
  }

  // ---------------------------------------------------------------- interrupts

  public boolean interrupt() {
    return !interrupts.isEmpty();
  }

  public void pushInterrupt(Interrupt e) {
    interrupts.add(e);
  }

  public Interrupt popInterrupt() {
    return interrupts.isEmpty() ? null : interrupts.remove(interrupts.size() - 1);
  }

  // ---------------------------------------------------------------- errors

  /**
   * Ruby {@code handle_error(e, line_number)}: records the error and returns the rendered message,
   * or rethrows the original exception when errors are re-raised (render!).
   */
  public String handleError(RuntimeException original, Integer lineNumber) {
    LiquidException e;
    if (original instanceof LiquidException) {
      e = (LiquidException) original;
    } else {
      e = new LiquidException(LiquidException.Kind.INTERNAL, "internal", original);
      if (System.getenv("LIQUID_DEBUG") != null) {
        original.printStackTrace();
      }
    }
    if (e.templateName() == null) {
      e.templateName(templateName);
    }
    if (e.lineNumber() == null) {
      e.lineNumber(lineNumber);
    }
    errors.add(e);
    if (rethrowErrors) {
      throw original;
    }
    return Values.rubyToS(exceptionRenderer.apply(e));
  }

  /** Ruby {@code invoke(method, *args)}: call a filter, then {@code to_liquid}. */
  public Object invoke(String method, Object input, List<Object> args) {
    return Values.toLiquid(Strainer.invoke(this, method, input, args));
  }

  public Object invoke(String method, Object input, Object... args) {
    return invoke(method, input, new ArrayList<>(Arrays.asList(args)));
  }

  // ---------------------------------------------------------------- scopes

  /** Push new local scope on the stack. Prefer {@link #stack}. */
  public void push(Map<String, Object> newScope) {
    scopes.add(0, newScope != null ? newScope : new LinkedHashMap<>());
    if (scopes.size() > Block.MAX_DEPTH) {
      throw new LiquidException(LiquidException.Kind.STACK_LEVEL, "Nesting too deep");
    }
  }

  public void push() {
    push(null);
  }

  /** Merge a hash of variables in the current local scope. */
  public void merge(Map<String, Object> newScopes) {
    scopes.get(0).putAll(newScopes);
  }

  public Map<String, Object> pop() {
    if (scopes.size() == 1) {
      throw new LiquidException(LiquidException.Kind.CONTEXT, null);
    }
    return scopes.remove(0);
  }

  /**
   * Ruby {@code stack(new_scope = nil) { ... }}: with a scope, pushes it for the duration; without,
   * the first {@link #set} inside pushes a fresh scope that is popped at the end.
   */
  public <T> T stack(Map<String, Object> newScope, Supplier<T> body) {
    boolean oldStackUsed = thisStackUsed;
    try {
      if (newScope != null) {
        push(newScope);
        thisStackUsed = true;
      } else {
        thisStackUsed = false;
      }
      return body.get();
    } finally {
      if (thisStackUsed) {
        pop();
      }
      thisStackUsed = oldStackUsed;
    }
  }

  public <T> T stack(Supplier<T> body) {
    return stack(null, body);
  }

  public void stackRun(Map<String, Object> newScope, Runnable body) {
    stack(newScope, () -> {
      body.run();
      return null;
    });
  }

  public void clearInstanceAssigns() {
    scopes.set(0, new LinkedHashMap<>());
  }

  /** Ruby {@code context[key] = value}: sets in the innermost scope (pushing one if this stack level has none). */
  public void set(String key, Object value) {
    if (!thisStackUsed) {
      thisStackUsed = true;
      push(new LinkedHashMap<>());
    }
    scopes.get(0).put(key, value);
  }

  /** Ruby {@code context[expression]}: parse the markup as an expression and evaluate it. */
  public Object get(String expression) {
    return evaluate(Expression.parse(expression));
  }

  public boolean hasKey(String key) {
    return get(key) != null;
  }

  public Object evaluate(Object object) {
    return object instanceof Evaluable ? ((Evaluable) object).evaluate(this) : object;
  }

  public Object findVariable(Object key) {
    return findVariable(key, true);
  }

  /** Fetches an object starting at the local scope and then moving up the hierarchy. */
  public Object findVariable(Object key, boolean raiseOnNotFound) {
    Object scope = null;
    for (Map<String, Object> s : scopes) {
      if (s.containsKey(key)) {
        scope = s;
        break;
      }
    }
    Object variable = null;
    if (scope == null) {
      for (Object e : environments) {
        variable = lookupAndEvaluate(e, key, raiseOnNotFound);
        // When lookup returned a value OR there is no value but the lookup also did not raise
        // then it is the value we are looking for.
        if (variable != null || strictVariables && raiseOnNotFound) {
          scope = e;
          break;
        }
      }
    }
    if (scope == null) {
      scope = environments.isEmpty() ? scopes.get(scopes.size() - 1) : environments.get(environments.size() - 1);
    }
    if (variable == null) {
      variable = lookupAndEvaluate(scope, key, raiseOnNotFound);
    }
    variable = Values.toLiquid(variable);
    if (variable instanceof Drop) {
      ((Drop) variable).setContext(this);
    }
    return variable;
  }

  public Object lookupAndEvaluate(Object obj, Object key) {
    return lookupAndEvaluate(obj, key, true);
  }

  @SuppressWarnings("unchecked")
  public Object lookupAndEvaluate(Object obj, Object key, boolean raiseOnNotFound) {
    if (strictVariables && raiseOnNotFound) {
      boolean missing = obj instanceof Map ? !((Map<?, ?>) obj).containsKey(key) : obj instanceof Drop && !((Drop) obj).hasKey(key);
      if (missing) {
        throw new LiquidException(LiquidException.Kind.UNDEFINED_VARIABLE, "undefined variable " + Values.rubyToS(key));
      }
    }
    Object value = Values.index(obj, key);
    if (value instanceof LiquidProc) {
      Object v = ((LiquidProc) value).call(this);
      if (obj instanceof Map) {
        ((Map<Object, Object>) obj).put(key, v);
      } else if (obj instanceof List && Values.isInteger(key)) {
        List<Object> l = (List<Object>) obj;
        long i = Values.toLong(key);
        if (i < 0) {
          i += l.size();
        }
        l.set((int) i, v);
      } else if (obj instanceof Drop) {
        ((Drop) obj).put(key, v);
      }
      return v;
    }
    return value;
  }

  private void squashInstanceAssignsWithEnvironments() {
    Map<String, Object> last = scopes.get(scopes.size() - 1);
    for (String k : new ArrayList<>(last.keySet())) {
      for (Object env : environments) {
        boolean has = env instanceof Map ? ((Map<?, ?>) env).containsKey(k) : env instanceof Drop && ((Drop) env).hasKey(k);
        if (has) {
          last.put(k, lookupAndEvaluate(env, k));
          break;
        }
      }
    }
  }
}
