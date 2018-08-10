package tiro.target.traversal;

import tiro.Output;
import tiro.target.entrypoint.EntryPointAnalysis;
import tiro.target.event.CallPath;

import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.*;

import java.util.*;

/*
 * A class that gathers all necessary information from a single traversal of the call graph.
 * If a specific type of information needs to be gathered, implement a subclass of
 * CallGraphTraversalPlugin which specifies whether a Unit (i.e. instruction) is of interest
 * stores the resulting call path for that instruction.
 *
 * An aggregate edge predicate is used with the path finder to track which plugin has
 * expressed interest in a given instruction.
 */

public class CallGraphTraversal extends SceneTransformer {
    public static interface Plugin {
        // Process the given unit and return true if this unit should be targeted
        public boolean processUnit(SootMethod method, Unit unit);

        // Process the resulting targeted call path
        public void onTargetPath(CallPath path);
    }

    private final EntryPointAnalysis _entryPointAnalysis;
    private final List<Plugin> _plugins = new ArrayList<Plugin>();

    public CallGraphTraversal(EntryPointAnalysis entryPointAnalysis) {
        _entryPointAnalysis = entryPointAnalysis;
    }

    public void addPlugin(Plugin plugin) {
        _plugins.add(plugin);
    }

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        Output.progress("Traversing the call graph");

        PluginBasedEdgePredicate edgePredicate = new PluginBasedEdgePredicate(_plugins);
        AndroidAnyPathFinder pathFinder = new AndroidAnyPathFinder(
                Scene.v().getCallGraph(), _entryPointAnalysis.getEntryPoints().iterator(),
                edgePredicate);

        for (List<Edge> path = pathFinder.next(); path != null; path = pathFinder.next()) {
            for (Plugin plugin : _plugins) {
                for (Unit targetUnit : edgePredicate.getTargetUnitsForPlugin(plugin)) {
                    CallPath newCallPath = new CallPath(path, targetUnit);
                    plugin.onTargetPath(newCallPath);
                }
            }
        }
    }

    private class PluginBasedEdgePredicate implements EdgePredicate {
        private final Map<Plugin, List<Unit>> _pluginTargets =
                new HashMap<Plugin, List<Unit>>();

        public PluginBasedEdgePredicate(List<Plugin> plugins) {
            plugins.forEach(p -> { _pluginTargets.put(p, new ArrayList<Unit>()); });
        }

        @Override
        public boolean want(Edge e) {
            SootMethod tgtMethod = e.getTgt().method();
            if (!tgtMethod.hasActiveBody()) {
                return false;
            }

            clearPluginTargets();
            boolean isTarget = false;

            for (Unit unit : tgtMethod.getActiveBody().getUnits()) {
                for (Map.Entry<Plugin, List<Unit>> pluginEntry : _pluginTargets.entrySet()) {
                    if (pluginEntry.getKey().processUnit(tgtMethod, unit)) {
                        pluginEntry.getValue().add(unit);
                        isTarget = true;
                    }
                }
            }

            return isTarget;
        }

        public List<Unit> getTargetUnitsForPlugin(Plugin plugin) {
            return _pluginTargets.get(plugin);
        }

        private void clearPluginTargets() {
            _pluginTargets.forEach((p, t) -> { t.clear(); });
        }
    }
}
