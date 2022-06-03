package Angel.CheckIn;

import Angel.CustomListEmbed;
import Angel.MessageEntry;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class CheckInQueueEmbed extends CustomListEmbed {
    private final List<String> reactions;
    private final CheckInMain ciMain;
    private final Logger log = LogManager.getLogger(CheckInQueueEmbed.class);

    CheckInQueueEmbed(MessageEntry entry, String prefixString, List<String> alternateStrings, @Nullable String suffixString, List<String> emoteList, CheckInMain ciMain) {
        super(entry, prefixString, alternateStrings, suffixString);
        reactions = emoteList;
        this.ciMain = ciMain;
    }

    @Override
    public List<String> getEmoteUnicodeToReactOn() {
        return reactions;
    }

    @Override
    public String getEmoteLabeling() {
        return "**To toggle someone in or out of the queue select the corresponding emoji next to their name**";
    }

    @Override
    public void takeAction(Message originalCmd, MessageReactionAddEvent event) {
        log.info(event.getMember().getEffectiveName() + " clicked number " + (reactions.indexOf(event.getReaction().getReactionEmote().getAsReactionCode()) + 1));
        ciMain.fromReactionEmoji(originalCmd,
                (getCurrentPage() - 1) * 10 + (reactions.indexOf(event.getReaction().getReactionEmote().getAsReactionCode()) + 1));
    }
}