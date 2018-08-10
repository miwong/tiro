package tiro.target.methods;

import tiro.Output;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class Methods {
    protected static String normalizeSupportLibraryMethod(String signature) {
        return signature.replaceAll("android.support.v[0-9]+.app", "android.support.app");
    }

    protected static void safeAddClass(Set<SootClass> destinationSet, String className) {
        if (Scene.v().containsClass(className)) {
            destinationSet.add(Scene.v().getSootClass(className));
        }
    }

    protected static <T> void safeAddMethod(Map<SootMethod, T> destinationMap,
            String methodSignature, T value) {
        if (Scene.v().containsMethod(methodSignature)) {
            destinationMap.put(Scene.v().getMethod(methodSignature), value);
        }
    }

    protected static void safeAddMethod(Set<SootMethod> destinationSet,
            String methodSignature) {
        if (Scene.v().containsMethod(methodSignature)) {
            destinationSet.add(Scene.v().getMethod(methodSignature));
        }
    }

    protected static void safeAddMethodRef(Collection<SootMethodRef> destination,
            SootClass klass, String methodName, List<Type> parameterTypes, Type returnType,
            boolean isStatic) {
        SootMethodRef methodRef = safeMakeMethodRef(klass, methodName, parameterTypes,
                returnType, isStatic);
        if (methodRef != null) {
            destination.add(methodRef);
        }
    }

    protected static SootMethodRef safeMakeMethodRef(SootClass klass, String methodName,
            List<Type> parameterTypes, Type returnType, boolean isStatic) {
        if (klass.declaresMethod(methodName, parameterTypes)) {
            return Scene.v().makeMethodRef(klass, methodName, parameterTypes, returnType,
                    isStatic);
        }
        return null;
    }
}
