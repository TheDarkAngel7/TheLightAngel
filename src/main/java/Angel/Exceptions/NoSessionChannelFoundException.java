package Angel.Exceptions;

public class NoSessionChannelFoundException extends Throwable {
    public NoSessionChannelFoundException(String name) {
        super("No Such Matching Session Channel Found with name " + name);
    }
}
