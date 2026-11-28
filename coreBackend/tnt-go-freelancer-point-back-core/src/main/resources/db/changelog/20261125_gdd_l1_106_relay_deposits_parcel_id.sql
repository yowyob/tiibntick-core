-- gdd-l1-106: a relay deposit made by the delivery flow was keyed on the delivery NEED id (packetId
-- received a needId). It is re-keyed on the parcel id the need registered (delivery_needs.packet_id), the
-- id that follows the goods from the need to the hub. A deposit whose need has no parcel keeps its need id
-- (the retrieval path still finds it by need). Re-runnable: a re-keyed deposit no longer matches a need id.
UPDATE relay_deposits rd
   SET packet_id = n.packet_id
  FROM delivery_needs n
 WHERE rd.packet_id = n.id
   AND n.packet_id IS NOT NULL;
