package tiro.target;

public class StaticAnalysisTimeoutException extends RuntimeException {
    public StaticAnalysisTimeoutException(String phase) {
        super("Timeout during " + phase);
    }
}
