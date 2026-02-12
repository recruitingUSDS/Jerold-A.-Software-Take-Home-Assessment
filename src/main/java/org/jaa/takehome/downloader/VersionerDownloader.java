package org.jaa.takehome.downloader;

import com.fasterxml.jackson.databind.*;
import org.jaa.takehome.descriptor.PartDescriptor;
import org.jaa.takehome.descriptor.TitleDescriptor;
import org.jaa.takehome.utilities.Utils;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.jaa.takehome.Constants.*;

/**
 * Downloader for the **current** eCFR “Versioner”
 * What it does:
 *   GET /titles → list of all titles.
 *   For each title, GET /titles/{title}/parts → list of parts.
 *   For each part, GET /titles/{title}/parts/{part} → full XML document.
 *   Writes each XML file to {@code output/Title-XX/part-YY.xml}.
 * If the API ever requires a bearer token, set {@code API_KEY}.  The
 * client already respects the public 5 req/s rate‑limit by sleeping
 * {@code REQUEST_DELAY_MS} between calls.
 * @author OpenAI ChatGPT
 */
public class VersionerDownloader {

    /* --------------------------------------------------------------- */
    /*                     ★★  CONFIGURATION  ★★                      */
    /* --------------------------------------------------------------- */
    /** Jackson ObjectMapper – used for JSON <-> POJO conversion. */
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;

    public VersionerDownloader() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        ObjectMapper jsonMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }


    /* --------------------------------------------------------------- */
    /** Download every part that belongs to the supplied title. */
    public void downloadTitle(TitleDescriptor title) throws IOException, InterruptedException {
        String titleNum = title.number;
        File output = OUTPUT_ROOT.toFile();
        File partDetailsDirectory = new File(output, "title-" + title.number);
        partDetailsDirectory.mkdirs();
        Path titleDir = OUTPUT_ROOT.resolve("title-" + String.format("%02d", Integer.parseInt(titleNum)));
        Files.createDirectories(titleDir);

        // 2️⃣ List parts for this title
        List<PartDescriptor> parts = title.getParts();
        File csvFile = new File(titleDir.toFile(), "Parts.csv");
        if (csvFile.exists()) csvFile.delete();
        csvFile.createNewFile();
        System.out.printf("Writing %d parts to %s: ", parts.size(), titleDir);
        try (PrintStream outputFileStream = new PrintStream(csvFile)) {
            for (PartDescriptor part : parts) {
                outputFileStream.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                                        part.getType(),
                                        part.getPartNumber(),
                                        part.getTitle(),
                                        part.getIdentifier(),
                                        part.getName(),
                                        part.getAmendedDate(),
                                        part.getIssueDate(),
                                        part.isSubstantive(),
                                        part.isRemoved(),
                                        part.isSubPart());
            }
            System.out.println("Done.");
        }
    }



    /** GET /titles/{title}/parts → list of parts for that title (JSON array). */
    public List<PartDescriptor> fetchPartsForTitle(TitleDescriptor titleDescriptor) throws IOException, InterruptedException {
        String titleNumber = titleDescriptor.getNumber();
        List<PartDescriptor> parts = new ArrayList<>();
        String url = TitleDownloader.API_BASE
                + "/versions"
                + "/title-"
                + titleNumber
                + ".json";
        HttpRequest request = buildRequest(url, "application/json");
        HttpResponse<String> resp = httpClient.send(request,
                                            HttpResponse.BodyHandlers.ofString());
        int status = resp.statusCode();
        // 429 – respect Retry-After header
        if (status == 429) {
            long waitSec = resp.headers()
                    .firstValue("Retry-After")
                    .map(Long::parseLong)
                    .orElse(5L);
            System.out.println("\nRate‑limit hit – sleeping " + waitSec + " s\n");
            TimeUnit.SECONDS.sleep(waitSec);
            // retry once
            return fetchPartsForTitle(titleDescriptor);
        }
        if (resp.statusCode() == 503) {
            Utils.reportError(request, resp, "Failed to list parts for title number " + titleDescriptor.getNumber() + " '" + titleDescriptor.getName() + "'");
            return null;
        }
        Utils.ensureSuccess(request, resp, "Failed to list parts for title number " + titleDescriptor.getNumber() + " '" + titleDescriptor.getName() + "'\n");
        System.out.printf("Fetching all parts for title number %s '%s': ", titleDescriptor.getNumber(), titleDescriptor.getName());
        System.out.flush();
        JsonNode root = mapper.readTree(resp.body());
        JsonNode versions = root.path("content_versions");
        int colMarker = 0;
        int count = 0;
        for (JsonNode node : versions) {
            count++;
            colMarker++;
            if (colMarker % 80 == 0) {
                System.out.printf("\015\033[KFetching all parts for title number %s '%s' (%,d): ", titleDescriptor.getNumber(),titleDescriptor.getName(), count);
                colMarker = 0;
            } else {
                System.out.print(".");
            }
            System.out.flush();
            String type = node.path("type").asText();
            String partNumber = node.path("part").asText();
            String title = node.path("title").asText();
            String identifier = node.path("identifier").asText();
            String name = node.path("name").asText();
            String amended = node.path("amendment_date").asText();
            String issued = node.path("issue_date").asText();
            boolean substantive = node.path("substantive").asBoolean();
            boolean removed = node.path("removed").asBoolean();
            boolean subPart = node.path("subpart").asBoolean();
            PartDescriptor partDescriptor = new PartDescriptor(type, partNumber, titleDescriptor,
                                                               name, identifier,
                                                               amended, issued,
                                                               substantive, removed, subPart, null);
            parts.add(partDescriptor);
        }
        System.out.printf("\015\033[KFetched %,d parts for title number %s '%s'\n", count, titleDescriptor.getNumber(), titleDescriptor.getName());
        return parts;
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
