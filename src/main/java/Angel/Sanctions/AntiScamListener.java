package Angel.Sanctions;

import Angel.EmbedDesign;
import Angel.MessageEntry;
import Angel.Sanctions.Database.BanInfo;
import Angel.Sanctions.Exceptions.InvalidExpirationDateException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AntiScamListener extends ListenerAdapter implements SanctionLogic {

    private final Logger log = LogManager.getLogger(AntiScamListener.class);

    private final ConcurrentHashMap<Long, AntiScamViolation> violations = new ConcurrentHashMap<>();

    private final ReentrantLock reentrantLock = new ReentrantLock();

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || isTeamMember(event.getAuthor().getIdLong()) || event.getChannelType() == ChannelType.PRIVATE) return;

        if (event.getMessage().getAttachments().size() >= sanctionConfig.getMinAttachments() ||
                countLinks(event.getMessage().getContentRaw()) >= sanctionConfig.getMinLinks()) {
            reentrantLock.lock();
            try {
                violations.entrySet().removeIf(entry -> entry.getValue().hasTimedOut());

                if (violations.containsKey(event.getAuthor().getIdLong()) && !violations.get(event.getAuthor().getIdLong()).hasTimedOut()) {
                    AntiScamViolation record = violations.get(event.getAuthor().getIdLong());

                    record.incrementViolationCount();

                    log.info("{} has triggered Anti-Scam Filter - Count: {}", event.getMember().getEffectiveName(), record.getViolationCount());

                    if (record.getViolationCount() == sanctionConfig.getAntiScamViolationsBanTrigger() - 1) {
                        String warningString = ":warning: **You have triggered my Anti-Scam Filter! " +
                                "Sending another message with # within the next $ seconds " +
                                "will result in your account getting banned for %!**";
                        try {
                            event.getMessage().reply(warningString.replace("#", (sanctionConfig.getMinAttachments() == sanctionConfig.getMinLinks() ?
                                                    sanctionConfig.getMinAttachments() + " or more attachments or links" : sanctionConfig.getMinAttachments() + " or more attachments, or " +
                                                    sanctionConfig.getMinLinks() + " or more links"))
                                            .replace("$", String.valueOf(sanctionConfig.getMaxAntiScamTimeoutSeconds()))
                                            .replace("%", formatDurationString(sanctionConfig.getAntiScamBanDuration())))
                                    .queue();
                        }
                        catch (InvalidExpirationDateException e) {
                            log.error(e);
                        }
                    }

                    else if (record.getViolationCount() >= sanctionConfig.getAntiScamViolationsBanTrigger()) {

                        if (!event.getMessage().getAttachments().isEmpty()) {
                            List<CompletableFuture<FileUpload>> downloadFutures = new ArrayList<>();

                            for (Message.Attachment attachment : event.getMessage().getAttachments()) {
                                CompletableFuture<FileUpload> download = attachment.getProxy().download()
                                        .thenApply(inputStream -> FileUpload.fromData(inputStream, attachment.getFileName()))
                                        .exceptionally(throwable -> {
                                            log.error("Failed downloading attachment: {}", attachment.getFileName(), throwable);
                                            return null;
                                        });
                                downloadFutures.add(download);
                            }

                            CompletableFuture.allOf(downloadFutures.toArray(new CompletableFuture[0]))
                                    .thenAccept(v -> {
                                        List<FileUpload> filesToUpLoad = new ArrayList<>();
                                        for (CompletableFuture<FileUpload> future : downloadFutures) {
                                            FileUpload file = future.join();
                                            if (file != null) {
                                                filesToUpLoad.add(file);
                                            }
                                        }
                                        logEvidenceToStaffChannel(event, filesToUpLoad);
                                        executeAntiScamBan(event);
                                    });
                        }
                        else {
                            logEvidenceToStaffChannel(event, new ArrayList<>());
                            executeAntiScamBan(event);
                        }
                    }
                }
                else {
                    violations.put(event.getAuthor().getIdLong(), new AntiScamViolation());
                    log.warn("{} has triggered the Anti-Scam Filter with {} files and {} links!",
                            event.getMember().getEffectiveName(), event.getMessage().getAttachments().size(), countLinks(event.getMessage().getContentRaw()));
                }
            }
            finally {
                reentrantLock.unlock();
            }
        }
    }

    private void executeAntiScamBan(MessageReceivedEvent event) {
        long targetDiscordID = event.getAuthor().getIdLong();

        try {
            databaseContainer.addSanction(new BanInfo(targetDiscordID, sanctionConfig.getAntiScamBanDuration(), "Compromised/Hacked Account"));
            violations.remove(targetDiscordID);
            databaseManager.saveDatabase();
        }
        catch (InvalidExpirationDateException e) {
            mainConfig.logChannel.sendMessageEmbeds(new MessageEntry("Anti-Scam Ban Failed", "**Unable to Ban " + event.getMember().getEffectiveName() + " due to invalid anti-scam ban configuration!", EmbedDesign.ERROR).getEmbed()).queue();
            log.error(e);
        }

    }

    private void logEvidenceToStaffChannel(MessageReceivedEvent event, List<FileUpload> files) {

        Message msg = event.getMessage();
        String content = msg.getContentRaw();

        // Fallback if they sent a blank message containing only attachments
        if (content.isEmpty()) {
            content = "*(No text content provided)*";
        }

        // Build a list of the filenames they attempted to upload
        String attachmentUrls = msg.getAttachments().stream()
                .map(Message.Attachment::getFileName)
                .collect(Collectors.joining("\n"));

        // Construct an organized evidence card
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🚨 Automated Anti-Scam Ban")
                .setColor(Color.RED)
                .addField("Target User", event.getAuthor().getAsMention() + " (" + event.getAuthor().getId() + ")", false)
                .addField("Previous Channel", event.getChannel().getAsMention(), true)
                .addField("Attachment Count", String.valueOf(msg.getAttachments().size()), true)
                .addField("Message Content", content.length() > 1024 ? content.substring(0, 1021) + "..." : content, false)
                .setTimestamp(ZonedDateTime.now());

        if (!attachmentUrls.isEmpty()) {
            embed = embed.addField("Preserved Attachments", attachmentUrls, false);
        }

        // Ship it off to the private channel
        getTeamBureauChannel().sendMessageEmbeds(embed.build()).queue();
        getTeamBureauChannel().sendMessage("See Attachments:").setFiles(files).queue();

    }
    private int countLinks(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return 0;
        }

        Matcher matcher = Pattern.compile("https?://", Pattern.CASE_INSENSITIVE).matcher(rawText);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
