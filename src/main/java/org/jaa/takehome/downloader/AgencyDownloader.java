package org.jaa.takehome.downloader;




import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.jaa.takehome.descriptor.AgencyDescriptor;
import org.jaa.takehome.descriptor.PartDescriptor;
import org.jaa.takehome.descriptor.TitleDescriptor;
import org.jaa.takehome.utilities.Utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    /*                     ★★  CONFIGURATION  ★★                      */
    /* --------------------------------------------------------------- */
    /** Base URL for the live Versioner API. */

    /** Where the XML files will be written (under a sub‑folder named after the agency). */
    private static final Path OUTPUT_ROOT = Paths.get("output");


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
    /** Main orchestration method. */
    public void getAllPartsForAllAgenciesAndSaveXml(List<AgencyDescriptor> allAgencies) throws IOException, InterruptedException {

        // 1️⃣  Verify that the agency exists (optional but nice for early failure)
        if (allAgencies.isEmpty()) {
            throw new IllegalArgumentException("No Agencies found in the API");
        }

        for (AgencyDescriptor agency : allAgencies) {
            // 0️⃣  Create the top‑level output folder for the agency
            String agencySlug = slugify(agency.getName());
            Path agencyRoot = OUTPUT_ROOT.resolve(agencySlug);
            Files.createDirectories(agencyRoot);

            // 3️⃣  For each title, download all parts that belong to the same agency
            for (TitleDescriptor title : titleDescriptorList) {
                downloadTitle(title, agencyRoot, agency);
            }
        }
    }

    /* --------------------------------------------------------------- */
    /** Download every part for a given title (already known to belong to the target agency). */
    private void downloadTitle(TitleDescriptor titleDetails,
                               Path agencyRoot,
                               AgencyDescriptor agency)
            throws IOException, InterruptedException {

        String titleNum = titleDetails.number;
        Path titleDir = agencyRoot.resolve("Title-" + String.format("%02d", Integer.parseInt(titleNum)));
        Files.createDirectories(titleDir);

        // 2️⃣  List parts for this title – we also pass the agency filter just in case a title mixes agencies.
        List<PartDescriptor> parts = titleDetails.getParts();
        System.out.printf("Title number %s '%s' → Total of %d part(s)\n",
                          titleDetails.number, titleDetails.getName(), parts.size());

        int count = 1;
        for (PartDescriptor part : parts) {
            System.out.printf("\015\033[K\t[%3d/%d] Part Number %s: Name '%s' – Title Name '%s'",
                              count, parts.size(), part.getPartNumber(), part.getName(), part.getTitle());
            System.out.flush();
            count++;
            String xml = fetchPartXml(titleDetails, part);
            savePartXml(titleDir, part.getPartNumber(), xml);
            Thread.sleep(REQUEST_DELAY_MS);
        }
    }

    /* --------------------------------------------------------------- */
    /** GET /agencies → simple list of agency names (String). */
    public List<AgencyDescriptor> getAllAgencyDetailsFromEndpoint()
            throws IOException, InterruptedException {
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
            AgencyDescriptor agencyDescriptor = new AgencyDescriptor(name, shortName, displayName, sortableName, slug);

            JsonNode cfrRefs = node.path("cfr_references");
            if (cfrRefs != null && cfrRefs.isArray()) {
                for (JsonNode cfrRef : cfrRefs) {
                    String titleNumber = cfrRef.path("title").asText();
                    String chapterName = cfrRef.path("chapter").asText();
                    for (TitleDescriptor titleDescriptor : titleDescriptorList) {
                        if (titleDescriptor.number.equals(titleNumber)) {
                            agencyDescriptor.setTitleDescriptor(titleDescriptor);
                            List<PartDescriptor> parts = titleDescriptor.getParts();
                            for (PartDescriptor part : parts) {
                                if (part.getType().equalsIgnoreCase("chapter")) {

                                }
                            }
                        }
                    }
                }
            }
            allAgencies.add(agencyDescriptor);
        }
        return allAgencies;
    }



    /** GET /titles/{title}/parts/{part} → raw XML for a single part. */
    private String fetchPartXml(TitleDescriptor titleDetails, PartDescriptor partDescriptor)
            throws IOException, InterruptedException {
        // https://www.ecfr.gov/api/versioner/v1/full/2022-12-29/title-2.xml"
        String url = String.format("%s/%s/title-%s.xml?part=%s",
                                       ENDPOINT_PARTS,
                                       partDescriptor.getAmendedDate(),
                                       titleDetails.number,
                                       partDescriptor.getPartNumber());
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
            return fetchPartXml(titleDetails, partDescriptor);
        }
        Utils.ensureSuccess(request, resp, "Failed to fetch part: "
                + titleDetails.number
                + ": Part Number :"
                + partDescriptor.getPartNumber()
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
    }

    /** Write the XML payload to a file named “part‑<number>.xml”. */
    private void savePartXml(Path titleDir, String partNumber, String xml) {
        String safePart = partNumber.replaceAll("[^0-9A-Za-z]", "_");
        Path outFile = titleDir.resolve("part-" + safePart + ".xml");
        try {
            Files.writeString(outFile, xml,
                              StandardOpenOption.CREATE,
                              StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write " + outFile, e);
        }
    }

    /* --------------------------------------------------------------- */
    /** Very small utility: URL‑encode a query‑parameter value. */
    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Turn a free‑form string into a safe directory name (e.g. “Environmental Protection Agency” → “environmental‑protection‑agency”). */
    private static String slugify(String s) {
        return s.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }




}