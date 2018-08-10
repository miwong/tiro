package tiro.target.constraint;

import tiro.Output;

import java.util.HashSet;
import java.util.Set;

// Immutable class to store binary expressions and operations

public abstract class Predicate {
    public enum Operator {
        NONE,
        AND,
        OR,
        NOT
    }

    private static final Predicate TRUE = new ExpressionPredicate(Expression.getTrue());
    private static final Predicate FALSE = new ExpressionPredicate(Expression.getFalse());

    public static Predicate getTrue() {
        return TRUE;
        //return new Predicate(Expression.getTrue());
    }

    public static Predicate getFalse() {
        return FALSE;
        //return new Predicate(Expression.getFalse());
    }

    public static Predicate combine(Operator unaryOperator, Predicate pred) {
        if (pred == null) {
            return null;
        }

        return new UnaryPredicate(unaryOperator, pred);
    }

    public static Predicate combine(Operator binaryOperator, Predicate left, Predicate right) {
        if (left == null && right == null) {
            return null;
        } else if (left == null) {
            return right;
        } else if (right == null) {
            return left;
        }

        if (left.equals(right)) {
            return left;
        }

        // Can do these optimizations here, but would affect performance if unnecessary
        // For now, perform these only at specific situations/applications.
        //if (right.contains(left)) {
        //    if (binaryOperator.equals(Operator.OR)) {
        //        return left;
        //    } else if (binaryOperator.equals(Operator.AND)) {
        //        return right;
        //    }
        //}

        //if (left.contains(right)) {
        //    if (binaryOperator.equals(Operator.OR)) {
        //        return right;
        //    } else if (binaryOperator.equals(Operator.AND)) {
        //        return left;
        //    }
        //}

        //if (left.isOppositeOf(right)) {
        //    if (binaryOperator.equals(Operator.AND)) {
        //        return Predicate.getFalse();
        //    }
        //}

        return new BinaryPredicate(binaryOperator, left, right);
    }

    // ------------------------------------------------------------------------

    public abstract Predicate.Operator getOperator();
    public abstract boolean isExpression();
    public abstract boolean isUnary();
    public abstract boolean isBinary();

    public abstract boolean containsExpression(Expression expression);
    public abstract boolean contains(Predicate other);
    public abstract boolean dependsOnInput();
    public abstract Set<Variable> getAllVariables(Set<Variable> set);
    public Set<Variable> getAllVariables() {
        Set<Variable> result = new HashSet<Variable>();
        getAllVariables(result);
        return result;
    }

    @Override public abstract String toString();
    @Override public abstract Predicate clone();
    @Override public abstract boolean equals(Object other);

    public abstract void print(int indent);

    public void print() {
        print(0);
    }

    public abstract Set<Variable> searchVariables(Predicate.VariablePredicate predicate,
                                                  Set<Variable> result);

    public Set<Variable> searchVariables(Predicate.VariablePredicate predicate) {
        Set<Variable> result = new HashSet<Variable>();
        searchVariables(predicate, result);
        return result;
    }

    public static interface VariablePredicate {
        public boolean want(Variable variable);
    }

    // ------------------------------------------------------------------------

    public boolean isVariable() {
        return isExpression() && ((ExpressionPredicate)this).isVariable();
    }

    public Variable getVariable() {
        return ((ExpressionPredicate)this).getVariable();
    }

    public boolean isTrue() {
        if (isExpression() && ((ExpressionPredicate)this).getExpression().isTrue()) {
            return true;
        }

        return false;
    }

    public boolean isFalse() {
        if (isExpression() && ((ExpressionPredicate)this).getExpression().isFalse()) {
            return true;
        }

        return false;
    }

    // ------------------------------------------------------------------------

    public boolean isOppositeOf(Predicate other) {
        if (other == null) {
            return false;
        }

        if (this.isExpression() && other.isExpression()) {
            return ((ExpressionPredicate)this).getExpression().isOppositeOf(
                    ((ExpressionPredicate)other).getExpression());

        } else if (other.isUnary() && other.getOperator().equals(Operator.NOT)) {
            return this.equals(((UnaryPredicate)other).getChild());

        } else if (this.isUnary() && this.getOperator().equals(Operator.NOT)) {
            return ((UnaryPredicate)this).getChild().equals(other);

        } else if (this.isBinary() && other.isBinary()) {
            BinaryPredicate thisPred = (BinaryPredicate)this;
            BinaryPredicate otherPred = (BinaryPredicate)other;

            if (thisPred.getOperator().equals(Predicate.Operator.OR)
                    && otherPred.getOperator().equals(Predicate.Operator.OR)) {

                if (thisPred.getLeftChild().isOppositeOf(otherPred.getLeftChild())
                        && thisPred.getRightChild().isOppositeOf(otherPred.getRightChild())) {
                    return true;
                }

                if (thisPred.getLeftChild().isOppositeOf(otherPred.getRightChild())
                        && thisPred.getRightChild().isOppositeOf(otherPred.getLeftChild())) {
                    return true;
                }
            } else if (thisPred.getOperator().equals(Predicate.Operator.AND)
                           && otherPred.getOperator().equals(Predicate.Operator.AND)) {

                return thisPred.getLeftChild().isOppositeOf(otherPred.getLeftChild())
                        || thisPred.getRightChild().isOppositeOf(otherPred.getRightChild());
            }

        } else if (this.isExpression()
                       && other.isBinary() && other.getOperator().equals(Operator.AND)) {
            return this.isOppositeOf(((BinaryPredicate)other).getLeftChild())
                    || this.isOppositeOf(((BinaryPredicate)other).getRightChild());

        } else if (this.isBinary() && this.getOperator().equals(Operator.AND)
                       && other.isExpression()) {
            return other.isOppositeOf(((BinaryPredicate)this).getLeftChild())
                    || other.isOppositeOf(((BinaryPredicate)this).getRightChild());
        }

        return false;
    }

    // ------------------------------------------------------------------------

    protected String getOperatorString() {
        switch (getOperator()) {
            case AND: return "and";
            case OR:  return "or";
            case NOT: return "not";
            default:  return "";
        }
    }
}

