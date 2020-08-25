package Angel;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;

public abstract class EmbedHandler {
    private MainConfiguration mainConfig;
    private DiscordBotMain discord;
    private EmbedBuilder embedBuilder = new EmbedBuilder();
    // Image Background Hex: #2F3136
    public String checkIcon;
    public String warningIcon;
    public String errorIcon;
    public String infoIcon;
    public String stopIcon;
    String helpIcon;
    private ArrayList<Thread> threadList = new ArrayList<>();
    private Message messageEmbed;
    private boolean embedReady = false;

    EmbedHandler(MainConfiguration mainConfig) {
        this.mainConfig = mainConfig;
        checkIcon = mainConfig.checkIconURL;
        warningIcon = mainConfig.warningIconURL;
        errorIcon = mainConfig.errorIconURL;
        infoIcon = mainConfig.infoIconURL;
        stopIcon = mainConfig.stopIconURL;
        helpIcon = mainConfig.helpIconURL;
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
        embedBuilder.setColor(Color.GREEN);
        embedBuilder.setTitle(title);
        embedBuilder.setThumbnail(checkIcon);
        addMessage(msg);
    }
    public void setAsWarning(String title, String msg) {
        isEmbedReadyToModify();
        embedBuilder.setColor(Color.YELLOW);
        embedBuilder.setTitle(title);
        embedBuilder.setThumbnail(warningIcon);
        addMessage(msg);
    }
    public void setAsError(String title, String msg) {
        isEmbedReadyToModify();
        embedBuilder.setColor(Color.RED);
        embedBuilder.setTitle(title);
        embedBuilder.setThumbnail(errorIcon);
        addMessage(msg);
    }
    public void setAsStop(String title, String msg) {
        isEmbedReadyToModify();
        embedBuilder.setColor(Color.RED);
        embedBuilder.setTitle(title);
        embedBuilder.setThumbnail(stopIcon);
        addMessage(msg);
    }
    public void setAsInfo(String title, String msg) {
        isEmbedReadyToModify();
        embedBuilder.setColor(Color.BLUE);
        embedBuilder.setTitle(title);
        embedBuilder.setThumbnail(infoIcon);
        addMessage(msg);
    }
    public void setAsHelp(String title, String msg) {
        isEmbedReadyToModify();
        embedBuilder.setColor(Color.BLUE);
        embedBuilder.setTitle(title);
        embedBuilder.setThumbnail(helpIcon);
        addMessage(msg);
    }
    private void addMessage(String msg) {
        while (!embedReady) {
            Message testEmbed = null;
            try {
                embedBuilder.addField(mainConfig.fieldHeader, msg, true);
                testEmbed = mainConfig.owner.
                        getUser().openPrivateChannel().flatMap(channel -> channel.sendMessage(embedBuilder.build())).complete();
            }
            catch (NullPointerException ex) {
                 testEmbed = mainConfig.guild.getJDA().getUserById("260562868519436308").
                        openPrivateChannel().flatMap(channel -> channel.sendMessage(embedBuilder.build())).complete();
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
    //////////////////////////////////////////////////////////
    // Output Handler Methods
    // The "Member author" objects in the ToHelpChannel and ToDiscussionChannel methods are annotated as Nullable
    // as in some cases in the code I cannot get Member objects
    ///////////////////////////////////////////////////////////
    public void sendDM(User user) {
        user.openPrivateChannel().flatMap(channel -> channel.sendMessage(messageEmbed)).queue();
        messageSent();
    }
    public void sendToHelpChannel(MessageChannel msgChannel, @Nullable Member author) {
        TextChannel channel = discord.guild.getTextChannelById(msgChannel.getIdLong());
        try {
            if (channel != mainConfig.botSpamChannel) {
                if (channel != mainConfig.helpChannel) {
                    mainConfig.helpChannel.sendMessage(author.getAsMention()).queue();
                }
                mainConfig.helpChannel.sendMessage(messageEmbed).queue();
            }
            else {
                mainConfig.botSpamChannel.sendMessage(messageEmbed).queue();
            }
        }
        catch (NullPointerException ex) {
            // Take No Action
        }
        messageSent();
    }
    public void sendToTeamDiscussionChannel(MessageChannel msgChannel, @Nullable Member author) {
        TextChannel channel = discord.guild.getTextChannelById(msgChannel.getIdLong());
        if (channel != mainConfig.managementChannel) {
            if (author != null && (channel != mainConfig.discussionChannel && channel != mainConfig.managementChannel)
                    && discord.isTeamMember(author.getIdLong())) {
                mainConfig.discussionChannel.sendMessage(author.getAsMention()).queue();
            }
            mainConfig.discussionChannel.sendMessage(messageEmbed).queue();
        }
        else {
            mainConfig.managementChannel.sendMessage(messageEmbed).queue();
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
        while (threadList.get(0) != Thread.currentThread()) {
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
        try {
            embedBuilder.clearFields();
            embedReady = false;
            // Remove this Thread from the list.
            threadList.remove(Thread.currentThread());
        }
        catch (IndexOutOfBoundsException e) {
            // Take No Action
        }
    }
}