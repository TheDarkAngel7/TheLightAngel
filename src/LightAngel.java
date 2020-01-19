import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import javax.security.auth.login.LoginException;

class LightAngel {

    public static void main(String[]args) throws LoginException {
        JDA api = new JDABuilder(AccountType.BOT)
        .setToken("<Token Here>").build();
        api.addEventListener(new DiscordBotMain());
        api.setAutoReconnect(true);
    }

}
