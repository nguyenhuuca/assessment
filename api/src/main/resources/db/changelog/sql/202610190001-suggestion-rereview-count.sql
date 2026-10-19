-- How many times the submitter asked for a re-review (capped at 2 in the service).
-- DEFAULT 0 keeps old pods inserting during a rolling deploy.
ALTER TABLE video_suggestions ADD COLUMN rereview_count INTEGER NOT NULL DEFAULT 0;
