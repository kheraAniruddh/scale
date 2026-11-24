package org.scale.scale.ingest;

import org.scale.scale.ingest.error.BadUploadException;

/** Derives a safe {@code <name>.json} output file name from an uploaded file name. */
final class OutputFileNames {

    private OutputFileNames() {
    }

    static String jsonNameFor(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BadUploadException("Uploaded file must have a file name");
        }
        // Keep only the last path segment, whatever separator the client used.
        String name = originalFileName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1);

        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isEmpty() || safe.chars().allMatch(c -> c == '.')) {
            throw new BadUploadException("Uploaded file name '" + originalFileName + "' is not usable");
        }
        return safe + ".json";
    }
}
