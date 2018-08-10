package tiro.target.constraint;

import tiro.Output;
import tiro.target.dependency.Dependence;

import soot.Type;

public class KeyValueAccessVariable extends SymbolicVariable implements Dependence {
    public enum DatabaseType {
        BUNDLE,
        SHARED_PREFS,
        STRING_TABLE
    }

    private final KeyValueAccessVariable.DatabaseType _databaseType;
    private final Variable _database;
    private final Variable _key;

    public KeyValueAccessVariable(Variable database, Variable key,
                                  KeyValueAccessVariable.DatabaseType databaseType,
                                  Type type) {
        super(type);
        _databaseType = databaseType;
        _database = database;
        _key = key;

        setSymbol(toString());
    }

    public KeyValueAccessVariable.DatabaseType getDatabaseType() {
        return _databaseType;
    }

    public Variable getDatabaseVariable() {
        return _database;
    }

    public Variable getKeyVariable() {
        return _key;
    }

    @Override public boolean isInputVariable() { return false; }
    @Override public boolean isSystemVariable() { return false; }
    @Override public boolean isHeapVariable() { return false; }

    @Override
    public String toString() {
        String databaseString = _database != null ? _database.toString() : "unknown";
        String keyString = _key != null ? _key.toString() : "*";

        String typeString;
        switch (_databaseType) {
            case BUNDLE:
                typeString = "Bundle";
                break;
            case SHARED_PREFS:
                typeString = "SharedPrefs";
                break;
            case STRING_TABLE:
                typeString = "StringTable";
                break;
            default:
                typeString = "KeyValueStore";
                break;
        }

        return typeString + "<" + databaseString + ">[" + keyString + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof KeyValueAccessVariable)) {
            return false;
        }

        KeyValueAccessVariable other = (KeyValueAccessVariable)obj;
        return _databaseType.equals(other.getDatabaseType())
                && _database.equals(other.getDatabaseVariable())
                && _key.equals(other.getKeyVariable());
    }

    @Override
    public boolean dependsOnInput() {
        return (_database != null && _database.dependsOnInput())
                || (_key != null && _key.dependsOnInput());
    }

    @Override
    public boolean dependsOnInput(int inputVariable) {
        return (_database != null && _database.dependsOnInput(inputVariable))
                || (_key != null && _key.dependsOnInput(inputVariable));
    }
}
