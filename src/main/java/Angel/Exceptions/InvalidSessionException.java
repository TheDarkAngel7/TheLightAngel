package Angel.Exceptions;

public class InvalidSessionException extends Throwable {

    public InvalidSessionException() {
        super("Could Not Find a Session!");
    }

    public InvalidSessionException(String name) {
        super("Could Not Find a Session using getSession() with the name of " + name);
    }
}
