package tiro.target.constraint;

import tiro.Output;
import tiro.target.*;

import java.util.Set;

public final class BinaryPredicate extends Predicate {
    private final Operator _operator;
    private final Predicate _leftChild;
    private final Predicate _rightChild;

    public BinaryPredicate(Operator operator, Predicate leftChild, Predicate rightChild) {
        _operator = operator;
        _leftChild = leftChild;
        _rightChild = rightChild;
    }

    @Override
    public Predicate.Operator getOperator() {
        return _operator;
    }

    @Override public boolean isExpression() { return false; }
    @Override public boolean isUnary() { return false; }
    @Override public boolean isBinary() { return true; }

    public Predicate getLeftChild() {
        return _leftChild;
    }

    public Predicate getRightChild() {
        return _rightChild;
    }

    @Override
    public boolean containsExpression(Expression expression) {
        return _leftChild.containsExpression(expression)
                || _rightChild.containsExpression(expression);
    }

    @Override
    public boolean contains(Predicate other) {
        if (this.equals(other)) {
            return true;
        }

        if (_operator.equals(Predicate.Operator.AND)) {
            return _leftChild.contains(other) || _rightChild.contains(other);
        } else if (_operator.equals(Predicate.Operator.OR)) {
            return _leftChild.contains(other) && _rightChild.contains(other);
        }

        return false;
    }

    @Override
    public boolean dependsOnInput() {
        return _leftChild.dependsOnInput() || _rightChild.dependsOnInput();
    }

    @Override
    public Set<Variable> getAllVariables(Set<Variable> set) {
        _leftChild.getAllVariables(set);
        _rightChild.getAllVariables(set);
        return set;
    }

    @Override
    public Set<Variable> searchVariables(Predicate.VariablePredicate predicate,
                                         Set<Variable> result) {
        _leftChild.searchVariables(predicate, result);
        _rightChild.searchVariables(predicate, result);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder constraints = new StringBuilder();

        if (_leftChild.isExpression()
                || (_leftChild.isBinary()
                    && _leftChild.getOperator().equals(getOperator()))) {
            constraints.append(_leftChild.toString());
        } else {
            constraints.append("(");
            constraints.append(_leftChild.toString());
            constraints.append(")");
        }

        constraints.append(getOperatorString());

        if (_rightChild.isExpression()
                || (_rightChild.isBinary()
                    && _rightChild.getOperator().equals(getOperator()))) {
            constraints.append(_rightChild.toString());
        } else {
            constraints.append("(");
            constraints.append(_rightChild.toString());
            constraints.append(")");
        }

        return constraints.toString();
    }

    @Override
    public Predicate clone() {
        return new BinaryPredicate(_operator, _leftChild, _rightChild);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof BinaryPredicate)) {
            return false;
        }

        BinaryPredicate other = (BinaryPredicate)obj;
        return _operator.equals(other.getOperator())
                && _leftChild.equals(other.getLeftChild())
                && _rightChild.equals(other.getRightChild());
    }

    @Override
    public void print(int indent) {
        if (_leftChild.isBinary() && _leftChild.getOperator().equals(_operator)) {
            _leftChild.print(indent);
        } else {
            _leftChild.print(indent + 1);
        }

        StringBuilder outputString = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            outputString.append("    ");
        }
        outputString.append(getOperatorString());
        Output.printConstraint(outputString.toString());

        if (_rightChild.isBinary() && _rightChild.getOperator().equals(_operator)) {
            _rightChild.print(indent);
        } else {
            _rightChild.print(indent + 1);
        }
    }
}
