package com.wayward;

import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Session;

public final class Wayward {

    public static final String MOD_ID = "wayward";

    public static String url = "http://localhost:8080";
    public static String token = "";

    public static void init() {
        auth();
    }

    public static void auth() {
        MinecraftClient client = MinecraftClient.getInstance();
        Session session = client.getSession();
        String serverId = UUID.randomUUID().toString();
        try {
            client.getSessionService().joinServer(session.getProfile(), session.getAccessToken(), serverId);
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                    .uri(URI.create(String.format("%s/auth?username=%s&serverId=%s", url, session.getUsername(), serverId)))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() != 200) return;
            token = JsonParser.parseString(response.body()).getAsJsonObject().get("token").getAsString();
        } catch (Exception e) {}
    }
}
