package Angel.PlayerList;

import Angel.CommonLogic;
import Angel.EmbedDesign;
import Angel.Exceptions.InvalidSessionException;
import Angel.MessageEntry;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;

import java.awt.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerListMessage implements CommonLogic {

    private final Session targetSession;
    private TextChannel targetChannel;
    private boolean useMentions = false;
    private boolean sortAlphabetically = false;

    public PlayerListMessage(String sessionName) throws InvalidSessionException {
        this.targetSession = sessionManager.getSession(sessionName);
        this.targetChannel = targetSession.getSessionChannel();
    }

    public PlayerListMessage useMentions(boolean useMentions) {
        this.useMentions = useMentions;
        return this;
    }

    public PlayerListMessage sortListAlphabetically(boolean sortAlphabetically) {
        this.sortAlphabetically = sortAlphabetically;
        return this;
    }

    public PlayerListMessage setTargetChannel(TextChannel targetChannel) {
        this.targetChannel = targetChannel;
        return this;
    }
    
    public MessageCreateAction getMessageCreateAction() {
        InputStream resourceStream = getClass().getResourceAsStream("/sessions/" + targetSession.getSessionName() +"_128sm.png");
        FileUpload thumbnail = FileUpload.fromData(resourceStream, targetSession.getSessionName() + "_128sm.png");
        
        return targetChannel.sendMessageEmbeds(getPlayerListEmbed()).setFiles(thumbnail);
    }

    private MessageEmbed getPlayerListEmbed() {

        /**
        try {
            targetSession = sessionManager.getSession(searchQuery);
        }
        catch (InvalidSessionException e) {

        }
         **/

        // List of Staff Members
        List<Player> staffMembers = new ArrayList<>();

        // List of "Supporters"
        // Supporters are VIPs, Nitro Boosters, or Patrons
        List<Player> supporters = new ArrayList<>();

        // List of Members who are Not Supporters
        List<Player> members = new ArrayList<>();

        // Atomic Boolean for Unrecognized Player Detection
        // If a Player Cannot be found in the discord, the footer then comes in and says that a Kickvote may be needed
        AtomicBoolean unrecognizedPlayerFound = new AtomicBoolean(false);

        targetSession.getPlayerList().forEach(player -> {

            if (player.isStaff()) {
                staffMembers.add(player);
            }
            else if (player.isSupporter()) {
                supporters.add(player);
            }
            else if (player.isCrewMember()) {
                members.add(player);
            }
            else {
                unrecognizedPlayerFound.set(true);
            }
        });

        if (!sortAlphabetically) {
            Collections.shuffle(staffMembers);
            Collections.shuffle(supporters);
            Collections.shuffle(members);
        }
        else {
            staffMembers.sort(Comparator.comparing(Player::getPlayerName, String.CASE_INSENSITIVE_ORDER));
            supporters.sort(Comparator.comparing(Player::getPlayerName, String.CASE_INSENSITIVE_ORDER));
            members.sort(Comparator.comparing(Player::getPlayerName, String.CASE_INSENSITIVE_ORDER));
        }

        EmbedBuilder builder = new EmbedBuilder();

        //  String result = "";

        if (!staffMembers.isEmpty()) {
            builder = builder.addField("Staff (" + staffMembers.size() + ")", convertListToRegexString(staffMembers, useMentions), false);
        }

        if (!supporters.isEmpty()) {
            builder = builder.addField("Supporters (" + supporters.size() + ")", convertListToRegexString(supporters, useMentions), false);
        }

        if (!members.isEmpty()) {
            if (staffMembers.isEmpty() && supporters.isEmpty()) {
                builder = builder.addField("", convertListToRegexString(members, useMentions), false);
            }
            else {
                builder = builder.addField("Members (" + members.size() + ")", convertListToRegexString(members, useMentions), false);
            }
        }

        // Build the Resulting Output

        int playerCount = staffMembers.size() + supporters.size() + members.size();

        builder = builder.setTitle(targetSession.getSessionName() + " (" +  playerCount + ")")
                .setThumbnail("attachment://" +  targetSession.getSessionName().toLowerCase() + "_128sm.png");

        switch (targetSession.getStatus()) {
            case OFFLINE:
                return new MessageEntry(targetSession.getSessionName() + " Status", "**" + targetSession.getSessionName() + " is Offline until Further Notice**", EmbedDesign.STOP)
                        .getEmbed(false);
            case ONLINE:
                if (playerCount == 0) {
                    builder = builder.clearFields().addField("", "**" + targetSession.getSessionName() + " is empty... YEET!**", false)
                            .setColor(Color.RED);
                }
                else if (playerCount <= 9) {
                    builder = builder.setFooter("Low Player Count - Great for Sourcing! - Last Read: " + targetSession.getLastUpdateInSeconds() + "s ago")
                            .setColor(Color.decode("#80FF40"));
                }
                else if (playerCount >= 10 && playerCount < 20) {
                    builder = builder.setFooter("Money Making Time - Join Now! - Last Read: " + targetSession.getLastUpdateInSeconds() + "s ago")
                            .setColor(Color.GREEN);
                }
                else {
                    builder = builder.setFooter("Money Making Time - May Be Hard to Join - Last Read: " + targetSession.getLastUpdateInSeconds() + "s ago")
                            .setColor(Color.decode("#005700"));
                }

                if (targetSession.isExperiencingScreenshotTrouble()) {
                    builder = builder.addField("", ":warning: **My sensors tell me that " + targetSession.getSessionName() +
                            " appears to be having difficulties capturing the player list. The previous known good player list is being displayed.**", false);
                }
                break;
            case RESTARTING:
                return new MessageEntry(targetSession.getSessionName() + " Status", "**" + targetSession.getSessionName() + " is being restarted at this time. You will be notified in "
                        + targetSession.getSessionChannel().getAsMention() + " when the session is back online.**", EmbedDesign.STOP).getEmbed(false);
            case RESTART_MOD:
                return new MessageEntry(targetSession.getSessionName() + " Status", "**" + targetSession.getSessionName() + " is being restarted due to modded elements that have been discovered in the session. You will be notified in "
                        + targetSession.getSessionChannel().getAsMention() + " when the session is back online.", EmbedDesign.STOP).getEmbed(false);
            case RESTART_SOON:
                builder = builder.addField("", ":warning: This session is going to be restarted soon! If you are in the session please wrap up what you are doing and leave it as soon as possible. If you outside the session please stay out until the session is back online.", false)
                        .setColor(Color.YELLOW).setThumbnail(mainConfig.getWarningIconURL());
                break;
        }

        return builder.build();
    }

    private String convertListToRegexString(List<Player> players, boolean useMentions) {
        int index = 0;
        String result = "";

        while (index < players.size()) {
            if (useMentions) {
                result = result.concat(players.get(index).getDiscordAccount().getAsMention());
            }
            else {
                result = result.concat("`" + players.get(index).getDiscordAccount().getEffectiveName() + "`");
            }

            if (index < players.size() - 1) {
                result = result.concat(", ");
            }

            index++;
        }

        return result;
    }
}
