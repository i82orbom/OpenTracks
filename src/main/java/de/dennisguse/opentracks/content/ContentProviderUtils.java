/*
 * Copyright 2010 Google Inc.
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

package de.dennisguse.opentracks.content;

import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;

import java.util.Iterator;
import java.util.List;

import de.dennisguse.opentracks.BuildConfig;
import de.dennisguse.opentracks.content.Waypoint.WaypointType;

/**
 * Utilities to access data from the app's content provider.
 *
 * @author Rodrigo Damazio
 */
public interface ContentProviderUtils {

    /**
     * Maximum number of waypoints that will be loaded at one time.
     */
    int MAX_LOADED_WAYPOINTS_POINTS = 10000;

    /**
     * The authority (the first part of the URI) for the app's content provider.
     */
    String AUTHORITY_PACKAGE = BuildConfig.APPLICATION_ID + ".content";

    /**
     * The base URI for the app's content provider.
     */
    String CONTENT_BASE_URI = "content://" + AUTHORITY_PACKAGE;

    /**
     * The default {@link LocationFactory} which creates a location each time.
     */
    LocationFactory DEFAULT_LOCATION_FACTORY = new LocationFactory() {
        @Override
        public Location createLocation() {
            return new SensorDataSetLocation(LocationManager.GPS_PROVIDER);
        }
    };

    /**
     * Clears a track: removes waypoints and trackpoints.
     * Only keeps the track id.
     *
     * @param trackId the track id
     */
    void clearTrack(Context context, long trackId);

    /**
     * Creates a {@link Track} from a cursor.
     *
     * @param cursor the cursor pointing to the track
     */
    Track createTrack(Cursor cursor);

    /**
     * Deletes all tracks (including waypoints and track points).
     */
    void deleteAllTracks(Context context);

    /**
     * Deletes a track.
     *
     * @param trackId the track id
     */
    void deleteTrack(Context context, long trackId);

    /**
     * Gets all the tracks.
     * If no track exists, an empty list is returned.
     * NOTE: the returned tracks do not have any track points attached.
     */
    List<Track> getAllTracks();

    /**
     * Gets the last track or null.
     */
    Track getLastTrack();

    /**
     * Gets a track by a track id or null
     * Note that the returned track doesn't have any track points attached.
     *
     * @param trackId the track id.
     */
    Track getTrack(long trackId);

    /**
     * Gets a track cursor.
     * The caller owns the returned cursor and is responsible for closing it.
     *
     * @param selection     the selection. Can be null
     * @param selectionArgs the selection arguments. Can be null
     * @param sortOrder     the sort order. Can be null
     */
    Cursor getTrackCursor(String selection, String[] selectionArgs, String sortOrder);

    /**
     * Inserts a track.
     * NOTE: This doesn't insert any track points.
     *
     * @param track the track
     * @return the content provider URI of the inserted track.
     */
    Uri insertTrack(Track track);

    /**
     * Updates a track.
     * NOTE: This doesn't update any track points.
     *
     * @param track the track
     */
    void updateTrack(Track track);

    /**
     * Creates a waypoint from a cursor.
     *
     * @param cursor the cursor pointing to the waypoint
     */
    Waypoint createWaypoint(Cursor cursor);

    /**
     * Deletes a waypoint.
     * If deleting a statistics waypoint, this will also correct the next statistics waypoint after the deleted one to reflect the  deletion.
     * The generator is used to update the next statistics waypoint.
     *
     * @param waypointId           the waypoint id
     * @param descriptionGenerator the description generator. Can be null for waypoint marker
     */
    void deleteWaypoint(Context context, long waypointId, DescriptionGenerator descriptionGenerator);

    /**
     * Gets the first waypoint id for a track.
     * The first waypoint is special as it contains the stats for the track.
     * Returns -1L if it doesn't exist.
     *
     * @param trackId the track id
     */
    long getFirstWaypointId(long trackId);

    /**
     * Gets the last waypoint for a type. Returns null if it doesn't exist.
     *
     * @param trackId      the track id
     * @param waypointType the waypoint type
     */
    Waypoint getLastWaypoint(long trackId, WaypointType waypointType);

    /**
     * Gets the next waypoint number for a type.
     * Returns -1 if not able to get the next waypoint number.
     *
     * @param trackId      the track id
     * @param waypointType the waypoint type
     */
    int getNextWaypointNumber(long trackId, WaypointType waypointType);

    /**
     * Gets a waypoint from a waypoint id.
     * Returns null if not found.
     *
     * @param waypointId the waypoint id
     */
    Waypoint getWaypoint(long waypointId);

    /**
     * Gets a waypoint cursor.
     * he caller owns the returned cursor and is responsible for closing it.
     *
     * @param selection     the selection. Can be null
     * @param selectionArgs the selection arguments. Can be null
     * @param sortOrder     the sort order. Can be null
     * @param maxWaypoints  the maximum number of waypoints to return. -1 for no
     *                      limit
     */
    Cursor getWaypointCursor(String selection, String[] selectionArgs, String sortOrder, int maxWaypoints);

    /**
     * Gets a waypoint cursor for a track.
     * The caller owns the returned cursor and is responsible for closing it.
     *
     * @param trackId       the track id
     * @param minWaypointId the minimum waypoint id. -1L to ignore
     * @param maxWaypoints  the maximum number of waypoints to return. -1 for no limit
     */
    Cursor getWaypointCursor(long trackId, long minWaypointId, int maxWaypoints);

    /**
     * Gets the number of waypoints for a track.
     *
     * @param trackId the track id
     */
    int getWaypointCount(long trackId);

    /**
     * Inserts a waypoint.
     *
     * @param waypoint the waypoint
     * @return the content provider URI of the inserted waypoint.
     */
    Uri insertWaypoint(Waypoint waypoint);

    /**
     * Updates a waypoint.
     * Returns true if successful.
     *
     * @param waypoint the waypoint
     */
    boolean updateWaypoint(Waypoint waypoint);

    /**
     * Inserts multiple track points.
     *
     * @param locations an array of locations
     * @param length    the number of locations (from the beginning of the array) to
     *                  insert, or -1 for all of them
     * @param trackId   the track id
     * @return the number of points inserted
     */
    int bulkInsertTrackPoint(Location[] locations, int length, long trackId);

    /**
     * Creates a location object from a cursor.
     *
     * @param cursor the cursor pointing to the location
     */
    Location createTrackPoint(Cursor cursor);

    /**
     * Gets the first location id for a track.
     * Returns -1L if it doesn't exist.
     *
     * @param trackId the track id
     */
    long getFirstTrackPointId(long trackId);

    /**
     * Gets the last location id for a track.
     * Returns -1L if it doesn't exist.
     *
     * @param trackId the track id
     */
    long getLastTrackPointId(long trackId);

    /**
     * Gets the track point id of a location.
     *
     * @param trackId  the track id
     * @param location the location
     * @return track point id if the location is in the track. -1L otherwise.
     */
    long getTrackPointId(long trackId, Location location);

    /**
     * Gets the first valid location for a track.
     * Returns null if it doesn't exist.
     *
     * @param trackId the track id
     */
    Location getFirstValidTrackPoint(long trackId);

    /**
     * Gets the last valid location for a track.
     * Returns null if it doesn't exist.
     *
     * @param trackId the track id
     */
    Location getLastValidTrackPoint(long trackId);

    /**
     * Creates a location cursor. The caller owns the returned cursor and is responsible for closing it.
     *
     * @param trackId           the track id
     * @param startTrackPointId the starting track point id. -1L to ignore
     * @param maxLocations      maximum number of locations to return. -1 for no limit
     * @param descending        true to sort the result in descending order (latest location first)
     */
    Cursor getTrackPointCursor(long trackId, long startTrackPointId, int maxLocations, boolean descending);

    /**
     * Creates a new read-only iterator over a given track's points.
     * It provides a lightweight way of iterating over long tracks without failing due to the underlying cursor limitations.
     * Since it's a read-only iterator, {@link Iterator#remove()} always throws {@link UnsupportedOperationException}.
     * Each call to {@link LocationIterator#next()} may advance to the next DB record, and if so, the iterator calls {@link LocationFactory#createLocation()} and populates it with information retrieved from the record.
     * When done with iteration, {@link LocationIterator#close()} must be called.
     *
     * @param trackId           the track id
     * @param startTrackPointId the starting track point id. -1L to ignore
     * @param descending        true to sort the result in descending order (latest location first)
     * @param locationFactory   the location factory
     */
    LocationIterator getTrackPointLocationIterator(long trackId, long startTrackPointId, boolean descending, LocationFactory locationFactory);

    /**
     * Inserts a track point.
     *
     * @param location the location
     * @param trackId  the track id
     * @return the content provider URI of the inserted track point
     */
    Uri insertTrackPoint(Location location, long trackId);

    /**
     * A lightweight wrapper around the original {@link Cursor} with a method to clean up.
     */
    interface LocationIterator extends Iterator<Location>, AutoCloseable {

        /**
         * Gets the most recently retrieved track point id by {@link #next()}.
         */
        long getLocationId();

        /**
         * Closes the iterator.
         */
        void close();
    }

    /**
     * A factory for creating new {@link Location}.
     */
    interface LocationFactory {

        /**
         * Creates a new {@link Location}.
         * An implementation can create new instances or reuse existing instances for optimization.
         */
        Location createLocation();
    }

    /**
     * A factory which can produce instances of {@link ContentProviderUtils}, and can be overridden for testing.
     */
    class Factory {

        /**
         * Creates an instance of {@link ContentProviderUtils}.
         *
         * @param context the context
         */
        public static ContentProviderUtils get(Context context) {
            return new ContentProviderUtilsImpl(context.getContentResolver());
        }
    }
}
