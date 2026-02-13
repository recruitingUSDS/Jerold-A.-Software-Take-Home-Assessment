package org.jaa.takehome.descriptor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


/* --------------------------------------------------------------- */
/** Agency Descriptor - contains a list of referenced sections – */
@JsonIgnoreProperties(ignoreUnknown = true)
public  class AgencyDescriptor {
    private String name;
    private String shortName;
    private String displayName;
    private String sortableName;
    private String slug;
    private String sectionName;
    private final List<TitleDescriptor> agencyTitles = new ArrayList<>();
    private final List<ChapterDescriptor> agencyChapters = new ArrayList<>();
    private final Path agencyOutputPath;

    /**
     * constructor
     * @param name
     * @param shortName
     * @param displayName
     * @param sortableName
     * @param slug
     * @param agencyOutputPath
     */
    public AgencyDescriptor(String name, String shortName, String displayName, String sortableName, String slug, Path agencyOutputPath) {
        this.name = name;
        this.shortName = shortName;
        this.displayName = displayName;
        this.sortableName = sortableName;
        this.slug = slug;
        this.agencyOutputPath = agencyOutputPath;
    }

    // Settors and gettors
    public Path getAgencyOutputPath() {return agencyOutputPath;}
    public String getName() {return name; }
    public String getShortName() {return shortName;}
    public void setShortName(String shortName) {this.shortName = shortName;}
    public void setName(String name) {this.name = name;}
    public void addAgencyChapter(ChapterDescriptor chapter) { agencyChapters.add(chapter); }
    public void addTitleDescriptor(TitleDescriptor titleDescriptor) { this.agencyTitles.add(titleDescriptor); }
    public int getTitleCount() { return agencyTitles.size(); }
    public int getChapterCount() { return agencyChapters.size(); }
    public List<ChapterDescriptor> getChapters() { return agencyChapters; }

    public String getDisplayName() {return displayName;}
    public void setDisplayName(String displayName) {this.displayName = displayName;}
    public String getSortableName() {return sortableName;}
    public void setSortableName(String sortableName) {this.sortableName = sortableName;}
    public String getSlug() {return slug;}
    public void setSlug(String slug) {this.slug = slug;}
    public String getSectionName() {return sectionName;}
    public void setSectionName(String sectionName) {this.sectionName = sectionName;}

    @Override
    public String toString() {
        List<String> chapters = new ArrayList<>();
        for (ChapterDescriptor chapter : agencyChapters) {
            chapters.add("["
                                 + chapter.getChapterName()
                                 + ";"
                                 + chapter.getAgencyDescriptor().getName()
                                 + ";"
                                 + chapter.getTitleDescriptor().getName()
                         + "]");
        }
        return "AgencyDescriptor{agencyName='"
                + name
                + "', chapters count = "
                + (chapters != null ? chapters.size() : "nil")
                + "Chapters : ["
                + (chapters != null ? String.join(",", chapters) : "nil")
                + "]";
    }
}
