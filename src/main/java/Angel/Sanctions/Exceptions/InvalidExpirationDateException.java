package Angel.Sanctions.Exceptions;

public class InvalidExpirationDateException extends Exception {
    public InvalidExpirationDateException(String message) {
        super("Cannot Parse Expiration Date: " + message);
    }
}
