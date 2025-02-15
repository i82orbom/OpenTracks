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

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.util.Log;

import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.content.ContentProviderUtils;
import de.dennisguse.opentracks.content.ContentProviderUtils.LocationIterator;
import de.dennisguse.opentracks.content.DescriptionGeneratorImpl;
import de.dennisguse.opentracks.content.Track;
import de.dennisguse.opentracks.content.Waypoint;
import de.dennisguse.opentracks.content.Waypoint.WaypointType;
import de.dennisguse.opentracks.services.TrackRecordingService;
import de.dennisguse.opentracks.stats.TripStatistics;
import de.dennisguse.opentracks.stats.TripStatisticsUpdater;
import de.dennisguse.opentracks.util.FileUtils;
import de.dennisguse.opentracks.util.LocationUtils;
import de.dennisguse.opentracks.util.PreferencesUtils;
import de.dennisguse.opentracks.util.StringUtils;
import de.dennisguse.opentracks.util.TrackIconUtils;
import de.dennisguse.opentracks.util.UnitConversions;

/**
 * Abstract class for file track importers.
 *
 * @author Jimmy Shih
 */
abstract class AbstractFileTrackImporter extends DefaultHandler implements TrackImporter {

    private static final String TAG = AbstractFileTrackImporter.class.getSimpleName();

    // The maximum number of buffered locations for bulk-insertion
    private static final int MAX_BUFFERED_LOCATIONS = 512;
    private final Context context;
    private final long importTrackId;
    private final ContentProviderUtils contentProviderUtils;
    private final int recordingDistanceInterval;

    private final List<Long> trackIds;
    private final List<Waypoint> waypoints;

    // The current element content
    //TODO Should be made private and getter be used by child classes.
    protected String content;
    protected String name;
    protected String description;
    protected String category;
    protected String latitude;
    protected String longitude;
    protected String altitude;
    protected String time;
    protected String waypointType;
    protected String photoUrl;

    // The current track data
    private TrackData trackData;

    // The SAX locator to get the current line information
    private Locator locator;

    /**
     * Constructor.
     *
     * @param context       the context
     * @param importTrackId the track id to import to. -1L to import to a new track.
     */
    AbstractFileTrackImporter(Context context, long importTrackId, ContentProviderUtils contentProviderUtils) {
        this.context = context;
        this.importTrackId = importTrackId;
        this.contentProviderUtils = contentProviderUtils;
        this.recordingDistanceInterval = PreferencesUtils.getRecordingDistanceInterval(context);
        trackIds = new ArrayList<>();
        waypoints = new ArrayList<>();
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        String newContent = new String(ch, start, length);
        if (content == null) {
            content = newContent;
        } else {
            // In 99% of the cases, a single call to this method will be made for each sequence of characters we're interested in, so we'll rarely be concatenating strings, thus not justifying the use of a StringBuilder.
            content += newContent;
        }
    }

    @Override
    public long importFile(InputStream inputStream) {
        try {
            SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
            long start = System.currentTimeMillis();

            saxParser.parse(inputStream, this);
            Log.d(TAG, "Total import time: " + (System.currentTimeMillis() - start) + "ms");
            if (trackIds.size() != 1) {
                Log.d(TAG, trackIds.size() + " tracks imported");
                cleanImport();
                return -1L;
            }
            return trackIds.get(0);
        } catch (IOException | SAXException | ParserConfigurationException e) {
            Log.e(TAG, "Unable to import file", e);
            cleanImport();
            return -1L;
        }
    }

    /**
     * On file end.
     */
    protected void onFileEnd() {
        // Add waypoints to the last imported track
        int size = trackIds.size();
        if (size == 0) {
            return;
        }
        long trackId = trackIds.get(size - 1);
        Track track = contentProviderUtils.getTrack(trackId);
        if (track == null) {
            return;
        }

        int waypointPosition = -1;
        Waypoint waypoint = null;
        Location location = null;
        TripStatisticsUpdater trackTripStatisticstrackUpdater = new TripStatisticsUpdater(track.getTripStatistics().getStartTime());
        TripStatisticsUpdater markerTripStatisticsUpdater = new TripStatisticsUpdater(track.getTripStatistics().getStartTime());

        try (LocationIterator locationIterator = contentProviderUtils.getTrackPointLocationIterator(track.getId(), -1L, false, ContentProviderUtils.DEFAULT_LOCATION_FACTORY)) {

            while (true) {
                if (waypoint == null) {
                    waypointPosition++;
                    waypoint = waypointPosition < waypoints.size() ? waypoints.get(waypointPosition) : null;
                    if (waypoint == null) {
                        // No more waypoints
                        return;
                    }
                }
                if (location == null) {
                    if (!locationIterator.hasNext()) {
                        // No more track points. Ignore the rest of the waypoints.
                        return;
                    }
                    location = locationIterator.next();
                    trackTripStatisticstrackUpdater.addLocation(location, recordingDistanceInterval);
                    markerTripStatisticsUpdater.addLocation(location, recordingDistanceInterval);
                }
                if (waypoint.getLocation().getTime() > location.getTime()) {
                    location = null;
                } else if (waypoint.getLocation().getTime() < location.getTime()) {
                    waypoint = null;
                } else {
                    // The waypoint location time matches the track point time
                    if (!LocationUtils.isValidLocation(location)) {
                        // Invalid location, load the next location
                        location = null;
                        continue;
                    }

                    // Valid location
                    if (location.getLatitude() == waypoint.getLocation().getLatitude() && location.getLongitude() == waypoint.getLocation().getLongitude()) {

                        // Get tripStatistics, description, and icon
                        TripStatistics tripStatistics;
                        String waypointDescription;
                        String icon;
                        if (waypoint.getType() == WaypointType.STATISTICS) {
                            tripStatistics = markerTripStatisticsUpdater.getTripStatistics();
                            markerTripStatisticsUpdater = new TripStatisticsUpdater(location.getTime());
                            waypointDescription = new DescriptionGeneratorImpl(context).generateWaypointDescription(tripStatistics);
                            icon = context.getString(R.string.marker_statistics_icon_url);
                        } else {
                            tripStatistics = null;
                            waypointDescription = waypoint.getDescription();
                            icon = context.getString(R.string.marker_waypoint_icon_url);
                        }

                        // Get length and duration
                        double length = trackTripStatisticstrackUpdater.getTripStatistics().getTotalDistance();
                        long duration = trackTripStatisticstrackUpdater.getTripStatistics().getTotalTime();

                        // Insert waypoint
                        Waypoint newWaypoint = new Waypoint(waypoint.getName(), waypointDescription, waypoint.getCategory(), icon, track.getId(), waypoint.getType(), length, duration, -1L, -1L, location, tripStatistics, waypoint.getPhotoUrl());
                        contentProviderUtils.insertWaypoint(newWaypoint);
                    }

                    // Load the next waypoint
                    waypoint = null;
                }
            }
        }
    }

    /**
     * On track start.
     */
    protected void onTrackStart() throws SAXException {
        trackData = new TrackData();
        long trackId;
        if (importTrackId == -1L) {
            Uri uri = contentProviderUtils.insertTrack(trackData.track);
            trackId = Long.parseLong(uri.getLastPathSegment());
        } else {
            if (trackIds.size() > 0) {
                throw new SAXException(createErrorMessage("Cannot import more than one track to an existing track " + importTrackId));
            }
            trackId = importTrackId;
            contentProviderUtils.clearTrack(context, trackId);
        }
        trackIds.add(trackId);
        trackData.track.setId(trackId);
    }

    /**
     * On track end.
     */
    protected void onTrackEnd() {
        flushLocations(trackData);
        if (name != null) {
            trackData.track.setName(name);
        }
        if (description != null) {
            trackData.track.setDescription(description);
        }
        if (category != null) {
            trackData.track.setCategory(category);
            trackData.track.setIcon(TrackIconUtils.getIconValue(context, category));
        }
        if (trackData.tripStatisticsUpdater == null) {
            trackData.tripStatisticsUpdater = new TripStatisticsUpdater(trackData.importTime);
            trackData.tripStatisticsUpdater.updateTime(trackData.importTime);
        }
        trackData.track.setTripStatistics(trackData.tripStatisticsUpdater.getTripStatistics());
        trackData.track.setNumberOfPoints(trackData.numberOfLocations);
        contentProviderUtils.updateTrack(trackData.track);
        insertFirstWaypoint(trackData.track);
    }

    /**
     * On track segment start.
     */
    protected void onTrackSegmentStart() {
        trackData.numberOfSegments++;

        //If not the first segment, add a pause separator if there is at least one location in the last segment.
        if (trackData.numberOfSegments > 1 && trackData.lastLocationInCurrentSegment != null) {
            insertLocation(createLocation(TrackRecordingService.PAUSE_LATITUDE, 0.0, 0.0, trackData.lastLocationInCurrentSegment.getTime()));
        }
        trackData.lastLocationInCurrentSegment = null;
    }

    /**
     * Adds a waypoint.
     *
     * @param type the waypoint type
     */
    protected void addWaypoint(WaypointType type) throws SAXException {
        // Waypoint must have a time, else cannot match to the track points
        if (time == null) {
            return;
        }

        Waypoint waypoint = new Waypoint();
        Location location = createLocation();

        if (!LocationUtils.isValidLocation(location)) {
            throw new SAXException(createErrorMessage("Invalid location detected: " + location));
        }
        waypoint.setLocation(location);

        if (name != null) {
            waypoint.setName(name);
        }
        if (description != null) {
            waypoint.setDescription(description);
        }
        if (category != null) {
            waypoint.setCategory(category);
        }
        waypoint.setType(type);

        if (photoUrl != null) {
            waypoint.setPhotoUrl(photoUrl);
        }
        waypoints.add(waypoint);
    }

    /**
     * Gets a track point.
     */
    protected Location getTrackPoint() throws SAXException {
        Location location = createLocation();

        // Calculate derived attributes from the previous point
        if (trackData.lastLocationInCurrentSegment != null && trackData.lastLocationInCurrentSegment.getTime() != 0) {
            long timeDifference = location.getTime() - trackData.lastLocationInCurrentSegment.getTime();

            // Check for negative time change
            if (timeDifference <= 0) {
                Log.w(TAG, "Time difference not postive.");
            } else {

                /*
                 * We don't have a speed and bearing in GPX, make something up from the last two points.
                 * GPS points tend to have some inherent imprecision, speed and bearing will likely be off, so the statistics for things like max speed will also be off.
                 */
                double duration = timeDifference * UnitConversions.MS_TO_S;
                double speed = trackData.lastLocationInCurrentSegment.distanceTo(location) / duration;
                location.setSpeed((float) speed);
            }
            location.setBearing(trackData.lastLocationInCurrentSegment.bearingTo(location));
        }

        if (!LocationUtils.isValidLocation(location)) {
            throw new SAXException(createErrorMessage("Invalid location detected: " + location));
        }

        if (trackData.numberOfSegments > 1 && trackData.lastLocationInCurrentSegment == null) {
            // If not the first segment, add a resume separator before adding the first location.
            insertLocation(
                    createLocation(TrackRecordingService.RESUME_LATITUDE, 0.0, 0.0, location.getTime()));
        }
        trackData.lastLocationInCurrentSegment = location;
        return location;
    }

    /**
     * Inserts a track point.
     *
     * @param location the location
     */
    protected void insertTrackPoint(Location location) {
        insertLocation(location);

        if (trackData.track.getStartId() == -1L) {
            // Flush the location to set the track start id and the track end id
            flushLocations(trackData);
        }
    }

    /**
     * Creates an error message.
     *
     * @param message the message
     */
    protected String createErrorMessage(String message) {
        return String.format(Locale.US, "Parsing error at line: %d column: %d. %s", locator.getLineNumber(), locator.getColumnNumber(), message);
    }

    /**
     * Gets the photo url for a file.
     *
     * @param fileName the file name
     */
    protected String getPhotoUrl(String fileName) {
        if (importTrackId == -1L) {
            return null;
        }
        File dir = FileUtils.getPhotoDir(importTrackId);
        File file = new File(dir, fileName);
        return Uri.fromFile(file).toString();
    }

    /**
     * Creates a location.
     */
    private Location createLocation() throws SAXException {
        if (latitude == null || longitude == null) {
            return null;
        }
        double latitudeValue;
        double longitudeValue;
        try {
            latitudeValue = Double.parseDouble(latitude);
            longitudeValue = Double.parseDouble(longitude);
        } catch (NumberFormatException e) {
            throw new SAXException(createErrorMessage(String.format(Locale.US, "Unable to parse latitude longitude: %s %s", latitude, longitude)), e);
        }
        Double altitudeValue = null;
        if (altitude != null) {
            try {
                altitudeValue = Double.parseDouble(altitude);
            } catch (NumberFormatException e) {
                throw new SAXException(createErrorMessage(String.format(Locale.US, "Unable to parse altitude: %s", altitude)), e);
            }
        }

        long timeValue;
        if (time == null) {
            timeValue = trackData.importTime;
        } else {
            try {
                timeValue = StringUtils.getTime(time);
            } catch (IllegalArgumentException e) {
                throw new SAXException(createErrorMessage(String.format(Locale.US, "Unable to parse time: %s", time)), e);
            }
        }
        return createLocation(latitudeValue, longitudeValue, altitudeValue, timeValue);
    }

    /**
     * Creates a location.
     *
     * @param latitudeValue  the latitude value
     * @param longitudeValue the longitude value
     * @param altitudeValue  the altitude value
     * @param timeValue      the time value
     */
    private Location createLocation(double latitudeValue, double longitudeValue, Double altitudeValue, long timeValue) {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(latitudeValue);
        location.setLongitude(longitudeValue);
        if (altitudeValue != null) {
            location.setAltitude(altitudeValue);
        } else {
            location.removeAltitude();
        }
        location.setTime(timeValue);
        location.removeAccuracy();
        location.removeBearing();
        location.removeSpeed();
        return location;
    }

    /**
     * Inserts a location.
     *
     * @param location the location
     */
    private void insertLocation(Location location) {
        if (trackData.tripStatisticsUpdater == null) {
            trackData.tripStatisticsUpdater = new TripStatisticsUpdater(location.getTime() != -1L ? location.getTime() : trackData.importTime);
        }
        trackData.tripStatisticsUpdater.addLocation(location, recordingDistanceInterval);

        trackData.bufferedLocations[trackData.numBufferedLocations] = location;
        trackData.numBufferedLocations++;
        trackData.numberOfLocations++;

        if (trackData.numBufferedLocations >= MAX_BUFFERED_LOCATIONS) {
            flushLocations(trackData);
        }
    }

    /**
     * Flushes the locations to the database.
     *
     * @param data the track data
     */
    private void flushLocations(TrackData data) {
        if (data.numBufferedLocations <= 0) {
            return;
        }
        contentProviderUtils.bulkInsertTrackPoint(data.bufferedLocations, data.numBufferedLocations, data.track.getId());
        data.numBufferedLocations = 0;
        if (data.track.getStartId() == -1L) {
            data.track.setStartId(contentProviderUtils.getFirstTrackPointId(data.track.getId()));
        }
        data.track.setStopId(contentProviderUtils.getLastTrackPointId(data.track.getId()));
    }

    /**
     * Inserts the first waypoint, the track statistics waypoint.
     *
     * @param track the track
     */
    private void insertFirstWaypoint(Track track) {
        String waypointName = context.getString(R.string.marker_split_name_format, 0);
        String waypointCategory = "";
        TripStatisticsUpdater updater = new TripStatisticsUpdater(track.getTripStatistics().getStartTime());
        TripStatistics tripStatistics = updater.getTripStatistics();
        String waypointDescription = new DescriptionGeneratorImpl(context).generateWaypointDescription(tripStatistics);
        String icon = context.getString(R.string.marker_statistics_icon_url);
        double length = 0.0;
        long duration = 0L;
        Location waypointLocation = new Location("");
        waypointLocation.setLatitude(100);
        waypointLocation.setLongitude(180);
        Waypoint waypoint = new Waypoint(waypointName, waypointDescription, waypointCategory, icon, track.getId(), WaypointType.STATISTICS, length, duration, -1L, -1L, waypointLocation, tripStatistics, "");
        contentProviderUtils.insertWaypoint(waypoint);
    }

    /**
     * Cleans up import.
     */
    private void cleanImport() {
        for (long trackId : trackIds) {
            contentProviderUtils.deleteTrack(context, trackId);
        }
    }

    /**
     * Data for the current track.
     *
     * @author Jimmy Shih
     */
    private class TrackData {
        // The current track
        Track track = new Track();

        // The number of segments processed for the current track
        int numberOfSegments = 0;

        // The last location in the current segment; Null if the current segment doesn't have a last location
        Location lastLocationInCurrentSegment;

        // The number of locations processed for the current track
        int numberOfLocations = 0;

        // The trip statistics updater for the current track
        TripStatisticsUpdater tripStatisticsUpdater;

        // The import time of the track.
        long importTime = System.currentTimeMillis();

        // The buffered locations
        Location[] bufferedLocations = new Location[MAX_BUFFERED_LOCATIONS];

        // The number of buffered locations
        int numBufferedLocations = 0;
    }
}
