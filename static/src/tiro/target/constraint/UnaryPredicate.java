package tiro.target.constraint;

import tiro.Output;

import java.util.Set;

final class UnaryPredicate extends Predicate {
    private final Operator _operator;
    private final Predicate _child;

    public UnaryPredicate(Operator operator, Predicate child) {
        _operator = operator;
        _child = child;
    }

    @Override
    public Predicate.Operator getOperator() {
        return _operator;
    }

    @Override public boolean isExpression() { return false; }
    @Override public boolean isUnary() { return true; }
    @Override public boolean isBinary() { return false; }

    public Predicate getChild() {
        return _child;
    }

    @Override
    public boolean containsExpression(Expression expression) {
        return _child.containsExpression(expression);
    }

    @Override
    public boolean contains(Predicate other) {
        return this.equals(other);
    }

    @Override
    public boolean dependsOnInput() {
        return _child.dependsOnInput();
    }

    @Override
    public Set<Variable> getAllVariables(Set<Variable> set) {
        _child.getAllVariables(set);
        return set;
    }

    @Override
    public Set<Variable> searchVariables(Predicate.VariablePredicate predicate,
                                         Set<Variable> result) {
        _child.searchVariables(predicate, result);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder constraints = new StringBuilder();
        constraints.append(getOperatorString());

        if (_child.isExpression()) {
            constraints.append(_child.toString());
        } else {
            constraints.append("(");
            constraints.append(_child.toString());
            constraints.append(")");
        }

        return constraints.toString();
    }

    @Override
    public Predicate clone() {
        return new UnaryPredicate(_operator, _child);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof UnaryPredicate)) {
            return false;
        }

        UnaryPredicate other = (UnaryPredicate)obj;
        return _operator.equals(other.getOperator()) && _child.equals(other.getChild());
    }

    @Override
    public void print(int indent) {
        StringBuilder outputString = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            outputString.append("    ");
        }

        outputString.append(getOperatorString());
        Output.printConstraint(outputString.toString());

        _child.print(indent + 1);
    }
}
