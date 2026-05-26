package com.termux.app.ghostex;

import android.content.Context;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class GhostexMachineStoreTest {

    private static final String PREFS_NAME = "ghostex_android_machines";

    private Context context;
    private GhostexMachineStore store;

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-13:53:
    Saved-machine existence is part of async recovery correctness. A deleted
    machine must remain absent so stale Check connection callbacks cannot
    recreate an account through password recovery.

    CDXC:AndroidConnectionManagement 2026-05-17-13:55:
    Automatic reconnect should start from an existing saved machine only.
    Persisted last-machine selections are cleared when they point at deleted or
    unknown ids.

    CDXC:AndroidConnectionSecurity 2026-05-17-14:57:
    The store should self-heal invalid persisted machine records and reject
    invalid programmatic saves so automatic reconnect never revives malformed
    SSH targets or credential keys.

    CDXC:AndroidConnectionManagement 2026-05-17-15:27:
    Self-healing must also clear a last-selected id that points at a scrubbed
    record, keeping the machine switcher and reconnect state aligned with the
    rewritten saved-machine list.

    CDXC:AndroidConnectionManagement 2026-05-17-21:07:
    Multi-machine settings should prevent duplicate host/user/port targets
    while still allowing separate SSH accounts or ports on the same Tailscale
    host. Older duplicate settings should self-heal on load so reconnect state
    never remains ambiguous after an upgrade.
    */
    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteSharedPreferences(PREFS_NAME);
        store = new GhostexMachineStore(context);
    }

    @Test
    public void tracksWhetherMachineStillExists() {
        GhostexMachine machine = new GhostexMachine("mac-a", "Mac A", "mac-a.tailnet.ts.net",
            "madda", 22, false, 0L);

        Assert.assertFalse(store.hasMachine(machine.id));

        store.saveMachine(machine);
        Assert.assertTrue(store.hasMachine(machine.id));

        store.deleteMachine(machine.id);
        Assert.assertFalse(store.hasMachine(machine.id));
    }

    @Test
    public void deletingSelectedMachineClearsLastMachineSelection() {
        GhostexMachine machine = new GhostexMachine("mac-a", "Mac A", "mac-a.tailnet.ts.net",
            "madda", 22, false, 0L);

        store.saveMachine(machine);
        store.setLastMachineId(machine.id);

        store.deleteMachine(machine.id);

        Assert.assertNull(store.getLastMachineId());
        Assert.assertNull(store.getLastMachine());
    }

    @Test
    public void ignoresUnknownLastMachineSelection() {
        GhostexMachine machine = new GhostexMachine("mac-a", "Mac A", "mac-a.tailnet.ts.net",
            "madda", 22, false, 0L);
        store.saveMachine(machine);

        store.setLastMachineId("deleted-machine");

        Assert.assertNull(store.getLastMachineId());
        Assert.assertEquals(machine.id, store.getLastMachine().id);
    }

    @Test
    public void loadingMachinesScrubsInvalidPersistedRecords() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("machines_json",
                "[" +
                    "{\"id\":\"mac-a\",\"name\":\"Mac A\",\"host\":\"mac-a.tailnet.ts.net\",\"username\":\"madda\",\"port\":22}," +
                    "{\"id\":\"bad/machine\",\"name\":\"Bad\",\"host\":\"bad;host\",\"username\":\"madda\",\"port\":22}" +
                    "]")
            .apply();

        Assert.assertEquals(1, store.getMachines().size());

        String rewritten = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("machines_json", "");
        Assert.assertTrue(rewritten.contains("\"id\":\"mac-a\""));
        Assert.assertFalse(rewritten.contains("bad/machine"));
    }

    @Test
    public void loadingMachinesClearsSelectionForScrubbedRecord() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("machines_json",
                "[" +
                    "{\"id\":\"mac-a\",\"name\":\"Mac A\",\"host\":\"mac-a.tailnet.ts.net\",\"username\":\"madda\",\"port\":22}," +
                    "{\"id\":\"bad/machine\",\"name\":\"Bad\",\"host\":\"bad;host\",\"username\":\"madda\",\"port\":22}" +
                    "]")
            .putString("last_machine_id", "bad/machine")
            .apply();

        Assert.assertEquals(1, store.getMachines().size());
        Assert.assertNull(store.getLastMachineId());
        Assert.assertEquals("mac-a", store.getLastMachine().id);
    }

    @Test
    public void loadingCorruptMachineJsonClearsReconnectState() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("machines_json", "{not json")
            .putString("last_machine_id", "mac-a")
            .apply();

        Assert.assertTrue(store.getMachines().isEmpty());
        Assert.assertNull(store.getLastMachineId());
    }

    @Test
    public void rejectsInvalidProgrammaticMachineSave() {
        try {
            store.saveMachine(new GhostexMachine("bad/machine", "Bad", "mac.tailnet.ts.net",
                "madda", 22, false, 0L));
            Assert.fail("Expected invalid machine save to fail.");
        } catch (IllegalArgumentException error) {
            Assert.assertEquals("Invalid Ghostex SSH machine cannot be saved: bad/machine", error.getMessage());
        }
    }

    @Test
    public void rejectsDuplicateSshTargetSaves() {
        store.saveMachine(new GhostexMachine("mac-a", "Mac A", "mac-a.tailnet.ts.net",
            "madda", 22, false, 0L));

        try {
            store.saveMachine(new GhostexMachine("mac-a-copy", "Mac Copy", "MAC-A.TAILNET.TS.NET",
                "madda", 22, false, 0L));
            Assert.fail("Expected duplicate SSH target save to fail.");
        } catch (IllegalArgumentException error) {
            Assert.assertEquals("Duplicate Ghostex SSH target cannot be saved: madda@MAC-A.TAILNET.TS.NET", error.getMessage());
        }
    }

    @Test
    public void allowsSameHostForDifferentAccountsOrPorts() {
        store.saveMachine(new GhostexMachine("mac-a", "Mac A", "mac-a.tailnet.ts.net",
            "madda", 22, false, 0L));
        store.saveMachine(new GhostexMachine("mac-a-build", "Build Account", "mac-a.tailnet.ts.net",
            "build", 22, false, 0L));
        store.saveMachine(new GhostexMachine("mac-a-alt-port", "Alt Port", "mac-a.tailnet.ts.net",
            "madda", 2222, false, 0L));

        Assert.assertEquals(3, store.getMachines().size());
    }

    @Test
    public void findsDuplicateTargetsForEditorValidation() {
        store.saveMachine(new GhostexMachine("mac-a", "Mac A", "mac-a.tailnet.ts.net",
            "madda", 22, false, 0L));

        Assert.assertEquals("mac-a", store.findDuplicateSshTargetId("MAC-A.TAILNET.TS.NET",
            "madda", 22, null));
        Assert.assertNull(store.findDuplicateSshTargetId("mac-a.tailnet.ts.net",
            "madda", 22, "mac-a"));
        Assert.assertNull(store.findDuplicateSshTargetId("mac-a.tailnet.ts.net",
            "other", 22, null));
    }

    @Test
    public void loadingMachinesScrubsDuplicateSshTargets() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("machines_json",
                "[" +
                    "{\"id\":\"mac-a\",\"name\":\"Mac A\",\"host\":\"mac-a.tailnet.ts.net\",\"username\":\"madda\",\"port\":22}," +
                    "{\"id\":\"mac-a-copy\",\"name\":\"Copy\",\"host\":\"MAC-A.TAILNET.TS.NET\",\"username\":\"madda\",\"port\":22}," +
                    "{\"id\":\"mac-a-build\",\"name\":\"Build\",\"host\":\"mac-a.tailnet.ts.net\",\"username\":\"build\",\"port\":22}" +
                    "]")
            .putString("last_machine_id", "mac-a-copy")
            .apply();

        Assert.assertEquals(2, store.getMachines().size());
        Assert.assertNull(store.getLastMachineId());

        String rewritten = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("machines_json", "");
        Assert.assertTrue(rewritten.contains("\"id\":\"mac-a\""));
        Assert.assertTrue(rewritten.contains("\"id\":\"mac-a-build\""));
        Assert.assertFalse(rewritten.contains("mac-a-copy"));
    }

    @Test
    public void persistsDrawerDisclosureStatePerMachine() {
        java.util.HashSet<String> collapsedProjects = new java.util.HashSet<>();
        collapsedProjects.add("id:project-a");
        java.util.HashSet<String> collapsedLists = new java.util.HashSet<>();
        collapsedLists.add("path:/Users/madda/dev/_active/zmux");

        store.setCollapsedProjectKeys("mac-a", collapsedProjects);
        store.setCollapsedProjectSessionListKeys("mac-a", collapsedLists);

        GhostexMachineStore restored = new GhostexMachineStore(context);
        Assert.assertTrue(restored.getCollapsedProjectKeys("mac-a").contains("id:project-a"));
        Assert.assertTrue(restored.getCollapsedProjectSessionListKeys("mac-a")
            .contains("path:/Users/madda/dev/_active/zmux"));
        Assert.assertTrue(restored.getCollapsedProjectKeys("mac-b").isEmpty());
        Assert.assertTrue(restored.getCollapsedProjectSessionListKeys("mac-b").isEmpty());
    }

}
