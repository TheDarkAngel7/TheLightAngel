package Angel;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class CustomListEmbed {

    private final ListEmbed listEmbed;

    public CustomListEmbed(MessageEntry entry, String prefixString, List<String> alternateStrings, @Nullable String suffixString) {
        this.listEmbed = new ListEmbed(entry, prefixString, alternateStrings, suffixString, this);
    }

    public abstract List<String> getEmoteUnicodeToReactOn();

    public abstract String getEmoteLabeling();

    public abstract void takeAction(Message originalCmd, MessageReactionAddEvent event);

    public String deleteCurrentPage() {
        return listEmbed.deletePage(listEmbed.getCurrentPage());
    }

    public int getCurrentPage() {
        return listEmbed.getCurrentPage();
    }

    public ListEmbed getListEmbed() {
        return listEmbed;
    }
}