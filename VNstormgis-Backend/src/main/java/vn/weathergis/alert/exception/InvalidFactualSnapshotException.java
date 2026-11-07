package vn.weathergis.alert.exception;

/** Snapshot cannot be interpreted without inventing facts. */
public class InvalidFactualSnapshotException extends IllegalStateException {
    public InvalidFactualSnapshotException() {
        super("Invalid factual snapshot");
    }

    public InvalidFactualSnapshotException(Throwable cause) {
        super("Invalid factual snapshot", cause);
    }
}
