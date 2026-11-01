CREATE TABLE order_return_items (
    id BIGSERIAL PRIMARY KEY,
    return_id BIGINT NOT NULL REFERENCES order_return(id) ON DELETE CASCADE,
    order_item_id BIGINT NOT NULL REFERENCES order_items(id),
    product_id BIGINT NOT NULL,
    product_name VARCHAR(220),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    refund_amount NUMERIC(19,2) NOT NULL CHECK (refund_amount >= 0),
    disposition VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (disposition IN ('PENDING','RESTOCKED','DAMAGED')),
    UNIQUE (return_id, order_item_id)
);
CREATE INDEX idx_order_return_items_order_item ON order_return_items(order_item_id);
