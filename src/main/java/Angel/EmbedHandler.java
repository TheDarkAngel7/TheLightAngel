package Angel;

import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;

import javax.annotation.Nullable;
import java.util.*;

public class EmbedHandler {
    private MainConfiguration mainConfig;
    private DiscordBotMain discord;
    private List<MessageEntry> messageQueue = new ArrayList<>();
    // Dictionary<Command Message Object, Resulting MessageEntry Object>
    private Dictionary<Message, MessageEntry> commandMessageMap = new Hashtable();

    EmbedHandler(MainConfiguration mainConfig) {
        this.mainConfig = mainConfig;
    }

    void setDiscordInstance(DiscordBotMain discordInstance) {
        discord = discordInstance;
    }

    ///////////////////////////////////////////////////////////////
    // EmbedBuilder handlers
    // the coder would select the kind of embed they want
    // whether that's a Success, Warning, Info, Error, or a Stop
    // then they would code where to send it too by calling
    // one of the output handler methods.
    ///////////////////////////////////////////////////////////////

    public void setAsSuccess(String title, String msg) {
        messageQueue.add(new MessageEntry(title, msg, EmbedDesign.SUCCESS, mainConfig));
    }
    public void setAsWarning(String title, String msg) {
        messageQueue.add(new MessageEntry(title, msg, EmbedDesign.WARNING, mainConfig));
    }
    public void setAsError(String title, String msg) {
        messageQueue.add(new MessageEntry(title, msg, EmbedDesign.ERROR, mainConfig));
    }
    public void setAsStop(String title, String msg) {
        messageQueue.add(new MessageEntry(title, msg, EmbedDesign.STOP, mainConfig));
    }
    public void setAsInfo(String title, String msg) {
        messageQueue.add(new MessageEntry(title, msg, EmbedDesign.INFO, mainConfig));
    }
    public void setAsHelp(String title, String msg) {
        messageQueue.add(new MessageEntry(title, msg, EmbedDesign.HELP, mainConfig));
    }
    public void clearFields() {
        messageQueue.remove(messageQueue.size() - 1);
    }

    public void editEmbed(Message originalCmd, String newTitle, String newMsg, @Nullable EmbedDesign requestedType) {
        MessageEntry entry = commandMessageMap.get(originalCmd);
        Message botResponse = commandMessageMap.get(originalCmd).getResultEmbed();

        if (requestedType != null) {
            entry.setDesign(requestedType);
        }

        if (newTitle == null && newMsg != null) {
            entry.setMessage(newMsg);
        }
        else if (newMsg == null) {
            entry.setTitle(newTitle);
        }
        else {
            entry.setTitle(newTitle).setMessage(newMsg);
        }
        botResponse.getChannel().editMessageById(botResponse.getIdLong(), entry.getEmbed(entry.isFieldOriginallyIncluded())).queue();
    }
    ///////////////////////////////////////////////////////////
    // Output Handler Methods
    // The "Member author" objects in the ToHelpChannel and ToDiscussionChannel methods are annotated as Nullable
    // as in some cases in the code I cannot get Member objects
    ///////////////////////////////////////////////////////////
    public void sendDM(@Nullable Message msg, User user) {
        MessageEntry entry = messageQueue.get(messageQueue.size() - 1);
        entry.setOriginalCmd(msg).setTargetUser(user).setChannels(Arrays.asList(TargetChannelSet.DM));
        sendAllMessages();
    }
    public void sendToMemberOutput(Message msg, @Nullable User author) {
        MessageEntry entry = messageQueue.get(messageQueue.size() - 1);
        entry.setOriginalCmd(msg).setChannels(Arrays.asList(TargetChannelSet.MEMBER));
        if (author != null) entry.setTargetUser(author);
        sendAllMessages();
    }
    public void sendToTeamOutput(Message msg, @Nullable User author) {
        MessageEntry entry = messageQueue.get(messageQueue.size() - 1);
        entry.setOriginalCmd(msg).setChannels(Arrays.asList(TargetChannelSet.TEAM));
        if (author != null) entry.setTargetUser(author);
        sendAllMessages();
    }
    // Tagging author is not necessary in the log channel
    public void sendToLogChannel() {
        messageQueue.get(messageQueue.size() - 1).setChannels(Arrays.asList(TargetChannelSet.LOG));
        sendAllMessages();
    }

    public void sendToChannel(Message msg, MessageChannel channel) {
        channel.sendMessage(messageQueue.get(messageQueue.size() - 1).getEmbed()).queue(m -> {
            if (msg != null) commandMessageMap.put(msg, messageQueue.get(messageQueue.size() - 1));
        });
    }
    public void sendToChannels(Message msg, TargetChannelSet... sets) {
        messageQueue.get(messageQueue.size() - 1).setChannels(Arrays.asList(sets)).setOriginalCmd(msg);
        sendAllMessages();
    }
    public void deleteResultsByCommand(Message originalCmd) {
        originalCmd.delete().queue();
        commandMessageMap.remove(originalCmd).getResultEmbed().delete().queue();
    }

    private void sendAllMessages() {
        while (true) {
            try {
                messageQueue.forEach(entry -> {
                    entry.getChannels().forEach(channel -> {
                        switch (channel) {
                            case DM:
                                entry.getTargetUser().openPrivateChannel().flatMap(c -> c.sendMessage(entry.getEmbed())).queue(m -> {
                                    if (entry.getOriginalCmd() != null) {
                                        entry.setResultEmbed(m);
                                    }
                                });
                                break;
                            case LOG:
                                mainConfig.logChannel.sendMessage(entry.getEmbed()).queue();
                                break;
                            case TEAM:
                                if (entry.getOriginalCmd().getChannelType().equals(ChannelType.PRIVATE))
                                    entry.setChannels(Arrays.asList(TargetChannelSet.DM));
                                else {
                                    if (!entry.getOriginalCmd().getChannel().equals(mainConfig.managementChannel) && (!mainConfig.forceToManagementChannel ||
                                            mainConfig.managementChannelID.equalsIgnoreCase("None")
                                            || entry.getOriginalCmd().getChannel().equals(mainConfig.discussionChannel))) {
                                        if (entry.getTargetUser() != null && (!entry.getOriginalCmd().getChannel().equals(mainConfig.discussionChannel) && !entry.getOriginalCmd().getChannel().equals(mainConfig.managementChannel))
                                                && discord.isTeamMember(entry.getTargetUser().getIdLong())) {
                                            mainConfig.discussionChannel.sendMessage(entry.getTargetUser().getAsMention()).queue();
                                        }
                                        mainConfig.discussionChannel.sendMessage(messageQueue.get(0).getEmbed()).queue(m -> {
                                            entry.setResultEmbed(m);
                                        });
                                    }
                                    else {
                                        if (entry.getTargetUser() != null && !entry.getOriginalCmd().getChannel().equals(mainConfig.managementChannel)) {
                                            mainConfig.managementChannel.sendMessage(entry.getTargetUser().getAsMention()).queue();
                                        }
                                        mainConfig.managementChannel.sendMessage(messageQueue.get(0).getEmbed()).queue(m -> {
                                            entry.setResultEmbed(m);
                                        });
                                    }
                                }
                                break;
                            case MEMBER:
                                if (entry.getOriginalCmd().getChannelType().equals(ChannelType.PRIVATE))
                                    entry.setChannels(Arrays.asList(TargetChannelSet.DM));
                                else {
                                    try {
                                        if (!entry.getOriginalCmd().getChannel().equals(mainConfig.botSpamChannel) &&
                                                (!mainConfig.forceToDedicatedChannel ||
                                                        mainConfig.dedicatedOutputChannelID.equalsIgnoreCase("None"))) {
                                            if (entry.getTargetUser() != null && !entry.getOriginalCmd().getChannel().equals(mainConfig.helpChannel)) {
                                                mainConfig.helpChannel.sendMessage(entry.getTargetUser().getAsMention()).queue();
                                            }
                                            mainConfig.helpChannel.sendMessage(messageQueue.get(0).getEmbed()).queue(m -> {
                                                entry.setResultEmbed(m);
                                            });
                                        }
                                        else if (mainConfig.forceToDedicatedChannel || !entry.getOriginalCmd().getChannel().equals(mainConfig.dedicatedOutputChannel)) {
                                            if (!mainConfig.botSpamChannelID.equalsIgnoreCase("None")
                                                    && entry.getOriginalCmd().getChannel().equals(mainConfig.botSpamChannel)) {
                                                mainConfig.botSpamChannel.sendMessage(messageQueue.get(0).getEmbed()).queue(m -> {
                                                    entry.setResultEmbed(m);
                                                });
                                            }
                                            else {
                                                if (entry.getTargetUser() != null && !entry.getOriginalCmd().getChannel().equals(mainConfig.dedicatedOutputChannel)) {
                                                    mainConfig.dedicatedOutputChannel.sendMessage(entry.getTargetUser().getAsMention()).queue();
                                                }
                                                mainConfig.dedicatedOutputChannel.sendMessage(messageQueue.get(0).getEmbed()).queue(m -> {
                                                    entry.setResultEmbed(m);
                                                });
                                            }
                                        }
                                    }
                                    catch (NullPointerException ex) {
                                        // Take No Action
                                    }
                                }
                                break;
                            case SAME:
                                entry.getOriginalCmd().getChannel().sendMessage(entry.getEmbed()).queue(m -> {
                                    entry.setResultEmbed(m);
                                });
                                break;
                        }
                    });
                    messageQueue.remove(entry);
                    if (entry.getOriginalCmd() != null) {
                        commandMessageMap.put(entry.getOriginalCmd(), entry);
                    }
                });
                if (messageQueue.isEmpty()) break;
            }
            catch (ConcurrentModificationException ex) {
                // Take No Action - We want the loop to go back around
            }
        }
    }
}