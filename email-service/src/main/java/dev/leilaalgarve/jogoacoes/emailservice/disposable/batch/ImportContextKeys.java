package dev.leilaalgarve.jogoacoes.emailservice.disposable.batch;

/** Names shared by the steps of the import job: job parameters and job execution context keys. */
public final class ImportContextKeys {

    public static final String JOB_NAME = "importDisposableDomainsJob";

    /** Job parameter: the repository this execution imports. */
    public static final String SOURCE_ID = "sourceId";
    /** Job parameter: when it was asked for -- makes every request a new job instance. */
    public static final String REQUESTED_AT = "requestedAt";

    public static final String DOWNLOAD_STEP = "downloadStep";
    public static final String IMPORT_STEP = "importStep";
    public static final String PRUNE_STEP = "pruneStep";

    /** Exit code of downloadStep when the list is the one that updated the database last. */
    public static final String UNCHANGED = "UNCHANGED";

    static final String LIST_FILE = "listFile";
    static final String SHA256 = "sha256";
    static final String DOMAINS_BEFORE = "domainsBefore";
    public static final String ADDED = "added";
    public static final String REMOVED = "removed";

    private ImportContextKeys() {
    }
}
