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
 */
public class VersionerDownloader {

    /* --------------------------------------------------------------- */
    /*                     ★★  CONFIGURATION  ★★                      */
    /* --------------------------------------------------------------- */
    /** Jackson ObjectMapper – used for JSON <-> POJO conversion. */
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;

    /**
     * constructor
     */
    public VersionerDownloader() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        ObjectMapper jsonMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }


    /* --------------------------------------------------------------- */

    /** Download every part that belongs to the supplied title.
     * Generates CSV file for full cross referencing to parts/titles.
     * @param title
     * @throws IOException
     * @throws InterruptedException
     */
    public void crossReferenceAllPartsForTitle(TitleDescriptor title) throws IOException, InterruptedException {
        String titleNum = title.number;
        File output = OUTPUT_ROOT.toFile();
        Path partDetailsPath = Paths.get(OUTPUT_ROOT.toString(), "AllTitles");
        Path titleDir = Paths.get(partDetailsPath.toString(), String.format("title-%02d", Integer.parseInt(titleNum)));
        Files.createDirectories(titleDir);
        Path labelPath = Paths.get(titleDir.toString(), Utils.slugify(title.getName()) + ".txt");
        if (Files.exists(labelPath)) {
            Files.delete(labelPath);
        }
        Files.createFile(labelPath);


        // 2️⃣ List parts for this title
        List<PartDescriptor> parts = title.getParts();
        File csvFile = new File(titleDir.toFile(), "parts.csv");
        if (csvFile.exists()) csvFile.delete();
        Path csvPath = csvFile.toPath().toAbsolutePath().normalize();
        Path relativePartPath = currentWorkingDirectoryPath.relativize(csvPath);
        csvFile.createNewFile();
        try (PrintStream outputFileStream = new PrintStream(csvFile)) {
            for (PartDescriptor part : parts) {
                outputFileStream.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                                        part.getType(),
                                        part.getPartNumber(),
                                        part.getTitleName(),
                                        part.getIdentifier(),
                                        part.getName(),
                                        part.getAmendedDate(),
                                        part.getIssueDate(),
                                        part.isSubstantive(),
                                        part.isRemoved(),
                                        part.isSubPart());
            }
            System.out.printf("\tWrote %,7d parts to .../%s\n", parts.size(), relativePartPath);
        }
    }


    /** GET List of parts for supplied title
     * Retrieves full list of parts for provided title
     * @param titleDescriptor retrieved from other calls
     * @return list of parts for given title
     * @throws IOException either http or file i/o errors
     * @throws InterruptedException unlikely
     */
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
            parts.addAll(fetchPartsForTitle(titleDescriptor));
        }
        if (resp.statusCode() != 200) {
            Utils.reportError(request, resp, "Failed to list parts for title number " + titleDescriptor.getNumber() + " '" + titleDescriptor.getName() + "'");
            return null;
        }
        Utils.ensureSuccess(request, resp, "Failed to list parts for title number " + titleDescriptor.getNumber() + " '" + titleDescriptor.getName() + "'\n");
        System.out.printf("Fetching all parts for title number %2.2s: %s: ", titleDescriptor.getNumber(), titleDescriptor.getName());
        System.out.flush();
        JsonNode root = mapper.readTree(resp.body());
        JsonNode versions = root.path("content_versions");
        int colMarker = 0;
        int count = 0;
        for (JsonNode node : versions) {
            count++;
            colMarker++;
            if (colMarker % 80 == 0) {
                System.out.printf("\015\033[KFetched \033[32m%,7d\033[0m parts so far for title number %2.2s: \33[33m%41.41s\033[0m: ",
                                  count, titleDescriptor.getNumber(),titleDescriptor.getName());
                colMarker = 0;
            } else {
                System.out.print(".");
            }
            System.out.flush();
            String type = node.path("type").asText();
            String partNumber = node.path("part").asText();
            String title = node.path("title").asText();
            if (!title.trim().equalsIgnoreCase(titleDescriptor.getNumber())) {
                System.out.printf("Unexpected title cross reference, expecting '%s', but found '%s'\n",
                                  titleDescriptor.getNumber(), title);
            }
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
        System.out.printf("\015\033[KFetched \033[32m%,7d\033[0m total parts for title number %2.2s: \033[33m%41.41s\033[0m: ", count, titleDescriptor.getNumber(), titleDescriptor.getName());
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
