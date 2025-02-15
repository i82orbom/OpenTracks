package de.dennisguse.opentracks.content;

import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import de.dennisguse.opentracks.android.IContentResolver;
import de.dennisguse.opentracks.io.file.TrackFileFormat;
import de.dennisguse.opentracks.io.file.exporter.TrackExporter;

/**
 * A content provider that mimics the behavior of {@link androidx.core.content.FileProvider}, which shares virtual (non-existing) KML-files.
 * The actual content of the virtual files is generated by using the functionality defined in {@link CustomContentProvider}.
 *
 * Moreover, it manages access to OpenTrack's database via {@link CustomContentProvider}.
 *
 * Explanation:
 * Although a request is handled by a {@link android.content.ContentProvider} (with temporarily granted permission), Android's security infrastructure prevents forwarding queries to non-exported {@link android.content.ContentProvider}.
 * Thus, if {@link ShareContentProvider} and {@link CustomContentProvider} would be two different instances, the data would not be accessible to external apps.
 * While handling a request {@link ShareContentProvider} could `grantPermissions()` to the calling app for {@link CustomContentProvider}'s URI.
 * However, while handling the request this would allow the calling app to actually contact {@link CustomContentProvider} directly and get access to stored data that should remain private.
 */
public class ShareContentProvider extends CustomContentProvider implements IContentResolver {

    private static final String[] COLUMNS = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};

    public static final String TAG = ShareContentProvider.class.getSimpleName();

    private static final int URI_GPX = 0;
    private static final int URI_KML_ONLY = 1;
    private static final int URI_KML_WITH_TRACKDETAIL = 2;
    private static final int URI_KML_WITH_TRACKDETAIL_SENSORDATA = 3;

    private static final int URI_KMZ_ONLY_TRACK = 4;
    private static final int URI_KMZ_WITH_TRACKDETAIL = 5;
    private static final int URI_KMZ_WITH_TRACKDETAIL_AND_SENSORDATA = 6;
    private static final int URI_KMZ_WITH_TRACKDETAIL_SENSORDATA_AND_PICTURES = 7;

    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    private static final String TRACKID_DELIMITER = "_";

    static {
        uriMatcher.addURI(ContentProviderUtils.AUTHORITY_PACKAGE, TracksColumns.TABLE_NAME + "/" + TrackFileFormat.GPX.getName() + "/*", URI_GPX);

        uriMatcher.addURI(ContentProviderUtils.AUTHORITY_PACKAGE, TracksColumns.TABLE_NAME + "/" + TrackFileFormat.KML_ONLY_TRACK.getName() + "/*", URI_KML_ONLY);
        uriMatcher.addURI(ContentProviderUtils.AUTHORITY_PACKAGE, TracksColumns.TABLE_NAME + "/" + TrackFileFormat.KML_WITH_TRACKDETAIL.getName() + "/*", URI_KML_WITH_TRACKDETAIL);
        uriMatcher.addURI(ContentProviderUtils.AUTHORITY_PACKAGE, TracksColumns.TABLE_NAME + "/" + TrackFileFormat.KML_WITH_TRACKDETAIL_AND_SENSORDATA.getName() + "/*", URI_KML_WITH_TRACKDETAIL_SENSORDATA);
        uriMatcher.addURI(ContentProviderUtils.AUTHORITY_PACKAGE, TracksColumns.TABLE_NAME + "/" + TrackFileFormat.KMZ_ONLY_TRACK.getName() + "/*", URI_KMZ_ONLY_TRACK);

        uriMatcher.addURI(ContentProviderUtils.AUTHORITY_PACKAGE, TracksColumns.TABLE_NAME + "/" + TrackFileFormat.KMZ_WITH_TRACKDETAIL.getName() + "/*", URI_KMZ_WITH_TRACKDETAIL);
        uriMatcher.addURI(ContentProviderUtils.AUTHORITY_PACKAGE, TracksColumns.TABLE_NAME + "/" + TrackFileFormat.KMZ_WITH_TRACKDETAIL_AND_SENSORDATA.getName() + "/*", URI_KMZ_WITH_TRACKDETAIL_AND_SENSORDATA);
        uriMatcher.addURI(ContentProviderUtils.AUTHORITY_PACKAGE, TracksColumns.TABLE_NAME + "/" + TrackFileFormat.KMZ_WITH_TRACKDETAIL_AND_SENSORDATA_AND_PICTURES.getName() + "/*", URI_KMZ_WITH_TRACKDETAIL_SENSORDATA_AND_PICTURES);
    }

    public static Pair<Uri, String> createURI(long[] trackIds, @NonNull TrackFileFormat trackFileFormat) {
        if (trackIds.length == 0) {
            throw new UnsupportedOperationException();
        }

        StringBuilder builder = new StringBuilder();
        for (long trackId : trackIds) {
            builder.append(trackId).append(TRACKID_DELIMITER);
        }
        builder.deleteCharAt(builder.lastIndexOf(TRACKID_DELIMITER));

        Uri uri = Uri.parse(ContentProviderUtils.CONTENT_BASE_URI + "/" + TracksColumns.TABLE_NAME + "/" + trackFileFormat.getName() + "/" + builder + "." + trackFileFormat.getExtension());
        String mime = getTypeMime(uri);

        Log.d(TAG, "Created uri " + uri.toString() + " with MIME " + mime);

        return new Pair<>(uri, mime);
    }

    private static long[] parseURI(Uri uri) {
        String lastPathSegment = uri.getLastPathSegment();
        if (lastPathSegment == null) {
            return new long[]{};
        }

        String fileExtension = "." + getTrackFileFormat(uri).getExtension();
        String[] lastPathSegmentSplit = lastPathSegment.replace(fileExtension, "").split(TRACKID_DELIMITER);

        long[] trackIds = new long[lastPathSegmentSplit.length];
        for (int i = 0; i < trackIds.length; i++) {
            trackIds[i] = Long.valueOf(lastPathSegmentSplit[i]);
        }
        return trackIds;
    }

    /**
     * Do not allow to be exported via AndroidManifest.
     * Check that caller has permissions to access {@link CustomContentProvider}.
     */
    @Override
    public void attachInfo(@NonNull Context context, @NonNull ProviderInfo info) {
        super.attachInfo(context, info);

        // Sanity check our security
        if (info.exported) {
            throw new UnsupportedOperationException("Provider must not be exported");
        }

        if (!info.grantUriPermissions) {
            throw new SecurityException("Provider must grant uri permissions");
        }
    }

    private static TrackFileFormat getTrackFileFormat(@NonNull Uri uri) {
        switch (uriMatcher.match(uri)) {
            case URI_GPX:
                return TrackFileFormat.GPX;

            case URI_KML_ONLY:
                return TrackFileFormat.KML_ONLY_TRACK;
            case URI_KML_WITH_TRACKDETAIL:
                return TrackFileFormat.KML_WITH_TRACKDETAIL;
            case URI_KML_WITH_TRACKDETAIL_SENSORDATA:
                return TrackFileFormat.KML_WITH_TRACKDETAIL_AND_SENSORDATA;

            case URI_KMZ_ONLY_TRACK:
                return TrackFileFormat.KMZ_ONLY_TRACK;
            case URI_KMZ_WITH_TRACKDETAIL:
                return TrackFileFormat.KMZ_WITH_TRACKDETAIL;
            case URI_KMZ_WITH_TRACKDETAIL_AND_SENSORDATA:
                return TrackFileFormat.KMZ_WITH_TRACKDETAIL_AND_SENSORDATA;
            case URI_KMZ_WITH_TRACKDETAIL_SENSORDATA_AND_PICTURES:
                return TrackFileFormat.KMZ_WITH_TRACKDETAIL_AND_SENSORDATA_AND_PICTURES;

            default:
                throw new RuntimeException("Could not derive TrackFileFormat from Uri " + uri);
        }
    }

    @Nullable
    public static String getTypeMime(@NonNull Uri uri) {
        return getTrackFileFormat(uri).getMimeType();
    }

    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (uriMatcher.match(uri) == -1) {
            return super.query(uri, projection, selection, selectionArgs, sortOrder);
        }

        // ContentProvider has already checked granted permissions
        if (projection == null) {
            projection = COLUMNS;
        }

        String[] cols = new String[projection.length];
        Object[] values = new Object[projection.length];
        int i = 0;
        for (String col : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                cols[i] = OpenableColumns.DISPLAY_NAME;
                values[i++] = uri.getLastPathSegment();
            } else if (OpenableColumns.SIZE.equals(col)) {
                cols[i] = OpenableColumns.SIZE;
                values[i++] = -1;
            }
        }

        cols = Arrays.copyOf(cols, i);
        values = Arrays.copyOf(values, i);

        final MatrixCursor cursor = new MatrixCursor(cols, 1);
        cursor.addRow(values);
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        String mime = getTypeMime(uri);
        if (mime != null) {
            return mime;
        }

        return super.getType(uri);
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        ContentProviderUtils contentProviderUtils = new ContentProviderUtilsImpl(this);

        long[] trackIds = parseURI(uri);
        final Track[] tracks = new Track[trackIds.length];
        for (int i = 0; i < trackIds.length; i++) {
            tracks[i] = contentProviderUtils.getTrack(trackIds[i]);
        }

        final TrackExporter trackExporter = getTrackFileFormat(uri).newTrackExporter(getContext(), tracks, null);

        PipeDataWriter pipeDataWriter = new PipeDataWriter<String>() {
            @Override
            public void writeDataToPipe(@NonNull ParcelFileDescriptor output, @NonNull Uri uri, @NonNull String mimeType, @Nullable Bundle opts, @Nullable String args) {
                try (FileOutputStream fileOutputStream = new FileOutputStream(output.getFileDescriptor())) {
                    trackExporter.writeTrack(getContext(), fileOutputStream);
                } catch (IOException e) {
                    Log.w(TAG, "there occurred an error while sharing a file: " + e);
                }
            }
        };

        return openPipeHelper(uri, getType(uri), null, null, pipeDataWriter);
    }
}
