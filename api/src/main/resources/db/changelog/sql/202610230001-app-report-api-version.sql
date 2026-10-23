-- Backend image tag (prod-<N>) at report time; app_version is the web build sent by the browser.
ALTER TABLE app_report
    ADD COLUMN api_version VARCHAR(40);
