package org.jaa.takehome.descriptor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
    private TitleDescriptor titleDescriptor;
    private List<PartDescriptor> agencyParts = new ArrayList<>();

    public AgencyDescriptor(String name, String shortName, String displayName, String sortableName, String slug) {
        this.name = name;
        this.shortName = shortName;
        this.displayName = displayName;
        this.sortableName = sortableName;
        this.slug = slug;
    }

    public String getName() {return name; }
    public void setName(String name) {this.name = name;}

    public void addAgencyPart(PartDescriptor part) { agencyParts.add(part); }
    public void setTitleDescriptor(TitleDescriptor titleDescriptor) { this.titleDescriptor = titleDescriptor; }
    @Override
    public String toString() {
        List<String> titles = new ArrayList<>();
        for (PartDescriptor part : agencyParts) {
            titles.add(part.getTitle());
        }
        return "AgencyDescriptor{agencyName='"
                + name
                + "', title count = "
                + titles.size()
                + "titles : ["
                + String.join(",", titles)
                + "]";
    }
}
