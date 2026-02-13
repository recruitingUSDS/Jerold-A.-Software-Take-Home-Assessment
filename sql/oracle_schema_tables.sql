
-- List of titles
CREATE TABLE cfr_title (
    title_guid        RAW(16),
    title_number      VARCHAR(80),
    title_name        VARCHAR(200),
    latest_amended_on DATE,
    latest_issue_date DATE,
    up_to_date_as_of  DATE,
    reserved          NUMBER(1),
    PRIMARY KEY(title_guid)
);

-- list of agencies
CREATE TABLE cfr_agency (
    agency_guid RAW(16),
    name VARCHAR(200),
    short_name VARCHAR(200),
    display_name VARCHAR(200),
    sortable_name VARCHAR(200),
    slug VARCHAR(200),
    section_name VARCHAR(200),
    PRIMARY KEY(agency_guid)
);

-- chapters, cross referenced by agency and title
CREATE TABLE cfr_chapter (
    chapter_guid    RAW(16),
    chapter_name    VARCHAR(200),
    fk_title_guid   RAW(16),
    fk_agency_guid  RAW(16),
    chapter_content CLOB,
    PRIMARY KEY(chapter_guid)
);

-- fk_title_guid
ALTER TABLE cfr_chapter
ADD FOREIGN KEY (fk_title_guid) REFERENCES cfr_title(title_guid);

-- fk_agency_guid
ALTER TABLE cfr_chapter
ADD FOREIGN KEY (fk_agency_guid) REFERENCES cfr_agency(agency_guid);

-- sample data

-- titles
insert into cfr_title values (sys_guid(), 'title-1', 'testing title 1', to_date('05-10-1965', 'dd-mm-yyyy'), to_date('05-10-1965', 'dd-mm-yyyy'), to_date('05-10-1965', 'dd-mm-yyyy'), 0);
insert into cfr_title values (sys_guid(), 'title-2', 'testing title 2', to_date('05-10-1955', 'dd-mm-yyyy'), to_date('05-10-1955', 'dd-mm-yyyy'), to_date('05-10-1955', 'dd-mm-yyyy'), 0);

-- agencies
insert into cfr_agency values (sys_guid(), 'agency of the president', 'short name', 'display name', 'sortable name', 'short-name', 'section 33');
insert into cfr_agency values (sys_guid(), 'agency of comptrolloer', 'short name', 'display name', 'sortable name', 'short-name', 'section 99');

-- chapters
insert into cfr_chapter values (sys_guid(), 'chapter 53', hextoraw('4ABA5D3C3991D21DE063D301A8C00AFB'), hextoraw('4ABA5D3C3992D21DE063D301A8C00AFB'), 'large xml document');
insert into cfr_chapter values (sys_guid(), 'chapter 55', hextoraw('4ABA5D3C3991D21DE063D301A8C00AFB'), hextoraw('4ABA5D3C3992D21DE063D301A8C00AFB'), 'large xml document');
insert into cfr_chapter values (sys_guid(), 'chapter 99', hextoraw('4ABA5D3C3994D21DE063D301A8C00AFB'), hextoraw('4ABA5D3C3992D21DE063D301A8C00AFB'), 'large xml document');
insert into cfr_chapter values (sys_guid(), 'chapter 935', hextoraw('4ABA5D3C3999D21DE063D301A8C00AFB'), hextoraw('4ABA5D3C3992D21DE063D301A8C00AFB'), 'large xml document');


-- insert using title number
-- using sub select
insert
    INTO cfr_chapter (
        chapter_guid,
        chapter_name,
        fk_title_guid,
        fk_agency_guid,
        chapter_content
    )
    values (
        sys_guid ( ), 
        'chapter 1', 
        (select    title_guid
        from    cfr_title
        where title_number = 'title-1'),
        (select agency_guid
        from    cfr_agency
        where name = 'agency of comptrolloer'), 
        'smaller xml document');

-- all rows
select * from cfr_title;
select * from cfr_agency;
select * from cfr_chapter;

-- relevent join
select c.chapter_name, a.name, t.title_number, c.chapter_content
from cfr_chapter c, cfr_agency a, cfr_title t
where c.fk_agency_guid = a.agency_guid
and c.fk_title_guid = t.title_guid;

-- cleanup tables
delete from cfr_title;
delete from cfr_chapter;
delete from cfr_agency;

-- drop tables
drop table cfr_title;
drop table cfr_chapter;
drop table cfr_agency;
commit;
