package org.jaa.takehome.descriptor;

public class ChapterDescriptor {
    private String chapterName;
    private TitleDescriptor titleDescriptor;
    private AgencyDescriptor agencyDescriptor;
    public ChapterDescriptor(String chapterName, TitleDescriptor titleDescriptor, AgencyDescriptor agencyDescriptor) {
        this.chapterName = chapterName;
        this.titleDescriptor = titleDescriptor;
        this.agencyDescriptor = agencyDescriptor;
    }

    public void setChapterName(String chapterName) { this.chapterName = chapterName; }
    public String getChapterName() { return chapterName; }
    public TitleDescriptor getTitleDescriptor() { return this.titleDescriptor; }
    public AgencyDescriptor getAgencyDescriptor() { return this.agencyDescriptor; }



}
