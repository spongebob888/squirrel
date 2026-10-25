package dev.squirrel.vpn;

/** Accesses the workspace model even when its editor window is closed. */
final class WorkspaceTestViews {
    static WorkspaceUi workspace(MainActivity activity) throws Exception {
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("workspace");
        field.setAccessible(true);
        return (WorkspaceUi) field.get(activity);
    }
}
