-- Add tracking_no column to vendor_order_assignment table
ALTER TABLE vendor_order_assignment
ADD COLUMN tracking_no VARCHAR(100) NULL;

-- Optional: Add an index if tracking_no will be frequently searched
-- CREATE INDEX IF NOT EXISTS idx_vendor_order_assignment_tracking_no ON vendor_order_assignment(tracking_no);

COMMENT ON COLUMN vendor_order_assignment.tracking_no IS 'Tracking number provided by the vendor or courier for the shipment.';
