package Angel;

import Angel.Exceptions.TitleEmptyException;
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

public class MessageEntry implements CommonLogic {
    private String title = "";
    private String msg = "";
    private String footer = "";
    private EmbedDesign design;
    private AtomicReference<Message> originalCmd = new AtomicReference<>();
    private AtomicReference<Message> resultEmbed = new AtomicReference<>();
    private boolean fieldOriginallyIncluded = true;
    private boolean isListEmbed = false;
    private List<TargetChannelSet> targetChannels = new ArrayList<>();
    private List<TextChannel> customChannels = new ArrayList<>();
    private User targetUser;

    public MessageEntry() {
        this.title = "System Message";
        this.msg = "";
        this.design = EmbedDesign.INFO;
    }
    public MessageEntry(String title, String msg, EmbedDesign design) {
        this.title = title;
        this.msg = msg;
        this.design = design;
    }

    // Constructor Specifically For Sending the Same Embed to
    // Multiple Channels, We Don't Ask For a TargetChannelSet here, and we ask for the TextChannel objects

    public MessageEntry(String title, EmbedDesign design, Message originalCmd, TextChannel... channels) {
        this.title = title;
        this.design = design;
        this.originalCmd.set(originalCmd);
        this.customChannels.addAll(Arrays.asList(channels));
        this.targetChannels.add(TargetChannelSet.CUSTOM);
    }
    // Constructor Exactly Like the one above except an initial message is included
    public MessageEntry(String title, String msg, EmbedDesign design, Message originalCmd, TextChannel... channels) {
        this.title = title;
        this.msg = msg;
        this.design = design;
        this.originalCmd.set(originalCmd);
        this.customChannels.addAll(Arrays.asList(channels));
        this.targetChannels.add(TargetChannelSet.CUSTOM);
    }

    // Constructor Specifically for Creating new ListEmbed objects
    // We won't require a message string here as that'll be set by the ListEmbed constructor method

    public MessageEntry(String title, EmbedDesign design, Message originalCmd, TargetChannelSet... sets) {
        this.title = title;
        this.design = design;
        this.originalCmd.set(originalCmd);
        this.targetChannels.addAll(Arrays.asList(sets));
        isListEmbed = true;
    }

    public MessageEntry (String title, String msg, EmbedDesign design, Message originalCmd, TargetChannelSet... sets) {
        this.title = title;
        this.msg = msg;
        this.design = design;
        this.originalCmd.set(originalCmd);
        this.targetChannels.addAll(Arrays.asList(sets));
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

    public MessageEntry setFooter(String footer) {
        this.footer = footer;
        return this;
    }

    public MessageEntry setChannels(List<TargetChannelSet> channels) {
        this.targetChannels = channels;
        return this;
    }

    public MessageEntry setChannels(TargetChannelSet... channels) {
        if (!targetChannels.isEmpty()) targetChannels.clear();
        this.targetChannels.addAll(Arrays.asList(channels));
        return this;
    }

    public MessageEntry setCustomChannels(TextChannel... messageChannels) {
        if (!customChannels.isEmpty()) customChannels.clear();
        customChannels.addAll(Arrays.asList(messageChannels));
        targetChannels.add(TargetChannelSet.CUSTOM);
        return this;

    }

    public MessageEntry setCustomChannels(List<TextChannel> messageChannels) {
        if (!customChannels.isEmpty()) customChannels.clear();
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
    // Target User is the one to receive the Direct Message if TargetChannelSet.DM is among the target channels.
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

    public MessageEntry setResultEmbed(Message resultEmbed) {
        this.resultEmbed.set(resultEmbed);
        return this;
    }

    public MessageEntry dontUseFieldHeader() {
        fieldOriginallyIncluded = false;
        return this;
    }

    public MessageEntry setAsIsListEmbed() {
        isListEmbed = true;
        return this;
    }

    public List<TargetChannelSet> getTargetChannels() {
        return targetChannels;
    }

    public List<TextChannel> getCustomChannels() {
        return customChannels;
    }

    public Message getOriginalCmd() {
        return originalCmd.get();
    }

    public Message getResultEmbed() {
        return resultEmbed.get();
    }

    public User getTargetUser() {
        return targetUser;
    }

    public String getMessage() {
        return msg;
    }

    public String getTitle() {
        return title;
    }

    public boolean isFieldOriginallyIncluded() {
        return fieldOriginallyIncluded;
    }

    public boolean isListEmbed() {
        return isListEmbed;
    }

    // Default Setting True (fieldOriginallyIncluded) as that is what this class is initalized with,
    // but using this method will just construct the MessageEmbed object with the setting that is current
    // This is in case the dontUseFieldHeader method is used, the changed variable will be used
    public MessageEmbed getEmbed() {
        return getEmbed(fieldOriginallyIncluded);
    }

    public MessageEmbed getEmbed(boolean includeFieldHeader) {
        // Image Background Hex: #2F3136
        EmbedBuilder embed = new EmbedBuilder();
        switch (design) {
            case SUCCESS:
                embed.setColor(Color.GREEN).setThumbnail(mainConfig.getCheckIconURL());
                break;
            case WARNING:
                embed.setColor(Color.YELLOW).setThumbnail(mainConfig.getWarningIconURL());
                break;
            case ERROR:
                embed.setColor(Color.RED).setThumbnail(mainConfig.getErrorIconURL());
                break;
            case STOP:
                embed.setColor(Color.RED).setThumbnail(mainConfig.getStopIconURL());
                break;
            case INFO:
                embed.setColor(Color.BLUE).setThumbnail(mainConfig.getInfoIconURL());
                break;
            case HELP:
                embed.setColor(Color.decode("#2F3136").brighter()).setThumbnail(mainConfig.getHelpIconURL());
                break;
        }

        if (title.isEmpty()) {
            aue.logCaughtException(Thread.currentThread(), new TitleEmptyException());
            title = "Message From System";
        }

        if (!footer.isEmpty()) {
            embed.setFooter(footer);
        }

        if (includeFieldHeader) return embed.setTitle(title).addField(mainConfig.fieldHeader, msg, true).build();
        else {
            fieldOriginallyIncluded = false;
            return embed.setTitle(title).addField("", msg, true).build();
        }
    }
}