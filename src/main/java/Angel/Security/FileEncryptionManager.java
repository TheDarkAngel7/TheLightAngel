package Angel.Security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;

public class FileEncryptionManager {
    private static final Logger log = LogManager.getLogger(FileEncryptionManager.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    public static void writeEncryptedFile(File file, String data, String callerClass) {
        try {
            log.info("[{}]: Attempting to Write Encrypted File", callerClass);

            byte[] iv = new byte[IV_LENGTH_BYTE];
            SecretKey key = getSecretKey();
            SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            byte[] cipherText = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            Files.write(file.toPath(), byteBuffer.array());

            log.info("[{}]: Successfully Wrote Encrypted File", callerClass);
        }
        catch (Exception e) {
            log.fatal("[{}]: Error while writing encrypted file", callerClass, e);
        }

    }

    public static String readEncryptedFile(File file, String callerClass) {
        try {
            log.info("[{}]: Attempting to Read Encrypted File", callerClass);

            byte[] fileData = Files.readAllBytes(file.toPath());
            SecretKey key = getSecretKey();

            ByteBuffer byteBuffer = ByteBuffer.wrap(fileData);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            log.info("[{}]: Successfully Read Encrypted File", callerClass);
            return new String(plainText, StandardCharsets.UTF_8);
        }
        catch (AEADBadTagException | BufferUnderflowException e) {
            log.warn("[{}]: File is not encrypted (legacy format detected). Reading as plain text...", callerClass);
            try {
                String unencryptedText = Files.readString(file.toPath());
                writeEncryptedFile(file, unencryptedText, callerClass);
                return unencryptedText;
            }
            catch (IOException ioEx) {
                log.fatal("[{}]: Failed to read legacy file fallback", callerClass, ioEx);
                return "";
            }
        }
        catch (Exception e) {
            log.fatal("[{}]: Error while reading encrypted file", callerClass, e);
            return "";
        }
    }

    private static SecretKey getSecretKey() {
        String encodedKey = System.getenv("ANGEL_ENCRYPTION_KEY");

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalStateException("ANGEL_ENCRYPTION_KEY environment variable is not set.");
        }

        byte[] decodedKey = Base64.getDecoder().decode(encodedKey);

        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }
}
