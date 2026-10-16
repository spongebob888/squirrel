package dev.squirrel.vpn;

final class AppState {
    enum Phase { DISCONNECTED, CONNECTING, CONNECTED, STOPPING, ERROR }
    static final class Snapshot {
        final Phase phase;
        final String detail;
        Snapshot(Phase phase, String detail) { this.phase = phase; this.detail = detail; }
        boolean busy() { return phase == Phase.CONNECTING || phase == Phase.CONNECTED || phase == Phase.STOPPING; }
    }
    private static volatile Snapshot current = new Snapshot(Phase.DISCONNECTED, "Ready when you are");
    static Snapshot get() { return current; }
    static void set(Phase phase, String detail) { current = new Snapshot(phase, detail); }
    private AppState() {}
}
