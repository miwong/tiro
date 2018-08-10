package tiro.target.traversal;

import soot.*;
import soot.jimple.toolkits.callgraph.*;

import java.util.*;

class AndroidAnyPathFinder extends SootCallGraphAnyPathFinder {
    public AndroidAnyPathFinder(CallGraph graph, MethodOrMethodContext entryMethod,
            EdgePredicate edgePredicate) {
        super(graph, entryMethod, edgePredicate);
    }

    public AndroidAnyPathFinder(CallGraph graph, Iterator<MethodOrMethodContext> entryMethods,
            EdgePredicate edgePredicate) {
        super(graph, entryMethods, edgePredicate);
    }

    @Override
    protected Iterator<Edge> computeChildren(Edge edge) {
        // Filter the traversal to only application classes.
        SootClass currentClass = edge.getTgt().method().getDeclaringClass();

        if (!currentClass.isApplicationClass()
                || currentClass.getName().startsWith("android.support.v")) {
            return Collections.<Edge>emptyList().iterator();
        }

        return super.computeChildren(edge);
    }
}
