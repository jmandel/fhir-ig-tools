package org.hl7.fhir.jekyll.liquid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Port of liquid-4.0.4/lib/liquid/condition.rb. */
public class Condition {
  /** An operator implementation (Ruby {@code @@operators} value). */
  @FunctionalInterface
  public interface Operator {
    Object apply(Condition cond, Object left, Object right);
  }

  private static final Map<String, Operator> OPERATORS = new HashMap<>();

  static {
    OPERATORS.put("==", (c, l, r) -> c.equalVariables(l, r));
    OPERATORS.put("!=", (c, l, r) -> !Values.truthy(c.equalVariables(l, r)));
    OPERATORS.put("<>", (c, l, r) -> !Values.truthy(c.equalVariables(l, r)));
    for (String op : new String[] {"<", ">", ">=", "<="}) {
      OPERATORS.put(op, (c, l, r) -> {
        if (Values.respondsToRelop(l) && Values.respondsToRelop(r) && !(l instanceof Map) && !(r instanceof Map)) {
          try {
            return Values.relop(op, l, r);
          } catch (RubyError e) {
            if (e.isArgumentError()) {
              throw LiquidException.argument(e.getMessage());
            }
            throw e;
          }
        }
        return null;
      });
    }
    OPERATORS.put("contains", (c, left, right) -> {
      if (Values.truthy(left) && Values.truthy(right) && respondsToInclude(left)) {
        if (left instanceof String) {
          return ((String) left).contains(Values.rubyToS(right));
        }
        return include(left, right);
      }
      return false;
    });
  }

  /** Ruby {@code Condition.operators}: mutable, like Ruby's class variable. */
  public static Map<String, Operator> operators() {
    return OPERATORS;
  }

  static boolean respondsToInclude(Object o) {
    return o instanceof String || o instanceof List || o instanceof Map || o instanceof RubyRange;
  }

  static boolean include(Object left, Object right) {
    if (left instanceof List) {
      for (Object e : (List<?>) left) {
        if (Values.rubyEquals(e, right)) {
          return true;
        }
      }
      return false;
    }
    if (left instanceof Map) {
      for (Object k : ((Map<?, ?>) left).keySet()) {
        if (Values.rubyEql(k, right)) {
          return true;
        }
      }
      return false;
    }
    if (left instanceof RubyRange) {
      return ((RubyRange) left).includes(right);
    }
    return false;
  }

  private Object left;
  private String operator;
  private Object right;
  private String childRelation;
  private Condition childCondition;
  private BlockBody attachment;

  public Condition() {
    this(null, null, null);
  }

  public Condition(Object left) {
    this(left, null, null);
  }

  public Condition(Object left, String operator, Object right) {
    this.left = left;
    this.operator = operator;
    this.right = right;
  }

  public Object left() {
    return left;
  }

  public String operator() {
    return operator;
  }

  public Object right() {
    return right;
  }

  public BlockBody attachment() {
    return attachment;
  }

  public Condition childCondition() {
    return childCondition;
  }

  public Object evaluate(Context context) {
    Condition condition = this;
    Object result;
    while (true) {
      result = interpretCondition(condition.left, condition.right, condition.operator, context);
      if ("or".equals(condition.childRelation)) {
        if (Values.truthy(result)) {
          break;
        }
      } else if ("and".equals(condition.childRelation)) {
        if (!Values.truthy(result)) {
          break;
        }
      } else {
        break;
      }
      condition = condition.childCondition;
    }
    return result;
  }

  public void or(Condition condition) {
    childRelation = "or";
    childCondition = condition;
  }

  public void and(Condition condition) {
    childRelation = "and";
    childCondition = condition;
  }

  /** Ruby {@code condition.send(op, other)} for "and"/"or". */
  public void link(String op, Condition condition) {
    if (op.equals("and")) {
      and(condition);
    } else {
      or(condition);
    }
  }

  public void attach(BlockBody attachment) {
    this.attachment = attachment;
  }

  public boolean isElse() {
    return false;
  }

  private Object equalVariables(Object left, Object right) {
    if (left instanceof Expression.MethodLiteral) {
      String m = ((Expression.MethodLiteral) left).methodName;
      return Values.respondsTo(right, m) ? Values.send(right, m) : null;
    }
    if (right instanceof Expression.MethodLiteral) {
      String m = ((Expression.MethodLiteral) right).methodName;
      return Values.respondsTo(left, m) ? Values.send(left, m) : null;
    }
    return Values.rubyEquals(left, right);
  }

  private Object interpretCondition(Object left, Object right, String op, Context context) {
    // If the operator is empty this means that the decision statement is just
    // a single variable. We can just poll this variable from the context and
    // return this as the result.
    if (op == null) {
      return context.evaluate(left);
    }
    left = context.evaluate(left);
    right = context.evaluate(right);
    Operator operation = OPERATORS.get(op);
    if (operation == null) {
      throw LiquidException.argument("Unknown operator " + op);
    }
    return operation.apply(this, left, right);
  }

  /** Port of {@code Liquid::ElseCondition}. */
  public static class ElseCondition extends Condition {
    @Override
    public boolean isElse() {
      return true;
    }

    @Override
    public Object evaluate(Context context) {
      return true;
    }
  }
}
