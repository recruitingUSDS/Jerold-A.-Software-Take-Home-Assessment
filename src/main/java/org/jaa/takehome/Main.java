package org.jaa.takehome;

import org.jaa.takehome.descriptor.AgencyDescriptor;
import org.jaa.takehome.descriptor.PartDescriptor;
import org.jaa.takehome.descriptor.TitleDescriptor;
import org.jaa.takehome.downloader.AgencyDownloader;
import org.jaa.takehome.downloader.TitleDownloader;
import org.jaa.takehome.downloader.VersionerDownloader;

import java.nio.file.Paths;
import java.util.List;

/**
 * driver to download all required data
 */
public class Main {
    /* --------------------------------------------------------------------- */
    /*                     ENTRY POINT (main)                                */
    /* --------------------------------------------------------------------- */
    public static void main(String[] args) {

        Constants.currentWorkingDirectoryPath = Paths.get("").toAbsolutePath();
        System.out.println("Current working directory: "+Constants.currentWorkingDirectoryPath.toAbsolutePath().normalize());
        final TitleDownloader titleDownloader;
        final VersionerDownloader versionerDownloader;
        final AgencyDownloader agencyDownloader;
        final List<AgencyDescriptor> allAgencies;
        try {
            System.out.println("--------------------------------------------------------------------------------");

            /* Get all titles and all details and store in fs/db*/
            titleDownloader = new TitleDownloader();
            List<TitleDescriptor> allTitles = titleDownloader.getAllTitlesFromEndpoint();
            if (allTitles != null && !allTitles.isEmpty()) {
                titleDownloader.saveAllTitles(allTitles);
            }

            versionerDownloader = new VersionerDownloader();
            for (TitleDescriptor title : allTitles) {
                List<PartDescriptor> partDescriptors = versionerDownloader.fetchPartsForTitle(title);
                if (partDescriptors != null) {
                    title.setParts(partDescriptors);
                }
                versionerDownloader.crossReferenceAllPartsForTitle(title);
            }
            System.out.println("--------------------------------------------------------------------------------");
            /* get All agencies and store in fs/db */
            agencyDownloader = new AgencyDownloader(allTitles);
            allAgencies = agencyDownloader.getAllAgencyDetailsFromEndpoint();
            agencyDownloader.getAllPartsForAllAgenciesAndSaveXml(allAgencies);

            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("Downloading and saving all title XML files");
            for (TitleDescriptor title : allTitles) {
                titleDownloader.retrieveAndSaveTitleXml(title);
            }
            System.out.println("--------------------------------------------------------------------------------");


        } catch (Exception e) {
            System.out.println("\n\n");
            System.out.flush();
            System.out.println("Download failed: " + e.getMessage());
            e.printStackTrace(System.out);
            System.exit(1);
        }

    }

}
