package tiro.target.constraint;

import tiro.Output;

import soot.NullType;

public final class NullVariable extends ConstantVariable<String> {
    static NullVariable _instance = null;

    public static NullVariable getInstance() {
        if (_instance == null) {
            _instance = new NullVariable();
        }

        return _instance;
    }

    private NullVariable() {
        super("null", NullType.v());
    }
}
