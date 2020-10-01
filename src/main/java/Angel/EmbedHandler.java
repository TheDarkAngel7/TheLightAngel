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
    private EmbedType type = EmbedType.EMBED_NONE;
    // Dictionary<Command Message Object, Output Message Object>
    private Dictionary<Message, Message> commandMessageMap = new Hashtable();
    // Image Background Hex: #2F3136
    private ArrayList<Thread> threadList = new ArrayList<>();
    private Message messageEmbed;
    private boolean embedReady = false;

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
        isEmbedReadyToModify();
        embedBuilder = type.getBuilder(type.EMBED_SUCCESS, mainConfig).setTitle(title);
        addMessage(msg);
    }
    public void setAsWarning(String title, String msg) {
        isEmbedReadyToModify();
        embedBuilder = type.getBuilder(type.EMBED_WARNING, mainConfig).setTitle(title);
        addMessage(msg);
    }
    public void setAsError(String title, String msg) {
        isEmbedReadyToModify();
        embedBuilder = type.getBuilder(type.EMBED_ERROR, mainConfig).setTitle(title);
        addMessage(msg);
    }
    public void setAsStop(String title, String msg) {
        isEmbedReadyToModify();
        embedBuilder = type.getBuilder(type.EMBED_STOP, mainConfig).setTitle(title);
        addMessage(msg);
    }
    public void setAsInfo(String title, String msg) {
        isEmbedReadyToModify();
        embedBuilder = type.getBuilder(type.EMBED_INFO, mainConfig).setTitle(title);
        addMessage(msg);
    }
    public void setAsHelp(String title, String msg) {
        isEmbedReadyToModify();
        embedBuilder = type.getBuilder(type.EMBED_HELP, mainConfig).setTitle(title);
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
    public void editEmbed(Message originalCmd, String newTitle, String newMsg, boolean keepOriginalDesign, @Nullable EmbedType requestedType) {
        isEmbedReadyToModify();
        Message botResponse = commandMessageMap.get(originalCmd);
        MessageEmbed msgEmbed = botResponse.getEmbeds().get(0);
        if (keepOriginalDesign) {
            embedBuilder.setColor(msgEmbed.getColor());
            embedBuilder.setTitle(msgEmbed.getTitle());
            embedBuilder.setThumbnail(msgEmbed.getThumbnail().getUrl());
        }
        else embedBuilder = type.getBuilder(requestedType, mainConfig);

        embedBuilder.addField(mainConfig.fieldHeader, msgEmbed.getFields().get(0).getValue(), true);
        if (newTitle == null && newMsg != null) {
            embedBuilder.clearFields().addField(mainConfig.fieldHeader, newMsg, true);
        }
        else if (newMsg == null) {
            embedBuilder.setTitle(newTitle);
        }
        else {
            embedBuilder.clearFields().addField(mainConfig.fieldHeader, newMsg, true);
            embedBuilder.setTitle(newTitle);
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
    public void sendToHelpChannel(Message msg, @Nullable Member author) {
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
                if (msg.getChannel().equals(mainConfig.botSpamChannel)) {
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
    public void sendToTeamDiscussionChannel(Message msg, @Nullable Member author) {
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
    // Tagging author is not necessary in the log channel
    public void sendToLogChannel() {
        mainConfig.logChannel.sendMessage(messageEmbed).queue();
        messageSent();
    }

    public void sendToChannel(MessageChannel channel) {
        try {
            channel.sendMessage(messageEmbed).queue();
        }
        catch (IllegalArgumentException ex) {
            channel.sendMessage(embedBuilder.build()).queue();
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