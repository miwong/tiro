package tiro.target.constraint;

import tiro.Output;

import soot.DoubleType;
import soot.FloatType;
import soot.IntType;
import soot.LongType;
import soot.ShortType;

public final class NumberVariable extends ConstantVariable<Number> {
    public NumberVariable(int number) {
        super(new Integer(number), IntType.v());
    }

    public NumberVariable(short number) {
        super(new Short(number), ShortType.v());
    }

    public NumberVariable(long number) {
        super(new Long(number), LongType.v());
    }

    public NumberVariable(float number) {
        super(new Float(number), FloatType.v());
    }

    public NumberVariable(double number) {
        super(new Double(number), DoubleType.v());
    }
}
