package org.jaa.takehome;

import org.jaa.takehome.descriptor.AgencyDescriptor;
import org.jaa.takehome.descriptor.PartDescriptor;
import org.jaa.takehome.descriptor.TitleDescriptor;
import org.jaa.takehome.downloader.AgencyDownloader;
import org.jaa.takehome.downloader.TitleDownloader;
import org.jaa.takehome.downloader.VersionerDownloader;

import java.util.List;

public class Main {
    /* --------------------------------------------------------------------- */
    /*                     ENTRY POINT (main)                               */
    /* --------------------------------------------------------------------- */
    public static void main(String[] args) {
        final TitleDownloader titleDownloader;
        final VersionerDownloader versionerDownloader;
        final AgencyDownloader AgencyDownloader;
        final List<AgencyDescriptor> allAgencies;
        try
        {
            System.out.println("--------------------------------------------------------------------------------");

            /* Get all titles and all details and store in fs/db*/
            titleDownloader = new TitleDownloader();
            List<TitleDescriptor> allTitles = titleDownloader.getAllTitlesFromEndpoint();
            titleDownloader.saveAllTitles(allTitles);

            versionerDownloader = new VersionerDownloader();
            for (TitleDescriptor title : allTitles) {
                List<PartDescriptor> partDescriptors = versionerDownloader.fetchPartsForTitle(title);
                if (partDescriptors != null) {
                    title.setParts(partDescriptors);
                }

            }
            System.out.println("--------------------------------------------------------------------------------");
            /* get All agencies and store in fs/db */
            AgencyDownloader agencyDownloader = new AgencyDownloader(allTitles);
            allAgencies = agencyDownloader.getAllAgencyDetailsFromEndpoint();
            agencyDownloader.getAllPartsForAllAgenciesAndSaveXml(allAgencies);

            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("Downloading and saving all title XML files");
            for (TitleDescriptor title : allTitles) {
                System.out.printf("\015\033[KTitle Number %s '%s' ... ", title.getNumber(), title.getName());
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
