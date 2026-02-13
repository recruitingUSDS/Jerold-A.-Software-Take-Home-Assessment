package org.jaa.takehome.downloader;




import com.fasterxml.jackson.databind.*;
import org.jaa.takehome.descriptor.AgencyDescriptor;
import org.jaa.takehome.descriptor.ChapterDescriptor;
import org.jaa.takehome.descriptor.PartDescriptor;
import org.jaa.takehome.descriptor.TitleDescriptor;
import org.jaa.takehome.utilities.Utils;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.jaa.takehome.Constants.*;

public class ChapterLocator {

    /* --------------------------------------------------------------- */
    /*                     ★★  CONFIGURATION  ★★                      */
    /* --------------------------------------------------------------- */
    /** Jackson ObjectMapper – used for JSON <-> POJO conversion. */
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;

    public ChapterLocator() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public ChapterDescriptor findChapter(TitleDescriptor titleDescriptor, AgencyDescriptor agencyDescriptor, String chapter) throws IOException, InterruptedException {
        ChapterDescriptor chapterDescriptor = null;
        String date = titleDescriptor.getLatestIssueDate();
        String title = titleDescriptor.getNumber();
        System.out.printf("Attempting to locate chapter \033[32m%s\033[0m, for Agency \033[34m%s\033[0mfor title \033[33m%s\033[0m on date \033[34m%s\033[0m",
                          chapter, agencyDescriptor.getName(), title, date);
        String url = ENDPOINT_ANCESTRY_PARTS
                + "/"
                + date
                + "/"
                + "title-"
                + title
                + ".json"
                + "?chapter="
                + chapter;
        HttpRequest request = buildRequest(url, "application/json");
        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = resp.statusCode();
        Utils.ensureSuccess(request, resp, "Failed to locate chapter " + chapter + "for title " + titleDescriptor.getName());
        JsonNode root = mapper.readTree(resp.body());
        JsonNode ancestors = root.get("ancestors");
        for (JsonNode node : ancestors) {
            String type = node.get("type").asText();
            if (type.equalsIgnoreCase("chapter")) {
                String label = node.get("label").asText();
                chapterDescriptor = new ChapterDescriptor(chapter, titleDescriptor, agencyDescriptor);
                System.out.printf("\015\033[KFound Chapter \033[32m%s\033[0m Named \033[35m%s\033[0m for title \033[33m%s\033[0m\n", chapter, label, title);
                return chapterDescriptor;
            }
        }
        System.out.printf("\015\033[K\033[31mUnable to locate chapter %s, for title %s on date %s\n", chapter, title, date);
        return chapterDescriptor;
    }

    /* --------------------------------------------------------------- */
    /** Helper that adds the optional Authorization header and the Accept header. */
    private HttpRequest buildRequest(String url, String acceptHeader) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", acceptHeader)
                .GET();
        return builder.build();
    }
}
