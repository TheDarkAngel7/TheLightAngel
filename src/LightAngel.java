import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import javax.security.auth.login.LoginException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.concurrent.TimeoutException;

class LightAngel {
    static boolean success = false;

    public static void main(String[]args) throws LoginException, TimeoutException, IOException {
        JDA api = new JDABuilder(AccountType.BOT)
        .setToken("<TOKEN HERE>").build();
        try {
            api.addEventListener(new DiscordBotMain());
            success = true;
        }
        catch (IOException e) {
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream("data/data.dat"));
            System.out.println("[System] Creating Data File...");
            objectOutputStream.close();
        }
        api.setAutoReconnect(true);
        if (!success) {
            api.addEventListener(new DiscordBotMain());
        }
    }
}
