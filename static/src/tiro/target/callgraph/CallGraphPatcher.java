package tiro.target.callgraph;

import soot.Body;
import soot.Hierarchy;
import soot.Local;
import soot.Modifier;
import soot.PatchingChain;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.ParameterRef;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.CallGraphPatchingTag;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.LocalDefs;
import soot.toolkits.scalar.LocalUses;
import soot.toolkits.scalar.UnitValueBoxPair;
import soot.util.Chain;

import java.util.ArrayList;
import java.util.List;

abstract class CallGraphPatcher {
    protected final CallGraphPatchingTag.Kind _kind;
    protected final SootClass _patchClass;
    protected final Hierarchy _cha;

    public abstract boolean shouldPatch(final Body body, Stmt invokeStmt);
    public abstract void patch(final Body body, UnitGraph cfg, LocalDefs localDefs,
            LocalUses localUses, Stmt invokeStmt);

    protected CallGraphPatcher(CallGraphPatchingTag.Kind kind, SootClass patchClass) {
        _kind = kind;
        _patchClass = patchClass;
        _cha = Scene.v().getActiveHierarchy();
    }

    protected void applyCallGraphPatch(Stmt invokeStmt, SootMethod targetMethod) {
        invokeStmt.addTag(new CallGraphPatchingTag(_kind, targetMethod));
    }

    protected SootMethod getBridgeMethod(SootMethod invokedMethod, SootClass targetClass) {
        String bridgeMethodName = generateBridgeMethodName(targetClass);

        // TODO: should check for method signature (name + parameter types)
        if (_patchClass.declaresMethodByName(bridgeMethodName)) {
            return _patchClass.getMethodByName(bridgeMethodName);
        } else {
            // Construct fake method with the signature:
            //     returnType bridgeMethod(receiverTypeIfExists, {parameterTypes})
            List<Type> parameters = new ArrayList<Type>();
            if (!invokedMethod.isStatic()) {
                parameters.add(invokedMethod.getDeclaringClass().getType());
            }
            parameters.addAll(invokedMethod.getParameterTypes());

            SootMethod bridgeMethod = new SootMethod(bridgeMethodName,
                    parameters, invokedMethod.getReturnType(),
                    Modifier.PUBLIC | Modifier.STATIC);
            _patchClass.addMethod(bridgeMethod);
            return bridgeMethod;
        }
    }

    protected List<Local> handleBridgeMethodParameters(SootMethod bridgeMethod,
            PatchingChain<Unit> units, Chain<Local> locals) {
        List<Local> parameterLocals = new ArrayList<Local>();

        // Note: bridge methods are always static, so there is no "this" parameter.
        for (int i = 0; i < bridgeMethod.getParameterCount(); i++) {
            Type parameterType = bridgeMethod.getParameterType(i);
            Local parameterLocal = Jimple.v().newLocal("parameter" + i, parameterType);
            locals.add(parameterLocal);
            ParameterRef parameterRef = Jimple.v().newParameterRef(parameterType, i);
            units.add(Jimple.v().newIdentityStmt(parameterLocal, parameterRef));

            parameterLocals.add(parameterLocal);
        }

        return parameterLocals;
    }

    private String generateBridgeMethodName(SootClass targetClass) {
        return String.format("bridge_%s_%s",
                getPatchKindName(), targetClass.getName().replace('.', '_'));
    }

    private String getPatchKindName() {
        switch (_kind) {
            //case Reflection: return "reflection";
            //case NativeMethod: return "native";
            case Activity: return "activity";
            case Service: return "service";
            case BroadcastReceiver: return "receiver";
            case AsyncTask: return "async";
            case Executor: return "executor";
            case Thread: return "thread";
            default: return "<unknown>";
        }
    }

    // Method to process the statements retrieved when following a def-use chain.
    protected static interface DefUseProcessingFunction {
        public void process(Stmt stmt);
    }

    protected void processDefUseForBuilderPattern(final Body body, LocalDefs localDefs,
            LocalUses localUses, Stmt stmt, Local local, DefUseProcessingFunction processor) {
        for (Unit defUnit : localDefs.getDefsOfAt(local, stmt)) {
            Stmt defStmt = (Stmt)defUnit;
            processor.process(defStmt);

            for (UnitValueBoxPair useUnitValue : localUses.getUsesOf(defUnit)) {
                Stmt useStmt = (Stmt)useUnitValue.getUnit();
                processor.process(useStmt);
            }

            // If this is an invocation statement where the return type is the same as the
            // receiver type, then the code is likely using the builder pattern.  Continue
            // following the defs of the receiver value.
            if (!defStmt.containsInvokeExpr()) {
                continue;
            }
            InvokeExpr invoke = defStmt.getInvokeExpr();
            if (!(invoke instanceof InstanceInvokeExpr)) {
                continue;
            }

            InstanceInvokeExpr instanceInvoke = (InstanceInvokeExpr)invoke;
            SootMethod method = instanceInvoke.getMethod();

            if (method.getReturnType().equals(method.getDeclaringClass().getType())) {
                processDefUseForBuilderPattern(body, localDefs, localUses, defStmt,
                        (Local)instanceInvoke.getBase(), processor);
            }
        }
    }
}
