package Angel.Nicknames;

import Angel.CustomListEmbed;
import Angel.MessageEntry;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

class NickRequestListEmbed extends CustomListEmbed {
    private final Logger log = LogManager.getLogger(NickRequestListEmbed.class);
    private final NicknameMain nickMain;
    private final String checkMarkUnicode = "\u2705";
    private final String crossMarkUnicode = "\u274C";

    NickRequestListEmbed(MessageEntry entry, String prefixString, List<String> alternateStrings, @Nullable String suffixString, NicknameMain nickMain) {
        super(entry, prefixString, alternateStrings, suffixString);
        this.nickMain = nickMain;
    }

    @Override
    public List<String> getEmoteUnicodeToReactOn() {
        return Arrays.asList(checkMarkUnicode, crossMarkUnicode);
    }

    @Override
    public String getEmoteLabeling() {
        return checkMarkUnicode + " **Click to Accept This Nickname Request**" +
                "\n" + crossMarkUnicode + " **Click to Reject This Nickname Request**";
    }

    @Override
    public void takeAction(Message originalCmd, MessageReactionAddEvent event) {
        String result = deleteCurrentPage();
        long targetDiscordID = Long.parseLong(result.split("<@!")[1].split(">")[0]);
        boolean requestStatus = true;
        String reactionType = "";

        try {
            switch (event.getReaction().getReactionEmote().getAsReactionCode()) {
                case checkMarkUnicode:
                    reactionType = "Check Mark";
                    break;
                case crossMarkUnicode:
                    reactionType = "The Red X";
                    requestStatus = false;
                    break;
            }

            log.info(event.getUser().getAsTag() + " just reacted with the " + reactionType);
            nickMain.requestHandler(originalCmd, requestStatus, targetDiscordID);
        }
        catch (IOException ex) {
            log.error("Caught Exception by the Nickname Request Handler", ex);
        }
    }
}
