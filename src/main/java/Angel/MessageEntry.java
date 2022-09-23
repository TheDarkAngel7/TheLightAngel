package Angel;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MessageEntry {
    private String title;
    private String msg;
    private EmbedDesign design;
    private AtomicReference<Message> originalCmd = new AtomicReference<>();
    private AtomicReference<Message> resultEmbed = new AtomicReference<>();
    private boolean fieldOriginallyIncluded = true;
    private boolean isListEmbed = false;
    private List<TargetChannelSet> targetChannels = new ArrayList<>();
    private List<TextChannel> customChannels = new ArrayList<>();
    private User targetUser;
    private final MainConfiguration mainConfig;

    public MessageEntry(String title, String msg, EmbedDesign design, MainConfiguration mainConfig) {
        this.title = title;
        this.msg = msg;
        this.design = design;
        this.mainConfig = mainConfig;
    }

    // Constructor Specifically For Custom Channels, We Don't Ask For a TargetChannelSet here and we ask for the MessageChannel objects

    public MessageEntry(String title, EmbedDesign design, MainConfiguration mainConfig, Message originalCmd, TextChannel... channels) {
        this.title = title;
        this.design = design;
        this.originalCmd.set(originalCmd);
        this.mainConfig = mainConfig;
        this.customChannels = Arrays.asList(channels);
        this.targetChannels.add(TargetChannelSet.CUSTOM);
    }
    // Constructor Exactly Like the one above except an initial message is included
    public MessageEntry(String title, String msg, EmbedDesign design, MainConfiguration mainConfig, Message originalCmd, TextChannel... channels) {
        this.title = title;
        this.msg = msg;
        this.design = design;
        this.originalCmd.set(originalCmd);
        this.mainConfig = mainConfig;
        this.customChannels = Arrays.asList(channels);
        this.targetChannels.add(TargetChannelSet.CUSTOM);
    }

    // Constructor Specifically for Creating new ListEmbed objects
    // We won't require a message string here as that'll be set by the ListEmbed constructor method

    public MessageEntry(String title, EmbedDesign design, MainConfiguration mainConfig, Message originalCmd, TargetChannelSet... sets) {
        this.title = title;
        this.design = design;
        this.mainConfig = mainConfig;
        this.originalCmd.set(originalCmd);
        this.targetChannels = Arrays.asList(sets);
        isListEmbed = true;
    }

    public MessageEntry (String title, String msg, EmbedDesign design, MainConfiguration mainConfig, Message originalCmd, TargetChannelSet... sets) {
        this.title = title;
        this.msg = msg;
        this.design = design;
        this.mainConfig = mainConfig;
        this.originalCmd.set(originalCmd);
        this.targetChannels = Arrays.asList(sets);
        isListEmbed = true;
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
        this.targetChannels = channels;
        return this;
    }

    public MessageEntry setChannels(TargetChannelSet... channels) {
        this.targetChannels = Arrays.asList(channels);
        return this;
    }

    public MessageEntry setCustomChannels(TextChannel... messageChannels) {
        customChannels = Arrays.asList(messageChannels);
        targetChannels.add(TargetChannelSet.CUSTOM);
        return this;

    }

    public MessageEntry setCustomChannels(List<TextChannel> messageChannels) {
        customChannels.addAll(messageChannels);
        targetChannels.add(TargetChannelSet.CUSTOM);
        return this;
    }

    public MessageEntry addChannel(TargetChannelSet channel) {
        targetChannels.add(channel);
        return this;
    }

    public MessageEntry addChannel(List<TargetChannelSet> channels) {
        this.targetChannels.addAll(channels);
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
        this.originalCmd.set(originalCmd);
        return this;
    }

    MessageEntry setResultEmbed(Message resultEmbed) {
        this.resultEmbed.set(resultEmbed);
        return this;
    }

    public MessageEntry dontUseFieldEmbed() {
        fieldOriginallyIncluded = false;
        return this;
    }

    public MessageEntry setAsIsListEmbed() {
        isListEmbed = true;
        return this;
    }

    List<TargetChannelSet> getTargetChannels() {
        return targetChannels;
    }

    List<TextChannel> getCustomChannels() {
        return customChannels;
    }

    public Message getOriginalCmd() {
        return originalCmd.get();
    }

    public Message getResultEmbed() {
        return resultEmbed.get();
    }

    User getTargetUser() {
        return targetUser;
    }

    String getMessage() {
        return msg;
    }

    String getTitle() {
        return title;
    }

    boolean isFieldOriginallyIncluded() {
        return fieldOriginallyIncluded;
    }

    boolean isListEmbed() {
        return isListEmbed;
    }

    // Default Setting False as that is what this class is initalized with,
    // but using this method will just construct the MessageEmbed object with the setting that is current
    public MessageEmbed getEmbed() {
        return getEmbed(fieldOriginallyIncluded);
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
