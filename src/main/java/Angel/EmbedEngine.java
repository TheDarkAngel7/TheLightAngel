package Angel;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class EmbedEngine implements CommonLogic {
    private final Logger log = LogManager.getLogger(EmbedEngine.class);
    private List<MessageEntry> messageQueue = new ArrayList<>();
    // Dictionary<Command Message Object, Resulting MessageEntry Object>
    private Dictionary<Message, MessageEntry> commandMessageMap = new Hashtable();

    ///////////////////////////////////////////////////////////////
    // EmbedBuilder handlers
    // the coder would select the kind of embed they want
    // whether that's a Success, Warning, Info, Error, or a Stop
    // then they would code where to send it too by calling
    // one of the output handler methods.
    ///////////////////////////////////////////////////////////////

    public void setAsSuccess(String title, String msg) {
        messageQueue.add(new MessageEntry(title, msg, EmbedDesign.SUCCESS));
    }
    public void setAsWarning(String title, String msg) {
        messageQueue.add(new MessageEntry(title, msg, EmbedDesign.WARNING));
    }
    public void setAsError(String title, String msg) {
        messageQueue.add(new MessageEntry(title, msg, EmbedDesign.ERROR));
    }
    public void setAsStop(String title, String msg) {
        messageQueue.add(new MessageEntry(title, msg, EmbedDesign.STOP));
    }
    public void setAsInfo(String title, String msg) {
        messageQueue.add(new MessageEntry(title, msg, EmbedDesign.INFO));
    }
    public void setAsHelp(String title, String msg) {
        messageQueue.add(new MessageEntry(title, msg, EmbedDesign.HELP));
    }

    public void editEmbed(Message originalCmd, String newTitle, String newMsg, EmbedDesign requestedType) {
        MessageEntry entry = null;

        while (true) {
            entry = commandMessageMap.get(originalCmd);
            if (entry != null) break;
            else {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {}
            }
        }
        Message botResponse = commandMessageMap.get(originalCmd).getResultEmbed();
        if (requestedType != null) {
            entry.setDesign(requestedType);
        }

        if (newTitle == null && newMsg != null) {
            entry.setMessage(newMsg);
        }
        else if (newMsg == null && newTitle != null) {
            entry.setTitle(newTitle);
        }
        else if (newMsg != null && newTitle != null) {
            entry.setTitle(newTitle).setMessage(newMsg);
        }

        botResponse.getChannel().editMessageEmbedsById(botResponse.getIdLong(), entry.getEmbed(entry.isFieldOriginallyIncluded())).queue();
    }
    ///////////////////////////////////////////////////////////
    // Output Handler Methods
    // The "Member author" objects in the ToHelpChannel and ToDiscussionChannel methods are annotated as Nullable
    // as in some cases in the code I cannot get Member objects
    ///////////////////////////////////////////////////////////
    public void sendDM(Message msg, User user) {
        MessageEntry entry = messageQueue.get(messageQueue.size() - 1);
        entry.setOriginalCmd(msg).setTargetUser(user).setChannels(Arrays.asList(TargetChannelSet.DM));
        sendAllMessages();
    }
    public void sendToMemberOutput(Message msg, User author) {
        MessageEntry entry = messageQueue.get(messageQueue.size() - 1);
        entry.setOriginalCmd(msg).setChannels(Arrays.asList(TargetChannelSet.MEMBER));
        if (author != null) entry.setTargetUser(author);
        sendAllMessages();
    }
    public void sendToTeamOutput(Message msg, User author) {
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

    public void sendToChannel(Message msg, TextChannel channel) {
        MessageEntry entry = messageQueue.get(messageQueue.size() - 1);
        channel.sendMessageEmbeds(entry.getEmbed()).queue(m -> {
            if (msg != null) commandMessageMap.put(msg, entry);
        });
    }
    public void sendToChannels(Message msg, TargetChannelSet... sets) {
        messageQueue.get(messageQueue.size() - 1).setChannels(Arrays.asList(sets)).setOriginalCmd(msg);
        sendAllMessages();
    }
    public void sendToChannels(Message msg, List<TargetChannelSet> sets) {
        messageQueue.get(messageQueue.size() - 1).setChannels(sets).setOriginalCmd(msg);
        sendAllMessages();
    }
    public void deleteResultsByCommand(Message originalCmd) {
        originalCmd.delete().queue();
        commandMessageMap.remove(originalCmd).getResultEmbed().delete().queue();
    }

    public void sendAsMessageEntryObj(MessageEntry entry) {
        messageQueue.add(entry);
        sendAllMessages();
    }

    public MessageEntry getMessageEntryObj(Message originalCmd) {
        return commandMessageMap.get(originalCmd);
    }

    private void sendAllMessages() {
        while (true) {
            try {
                messageQueue.forEach(entry -> {
                    entry.getTargetChannels().forEach(channel -> {
                        switch (channel) {
                            case DM:
                                entry.getTargetUser().openPrivateChannel().flatMap(c -> c.sendMessageEmbeds(entry.getEmbed())).queue(m -> {
                                    if (entry.getOriginalCmd() != null) {
                                        placeInCmdMap(entry.setResultEmbed(m));
                                    }
                                }, error -> {
                                    log.warn("Whoops... apparently I cannot send a DM to " + entry.getTargetUser().getAsTag() + " - " + error.getMessage());
                                });
                                break;
                            case LOG:
                                mainConfig.logChannel.sendMessageEmbeds(entry.getEmbed()).queue();
                                break;
                            case TEAM:
                                if (entry.getOriginalCmd().getChannel().getIdLong() != mainConfig.managementChannel.getIdLong() && (!mainConfig.forceToManagementChannel ||
                                        mainConfig.managementChannelID.equalsIgnoreCase("None")
                                        || entry.getOriginalCmd().getChannel().getIdLong() == mainConfig.discussionChannel.getIdLong() ||
                                        // V Bypass mainConfig.forceToManagementChannel being true, in cases where channels has more than 1 target and
                                        // TEAM is one of them, go to discussion channel.
                                        entry.getTargetChannels().size() > 1 && entry.getTargetChannels().contains(TargetChannelSet.TEAM))) {
                                    if (entry.getTargetUser() != null && (entry.getOriginalCmd().getChannel().getIdLong() != mainConfig.discussionChannel.getIdLong() &&
                                            entry.getOriginalCmd().getChannel().getIdLong() != mainConfig.managementChannel.getIdLong())
                                            && isTeamMember(entry.getTargetUser().getIdLong())) {
                                        mainConfig.discussionChannel.sendMessage(entry.getTargetUser().getAsMention()).queue();
                                    }
                                    mainConfig.discussionChannel.sendMessageEmbeds(messageQueue.get(0).getEmbed()).queue(m -> {
                                        placeInCmdMap(entry.setResultEmbed(m));
                                    }, error -> {
                                        log.error("sendAllMessages Team Discussion Channel: " + error.getMessage());
                                    });
                                }
                                else {
                                    if (entry.getTargetUser() != null && entry.getOriginalCmd().getChannel().getIdLong() != mainConfig.managementChannel.getIdLong()) {
                                        mainConfig.managementChannel.sendMessage(entry.getTargetUser().getAsMention()).queue();
                                    }
                                    mainConfig.managementChannel.sendMessageEmbeds(messageQueue.get(0).getEmbed()).queue(m -> {
                                        placeInCmdMap(entry.setResultEmbed(m));
                                    }, error -> {
                                        log.error("sendAllMessages Management Channel: " + error.getMessage());
                                    });
                                }
                                break;
                            case MEMBER:
                                if (entry.getOriginalCmd().getChannelType().equals(ChannelType.PRIVATE) && !entry.isListEmbed())
                                    entry.setChannels(Arrays.asList(TargetChannelSet.DM));
                                else {
                                    try {
                                        if (entry.getOriginalCmd().getChannel().getIdLong() != mainConfig.botSpamChannel.getIdLong() &&
                                                (!mainConfig.forceToDedicatedChannel ||
                                                        mainConfig.dedicatedOutputChannelID.equalsIgnoreCase("None"))) {
                                            if (!entry.getOriginalCmd().getMember().hasPermission(mainConfig.helpChannel, Permission.VIEW_CHANNEL)) {
                                                entry.getOriginalCmd().getChannel().sendMessageEmbeds(messageQueue.get(0).getEmbed()).queue(m -> {
                                                    placeInCmdMap(entry.setResultEmbed(m).setChannels(TargetChannelSet.SAME));
                                                }, error -> {
                                                    log.error("sendAllMessages SAME Channel Due to Lack of Permissions to Help Channel: #" + entry.getOriginalCmd().getChannel().getName()+ ": " + error.getMessage());
                                                });
                                                break;
                                            }

                                            if (entry.getTargetUser() != null && entry.getOriginalCmd().getChannel().getIdLong() != mainConfig.helpChannel.getIdLong()) {
                                                mainConfig.helpChannel.sendMessage(entry.getTargetUser().getAsMention()).queue();
                                            }
                                            mainConfig.helpChannel.sendMessageEmbeds(messageQueue.get(0).getEmbed()).queue(m -> {
                                                placeInCmdMap(entry.setResultEmbed(m));
                                            }, error -> {
                                                log.error("sendAllMessages Help Channel: " + error.getMessage());
                                            });
                                        }
                                        else if (mainConfig.forceToDedicatedChannel || entry.getOriginalCmd().getChannel().getIdLong() != mainConfig.dedicatedOutputChannel.getIdLong()) {
                                            if (!mainConfig.botSpamChannelID.equalsIgnoreCase("None")
                                                    && entry.getOriginalCmd().getChannel().getIdLong() == mainConfig.botSpamChannel.getIdLong()) {
                                                mainConfig.botSpamChannel.sendMessageEmbeds(messageQueue.get(0).getEmbed()).queue(m -> {
                                                    placeInCmdMap(entry.setResultEmbed(m));
                                                }, error -> {
                                                    log.error("sendAllMessages Bot Spam Channel: " + error.getMessage());
                                                });
                                            }
                                            else {
                                                if (!entry.getOriginalCmd().getMember().hasPermission(mainConfig.dedicatedOutputChannel, Permission.VIEW_CHANNEL)) {
                                                    entry.getOriginalCmd().getChannel().sendMessageEmbeds(messageQueue.get(0).getEmbed()).queue(m -> {
                                                        placeInCmdMap(entry.setResultEmbed(m).setChannels(TargetChannelSet.SAME));
                                                    }, error -> {
                                                        log.error("sendAllMessages SAME Channel Due to Lack of Permissions to Dedicated Channel: #" + entry.getOriginalCmd().getChannel().getName()+ ": " + error.getMessage());
                                                    });
                                                    break;
                                                }
                                                if (entry.getTargetUser() != null && entry.getOriginalCmd().getChannel().getIdLong() != mainConfig.dedicatedOutputChannel.getIdLong()) {
                                                    mainConfig.dedicatedOutputChannel.sendMessage(entry.getTargetUser().getAsMention()).queue();
                                                }
                                                mainConfig.dedicatedOutputChannel.sendMessageEmbeds(messageQueue.get(0).getEmbed()).queue(m -> {
                                                    placeInCmdMap(entry.setResultEmbed(m));
                                                }, error -> {
                                                    log.error("sendAllMessages Dedicated Output Channel: " + error.getMessage());
                                                });
                                            }
                                        }
                                    }
                                    catch (NullPointerException ex) {
                                        // Handler if Bot Spam is None
                                        if (mainConfig.dedicatedOutputChannelID.equalsIgnoreCase("None") ||
                                                !entry.getOriginalCmd().getMember().hasPermission(mainConfig.dedicatedOutputChannel, Permission.VIEW_CHANNEL)) {
                                            entry.getOriginalCmd().getChannel().sendMessageEmbeds(messageQueue.get(0).getEmbed()).queue(m -> {
                                                placeInCmdMap(entry.setResultEmbed(m).setChannels(TargetChannelSet.SAME));
                                            }, error -> {
                                                log.error("sendAllMessages Same Channel - Set By Bot Spam None Handler");
                                            });
                                        }
                                        else {
                                            if (entry.getTargetUser() != null && entry.getOriginalCmd().getChannel().getIdLong() != mainConfig.dedicatedOutputChannel.getIdLong()) {
                                                mainConfig.dedicatedOutputChannel.sendMessage(entry.getTargetUser().getAsMention()).queue();
                                            }
                                            mainConfig.dedicatedOutputChannel.sendMessageEmbeds(messageQueue.get(0).getEmbed()).queue(m -> {
                                                placeInCmdMap(entry.setResultEmbed(m));
                                            }, error -> {
                                                log.error("sendAllMessages Dedicated Output Channel - Bot Spam None Handler: " + error.getMessage());
                                            });
                                        }

                                    }
                                }
                                break;
                            case CUSTOM:
                                entry.getCustomChannels().forEach(c -> {
                                    c.sendMessageEmbeds(entry.getEmbed()).queue(m -> {
                                        placeInCmdMap(entry.setResultEmbed(m));
                                    }, error -> {
                                        log.error("sendAllMessages CUSTOM Channel: #" + c.getName() + ": " + error.getMessage());
                                    });
                                });
                                break;
                            case SAME:
                                entry.getOriginalCmd().getChannel().sendMessageEmbeds(entry.getEmbed()).queue(m -> {
                                    placeInCmdMap(entry.setResultEmbed(m));
                                }, error -> {
                                    log.error("sendAllMessages SAME Channel : #" + entry.getOriginalCmd().getChannel().getName()+ ": " + error.getMessage());
                                });
                                break;
                        }
                    });
                    messageQueue.remove(entry);
                });
                if (messageQueue.isEmpty()) break;
            }
            catch (ConcurrentModificationException ex) {
                // Take No Action - We want the loop to go back around
            }
        }
    }
    private void placeInCmdMap(MessageEntry entry) {
        if (commandMessageMap.get(entry.getOriginalCmd()) != null) {
            commandMessageMap.remove(entry.getOriginalCmd());
        }
        commandMessageMap.put(entry.getOriginalCmd(), entry);
    }
}