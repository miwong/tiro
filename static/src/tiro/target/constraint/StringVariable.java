package tiro.target.constraint;

import tiro.Output;

import soot.RefType;

public final class StringVariable extends ConstantVariable<String> {
    public StringVariable(String value) {
        super(value, RefType.v("java.lang.String"));
    }

    @Override
    public String toString() {
        return "\"" + super.toString() + "\"";
    }
}
