# Take Home Assignment for USDS 
* Jerold Abramson 
* `jerry@jabramson.com`
* `jerrythea@gmail.com`

This Java program ....

## Features
- ... 
- ... 
- ... 

```java
public Class Main {
   public static void main(String[] args) {
      /** STUFF **/
   }
}
```

```

+----------------+         1..*          +--------------------+
|   Title        |---------------------->|   Chapter          |
|----------------|   titleNumber (PK)    |--------------------|
| partNnumber    |                       | chapterNumber (PK) |
| name           |                       | titleNumber (FK)   |
| latestAmendedOn|                       +--------------------+
| latestIssueDate|                              |
| upToDateAsOf   |                              |
| title          |                              |
+----------------+                              |
       ^                                        |
       |                                        |
       |                                        | 0..* (optional)
       |                                        |
       |                                        |              
       |                                        v
       |                               +-----------------+
       |                               |   Subpart       |
       |                               |-----------------|
       |                               | subpartLetter   |
       |                               | partNumber (FK) |
       |                               | titleNumber (FK)|
       |                               +-----------------+
  +--------------------+
  |      Part          |
  |--------------------|
  | Part Number        |
  | chapterNumber (FK) |
  | titleNumber (FK)   |
  +--------------------+
       |                                        |
       |                                        |       
       |                                        | 0..* (optional)
       |                                        |       
       |                                        v
       |                               +-----------------+
       |                               |   Section       |
       |                               |-----------------|
       |                               | sectionNumber   |
       |                               | partNumber (FK) |
       |                               | titleNumber (FK)|
       |                               +-----------------+
```
