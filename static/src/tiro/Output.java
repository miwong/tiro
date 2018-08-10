package tiro;

public class Output {
    private static ThreadLocal<StringBuffer> _buffer = new ThreadLocal<StringBuffer>();

    public static void log(String output) {
        outputOrBuffer(output);
    }

    public static void warn(String output) {
        outputOrBuffer("[Warning] " + output);
    }

    public static void error(String output) {
        outputOrBuffer("[Error] " + output);
        //(new Exception()).printStackTrace();
    }

    public static void debug(boolean debugFlag, String output) {
        if (debugFlag) {
            outputOrBuffer("[Debug] " + output);
        }
    }

    public static void debug(String output) {
        outputOrBuffer("[Debug] " + output);
    }

    public static void progress(String output) {
        double elapsedTime = ((double)System.currentTimeMillis()
                - TIROStaticAnalysis.Config.StartTime) / 60000;
        outputOrBuffer(String.format(">>> %s (%.3f min)", output, elapsedTime));
        System.out.flush();
    }

    public static void printSubtitle(String subtitle) {
        String titleString = " - - - - - - - - - - - - - - - - - - - - -"
                           + " - - - - - - - - - - - - - - - - - - - - -";
        int startIndex = (84 - 6 - subtitle.length()) / 2;
        titleString = titleString.substring(0, startIndex)
                + " [ " + subtitle + " ] "
                + titleString.substring(startIndex + subtitle.length() + 6);

        outputOrBuffer(titleString);
    }

    public static void printEventChainDivider() {
        outputOrBuffer(" --------------------------------------------"
                     + "---------------------------------------------");
    }

    public static void printPath(String output) {
        outputOrBuffer("  [Path] " + output);
    }

    public static void printConstraint(String output) {
        outputOrBuffer("  [Constraint] " + output);
    }

    public static void printInstrumentation(String output) {
        outputOrBuffer("  [Instrument] " + output);
    }

    public static void startBuffering() {
        _buffer.set(new StringBuffer());
    }

    public static void flushBuffer() {
        System.out.print(_buffer.get().toString());
        _buffer.set(null);
    }

    public static void clearBuffer() {
        _buffer.set(new StringBuffer());
    }

    private static void outputOrBuffer(String output) {
        if (_buffer.get() == null) {
            System.out.println(output);
        } else {
            StringBuffer currentBuffer = _buffer.get();
            currentBuffer.append(output);
            currentBuffer.append(System.lineSeparator());
        }
    }
}

