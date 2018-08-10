package tiro.target.methods;

import tiro.Output;

import soot.BooleanType;
import soot.IntType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethodRef;
import soot.Type;
import soot.VoidType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ServiceMethods extends Methods {
    protected static final SootClass _serviceClass =
            Scene.v().getSootClass("android.app.Service");

    private static List<SootMethodRef> _lifecycleMethods = new ArrayList<SootMethodRef>();

    static {
        // Lifecycle methods.
        safeAddMethodRef(_lifecycleMethods, _serviceClass, "onCreate",
                Collections.<Type>emptyList(), VoidType.v(), false);
        safeAddMethodRef(_lifecycleMethods, _serviceClass, "onBind",
                Arrays.asList(new Type[] { RefType.v("android.content.Intent") }),
                RefType.v("android.os.IBinder"), false);
        safeAddMethodRef(_lifecycleMethods, _serviceClass, "onRebind",
                Arrays.asList(new Type[] { RefType.v("android.content.Intent") }),
                RefType.v("android.os.IBinder"), false);
        safeAddMethodRef(_lifecycleMethods, _serviceClass, "onStart",
                Arrays.asList(new Type[] { RefType.v("android.content.Intent"), IntType.v() }),
                VoidType.v(), false);
        safeAddMethodRef(_lifecycleMethods, _serviceClass, "onStartCommand",
                Arrays.asList(new Type[] { RefType.v("android.content.Intent"), IntType.v(),
                                           IntType.v() }),
                IntType.v(), false);
        safeAddMethodRef(_lifecycleMethods, _serviceClass, "onTaskRemoved",
                Arrays.asList(new Type[] { RefType.v("android.content.Intent") }),
                VoidType.v(), false);
        safeAddMethodRef(_lifecycleMethods, _serviceClass, "onUnbind",
                Arrays.asList(new Type[] { RefType.v("android.content.Intent") }),
                BooleanType.v(), false);
    }

    public static List<SootMethodRef> getLifecycleMethods() {
        return _lifecycleMethods;
    }
}
