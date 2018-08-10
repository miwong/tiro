package tiro.target.constraint;

import tiro.Output;
import tiro.target.event.CallPath;

import soot.*;

import java.util.List;

public class HeapDependenceConstraintAnalysis extends ConstraintAnalysis {
    private final HeapVariable _heapDependence;
    private Predicate _heapStoreConstraint = null;

    public HeapDependenceConstraintAnalysis(CallPath callPath, HeapVariable heapDependence) {
        super(callPath);
        _heapDependence = heapDependence;
    }

    public Predicate getHeapStoreConstraint() {
        return _heapStoreConstraint;
    }

    @Override
    public List<HeapVariable> getHeapDependencies() {
        List<HeapVariable> result = super.getHeapDependencies();
        result.remove(_heapDependence);
        return result;
    }

    @Override
    protected void processTargetUnit(Unit targetUnit, DataMap dataMap) {
        // Debugging
        //dataMap.LocalMap.forEach(
        //        (x,y) -> { Output.debug("target local map: " + x + " -> " + y); });
        //dataMap.HeapMap.forEach(
        //        (x,y) -> { Output.debug("target heap map: " + x.getNode() + " -> " + y); });

        // Add constraint representing stored heap value
        ExpressionSet heapDepExprSet = new ExpressionSet(_heapDependence.getExpression());
        Type heapDepType = _heapDependence.getExpression().getType();

        for (HeapVariable heapVar : dataMap.HeapMap.keySet()) {
            if (heapVar.intersects(_heapDependence)) {
                ExpressionSet storedExprSet = dataMap.HeapMap.get(heapVar);

                Predicate storeConstraint = null;
                if (Variable.isStringType(heapDepType)) {
                    storeConstraint = ExpressionSet.combine(Expression.Operator.STR_EQ,
                        heapDepExprSet, storedExprSet).toPredicate();
                } else {
                    storeConstraint = ExpressionSet.combine(Expression.Operator.EQ,
                        heapDepExprSet, storedExprSet).toPredicate();
                }

                _heapStoreConstraint = Predicate.combine(Predicate.Operator.OR,
                                                         _heapStoreConstraint,
                                                         storeConstraint);
            }
        }

        //_constraints = Predicate.combine(Predicate.Operator.AND,
        //        _constraints, _heapStoreConstraint);
    }
}
