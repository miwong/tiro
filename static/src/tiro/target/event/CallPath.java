package tiro.target.event;

import tiro.Output;

import soot.MethodOrMethodContext;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.Targets;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.ArrayList;
import java.util.List;

// Class to hold a call path.
// Should be constructed with a list of edges:
//     entryPoint->node1, node1->node2, ..., nodeX->target node
// and with a target Unit, where the unit appears in the target node's body.
// A CallPath object should be able to represent any point in the app's code.

public final class CallPath {
    protected final List<Edge> _edges;
    protected final List<MethodOrMethodContext> _nodes;
    protected final Unit _targetUnit;

    public CallPath(List<Edge> edges, Unit targetUnit) throws IllegalArgumentException {
        // Some quick error checking
        for (int i = 0; i < edges.size() - 1; i++) {
            if (!edges.get(i).getTgt().equals(edges.get(i + 1).getSrc())) {
                throw new IllegalArgumentException("Call path edges do not match");
            }
        }

        // Debugging
        //edges.forEach(e -> { Output.debug("edge: " + e); });

        // Remove the first edge since it's just dummy method->entrypoint
        _edges = new ArrayList<Edge>(edges.size() - 1);
        _edges.addAll(edges.subList(1, edges.size()));

        _nodes = new ArrayList<MethodOrMethodContext>(edges.size());
        edges.forEach(e -> { _nodes.add(e.getTgt()); });

        _targetUnit = targetUnit;
    }

    public List<Edge> getEdges() {
        return _edges;
    }

    public List<MethodOrMethodContext> getNodes() {
        return _nodes;
    }

    public Unit getTargetUnit() {
        return _targetUnit;
    }

    public SootMethod getEntryMethod() {
        return _nodes.get(0).method();
    }

    public SootMethod getTargetMethod() {
        return _nodes.get(_nodes.size() - 1).method();
    }

    public void print() {
        _nodes.forEach(m -> { Output.printPath(m.method().toString()); });
        Output.printPath(_targetUnit.toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!(obj instanceof CallPath)) {
            return false;
        }

        CallPath other = (CallPath)obj;
        return this.getTargetMethod().equals(other.getTargetMethod())
                && this.getTargetUnit().equals(other.getTargetUnit());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(this.getTargetMethod())
                .append(this.getTargetUnit())
                .toHashCode();
    }
}
