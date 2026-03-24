package Angel.Exceptions;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class InvalidHelpRequestException extends Throwable {
    public InvalidHelpRequestException(TextChannel channel, String message) {
        super("Unable to create a HelpRequest in " + channel.getName() + ": " + message);
    }
}
