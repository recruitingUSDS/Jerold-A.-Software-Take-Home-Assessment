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

/**
 * eCFR downloader that filters by agency.
 *
 * <p>Workflow:
 * <ol>
 *   <li>GET /agencies → verify the agency you want.</li>
 *   <li>GET /titles → keep only titles whose {@code agency} matches the target.</li>
 *   <li>For each title, GET /titles/{title}/parts?agency=TARGET → list of parts for that agency.</li>
 *   <li>GET /titles/{title}/parts/{part} → full XML; write to disk.</li>
 * </ol>
 *
 * The client respects the public 5 req/s limit (500 ms pause) and retries once on a
 * 429 response, honoring the {@code Retry‑After} header.
 */
public class AgencyDownloader {

    /* --------------------------------------------------------------- */
    private final HttpClient httpClient;
    private final ObjectMapper jsonMapper;
    private final List<TitleDescriptor> titleDescriptorList;

    public AgencyDownloader(List<TitleDescriptor> titleDetails) {
        this.titleDescriptorList = titleDetails;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        this.jsonMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }


    /* --------------------------------------------------------------- */

    /**
     * Main orchestration method.
     */
    public void getAllPartsForAllAgenciesAndSaveXml(List<AgencyDescriptor> allAgencies) throws IOException, InterruptedException {

        // 1️⃣  Verify that the agency exists (optional but nice for early failure)
        if (allAgencies.isEmpty()) {
            throw new IllegalArgumentException("No Agencies found in the API");
        }

        for (AgencyDescriptor agency : allAgencies) {
            // 3️⃣  For each Agency, download all parts that belong to the same agency
            for (TitleDescriptor title : titleDescriptorList) {
                downloadTitlePartsForAgency(title, agency);
            }
        }
    }

    /* --------------------------------------------------------------- */

    /**
     * Download every part for a given title (already known to belong to the target agency).
     */
    private void downloadTitlePartsForAgency(TitleDescriptor titleDetails,
                                             AgencyDescriptor agency)
            throws IOException, InterruptedException {
        Path agencyRoot = agency.getAgencyOutputPath();
        String titleNum = titleDetails.getNumber();
        Path titleDir = agencyRoot.resolve("Title-" + String.format("%02d", Integer.parseInt(titleNum)));
        Files.createDirectories(titleDir);

        // 2️⃣  List parts for this title – we also pass the agency filter just in case a title mixes agencies.
        int count = 1;

        System.out.printf("Agency → \"\033[32m%s\033[0m\" → For (Title \033[34m%s\033[0m; \033[36m%s\033[0m) → Total of [\033[35m%,7d\33[0m] Expected Chapters(s)",
                          agency.getName(), titleDetails.getNumber(), titleDetails.getName(), agency.getChapters().size());

        for (ChapterDescriptor chapter : agency.getChapters()) {
            System.out.flush();
            count++;
            Exception passException = null;
            boolean ok = false;
            for (int backoff = 1; backoff < 5 && !ok; backoff++) {
                try {
                    String xml = fetchPartXml(titleDetails, chapter);
                    if (xml != null) {
                        double kb = xml.length() / 1024.0;
                        System.out.printf("\015\033[KDownload size \033[35m%.2f KB\033[0m for Agency → \"\033[32m%s\033[0m\" → For (Title \033[34m%s\033[0m; \033[36m%s\033[0m) → Chapter Number \033[32m%s\033[0m: ",
                                          kb, agency.getName(), titleDetails.getNumber(), titleDetails.getName(), chapter.getChapterName());
                        savePartXml(titleDir, chapter.getChapterName(), xml);
                    }
                    ok = true;
                } catch (IOException ie) {
                    passException = ie;
                    long backoffDelay = REQUEST_DELAY_MS * backoff;
                    System.out.printf("\nError downloading XML: \033[31m%s\033[0m. Backoff delay of %d Milliseconds\n",
                                      ie.getMessage(),
                                      backoffDelay);
                    Thread.sleep(backoffDelay);
                }
            }
            if (!ok && passException != null) throw new IOException(passException.getMessage());
        }
    }


    /* --------------------------------------------------------------- */
    /** GET /agencies → simple list of agency names (String). */
    public List<AgencyDescriptor> getAllAgencyDetailsFromEndpoint()
            throws IOException, InterruptedException {
        System.out.println("Getting all Agency Details");
        ChapterLocator chapterLocator = new ChapterLocator();
        List<AgencyDescriptor> allAgencies = new ArrayList<>();
        String url = API_BASE_URL + ENDPOINT_AGENCIES;
        HttpRequest request = buildRequest(url, "application/json");
        HttpResponse<String> resp = httpClient.send(request,
                                                    HttpResponse.BodyHandlers.ofString());
        Utils.ensureSuccess(request, resp, "Failed to fetch agencies – HTTP ");

        // The API returns an array of strings:
        // ["Department of Labor", "Environmental Protection Agency", …]
        JsonNode root = jsonMapper.readTree(resp.body());
        JsonNode agencies = root.path("agencies");
        for (JsonNode node : agencies) {
            String name = node.path("name").asText();
            String shortName = node.path("short_name").asText();
            String displayName = node.path("display_name").asText();
            String sortableName = node.path("sortable_name").asText();
            String slug = node.path("slug").asText();
            String agencySlug = Utils.slugify(name);
            Path agencyRoot = Paths.get(OUTPUT_ROOT.toString(), "Agencies", agencySlug);
            Files.createDirectories(agencyRoot);
            AgencyDescriptor agencyDescriptor = new AgencyDescriptor(name, shortName, displayName, sortableName, slug, agencyRoot);
            JsonNode cfrRefs = node.path("cfr_references");
            List<PartDescriptor> agencyParts = new ArrayList<>();
            if (cfrRefs != null && cfrRefs.isArray()) {
                for (JsonNode cfrRef : cfrRefs) {
                    String titleNumber = cfrRef.path("title").asText();
                    String chapterName = cfrRef.path("chapter").asText();
                    if (chapterName != null && !chapterName.isEmpty()) {
                        for (TitleDescriptor titleDescriptor : titleDescriptorList) {
                            if (titleDescriptor.getNumber().trim().equalsIgnoreCase(titleNumber)) {
                                ChapterDescriptor chapterDescriptor = chapterLocator.findChapter(titleDescriptor, agencyDescriptor, chapterName);
                                if (chapterDescriptor != null) {
                                    agencyDescriptor.addTitleDescriptor(titleDescriptor);
                                    agencyDescriptor.addAgencyChapter(chapterDescriptor);
                                }
                                break;
                            }
                        }
                    }
                }
            }
            allAgencies.add(agencyDescriptor);
        }
        saveAgencyList(allAgencies);
        return allAgencies;
    }


    private void saveAgencyList(List<AgencyDescriptor> agencyDescriptorList) throws IOException {
        Path agencyRoot = Paths.get(OUTPUT_ROOT.toString(), "Agencies");
        Path agencyCSVPath = Paths.get(agencyRoot.toString(), "agency.csv");
        File csvFile = agencyCSVPath.toFile();
        if (csvFile.exists()) csvFile.delete();
        csvFile.createNewFile();
        Path csvPath = csvFile.toPath().toAbsolutePath().normalize();
        Path relativePartPath = currentWorkingDirectoryPath.relativize(csvPath);
        try (PrintStream outputFileStream = new PrintStream(csvFile)) {
            for (AgencyDescriptor agency : agencyDescriptorList) {
                outputFileStream.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                                        agency.getName(),
                                        agency.getShortName(),
                                        agency.getDisplayName(),
                                        agency.getSortableName(),
                                        agency.getSlug(),
                                        agency.getSectionName(),
                                        (agency.getTitleCount() + " Titles"),
                                        (agency.getChapterCount() + " Chapters"));

            }
        }
    }



    /** GET /titles/{title}/parts/{part} → raw XML for a single part. */
    private String fetchPartXml(TitleDescriptor titleDetails, ChapterDescriptor chapterDescriptor)
            throws IOException, InterruptedException {
        String date = titleDetails.getLatestAmendedOn();
        // https://www.ecfr.gov/api/versioner/v1/full/2022-12-29/title-2.xml"
        String url = API_BASE_URL
                + ENDPOINT_XML_PART
                + String.format("/%s/title-%s.xml?chapter=%s", date, titleDetails.getNumber(), chapterDescriptor.getChapterName());
        HttpRequest request = buildRequest(url, "application/xml");
        HttpResponse<String> resp = httpClient.send(request,
                                                    HttpResponse.BodyHandlers.ofString());
        int status = resp.statusCode();
        // 429 – respect Retry-After header
        if (status == 429) {
            long waitSec = resp.headers()
                    .firstValue("Retry-After")
                    .map(Long::parseLong)
                    .orElse(5L);
            System.out.println("Rate‑limit hit – sleeping " + waitSec + " s");
            TimeUnit.SECONDS.sleep(waitSec);
            // retry once
            return fetchPartXml(titleDetails, chapterDescriptor);
        }
        if (status == 404) {
            System.out.printf("\015\033[KXML not found: Title [\033[31m%s\033[0m] Chapter %s\n",
                              titleDetails.getNumber(),
                              chapterDescriptor.getChapterName());
            return null;
        }
        Utils.ensureSuccess(request, resp, "Failed to fetch part: "
                + titleDetails.number
                + ": Chapter Number :"
                + chapterDescriptor.getChapterName()
                + ":");
        return resp.body();   // raw XML
    }

    /* --------------------------------------------------------------- */
    /** Helper that adds the optional Authorization header and the Accept header. */
    private HttpRequest buildRequest(String url, String acceptHeader) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", acceptHeader)
                .GET();
        return builder.build();
        /*
            private HttpRequest buildRequest(String url, String acceptHeader) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", acceptHeader)
                .GET();
         */
    }

    /** Write the XML payload to a file named “part‑<number>.xml”. */
    private void savePartXml(Path titleDir, String chapterNumber, String xml) {
        String safeChapter = chapterNumber.replaceAll("[^0-9A-Za-z]", "_");
        Path outFile = titleDir.resolve("chapter-" + safeChapter + ".xml");
        Path outPath = outFile.toAbsolutePath().normalize();
        Path relativePartPath = currentWorkingDirectoryPath.relativize(outPath);
        try {
            Files.writeString(outFile, xml,
                              StandardOpenOption.CREATE,
                              StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write " + outFile, e);
        }
        System.out.printf("Saved to file .../%s\n", relativePartPath);
    }

    /* --------------------------------------------------------------- */
    /** Very small utility: URL‑encode a query‑parameter value. */
    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }




}