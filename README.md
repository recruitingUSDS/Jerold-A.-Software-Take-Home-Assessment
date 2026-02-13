# Take Home Assignment for USDS 
* Jerold Abramson 
* `jerry@jabramson.com`
* `jerrythea@gmail.com`

# First Challenge
The first challenge with this assigment was understanding the domain
in which this massive amount of data resided in.

The initial impetus was to go straight to the AI and write the code to
utiize the ```REST APIs``` to download the data.

This was a flawed approach for a variety of reasons:
* The LLM did not have current APIs available, and kept generating
code against non-existent end-points.
* With prompting, I was able to get the AI to understand the
end-points, but the AI didn't have the required intelligence to really
understand the structure and layout of the CCR.

# Incremental implementation
Since the data set is so huge, and my understanding thereof took quite a bit of time, I decided to break the project into several phases.
> ## Initial phase
>1. Understand the data.
>2. Work through the Java code to utilize the REST APIs
>3. Download the data and relationships to flat files:
>> * CSV files for 'database tables'
>> * Flat XML files for the XML content
---
> ## Database phase
> 1. Design the database
> 2. Implement the database functionality using JDBC
---
> ## User interface phase
> * Design a small web site around the database using Oracle APEX.
---
# Understanding the data
Using the existing UIs available at ecfr.gov using the 'Browse' menu
- Titles
- Agencies
- Incorporation by reference [not used]
- Recent updates


# studying and 'playing' with the current REST endpoints
The ecfr.gov has a complete set of ```develoer resources``` available
at https://www.ecfr.gov/developers/documentation/api/v1

Using the interactive API documentation allowed for a better
understanding the actual end-points that needed to be coded against
for this assigment.

# Utilized end-points
    API_BASE_URL            = "https://www.ecfr.gov/api"

## End‑points (relative to the base URL)
    ENDPOINT_AGENCIES       = "/admin/v1/agencies.json"
    ENDPOINT_TITLES         = "/versioner/v1/titles"
    ENDPOINT_PARTS          = "/versioner/v1/full"
    ENDPOINT_XML_PART       = "/versioner/v1/full"
    ENDPOINT_ANCESTRY_PARTS = "/versioner/v1/ancestry"
## A better understanding of the limited scope for the assignment
* The assignment is focused on analysis of the data using the 'Agency'.

* Given this, it became clear that only the XML content for a given agency was required.

* A better understanding the REST endpoint for 'agencies.json' uncovered this crucial data structure.

```json
{
  "agencies": [
    {
      "name": "Administrative Conference of the United States",
      "short_name": "ACUS",
      ...
      "children": [],
      "cfr_references": [
        {
          "title": 1,
          "chapter": "III"
        }
      ]
    },
    {
      "name": "Advisory Council on Historic Preservation",
      ...
      ...
      ...
      "cfr_references": [
        {
          "title": 36,
          "chapter": "VIII"
        }
      ]
    },
    {
      "name": "President's Commission on White House Fellowships",
      "short_name": "",
      "slug": "president's-commission-on-white-house-fellowships",
      ...
      "children": [],
      "cfr_references": [
        {
          "title": 1,
          "chapter": "IV",
          "part": "425"
        }
      ]
    }
  ]
}
```

* It became clear the the ```cfr_references``` was crucial.
```json
      "cfr_references": [
        {
          "title": 1,
          "chapter": "IV",
          "part": "425"
        }
      ]
```

* However, the concept of "chapter" seemed a bit foreign, since the eCFR talks mostly about sections and parts.

* I then located a key end-point to help with the chapter references:
```json
{
  "ancestors": [
    {
      "identifier": "1",
      "label": "Title 1 - General Provisions",
      "label_level": "Title 1",
      "label_description": "General Provisions",
      "reserved": false,
      "type": "title",
      "size": 330548
    },
    {
      "identifier": "I",
      "label": " Chapter I - Administrative Committee of the Federal Register",
      "label_level": " Chapter I",
      "label_description": "Administrative Committee of the Federal Register",
      "reserved": false,
      "type": "chapter",
      "size": 110792,
      "descendant_range": "1 – 49"
    }
  ]
}
```
I was able to use this end-point to validate chapter references.

It then became a somewhat easy matter to download the XML for just the relevent chapter:
```java
String url = API_BASE_URL
             + ENDPOINT_XML_PART
             + String.format("/%s/title-%s.xml?chapter=%s", date, titleDetails.getNumber(), chapterDescriptor.getChapterName());
```



# Database design
Now that the basic Java data structures are in place, mapping this to an
Entity Relationship diagram and database tables becomes much easier.

## Entities
 - title
 - chapter
 - agency
## Relationships
- An agency has zero or more chapters.
- A chapter is part of a single title.
- A title can contain multiple chapters.
- A chapter contains a large XML document
---
> 
>### *OUT OF SCOPE*
>#### *Entities*
>- part
>- section
>#### *Relationships*
>- A part has multiple sections
>- The relationship between chapters and sections is based on a complex set of hiearchy relationships that
>- can only be determined fully by parsing the XML blob directly.

---
```
+------------------------------+  1                     +---------------------------+
|   Title                      |<---------------------->|   Chapter                 |
|------------------------------|                   0..* |---------------------------|
| title_guid GUID (PK)         |                        | chapter_guid GUID (PK)    |
| title_number VARCHAR(80)     |                     /->| chapter_name VARCHAR(200) |
| title_name VARCHAR(200)      |               0..*/    | title_guid GUID (FK)      |
| latest_amended_on DATE       |                  /     | agency_guid GUID (FK)     |
| latest_issue_date DATE       |                /       | chapter_content BLOB      |
| up_to_date_as_of DATE        |              /         +---------------------------+                       
| reserved BOOLEAN             |             /                    | 1..*
+------------------------------+\          /                      |
                                 \       /                        |
                                  \    /                          |
                                   \ /                            |
                                   /\                             |
  +----------------------------+ /1  \                            |
  |      agency                |      \                           |
  |----------------------------+       \                          |
  | agency_guid GUID (PK)      |        \                         |
  | name VARCHAR(200)          |         \                        |
  | short_name VARCHAR(200)    |          \                       |
  | display_name VARCHAR(200)  |           \                      |
  | sortable_name VARCHAR(200) |            \                     |
  | slug VARCHAR(200)          |             \                    |
  | section_name VARCHAR(200)  |              |                   |
  +----------------------------+              |                   |
                                              |                   |
  +===============================================================+
  |                  DATA AVAILABLE FOR FUTURE CAPABILITIES       |
  +===============================================================+  
                                              |                 /
                                              |               /
                                              | 0..*         / 1
                                              v             v
                                       +-------------------------+
                                       |   part                  |
+------------------------+             +-------------------------+
| section                |             | part_guid GUID (PK)     |
+------------------------+ 1..*      1 | title_guid GUID (FK)    |
| section_guid GUID (PK) |<----------->| agency_guid GUID (FK)   |
| type VARCHAR(32)       |             | part_number VARCHAR(32) |
| number VARCHAR(32)     |             | identifier VARCHAR(200) |
| identifier VARCHAR(32) |             | amended_date DATE       |
| name VARCHAR(2000)     |             | issue_date DATE         |
| amended_date DATE      |             | substantive BOOLEAN     |
| issued_date DATE       |             | remove BOOLEAN          |
| substantive BOOLEAN    |             | subpart BOOLEAN         |
| removed BOOLEAN        |             +-------------------------+
| sub_part boolean       |
+------------------------+
```
