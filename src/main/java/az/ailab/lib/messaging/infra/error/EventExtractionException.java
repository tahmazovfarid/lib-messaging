package az.ailab.lib.messaging.infra.error;

public class EventExtractionException extends RuntimeException {

    public EventExtractionException(String message) {
        super(message);
    }

    public EventExtractionException(String message, Throwable cause) {
        super(message, cause);
    }

}
