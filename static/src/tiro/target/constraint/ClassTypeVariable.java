package tiro.target.constraint;

import tiro.Output;

import soot.RefType;
import soot.SootMethod;

import java.util.Set;

public class ClassTypeVariable extends SymbolicVariable {
    private final Variable _object;

    public ClassTypeVariable(Variable object) {
        super("Class<" + object.toString() + ">", RefType.v("java.lang.String"));
        _object = object;
    }

    public Variable getObjectVariable() {
        return _object;
    }

    @Override
    public boolean dependsOnInput() {
        return _object != null && _object.dependsOnInput();
    }

    @Override
    public boolean dependsOnInput(int inputNumber) {
        return _object != null && _object.dependsOnInput(inputNumber);
    }

    @Override
    public Set<Variable> getAllVariables(Set<Variable> set) {
        set.add(this);
        _object.getAllVariables(set);
        return set;
    }

    @Override public boolean isInputVariable() { return false; }
    @Override public boolean isSystemVariable() { return false; }
    @Override public boolean isHeapVariable() { return false; }
}
