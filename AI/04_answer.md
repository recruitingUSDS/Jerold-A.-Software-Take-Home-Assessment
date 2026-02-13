Below is a **complete, self‑contained Java example** that shows how you can program‑matically download the HTML pages that make up **Title 1** of the Electronic Code of Federal Regulations (eCFR).  

The code is written for Java 17+ and uses only the JDK’s built‑in HTTP client (`java.net.http.HttpClient`) and the popular **Jsoup** library for HTML parsing.  

> **Important disclaimer** – Before you run any automated downloader against a public website you should:
> 1. **Read the site’s robots.txt** (e.g. <https://www.ecfr.gov/robots.txt>) and any Terms of Use to confirm that bulk downloading is permitted.  
> 2. **Respect the site’s rate limits** – the example includes a configurable delay (`REQUEST_DELAY_MS`) so you don’t hammer the server.  
> 3. **Prefer an official bulk‑download or API** if one exists (the eCFR provides a bulk‑download zip on the “Download the eCFR” page).  
> 4. **Identify your program** with a clear `User‑Agent` string that includes contact information.

If you are comfortable with those conditions, the code below will:

1. **Fetch the Table of Contents (TOC) for Title 1** – this page lists every part and sub‑part.  
2. **Parse the TOC** to collect the URLs of every individual part.  
3. **Download each part’s HTML** and save it to a local directory (`output/`).  
4. **Throttle requests** to stay friendly to the server.

---

## 1️⃣ Maven / Gradle Setup

The only external dependency is **Jsoup** (for easy HTML parsing).  

If you use **Maven**, add this to your `pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>org.jsoup</groupId>
        <artifactId>jsoup</artifactId>
        <version>1.18.1</version>
    </dependency>
</dependencies>
```

If you use **Gradle (Kotlin DSL)**:

```kotlin
dependencies {
    implementation("org.jsoup:jsoup:1.18.1")
}
```

*(If you prefer not to use a build tool, you can download the Jsoup JAR from https://jsoup.org/download and add it to your classpath manually.)*

---

## 2️⃣ Full Java Source (single file)

```java
package com.example.ecfrdownloader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

/**
 * Simple downloader for Title 1 of the eCFR (www.ecfr.gov).
 *
 * <p>Features:
 * <ul>
 *   <li>Fetches the Title‑1 Table of Contents page.</li>
 *   <li>Extracts URLs for every part (and sub‑part) listed.</li>
 *   <li>Downloads each page, respecting a configurable delay.</li>
 *   <li>Saves files under {@code output/Title-1/} preserving the part identifier in the filename.</li>
 * </ul>
 *
 * <p>Before running, make sure you are allowed to scrape the site and that you respect the
 * site's robots.txt and usage policies.
 *
 * @author  OpenAI ChatGPT
 */
public class Title1Downloader {

    /** Base URL of the eCFR site (no trailing slash). */
    private static final String ECFR_BASE = "https://www.ecfr.gov";

    /** Relative URL for the Title‑1 Table of Contents page. */
    private static final String TITLE_1_TOC = "/current/title-1";

    /** Where to store the downloaded HTML files. */
    private static final Path OUTPUT_ROOT = Paths.get("output", "Title-1");

    /** How long to wait between successive HTTP requests (in milliseconds). */
    private static final long REQUEST_DELAY_MS = 500;   // 0.5 s – adjust as needed

    /** Custom User‑Agent – replace with your own contact info if you wish. */
    private static final String USER_AGENT = "Title1Downloader/1.0 (+https://github.com/yourname)";

    /** Shared HttpClient used for all requests. */
    private final HttpClient httpClient;

    public Title1Downloader() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    /* --------------------------------------------------------------------- */
    /*                     PUBLIC ENTRY POINT (main)                        */
    /* --------------------------------------------------------------------- */
    public static void main(String[] args) {
        Title1Downloader downloader = new Title1Downloader();
        try {
            downloader.run();
            System.out.println("\n✅ All done! Files are in " + OUTPUT_ROOT.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("❌ Download failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /* --------------------------------------------------------------------- */
    /*                         Core workflow                                 */
    /* --------------------------------------------------------------------- */
    public void run() throws IOException, InterruptedException {
        // 1️⃣ Ensure output directory exists
        Files.createDirectories(OUTPUT_ROOT);

        // 2️⃣ Download and parse the TOC page for Title 1
        String tocHtml = fetchPage(ECFR_BASE + TITLE_1_TOC);
        List<PartInfo> parts = extractPartsFromToc(tocHtml);

        System.out.printf("🔎 Found %d parts to download.%n", parts.size());

        // 3️⃣ Iterate over each part, download its HTML, and save to disk
        int counter = 0;
        for (PartInfo part : parts) {
            counter++;
            System.out.printf("[%3d/%d] Downloading %s …%n", counter, parts.size(), part.title);
            String partHtml = fetchPage(part.absoluteUrl);
            savePart(part, partHtml);
            // Respectful throttling
            Thread.sleep(REQUEST_DELAY_MS);
        }
    }

    /* --------------------------------------------------------------------- */
    /*                     Helper: fetch a page (String)                     */
    /* --------------------------------------------------------------------- */
    private String fetchPage(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return response.body();
        } else {
            throw new IOException("Failed to fetch " + url + " – HTTP " + status);
        }
    }

    /* --------------------------------------------------------------------- */
    /*                Helper: parse TOC and collect part URLs                */
    /* --------------------------------------------------------------------- */
    private List<PartInfo> extractPartsFromToc(String tocHtml) {
        Document doc = Jsoup.parse(tocHtml, ECFR_BASE); // base URI helps resolve relative URLs

        // The TOC page uses a fairly regular structure:
        //   <nav class="toc"> … <a href="/current/title-1/part-1">Part 1 – ...</a>
        // We’ll look for anchors inside the main navigation area.
        Elements links = doc.select("nav.toc a[href]");

        // Filter out any navigation links that don’t point to a part (e.g., “Home”, “Search”)
        List<PartInfo> parts = new ArrayList<>();
        for (Element link : links) {
            String href = link.attr("href");
            // We only want URLs that contain “/part-” under the Title‑1 path.
            if (href.matches("^/current/title-1/part-\\d+.*")) {
                String title = link.text().trim();
                String absolute = ECFR_BASE + href;
                parts.add(new PartInfo(title, absolute));
            }
        }

        // The TOC may contain duplicate entries (e.g., sub‑parts). Remove duplicates by URL.
        Map<String, PartInfo> deduped = parts.stream()
                .collect(Collectors.toMap(p -> p.absoluteUrl, p -> p, (a, b) -> a));

        // Sort alphabetically by part number for deterministic output
        return deduped.values().stream()
                .sorted(Comparator.comparing(p -> p.title))
                .collect(Collectors.toList());
    }

    /* --------------------------------------------------------------------- */
    /*                Helper: write a part's HTML to disk                     */
    /* --------------------------------------------------------------------- */
    private void savePart(PartInfo part, String html) {
        // Build a safe filename: replace spaces and slashes, keep the part number
        // Example title: "Part 1 – General Provisions"
        String sanitized = part.title.replaceAll("[^a-zA-Z0-9\\-]+", "_");
        Path file = OUTPUT_ROOT.resolve(sanitized + ".html");

        try {
            Files.writeString(file, html, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write file " + file, e);
        }
    }

    /* --------------------------------------------------------------------- */
    /*                     Simple DTO for a part entry                       */
    /* --------------------------------------------------------------------- */
    private static final class PartInfo {
        final String title;        // Human‑readable title as shown on the TOC
        final String absoluteUrl;  // Fully‑qualified URL to the part page

        PartInfo(String title, String absoluteUrl) {
            this.title = title;
            this.absoluteUrl = absoluteUrl;
        }
    }
}
```

---

## 3️⃣ How the Code Works (Step‑by‑Step)

| Step | What Happens | Key Lines |
|------|--------------|-----------|
| **1. Initialise** | Creates an `HttpClient` that follows redirects and has a 20 s connect timeout. | `HttpClient.newBuilder()` |
| **2. Output folder** | Guarantees `output/Title-1/` exists. | `Files.createDirectories(OUTPUT_ROOT);` |
| **3. Fetch TOC** | Performs a GET request to `https://www.ecfr.gov/current/title-1`. | `fetchPage(ECFR_BASE + TITLE_1_TOC);` |
| **4. Parse TOC** | Uses Jsoup to locate all `<a>` elements inside the navigation block (`nav.toc`). Filters those that match `/part-\\d+`. | `doc.select("nav.toc a[href]")` + regex filter |
| **5. De‑duplicate & sort** | Removes duplicate URLs and sorts alphabetically for predictable ordering. | `Map<String, PartInfo>` & `sorted` |
| **6. Loop over parts** | For each part URL: <br>  • GET the HTML <br>  • Save it as a cleaned‑up filename <br>  • Sleep `REQUEST_DELAY_MS` milliseconds to avoid hammering the server. | `fetchPage(part.absoluteUrl)`, `savePart(part, partHtml)`, `Thread.sleep` |
| **7. Completion** | Prints a success message with the absolute path to the output directory. | `System.out.println("\n✅ All done! …")` |

---

## 4️⃣ Customising the Downloader

| Need | What to Change |
|------|----------------|
| **Different delay** | Adjust `REQUEST_DELAY_MS`. A larger value (e.g., 2000 ms) is more polite. |
| **Save as JSON or plain text** | Replace the `savePart` method with code that extracts the `<pre>` or `<section>` text and writes it in your desired format. |
| **Parallel downloads** (use with extreme caution) | Replace the sequential `for` loop with an `ExecutorService` and submit tasks; still keep a **global rate limiter** (e.g., Guava’s `RateLimiter`). |
| **Only a subset of parts** | After `extractPartsFromToc`, filter the list: `parts = parts.stream().filter(p -> p.title.contains("Part 10")).collect(Collectors.toList());` |
| **Follow “next page” links** (if a title spans multiple paginated TOC pages) | Extend `extractPartsFromToc` to detect a “next” anchor (`a[rel=next]`) and recursively fetch additional TOC pages. |
| **Respect robots.txt programmatically** | Use a small library like `crawler-commons` to parse `https://www.ecfr.gov/robots.txt` and verify that the paths you request are allowed. |

---

## 5️⃣ Things to Keep in Mind

1. **Legal / Policy** – The eCFR is a U.S. government work and is in the public domain, but the site’s Terms of Use still ask you to “use the site responsibly”.  
2. **Bulk‑download option** – The eCFR offers a complete zip archive (see the “Download the eCFR” link on the home page). If you need *all* of Title 1, that zip is far faster and more courteous than scraping each page individually.  
3. **Change in site structure** – The CSS selector `nav.toc a[href]` works today, but if the government redesigns the site the selector may need tweaking. The code is modular; you only need to modify `extractPartsFromToc`.  
4. **Encoding** – The pages are UTF‑8; `Files.writeString` preserves that encoding automatically.  
5. **Error handling** – The example aborts on the first non‑2xx response. For a more robust production script you could catch the exception, log it, and continue with the next part.

---

## 6️⃣ Running the Example

```bash
# 1️⃣ Clone / create a project directory
mkdir ecfr-downloader && cd ecfr-downloader

# 2️⃣ Add the Java source file (Title1Downloader.java) under src/main/java/com/example/ecfrdownloader/
#    (or use any package you like; just adjust the package statement.)

# 3️⃣ Add Jsoup to your build (see Maven/Gradle snippets above).

# 4️⃣ Build & run
# Maven
mvn compile exec:java -Dexec.mainClass=com.example.ecfrdownloader.Title1Downloader

# Gradle
./gradlew run --args=''
```

After the run finishes, you’ll see a directory structure like:

```
output/
└─ Title-1/
   ├─ Part_1_-_General_Provisions.html
   ├─ Part_2_-_Definitions.html
   ├─ Part_3_-_Procedures.html
   └─ … (one file per part)
```

Each file contains the **exact HTML** returned by the eCFR for that part, which you can later process with Jsoup, convert to plain text, or feed into any downstream analysis pipeline.

---

### 🎉 Happy scraping (responsibly)! If you run into any hiccups—e.g., a change in the site’s markup or a need for more sophisticated rate‑limiting—feel free to ask for further tweaks.
