package org.jaa.takehome.utilities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Utils {

    private static final ObjectMapper mapper = new ObjectMapper();
    public static void reportError(HttpRequest request, HttpResponse<String> resp, String message)  throws IOException {
        System.out.println(message + ": " + "Response Code: " + resp.statusCode()  + "\n" + request.toString() + "\n\t=> " + request);
        try {
            JsonNode root = mapper.readTree(resp.body());
            System.out.println(root.toPrettyString());
        } catch (JsonProcessingException e) {
            System.out.printf("Error Reported, invalid JSON reply: '%s'\n", resp.body());
        }

    }
    public static void ensureSuccess(HttpRequest request, HttpResponse<String> resp, String message)  throws IOException {
        if (resp.statusCode() != 200) {
            throw new IOException(message + ": " + request.toString() + ": " + resp.statusCode() + " => " + request.method() + " " + request.uri());
        }
    }
}
