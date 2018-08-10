package tiro.target.constraint;

import tiro.Output;

import soot.RefType;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Set;

public final class StringExpression extends Expression {
    private final Expression.Operator _operator;
    private final Expression _left;
    private final Expression _right;

    public StringExpression(Expression.Operator operator, Expression left, Expression right) {
        super(RefType.v("java.lang.String"));
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
    @Override public boolean isArithmeticExpression() { return false; }
    @Override public boolean isStringExpression() { return true; }

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
        return new StringExpression(_operator, _left, _right);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof StringExpression)) {
            return false;
        }

        StringExpression other = (StringExpression)obj;
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
        return this.equals(other);
    }

    @Override
    public boolean implies(Expression other) {
        return false;
    }

    @Override
    public boolean isOppositeOf(Expression other) {
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
