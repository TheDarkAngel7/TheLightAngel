import java.io.*;
import java.util.ArrayList;
import java.util.Date;

class FileHandler extends Core {
    private File dataFile = new File("data/data.dat");
    private File tempDataFile = new File("data/datatemp.dat");
    private ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(dataFile));

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
        // Close Input if it hasn't been closed already
        inputStream.close();
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
        // Delete Original File
        System.gc();
        if (dataFile.delete()) {
            System.out.println("[System] Successfully Deleted Original File!");
        } else {
            System.out.println("[System] Couldn't Delete Original File! Please Restart the Program and Try Again");
            while (!dataFile.delete()) {
                System.out.println("[System] Trying Again");
                inputStream.close();
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