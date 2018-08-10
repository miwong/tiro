package tiro.target.constraint;

import tiro.Output;
import tiro.target.StaticAnalysisTimeoutException;

import soot.*;
import soot.jimple.*;
import soot.shimple.*;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.ForwardBranchedFlowAnalysis;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.toolkits.scalar.ValueUnitPair;

import java.util.*;
import java.util.stream.Collectors;

class IntraproceduralConstraintAnalysis extends ForwardBranchedFlowAnalysis<DataMap> {
    private final UnitGraph _cfg;

    // Track number of times a stmt has been processed ensure convergence
    private final Map<Unit, Integer> _flowThroughCounts = new HashMap<Unit, Integer>();
    private static final int MAX_FLOW_THROUGH_COUNT = 3;

    // Live local analysis to avoid propagating values that are dead
    private final SimpleLiveLocals _liveLocalsAnalysis;

    // Stores the data values that are passed to this method (jumps start data flow analysis)
    private final DataMap _parameterMap;

    // Store methods that we do not want to analyze as auxilliary methods when we encounter an
    // invoke expression (avoids infinitely looping for recursive paths)
    private final Set<SootMethod> _excludeMethods;

    // Current depth of auxiliary method processing (0 = processing a method on the call path)
    private final int _auxDepth;

    // Access to heap variables that may need to be resolved via event chains
    private final Set<HeapVariable> _heapDependencies = new HashSet<HeapVariable>();

    // Track read heap variables to constrain multiple reads
    private final Set<HeapVariable> _readHeapVariables = new HashSet<HeapVariable>();

    public IntraproceduralConstraintAnalysis(UnitGraph graph, DataMap parameterMap,
            Set<SootMethod> excludeMethods) {
        this(graph, parameterMap, excludeMethods, 0);
    }

    public IntraproceduralConstraintAnalysis(UnitGraph graph, DataMap parameterMap,
            Set<SootMethod> excludeMethods, int auxDepth) {
        super(graph);
        _cfg = graph;
        _parameterMap = parameterMap;
        _excludeMethods = excludeMethods;
        _auxDepth = auxDepth;

        _cfg.getBody().getUnits().forEach(u -> { _flowThroughCounts.put(u, 0); });
        _liveLocalsAnalysis = new SimpleLiveLocals(graph);

        //Output.debug("IntraproceduralConstraintAnalysis: " + graph.getBody().getMethod());
        //if (graph.getBody().getMethod().getSignature().contains("handleSms")) {
        //    Output.debug("IntraproceduralConstraintAnalysis: "
        //            + graph.getBody().getMethod());
        //    printDataMapDebug(parameterMap);
        //}

        doAnalysis();
    }

    public Set<HeapVariable> getHeapDependencies() {
        return _heapDependencies;
    }

    protected DataMap newInitialFlow() {
        //return _parameterMap.clone();
        return new DataMap();
    }

    protected DataMap entryInitialFlow() {
        //return _parameterMap.clone();
        DataMap dataMap = new DataMap();
        dataMap.HeapMap.putAll(_parameterMap.HeapMap);
        return dataMap;
    }

    protected void copy(DataMap source, DataMap dest) {
        dest.copy(source);
    }

    protected void merge(DataMap in1, DataMap in2, DataMap out) {
        out.merge(in1, in2);
    }

    protected void flowThrough(DataMap in, Unit s, List<DataMap> fallOut,
            List<DataMap> branchOuts) {
        // In cases where the constraints are complex (e.g. in a long method with many loops),
        // make sure that we detect timeouts and stop analysis in for the current path.
        if (Thread.interrupted()) {
            throw new StaticAnalysisTimeoutException("ConstraintAnalysis");
        }

        // In future, may want to look at expressions that have changed and remove them
        if (_flowThroughCounts.get(s) >= MAX_FLOW_THROUGH_COUNT) {
            return;
        }

        // Debugging
        //if (_cfg.getBody().getMethod().getSignature().contains("onCreate")) {
        //    Output.debug(".....................................");
        //    Output.debug("flowThrough (" + s.getClass().getName() + "): " + s);
        //    if (((Stmt)s).fallsThrough()) {
        //        printDataMapDebug(fallOut.get(0));
        //    }
        //    //printDataMapDebug(in);
        //}
        //if (_cfg.getBody().getMethod().getSignature().contains("bcccdc5482")) {
        //    Output.debug(".....................................");
        //    Output.debug("flowThrough (" + _cfg.getBody().getMethod().getName() + ") ["
        //            + s.hashCode() + "] [" + _flowThroughCounts.get(s) + "]: " + s);
        //    printDataMapDebug(in);
        //}

        _flowThroughCounts.put(s, _flowThroughCounts.get(s) + 1);
        _constraintStmtSwitch.initialize(in, fallOut, branchOuts);
        s.apply(_constraintStmtSwitch);

        removeDeadLocals(s, fallOut, branchOuts);
    }

    protected void printDataMapDebug(DataMap dataMap) {
        dataMap.LocalMap.forEach((x,y) -> { Output.debug(String.format("%8s -> %s", x, y)); });
        dataMap.HeapMap.forEach((x,y) -> { Output.debug("  " + x + " -> " + y); });
        //if (dataMap.ControlFlowConstraint != null) {
        //    Output.debug("  " + dataMap.ControlFlowConstraint);
        //    dataMap.ControlFlowConstraint.print(1);
        //}
    }

    protected void removeDeadLocals(Unit s, List<DataMap> fallOut, List<DataMap> branchOuts) {
        // Use SimpleLiveLocals to remove dead variables from the LocalMap
        Set<Local> liveLocals = new HashSet<Local>(_liveLocalsAnalysis.getLiveLocalsAfter(s));

        // Keep the locals used in this statement, in case we need to reference them when
        // processing the unit later (esp. for the target unit).
        liveLocals.addAll(s.getUseBoxes().stream()
                .filter(v -> v.getValue() instanceof Local)
                .map(v -> (Local)v.getValue())
                .collect(Collectors.toList()));

        // Remove the non-live locals from each data map.
        fallOut.forEach(dataMap -> dataMap.LocalMap.keySet().retainAll(liveLocals));
        branchOuts.forEach(dataMap -> dataMap.LocalMap.keySet().retainAll(liveLocals));
    }

    protected ExpressionSet resolveValue(Value value, DataMap dataMap) {
        RHSValueResolver resolver = new RHSValueResolver(dataMap);
        value.apply(resolver);

        ExpressionSet result = resolver.getData();
        if (result == null || result.isEmpty()) {
            return null;
        }

        return result;
    }

    protected Predicate resolveConstraint(Value value, DataMap dataMap) {
        ConstraintResolver resolver = new ConstraintResolver(dataMap);
        value.apply(resolver);
        return resolver.getConstraint();
    }

    // Returns the constraints of the auxiliary method's control flow.
    protected Predicate handleAuxiliaryMethod(InvokeExpr expr, DataMap in,
                                              VariableExpression returnIdentifier) {
        SootMethod auxMethod = expr.getMethod();
        if (!auxMethod.hasActiveBody()) {
            return null;
        }

        // Construct parameter map
        DataMap parameterMap = new DataMap();
        parameterMap.HeapMap.putAll(in.HeapMap);

        // Offset parameters if we have an instance invocation to a static method (likely
        // a special case for reflection or other type of call graph patching.
        int argOffset = (expr instanceof InstanceInvokeExpr) && auxMethod.isStatic() ? 1 : 0;

        for (int i = 0; i < expr.getArgCount() && i < auxMethod.getParameterCount(); i++) {
            ExpressionSet param = resolveValue(expr.getArg(i), in);
            if (param != null) {
                Local auxParamLocal =
                        auxMethod.getActiveBody().getParameterLocal(i + argOffset);
                parameterMap.LocalMap.put(auxParamLocal, param);
            }
        }

        if (expr instanceof InstanceInvokeExpr
                && (!auxMethod.isStatic() || auxMethod.getParameterCount() > 0)) {
            InstanceInvokeExpr instanceExpr = (InstanceInvokeExpr)expr;
            ExpressionSet base = resolveValue(instanceExpr.getBase(), in);
            if (base != null) {
                Local auxThisLocal = (argOffset == 0)
                        ? auxMethod.getActiveBody().getThisLocal()
                        : auxMethod.getActiveBody().getParameterLocal(0);
                parameterMap.LocalMap.put(auxThisLocal, base);
            }
        }

        // Analyze constraints in auxiliary method
        UnitGraph auxCfg = new BriefUnitGraph(auxMethod.getActiveBody());
        IntraproceduralConstraintAnalysis auxIntraAnalysis =
                new IntraproceduralConstraintAnalysis(
                        auxCfg, parameterMap, _excludeMethods, _auxDepth + 1);

        // Track all heap dependencies encountered
        _heapDependencies.addAll(auxIntraAnalysis.getHeapDependencies());

        // TODO Merge auxilliary heap map with current heap map

        // If auxilliary method returns a value, merge auxiliary constraints with current
        // constraints.
        if (auxMethod.getReturnType() != null
                && !auxMethod.getReturnType().equals(VoidType.v())) {
            // Track the values that the returned variable can take and the constraints that
            // lead there.
            Predicate returnPred = null;

            for (Unit tailUnit : auxCfg.getTails()) {
                if (tailUnit instanceof ThrowStmt) {
                    continue;
                }

                if (!(tailUnit instanceof ReturnStmt)) {
                    Output.error("Method returns value but no return statement found: "
                            + auxMethod);
                    continue;
                }

                ReturnStmt returnStmt = (ReturnStmt)tailUnit;
                DataMap returnDataMap = auxIntraAnalysis.getFlowBefore(returnStmt);

                ExpressionSet returnOp = auxIntraAnalysis.resolveValue(
                        returnStmt.getOp(), returnDataMap);
                if (returnOp != null) {
                    // Generate constraint for the return value of auxiliary method.
                    Expression.Operator returnExprOperator =
                            (Variable.isStringType(returnIdentifier.getType()))
                                ? Expression.Operator.STR_EQ : Expression.Operator.EQ;
                    ExpressionSet returnValueExprSet = ExpressionSet.combine(
                            returnExprOperator, returnIdentifier, returnOp);

                    Predicate returnValuePred = Predicate.combine(Predicate.Operator.AND,
                            returnDataMap.ControlFlowConstraint,
                            returnValueExprSet.toPredicate());
                    returnPred = Predicate.combine(Predicate.Operator.OR,
                            returnPred, returnValuePred);
                }
            }

            return returnPred;
        }

        return null;
    }

    private ConstraintStmtSwitch _constraintStmtSwitch = new ConstraintStmtSwitch();

    private class ConstraintStmtSwitch extends AbstractStmtSwitch {
        private DataMap _in = null;
        private List<DataMap> _fallOut = null;
        private List<DataMap> _branchOuts = null;

        public void initialize(DataMap in, List<DataMap> fallOut, List<DataMap> branchOuts) {
            _in = in;
            _fallOut = fallOut;
            _branchOuts = branchOuts;
        }

        @Override
        public void caseInvokeStmt(InvokeStmt stmt) {
            final DataMap dataMap = _fallOut.get(0);
            copy(_in, dataMap);

            // Add constraints from invocation.
            Predicate invokeConstraint = resolveConstraint(stmt.getInvokeExpr(), _in);
            dataMap.ControlFlowConstraint = Predicate.combine(Predicate.Operator.AND,
                    dataMap.ControlFlowConstraint, invokeConstraint);
        }
        @Override
        public void caseAssignStmt(AssignStmt stmt) {
            final DataMap dataMap = _fallOut.get(0);
            copy(_in, dataMap);

            // Add to local or heap map
            ExpressionSet rightOp = resolveValue(stmt.getRightOp(), _in);
            if (rightOp != null) {
                stmt.getLeftOp().apply(new AbstractShimpleValueSwitch() {
                    @Override
                    public void caseLocal(Local def) {
                        dataMap.LocalMap.put(def, rightOp);
                        //Output.log("  assign def = " + rightOp);
                        //Output.log("    type: " + def.getType().getClass().getName());
                    }
                    @Override
                    public void caseArrayRef(ArrayRef v) {
                        dataMap.LocalMap.put((Local)v.getBase(), rightOp);
                    }
                    @Override
                    public void caseStaticFieldRef(StaticFieldRef def) {
                        HeapVariable heapVariable = new HeapVariable(def);
                        for (HeapVariable heapVar : _in.HeapMap.keySet()) {
                            if (heapVar.intersects(heapVariable)) {
                                dataMap.HeapMap.put(heapVar, rightOp);
                                return;
                            }
                        }

                        dataMap.HeapMap.put(heapVariable, rightOp);
                    }
                    @Override
                    public void caseInstanceFieldRef(InstanceFieldRef def) {
                        HeapVariable heapVariable = new HeapVariable(def);
                        for (HeapVariable heapVar : _in.HeapMap.keySet()) {
                            if (heapVar.intersects(heapVariable)) {
                                dataMap.HeapMap.put(heapVar, rightOp);
                                return;
                            }
                        }

                        dataMap.HeapMap.put(heapVariable, rightOp);
                    }
                    @Override
                    public void defaultCase(Object obj) {
                        Output.error("Unsupported lhs value ("
                                + obj.getClass().getName() + "): " + obj);
                    }
                });
            }

            Predicate rightOpConstraint = resolveConstraint(stmt.getRightOp(), _in);
            dataMap.ControlFlowConstraint = Predicate.combine(Predicate.Operator.AND,
                    dataMap.ControlFlowConstraint, rightOpConstraint);
        }
        @Override
        public void caseIdentityStmt(IdentityStmt stmt) {
            // IdentityStmts are used to pass parameter values
            final DataMap dataMap = _fallOut.get(0);
            copy(_in, dataMap);

            if (!(stmt.getLeftOp() instanceof Local)) {
                Output.error("IdentityStmt does not assign to Local");
                return;
            }

            Local def = (Local)stmt.getLeftOp();

            if (_parameterMap.LocalMap.containsKey(def)) {
                dataMap.LocalMap.put(def, _parameterMap.LocalMap.get(def));
            }
        }
        @Override
        public void caseEnterMonitorStmt(EnterMonitorStmt stmt) {
            copy(_in, _fallOut.get(0));
        }
        @Override
        public void caseExitMonitorStmt(ExitMonitorStmt stmt) {
            copy(_in, _fallOut.get(0));
        }
        @Override
        public void caseGotoStmt(GotoStmt stmt) {
            copy(_in, _branchOuts.get(0));
        }
        @Override
        public void caseIfStmt(IfStmt stmt) {
            //Output.log("IfStmt: " + stmt);
            //Output.log("  branch targets: " + stmt.getUnitBoxes().size());

            Predicate condition = resolveConstraint(stmt.getCondition(), _in);
            DataMap branchDataMap = _branchOuts.get(0);
            copy(_in, branchDataMap);
            branchDataMap.ControlFlowConstraint = Predicate.combine(Predicate.Operator.AND,
                    branchDataMap.ControlFlowConstraint, condition);

            Predicate notCondition = Predicate.combine(Predicate.Operator.NOT, condition);
            DataMap fallDataMap = _fallOut.get(0);
            copy(_in, fallDataMap);
            fallDataMap.ControlFlowConstraint = Predicate.combine(Predicate.Operator.AND,
                    fallDataMap.ControlFlowConstraint, notCondition);

            //Output.log("  if constraint: " + condition);
            //Output.log("    new constraints: " + branchDataMap.ControlFlowConstraint);
        }
        @Override
        public void caseLookupSwitchStmt(LookupSwitchStmt stmt) {
            ExpressionSet keyExprSet = resolveValue(stmt.getKey(), _in);
            if (keyExprSet == null) {
                return;
            }

            // Keep track of the default predicate by using the opposite constraints
            // generated for each lookup value.
            Predicate defaultPred = null;

            for (int i = 0; i < stmt.getTargetCount(); i++) {
                int lookupValue = stmt.getLookupValue(i);
                ExpressionSet lookupExprSet = ExpressionSet.combine(Expression.Operator.EQ,
                        keyExprSet,
                        new VariableExpression(new NumberVariable(lookupValue)));
                Predicate lookUpPred = lookupExprSet.toPredicate();

                _branchOuts.get(i).ControlFlowConstraint = Predicate.combine(
                        Predicate.Operator.AND,
                        _branchOuts.get(i).ControlFlowConstraint, lookUpPred);

                // Track default case constraint
                defaultPred = Predicate.combine(Predicate.Operator.AND,
                                                defaultPred,
                                                lookupExprSet.toNotPredicate());
            }

            // Handle default case
            int defaultIndex = stmt.getTargetCount();
            _branchOuts.get(defaultIndex).ControlFlowConstraint = Predicate.combine(
                    Predicate.Operator.AND,
                    _branchOuts.get(defaultIndex).ControlFlowConstraint, defaultPred);
            //Output.debug("lookup default constraint: " + defaultPred);
        }
        @Override
        public void caseNopStmt(NopStmt stmt) {
            copy(_in, _fallOut.get(0));
        }
        @Override
        public void caseRetStmt(RetStmt stmt) {
            copy(_in, _fallOut.get(0));
        }
        @Override
        public void caseReturnStmt(ReturnStmt stmt) {
            // Do nothing
        }
        @Override
        public void caseReturnVoidStmt(ReturnVoidStmt stmt) {
            // Do nothing
        }
        @Override
        public void caseTableSwitchStmt(TableSwitchStmt stmt) {
            ExpressionSet keyExprSet = resolveValue(stmt.getKey(), _in);
            if (keyExprSet == null) {
                return;
            }

            for (int i = 0; i < stmt.getHighIndex() - stmt.getLowIndex() + 1; i++) {
                int switchValue = stmt.getLowIndex() + i;
                ExpressionSet switchExprSet = ExpressionSet.combine(Expression.Operator.EQ,
                        keyExprSet,
                        new VariableExpression(new NumberVariable(switchValue)));
                Predicate switchPred = switchExprSet.toPredicate();
                //Output.debug("table constraint: " + switchPred);

                _branchOuts.get(i).ControlFlowConstraint = Predicate.combine(
                        Predicate.Operator.AND,
                        _branchOuts.get(i).ControlFlowConstraint, switchPred);
            }

            // Handle default case
            int defaultIndex = stmt.getHighIndex() - stmt.getLowIndex() + 1;
            Predicate lowIndexPred = ExpressionSet.combine(
                    Expression.Operator.LT, keyExprSet,
                    new VariableExpression(new NumberVariable(stmt.getLowIndex()))
                ).toPredicate();
            Predicate highIndexPred = ExpressionSet.combine(
                    Expression.Operator.GT, keyExprSet,
                    new VariableExpression(new NumberVariable(stmt.getHighIndex()))
                ).toPredicate();
            Predicate defaultPred = Predicate.combine(Predicate.Operator.AND,
                    lowIndexPred, highIndexPred);

            _branchOuts.get(defaultIndex).ControlFlowConstraint =
                    Predicate.combine(Predicate.Operator.AND,
                            _branchOuts.get(defaultIndex).ControlFlowConstraint, defaultPred);
            //Output.debug("table default constraint: " + defaultPred);
        }
        @Override
        public void caseThrowStmt(ThrowStmt stmt) {
            // Do nothing
        }
        @Override
        public void defaultCase(Object obj) {
            Output.error("ConstraintAnalysis: Unsupported stmt (" + obj.getClass().getName()
                    + ") " + obj);
        }
    }

    private class RHSValueResolver extends AbstractShimpleValueSwitch {
        private final DataMap _in;
        private ExpressionSet _data = null;

        public RHSValueResolver(DataMap in) {
            _in = in;
        }

        public ExpressionSet getData() {
            return _data;
        }

        // Helper methods
        private void handleArithmeticExpr(BinopExpr expr, Expression.Operator operator) {
            ExpressionSet op1 = resolveValue(expr.getOp1(), _in);
            ExpressionSet op2 = resolveValue(expr.getOp2(), _in);

            if (op1 != null && op2 != null) {
                _data = ExpressionSet.combine(operator, op1, op2);
            }
        }

        private void handleHeapAccess(FieldRef ref) {
            HeapVariable heapVariable = new HeapVariable(ref);
            _data = new ExpressionSet();

            // Check if heap variable was propagated
            for (HeapVariable heapVar : _in.HeapMap.keySet()) {
                if (heapVar.intersects(heapVariable)) {
                    _data.addAll(_in.HeapMap.get(heapVar));
                }
            }

            // Check if this heap variable has already been read
            if (_data.isEmpty()) {
                for (HeapVariable heapVar : _readHeapVariables) {
                    if (heapVar.intersects(heapVariable)) {
                        _data.add(heapVar.getExpression());
                    }
                }
            }

            // Otherwise, track this as a heap dependency
            if (_data.isEmpty()) {
                _data.add(heapVariable.getExpression());

                // Since this is an unresolved access to a heap variable, track it as a heap
                // dependency.
                _heapDependencies.add(heapVariable);
                _readHeapVariables.add(heapVariable);
            }
        }

        private void handleInvokeExpr(InvokeExpr expr) {
            SootMethod method = expr.getMethod();
            SootMethodRef methodRef = expr.getMethodRef();

            if (methodRef.getSignature().equals("<android.telephony.SmsMessage: "
                        + "android.telephony.SmsMessage createFromPdu(byte[])>")) {
                ExpressionSet op1 = resolveValue(expr.getArg(0), _in);
                if (op1 != null) {
                    _data = ExpressionSet.transform(op1, e -> {
                        if (e.isVariable()) {
                            return new VariableExpression(
                                    new MethodCallVariable(expr, e.getVariable()));
                            //return new VariableExpression(
                            //        "<SmsMessage>(" + e.getVariable() + ")",
                            //        methodRef.returnType());
                        } else {
                            return new VariableExpression(new MethodCallVariable(expr));
                            //return new VariableExpression("<SmsMessage>",
                            //        methodRef.returnType());
                        }
                    });

                    return;
                }

            } else if (methodRef.getSignature().startsWith(
                       "<android.os.Bundle: java.lang.Object get(java.lang.String")) {
                // TODO: handle other Bundle "get" methods
                InstanceInvokeExpr instanceExpr = (InstanceInvokeExpr)expr;
                ExpressionSet base = resolveValue(instanceExpr.getBase(), _in);
                ExpressionSet op1 = resolveValue(instanceExpr.getArg(0), _in);

                if (base != null && op1 != null) {
                    List<ExpressionSet> results = new ArrayList<ExpressionSet>();
                    for (Expression baseExpr : base.getExpressions()) {
                        if (!baseExpr.isVariable()) {
                            results.add(new ExpressionSet(new VariableExpression(
                                    new KeyValueAccessVariable(null, null,
                                            KeyValueAccessVariable.DatabaseType.BUNDLE,
                                            expr.getMethod().getReturnType()))));
                            continue;
                        }

                        ExpressionSet partialResult = ExpressionSet.transform(op1,  e -> {
                            if (e.isVariable()) {
                                return new VariableExpression(new KeyValueAccessVariable(
                                        baseExpr.getVariable(), e.getVariable(),
                                        KeyValueAccessVariable.DatabaseType.BUNDLE,
                                        expr.getMethod().getReturnType()));
                            } else {
                                return new VariableExpression(new KeyValueAccessVariable(
                                        baseExpr.getVariable(), null,
                                        KeyValueAccessVariable.DatabaseType.BUNDLE,
                                        expr.getMethod().getReturnType()));
                            }
                        });
                        results.add(partialResult);
                    }

                    _data = ExpressionSet.merge(results);
                    return;
                }

            } else if (methodRef.getSignature().startsWith(
                           "<android.content.Context: java.lang.String getString(int")
                        || methodRef.getSignature().equals(
                           "<android.content.Context: java.lang.CharSequence getText(int)>")) {
                InstanceInvokeExpr instanceExpr = (InstanceInvokeExpr)expr;
                ExpressionSet op1 = resolveValue(instanceExpr.getArg(0), _in);

                if (op1 != null) {
                    _data = ExpressionSet.transform(op1,  e -> {
                        Variable opVariable = e.isVariable() ? e.getVariable() : null;
                        return new VariableExpression(new KeyValueAccessVariable(
                                new PlaceholderVariable("Context",
                                    RefType.v("android.content.Context")),
                                opVariable,
                                KeyValueAccessVariable.DatabaseType.STRING_TABLE,
                                RefType.v("java.lang.String")));
                    });
                    return;
                }

            } else if (methodRef.getSignature().startsWith(
                       "<java.lang.StringBuilder: java.lang.StringBuilder append(")) {
                InstanceInvokeExpr instanceExpr = (InstanceInvokeExpr)expr;
                ExpressionSet op1 = resolveValue(instanceExpr.getBase(), _in);
                ExpressionSet op2 = resolveValue(instanceExpr.getArg(0), _in);

                if (op1 != null && op2 != null) {
                    _data = ExpressionSet.combine(Expression.Operator.APPEND, op1, op2);
                    return;
                }

            } else if (methodRef.getSignature().endsWith("java.lang.String toString()>")
                        || methodRef.getSignature().endsWith("char[] toCharArray()>")) {
                InstanceInvokeExpr instanceExpr = (InstanceInvokeExpr)expr;
                _data = resolveValue(instanceExpr.getBase(), _in);
                return;

            } else if (methodRef.getSignature().equals(
                           "<java.lang.String: boolean equals(java.lang.Object)>")
                        || methodRef.getSignature().equals(
                            "<java.lang.String: boolean contains(java.lang.CharSequence)>")) {
                String returnString = "Return<" + methodRef.declaringClass().getShortName()
                        + "." + methodRef.name() + "(){" + expr.hashCode() + "}>";

                VariableExpression returnExpr = new VariableExpression(
                        new PlaceholderVariable(returnString, BooleanType.v()));
                _data = new ExpressionSet(returnExpr);
                return;
            } else if (methodRef.getSignature().endsWith(
                        "boolean equals(java.lang.Object)>")) {
                String returnString = "Return<" + methodRef.declaringClass().getShortName()
                        + "." + methodRef.name() + "(){" + expr.hashCode() + "}>";
                VariableExpression returnExpr = new VariableExpression(
                        new PlaceholderVariable(returnString, BooleanType.v()));

                _data = new ExpressionSet(returnExpr);
                return;

            // Handle permission requests.
            //} else if (methodRef.getSignature().endsWith("int checkSelfPermission("
            //            + "android.content.Context,java.lang.String)>")) {
            //    ExpressionSet permissionArg = resolveValue(expr.getArg(1), _in);
            //    if (permissionArg != null) {
            //        _data = ExpressionSet.transform(permissionArg, e -> {
            //            if (e.isVariable()) {
            //                return new VariableExpression(new PlaceholderVariable(
            //                        "Permission<"
            //                        + ((ConstantVariable)e.getVariable()).getValue() + ">",
            //                        IntType.v()));
            //            } else {
            //                return e;
            //            }
            //        });
            //        return;
            //    }

            //} else if (methodRef.getSignature().contains("int checkPermission("
            //                + "java.lang.String")
            //            || methodRef.getSignature().contains("int checkCallingPermission("
            //                + "java.lang.String")) {
            //    ExpressionSet permissionArg = resolveValue(expr.getArg(0), _in);
            //    if (permissionArg != null) {
            //        _data = ExpressionSet.transform(permissionArg, e -> {
            //            if (e.isVariable() && e.getVariable().isConstant()) {
            //                return new VariableExpression(new PlaceholderVariable(
            //                        "Permission<"
            //                        + ((ConstantVariable)e.getVariable()).getValue() + ">",
            //                        IntType.v()));
            //            } else {
            //                return e;
            //            }
            //        });
            //        return;
            //    }

            // Only process auxiliary methods for application methods
            } else if (method.getDeclaringClass().isApplicationClass()
                        && !_excludeMethods.contains(method) && _auxDepth == 0) {
                String returnString = "Return<" + methodRef.declaringClass().getShortName()
                        + "." + methodRef.name() + "(){" + expr.hashCode() + "}>";

                if (expr instanceof InstanceInvokeExpr) {
                    InstanceInvokeExpr instanceExpr = (InstanceInvokeExpr)expr;
                    ExpressionSet base = resolveValue(instanceExpr.getBase(), _in);

                    if (base != null) {
                        for (Expression baseExpr : base.getExpressions()) {
                            if (baseExpr.isVariable()) {
                                returnString = "Return<" + baseExpr.getVariable() + "."
                                        + expr.getMethodRef().name() + "(){"
                                        + expr.hashCode() + "}>";
                                break;
                            }
                        }
                    }
                }

                VariableExpression returnIdentifier = new VariableExpression(
                        new PlaceholderVariable(returnString,
                            expr.getMethod().getReturnType()));
                _data = new ExpressionSet(returnIdentifier);
                return;
            }

            // Default case
            if (expr instanceof InstanceInvokeExpr) {
                InstanceInvokeExpr instanceExpr = (InstanceInvokeExpr)expr;
                ExpressionSet base = resolveValue(instanceExpr.getBase(), _in);
                // TODO: handle parameters

                if (base != null) {
                    _data = ExpressionSet.transform(base, e -> {
                        if (e.isVariable()) {
                            return new VariableExpression(new MethodCallVariable(
                                    expr, e.getVariable()));
                        } else {
                            return new VariableExpression(new MethodCallVariable(expr));
                        }
                    });
                }
            }
        }

        // -------------------- Shimple ---------------------
        @Override
        public void casePhiExpr(PhiExpr e) {
            List<ExpressionSet> argExprSets = new ArrayList<ExpressionSet>();
            for (ValueUnitPair argValueUnit : e.getArgs()) {
                ExpressionSet arg = resolveValue(argValueUnit.getValue(), _in);
                if (arg != null) {
                    argExprSets.add(arg);
                }
            }

            if (!argExprSets.isEmpty()) {
                _data = ExpressionSet.merge(argExprSets);
            }
        }

        // ------------------ ValueSwitch -------------------
        @Override
        public void caseLocal(Local l) {
            if (_in.LocalMap.containsKey(l)) {
                _data = new ExpressionSet(_in.LocalMap.get(l));
            }
        }

        // ----------------- ConstantSwitch -----------------
        @Override
        public void caseDoubleConstant(DoubleConstant v) {
            _data = new ExpressionSet(new VariableExpression(new NumberVariable(v.value)));
        }
        @Override
        public void caseFloatConstant(FloatConstant v) {
            _data = new ExpressionSet(new VariableExpression(new NumberVariable(v.value)));
        }
        @Override
        public void caseIntConstant(IntConstant v) {
            // In soot, boolean constants also fall into this case.  Differentiate when we
            // use the constant in an expression.
            _data = new ExpressionSet(new VariableExpression(new NumberVariable(v.value)));
        }
        @Override
        public void caseLongConstant(LongConstant v) {
            _data = new ExpressionSet(new VariableExpression(new NumberVariable(v.value)));
        }
        @Override
        public void caseNullConstant(NullConstant v) {
            // TODO: make this into a special NULL type to distinguish from the integer 0
            _data = new ExpressionSet(Expression.getNull());
        }
        @Override
        public void caseStringConstant(StringConstant v) {
            // StringConstant is surrounded by unnecessary " quotations
            String stringConstant = v.toString();
            stringConstant = stringConstant.substring(1, stringConstant.length() - 1);
            _data = new ExpressionSet(new VariableExpression(
                            new StringVariable(stringConstant)));
        }
        @Override
        public void caseClassConstant(ClassConstant v) {
            _data = new ExpressionSet(new VariableExpression(
                            new StringVariable(v.toString())));
        }

        // ------------------- ExprSwitch -------------------
        @Override
        public void caseAddExpr(AddExpr v) {
            handleArithmeticExpr(v, Expression.Operator.ADD);
        }
        @Override
        public void caseAndExpr(AndExpr v) {
            handleArithmeticExpr(v, Expression.Operator.AND);
        }
        @Override
        public void caseCmpExpr(CmpExpr v) {
            handleArithmeticExpr(v, Expression.Operator.CMP);
        }
        @Override
        public void caseCmpgExpr(CmpgExpr v) {
            handleArithmeticExpr(v, Expression.Operator.CMP);
        }
        @Override
        public void caseCmplExpr(CmplExpr v) {
            handleArithmeticExpr(v, Expression.Operator.CMP);
        }
        @Override
        public void caseDivExpr(DivExpr v) {
            handleArithmeticExpr(v, Expression.Operator.DIV);
        }
        @Override
        public void caseMulExpr(MulExpr v) {
            handleArithmeticExpr(v, Expression.Operator.MUL);
        }
        @Override
        public void caseOrExpr(OrExpr v) {
            handleArithmeticExpr(v, Expression.Operator.OR);
        }
        @Override
        public void caseRemExpr(RemExpr v) {
            handleArithmeticExpr(v, Expression.Operator.REM);
        }
        @Override
        public void caseShlExpr(ShlExpr v) {
            handleArithmeticExpr(v, Expression.Operator.SHL);
        }
        @Override
        public void caseShrExpr(ShrExpr v) {
            handleArithmeticExpr(v, Expression.Operator.SHR);
        }
        @Override
        public void caseUshrExpr(UshrExpr v) {
            handleArithmeticExpr(v, Expression.Operator.SHR);
        }
        @Override
        public void caseSubExpr(SubExpr v) {
            handleArithmeticExpr(v, Expression.Operator.SUB);
        }
        @Override
        public void caseXorExpr(XorExpr v) {
            handleArithmeticExpr(v, Expression.Operator.XOR);
        }
        @Override
        public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v) {
            handleInvokeExpr(v);
        }
        @Override
        public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {
            handleInvokeExpr(v);
        }
        @Override
        public void caseStaticInvokeExpr(StaticInvokeExpr v) {
            handleInvokeExpr(v);
        }
        @Override
        public void caseVirtualInvokeExpr(VirtualInvokeExpr v) {
            handleInvokeExpr(v);
        }
        @Override
        public void caseDynamicInvokeExpr(DynamicInvokeExpr v) {
            handleInvokeExpr(v);
        }
        @Override
        public void caseCastExpr(CastExpr v) {
            ExpressionSet op = resolveValue(v.getOp(), _in);

            if (op != null) {
                final Type castType = v.getType();
                _data = ExpressionSet.transform(op, e -> {
                    if (e.isVariable()) {
                        return new VariableExpression(e.getVariable(), castType);
                    } else {
                        return e;
                    }
                });
            }
        }
        @Override
        public void caseInstanceOfExpr(InstanceOfExpr v) {
            ExpressionSet op = resolveValue(v.getOp(), _in);
            if (op != null) {
                final Type castType = v.getType();
                _data = ExpressionSet.transform(op, e -> {
                    if (e.isVariable()) {
                        return new VariableExpression(new ClassTypeVariable(e.getVariable()));
                    } else {
                        return e;
                    }
                });
            }
        }
        @Override
        public void caseNewArrayExpr(NewArrayExpr v) {
            // Do nothing
        }
        @Override
        public void caseNewMultiArrayExpr(NewMultiArrayExpr v) {
            // Do nothing
        }
        @Override
        public void caseNewExpr(NewExpr v) {
            //if (v.getBaseType().getEscapedName().equals("java.lang.StringBuilder")) {
            //    _data = new ExpressionSet(new VariableExpression(new StringVariable("")));
            //} else if (v.getBaseType().getEscapedName().equals("java.lang.String")) {
            //    _data = new ExpressionSet(new VariableExpression(new StringVariable("")));
            if (Variable.isStringType(v.getBaseType())) {
                _data = new ExpressionSet(new VariableExpression(new StringVariable("")));
            } else {
                String newIdentifier = "New<" + v.getBaseType().getClassName() + ">";
                newIdentifier += "(" + v.hashCode() + ")";
                _data = new ExpressionSet(new VariableExpression(new PlaceholderVariable(
                        newIdentifier, v.getType())));
            }
        }
        @Override
        public void caseLengthExpr(LengthExpr v) {
            ExpressionSet op = resolveValue(v.getOp(), _in);
            if (op != null) {
                _data = ExpressionSet.transform(op, e -> {
                    if (e.isVariable()) {
                        return new VariableExpression(new PlaceholderVariable(
                                "Array.length(" + e.getVariable() + ")", IntType.v()));
                    } else {
                        return new VariableExpression(new PlaceholderVariable(
                                "Array.length{" + v.hashCode() + "}", IntType.v()));
                    }
                });
            } else {
                _data = new ExpressionSet(new VariableExpression(new PlaceholderVariable(
                        "Array.length{" + v.hashCode() + "}", IntType.v())));
            }
        }
        @Override
        public void caseNegExpr(NegExpr v) {
            ExpressionSet op = resolveValue(v.getOp(), _in);

            if (op != null) {
                ExpressionSet negExprGrp = new ExpressionSet(new VariableExpression(
                        new NumberVariable(-1)));
                _data = ExpressionSet.combine(Expression.Operator.MUL, op, negExprGrp);
            }
        }

        // ------------------- RefSwitch --------------------
        @Override
        public void caseArrayRef(ArrayRef v) {
            _data = resolveValue(v.getBase(), _in);
        }
        @Override
        public void caseStaticFieldRef(StaticFieldRef v) {
            handleHeapAccess(v);
        }
        @Override
        public void caseInstanceFieldRef(InstanceFieldRef v) {
            handleHeapAccess(v);
        }
        @Override
        public void caseParameterRef(ParameterRef v) {
            // Do nothing (we handle parameters with _parameterMap)
            //_data = _parameterMap.LocalMap.get(v.getIndex());
        }
        @Override
        public void caseCaughtExceptionRef(CaughtExceptionRef v) {
            // Do nothing
        }
        @Override
        public void caseThisRef(ThisRef v) {
            // Do nothing
        }

        @Override
        public void defaultCase(Object obj) {
            Output.error("Unsupported rhs value (" + obj.getClass().getName() + "): " + obj);
        }
    }

    private class ConstraintResolver extends AbstractShimpleValueSwitch {
        private final DataMap _in;
        private Predicate _constraint = null;

        public ConstraintResolver(DataMap in) {
            _in = in;
        }

        public Predicate getConstraint() {
            return _constraint;
        }

        // Helper methods
        private void handleBinopExpr(BinopExpr expr) {
            Predicate op1Constraint = resolveConstraint(expr.getOp1(), _in);
            Predicate op2Constraint = resolveConstraint(expr.getOp2(), _in);
            _constraint = Predicate.combine(Predicate.Operator.AND,
                    _constraint, op1Constraint);
            _constraint = Predicate.combine(Predicate.Operator.AND,
                    _constraint, op2Constraint);

        }

        private void handleConditionExpr(ConditionExpr expr, Expression.Operator operator) {
            ExpressionSet op1 = resolveValue(expr.getOp1(), _in);
            ExpressionSet op2 = resolveValue(expr.getOp2(), _in);

            Predicate op1Constraint = resolveConstraint(expr.getOp1(), _in);
            Predicate op2Constraint = resolveConstraint(expr.getOp2(), _in);

            if (op1 != null && op2 != null) {
                Predicate condConstraint =
                        ExpressionSet.combine(operator, op1, op2).toPredicate();
                condConstraint = Predicate.combine(Predicate.Operator.AND,
                        condConstraint, op1Constraint);
                condConstraint = Predicate.combine(Predicate.Operator.AND,
                        condConstraint, op2Constraint);
                _constraint = condConstraint;
            }
        }

        private void handleInvokeExpr(InvokeExpr expr) {
            SootMethod method = expr.getMethod();
            SootMethodRef methodRef = expr.getMethodRef();

            if (methodRef.getSignature().equals(
                           "<java.lang.String: boolean equals(java.lang.Object)>")
                        || methodRef.getSignature().equals(
                            "<java.lang.String: boolean contains(java.lang.CharSequence)>")) {
                InstanceInvokeExpr instanceExpr = (InstanceInvokeExpr)expr;

                ExpressionSet op1 = resolveValue(instanceExpr.getBase(), _in);
                ExpressionSet op2 = resolveValue(instanceExpr.getArg(0), _in);
                Expression.Operator operator = methodRef.name().equals("equals")
                        ? Expression.Operator.STR_EQ : Expression.Operator.CONTAINS;

                if (op1 != null && op2 != null) {
                    String returnString = "Return<" + methodRef.declaringClass().getShortName()
                            + "." + methodRef.name() + "(){" + expr.hashCode() + "}>";
                    VariableExpression returnExpr = new VariableExpression(
                            new PlaceholderVariable(returnString, BooleanType.v()));

                    Predicate eqConstraint = ExpressionSet.combine(
                            operator, op1, op2).toPredicate();
                    Expression eqRetExpr = new ArithmeticExpression(Expression.Operator.EQ,
                            returnExpr, Expression.getTrue());
                    eqConstraint = Predicate.combine(Predicate.Operator.AND,
                            eqConstraint, new ExpressionPredicate(eqRetExpr));

                    Predicate neConstraint = ExpressionSet.combine(
                            operator, op1, op2).toNotPredicate();
                    Expression neRetExpr = new ArithmeticExpression(Expression.Operator.EQ,
                            returnExpr, Expression.getFalse());
                    neConstraint = Predicate.combine(Predicate.Operator.AND,
                            neConstraint, new ExpressionPredicate(neRetExpr));

                    Predicate returnConstraint = Predicate.combine(Predicate.Operator.OR,
                                                                   eqConstraint,
                                                                   neConstraint);

                    _constraint = returnConstraint;
                    //_constraint = Predicate.combine(Predicate.Operator.AND,
                    //        _constraint, returnConstraint);
                    //Output.log("data constraint: " + returnExpr + " -> " + returnConstraint);

                    return;
                }
            } else if (methodRef.getSignature().endsWith(
                        "boolean equals(java.lang.Object)>")) {
                InstanceInvokeExpr instanceExpr = (InstanceInvokeExpr)expr;

                ExpressionSet op1 = resolveValue(instanceExpr.getBase(), _in);
                ExpressionSet op2 = resolveValue(instanceExpr.getArg(0), _in);

                if (op1 != null && op2 != null) {
                    String returnString = "Return<" + methodRef.declaringClass().getShortName()
                            + "." + methodRef.name() + "(){" + expr.hashCode() + "}>";
                    VariableExpression returnExpr = new VariableExpression(
                            new PlaceholderVariable(returnString, BooleanType.v()));

                    Predicate eqConstraint = ExpressionSet.combine(
                            Expression.Operator.EQ, op1, op2).toPredicate();
                    Expression eqRetExpr = new ArithmeticExpression(Expression.Operator.EQ,
                            returnExpr, Expression.getTrue());
                    eqConstraint = Predicate.combine(Predicate.Operator.AND,
                            eqConstraint, new ExpressionPredicate(eqRetExpr));

                    Predicate neConstraint = ExpressionSet.combine(
                            Expression.Operator.EQ, op1, op2).toNotPredicate();
                    Expression neRetExpr = new ArithmeticExpression(Expression.Operator.EQ,
                            returnExpr, Expression.getFalse());
                    neConstraint = Predicate.combine(Predicate.Operator.AND,
                            neConstraint, new ExpressionPredicate(neRetExpr));

                    Predicate returnConstraint = Predicate.combine(Predicate.Operator.OR,
                                                                   eqConstraint,
                                                                   neConstraint);

                    _constraint = returnConstraint;
                    //_constraint = Predicate.combine(Predicate.Operator.AND,
                    //        _constraint, returnConstraint);
                    return;
                }
            } else if (method.getDeclaringClass().isApplicationClass()
                        && !_excludeMethods.contains(method) && _auxDepth == 0) {
                // For now, only process auxiliary methods for application methods
                String returnString = "Return<" + methodRef.declaringClass().getShortName()
                        + "." + methodRef.name() + "(){" + expr.hashCode() + "}>";

                if (expr instanceof InstanceInvokeExpr) {
                    InstanceInvokeExpr instanceExpr = (InstanceInvokeExpr)expr;
                    ExpressionSet base = resolveValue(instanceExpr.getBase(), _in);

                    if (base != null) {
                        for (Expression baseExpr : base.getExpressions()) {
                            if (baseExpr.isVariable()) {
                                returnString = "Return<" + baseExpr.getVariable() + "."
                                        + expr.getMethodRef().name() + "(){"
                                        + expr.hashCode() + "}>";
                                break;
                            }
                        }
                    }
                }

                VariableExpression returnIdentifier = new VariableExpression(
                        new PlaceholderVariable(returnString,
                            expr.getMethod().getReturnType()));
                Predicate auxiliaryConstraints =
                        handleAuxiliaryMethod(expr, _in, returnIdentifier);

                _constraint = auxiliaryConstraints;
                //_constraint = Predicate.combine(Predicate.Operator.AND,
                //        _constraint, auxiliaryConstraints);
                return;
            }
        }

        // ------------------- ExprSwitch -------------------
        @Override
        public void caseAddExpr(AddExpr v) {
            handleBinopExpr(v);
        }
        @Override
        public void caseAndExpr(AndExpr v) {
            handleBinopExpr(v);
        }
        @Override
        public void caseCmpExpr(CmpExpr v) {
            handleBinopExpr(v);
        }
        @Override
        public void caseCmpgExpr(CmpgExpr v) {
            handleBinopExpr(v);
        }
        @Override
        public void caseCmplExpr(CmplExpr v) {
            handleBinopExpr(v);
        }
        @Override
        public void caseDivExpr(DivExpr v) {
            handleBinopExpr(v);
        }
        @Override
        public void caseMulExpr(MulExpr v) {
            handleBinopExpr(v);
        }
        @Override
        public void caseOrExpr(OrExpr v) {
            handleBinopExpr(v);
        }
        @Override
        public void caseRemExpr(RemExpr v) {
            handleBinopExpr(v);
        }
        @Override
        public void caseShlExpr(ShlExpr v) {
            handleBinopExpr(v);
        }
        @Override
        public void caseShrExpr(ShrExpr v) {
            handleBinopExpr(v);
        }
        @Override
        public void caseUshrExpr(UshrExpr v) {
            handleBinopExpr(v);
        }
        @Override
        public void caseSubExpr(SubExpr v) {
            handleBinopExpr(v);
        }
        @Override
        public void caseXorExpr(XorExpr v) {
            handleBinopExpr(v);
        }
        @Override
        public void caseEqExpr(EqExpr v) {
            handleConditionExpr(v, Expression.Operator.EQ);
        }
        @Override
        public void caseNeExpr(NeExpr v) {
            handleConditionExpr(v, Expression.Operator.NE);
        }
        @Override
        public void caseGeExpr(GeExpr v) {
            handleConditionExpr(v, Expression.Operator.GE);
        }
        @Override
        public void caseGtExpr(GtExpr v) {
            handleConditionExpr(v, Expression.Operator.GT);
        }
        @Override
        public void caseLeExpr(LeExpr v) {
            handleConditionExpr(v, Expression.Operator.LE);
        }
        @Override
        public void caseLtExpr(LtExpr v) {
            handleConditionExpr(v, Expression.Operator.LT);
        }
        @Override
        public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v) {
            handleInvokeExpr(v);
        }
        @Override
        public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {
            handleInvokeExpr(v);
        }
        @Override
        public void caseStaticInvokeExpr(StaticInvokeExpr v) {
            handleInvokeExpr(v);
        }
        @Override
        public void caseVirtualInvokeExpr(VirtualInvokeExpr v) {
            handleInvokeExpr(v);
        }
        @Override
        public void caseDynamicInvokeExpr(DynamicInvokeExpr v) {
            handleInvokeExpr(v);
        }
        @Override
        public void caseCastExpr(CastExpr v) {
            ExpressionSet op = resolveValue(v.getOp(), _in);

            if (op != null) {
                ExpressionSet classTypeExprSet = ExpressionSet.transform(op, e -> {
                    if (e.isVariable()) {
                        return new VariableExpression(new ClassTypeVariable(e.getVariable()));
                    } else {
                        return e;
                    }
                });

                VariableExpression classExpr = new VariableExpression(
                        new StringVariable(v.getType().toString()));
                Predicate classTypeConstraint = ExpressionSet.combine(
                        Expression.Operator.STR_EQ, classTypeExprSet, classExpr).toPredicate();

                _constraint = classTypeConstraint;
                //_constraint = Predicate.combine(Predicate.Operator.AND,
                //        _constraint, classTypeConstraint);
            }
        }
        //@Override
        //public void caseNewExpr(NewExpr v) {
        //    if (!Variable.isStringType(v.getBaseType())) {
        //        String newIdentifier = "New<" + v.getBaseType().getClassName() + ">";
        //        newIdentifier += "(" + v.hashCode() + ")";
        //        Variable newVariable = new PlaceholderVariable(newIdentifier, v.getType());
        //        VariableExpression classTypeExpr = new VariableExpression(
        //                new ClassTypeVariable(newVariable));

        //        VariableExpression classExpr = new VariableExpression(
        //                new StringVariable(v.getBaseType().toString()));
        //        Predicate classTypeConstraint = new ExpressionPredicate(Expression.combine(
        //                Expression.Operator.STR_EQ, classTypeExpr, classExpr));

        //        _constraint = classTypeConstraint;
        //    }
        //}
    }
}
