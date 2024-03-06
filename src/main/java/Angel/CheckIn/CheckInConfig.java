package Angel.CheckIn;

import Angel.CommonLogic;

public interface CheckInConfig extends CommonLogic {
    CheckInConfiguration ciConfig = new ModifyCheckInConfiguration(new FileHandler().getConfig());
}
