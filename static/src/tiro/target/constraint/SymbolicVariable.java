package tiro.target.constraint;

import tiro.Output;

import soot.Type;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Set;

public abstract class SymbolicVariable extends Variable {
    private String _symbol;

    protected SymbolicVariable(String symbol, Type type) {
        super(type);
        _symbol = symbol;
    }

    // Note: Calls to this constructor must be followed by a call to setSymbol().
    protected SymbolicVariable(Type type) {
        super(type);
        _symbol = null;
    }

    @Override public boolean isConstant() { return false; }
    @Override public boolean isSymbolic() { return true; }

    public String getSymbol() {
        return _symbol;
    }

    // This should only ever be invoked in a subclass's constructor.
    protected void setSymbol(String newSymbol) {
        _symbol = newSymbol;
    }

    @Override
    public String toString() {
        return _symbol;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SymbolicVariable)) {
            return false;
        }

        SymbolicVariable other = (SymbolicVariable)obj;
        return this.getType().equals(other.getType()) && _symbol.equals(other.getSymbol());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(_symbol)
                .toHashCode();
    }

    @Override
    public boolean dependsOnInput() {
        return isInputVariable();
    }

    @Override
    public boolean dependsOnInput(int inputNumber) {
        return isInputVariable() && ((InputVariable)this).getInputNumber() == inputNumber;
    }

    @Override
    public Set<Variable> getAllVariables(Set<Variable> set) {
        set.add(this);
        return set;
    }

    public abstract boolean isInputVariable();
    public abstract boolean isSystemVariable();
    public abstract boolean isHeapVariable();

    //public abstract boolean isHeapReference();
    //public abstract boolean isReturnVariable();
    //public abstract boolean isDependencyVariable();
}
