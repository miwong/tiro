package tiro.target.constraint;

import tiro.Output;

import soot.SootMethod;
import soot.jimple.InvokeExpr;

import java.util.Set;

public class MethodCallVariable extends SymbolicVariable {
    private final InvokeExpr _invokeExpr;
    private final Variable _receiver;
    private final Variable[] _parameters;

    public MethodCallVariable(InvokeExpr invokeExpr) {
        this(invokeExpr, null, null);
    }

    public MethodCallVariable(InvokeExpr invokeExpr, Variable receiver) {
        this(invokeExpr, receiver, null);
    }

    public MethodCallVariable(InvokeExpr invokeExpr, Variable receiver,
                              Variable[] parameters) {
        super((receiver != null ? receiver.toString() :
                invokeExpr.getMethodRef().declaringClass().getShortName()) + "."
                        + invokeExpr.getMethodRef().name() + "(){" + invokeExpr.hashCode()
                        + "}",
              invokeExpr.getMethod().getReturnType());
        _invokeExpr = invokeExpr;
        _receiver = receiver;
        _parameters = parameters == null ? null : parameters.clone();
    }

    public Variable getReceiverVariable() {
        return _receiver;
    }

    public SootMethod getMethod() {
        return _invokeExpr.getMethod();
    }

    //@Override
    //public String toString() {
    //    String receiver = _receiver != null ? _receiver.toString() :
    //            _invokeExpr.getMethodRef().declaringClass().getShortName();


    //    return receiver + "." + _invokeExpr.getMethodRef().name() + "(" +
    //            _invokeExpr.hashCode() + ")" + "<return>";
    //}

    @Override
    public boolean dependsOnInput() {
        if (_receiver != null && _receiver.dependsOnInput()) {
            return true;
        }
        //for (Variable parameter : _parameters) {
        //    if (parameter != null && parameter.dependsOnInput()) {
        //        return true;
        //    }
        //}

        return false;
    }

    @Override
    public boolean dependsOnInput(int inputNumber) {
        if (_receiver != null && _receiver.dependsOnInput(inputNumber)) {
            return true;
        }

        return false;
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

    //    //for (Variable parameter : _parameters) {
    //    //    if (parameter != null && parameter.isSystemVariable()) {
    //    //        return true;
    //    //    }
    //    //}
    //}

    //@Override
    //public boolean isHeapVariable() {
    //    return false;
    //}

}
