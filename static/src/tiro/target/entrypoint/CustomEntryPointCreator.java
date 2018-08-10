package tiro.target.entrypoint;

import tiro.Output;

import soot.Body;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.SootClass;
import soot.SootMethod;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.Stmt;
import soot.jimple.infoflow.entryPointCreators.AndroidEntryPointCreator;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class CustomEntryPointCreator extends AndroidEntryPointCreator {
    private Set<MethodOrMethodContext> _entryPoints =
            new HashSet<MethodOrMethodContext>();

    public CustomEntryPointCreator(Collection<String> androidClasses,
            Collection<String> additionalEntryPoints) {
        super(androidClasses, additionalEntryPoints);
    }

    public Set<MethodOrMethodContext> getEntryPoints() {
        return _entryPoints;
    }

    @Override
    protected Stmt buildMethodCall(SootMethod methodToCall, Body body,
                                   Local classLocal, LocalGenerator gen,
                                   Set<SootClass> parentClasses) {
        _entryPoints.add(methodToCall);
        return super.buildMethodCall(methodToCall, body, classLocal, gen, parentClasses);
    }
}
