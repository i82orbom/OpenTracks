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
package de.dennisguse.opentracks.io.file.exporter;

import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.content.ContentProviderUtils;
import de.dennisguse.opentracks.content.DescriptionGenerator;
import de.dennisguse.opentracks.content.DescriptionGeneratorImpl;
import de.dennisguse.opentracks.content.SensorDataSetLocation;
import de.dennisguse.opentracks.content.Track;
import de.dennisguse.opentracks.content.Waypoint;
import de.dennisguse.opentracks.content.Waypoint.WaypointType;
import de.dennisguse.opentracks.content.sensor.SensorDataSet;
import de.dennisguse.opentracks.util.StringUtils;

/**
 * Write track as KML to a file.
 *
 * @author Leif Hendrik Wilden
 */
public class KmlTrackWriter implements TrackWriter {

    private static final String WAYPOINT_STYLE = "waypoint";
    private static final String STATISTICS_STYLE = "statistics";
    private static final String START_STYLE = "start";
    private static final String END_STYLE = "end";
    private static final String TRACK_STYLE = "track";
    private static final String SCHEMA_ID = "schema";

    private static final String SENSOR_TYPE_CADENCE = "cadence";
    private static final String SENSOR_TYPE_HEART_RATE = "heart_rate";
    private static final String SENSOR_TYPE_POWER = "power";

    private static final String WAYPOINT_ICON = "http://maps.google.com/mapfiles/kml/pushpin/blue-pushpin.png";
    private static final String STATISTICS_ICON = "http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png";
    private static final String START_ICON = "http://maps.google.com/mapfiles/kml/paddle/grn-circle.png";
    private static final String END_ICON = "http://maps.google.com/mapfiles/kml/paddle/red-circle.png";
    private static final String TRACK_ICON = "http://earth.google.com/images/kml-icons/track-directional/track-0.png";

    private final Context context;
    private final boolean hasMultipleTracks;
    private final boolean exportPhotos;
    private final boolean exportTrackDetail;
    private final boolean exportSensorData;
    private final DescriptionGenerator descriptionGenerator;
    private final ContentProviderUtils contentProviderUtils;

    private PrintWriter printWriter;
    private List<Float> powerList = new ArrayList<>();
    private List<Float> cadenceList = new ArrayList<>();
    private List<Float> heartRateList = new ArrayList<>();

    private Location startLocation;

    /**
     * @param context            the context
     * @param hasMultipleTracks  should encode multiple tracks into one file?
     * @param exportTrackDetail should detailed information about the track be exported (e.g., title, description, waypoints, timing)?
     * @param exportSensorData   should {@link SensorDataSet} be exported?
     * @param exportPhotos       should pictures be exported (if true: exports to KMZ)?
     */
    public KmlTrackWriter(Context context, boolean hasMultipleTracks, boolean exportTrackDetail, boolean exportSensorData, boolean exportPhotos) {
        this.context = context;
        this.hasMultipleTracks = hasMultipleTracks;
        this.exportTrackDetail = exportTrackDetail;
        this.exportSensorData = exportSensorData;
        this.exportPhotos = exportPhotos;
        this.descriptionGenerator = new DescriptionGeneratorImpl(context);
        this.contentProviderUtils = ContentProviderUtils.Factory.get(context);
    }

    @Override
    public void prepare(OutputStream outputStream) {
        this.printWriter = new PrintWriter(outputStream);
    }

    @Override
    public void close() {
        if (printWriter != null) {
            printWriter.flush();
            printWriter = null;
        }
    }

    @Override
    public void writeHeader(Track[] tracks) {
        if (printWriter != null) {
            printWriter.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            printWriter.println("<kml xmlns=\"http://www.opengis.net/kml/2.2\"");
            printWriter.println("xmlns:gx=\"http://www.google.com/kml/ext/2.2\"");
            printWriter.println("xmlns:atom=\"http://www.w3.org/2005/Atom\">");
            printWriter.println("<Document>");
            printWriter.println("<open>1</open>");
            printWriter.println("<visibility>1</visibility>");

            if (exportTrackDetail) {
                Track track = tracks[0];
                printWriter.println("<name>" + StringUtils.formatCData(track.getName()) + "</name>");
                printWriter.println("<atom:author><atom:name>" + StringUtils.formatCData(context.getString(R.string.app_name)) + "</atom:name></atom:author>");
            }

            writeTrackStyle();
            writePlacemarkerStyle(START_STYLE, START_ICON, 32, 1);
            writePlacemarkerStyle(END_STYLE, END_ICON, 32, 1);
            writePlacemarkerStyle(STATISTICS_STYLE, STATISTICS_ICON, 20, 2);
            writePlacemarkerStyle(WAYPOINT_STYLE, WAYPOINT_ICON, 20, 2);
            printWriter.println("<Schema id=\"" + SCHEMA_ID + "\">");

            if (exportSensorData) {
                writeSensorStyle(SENSOR_TYPE_POWER, context.getString(R.string.description_sensor_power));
                writeSensorStyle(SENSOR_TYPE_CADENCE, context.getString(R.string.description_sensor_cadence));
                writeSensorStyle(SENSOR_TYPE_HEART_RATE, context.getString(R.string.description_sensor_heart_rate));
            }
            printWriter.println("</Schema>");
        }
    }

    @Override
    public void writeFooter() {
        if (printWriter != null) {
            printWriter.println("</Document>");
            printWriter.println("</kml>");
        }
    }

    @Override
    public void writeBeginWaypoints(Track track) {
        if (printWriter != null) {
            printWriter.println("<Folder>");
            if (exportTrackDetail) {
                printWriter.println("<name>" + StringUtils.formatCData(context.getString(R.string.track_markers, track.getName())) + "</name>");
            }
            printWriter.println("<open>1</open>");
        }
    }

    @Override
    public void writeEndWaypoints() {
        if (printWriter != null) {
            printWriter.println("</Folder>");
        }
    }

    @Override
    public void writeWaypoint(Waypoint waypoint) {
        if (printWriter != null && exportTrackDetail) {
            String styleName = waypoint.getType() == WaypointType.STATISTICS ? STATISTICS_STYLE : WAYPOINT_STYLE;

            if (waypoint.hasPhoto() && exportPhotos) {
                float heading = getHeading(waypoint.getTrackId(), waypoint.getLocation());
                writePhotoOverlay(waypoint.getName(), waypoint.getCategory(), waypoint.getDescription(), styleName, waypoint.getLocation(), waypoint.getPhotoUrl(), heading);
            } else {
                writePlacemark(waypoint.getName(), waypoint.getCategory(), waypoint.getDescription(), styleName, waypoint.getLocation());
            }
        }
    }

    @Override
    public void writeBeginTracks() {
        if (printWriter != null && hasMultipleTracks) {
            printWriter.println("<Folder id=tour>");
            printWriter.println("<name>" + context.getString(R.string.generic_tracks) + "</name>");
            printWriter.println("<open>1</open>");
        }
    }

    @Override
    public void writeEndTracks() {
        if (printWriter != null && hasMultipleTracks) {
            printWriter.println("</Folder>");
        }
    }

    @Override
    public void writeBeginTrack(Track track, Location startLocation) {
        this.startLocation = startLocation;
        if (printWriter != null) {
            String name = context.getString(R.string.marker_label_start, track.getName());
            writePlacemark(name, "", "", START_STYLE, startLocation);
            printWriter.println("<Placemark>");

            if (exportTrackDetail) {
                printWriter.println("<name>" + StringUtils.formatCData(track.getName()) + "</name>");
                printWriter.println("<description>" + StringUtils.formatCData(track.getDescription()) + "</description>");
            }

            printWriter.println("<styleUrl>#" + TRACK_STYLE + "</styleUrl>");
            writeCategory(track.getCategory());
            printWriter.println("<gx:MultiTrack>");
            printWriter.println("<altitudeMode>absolute</altitudeMode>");
            printWriter.println("<gx:interpolate>1</gx:interpolate>");
        }
    }

    @Override
    public void writeEndTrack(Track track, Location endLocation) {
        if (printWriter != null) {
            printWriter.println("</gx:MultiTrack>");
            printWriter.println("</Placemark>");

            if (exportTrackDetail) {
                String name = context.getString(R.string.marker_label_end, track.getName());
                String description = descriptionGenerator.generateTrackDescription(track, false);
                writePlacemark(name, "", description, END_STYLE, endLocation);
            }
        }
    }

    @Override
    public void writeOpenSegment() {
        if (printWriter != null) {
            printWriter.println("<gx:Track>");
            powerList.clear();
            cadenceList.clear();
            heartRateList.clear();
        }
    }

    @Override
    public void writeCloseSegment() {
        if (printWriter != null) {
            printWriter.println("<ExtendedData>");
            printWriter.println("<SchemaData schemaUrl=\"#" + SCHEMA_ID + "\">");
            if (exportSensorData) {
                if (powerList.size() > 0) {
                    writeSensorData(powerList, SENSOR_TYPE_POWER);
                }
                if (cadenceList.size() > 0) {
                    writeSensorData(cadenceList, SENSOR_TYPE_CADENCE);
                }
                if (heartRateList.size() > 0) {
                    writeSensorData(heartRateList, SENSOR_TYPE_HEART_RATE);
                }
            }
            printWriter.println("</SchemaData>");
            printWriter.println("</ExtendedData>");
            printWriter.println("</gx:Track>");
        }
    }

    @Override
    public void writeLocation(Location location) {
        if (printWriter != null) {
            if (exportTrackDetail) {
                printWriter.println("<when>" + getTime(location) + "</when>");
            }

            printWriter.println("<gx:coord>" + getCoordinates(location, " ") + "</gx:coord>");

            if (exportSensorData && location instanceof SensorDataSetLocation) {
                SensorDataSet sensorDataSet = ((SensorDataSetLocation) location).getSensorDataSet();
                if (sensorDataSet != null) {
                    if (sensorDataSet.hasHeartRate()) {
                        heartRateList.add(sensorDataSet.getHeartRate());
                    }
                    if (sensorDataSet.hasCadence()) {
                        cadenceList.add(sensorDataSet.getCadence());
                    }
                    if (sensorDataSet.hasPower()) {
                        powerList.add(sensorDataSet.getPower());
                    }
                }
            }
        }
    }

    /**
     * Writes the sensor data.
     *
     * @param list a list of sensor data
     * @param name the name of the sensor data
     */
    private void writeSensorData(List<Float> list, String name) {
        printWriter.println("<gx:SimpleArrayData name=\"" + name + "\">");
        for (int i = 0; i < list.size(); i++) {
            printWriter.println("<gx:value>" + list.get(i) + "</gx:value>");
        }
        printWriter.println("</gx:SimpleArrayData>");
    }

    /**
     * Writes a placemark.
     *
     * @param name        the name
     * @param category    the category
     * @param description the description
     * @param styleName   the style name
     * @param location    the location
     */
    private void writePlacemark(String name, String category, String description, String styleName, Location location) {
        if (location != null && exportTrackDetail) {
            printWriter.println("<Placemark>");
            printWriter.println("<name>" + StringUtils.formatCData(name) + "</name>");
            printWriter.println("<description>" + StringUtils.formatCData(description) + "</description>");
            printWriter.println("<TimeStamp><when>" + getTime(location) + "</when></TimeStamp>");
            printWriter.println("<styleUrl>#" + styleName + "</styleUrl>");
            writeCategory(category);
            printWriter.println("<Point>");
            printWriter.println("<coordinates>" + getCoordinates(location, ",") + "</coordinates>");
            printWriter.println("</Point>");
            printWriter.println("</Placemark>");
        }
    }

    /**
     * Writes a photo overlay.
     *
     * @param name        the name
     * @param category    the category
     * @param description the description
     * @param styleName   the style name
     * @param location    the location
     * @param photoUrl    the photo url
     * @param heading     the heading
     */
    private void writePhotoOverlay(String name, String category, String description, String styleName, Location location, String photoUrl, float heading) {
        if (location != null && exportTrackDetail) {
            printWriter.println("<PhotoOverlay>");
            printWriter.println("<name>" + StringUtils.formatCData(name) + "</name>");
            printWriter.println("<description>" + StringUtils.formatCData(description) + "</description>");
            printWriter.print("<Camera>");
            printWriter.print("<longitude>" + location.getLongitude() + "</longitude>");
            printWriter.print("<latitude>" + location.getLatitude() + "</latitude>");
            printWriter.print("<altitude>20</altitude>");
            printWriter.print("<heading>" + heading + "</heading>");
            printWriter.print("<tilt>90</tilt>");
            printWriter.println("</Camera>");
            printWriter.println("<TimeStamp><when>" + getTime(location) + "</when></TimeStamp>");
            printWriter.println("<styleUrl>#" + styleName + "</styleUrl>");
            writeCategory(category);

            if (exportPhotos) {
                printWriter.println("<Icon><href>" + Uri.decode(photoUrl) + "</href></Icon>");
            }

            printWriter.print("<ViewVolume>");
            printWriter.print("<near>10</near>");
            printWriter.print("<leftFov>-60</leftFov>");
            printWriter.print("<rightFov>60</rightFov>");
            printWriter.print("<bottomFov>-45</bottomFov>");
            printWriter.print("<topFov>45</topFov>");
            printWriter.println("</ViewVolume>");
            printWriter.println("<Point>");
            printWriter.println("<coordinates>" + getCoordinates(location, ",") + "</coordinates>");
            printWriter.println("</Point>");
            printWriter.println("</PhotoOverlay>");
        }
    }

    /**
     * Returns the formatted time of the location; either absolute or relative depending exportTrackDetail.
     *
     * @param location the location
     */
    private String getTime(Location location) {
        if (exportTrackDetail) {
            return StringUtils.formatDateTimeIso8601(location.getTime());
        } else {
            return StringUtils.formatDateTimeIso8601(location.getTime() - startLocation.getTime());
        }
    }

    /**
     * Gets the heading to a location.
     *
     * @param trackId  the track id containing the location
     * @param location the location
     */
    private float getHeading(long trackId, Location location) {
        long trackPointId = contentProviderUtils.getTrackPointId(trackId, location);
        if (trackPointId == -1L) {
            return location.getBearing();
        }
        Location viewLocation;
        try (Cursor cursor = contentProviderUtils.getTrackPointCursor(trackId, trackPointId, 10, true)) {
            if (cursor == null || cursor.getCount() == 0) {
                return location.getBearing();
            }
            cursor.moveToPosition(cursor.getCount() - 1);
            viewLocation = contentProviderUtils.createTrackPoint(cursor);
        }
        return viewLocation.bearingTo(location);
    }

    private String getCoordinates(Location location, String separator) {
        StringBuilder builder = new StringBuilder();
        builder.append(location.getLongitude()).append(separator).append(location.getLatitude());
        if (location.hasAltitude()) {
            builder.append(separator).append(location.getAltitude());
        }
        return builder.toString();
    }

    /**
     * Writes the category.
     *
     * @param category the category
     */
    private void writeCategory(String category) {
        if (category == null || category.equals("")) {
            return;
        }
        printWriter.println("<ExtendedData>");
        printWriter.println("<Data name=\"type\"><value>" + StringUtils.formatCData(category) + "</value></Data>");
        printWriter.println("</ExtendedData>");
    }

    /**
     * Writes the track style.
     */
    private void writeTrackStyle() {
        printWriter.println("<Style id=\"" + TRACK_STYLE + "\">");
        printWriter.println("<LineStyle><color>7f0000ff</color><width>4</width></LineStyle>");
        printWriter.println("<IconStyle>");
        printWriter.println("<scale>1.3</scale>");
        printWriter.println("<Icon><href>" + TRACK_ICON + "</href></Icon>");
        printWriter.println("</IconStyle>");
        printWriter.println("</Style>");
    }

    /**
     * Writes a placemarker style.
     *
     * @param name the name of the style
     * @param url  the url of the style icon
     * @param x    the x position of the hotspot
     * @param y    the y position of the hotspot
     */
    private void writePlacemarkerStyle(String name, String url, int x, int y) {
        printWriter.println("<Style id=\"" + name + "\"><IconStyle>");
        printWriter.println("<scale>1.3</scale>");
        printWriter.println("<Icon><href>" + url + "</href></Icon>");
        printWriter.println("<hotSpot x=\"" + x + "\" y=\"" + y + "\" xunits=\"pixels\" yunits=\"pixels\"/>");
        printWriter.println("</IconStyle></Style>");
    }

    /**
     * Writes a sensor style.
     *
     * @param name        the name of the sesnor
     * @param sensorType the sensor display name
     */
    private void writeSensorStyle(String name, String sensorType) {
        printWriter.println("<gx:SimpleArrayField name=\"" + name + "\" type=\"float\">");
        printWriter.println("<displayName>" + StringUtils.formatCData(sensorType) + "</displayName>");
        printWriter.println("</gx:SimpleArrayField>");
    }
}
