package tiro.target.constraint;

import tiro.Output;

import soot.jimple.InvokeExpr;

public final class SystemVariable extends MethodCallVariable {
    public SystemVariable(InvokeExpr expr) {
        super(expr);
    }

    @Override public boolean isInputVariable() { return false; }
    @Override public boolean isSystemVariable() { return true; }
    @Override public boolean isHeapVariable() { return false; }

    @Override
    public String toString() {
        return "<System>(" + super.toString() + ")";
    }
}
