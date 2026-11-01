-- Returns created before the RMA module have no RMA number; give them the same format new requests get so they can be found and managed.
UPDATE order_return SET rma_number = 'RMA-' || LPAD(CAST(id AS VARCHAR(18)), 6, '0') WHERE rma_number IS NULL;
