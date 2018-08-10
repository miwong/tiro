package tiro.target.constraint;

import tiro.Output;
import tiro.target.event.CallPath;

import soot.Type;

public final class InputVariable extends SymbolicVariable {
    private final int _inputNumber;

    public InputVariable(CallPath callPath, int inputNumber, Type type) {
        super("<Input" + inputNumber + ">{" + callPath.hashCode() + "}", type);
        _inputNumber = inputNumber;
    }

    public int getInputNumber() {
        return _inputNumber;
    }

    @Override public boolean isInputVariable() { return true; }
    @Override public boolean isSystemVariable() { return false; }
    @Override public boolean isHeapVariable() { return false; }
}
