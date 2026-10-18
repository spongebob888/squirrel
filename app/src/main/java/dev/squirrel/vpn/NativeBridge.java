package dev.squirrel.vpn;

import java.io.IOException;

public final class NativeBridge {
    static { System.loadLibrary("squirrel"); }
    private NativeBridge() {}
    public static native String normalizeDatabasePaths(String config, String privateRoot) throws IOException;
    public static native String prepareConfig(String config, String privateRoot, String apiSecret) throws IOException;
    public static native String validate(String config) throws IOException;
    public static native void start(int tunFd, String config, String logPath) throws IOException;
    public static native void stop() throws IOException;
    /** Empty while running; a diagnostic when the packet engine exits. */
    public static native String status() throws IOException;
    /** Saved-profile remote control plane; independent from the VPN session. Never logs passwords. */
    public static native String adminOutbounds(String config) throws IOException;
    public static native String adminRequest(String config, String outbound, String operation,
        String username, String password) throws IOException;
}
