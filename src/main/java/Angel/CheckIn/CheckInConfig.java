package Angel.CheckIn;

import Angel.MainConfig;

public interface CheckInConfig extends MainConfig {
    CheckInConfiguration ciConfig = new ModifyCheckInConfiguration(new FileHandler().getConfig());
}
