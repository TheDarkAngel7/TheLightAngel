package Angel.Sanctions;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class SanctionMain extends ListenerAdapter implements SanctionLogic {
    private final Logger log = LogManager.getLogger(SanctionMain.class);

    @Override
    public void onReady(@NotNull ReadyEvent event) {

    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        // For Future Expansion
    }

    public void reload() {
        sanctionConfigManager.reloadConfig();
    }
}
