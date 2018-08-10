package tiro.target;

import tiro.TIROStaticAnalysis;

import soot.jimple.infoflow.android.resources.ARSCFileParser;
import soot.jimple.infoflow.android.resources.ARSCFileParser.AbstractResource;
import soot.jimple.infoflow.android.resources.ARSCFileParser.StringResource;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

public class ResourceAnalysis {
    private final ARSCFileParser _resourceParser;

    public ResourceAnalysis(String apkPath) throws IOException {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        if (!TIROStaticAnalysis.Config.PrintSootOutput) {
            // Do not want excessive output from FlowDroid so temporariy disable stdout
            System.setOut(new PrintStream(new FileOutputStream("/dev/null")));
            System.setErr(new PrintStream(new FileOutputStream("/dev/null")));
        }

        try {
            _resourceParser = new ARSCFileParser();
            _resourceParser.parse(TIROStaticAnalysis.Config.ApkFile);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    public ARSCFileParser getResourceParser() {
        return _resourceParser;
    }

    public String getStringResource(int resourceId) {
        AbstractResource resource = _resourceParser.findResource(resourceId);
        if (resource != null && resource instanceof StringResource) {
            return ((StringResource)resource).getValue();
        }

        return null;
    }
}
