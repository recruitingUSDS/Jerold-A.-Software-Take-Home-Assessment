create user usds identified by welcome1 default tablespace users temporary tablespace temp quota unlimited on users;
grant create session to usds;

GRANT CREATE SESSION,
      CREATE TABLE,
      CREATE VIEW,
      
      CREATE SEQUENCE,
      CREATE PROCEDURE,
      CREATE TRIGGER,
      CREATE SYNONYM
   TO usds;
