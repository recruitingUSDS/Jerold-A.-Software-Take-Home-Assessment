package org.jaa.takehome.downloader;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import org.jaa.takehome.Constants;
import org.jaa.takehome.descriptor.TitleDescriptor;
import org.jaa.takehome.utilities.Utils;

import static org.jaa.takehome.Constants.*;

/**
 * Downloader that uses the eCFR v1 public REST API to fetch every part of Title 1.
  * Features:
 */
public class TitleDownloader {

    /* some redundant constants can be refactored out in the future */
    /** Base URL of the eCFR v1 API (no trailing slash). */
    public static final String API_BASE = "https://www.ecfr.gov/api/versioner/v1/";

    /** Where the downloaded JSON files will be written. */
    protected static final Path OUTPUT_ROOT = Paths.get("output", "AllTitles");

    /** HTTP client – we keep a single instance for connection reuse. */
    private final HttpClient httpClient;

    /** Jackson ObjectMapper – used for JSON <-> POJO conversion. */
    private final ObjectMapper mapper = new ObjectMapper();

    /** User‑Agent string that the API requires. */
    private static final String USER_AGENT = "TitleDownloader/1.0 (+https://github.com/jerryabramson)";

    /** Max number of items per page the API accepts (100 is the documented max). */
    private static final int PAGE_SIZE = 100;

    /** Minimum number of remaining requests before we pause (safety margin). */
    private static final int SAFETY_REMAINING = 5;

    /** Minimum sleep (ms) when we have to wait for the rate‑limit reset. */
    private static final long MIN_SLEEP_MS = 1_000L;

    /**
     * constructor
     */
    public TitleDownloader() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    /**
     * saves CSV of all titles and details
     * @param allTitles list of titles
     * @throws IOException unlikely file i/o exceptions
     * @throws InterruptedException very unlikely, usually pressing 'CTRL-C'
     */

    public void saveAllTitles(List<TitleDescriptor> allTitles) throws IOException, InterruptedException {
        File output = OUTPUT_ROOT.toFile();
        output.mkdirs();
        File csvFile = new File(output,"titles.csv");
        if (csvFile.exists()) csvFile.delete();
        csvFile.createNewFile();
        System.out.print("Retrieving all titles ... ");
        try (PrintStream outputFileStream = new PrintStream(csvFile)) {
            for (TitleDescriptor title : allTitles) {
                outputFileStream.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                                        title.number,
                                        title.name,
                                        title.latestAmendedOn,
                                        title.latestIssueDate,
                                        title.upToDateAsOf);

            }
        }
        System.out.printf("Retrieved %d titles and stored as csv file named %s\n",
                          allTitles.size(),
                          csvFile.getName());

    }

    /**
     * Retrieve full XML for a whole title (very large, and not terribly useful)
     * @param title provided title
     * @throws IOException if there are issues at the end-points, this can happen
     * @throws InterruptedException unlikely
     */
    public void retrieveAndSaveTitleXml(TitleDescriptor title) throws IOException, InterruptedException {
        File output = OUTPUT_ROOT.toFile();
        File partDetailsDirectory = new File(output, "title-" + title.number);
        partDetailsDirectory.mkdirs();
        File partDetailsFile = new File(partDetailsDirectory, "title-" + title.getNumber() + ".xml");
        System.out.printf("\015\033[KTitle (\033[35m%s\033[0m; \033[36m%s\033[0m) ",
                          title.getNumber(), title.getName());
        System.out.printf(" => Writing to: \033[31m%s\033[0m ... ",
                          partDetailsFile.getAbsolutePath());
        String xml = getFullTitleXml(title);
        if (xml != null && !xml.isEmpty()) {
            double kb = xml.length() / 1024.0;
            System.out.printf("Size is \033[35m%.2f\033[0m KB", kb);
            Files.write(partDetailsFile.toPath(), xml.getBytes());
            System.out.printf("\015\033[KTitle (\033[32m%s\033[0m; \033[33m%s\033[0m) saved to \033[32m%s\033[0m [size \033[35m%.2f\033[0m KB]\n",
                              title.getNumber(),
                              title.getName(),
                              partDetailsFile.getAbsolutePath(),
                              kb);
        }
        //System.out.printf("Pausing for %d milliseconds ...", Constants.REQUEST_DELAY_MS);
        //try {Thread.sleep(Constants.REQUEST_DELAY_MS);} catch (InterruptedException e) { /**/ }
    }


    /**
     * Gets a full list of all the titles
     * @return list of titles
     * @throws IOException unlikely
     * @throws InterruptedException unlikely
     */
    public  List<TitleDescriptor> getAllTitlesFromEndpoint()  throws IOException, InterruptedException {
        List<TitleDescriptor> allTitles = new ArrayList<>();
        String url = API_BASE + "/titles.json";
        HttpResponse<String> resp = sendGet(url);
        if (resp == null) return null;
        // The response body is a JSON object with a "titles" array.
        try {
            JsonNode root = mapper.readTree(resp.body());
            JsonNode titles = root.path("titles");
            //int testSize = 2;
            for (JsonNode node : titles) {
                String number = node.path("number").asText();
                String name = node.path("name").asText();
                String latestAmended = node.path("latest_amended_on").asText();
                String latestIssueDate = node.path("latest_issue_date").asText();
                String upToDateAsOf = node.path("up_to_date_as_of").asText();
                boolean reserved = node.path("reserved").asBoolean();
                TitleDescriptor titleDetails = new TitleDescriptor(number, name, latestAmended, latestIssueDate, upToDateAsOf, reserved);
                allTitles.add(titleDetails);
            }
        } catch (JsonProcessingException e) {
            System.out.println("Invalid JSON Response:");
            System.out.println(resp.body());
            throw new IllegalStateException("Cannot get Title numbers in API response");
        }
        return allTitles;
    }

    /**
     * Downloads full XML for a title, these can be quite large.
     * @param title title
     * @return very large XML string
     * @throws IOException unlikely
     * @throws InterruptedException unlikely
     */
    protected String getFullTitleXml(TitleDescriptor title) throws IOException, InterruptedException {
        // https://www.ecfr.gov/api/versioner/v1/full/2022-12-29/title-2.xml"
        ; // latestAmendedOn, number
        if (title.getLatestAmendedOn() != null && !title.getLatestAmendedOn().isEmpty()) {
            String url = String.format("%s/%s/title-%s.xml",
                                       ENDPOINT_PARTS,
                                       title.latestAmendedOn,
                                       title.number);
            HttpResponse<String> resp = sendGet(url);
            if (resp == null) return null;
            return resp.body();
        }
        return null;
    }


    /**
     * Helper: perform a GET request with proper headers & rate‑limit handling
     * @param url URL of rest endpoint
     * @return  full response
     * @throws IOException unlikely
     * @throws InterruptedException unlikely
     */
    private HttpResponse<String> sendGet(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                                                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            System.out.println();
            Utils.reportError(request, response, "Error getting titles from endpoint '" + url);
            return null;
        }


        // ---- Rate‑limit handling -------------------------------------------------
        Optional<String> remainingHeader = response.headers().firstValue("X-Rate-Limit-Remaining");
        Optional<String> resetHeader = response.headers().firstValue("X-Rate-Limit-Reset");

        if (remainingHeader.isPresent()) {
            int remaining = Integer.parseInt(remainingHeader.get());
            if (remaining <= SAFETY_REMAINING) {
                // The reset header is a Unix epoch seconds timestamp.
                long waitMs = MIN_SLEEP_MS;
                if (resetHeader.isPresent()) {
                    long resetEpochSec = Long.parseLong(resetHeader.get());
                    long nowSec = Instant.now().getEpochSecond();
                    long secsUntilReset = Math.max(0, resetEpochSec - nowSec);
                    waitMs = Math.max(waitMs, secsUntilReset * 1000L);
                }
                System.out.printf("⏳ Rate limit low (remaining=%d). Sleeping %d ms…%n", remaining, waitMs);
                Thread.sleep(waitMs);
            }
        }
        // -------------------------------------------------------------------------

        return response;
    }
}


