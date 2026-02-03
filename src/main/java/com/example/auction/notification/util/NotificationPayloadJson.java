package com.example.auction.notification.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Map;
public class NotificationPayloadJson {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() {};

    private NotificationPayloadJson() {}

    public static String toJson(Map<String, String> data) {
        if (data == null || data.isEmpty()) return null;
        try {
            return OM.writeValueAsString(data);
        } catch (Exception e) {
            return null;
        }
    }

    public static Map<String, String> fromJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return OM.readValue(json, TYPE);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
