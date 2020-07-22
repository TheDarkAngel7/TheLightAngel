package Angel;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;

public abstract class EmbedHandler {
    private MainConfiguration mainConfig;
    private EmbedBuilder embed = new EmbedBuilder();
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

    public void setAsSuccess(String title, String msg) {
        isEmbedReadyToModify();
        embed.setColor(Color.GREEN);
        embed.setTitle(title);
        embed.setThumbnail(checkIcon);
        addMessage(msg);
    }
    public void setAsWarning(String title, String msg) {
        isEmbedReadyToModify();
        embed.setColor(Color.YELLOW);
        embed.setTitle(title);
        embed.setThumbnail(warningIcon);
        addMessage(msg);
    }
    public void setAsError(String title, String msg) {
        isEmbedReadyToModify();
        embed.setColor(Color.RED);
        embed.setTitle(title);
        embed.setThumbnail(errorIcon);
        addMessage(msg);
    }
    public void setAsStop(String title, String msg) {
        isEmbedReadyToModify();
        embed.setColor(Color.RED);
        embed.setTitle(title);
        embed.setThumbnail(stopIcon);
        addMessage(msg);
    }
    public void setAsInfo(String title, String msg) {
        isEmbedReadyToModify();
        embed.setColor(Color.BLUE);
        embed.setTitle(title);
        embed.setThumbnail(infoIcon);
        addMessage(msg);
    }
    public void setAsHelp(String title, String msg) {
        isEmbedReadyToModify();
        embed.setColor(Color.BLUE);
        embed.setTitle(title);
        embed.setThumbnail(helpIcon);
        addMessage(msg);
    }
    private void addMessage(String msg) {
        while (!embedReady) {
            embed.addField(mainConfig.fieldHeader, msg, true);
            Message testEmbed = mainConfig.owner.
                    getUser().openPrivateChannel().flatMap(channel -> channel.sendMessage(embed.build())).complete();
            if (testEmbed.getEmbeds().get(0).getFields().size() == 1) {
                embedReady = true;
                messageEmbed = testEmbed;
            }
            else if (testEmbed.getEmbeds().get(0).getFields().size() > 1) {
                embed.clearFields();
                continue;
            }
            else continue;
        }
    }

    public void sendDM(User user) {
        user.openPrivateChannel().flatMap(channel -> channel.sendMessage(messageEmbed)).queue();
        messageSent();
    }
    public void sendToHelpChannel(MessageChannel msgChannel, @Nullable Member author) {
        if (msgChannel != mainConfig.botSpamChannel) {
            if (msgChannel != mainConfig.helpChannel) {
                mainConfig.helpChannel.sendMessage(author.getAsMention()).queue();
            }
            mainConfig.helpChannel.sendMessage(messageEmbed).queue();
        }
        else {
            mainConfig.botSpamChannel.sendMessage(messageEmbed).queue();
        }
        messageSent();
    }
    public void sendToTeamDiscussionChannel(MessageChannel msgChannel, @Nullable Member author) {
        if (msgChannel != mainConfig.managementChannel) {
            if (msgChannel != mainConfig.discussionChannel && msgChannel != mainConfig.managementChannel) {
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
    private void isEmbedReadyToModify() {
        if (!embed.getFields().isEmpty()) {
            try {
                threadList.add(Thread.currentThread());
                Thread.sleep(1000000000);
            }
            catch (InterruptedException e) {
                threadList.remove(0);
            }
        }
    }
    private void messageSent() {
        try {
            embed.clearFields();
            embedReady = false;
            threadList.get(0).interrupt();
        }
        catch (IndexOutOfBoundsException e) {
            // Take No Action
        }
    }
}
