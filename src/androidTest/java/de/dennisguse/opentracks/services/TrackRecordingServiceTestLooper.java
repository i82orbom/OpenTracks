package de.dennisguse.opentracks.services;

import android.content.ContentProvider;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ServiceTestRule;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.content.ContentProviderUtils;
import de.dennisguse.opentracks.content.CustomContentProvider;
import de.dennisguse.opentracks.content.Track;
import de.dennisguse.opentracks.content.WaypointCreationRequest;
import de.dennisguse.opentracks.stats.TripStatistics;
import de.dennisguse.opentracks.util.PreferencesUtils;

/**
 * Tests for the track recording service, which require a {@link Looper}.
 *
 * @author Bartlomiej Niechwiej
 * <p>
 * ATTENTION: This tests deletes all stored tracks in the database.
 * So, if it is executed on a real device, data might be lost.
 */
@RunWith(AndroidJUnit4.class)
public class TrackRecordingServiceTestLooper {

    @Rule
    public final ServiceTestRule mServiceRule = ServiceTestRule.withTimeout(5, TimeUnit.SECONDS);

    private Context context = ApplicationProvider.getApplicationContext();

    private ContentProviderUtils providerUtils;

    @BeforeClass
    public static void preSetUp() {
        // Prepare looper for Android's message queue
        if (Looper.myLooper() == null) Looper.prepare();
    }

    @AfterClass
    public static void finalTearDown() {
        if (Looper.myLooper() != null) Looper.myLooper().quit();
    }

    @Before
    public void setUp() {
        // Set up the mock content resolver
        ContentProvider customContentProvider = new CustomContentProvider() {
        };
        customContentProvider.attachInfo(context, null);

        providerUtils = ContentProviderUtils.Factory.get(context);

        // Let's use default values.
        SharedPreferences sharedPreferences = PreferencesUtils.getSharedPreferences(context);
        sharedPreferences.edit().clear().apply();

        // Disable auto resume by default.
        TrackRecordingServiceTest.updateAutoResumePrefs(context, PreferencesUtils.AUTO_RESUME_TRACK_CURRENT_RETRY_DEFAULT, 0);

        // Ensure that the database is empty before every test
        providerUtils.deleteAllTracks(context);
    }

    @After
    public void tearDown() throws TimeoutException {
        // Reset service (if some previous test failed)
        ITrackRecordingService service = ((ITrackRecordingService) mServiceRule.bindService(TrackRecordingServiceTest.createStartIntent(context)));
        if (service.isRecording() || service.isPaused()) {
            service.endCurrentTrack();
        }

        // Ensure that the database is empty after every test
        providerUtils.deleteAllTracks(context);
    }

    @MediumTest
    @Test
    public void testWithProperties_minRequiredAccuracy() throws TimeoutException {
        PreferencesUtils.setInt(context, R.string.recording_gps_accuracy_key, 500);
        fullRecordingSession();
    }

    @MediumTest
    @Test
    public void testWithProperties_voiceFrequencyDefault() throws TimeoutException {
        PreferencesUtils.setInt(context, R.string.voice_frequency_key, Integer.parseInt(context.getResources().getString(R.string.voice_frequency_default)));
        fullRecordingSession();
    }

    @MediumTest
    @Test
    public void testWithProperties_voiceFrequencyByDistance() throws TimeoutException {
        PreferencesUtils.setInt(context, R.string.voice_frequency_key, -1);
        fullRecordingSession();
    }

    @MediumTest
    @Test
    public void testWithProperties_voiceFrequencyByTime() throws TimeoutException {
        PreferencesUtils.setInt(context, R.string.voice_frequency_key, 1);
        fullRecordingSession();
    }


    @MediumTest
    @Test
    public void testWithProperties_maxRecordingDistanceDefault() throws TimeoutException {
        PreferencesUtils.setInt(context, R.string.max_recording_distance_key, Integer.parseInt(context.getResources().getString(R.string.max_recording_distance_default)));
        fullRecordingSession();
    }

    @MediumTest
    @Test
    public void testWithProperties_maxRecordingDistance() throws TimeoutException {
        PreferencesUtils.setInt(context, R.string.max_recording_distance_key, 50);
        fullRecordingSession();
    }

    @MediumTest
    @Test
    public void testWithProperties_minRecordingDistanceDefault() throws TimeoutException {
        int minRecordingIntervalDefault = Integer.parseInt(context.getResources().getString(R.string.min_recording_interval_default));
        PreferencesUtils.setInt(context, R.string.recording_distance_interval_key, minRecordingIntervalDefault);
        fullRecordingSession();
    }

    @MediumTest
    @Test
    public void testWithProperties_minRecordingDistance() throws TimeoutException {
        PreferencesUtils.setInt(context, R.string.recording_distance_interval_key, 2);
        fullRecordingSession();
    }

    @MediumTest
    @Test
    public void testWithProperties_splitFrequencyDefault() throws TimeoutException {
        PreferencesUtils.setInt(context, R.string.split_frequency_key, Integer.parseInt(context.getResources().getString(R.string.split_frequency_default)));
        fullRecordingSession();
    }

    @MediumTest
    @Test
    public void testWithProperties_splitFrequencyByDistance() throws TimeoutException {
        PreferencesUtils.setInt(context, R.string.split_frequency_key, -1);
        fullRecordingSession();
    }

    @MediumTest
    @Test
    public void testWithProperties_splitFrequencyByTime() throws TimeoutException {
        PreferencesUtils.setInt(context, R.string.split_frequency_key, 1);
        fullRecordingSession();
    }

    @MediumTest
    @Test
    public void testWithProperties_metricUnitsDefault() throws TimeoutException {
        PreferencesUtils.setString(context, R.string.stats_units_key, context.getString(R.string.stats_units_default));
        fullRecordingSession();
    }

    @MediumTest
    @Test
    public void testWithProperties_metricUnitsDisabled() throws TimeoutException {
        PreferencesUtils.setString(context, R.string.stats_units_key, context.getString(R.string.stats_units_imperial));
        fullRecordingSession();
    }

    @MediumTest
    @Test
    public void testWithProperties_minRecordingIntervalDefault() throws TimeoutException {
        PreferencesUtils.setInt(context, R.string.min_recording_interval_key, Integer.parseInt(context.getResources().getString(R.string.min_recording_interval_default)));
        fullRecordingSession();
    }

    @MediumTest
    @Test
    public void testWithProperties_minRecordingInterval() throws TimeoutException {
        PreferencesUtils.setInt(context, R.string.min_recording_interval_key, 2);
        fullRecordingSession();
    }

    @MediumTest
    @Test
    public void testWithProperties_minRequiredAccuracyDefault() throws TimeoutException {
        int recordingGPSAccuracyDefault = Integer.parseInt(context.getResources().getString(R.string.recording_gps_accuracy_default));
        PreferencesUtils.setInt(context, R.string.recording_gps_accuracy_key, recordingGPSAccuracyDefault);
        fullRecordingSession();
    }

    @MediumTest
    @Test
    public void testIntegration_completeRecordingSession() throws TimeoutException {
        List<Track> tracks = providerUtils.getAllTracks();
        Assert.assertTrue(tracks.isEmpty());
        fullRecordingSession();
    }

    private void fullRecordingSession() throws TimeoutException {
        ITrackRecordingService service = ((ITrackRecordingService) mServiceRule.bindService(TrackRecordingServiceTest.createStartIntent(context)));
        Assert.assertFalse(service.isRecording());

        // Start a track.
        long id = service.startNewTrack();
        Assert.assertTrue(id >= 0);
        Assert.assertTrue(service.isRecording());
        Track track = providerUtils.getTrack(id);
        Assert.assertNotNull(track);
        Assert.assertEquals(id, track.getId());
        Assert.assertEquals(id, PreferencesUtils.getRecordingTrackId(context));
        Assert.assertEquals(id, service.getRecordingTrackId());

        // Insert a few points, markers and statistics.
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 30; i++) {
            Location location = new Location("gps");
            location.setLongitude(35.0f + i / 10.0f);
            location.setLatitude(45.0f - i / 5.0f);
            location.setAccuracy(5);
            location.setSpeed(10);
            location.setTime(startTime + i * 10000);
            location.setBearing(3.0f);
            service.insertTrackPoint(location);

            if (i % 10 == 0) {
                service.insertWaypoint(WaypointCreationRequest.DEFAULT_STATISTICS);
            } else if (i % 7 == 0) {
                service.insertWaypoint(WaypointCreationRequest.DEFAULT_WAYPOINT);
            }
        }

        // Stop the track. Validate if it has correct data.
        service.endCurrentTrack();
        Assert.assertFalse(service.isRecording());
        Assert.assertEquals(-1L, service.getRecordingTrackId());
        track = providerUtils.getTrack(id);
        Assert.assertNotNull(track);
        Assert.assertEquals(id, track.getId());
        TripStatistics tripStatistics = track.getTripStatistics();
        Assert.assertNotNull(tripStatistics);
        Assert.assertTrue(tripStatistics.getStartTime() > 0);
        Assert.assertTrue(tripStatistics.getStopTime() >= tripStatistics.getStartTime());
    }
}
