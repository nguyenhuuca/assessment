-- ===============================
-- Migration: Add video_type column to video_source
-- Date: 2025-10-19
-- Author: Ca Nguyen
-- ===============================

-- Add new column video_type (nullable for backward compatibility)
ALTER TABLE video_sources
    ADD COLUMN IF NOT EXISTS video_type VARCHAR(50);

-- Update existing rows with default value
UPDATE video_sources
SET video_type = 'general'
WHERE video_type IS NULL;

-- (Optional) Enforce NOT NULL constraint and default value
ALTER TABLE video_sources
    ALTER COLUMN video_type SET DEFAULT 'general';
