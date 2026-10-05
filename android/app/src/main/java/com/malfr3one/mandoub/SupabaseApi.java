package com.malfr3one.mandoub;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SupabaseApi {
    public static final String BASE_URL = "https://cvfkzqbpuwsvggsqrusp.supabase.co";
    public static final String API_KEY = "sb_publishable_5VGLcKYmatISHVBnrfCZxA_RuWFKuwL";

    public static class AuthResult {
        public String accessToken;
        public String refreshToken;
        public String userId;
        public String email;
        public boolean needsConfirmation;
    }

    public static class ApiException extends Exception {
        public final int status;
        public ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    public AuthResult signIn(String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email.trim());
        body.put("password", password);
        JSONObject json = requestJson("POST", "/auth/v1/token?grant_type=password", body, null, null);
        return parseAuth(json, false);
    }

    public AuthResult signUp(String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email.trim());
        body.put("password", password);
        JSONObject json = requestJson("POST", "/auth/v1/signup", body, null, null);
        return parseAuth(json, !json.has("access_token") || json.optString("access_token").isEmpty());
    }

    public AuthResult refresh(String refreshToken) throws Exception {
        JSONObject body = new JSONObject();
        body.put("refresh_token", refreshToken);
        JSONObject json = requestJson("POST", "/auth/v1/token?grant_type=refresh_token", body, null, null);
        return parseAuth(json, false);
    }

    private AuthResult parseAuth(JSONObject json, boolean needsConfirmation) {
        AuthResult result = new AuthResult();
        result.accessToken = json.optString("access_token", "");
        result.refreshToken = json.optString("refresh_token", "");
        JSONObject user = json.optJSONObject("user");
        if (user != null) {
            result.userId = user.optString("id", "");
            result.email = user.optString("email", "");
        }
        result.needsConfirmation = needsConfirmation;
        return result;
    }

    public boolean claimAdmin(String accessToken, String code) throws Exception {
        JSONObject body = new JSONObject();
        body.put("p_code", code.trim());
        String text = request("POST", "/rest/v1/rpc/claim_admin", body.toString(), accessToken, null);
        return "true".equalsIgnoreCase(text.trim());
    }

    public JSONArray getRows(String accessToken, String path) throws Exception {
        String text = request("GET", "/rest/v1/" + path, null, accessToken, null);
        return new JSONArray(text);
    }

    public JSONObject insertRow(String accessToken, String table, JSONObject body) throws Exception {
        String text = request("POST", "/rest/v1/" + table, body.toString(), accessToken, "return=representation");
        JSONArray array = new JSONArray(text);
        return array.length() > 0 ? array.getJSONObject(0) : new JSONObject();
    }

    public JSONArray updateRows(String accessToken, String tableAndFilter, JSONObject body) throws Exception {
        String text = request("PATCH", "/rest/v1/" + tableAndFilter, body.toString(), accessToken, "return=representation");
        return new JSONArray(text);
    }

    private JSONObject requestJson(String method, String path, JSONObject body, String accessToken, String prefer) throws Exception {
        return new JSONObject(request(method, path, body == null ? null : body.toString(), accessToken, prefer));
    }

    private String request(String method, String path, String body, String accessToken, String prefer) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("apikey", API_KEY);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
        if (accessToken != null && !accessToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        }
        if (prefer != null) connection.setRequestProperty("Prefer", prefer);

        if (body != null) {
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String text = readAll(stream);
        connection.disconnect();

        if (status < 200 || status >= 300) {
            String message = text;
            try {
                JSONObject error = new JSONObject(text);
                message = error.optString("msg", error.optString("message", error.optString("error_description", error.optString("error", text))));
            } catch (Exception ignored) {}
            throw new ApiException(status, message);
        }
        return text == null || text.isEmpty() ? "{}" : text;
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }
}