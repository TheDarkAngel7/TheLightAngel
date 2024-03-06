package Angel.CustomEmbeds;

import Angel.*;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class CustomEmbedMain extends ListenerAdapter implements CommonLogic {
    private final Logger log = LogManager.getLogger(CustomEmbedMain.class);
    private final DiscordBotMain discord;

    CustomEmbedMain(DiscordBotMain discord) {
        this.discord = discord;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        Thread.currentThread().setUncaughtExceptionHandler(aue);
        if (event.getAuthor().isBot() || !event.getChannelType().isGuild()) return;
        Message msg = event.getMessage();
        String[] args = null;

        try {
            args = msg.getContentRaw().substring(1).split(" ");
        }
        catch (StringIndexOutOfBoundsException ex) {
            // Take No Action - Another Class Handles this
            return;
        }

        if (msg.getContentRaw().charAt(0) == mainConfig.commandPrefix) {

            switch (args[0].toLowerCase()) {
                case "e":
                case "embed":
                    newCustomEmbed(msg);
                    break;
            }
        }
    }
    private void newCustomEmbed(Message msg) {
        String[] args = msg.getContentRaw().substring(1).split(" ");

        EmbedDesign requestedDesign = null;

        if (discord.isTeamMember(msg.getAuthor().getIdLong())) {
            switch (args[1].toLowerCase()) {
                case "check":
                case "success":
                    requestedDesign = EmbedDesign.SUCCESS;
                    break;
                case "!":
                case "warn":
                case "warning":
                    requestedDesign = EmbedDesign.WARNING;
                    break;
                case "x":
                case "error":
                    requestedDesign = EmbedDesign.ERROR;
                    break;
                case "stop":
                    requestedDesign = EmbedDesign.STOP;
                    break;
                case "i":
                case "info":
                    requestedDesign = EmbedDesign.INFO;
                    break;
                case "?":
                case "help":
                    requestedDesign = EmbedDesign.HELP;
                    break;
            }
            if (args.length == 2) {
                embed.sendAsMessageEntryObj(new MessageEntry("Sample Output", "**This is a Sample**" +
                        "\n\nIf you would like to use this design use the same command again followed by what you want displayed" +
                        "\n`" + msg.getContentRaw() + " <Message>` or `" + msg.getContentRaw() + " <Title | Message>`"
                        , requestedDesign).setOriginalCmd(msg).setChannels(TargetChannelSet.SAME).dontUseFieldHeader());
                log.info(msg.getMember().getEffectiveName() + " requested a sample output of Embed Design " + requestedDesign.name());
            }
            else {
                String title = "";
                String customMsg = "";
                // index 2 to skip the cmd and first argument
                int index = 2;
                int breakIndex = 0;
                int charSum = 0;

                if (!msg.getContentRaw().contains("|")) {
                    title = "Message From " + msg.getMember().getEffectiveName();
                }
                else {
                    do {
                        if (args[index].charAt(0) == '|') {
                            breakIndex = index++;
                            break;
                        }
                        else if (args[index].contains("|")) {

                            if (args[index].charAt(args[index].length() - 1) == '|') {
                                title = title.concat(args[index].split("|")[0]);
                            }
                            else {
                                customMsg = customMsg.concat(args[index].split("|")[1]);
                                charSum = customMsg.toCharArray().length;
                            }
                            breakIndex = index++;
                            break;
                        }
                        else {
                            title = title.concat(args[index] + " ");
                        }
                    } while (++index < args.length);
                }

                do {
                    if (msg.getContentRaw().contains("|") && index == breakIndex) {
                        customMsg = customMsg.concat(args[index].substring(1) + " ");
                    }
                    else {
                        customMsg = customMsg.concat(args[index] + " ");
                    }
                } while (++index < args.length);

                embed.sendAsMessageEntryObj(new MessageEntry(title, customMsg, requestedDesign)
                        .setChannels(TargetChannelSet.SAME).setOriginalCmd(msg).dontUseFieldHeader());
                log.info(msg.getMember().getEffectiveName() + " successfully created an embed with character length of " + charSum +
                        " and was sent into the channel #" + msg.getChannel().getName());
            }
        }
        else {
            embed.setAsError("No Permissions", ":x: **You can only make me talk if you are a staff member... which you are not...**");
            embed.sendToMemberOutput(msg, msg.getAuthor());
        }
    }
}