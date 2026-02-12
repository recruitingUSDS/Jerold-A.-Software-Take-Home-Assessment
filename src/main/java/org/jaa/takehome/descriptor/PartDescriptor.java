package org.jaa.takehome.descriptor;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** POJO for a part – matches the JSON objects returned by /titles/{title}/parts. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PartDescriptor {
    private String type;
    private String partNumber;   // "1"
    private TitleDescriptor titleDescriptor;        // "General Provisions"
    private String identifier;
    private String name;
    private String amendedDate;
    private String issueDate;
    private boolean  substantive;
    private boolean removed;
    private boolean subPart;
    private String agency;       // may be null if the API does not echo it

    public PartDescriptor(String type, String partNumber, TitleDescriptor titleDescriptor,
                          String name, String identifier,
                          String amendedDate, String issueDate,
                          boolean substantive, boolean removed, boolean subPart, String agency) {
        this.type = type;
        this.partNumber = partNumber;
        this.titleDescriptor = titleDescriptor;
        this.identifier = identifier;
        this.name = name;
        this.amendedDate = amendedDate;
        this.issueDate = issueDate;
        this.substantive = substantive;
        this.removed = removed;
        this.subPart = subPart;
        this.agency = agency;
    }

    public String getType() {return type;}
    public void setType(String type) {this.type = type;}

    public String getIdentifier() {return identifier;}
    public void setIdentifier(String identifier) {this.identifier = identifier;}

    public String getName() {return name;}
    public void setName(String name) {this.name = name;}

    public String getAmendedDate() {return amendedDate;}
    public void setAmendedDate(String amendedDate) {this.amendedDate = amendedDate;}

    public String getIssueDate() {return issueDate;}
    public void setIssueDate(String issueDate) {this.issueDate = issueDate;}

    public boolean isSubstantive() {return substantive;}
    public void setSubstantive(boolean substantive) {this.substantive = substantive;}

    public boolean isRemoved() {return removed;}
    public void setRemoved(boolean removed) {this.removed = removed;}

    public boolean isSubPart() {return subPart;}
    public void setSubPart(boolean subPart) {this.subPart = subPart;}


    public String getPartNumber() { return partNumber; }
    public void setPartNumber(String partNumber) { this.partNumber = partNumber; }

    public String getTitle() { return titleDescriptor.getName(); }
    public void setTitleDescriptor(TitleDescriptor titleDescriptor) { this.titleDescriptor = titleDescriptor; }

    public String getAgency() { return agency; }
    public void setAgency(String agency) { this.agency = agency; }

    @Override
    public String toString() {
        return "PartDescriptor{partNumber='" + partNumber + "', title='" + titleDescriptor.getName() + "', agency='" + agency + "'}";
    }
}
