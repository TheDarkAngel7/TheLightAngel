package Angel.CheckIn.AFKChecks;

import Angel.CustomListEmbed;
import Angel.MessageEntry;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class AFKCheckListEmbed extends CustomListEmbed {
    private final Logger log = LogManager.getLogger(AFKCheckListEmbed.class);
    private final AFKCheckManagement afkMain;
    private final String refreshEmote = "\uD83D\uDD01";
    private final String cancelEmote = "\u274C";


    AFKCheckListEmbed(MessageEntry entry, String prefixString, List<String> alternateStrings, @Nullable String suffixString, AFKCheckManagement afkMain) {
        super(entry, prefixString, alternateStrings, suffixString);
        this.afkMain = afkMain;
    }

    @Override
    public List<String> getEmoteUnicodeToReactOn() {
        return Arrays.asList(refreshEmote, cancelEmote);
    }

    @Override
    public String getEmoteLabeling() {
        return refreshEmote + " **Refresh List**" +
                "\n" + cancelEmote + " **Cancel this AFK Check**";
    }

    @Override
    public void takeAction(Message originalCmd, MessageReactionAddEvent event) {
        switch (event.getReaction().getReactionEmote().getAsReactionCode()) {
            case refreshEmote:
                log.info(event.getUser().getAsTag() + " just requested a refresh");
                afkMain.refreshAFKCheckListEmbed();
                break;
            case cancelEmote:
                AtomicReference<Member> targetStaffMember = new AtomicReference<>();
                AtomicReference<Member> targetMember = new AtomicReference<>();
                long targetDiscordID = Long.parseLong(getListEmbed().getCurrentPage().split("<@")[1].split(">")[0]);

                event.getGuild().retrieveMemberById(event.getUser().getIdLong()).queue(targetStaffMember::set);
                event.getGuild().retrieveMemberById(targetDiscordID).queue(targetMember::set);

                afkMain.cancelAFKCheck(targetDiscordID);

                log.info(targetStaffMember.get().getEffectiveName() + " just cancelled the AFK Check of " +
                        targetMember.get().getEffectiveName());
                break;
        }
    }
}
