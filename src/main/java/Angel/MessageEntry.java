package Angel;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MessageEntry {
    private String title;
    private String msg;
    private EmbedDesign design;
    private Message originalCmd;
    private Message resultEmbed;
    private boolean fieldOriginallyIncluded = true;
    private List<TargetChannelSet> channels = new ArrayList<>();
    private User targetUser;
    private final MainConfiguration mainConfig;

    public MessageEntry(String title, String msg, EmbedDesign design, MainConfiguration mainConfig) {
        this.title = title;
        this.msg = msg;
        this.design = design;
        this.mainConfig = mainConfig;
    }

    public MessageEntry setTitle(String title) {
        this.title = title;
        return this;
    }

    public MessageEntry setMessage(String msg) {
        this.msg = msg;
        return this;
    }

    public MessageEntry setChannels(List<TargetChannelSet> channels) {
        this.channels = channels;
        return this;
    }

    public MessageEntry addChannel(TargetChannelSet channel) {
        channels.add(channel);
        return this;
    }

    public MessageEntry addChannel(List<TargetChannelSet> channels) {
        this.channels.addAll(channels);
        return this;
    }

    public MessageEntry setTargetUser(User targetUser) {
        this.targetUser = targetUser;
        return this;
    }

    public MessageEntry setDesign(EmbedDesign design) {
        this.design = design;
        return this;
    }

    public MessageEntry setOriginalCmd(Message originalCmd) {
        this.originalCmd = originalCmd;
        return this;
    }

    public void setResultEmbed(Message resultEmbed) {
        this.resultEmbed = resultEmbed;
    }

    List<TargetChannelSet> getChannels() {
        return channels;
    }

    Message getOriginalCmd() {
        return originalCmd;
    }

    Message getResultEmbed() {
        return resultEmbed;
    }

    User getTargetUser() {
        return targetUser;
    }

    boolean isFieldOriginallyIncluded() {
        return fieldOriginallyIncluded;
    }

    public MessageEmbed getEmbed() {
        return getEmbed(true);
    }

    public MessageEmbed getEmbed(boolean includeFieldHeader) {
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
        if (includeFieldHeader) return embed.setTitle(title).addField(mainConfig.fieldHeader, msg, true).build();
        else {
            fieldOriginallyIncluded = false;
            return embed.setTitle(title).addField("", msg, true).build();
        }
    }
}
