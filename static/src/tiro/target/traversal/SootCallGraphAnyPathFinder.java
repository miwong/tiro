package tiro.target.traversal;

import tiro.Output;

import soot.MethodOrMethodContext;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.EdgePredicate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

// Performs a depth-first search over soot's call-graph by its following edges.  An edge
// predicate object should be passed in that indicates whether a particular target
// edge/node is interesting and its path should be returned.

// This is a stack-based search and should only be used for an any-path traversal of the
// call-graph.

public class SootCallGraphAnyPathFinder {
    protected final CallGraph _graph;
    private final Iterator<Edge> _entryPoints;

    protected final Stack<Edge> _currentPath = new Stack<Edge>();
    private final EdgePredicate _edgePredicate;

    protected final Map<MethodOrMethodContext, Iterator<Edge>> _pendingEdges =
            new LinkedHashMap<MethodOrMethodContext, Iterator<Edge>>(1000);

    public SootCallGraphAnyPathFinder(CallGraph graph, MethodOrMethodContext entryMethod,
            EdgePredicate edgePredicate) {
        _graph = graph;
        _entryPoints = graph.edgesInto(entryMethod);
        _edgePredicate = edgePredicate;

        initializeTraversal();
    }

    public SootCallGraphAnyPathFinder(CallGraph graph,
            Iterator<MethodOrMethodContext> entryMethods, EdgePredicate edgePredicate) {
        _graph = graph;

        ArrayList<Edge> entryEdges = new ArrayList<Edge>();
        while (entryMethods.hasNext()) {
            Iterator<Edge> edgeIter = graph.edgesInto(entryMethods.next());
            while (edgeIter.hasNext()) {
                entryEdges.add(edgeIter.next());
            }
        }
        _entryPoints = entryEdges.iterator();
        _edgePredicate = edgePredicate;

        initializeTraversal();
    }

    private void initializeTraversal() {
        if (_entryPoints != null && _entryPoints.hasNext()) {
            Edge entryEdge = _entryPoints.next();

            _currentPath.push(entryEdge);
            _pendingEdges.put(entryEdge.getTgt(), computeChildren(entryEdge));
        }
    }

    public List<Edge> next() {
        while (!_currentPath.empty()) {
            Edge currentEdge = _currentPath.peek();

            if (_edgePredicate.want(currentEdge)) {
                List<Edge> path = currentPath();
                continueTraversal();
                return path;
            }

            continueTraversal();
        }

        return null;
    }

    protected List<Edge> currentPath() {
        ArrayList<Edge> result = new ArrayList<Edge>();
        _currentPath.iterator().forEachRemaining(e -> { result.add(e); });
        return result;
    }

    private void continueTraversal() {
        while (!_currentPath.empty()) {
            Edge currentEdge = _currentPath.peek();
            Iterator<Edge> children = _pendingEdges.get(currentEdge.getTgt());

            while (children.hasNext()) {
                Edge child = children.next();

                if (!_pendingEdges.containsKey(child.getTgt())) {
                    // This is a new node we have not yet explored.
                    _currentPath.push(child);
                    _pendingEdges.put(child.getTgt(), computeChildren(child));
                    return;
                }
            }

            // We have no more unvisited edges for the current node, so move backwards in the
            // the current path.
            _currentPath.pop();
        }

        // We're done with the paths stemming from the current entry-point.  Move on to the
        // next one.
        while (_entryPoints.hasNext()) {
            Edge nextEntryPoint = _entryPoints.next();

            if (!_pendingEdges.containsKey(nextEntryPoint.getTgt())) {
                // We have not yet visited this entry-point during our previous exploration.
                _currentPath.push(nextEntryPoint);
                _pendingEdges.put(nextEntryPoint.getTgt(), computeChildren(nextEntryPoint));
                return;
            }
        }

        return;
    }

    protected Iterator<Edge> computeChildren(Edge edge) {
        // CallGraph.edgesOutOf() sometimes return non-sensical edges...
        List<Edge> result = new ArrayList<Edge>();

        Iterator<Edge> outEdgeIter = _graph.edgesOutOf(edge.getTgt());
        while (outEdgeIter.hasNext()) {
            Edge outEdge = outEdgeIter.next();

            if (outEdge.srcUnit() != null) {
                result.add(outEdge);
            }
        }

        return result.iterator();
    }
}
