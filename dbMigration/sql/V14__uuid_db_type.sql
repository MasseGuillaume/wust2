CREATE OR REPLACE FUNCTION base36_decode(IN base36 varchar) RETURNS bigint AS $$
DECLARE
    a char[];
    ret bigint;
    i int;
    val int;
    chars varchar;
BEGIN
    chars := '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';

    FOR i IN REVERSE char_length(base36)..1 LOOP
        a := a || substring(upper(base36) FROM i FOR 1)::char;
    END LOOP;
    i := 0;
    ret := 0;
    WHILE i < (array_length(a,1)) LOOP
        val := position(a[i+1] IN chars)-1;
        ret := ret + (val * (36 ^ i));
        i := i + 1;
    END LOOP;

    RETURN ret;
END;
$$ LANGUAGE 'plpgsql' IMMUTABLE;

CREATE OR REPLACE FUNCTION base36_to_base16(v_input CHARACTER VARYING(36)) RETURNS CHARACTER VARYING(32) AS $$
DECLARE
    v_uuid CHARACTER VARYING(32) DEFAULT NULL;
    v_base36 CHARACTER VARYING(24) DEFAULT NULL;
BEGIN
    IF char_length(v_input) <> 25 THEN
        RETURN lpad(to_hex(CAST (v_input as INTEGER)), 32, '0');
    END IF;

    v_base36 := right(v_input, -1)::varchar(24);
    v_uuid := lpad(to_hex(base36_decode(right(v_base36, -12))), 16, '0') || lpad(to_hex(base36_decode(left(v_base36, -12))), 16, '0');
    RETURN v_uuid;
END;
$$ language plpgsql;

ALTER TABLE connection
    drop constraint connection_sourceid_fkey,
    drop constraint connection_targetid_fkey,
    drop constraint connection_pkey,
    drop constraint selfloop;
ALTER TABLE containment
    drop constraint containment_parentid_fkey,
    drop constraint containment_childid_fkey,
    drop constraint containment_pkey,
    drop constraint selfloop;
ALTER TABLE ownership
    drop constraint ownership_postid_fkey;

DROP VIEW post;

UPDATE rawpost SET id = base36_to_base16(id);
ALTER TABLE rawpost
    ALTER COLUMN id TYPE UUID USING id::UUID;
UPDATE connection SET sourceId = base36_to_base16(sourceId);
UPDATE connection SET targetId = base36_to_base16(targetId);
ALTER TABLE connection
    ALTER COLUMN sourceId TYPE UUID USING sourceId::UUID,
    ALTER COLUMN targetId TYPE UUID USING targetId::UUID;
UPDATE containment SET parentId = base36_to_base16(parentId);
UPDATE containment SET childId = base36_to_base16(childId);
ALTER TABLE containment
    ALTER COLUMN parentId TYPE UUID USING parentId::UUID,
    ALTER COLUMN childId TYPE UUID USING childId::UUID;
UPDATE ownership SET postId = base36_to_base16(postId);
ALTER TABLE ownership
    ALTER COLUMN postId TYPE UUID USING postId::UUID;

ALTER TABLE connection
    ADD constraint connection_sourceid_fkey foreign key (sourceId) references rawpost (id) on delete cascade,
    ADD constraint connection_targetid_fkey foreign key (targetId) references rawpost (id) on delete cascade,
    ADD constraint connection_pkey primary key (sourceId, targetId),
    ADD constraint selfloop check (sourceId <> targetId);
ALTER TABLE containment
    ADD constraint containment_parentid_fkey foreign key (parentId) references rawpost (id) on delete cascade,
    ADD constraint containment_childid_fkey foreign key (childId) references rawpost (id) on delete cascade,
    ADD constraint containment_pkey primary key (parentId, childId),
    ADD constraint selfloop check (parentId <> childId);
ALTER TABLE ownership
    ADD constraint ownership_postid_fkey foreign key (postId) references rawpost (id) on delete cascade;

CREATE VIEW post AS SELECT id,title FROM rawpost WHERE isdeleted = false;

DROP FUNCTION base36_decode(varchar);
DROP FUNCTION base36_to_base16(CHARACTER VARYING(36));
