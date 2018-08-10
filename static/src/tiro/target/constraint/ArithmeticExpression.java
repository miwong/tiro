package tiro.target.constraint;

import tiro.target.*;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Set;

public final class ArithmeticExpression extends Expression {
    private final Expression.Operator _operator;
    private final Expression _left;
    private final Expression _right;

    public ArithmeticExpression(Expression.Operator operator,
                                Expression left, Expression right) {
        super(left.getType());
        _operator = operator;
        _left = left;
        _right = right;
    }

    @Override
    public Expression.Operator getOperator() {
        return _operator;
    }

    @Override public boolean isVariable() { return false; }
    @Override public boolean isExpression() { return true; }
    @Override public boolean isArithmeticExpression() { return true; }
    @Override public boolean isStringExpression() { return false; }

    public Expression getLeft() {
        return _left;
    }

    public Expression getRight() {
        return _right;
    }

    @Override
    public String toString() {
        StringBuilder exprString = new StringBuilder();

        exprString.append("(");
        exprString.append(_left.toString());
        exprString.append(" ");
        exprString.append(getOperatorString(_operator));
        exprString.append(" ");
        exprString.append(_right.toString());
        exprString.append(")");

        return exprString.toString();
    }

    @Override
    public Expression clone() {
        return new ArithmeticExpression(_operator, _left, _right);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ArithmeticExpression)) {
            return false;
        }

        ArithmeticExpression other = (ArithmeticExpression)obj;
        return _operator.equals(other.getOperator())
                && _left.equals(other.getLeft())
                && _right.equals(other.getRight());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(_operator)
                .append(_left)
                .append(_right)
                .toHashCode();
    }

    @Override
    public boolean contains(Expression other) {
        if (this.equals(other)) {
            return true;
        }

        return _left.contains(other) || _right.contains(other);
    }

    @Override
    public boolean isEquivalentTo(Expression other) {
        if (this.equals(other)) {
            return true;
        }

        if (!isSimpleExpression() || !other.isSimpleExpression()) {
            return false;
        }

        ArithmeticExpression otherExpr = other.toArithmeticExpression();

        if (_left.isEquivalentTo(otherExpr.getLeft())
                && _right.isEquivalentTo(otherExpr.getRight())) {
            if (_operator.equals(Operator.EQ)) {
                if (otherExpr.getOperator().equals(Operator.LE)
                        || otherExpr.getOperator().equals(Operator.GE)) {
                    return true;
                }
            }

            if (otherExpr.getOperator().equals(Operator.EQ)) {
                if (_operator.equals(Operator.LE) || _operator.equals(Operator.GE)) {
                    return true;
                }
            }

            if (_operator.equals(Operator.NE)) {
                if (otherExpr.getOperator().equals(Operator.LT)
                        || otherExpr.getOperator().equals(Operator.GT)) {
                    return true;
                }
            }

            if (otherExpr.getOperator().equals(Operator.NE)) {
                if (_operator.equals(Operator.LT) || _operator.equals(Operator.GT)) {
                    return true;
                }
            }
        } else if (_left.isEquivalentTo(otherExpr.getLeft())
                    //_left.getVariable().toString().contains("<return>")) {
                    && _right.getVariable().isConstant()
                    && otherExpr.getRight().getVariable().isConstant()) {
            if (_operator.equals(Operator.EQ) && isNotEqualOperator(otherExpr.getOperator())) {
                return true;
            } else if (_operator.equals(Operator.NE)
                        && isEqualOperator(otherExpr.getOperator())) {
                return true;
            }
        } else if (_right.isEquivalentTo(otherExpr.getRight())
                    //_right.getVariable().contains("<return>")) {
                    && _left.getVariable().isConstant()
                    && otherExpr.getLeft().getVariable().isConstant()) {
            if (_operator.equals(Operator.EQ) && isNotEqualOperator(otherExpr.getOperator())) {
                return true;
            } else if (_operator.equals(Operator.NE)
                        && isEqualOperator(otherExpr.getOperator())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean implies(Expression other) {
        if (this.isEquivalentTo(other) && other.isArithmeticExpression()) {
            if (_operator.equals(Operator.EQ)) {
                switch (other.toArithmeticExpression().getOperator()) {
                    case LE: // fall through
                    case GE: // fall through
                    case EQ:
                        return true;
                    default:
                        break;
                }
            } else if (_operator.equals(Operator.NE) && other.isArithmeticExpression()) {
                switch (other.toArithmeticExpression().getOperator()) {
                    case LT: // fall through
                    case GT: // fall through
                    case NE:
                        return true;
                    default:
                        break;
                }
            }
        }

        return false;
    }

    @Override
    public boolean isOppositeOf(Expression other) {
        if (!this.isSimpleExpression() || !other.isSimpleExpression()) {
            return false;
        }

        ArithmeticExpression otherExpr = other.toArithmeticExpression();

        if (_left.equals(otherExpr.getLeft()) && _right.equals(otherExpr.getRight())) {
            if (_operator.equals(Operator.EQ)
                    && Expression.isNotEqualOperator(otherExpr.getOperator())) {
                return true;
            }

            if (otherExpr.getOperator().equals(Operator.EQ)
                    && Expression.isNotEqualOperator(_operator)) {
                return true;
            }
        } else if (_left.equals(otherExpr.getLeft())
                    && _right.getVariable().isConstant()
                    && otherExpr.getRight().getVariable().isConstant()) {
            if (_operator.equals(Operator.EQ) && otherExpr.getOperator().equals(Operator.EQ)) {
                return true;
            }
        }

        //System.out.println("Not opposite: " + _left.toString() + "; "
        //        + other.getLeft().toString());
        return false;
    }

    @Override
    public boolean dependsOnInput() {
        return _left.dependsOnInput() || _right.dependsOnInput();
    }

    @Override
    public Set<Variable> getAllVariables(Set<Variable> set) {
        _left.getAllVariables(set);
        _right.getAllVariables(set);
        return set;
    }

    @Override
    public Set<Variable> searchVariables(Predicate.VariablePredicate predicate,
                                          Set<Variable> result) {
        _left.searchVariables(predicate, result);
        _right.searchVariables(predicate, result);
        return result;
    }
}
