package com.adrifit.backend.common.storage;

import java.io.InputStream;

/**
 * Storage abstraction.
 * Local implementation: files in uploads/ directory.
 * Future S3 implementation: private bucket, no public ACL.
 */
public interface FileStorageService {

    /**
     * Store the given content under the given key.
     * @param key     relative path, e.g. "analyses/15/24/uuid.pdf"
     * @param stream  content
     * @param size    content length in bytes
     * @param contentType MIME type
     */
    void store(String key, InputStream stream, long size, String contentType);

    /**
     * Open a stream for reading. Caller must close.
     */
    InputStream load(String key);

    /**
     * Delete the stored file. Best-effort; no exception if already absent.
     */
    void delete(String key);

    /**
     * @return "local" or "s3"
     */
    String providerName();
}
