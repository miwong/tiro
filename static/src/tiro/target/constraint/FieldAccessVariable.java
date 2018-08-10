package tiro.target.constraint;

import tiro.Output;

import soot.SootField;
import soot.jimple.FieldRef;

import java.util.Set;

public class FieldAccessVariable extends SymbolicVariable {
    private final FieldRef _fieldRef;
    private final Variable _receiver;

    public FieldAccessVariable(FieldRef fieldRef, Variable receiver) {
        super((receiver == null ? receiver.toString()
                                : fieldRef.getField().getDeclaringClass().getShortName())
                      + "." + fieldRef.getField().getName(),
              fieldRef.getField().getType());
        _fieldRef = fieldRef;
        _receiver = receiver;
    }

    public Variable getReceiverVariable() {
        return _receiver;
    }

    public SootField getField() {
        return _fieldRef.getField();
    }

    @Override
    public boolean dependsOnInput() {
        return _receiver != null && _receiver.dependsOnInput();
    }

    @Override
    public boolean dependsOnInput(int inputNumber) {
        return _receiver != null && _receiver.dependsOnInput(inputNumber);
    }

    @Override
    public Set<Variable> getAllVariables(Set<Variable> set) {
        set.add(this);
        _receiver.getAllVariables(set);
        return set;
    }

    @Override public boolean isInputVariable() { return false; }
    @Override public boolean isSystemVariable() { return false; }
    @Override public boolean isHeapVariable() { return false; }

    //@Override
    //public boolean isInputVariable() {
    //    if (_receiver == null || !_receiver.isSymbolic()) {
    //        return false;
    //    }

    //    SymbolicVariable symbolicReceiver = (SymbolicVariable)_receiver;
    //    return symbolicReceiver.isInputVariable();
    //}

    //@Override
    //public boolean isSystemVariable() {
    //    if (_receiver == null || !_receiver.isSymbolic()) {
    //        return false;
    //    }

    //    SymbolicVariable symbolicReceiver = (SymbolicVariable)_receiver;
    //    return symbolicReceiver.isSystemVariable();
    //}

    //@Override
    //public boolean isHeapVariable() {
    //    return false;
    //}
}
