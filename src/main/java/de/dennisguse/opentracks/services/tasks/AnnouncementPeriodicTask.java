/*
 * Copyright 2009 Google Inc.
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

package de.dennisguse.opentracks.services.tasks;

import android.content.Context;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import java.util.Locale;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.services.TrackRecordingService;
import de.dennisguse.opentracks.stats.TripStatistics;
import de.dennisguse.opentracks.util.PreferencesUtils;
import de.dennisguse.opentracks.util.StringUtils;
import de.dennisguse.opentracks.util.UnitConversions;

/**
 * This class will periodically announce the user's trip statistics.
 *
 * @author Sandor Dornbush
 */
public class AnnouncementPeriodicTask implements PeriodicTask {

    /**
     * The rate at which announcements are spoken.
     */
    @VisibleForTesting
    static final float TTS_SPEECH_RATE = 0.9f;

    private static final String TAG = AnnouncementPeriodicTask.class.getSimpleName();

    private final Context context;

    private final AudioManager audioManager;
    private final UtteranceProgressListener utteranceListener = new UtteranceProgressListener() {
        @Override
        public void onStart(String utteranceId) {
        }

        @Override
        public void onDone(String utteranceId) {
            int result = audioManager.abandonAudioFocus(null);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                Log.w(TAG, "Failed to relinquish audio focus.");
            }
        }

        @Override
        public void onError(String utteranceId) {
        }
    };

    private TextToSpeech tts;
    // Response from TTS after its initialization
    private int initStatus = TextToSpeech.ERROR;

    private boolean ttsReady = false;

    // True if speech is allowed
    private boolean speechAllowed;

    /**
     * Listener which updates {@link #speechAllowed} when the phone state changes.
     */
    private final PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            speechAllowed = state == TelephonyManager.CALL_STATE_IDLE;
            if (!speechAllowed && tts != null && tts.isSpeaking()) {
                // If we're already speaking, stop it.
                tts.stop();
            }
        }
    };

    AnnouncementPeriodicTask(Context context) {
        this.context = context;
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public void start() {
        if (tts == null) {
            tts = newTextToSpeech(context, new OnInitListener() {
                @Override
                public void onInit(int status) {
                    initStatus = status;
                }
            });
        }
        speechAllowed = true;
        listenToPhoneState(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    @Override
    public void run(TrackRecordingService trackRecordingService) {
        if (trackRecordingService == null) {
            Log.e(TAG, "TrackRecordingService is null.");
            return;
        }
        announce(trackRecordingService.getTripStatistics());
    }

    /**
     * Runs this task.
     *
     * @param tripStatistics the trip statistics
     */
    @VisibleForTesting
    void announce(TripStatistics tripStatistics) {
        if (tripStatistics == null) {
            Log.e(TAG, "TripStatistics is null.");
            return;
        }

        synchronized (this) {
            if (!ttsReady) {
                ttsReady = initStatus == TextToSpeech.SUCCESS;
                if (ttsReady) {
                    onTtsReady();
                }
            }
            if (!ttsReady) {
                Log.i(TAG, "TTS not ready.");
                return;
            }
        }

        if (!speechAllowed) {
            Log.i(TAG, "Speech is not allowed at this time.");
            return;
        }
        speakAnnouncement(getAnnouncement(tripStatistics));
    }

    @Override
    public void shutdown() {
        listenToPhoneState(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
    }

    /**
     * Called when TTS is ready.
     */
    private void onTtsReady() {
        Locale locale = Locale.getDefault();
        int languageAvailability = tts.isLanguageAvailable(locale);
        if (languageAvailability == TextToSpeech.LANG_MISSING_DATA
                || languageAvailability == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Default locale not available, use English.");
            locale = Locale.ENGLISH;
            /*
             * TODO: instead of using english, load the language if missing and show a toast if not supported.
             *  Not able to change the resource strings to English.
             */
        }
        tts.setLanguage(locale);

        // Slow down the speed just a bit as it is hard to hear when exercising.
        tts.setSpeechRate(TTS_SPEECH_RATE);

        tts.setOnUtteranceProgressListener(utteranceListener);
    }

    /**
     * Speaks the announcement.
     *
     * @param announcement the announcement
     */
    private void speakAnnouncement(String announcement) {
        int result = audioManager.requestAudioFocus(null, TextToSpeech.Engine.DEFAULT_STREAM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.w(TAG, "Failed to request audio focus.");
        }

        /*
         * We don't care about the utterance id. It is supplied here to force
         * onUtteranceCompleted to be called.
         */
        tts.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, "not used");
    }

    /**
     * Create a new {@link TextToSpeech}.
     *
     * @param aContext       a context
     * @param onInitListener an on init listener
     */
    @VisibleForTesting
    protected TextToSpeech newTextToSpeech(Context aContext, OnInitListener onInitListener) {
        return new TextToSpeech(aContext, onInitListener);
    }

    /**
     * Gets the announcement.
     *
     * @param tripStatistics the trip statistics
     */
    @VisibleForTesting
    protected String getAnnouncement(TripStatistics tripStatistics) {
        boolean metricUnits = PreferencesUtils.isMetricUnits(context);
        boolean reportSpeed = PreferencesUtils.isReportSpeed(context);
        double distance = tripStatistics.getTotalDistance() * UnitConversions.M_TO_KM;
        double distancePerTime = tripStatistics.getAverageMovingSpeed() * UnitConversions.MS_TO_KMH;

        if (distance == 0) {
            return context.getString(R.string.voice_total_distance_zero);
        }

        if (!metricUnits) {
            distance *= UnitConversions.KM_TO_MI;
            distancePerTime *= UnitConversions.KM_TO_MI;
        }

        String rate;
        if (reportSpeed) {
            int speedId = metricUnits ? R.plurals.voiceSpeedKilometersPerHour : R.plurals.voiceSpeedMilesPerHour;
            rate = context.getResources().getQuantityString(speedId, getQuantityCount(distancePerTime), distancePerTime);
        } else {
            double timePerDistance = distancePerTime == 0 ? 0.0 : 1 / distancePerTime;
            int paceId = metricUnits ? R.string.voice_pace_per_kilometer : R.string.voice_pace_per_mile;
            long time = Math.round(timePerDistance * UnitConversions.HR_TO_MIN * UnitConversions.MIN_TO_S * UnitConversions.S_TO_MS);
            rate = context.getString(paceId, getAnnounceTime(time));
        }

        int totalDistanceId = metricUnits ? R.plurals.voiceTotalDistanceKilometers : R.plurals.voiceTotalDistanceMiles;
        String totalDistance = context.getResources().getQuantityString(totalDistanceId, getQuantityCount(distance), distance);

        return context.getString(R.string.voice_template, totalDistance,
                getAnnounceTime(tripStatistics.getMovingTime()), rate);
    }

    /**
     * Listens to phone state.
     *
     * @param listener the listener
     * @param events   the interested events
     */
    @VisibleForTesting
    protected void listenToPhoneState(PhoneStateListener listener, int events) {
        TelephonyManager telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephony != null) {
            telephony.listen(listener, events);
        }
    }

    /**
     * Gets the announce time.
     *
     * @param time the time
     */
    @VisibleForTesting
    String getAnnounceTime(long time) {
        int[] parts = StringUtils.getTimeParts(time);
        String seconds = context.getResources()
                .getQuantityString(R.plurals.voiceSeconds, parts[0], parts[0]);
        String minutes = context.getResources()
                .getQuantityString(R.plurals.voiceMinutes, parts[1], parts[1]);
        String hours = context.getResources()
                .getQuantityString(R.plurals.voiceHours, parts[2], parts[2]);
        StringBuilder sb = new StringBuilder();
        if (parts[2] != 0) {
            sb.append(hours);
            sb.append(" ");
        }
        sb.append(minutes);
        sb.append(" ");
        sb.append(seconds);
        return sb.toString();
    }

    /**
     * Gets the plural count to be used by getQuantityString.
     * getQuantityString only supports integer quantities, not a double quantity like "2.2".
     * <p>
     * As a temporary workaround, we convert a double quantity to an integer quantity.
     * If the double quantity is exactly 0, 1, or 2, then we can return these integer quantities.
     * Otherwise, we cast the double quantity to an integer quantity.
     * However, we need to make sure that if the casted value is 0, 1, or 2, we don't return those, instead, return the next biggest integer 3.
     *
     * @param d the double value
     */
    private int getQuantityCount(double d) {
        if (d == 0) {
            return 0;
        } else if (d == 1) {
            return 1;
        } else if (d == 2) {
            return 2;
        } else {
            //TODO This seems weird; why not use Math.round(d) or Math.ceil()?
            int count = (int) d;
            return count < 3 ? 3 : count;
        }
    }
}
