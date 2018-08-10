package tiro.target.constraint;

import soot.BooleanType;

final class BooleanVariable extends ConstantVariable<Boolean> {
    public BooleanVariable(boolean value) {
        super(new Boolean(value), BooleanType.v());
    }
}
