package Angel.PlayerList.Exceptions;

public class KickvoteException extends RuntimeException {
    public KickvoteException(String message, String sessionName) {
        super("KickvoteException Thrown for " + sessionName + ": " + message);
    }
}
