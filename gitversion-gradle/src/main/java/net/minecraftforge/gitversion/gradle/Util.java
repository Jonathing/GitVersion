/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gitversion.gradle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.ToNumberPolicy;
import com.google.gson.stream.JsonReader;
import net.minecraftforge.gradleutils.shared.SharedUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

final class Util extends SharedUtil {
    private static final Gson GSON = new GsonBuilder()
        .setObjectToNumberStrategy(Util::readNumber)
        .setPrettyPrinting()
        .create();

    private static Number readNumber(JsonReader in) throws IOException {
        try {
            return ToNumberPolicy.LONG_OR_DOUBLE.readNumber(in);
        } catch (Throwable suppressed) {
            try {
                return ToNumberPolicy.BIG_DECIMAL.readNumber(in);
            } catch (Throwable e) {
                IOException throwing = new IOException("Failed to read number from " + in, e);
                throwing.addSuppressed(suppressed);
                throw throwing;
            }
        }
    }

    public static <T> T fromJson(File file, Class<T> classOfT) throws JsonSyntaxException, JsonIOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            return fromJson(stream, classOfT);
        } catch (IOException e) {
            throw new JsonIOException(e);
        }
    }

    public static <T> T fromJson(byte[] data, Class<T> classOfT) throws JsonSyntaxException, JsonIOException {
        return fromJson(new ByteArrayInputStream(data), classOfT);
    }

    public static <T> T fromJson(InputStream stream, Class<T> classOfT) throws JsonSyntaxException, JsonIOException {
        return GSON.fromJson(new InputStreamReader(stream), classOfT);
    }

    public static <T> T fromJson(String data, Class<T> classOfT) throws JsonSyntaxException, JsonIOException {
        return GSON.fromJson(data, classOfT);
    }
}
