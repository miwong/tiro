package tiro.target.constraint;

import tiro.Output;

import soot.Type;

import java.util.Set;

public final class ExpressionPredicate extends Predicate {
    private final Expression _expression;

    public ExpressionPredicate(Expression expression) {
        _expression = expression;
    }

    public ExpressionPredicate(Variable variable) {
        _expression = new VariableExpression(variable);
    }

    public ExpressionPredicate(Variable variable, Type type) {
        _expression = new VariableExpression(variable, type);
    }

    @Override
    public Predicate.Operator getOperator() {
        return Predicate.Operator.NONE;
    }

    @Override public boolean isExpression() { return true; }
    @Override public boolean isUnary() { return false; }
    @Override public boolean isBinary() { return false; }

    public Expression getExpression() {
        return _expression;
    }

    public boolean isVariable() {
        return _expression.isVariable();
    }

    public Variable getVariable() {
        return _expression.getVariable();
    }

    @Override
    public boolean containsExpression(Expression expression) {
        return _expression.contains(expression);
    }

    @Override
    public boolean contains(Predicate other) {
        return this.equals(other);
    }

    @Override
    public boolean dependsOnInput() {
        return _expression.dependsOnInput();
    }

    @Override
    public Set<Variable> getAllVariables(Set<Variable> set) {
        _expression.getAllVariables(set);
        return set;
    }

    @Override
    public Set<Variable> searchVariables(Predicate.VariablePredicate predicate,
                                         Set<Variable> result) {
        _expression.searchVariables(predicate, result);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder constraints = new StringBuilder();

        constraints.append("(");
        constraints.append(getExpression().toString());
        constraints.append(")");

        return constraints.toString();
    }

    @Override
    public Predicate clone() {
        return new ExpressionPredicate(_expression);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof ExpressionPredicate)) {
            return false;
        }

        ExpressionPredicate other = (ExpressionPredicate)obj;
        return _expression.equals(other.getExpression());
    }

    @Override
    public void print(int indent) {
        StringBuilder outputString = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            outputString.append("    ");
        }

        outputString.append(_expression.toString());
        Output.printConstraint(outputString.toString());
    }
}
