CREATE TABLE stock_levels (
  sku        VARCHAR(64) PRIMARY KEY,
  available  INT NOT NULL,
  reserved   INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Comments from previous version can be kept if desired, but the core schema is as above.
COMMENT ON TABLE stock_levels IS 'Stores current stock levels for products, including available and reserved quantities.';
COMMENT ON COLUMN stock_levels.sku IS 'Stock Keeping Unit, unique identifier for the product.';
COMMENT ON COLUMN stock_levels.available IS 'Quantity of the product currently available for sale/reservation.';
COMMENT ON COLUMN stock_levels.reserved IS 'Quantity of the product currently reserved (e.g., in open orders).';
COMMENT ON COLUMN stock_levels.updated_at IS 'Timestamp of the last update to this stock level record. Automatically updated on row change.';
