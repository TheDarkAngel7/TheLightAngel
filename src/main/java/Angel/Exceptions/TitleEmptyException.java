package Angel.Exceptions;

public class TitleEmptyException extends Exception {
    public TitleEmptyException() {
        super("MessageEntry Cannot Have An Empty Title");
    }
}