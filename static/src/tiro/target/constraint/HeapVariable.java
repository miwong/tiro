package tiro.target.constraint;

import tiro.Output;
import tiro.target.dependency.Dependence;

import soot.Local;
import soot.PointsToSet;
import soot.RefLikeType;
import soot.Scene;
import soot.SootField;
import soot.jimple.FieldRef;
import soot.jimple.InstanceFieldRef;

import org.apache.commons.lang3.builder.HashCodeBuilder;

// Encapsulates different representations of a value that is stored in a field (i.e. heap)
// Note: Points-to set is null for fields holding primitive types, so the field itself is
//       used as the identifier for static cases, and the pointsto + field for instance cases.

public class HeapVariable extends SymbolicVariable implements Dependence {
    private final PointsToSet _pointsTo;
    private final SootField _field;
    private final Expression _expr;

    public HeapVariable(FieldRef fieldRef) {
        super(fieldRef.getField().getType());

        if (fieldRef instanceof InstanceFieldRef) {
            InstanceFieldRef instanceRef = (InstanceFieldRef)fieldRef;

            PointsToSet pointsTo = Scene.v().getPointsToAnalysis().reachingObjects(
                    (Local)instanceRef.getBase(), instanceRef.getField());
            if (pointsTo.isEmpty()) {
                // If the instance field is a primitive type, use the pointsTo for the base
                pointsTo = Scene.v().getPointsToAnalysis().reachingObjects(
                        (Local)instanceRef.getBase());
            }

            _pointsTo = pointsTo;
            _field = instanceRef.getField();
        } else {
            _pointsTo = Scene.v().getPointsToAnalysis().reachingObjects(fieldRef.getField());
            _field = fieldRef.getField();
        }

        String heapIdentifier = "Heap<" + _field.getDeclaringClass().getShortName() + "."
                + _field.getName();
        if (hasPointsToSet()) {
            heapIdentifier += "{" + _pointsTo.hashCode() + "}";
        }
        heapIdentifier += ">";
        setSymbol(heapIdentifier);

        _expr = new VariableExpression(this);
    }

    //public HeapVariable(PointsToSet pointsTo, SootField field) {
    //    _pointsTo = pointsTo;
    //    _field = field;
    //    _expr = generateExpression();
    //}

    @Override public boolean isInputVariable() { return false; }
    @Override public boolean isSystemVariable() { return false; }
    @Override public boolean isHeapVariable() { return true; }

    public PointsToSet getPointsToSet() {
        return _pointsTo;
    }

    public SootField getField() {
        return _field;
    }

    public boolean hasPointsToSet() {
        return !_pointsTo.isEmpty();
    }

    public Expression getExpression() {
        return _expr;
    }

    //private Expression generateExpression() {
    //    String heapIdentifier = "Heap<" + _field.getDeclaringClass().getShortName() + "." +
    //        _field.getName();

    //    if (hasPointsToSet()) {
    //        heapIdentifier += "(" + _pointsTo.hashCode() + ")";
    //    }

    //    heapIdentifier += ">";
    //    return new VariableExpression(heapIdentifier, _field.getType());
    //}

    public boolean intersects(HeapVariable other) {
        if (this.hasPointsToSet() && other.hasPointsToSet()) {
            return _pointsTo.hasNonEmptyIntersection(other._pointsTo)
                    && _field.equals(other._field);
        } else if (!this.hasPointsToSet() && !other.hasPointsToSet()) {
            return _field.equals(other._field);
        }

        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof HeapVariable)) {
            return false;
        }

        HeapVariable other = (HeapVariable)obj;
        if (this.hasPointsToSet() && other.hasPointsToSet()) {
            return _pointsTo.equals(other._pointsTo) && _field.equals(other._field);
        } else if (!this.hasPointsToSet() && !other.hasPointsToSet()) {
            return _field.equals(other._field);
        }

        return false;
    }

    @Override
    public int hashCode() {
        if (this.hasPointsToSet()) {
            return new HashCodeBuilder().append(_pointsTo).append(_field).toHashCode();
            //return _pointsTo.hashCode() * 19 + _field.hashCode();
        } else {
            return _field.hashCode();
        }
    }
}
