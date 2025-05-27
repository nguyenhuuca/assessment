--changeset canhlabs:create_youtube_video_table
CREATE TABLE youtube_video
(
    id         SERIAL PRIMARY KEY,
    video_id   VARCHAR(20) NOT NULL UNIQUE,
    source     VARCHAR(50) DEFAULT 'chatgpt',
    created_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);