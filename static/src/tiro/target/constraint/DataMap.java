package tiro.target.constraint;

import tiro.Output;

import soot.Local;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class DataMap {
    // Map of local variables to the expressions they might hold
    public Map<Local, ExpressionSet> LocalMap;

    // Map of heap variables (spark nodes/points-to-sets) to expressions
    public Map<HeapVariable, ExpressionSet> HeapMap;

    // Constraints that are held at a particular location in a method body
    public Predicate ControlFlowConstraint = null;

    public DataMap() {
        LocalMap = new HashMap<Local, ExpressionSet>();
        HeapMap = new HashMap<HeapVariable, ExpressionSet>();
        ControlFlowConstraint = null;
    }

    public DataMap(Map<Local, ExpressionSet> localMap,
            Map<HeapVariable, ExpressionSet> heapMap, Predicate constraint) {
        LocalMap = new HashMap<Local, ExpressionSet>(localMap);
        HeapMap = new HashMap<HeapVariable, ExpressionSet>(heapMap);
        ControlFlowConstraint = constraint;
    }

    public DataMap clone() {
        return new DataMap(this.LocalMap, this.HeapMap, this.ControlFlowConstraint);
    }

    public void copy(DataMap other) {
        if (this == other) {
            return;
        }

        this.LocalMap.clear();
        this.HeapMap.clear();

        this.LocalMap.putAll(other.LocalMap);
        this.HeapMap.putAll(other.HeapMap);
        this.ControlFlowConstraint = other.ControlFlowConstraint;

        //Output.log("------ DataMap.copy() -------");
        //this.LocalMap.forEach((x,y) -> { Output.log("  " + x + " -> " + y); });
    }

    public void merge(DataMap in1, DataMap in2) {
        if (in1 == in2) {
            copy(in1);
            return;
        }

        // Handle local variables
        this.LocalMap.clear();
        Set<Local> localVariables = new HashSet<Local>(in1.LocalMap.keySet());
        localVariables.addAll(in2.LocalMap.keySet());

        for (Local variable : localVariables) {
            if (in1.LocalMap.containsKey(variable) && in2.LocalMap.containsKey(variable)) {
                List<ExpressionSet> exprSetList = new ArrayList<ExpressionSet>();
                exprSetList.add(in1.LocalMap.get(variable));
                exprSetList.add(in2.LocalMap.get(variable));
                this.LocalMap.put(variable, ExpressionSet.merge(exprSetList));
            } else if (in1.LocalMap.containsKey(variable)) {
                this.LocalMap.put(variable, new ExpressionSet(in1.LocalMap.get(variable)));
            } else {
                this.LocalMap.put(variable, new ExpressionSet(in2.LocalMap.get(variable)));
            }
        }

        // Handle heap variables
        this.HeapMap.clear();
        Set<HeapVariable> heapVariables = new HashSet<HeapVariable>(in1.HeapMap.keySet());
        heapVariables.addAll(in2.HeapMap.keySet());

        for (HeapVariable variable : heapVariables) {
            if (in1.HeapMap.containsKey(variable) && in2.HeapMap.containsKey(variable)) {
                List<ExpressionSet> exprSetList = new ArrayList<ExpressionSet>();
                exprSetList.add(in1.HeapMap.get(variable));
                exprSetList.add(in2.HeapMap.get(variable));
                this.HeapMap.put(variable, ExpressionSet.merge(exprSetList));
            } else if (in1.HeapMap.containsKey(variable)) {
                this.HeapMap.put(variable, new ExpressionSet(in1.HeapMap.get(variable)));
            } else {
                this.HeapMap.put(variable, new ExpressionSet(in2.HeapMap.get(variable)));
            }
        }

        // Handle constraints
        if (in1.ControlFlowConstraint != null
                && in1.ControlFlowConstraint.isOppositeOf(in2.ControlFlowConstraint)) {

            this.ControlFlowConstraint = getSharedPredicateForMerge(in1.ControlFlowConstraint,
                                                                    in2.ControlFlowConstraint);

        } else if (in1.ControlFlowConstraint != null && in2.ControlFlowConstraint != null
                    && in2.ControlFlowConstraint.contains(in1.ControlFlowConstraint)) {

            this.ControlFlowConstraint = in1.ControlFlowConstraint;

        } else if (in1.ControlFlowConstraint != null && in2.ControlFlowConstraint != null
                    && in1.ControlFlowConstraint.contains(in2.ControlFlowConstraint)) {

            this.ControlFlowConstraint = in2.ControlFlowConstraint;

        } else {
            this.ControlFlowConstraint = Predicate.combine(Predicate.Operator.OR,
                                                           in1.ControlFlowConstraint,
                                                           in2.ControlFlowConstraint);
        }
    }

    protected Predicate getSharedPredicateForMerge(Predicate in1, Predicate in2) {
        if (in1.equals(in2)) {
            return in1;
        }

        if (in1.isBinary() && in2.isBinary() && in1.getOperator().equals(in2.getOperator())) {
            Predicate shared = getSharedPredicateForMerge(
                    ((BinaryPredicate)in1).getLeftChild(),
                    ((BinaryPredicate)in2).getLeftChild());
            return shared;
        }

        return null;
    }

    public void printDataMappings() {
        this.LocalMap.forEach(
                (x,y) -> { Output.debug("    local map: " + x + " -> " + y); });
        this.HeapMap.forEach(
                (x,y) -> { Output.debug("    heap map: " + x + " -> " + y); });
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof DataMap)) {
            return false;
        }

        DataMap other = (DataMap)obj;
        return this.LocalMap.equals(other.LocalMap) && this.HeapMap.equals(other.HeapMap);
    }

    public int hashCode() {
        return new HashCodeBuilder()
                .append(this.LocalMap)
                .append(this.HeapMap)
                .toHashCode();
    }

}
