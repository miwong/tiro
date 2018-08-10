package tiro.target.callgraph;

import tiro.Output;
import tiro.target.ManifestAnalysis;
import tiro.target.methods.ServiceMethods;

import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraphPatchingTag;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.LocalDefs;
import soot.toolkits.scalar.LocalUses;
import soot.util.Chain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ServicePatcher extends IntentBasedCallGraphPatcher {
    private static final SootClass _serviceClass = Scene.v().getSootClass(
            "android.app.Service");
    private static final String _startServiceMethodSignature =
            "android.content.ComponentName startService(android.content.Intent)";

    public ServicePatcher(SootClass patchClass, ManifestAnalysis manifestAnalysis) {
        super(CallGraphPatchingTag.Kind.Service, patchClass, manifestAnalysis);
    }

    @Override
    public boolean shouldPatch(final Body body, Stmt invokeStmt) {
        SootMethod invokedMethod = invokeStmt.getInvokeExpr().getMethod();
        if (!_cha.isClassSuperclassOfIncluding(_contextClass,
                invokedMethod.getDeclaringClass())) {
            return false;
        }

        if (invokedMethod.getSubSignature().equals(_startServiceMethodSignature)) {
            return true;
        }

        return false;
    }

    @Override
    public void patch(final Body body, UnitGraph cfg, LocalDefs localDefs,
            LocalUses localUses, Stmt invokeStmt) {
        InvokeExpr invoke = invokeStmt.getInvokeExpr();
        List<SootClass> targetServices = findTargetClassesFromIntent(
                body, cfg, localDefs, localUses, invokeStmt, invoke.getArg(0));
        if (targetServices == null) {
            return;
        }

        for (SootClass targetService : targetServices) {
            if (!_cha.isClassSuperclassOf(_serviceClass, targetService)) {
                continue;
            }

            //Output.debug("patching startService in: " + body.getMethod().getSignature());

            // Get bridge method/patch and add tag for call graph generation.
            SootMethod bridgeMethod = getBridgeMethod(invoke.getMethod(), targetService);
            constructServiceBridge(bridgeMethod, targetService);

            applyCallGraphPatch(invokeStmt, bridgeMethod);
        }
    }

    private void constructServiceBridge(SootMethod method, SootClass targetService) {
        JimpleBody body = Jimple.v().newBody(method);
        PatchingChain<Unit> units = body.getUnits();
        Chain<Local> locals = body.getLocals();

        // Handle the parameters.
        List<Local> parameterLocals = handleBridgeMethodParameters(method, units, locals);
        Local intentLocal = parameterLocals.get(1);

        // Instantiate the service.
        Local serviceLocal = Jimple.v().newLocal("service", targetService.getType());
        locals.add(serviceLocal);
        NewExpr newServiceExpr = Jimple.v().newNewExpr(targetService.getType());
        units.add(Jimple.v().newAssignStmt(serviceLocal, newServiceExpr));

        // Call the service's constructor.
        SootMethod initMethod = targetService.getMethod("void <init>()");
        InvokeExpr initInvokeExpr = Jimple.v().newSpecialInvokeExpr(
                serviceLocal, initMethod.makeRef());
        units.add(Jimple.v().newInvokeStmt(initInvokeExpr));

        // Call the lifecycle methods, passing the intent object that started the service.
        for (SootMethodRef lifecycleMethod : ServiceMethods.getLifecycleMethods()) {
            if (lifecycleMethod.parameterTypes().isEmpty()) {
                InvokeExpr lifecycleInvokeExpr = Jimple.v().newVirtualInvokeExpr(
                        serviceLocal, lifecycleMethod);
                units.add(Jimple.v().newInvokeStmt(lifecycleInvokeExpr));
            } else {
                // Service lifecycle methods only take intents or integers as parameters.
                List<Value> arguments = new ArrayList<Value>();
                for (Type parameterType : lifecycleMethod.parameterTypes()) {
                    if (parameterType.toString().equals("android.content.Intent")) {
                        arguments.add(intentLocal);
                    } else if (parameterType.equals(IntType.v())) {
                        arguments.add(IntConstant.v(0));
                    } else {
                        Output.error("Unsupported parameter for service lifecycle method: "
                                + parameterType);
                    }
                }

                InvokeExpr lifecycleInvokeExpr = Jimple.v().newVirtualInvokeExpr(
                        serviceLocal, lifecycleMethod, arguments);
                units.add(Jimple.v().newInvokeStmt(lifecycleInvokeExpr));
            }
        }

        // Return component name
        units.add(Jimple.v().newReturnStmt(StringConstant.v(targetService.getName())));

        method.setActiveBody(body);
    }
}
