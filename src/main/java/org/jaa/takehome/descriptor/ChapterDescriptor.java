package org.jaa.takehome.descriptor;

/**
 * full chapter definition
 */
public class ChapterDescriptor {
    private String chapterName;
    private TitleDescriptor titleDescriptor;
    private AgencyDescriptor agencyDescriptor;

    /**
     * constructor
     * @param chapterName
     * @param titleDescriptor
     * @param agencyDescriptor
     */
    public ChapterDescriptor(String chapterName, TitleDescriptor titleDescriptor, AgencyDescriptor agencyDescriptor) {
        this.chapterName = chapterName;
        this.titleDescriptor = titleDescriptor;
        this.agencyDescriptor = agencyDescriptor;
    }

    // getters and setters
    public void setChapterName(String chapterName) { this.chapterName = chapterName; }
    public String getChapterName() { return chapterName; }
    public TitleDescriptor getTitleDescriptor() { return this.titleDescriptor; }
    public AgencyDescriptor getAgencyDescriptor() { return this.agencyDescriptor; }



}
