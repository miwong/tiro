package tiro.target.constraint;

import tiro.Output;

import soot.Type;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Set;
import java.util.regex.Pattern;

public final class VariableExpression extends Expression {
    private static final Pattern _inputRegex = Pattern.compile(".*<Input[0-9]+>.*");

    private final Variable _variable;

    public VariableExpression(Variable variable, Type type) {
        super(type);
        _variable = variable;
    }

    public VariableExpression(Variable variable) {
        this(variable, variable.getType());
    }

    @Override
    public Expression.Operator getOperator() {
        return Expression.Operator.NONE;
    }

    @Override public boolean isVariable() { return true; }
    @Override public boolean isExpression() { return false; }
    @Override public boolean isArithmeticExpression() { return false; }
    @Override public boolean isStringExpression() { return false; }

    public Variable getVariable() {
        return _variable;
    }

    @Override
    public String toString() {
        return _variable.toString();
    }

    @Override
    public Expression clone() {
        return new VariableExpression(_variable, this.getType());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof VariableExpression)) {
            return false;
        }

        VariableExpression other = (VariableExpression)obj;
        return _variable.equals(other.getVariable());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(_variable.toString())
                .toHashCode();
    }

    @Override
    public boolean contains(Expression other) {
        return this.equals(other);
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
        return _variable.dependsOnInput();
        //return _inputRegex.matcher(_variable).matches();
    }

    @Override
    public Set<Variable> getAllVariables(Set<Variable> set) {
        _variable.getAllVariables(set);
        return set;
    }

    @Override
    public Set<Variable> searchVariables(Predicate.VariablePredicate predicate,
                                          Set<Variable> result) {
        if (predicate.want(_variable)) {
            result.add(_variable);
        }
        return result;
    }
}
