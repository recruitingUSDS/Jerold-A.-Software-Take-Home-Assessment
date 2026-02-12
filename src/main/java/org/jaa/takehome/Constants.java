package org.jaa.takehome;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {
    public static final String API_BASE_URL = "https://www.ecfr.gov/api";

    /** Where the XML files will be written. */
    public static final Path OUTPUT_ROOT = Paths.get("output");


    /** End‑points (relative to the base URL). */
    public static final String ENDPOINT_AGENCIES = "/admin/v1/agencies.json";
    public static final String ENDPOINT_TITLES   = "/versioner/v1/titles";

    /** End‑point to fetch parts for a given title. */
    public static final String ENDPOINT_PARTS = API_BASE_URL +  "/versioner/v1/full";

    /** End‑point to fetch the **full XML** for a single part. */
    public static final String ENDPOINT_PART_DOCUMENT = "title-{title}.json";


    /** Milliseconds to pause between successive calls (50 ms = 20 req/s). */
    public static final long REQUEST_DELAY_MS = 50L;


}
