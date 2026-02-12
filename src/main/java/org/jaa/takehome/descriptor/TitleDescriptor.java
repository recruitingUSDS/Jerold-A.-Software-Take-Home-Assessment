package org.jaa.takehome.descriptor;

import java.util.ArrayList;
import java.util.List;

public class TitleDescriptor {
    public String number;              // e.g. "1"
    public String name;                // e.g. "General Provisions"
    public String latestAmendedOn;     // e.g. "2022-12-29"
    public String latestIssueDate;     // e.g. "2024-05-17"
    public String upToDateAsOf;        // e.g. "2026-02-04"
    private boolean reserved;
    private List<PartDescriptor> parts = new ArrayList<>();

    public TitleDescriptor(String number,
                           String name,
                           String latestAmendedOn,
                           String latestIssueDate,
                           String upToDateAsOf,
                           boolean reserved)
    {
        this.number = number;
        this.name = name;
        this.latestAmendedOn = latestAmendedOn;
        this.latestIssueDate = latestIssueDate;
        this.upToDateAsOf = upToDateAsOf;
        this.reserved = reserved;
    }

    public String getNumber() {return number;}
    public void setNumber(String number) {this.number = number;}
    public String getName() {return name;}
    public void setName(String name) {this.name = name;}
    public String getLatestAmendedOn() {return latestAmendedOn;}
    public void setLatestAmendedOn(String latestAmendedOn) {this.latestAmendedOn = latestAmendedOn;}
    public String getLatestIssueDate() {return latestIssueDate;}
    public void setLatestIssueDate(String latestIssueDate) {this.latestIssueDate = latestIssueDate;}
    public String getUpToDateAsOf() {return upToDateAsOf;}
    public void setUpToDateAsOf(String upToDateAsOf) {this.upToDateAsOf = upToDateAsOf;}
    public boolean isReserved() {return reserved;}
    public void setReserved(boolean reserved) {this.reserved = reserved;}
    public void addPart(PartDescriptor part) { parts.add(part); }
    public void setParts(List<PartDescriptor> parts) {this.parts = parts;}
    public List<PartDescriptor> getParts() {return parts;}
}
