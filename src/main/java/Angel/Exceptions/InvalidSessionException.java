package Angel.Exceptions;

import Angel.CommonLogic;

public class InvalidSessionException extends Throwable implements CommonLogic {
    public InvalidSessionException(String name) {
        super("Could Not Find a Session using getSession() with the name of " + name);
    }
}
