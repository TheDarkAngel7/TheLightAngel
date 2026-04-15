package Angel;

import net.dv8tion.jda.api.entities.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class TransferHub implements TransferableRecords {
    private final Logger log = LogManager.getLogger(TransferHub.class);
    private final List<TransferableRecords> listeners = new ArrayList<>();

    // This method takes ANY class that 'implements TransferableRecords'
    public void register(TransferableRecords system) {
        listeners.add(system);
        log.info("Class Successfully Registered for Transferable Records: {}" , system.getClass().getSimpleName());
    }

    @Override
    public void executeTransfer(Message originalCmd, long oldAccountID, long newAccountID) {

        // We treat every 'system' in the list as a Transferable
        for  (TransferableRecords system : listeners) {
            log.info("Executing Transfer for: {}", system.getClass().getSimpleName());
            system.executeTransfer(originalCmd, oldAccountID, newAccountID);

        }
    }
}
