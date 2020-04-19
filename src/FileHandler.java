import java.io.*;
import java.util.ArrayList;
import java.util.Date;

class FileHandler {
    private File dataFile = new File("data/data.dat");
    private File tempDataFile = new File("data/datatemp.dat");
    private FileInputStream inputFile = new FileInputStream(dataFile);
    private ObjectInputStream inputStream = new ObjectInputStream(inputFile);

    FileHandler() throws IOException {
    }

    ArrayList<Long> getDiscordIDs() throws Exception {
        return (ArrayList<Long>) inputStream.readObject();
    }
    ArrayList<Integer> getRepOffenses() throws Exception {
        return (ArrayList<Integer>) inputStream.readObject();
    }
    ArrayList<Date> getIssuedDates() throws Exception {
        return (ArrayList<Date>) inputStream.readObject();
    }
    ArrayList<Date> getExpiryDates() throws Exception {
        return (ArrayList<Date>) inputStream.readObject();
    }
    ArrayList<String> getReasons() throws Exception {
        return (ArrayList<String>) inputStream.readObject();
    }
    ArrayList<String> getProofImages() throws Exception {
        return (ArrayList<String>) inputStream.readObject();
    }
    ArrayList<Long> getCurrentBotAbusers() throws Exception {
        return (ArrayList<Long>) inputStream.readObject();
    }

    void writeArrayData()
            throws Exception {
        this.inputStream.close();
        this.inputFile.close();
        // Open Output Stream
        ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(tempDataFile));
        // Write the Objects
        outputStream.writeObject(super.discordID);
        outputStream.writeObject(super.repOffenses);
        outputStream.writeObject(super.issuedDates);
        outputStream.writeObject(super.expiryDates);
        outputStream.writeObject(super.reasons);
        outputStream.writeObject(super.proofImages);
        outputStream.writeObject(super.currentBotAbusers);

        // Close
        outputStream.flush();
        outputStream.close();
        if (dataFile.delete()) {
            System.out.println("[System] Successfully Deleted Original File");
        }
        else {
            while (dataFile.exists()) {
                System.out.println("Result of Deletion: " + dataFile.delete());
            }
        }

        // Rename the file
        if (tempDataFile.renameTo(dataFile)) {
            System.out.println("[System] Successfully Renamed Temp File to Original File");
        }
        else {
            System.out.println("[System] Couldn't Rename Temp File");
        }

    }
}