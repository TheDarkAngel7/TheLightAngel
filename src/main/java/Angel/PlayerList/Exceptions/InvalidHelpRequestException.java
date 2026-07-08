package Angel.PlayerList.Exceptions;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class InvalidHelpRequestException extends Throwable {
    private final String message;
    public InvalidHelpRequestException(TextChannel channel, String message) {
        super("Unable to create a HelpRequest in " + channel.getName() + ": " + message);
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
