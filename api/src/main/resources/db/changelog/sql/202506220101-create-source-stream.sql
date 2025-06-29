-- Create table to store video source metadata
CREATE TABLE video_sources (
                               id SERIAL PRIMARY KEY, -- Unique identifier for each video source

                               video_id BIGINT NOT NULL, -- Foreign key to the videos table (if exists)

                               source_type VARCHAR(50) NOT NULL,
    -- The type of video source, e.g., 'google_drive', 'youtube', 's3', 'internal'

                               source_id VARCHAR(255) NOT NULL,
    -- The unique ID used by the external platform (e.g., Google Drive file ID, YouTube video ID)

                               source_url TEXT,
    -- Full video URL for platforms like YouTube or S3 (can be NULL for some sources)

                               credentials_ref VARCHAR(255),
    -- Optional reference to credentials (e.g., key file name, ID in vault)

                               created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- Timestamp when the record was created

                               updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    -- Timestamp when the record was last updated
);