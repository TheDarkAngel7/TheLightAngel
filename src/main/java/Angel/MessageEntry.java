package Angel;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class MessageEntry {
    private String title;
    private String msg;
    private EmbedDesign design;
    private Message originalCmd;
    private List<TargetChannelSet> channels = new ArrayList<>();
    private User targetUser;
    private final MainConfiguration mainConfig;

    MessageEntry(String title, String msg, EmbedDesign design, MainConfiguration mainConfig) {
        this.title = title;
        this.msg = msg;
        this.design = design;
        this.mainConfig = mainConfig;
    }

    void setTitle(String title) {
        this.title = title;
    }

    void setMessage(String msg) {
        this.msg = msg;
    }

    void setChannels(List<TargetChannelSet> channels) {
        this.channels = channels;
    }

    void setOriginalCmd(Message originalCmd) {
        this.originalCmd = originalCmd;
    }

    void setTargetUser(User targetUser) {
        this.targetUser = targetUser;
    }

    List<TargetChannelSet> getChannels() {
        return channels;
    }

    Message getOriginalCmd() {
        return originalCmd;
    }

    User getTargetUser() {
        return targetUser;
    }

    MessageEmbed getEmbed() {
        // Image Background Hex: #2F3136
        EmbedBuilder embed = new EmbedBuilder();
        switch (design) {
            case SUCCESS:
                embed.setColor(Color.GREEN).setThumbnail(mainConfig.checkIconURL);
                break;
            case WARNING:
                embed.setColor(Color.YELLOW).setThumbnail(mainConfig.warningIconURL);
                break;
            case ERROR:
                embed.setColor(Color.RED).setThumbnail(mainConfig.errorIconURL);
                break;
            case STOP:
                embed.setColor(Color.RED).setThumbnail(mainConfig.stopIconURL);
                break;
            case INFO:
                embed.setColor(Color.BLUE).setThumbnail(mainConfig.infoIconURL);
                break;
            case HELP:
                embed.setColor(Color.decode("#2F3136").brighter()).setThumbnail(mainConfig.helpIconURL);
                break;
        }
        return embed.setTitle(title).addField(mainConfig.fieldHeader, msg, true).build();
    }
}
