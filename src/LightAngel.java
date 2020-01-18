import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import javax.security.auth.login.LoginException;

class LightAngel {

    public static void main(String[]args) throws LoginException {
        JDA api = new JDABuilder(AccountType.BOT)
        .setToken("NjY0NTIwMzUyMzE1MDgwNzI3.XhYW-Q.R7foZHQSNKV8dglJZytdqNXcdIw").build();
        api.addEventListener(new DiscordBotMain());
        api.setAutoReconnect(true);
    }

}
