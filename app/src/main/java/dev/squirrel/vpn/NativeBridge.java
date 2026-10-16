package dev.squirrel.vpn;

import java.io.IOException;

public final class NativeBridge {
    static { System.loadLibrary("squirrel"); }
    private NativeBridge() {}
    public static native String normalizeDatabasePaths(String config, String privateRoot) throws IOException;
    public static native String validate(String config) throws IOException;
    public static native void start(int tunFd, String config, String logPath) throws IOException;
    public static native void stop() throws IOException;
    /** Empty while running; a diagnostic when the packet engine exits. */
    public static native String status() throws IOException;
}
