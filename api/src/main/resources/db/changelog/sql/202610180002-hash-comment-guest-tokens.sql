-- guest_token_hash held the plain guest token; store its SHA-256 hex (same as VideoCommentServiceImpl.hashGuestToken).
UPDATE video_comments
SET guest_token_hash = encode(sha256(convert_to(guest_token_hash, 'UTF8')), 'hex')
WHERE guest_token_hash IS NOT NULL AND guest_token_hash <> '';
