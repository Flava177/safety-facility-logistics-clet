-- Who ended the journey, which the record did not say.
--
-- A closed trip stored the reason, the evidence and the end odometer, and nothing about the person
-- who supplied them. That reading is the input to the fuel consumption and odometer-jump rules, and
-- there is a large difference between a driver taking it off the dial at the vehicle and an officer
-- closing the trip from a desk three hours later on a number relayed by phone. Both were recorded
-- identically, so nothing downstream - and nobody reading the trip - could tell them apart.
--
-- The actor was always in the audit trail. That is the right place for "what happened", and the
-- wrong place for a fact you want on the record itself and queryable: answering "how many trips did
-- officers close on drivers' behalf last month" should not require replaying an audit chain.
--
-- Nullable, and deliberately not backfilled. Trips closed before this column existed have no honest
-- answer - `last_modified_by` is the last person to touch the row, which is usually but not always
-- the closer - and inventing one would put a guess where a fact belongs. NULL reads as "closed
-- before this was recorded", which is true.
ALTER TABLE fleet_logistics.trips
    ADD COLUMN closed_by VARCHAR(160);

COMMENT ON COLUMN fleet_logistics.trips.closed_by IS
    'Actor id that closed the trip. NULL for trips closed before the column existed. Compare with '
    'the assigned driver to tell a driver''s own closure from one made on their behalf.';
