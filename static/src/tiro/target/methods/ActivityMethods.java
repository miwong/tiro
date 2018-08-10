package tiro.target.methods;

import tiro.Output;

import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.VoidType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActivityMethods extends Methods {
    private static final SootClass _activityClass =
            Scene.v().getSootClass("android.app.Activity");

    private static class ActivityStartInfo {
        public final int IntentParameterIndex;
        public final boolean RequiresResult;

        public ActivityStartInfo(int intentIndex, boolean requiresResult) {
            IntentParameterIndex = intentIndex;
            RequiresResult = requiresResult;
        }
    }

    private static List<SootMethodRef> _lifecycleMethods = new ArrayList<SootMethodRef>();
    private static Map<SootMethod, ActivityStartInfo> _startActivityInfo =
            new HashMap<SootMethod, ActivityStartInfo>();

    static {
        // Lifecycle methods.
        safeAddMethodRef(_lifecycleMethods, _activityClass, "onCreate",
                Arrays.asList(new Type[] { RefType.v("android.os.Bundle") }),
                VoidType.v(), false);
        safeAddMethodRef(_lifecycleMethods, _activityClass, "onCreate",
                Arrays.asList(new Type[] { RefType.v("android.os.Bundle"),
                                           RefType.v("android.os.PersistableBundle") }),
                VoidType.v(), false);
        safeAddMethodRef(_lifecycleMethods, _activityClass, "onStart",
                Collections.<Type>emptyList(), VoidType.v(), false);
        safeAddMethodRef(_lifecycleMethods, _activityClass, "onRestart",
                Collections.<Type>emptyList(), VoidType.v(), false);
        safeAddMethodRef(_lifecycleMethods, _activityClass, "onResume",
                Collections.<Type>emptyList(), VoidType.v(), false);
        safeAddMethodRef(_lifecycleMethods, _activityClass, "onPause",
                Collections.<Type>emptyList(), VoidType.v(), false);
        safeAddMethodRef(_lifecycleMethods, _activityClass, "onStop",
                Collections.<Type>emptyList(), VoidType.v(), false);
        safeAddMethodRef(_lifecycleMethods, _activityClass, "onDestroy",
                Collections.<Type>emptyList(), VoidType.v(), false);

        // Activity start methods.
        safeAddMethod(_startActivityInfo, "<android.app.Activity: "
                + "void startActivity(android.content.Intent)>",
                new ActivityStartInfo(0, false));
        safeAddMethod(_startActivityInfo, "<android.app.Activity: "
                + "void startActivity(android.content.Intent,android.os.Bundle)>",
                new ActivityStartInfo(0, false));
        safeAddMethod(_startActivityInfo, "<android.app.Activity: "
                + "void startActivityForResult(android.content.Intent,int)>",
                new ActivityStartInfo(0, true));
        safeAddMethod(_startActivityInfo, "<android.app.Activity: "
                + "void startActivityForResult(android.content.Intent,int,android.os.Bundle)>",
                new ActivityStartInfo(0, true));
        safeAddMethod(_startActivityInfo, "<android.app.Activity: "
                + "void startActivityFromChild(android.app.Activity,android.content.Intent,"
                + "int)>",
                new ActivityStartInfo(1, false));
        safeAddMethod(_startActivityInfo, "<android.app.Activity: "
                + "void startActivityFromChild(android.app.Activity,android.content.Intent,"
                + "int,android.os.Bundle)>",
                new ActivityStartInfo(0, false));

        //safeAddMethod(_startActivityInfo, "<android.app.Activity: "
        //        + "",
        //        new ActivityStartInfo(0, false));
    }

    public static List<SootMethodRef> getLifecycleMethods() {
        return _lifecycleMethods;
    }
}
