package Angel.PlayerList;

import Angel.BotAbuse.BotAbuseLogic;
import Angel.EmbedDesign;
import Angel.Exceptions.InvalidSessionException;
import Angel.MessageEntry;
import Angel.TargetChannelSet;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerListMain extends ListenerAdapter implements BotAbuseLogic {
    private final Logger log = LogManager.getLogger(PlayerListMain.class);

    private final List<String> commands = Arrays.asList("playersm", "playerm", "plm",
            "playersam", "playeram", "plam", "playersa", "playera", "pla", "players", "player", "pl",
            "host", "shot", "headcount", "hc");

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getMessage().getAuthor().isBot()) return;

        Message msg = event.getMessage();
        String[] args;

        try {
            args = msg.getContentRaw().substring(1).split(" ");
        }
        catch (StringIndexOutOfBoundsException ex) {
            // Take No Action - This exception is already handed by DiscordBotMain
            return;
        }

        if (msg.getContentRaw().charAt(0) == mainConfig.commandPrefix) {

            switch (args[0].toLowerCase()) {
                case "playersm":
                case "playerm":
                case "plm":
                    playerListCommand(msg, false, true);
                    break;
                case "playersam":
                case "playeram":
                case "plam":
                    playerListCommand(msg, true, true);
                    break;
                case "playersa":
                case "playera":
                case "pla":
                    playerListCommand(msg, true,false);
                    break;
                case "player":
                case "players":
                case "pl":
                    playerListCommand(msg, false, false);
                    break;
            }
        }
    }

    // !pl
    private void playerListCommand(Message msg, boolean sortAlphabetically, boolean useMentions) {

        String[] args = msg.getContentRaw().substring(1).split(" ");

        if (usedInSessionChannel(msg)) {
            if (baCore.botAbuseIsCurrent(msg.getAuthor().getIdLong())) {
                msg.delete().queue();
                MessageEntry entry = new MessageEntry("You Are Bot Abused",
                        "**Whoops, looks like you are currently bot abused:\n\n" +
                                baCore.getInfo(msg.getAuthor().getIdLong(), false), EmbedDesign.INFO)
                        .setTargetUser(msg.getAuthor()).setChannels(TargetChannelSet.DM);

                embed.sendAsMessageEntryObj(entry);
            }
            else {
                msg.getChannel().asTextChannel()
                        .sendMessageEmbeds(getPlayerListEmbed(msg.getChannel().getName(), sortAlphabetically, useMentions)).queue();
            }
        }
        else if (msg.getChannel().getIdLong() == mainConfig.dedicatedOutputChannel.getIdLong()) {
            if (args.length == 2) {
                msg.getChannel().asTextChannel()
                        .sendMessageEmbeds(getPlayerListEmbed(args[1], sortAlphabetically, useMentions)).queue();
            }
            else {
                // Reserved for an Error
            }
        }
    }

    private MessageEmbed getPlayerListEmbed(String searchQuery, boolean sortAlphabetically, boolean useMentions) {
        Session sessionQuery = null;

        try {
            sessionQuery = sessionManager.getSession(searchQuery);
        }
        catch (InvalidSessionException e) {
            // This is for an invalid search error
        }

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

        sessionQuery.getPlayerList().forEach(player -> {

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

        EmbedBuilder builder = new EmbedBuilder();

       //  String result = "";

        if (!staffMembers.isEmpty()) {
            if (!sortAlphabetically) {
                // result = result.concat("**Staff Members (" + staffMembers.size() + "**)\n" + convertListToRegexString(staffMembers, useMentions) + "\n\n");
                Collections.shuffle(staffMembers);
            }
            builder = builder.addField("Staff Members (" + staffMembers.size() + ")", convertListToRegexString(staffMembers, useMentions), false);        }



        if (!supporters.isEmpty()) {
            // result = result.concat("**Supporters (" + supporters.size() + "**)\n" + convertListToRegexString(supporters, useMentions) + "\n\n");

            if (!sortAlphabetically) {
                Collections.shuffle(supporters);
            }
            builder = builder.addField("Supporters (" + supporters.size() + ")", convertListToRegexString(supporters, useMentions), false);
        }

        if (members.isEmpty()) {
            // result = result.concat("Members (0)");

            builder = builder.addField("Members (0)", "", false);
        }

        else {
            // result = result.concat("**Members (" + members.size() + "**)\n" + convertListToRegexString(members, useMentions) + "\n\n");

            if (staffMembers.isEmpty() && supporters.isEmpty()) {
                builder = builder.addField("", convertListToRegexString(members, useMentions), false);
            }
            else {
                builder = builder.addField("Members (" + members.size() + ")", convertListToRegexString(members, useMentions), false);
            }

        }

        // Build the Resulting Output

        int playerCount = staffMembers.size() + supporters.size() + members.size();

        builder = builder.setTitle(sessionQuery.getSessionName() + " (" +  playerCount + ")")
                .setThumbnail("https://zoobot.fingered.me/rsrc/icon/" + sessionQuery.getSessionName().toLowerCase() + "_128sm.png");

        switch (sessionQuery.getStatus()) {
            case OFFLINE:
                builder = builder.clearFields().addField("", "**" + sessionQuery.getSessionName() + " is offline until further notice!**", false)
                        .setTitle(sessionQuery.getSessionName() + " Status");
                break;
            case ONLINE:
                if (playerCount == 0) {
                    builder = builder.clearFields().addField("", "**" + sessionQuery.getSessionName() + " is empty... YEET!**", false)
                            .setColor(Color.RED);
                }
                else if (playerCount <= 9) {
                    builder = builder.setFooter("**Low Player Count** - Great for Sourcing!").setColor(Color.getColor("#80FF40"));
                }
                else if (playerCount >= 10 && playerCount < 20) {
                    builder = builder.setFooter("**Money Making Time - Join Now!**").setColor(Color.GREEN);
                }
                else {
                    builder = builder.setFooter("**Money Making Time - May Be Hard to Join**").setColor(Color.RED);
                }
                    break;
            case RESTARTING:
            case RESTART_MOD:
            case RESTART_SOON:
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
                result = result.concat("**" + players.get(index).getDiscordAccount().getEffectiveName() + "**");
            }

            if (index < players.size() - 1) {
                result = result.concat(", ");
            }

            index++;
        }

        return result;
    }
    private boolean usedInSessionChannel(Message msg) {
        int index = 0;
        int sessionListSize = sessionManager.getSessions().size();

        while (index < sessionListSize) {

            if (msg.getChannel().asTextChannel().getIdLong() == sessionManager.getSessions().get(index++).getSessionChannel().getIdLong()) {
                return true;
            }

        }
        return false;
    }

    public boolean isValidCommand(String cmd) {
        return commands.contains(cmd.toLowerCase());
    }
}
