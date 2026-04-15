package Angel;

import net.dv8tion.jda.api.entities.Message;

public interface TransferableRecords {
    void executeTransfer(Message originalCmd, long oldAccountID, long newAccountID);
}
