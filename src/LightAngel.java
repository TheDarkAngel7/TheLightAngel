import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.concurrent.TimeoutException;

class LightAngel {
    static DiscordBotMain discord;
    static File dataFile = new File("data/data.json");
    static {
        System.out.println("[System] TheLightAngel is Starting! Please Wait...");
        try {
            if (!dataFile.exists()) {
                if (dataFile.createNewFile()) {
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(dataFile));
                    System.out.println("[System] Successfully Created new Data File");
                    objectOutputStream.close();
                }
            }
            else {
                System.out.println("[System] Successfully Found Existing Data File");
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) throws LoginException, IOException, TimeoutException {
        boolean isRestart = Boolean.parseBoolean(args[0]);
        discord = new DiscordBotMain(isRestart);
        JDA api = new JDABuilder(AccountType.BOT).setToken(discord.core.config.token).build();
        api.addEventListener(discord);
        api.setAutoReconnect(true);
    }
}