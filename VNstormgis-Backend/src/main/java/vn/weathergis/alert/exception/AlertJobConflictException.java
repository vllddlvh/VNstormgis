package vn.weathergis.alert.exception;

/**
 * Thrown when a verified alert submission conflicts with the persisted job's snapshot, keys, or
 * previously committed verified content.
 */
public class AlertJobConflictException extends IllegalStateException {

    public AlertJobConflictException(String message) {
        super(message);
    }
}
