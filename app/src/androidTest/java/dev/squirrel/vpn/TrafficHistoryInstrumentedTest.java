package dev.squirrel.vpn;

import android.test.InstrumentationTestCase;

@SuppressWarnings("deprecation")
public final class TrafficHistoryInstrumentedTest extends InstrumentationTestCase {
    public void testRatesUseElapsedTimeAndKeepUploadAndDownloadSeparate() {
        TrafficHistory history = new TrafficHistory();
        history.record(0, 100, 200);
        assertTrue(Double.isNaN(history.uploadRate()));
        assertEquals(0, history.size());
        history.record(2_000_000_000L, 2148, 16584);
        assertEquals(1024.0, history.uploadRate(), 0.001);
        assertEquals(8192.0, history.downloadRate(), 0.001);
        history.record(3_000_000_000L, 2148, 16584);
        assertEquals(0.0, history.uploadRate(), 0.001);
        assertEquals(0.0, history.downloadRate(), 0.001);
    }

    public void testCounterResetAndInvalidCountersDiscardOldHistory() {
        TrafficHistory history = new TrafficHistory();
        history.record(0, 100, 200);
        history.record(1_000_000_000L, 200, 400);
        history.record(2_000_000_000L, 10, 500);
        assertEquals(0, history.size());
        assertTrue(Double.isNaN(history.downloadRate()));
        history.record(3_000_000_000L, 110, 700);
        assertEquals(100.0, history.uploadRate(), 0.001);
        assertEquals(200.0, history.downloadRate(), 0.001);
        history.record(4_000_000_000L, -1, 900);
        assertEquals(0, history.size());
        assertTrue(Double.isNaN(history.uploadRate()));
    }

    public void testLongGapsAndNonIncreasingTimeRequireAFreshBaseline() {
        TrafficHistory history = new TrafficHistory();
        history.record(0, 0, 0);
        history.record(1_000_000_000L, 100, 200);
        history.record(7_000_000_000L, 1000, 2000);
        assertEquals(0, history.size());
        history.record(8_000_000_000L, 1100, 2200);
        assertEquals(100.0, history.uploadRate(), 0.001);
        history.record(8_000_000_000L, 1200, 2400);
        assertEquals(0, history.size());
        history.record(9_000_000_000L, 1300, 2600);
        assertEquals(100.0, history.uploadRate(), 0.001);
    }

    public void testHistoryRetainsOnlyTheLatestThirtyMeasurements() {
        TrafficHistory history = new TrafficHistory();
        long uploaded = 0, downloaded = 0;
        history.record(0, 0, 0);
        for (int i = 1; i <= 50; i++) {
            uploaded += i;
            downloaded += i * 10;
            history.record(i * 1_000_000_000L, uploaded, downloaded);
        }
        assertEquals(30, history.size());
        assertEquals(21.0, history.uploadAt(0), 0.001);
        assertEquals(210.0, history.downloadAt(0), 0.001);
        assertEquals(50.0, history.uploadRate(), 0.001);
        assertEquals(500.0, history.downloadRate(), 0.001);
        history.clear();
        history.record(60_000_000_000L, 5000, 10000);
        assertEquals(0, history.size());
        assertTrue(Double.isNaN(history.uploadRate()));
    }
}
