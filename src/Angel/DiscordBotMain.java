package Angel;

import Angel.BotAbuse.BotAbuseMain;
import Angel.Nicknames.NicknameMain;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class DiscordBotMain extends ListenerAdapter {
    MainConfiguration mainConfig;
    EmbedHandler embed;
    Guild guild;
    private NicknameMain nickFeature;
    private BotAbuseMain baFeature;
    private boolean isRestart;
    private final Logger log = LogManager.getLogger(DiscordBotMain.class);
    private boolean commandsSuspended = false;

    DiscordBotMain(boolean isRestart, MainConfiguration mainConfig, EmbedHandler embed) {
        this.mainConfig = mainConfig;
        this.embed = embed;
        this.isRestart = isRestart;
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        JDA jda = event.getJDA();
        guild = jda.getGuilds().get(0);
        mainConfig.guild = this.guild;
        if (!mainConfig.discordGuildConfigurationsExist()) {
            log.fatal("One or More of the Discord Configurations Don't Exist - Commands have been suspended in all features");
            commandsSuspended = true;
        }
        else mainConfig.discordSetup();

        try {
            nickFeature = new NicknameMain(commandsSuspended, mainConfig, embed, guild, this);
            baFeature = new BotAbuseMain(commandsSuspended, isRestart, mainConfig, embed, guild, this);
            log.info("All Features Successfully Initalized");
        }
        catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
        jda.addEventListener(nickFeature, baFeature);
        log.info("All Features Successfully Added as Event Listeners");
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getMessage().getChannelType() == ChannelType.PRIVATE) return;
        Message msg = event.getMessage();
        String[] args = event.getMessage().getContentRaw().substring(1).split(" ");

        if (msg.getMentionedMembers().contains(guild.getSelfMember())) {
            msg.getChannel().sendMessage(":blobnomping:").queue();
        }
        else if ((msg.getContentRaw().charAt(0) == mainConfig.commandPrefix && args[0].equalsIgnoreCase("restart"))
                && (isStaffMember(event.getAuthor().getIdLong()) || event.getAuthor() == mainConfig.owner)) {
            msg.delete().complete();
            try {
                embed.setAsWarning("Restart Initiated", "**Restart Initiated by " + msg.getMember().getAsMention()
                        + "\nPlease Allow up to 10 seconds for this to complete**");
                log.warn(msg.getMember().getEffectiveName() + " Invoked a Restart");
                embed.sendToTeamDiscussionChannel(msg.getChannel(), msg.getMember());
                Thread.sleep(5000);
                restartBot();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if ((msg.getContentRaw().charAt(0) == mainConfig.commandPrefix && args[0].equalsIgnoreCase("reload"))
                && (isStaffMember(event.getAuthor().getIdLong()) || event.getMember() == mainConfig.owner)) {
            embed.setAsWarning("Reloading Configuration", "**Reloading Configuration... Please Wait a Few Moments...**");
            embed.sendToTeamDiscussionChannel(msg.getChannel(), null);
            baFeature.reload(msg);
            nickFeature.reloadConfig(msg);
        }
    }

    private void restartBot() throws IOException {
        log.warn("Program Restarting...");
        new ProcessBuilder().command("cmd.exe", "/c", "start", mainConfig.systemPath + "\\restart.bat").start();
        System.exit(1);
    }

    public boolean isTeamMember(long targetDiscordID) {
        return guild.getMemberById(targetDiscordID).getRoles().contains(mainConfig.teamRole);
    }
    public boolean isStaffMember(long targetDiscordID) {
        return guild.getMemberById(targetDiscordID).getRoles().contains(mainConfig.staffRole) ||
                guild.getMemberById(targetDiscordID).getRoles().contains(mainConfig.adminRole);
    }
}