/*
 * Copyright 2008 Google Inc.
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
package de.dennisguse.opentracks.services;

import android.location.Location;

import androidx.annotation.VisibleForTesting;

import de.dennisguse.opentracks.content.WaypointCreationRequest;
import de.dennisguse.opentracks.content.sensor.SensorDataSet;

/**
 * App's service.
 * This service is the process that actually records and manages tracks.
 */
public interface ITrackRecordingService {

    /**
     * Starts gps.
     */
    void startGps();

    /**
     * Stops gps.
     */
    void stopGps();

    /**
     * Starts recording a new track.
     *
     * @return the track ID of the new track.
     */
    long startNewTrack();

    /**
     * Pauses the current recording track.
     */
    void pauseCurrentTrack();

    /**
     * Resumes the current recording track.
     */
    void resumeCurrentTrack();

    /**
     * Ends the current recording track.
     */
    void endCurrentTrack();

    /**
     * Returns true if currently recording a track.
     */
    boolean isRecording();

    /**
     * Returns true if the current recording track is paused. Returns true if not recording.
     */
    boolean isPaused();

    /**
     * Gets the current recording track ID. Returns -1 if not recording.
     */
    long getRecordingTrackId();

    /**
     * Gets the total time for the current recording track. Returns 0 if not recording.
     */
    long getTotalTime();

    /**
     * Inserts a waypoint in the current recording track.
     *
     * @param request the details of the waypoint to be inserted
     * @return the ID of the inserted waypoint
     */
    long insertWaypoint(WaypointCreationRequest request);

    /**
     * ONLY FOR TESTING
     *
     * Inserts a track point in the current recording track.
     * <p>
     * When recording a track, GPS locations are automatically inserted. This is used for
     * inserting special track points or for testing.
     *
     * @param location the track point to be inserted
     */
    @VisibleForTesting
    void insertTrackPoint(Location location);

    /**
     * Gets the current sensor data. Returns null if there is no data.
     *
     * @return SensorDataSet object.
     */
    SensorDataSet getSensorData();
}
