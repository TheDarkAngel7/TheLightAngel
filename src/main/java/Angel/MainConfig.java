package Angel;

import net.dv8tion.jda.api.entities.Guild;

public interface MainConfig {
    MainConfiguration mainConfig = new ModifyMainConfiguration(new FileHandler().getMainConfig());
    AngelExceptionHandler aue = new AngelExceptionHandler();

    default Guild getGuild() {
        return mainConfig.guild;
    }
}
