package io.github.overlordsiii.util;

import io.github.overlordsiii.Main;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import static io.github.overlordsiii.Main.*;

public class Util {
    public static void initConfigs() {
        try {
            if (!Files.exists(CONFIG_HOME_DIRECTORY)) {
                Files.createDirectory(CONFIG_HOME_DIRECTORY);
            } if (!Files.exists(TOKENS_DIRECTORY_PATH)) {
                Files.createDirectory(TOKENS_DIRECTORY_PATH);
            }
        } catch (IOException e) {
            Main.LOGGER.error("Unable to create config/token directory at: \"" + CONFIG_HOME_DIRECTORY + "\" or \"" + TOKENS_DIRECTORY_PATH + "\"", e);
            e.printStackTrace();
        }
        if (!Files.exists(CREDENTIALS_FILE_PATH)) {
            throw new IllegalArgumentException("Credentials file at: \"" + CREDENTIALS_FILE_PATH + "\" not found!");
        }
    }

    public static <T extends Enum<T>> T parseEnumSafe(String str, Class<T> clazz) {
        try {
            return Enum.valueOf(clazz, str);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Error while parsing enum constants, please make sure you pass one of the following: " + Arrays.deepToString(clazz.getEnumConstants()));
            throw new IllegalArgumentException("Illegal enum constant passed - please check logs for correct constants!");
        }
    }
}
