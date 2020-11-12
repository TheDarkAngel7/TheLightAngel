package Angel;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;

public class EmbedHandler {
    private MainConfiguration mainConfig;
    private DiscordBotMain discord;
    private EmbedBuilder embedBuilder;
    private EmbedDesign design = EmbedDesign.NONE;
    // Dictionary<Command Message Object, Output Message Object>
    private Dictionary<Message, Message> commandMessageMap = new Hashtable();
    private ArrayList<Thread> threadList = new ArrayList<>();
    private Message messageEmbed;
    private boolean embedReady = false;

    EmbedHandler(MainConfiguration mainConfig) {
        this.mainConfig = mainConfig;
        design.setConfig(mainConfig);
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
        isEmbedReadyToModify();
        embedBuilder = design.getBuilder(design.SUCCESS).setTitle(title);
        addMessage(msg);
    }
    public void setAsWarning(String title, String msg) {
        isEmbedReadyToModify();
        embedBuilder = design.getBuilder(design.WARNING).setTitle(title);
        addMessage(msg);
    }
    public void setAsError(String title, String msg) {
        isEmbedReadyToModify();
        embedBuilder = design.getBuilder(design.ERROR).setTitle(title);
        addMessage(msg);
    }
    public void setAsStop(String title, String msg) {
        isEmbedReadyToModify();
        embedBuilder = design.getBuilder(design.STOP).setTitle(title);
        addMessage(msg);
    }
    public void setAsInfo(String title, String msg) {
        isEmbedReadyToModify();
        embedBuilder = design.getBuilder(design.INFO).setTitle(title);
        addMessage(msg);
    }
    public void setAsHelp(String title, String msg) {
        isEmbedReadyToModify();
        embedBuilder = design.getBuilder(design.HELP).setTitle(title);
        addMessage(msg);
    }
    private void addMessage(String msg) {
        while (!embedReady) {
            Message testEmbed = null;
            try {
                embedBuilder.addField(mainConfig.fieldHeader, msg, true);
                testEmbed = mainConfig.owner.getUser()
                        .openPrivateChannel().flatMap(channel -> channel.sendMessage(embedBuilder.build())).complete();
            }
            catch (NullPointerException ex) {
                testEmbed = mainConfig.guild.getJDA().retrieveUserById("260562868519436308").complete()
                        .openPrivateChannel().flatMap(channel -> channel.sendMessage(embedBuilder.build())).complete();
            }
            if (testEmbed.getEmbeds().get(0).getFields().size() == 1) {
                embedReady = true;
                messageEmbed = testEmbed;
            }
            else if (testEmbed.getEmbeds().get(0).getFields().size() > 1) {
                embedBuilder.clearFields();
            }
        }
    }
    public void clearFields() {
        embedBuilder.clearFields();
    }
    public void editEmbed(Message originalCmd, String newTitle, String newMsg) {
        isEmbedReadyToModify();
        MessageEmbed msgEmbed = commandMessageMap.get(originalCmd).getEmbeds().get(0);
        embedBuilder.setColor(msgEmbed.getColor()).setTitle(msgEmbed.getTitle())
                .setThumbnail(msgEmbed.getThumbnail().getUrl());
        embedEditor(originalCmd, newTitle, newMsg);
    }

    public void editEmbed(Message originalCmd, String newTitle, String newMsg, EmbedDesign requestedType) {
        isEmbedReadyToModify();
        embedBuilder = design.getBuilder(requestedType);
        embedEditor(originalCmd, newTitle, newMsg);
    }
    private void embedEditor(Message originalCmd, String newTitle, String newMsg) {
        Message botResponse = commandMessageMap.get(originalCmd);
        MessageEmbed msgEmbed = null;
        boolean msgEmbedEmpty = true;
        while (msgEmbedEmpty) {
            try {
                msgEmbed = botResponse.getEmbeds().get(0);
                msgEmbedEmpty = false;
            }
            catch (NullPointerException ex) {
                try { Thread.sleep(1000); } catch (InterruptedException e) {}
            }
            catch (IndexOutOfBoundsException ex) {
                break;
            }
        }

        embedBuilder.addField(mainConfig.fieldHeader, msgEmbed.getFields().get(0).getValue(), true);
        if (newTitle == null && newMsg != null) {
            embedBuilder.clearFields().addField(mainConfig.fieldHeader, newMsg, true);
        }
        else if (newMsg == null) {
            embedBuilder.setTitle(newTitle);
        }
        else {
            embedBuilder.clearFields().setTitle(newTitle).addField(mainConfig.fieldHeader, newMsg, true);
        }
        botResponse.getChannel().editMessageById(botResponse.getIdLong(), embedBuilder.build()).queue();
        messageSent();
    }
    ///////////////////////////////////////////////////////////
    // Output Handler Methods
    // The "Member author" objects in the ToHelpChannel and ToDiscussionChannel methods are annotated as Nullable
    // as in some cases in the code I cannot get Member objects
    ///////////////////////////////////////////////////////////
    public void sendDM(@Nullable Message msg, User user) {
        user.openPrivateChannel().flatMap(channel -> channel.sendMessage(messageEmbed)).queue(m -> {
                if (msg != null) commandMessageMap.put(msg, m);
        });
        messageSent();
    }
    public void sendToHelpChannel(Message msg, @Nullable User author) {
        if (msg.getChannelType().equals(ChannelType.PRIVATE)) sendDM(msg, author);
        else {
            try {
                if (!msg.getChannel().equals(mainConfig.botSpamChannel) &&
                        (!mainConfig.forceToDedicatedChannel || msg.getChannel().equals(mainConfig.helpChannel) ||
                                mainConfig.dedicatedOutputChannelID.equalsIgnoreCase("None"))) {
                    if (author != null && !msg.getChannel().equals(mainConfig.helpChannel)) {
                        mainConfig.helpChannel.sendMessage(author.getAsMention()).queue();
                    }
                    mainConfig.helpChannel.sendMessage(messageEmbed).queue(m -> commandMessageMap.put(msg, m));
                }
                else if (mainConfig.forceToDedicatedChannel || !msg.getChannel().equals(mainConfig.dedicatedOutputChannel)) {
                    if (!mainConfig.botSpamChannelID.equalsIgnoreCase("None")
                            && msg.getChannel().equals(mainConfig.botSpamChannel)) {
                        mainConfig.botSpamChannel.sendMessage(messageEmbed).queue(m -> commandMessageMap.put(msg, m));
                    }
                    else {
                        if (author != null && !msg.getChannel().equals(mainConfig.dedicatedOutputChannel)) {
                            mainConfig.dedicatedOutputChannel.sendMessage(author.getAsMention()).queue();
                        }
                        mainConfig.dedicatedOutputChannel.sendMessage(messageEmbed).queue(m -> commandMessageMap.put(msg, m));
                    }
                }
            }
            catch (NullPointerException ex) {
                // Take No Action
            }
            messageSent();
        }
    }
    public void sendToTeamDiscussionChannel(Message msg, @Nullable User author) {
        if (msg.getChannelType().equals(ChannelType.PRIVATE)) sendDM(msg, author);
        else {
            if (!msg.getChannel().equals(mainConfig.managementChannel)) {
                if (author != null && (!msg.getChannel().equals(mainConfig.discussionChannel) && !msg.getChannel().equals(mainConfig.managementChannel))
                        && discord.isTeamMember(author.getIdLong())) {
                    mainConfig.discussionChannel.sendMessage(author.getAsMention()).queue();
                }
                mainConfig.discussionChannel.sendMessage(messageEmbed).queue(m -> commandMessageMap.put(msg, m));
            }
            else {
                mainConfig.managementChannel.sendMessage(messageEmbed).queue(m -> commandMessageMap.put(msg, m));
            }
            messageSent();
        }
    }
    // Tagging author is not necessary in the log channel
    public void sendToLogChannel() {
        mainConfig.logChannel.sendMessage(messageEmbed).queue();
        messageSent();
    }

    public void sendToChannel(Message msg, MessageChannel channel) {
        try {
            channel.sendMessage(messageEmbed).queue(m -> {
                if (msg != null) commandMessageMap.put(msg, m);
            });
        }
        catch (IllegalArgumentException ex) {
            channel.sendMessage(embedBuilder.build()).queue(m -> {
                if (msg != null) commandMessageMap.put(msg, m);
            });
        }
        messageSent();
    }
    // Thread Handlers
    // Each of these methods contain handlers for when this class has multiple threads running through it.

    // This method is run when the bot calls for a message to be setup (setAsInfo, setAsError, etc.)
    private void isEmbedReadyToModify() {
        if (!threadList.contains(Thread.currentThread())) threadList.add(Thread.currentThread());
        while (!threadList.get(0).equals(Thread.currentThread())) {
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                // Take No Action, the thread will be removed from the threadList later.
            }
        }
    }
    // This method is called when the message should have been sent.
    private void messageSent() {
        embedBuilder.clearFields();
        embedReady = false;
        // Remove this Thread from the list.
        threadList.remove(Thread.currentThread());
    }
}