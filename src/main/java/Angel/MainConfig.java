package Angel;

public interface MainConfig {
    MainConfiguration mainConfig = new ModifyMainConfiguration(new FileHandler().getMainConfig());
}
