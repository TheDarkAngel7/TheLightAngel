package Angel.Sanctions.Exceptions;

public class InvalidExpirationDateException extends RuntimeException {
    public InvalidExpirationDateException(String message) {
        super("Cannot Parse Expiration Date: " + message);
    }
}
