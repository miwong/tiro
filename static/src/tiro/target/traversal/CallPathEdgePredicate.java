package tiro.target.traversal;

import soot.Unit;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.EdgePredicate;

import java.util.Collection;

public abstract class CallPathEdgePredicate implements EdgePredicate {
    private Collection<Unit> _targetUnits = null;

    public Collection<Unit> getTargetUnits() {
        return _targetUnits;
    }

    public boolean want(Edge e) {
        _targetUnits = this.wantTarget(e);
        return _targetUnits != null && !_targetUnits.isEmpty();
    }

    public abstract Collection<Unit> wantTarget(Edge e);
}
