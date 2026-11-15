package dev.squirrel.vpn;

/** Rates derived from counters belonging to one engine run, bounded to 30 samples. */
final class TrafficHistory {
    static final int CAPACITY = 30;
    static final long MAX_GAP_NANOS = 5_000_000_000L;
    private final double[] upload = new double[CAPACITY], download = new double[CAPACITY];
    private long lastTime, lastUpload, lastDownload;
    private boolean baseline;
    private int size;

    void record(long timeNanos, long uploadBytes, long downloadBytes) {
        if (uploadBytes < 0 || downloadBytes < 0) { clear(); return; }
        long elapsed = timeNanos - lastTime;
        if (!baseline || elapsed <= 0 || elapsed > MAX_GAP_NANOS
                || uploadBytes < lastUpload || downloadBytes < lastDownload) {
            clear();
        } else {
            if (size == CAPACITY) {
                System.arraycopy(upload, 1, upload, 0, CAPACITY - 1);
                System.arraycopy(download, 1, download, 0, CAPACITY - 1);
                size--;
            }
            double seconds = elapsed / 1_000_000_000.0;
            upload[size] = (uploadBytes - lastUpload) / seconds;
            download[size++] = (downloadBytes - lastDownload) / seconds;
        }
        baseline = true;
        lastTime = timeNanos;
        lastUpload = uploadBytes;
        lastDownload = downloadBytes;
    }

    void clear() { size = 0; baseline = false; }
    int size() { return size; }
    double uploadAt(int index) { return upload[index]; }
    double downloadAt(int index) { return download[index]; }
    double uploadRate() { return size == 0 ? Double.NaN : upload[size - 1]; }
    double downloadRate() { return size == 0 ? Double.NaN : download[size - 1]; }
}
