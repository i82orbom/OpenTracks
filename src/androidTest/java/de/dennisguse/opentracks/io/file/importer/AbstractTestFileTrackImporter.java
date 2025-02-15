/*
 * Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.dennisguse.opentracks.io.file.importer;

import android.content.ContentUris;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.SimpleTimeZone;

import de.dennisguse.opentracks.content.ContentProviderUtils;
import de.dennisguse.opentracks.content.Track;
import de.dennisguse.opentracks.content.TracksColumns;
import de.dennisguse.opentracks.content.Waypoint;
import de.dennisguse.opentracks.content.WaypointsColumns;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

/**
 * Abstract class for testing file track importers.
 *
 * @author Jimmy Shih.
 */
public abstract class AbstractTestFileTrackImporter {

    static final String TRACK_NAME_0 = "blablub";
    static final String TRACK_DESCRIPTION_0 = "s'Laebe isch koi Schlotzer";

    static final double TRACK_LATITUDE = 48.768364;
    static final double TRACK_LONGITUDE = 9.177886;
    static final double TRACK_ELEVATION = 324.0;

    static final String TRACK_TIME_0 = "2010-04-22T18:21:00Z";
    static final String TRACK_TIME_1 = "2010-04-22T18:21:50.123";
    static final String TRACK_TIME_2 = "2010-04-22T18:23:00.123";
    static final String TRACK_TIME_3 = "2010-04-22T18:24:50.123";

    static final SimpleDateFormat DATE_FORMAT_0 = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'", Locale.US);
    static final SimpleDateFormat DATE_FORMAT_1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);
    static final long TRACK_ID_0 = 1;
    static final long TRACK_POINT_ID_0 = 1;
    static final long TRACK_POINT_ID_1 = 2;
    static final long TRACK_POINT_ID_3 = 4;
    static final Uri TRACK_ID_0_URI = ContentUris.appendId(TracksColumns.CONTENT_URI.buildUpon(), TRACK_ID_0).build();
    private static final long WAYPOINT_ID_0 = 1;
    private static final Uri WAYPOINT_ID_O_URI = ContentUris.appendId(WaypointsColumns.CONTENT_URI.buildUpon(), WAYPOINT_ID_0).build();

    protected final Context context = ApplicationProvider.getApplicationContext();

    static {
        // We can't omit the timezones in the test, otherwise it'll use the local timezone and fail depending on where the test runner is.
        SimpleTimeZone utc = new SimpleTimeZone(0, "UTC");
        DATE_FORMAT_0.setTimeZone(utc);
        DATE_FORMAT_1.setTimeZone(utc);
    }

    @Mock
    public ContentProviderUtils contentProviderUtils;

    Location createLocation(int index, long time) {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(TRACK_LATITUDE + index);
        location.setLongitude(TRACK_LONGITUDE + index);
        location.setAltitude(TRACK_ELEVATION + index);
        location.setTime(time);
        return location;
    }

    /**
     * Expects the first track point to be added.
     *
     * @param location     the location
     * @param trackId      the track id
     * @param trackPointId the track point id
     */
    protected void expectFirstTrackPoint(Location location, long trackId, long trackPointId) {
        when(contentProviderUtils.bulkInsertTrackPoint(location != null ? (Location[]) any() : (Location[]) any(), eq(1), eq(trackId))).thenReturn(1);
        when(contentProviderUtils.getFirstTrackPointId(trackId)).thenReturn(trackPointId);
        when(contentProviderUtils.getLastTrackPointId(trackId)).thenReturn(trackPointId);
    }

    /**
     * Expects the track to be updated.
     *
     * @param trackCaptor the track
     * @param lastTrack   true if it is the last track in the gpx
     * @param trackId     the track id
     */
    protected void expectTrackUpdate(ArgumentCaptor<Track> trackCaptor, boolean lastTrack, long trackId) {
        contentProviderUtils.updateTrack(trackCaptor.capture());

        when(contentProviderUtils.insertWaypoint((Waypoint) any())).thenReturn(WAYPOINT_ID_O_URI);
        if (lastTrack) {
            // Return null to not add waypoints
            when(contentProviderUtils.getTrack(trackId)).thenReturn(null);
        }
    }

    protected void verifyTrack(Track track, String name, String description, long time) {
        Assert.assertEquals(name, track.getName());
        Assert.assertEquals(description, track.getDescription());
        if (time != -1L) {
            Assert.assertEquals(time, track.getTripStatistics().getStartTime());
        }
        Assert.assertNotSame(-1, track.getStartId());
        Assert.assertNotSame(-1, track.getStopId());
    }
}
