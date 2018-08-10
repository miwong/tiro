package tiro.target.constraint;

import tiro.Output;

import soot.Type;

public final class PlaceholderVariable extends SymbolicVariable {
    public PlaceholderVariable(String symbol, Type type) {
        super(symbol, type);
    }

    @Override public boolean isInputVariable() { return false; }
    @Override public boolean isSystemVariable() { return false; }
    @Override public boolean isHeapVariable() { return false; }
}
