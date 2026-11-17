package vn.weathergis.chat.exception;

/** Internal chat-context failure (e.g. corrupt stored snapshot); never mapped to NO_DATA. */
public class ChatContextException extends RuntimeException {

    public ChatContextException(String message, Throwable cause) {
        super(message, cause);
    }
}
