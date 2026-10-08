package com.sumutiu.easyeconomy.storage;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.stream.MalformedJsonException;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static com.sumutiu.easyeconomy.util.EasyEconomyMessages.*;

public class StorageUtil {

    /**
     * Writes JSON to a temp file first and then moves it over the real file,
     * so a crash mid-write can't leave a half-written (corrupt) file behind.
     */
    public static void writeJsonAtomically(File file, Object data, Gson gson) throws IOException {
        File tmpFile = new File(file.getParentFile(), file.getName() + ".tmp");

        // Serialize to a String first: Gson wraps write errors in an unchecked JsonIOException,
        // which a plain catch (IOException) would miss
        String json = gson.toJson(data);
        Files.writeString(tmpFile.toPath(), json, StandardCharsets.UTF_8);

        try {
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Gson reports I/O errors while reading as a JsonSyntaxException too.
     * Only bad or cut-off JSON means the file itself is corrupt; any other
     * I/O error may be temporary, so the file must be left alone.
     */
    public static boolean isCorruptFileError(JsonParseException e) {
        Throwable cause = e.getCause();
        return cause instanceof IOException
                && !(cause instanceof MalformedJsonException)
                && !(cause instanceof EOFException);
    }

    /**
     * Moves a file that could not be parsed out of the way, so its data can still be
     * recovered by hand and the mod does not overwrite it on the next save.
     */
    // Returns false if the file could not be moved; it must then be treated as unreadable
    public static boolean quarantineCorruptFile(File file) {
        File backup = new File(file.getParentFile(), file.getName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Logger(1, String.format(FILE_CORRUPT_BACKUP, file.getPath(), backup.getName()));
            return true;
        } catch (IOException e) {
            Logger(2, String.format(FILE_CORRUPT_BACKUP_FAILED, file.getPath(), e.getMessage()));
            return false;
        }
    }
}
