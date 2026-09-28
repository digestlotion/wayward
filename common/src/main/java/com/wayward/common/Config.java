package com.wayward.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONF = Wayward.getConf();

    public static String url = "http://localhost:8080";
    public static String token = "";
    public static Map<String, String> worlds = new HashMap<>();     // TODO: world removing

    public static void init() {
        try {
            if (CONF.exists()) load();
            else save();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        JsonObject data = new JsonObject();
        try (FileWriter writer = new FileWriter(CONF)) {
            for (Field f : Config.class.getFields()) {
                data.add(f.getName(), GSON.toJsonTree(f.get(null)));
            }
            GSON.toJson(data, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        JsonObject data;
        try (FileReader reader = new FileReader(CONF)) {
            data = GSON.fromJson(reader, JsonObject.class);
            for (Field f : Config.class.getFields()) {
                f.set(null, GSON.fromJson(data.get(f.getName()), f.getType()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
