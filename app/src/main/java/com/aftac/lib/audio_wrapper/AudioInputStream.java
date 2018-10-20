package com.aftac.lib.audio_wrapper;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class AudioInputStream implements Runnable {
    private static final String LOG_TAG = "AudioInputStream";

    private static final int[] SAMPLE_RATES = new int[] {
            8000, 11025, 16000, 22050, 32000, 44100, 48000, 96000, 192000};

    private volatile boolean running = false;
    private volatile int sampleRate = 0;
    private volatile boolean stereo = false;
    private Handler mainThreadHandler;
    private int requestedSampleRate = 0;
    private boolean requestedStereo = false;
    private int requestedBufferSize;
    private int bufferSize;
    private AudioRecord stream;
    private AudioStreamReciever audioReciever;
    private Thread thread;
    private volatile short[] outBuffer;
    private volatile int readPos = 0, readAvailable = 0;
    private short[] inBuffer;
    private volatile boolean overflow = false;

    public interface AudioStreamReciever {
        void onRecieveAudioNotification(int amount);
    }

    public AudioInputStream(int sampleRate, boolean stereo, AudioStreamReciever audioReciever) {
        this(sampleRate, stereo, audioReciever, 0);
    }

    public AudioInputStream(int sampleRate, boolean stereo, AudioStreamReciever audioReciever, int requestedBufferSize) {
        mainThreadHandler = new Handler(Looper.getMainLooper()); // Handler to talk to main thread
        requestedSampleRate = sampleRate;
        requestedStereo = stereo;
        this.sampleRate = requestedSampleRate;
        this.stereo = requestedStereo;
        this.requestedBufferSize = requestedBufferSize;
        this.audioReciever = audioReciever;
    }

    public static boolean sampleRateSupported(int sampleRate, boolean stereo) {
        boolean supported = false;
        // Buffer size test
        int size = AudioRecord.getMinBufferSize(
                sampleRate,
                (stereo ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO),
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (size > 0) {
            // Try to create a test stream, and verify it was successful
            AudioRecord testStream = null;
            try {
                testStream = new AudioRecord(
                        AudioSource.MIC,
                        sampleRate,
                        (stereo ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO),
                        AudioFormat.ENCODING_PCM_16BIT, size
                );
                if (testStream.getState() == AudioRecord.STATE_INITIALIZED) {
                    // Make sure the created stream has the correct rate & stereo setting
                    if (testStream.getSampleRate() == sampleRate
                            && stereo == (testStream.getChannelConfiguration() == AudioFormat.CHANNEL_IN_STEREO)) {
                        supported = true;
                    }
                }
            } catch (IllegalArgumentException e) {
                // Format not supported
            }
            if (testStream != null)
                testStream.release();
        }

        return supported;
    }

    public static int[] getSupportedSampleRates(boolean stereo) {
        int i, pos, size, numSupported = 0;
        AudioRecord testStream = null;
        boolean[] supported = new boolean[SAMPLE_RATES.length];
        for (i = 0; i < SAMPLE_RATES.length; i++) supported[i] = false;

        // Test for each potential sample rate
        for (i = 0; i < SAMPLE_RATES.length; i++) {
            if (sampleRateSupported(SAMPLE_RATES[i], stereo)) {
                supported[i] = true;
                numSupported++;
            }
        }

        // Build the supported rate array
        int[] ret = new int[numSupported];
        pos = 0;
        for (i = 0; i < SAMPLE_RATES.length; i++) {
            if (supported[i]) ret[pos++] = SAMPLE_RATES[i];
        }

        return ret;
    }

    public boolean start() {
        if (audioReciever == null)
            return false; // No point in running if there's nothing to receive the audio data
        else if (running)
            return true; // Already running

        // Get minimum buffer size
        // If the requested buffer size is smaller than the minimum, use the minimum size
        bufferSize = AudioRecord.getMinBufferSize(
                requestedSampleRate,
                (requestedStereo ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO),
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (bufferSize < requestedBufferSize) bufferSize = requestedBufferSize;

        if (bufferSize > 0) {
            // Create the stream
            try {
                stream = new AudioRecord(
                        AudioSource.MIC,
                        requestedSampleRate,
                        (requestedStereo ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO),
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                );
            } catch (IllegalArgumentException e) {
                throw new RuntimeException(e);
            }

            // Make sure the stream was created successfully
            if (stream.getState() == AudioRecord.STATE_UNINITIALIZED) {
                stream.release();
                stream = null;
            }
        } else
            stream = null;

        if (stream != null) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                Log.d(LOG_TAG, "Started listening");

            this.sampleRate = stream.getSampleRate();
            this.stereo = (stream.getChannelConfiguration() == AudioFormat.CHANNEL_IN_STEREO);

            // Make sure buffers exist and are correct size
            if (inBuffer == null || inBuffer.length != bufferSize)
                inBuffer = new short[bufferSize];
            if (outBuffer == null || outBuffer.length != bufferSize)
                outBuffer = new short[bufferSize];

            // Initialize values
            readPos = 0;
            readAvailable = 0;
            overflow = false;

            // Start a separate thread to manage the stream
            running = true;
            thread = new Thread(this);
            thread.start();

            return true; // Successs
        }

        if (Log.isLoggable(LOG_TAG, Log.DEBUG))
            Log.d(LOG_TAG, "Failed to start listening");
        return false; // Failure
    }

    public void stop() {
        if (thread == null)
            return;

        // Stop running
            running = false;

        // Wait for the thread to stop
        boolean retry = true;
        while (retry) {
            try {
                thread.join();
                retry=false;
            } catch (InterruptedException e) { e.printStackTrace(); }
        }
        thread=null;

        // Reset values
        readPos = 0;
        readAvailable = 0;
        overflow = false;
    }

    public int read(short[] buffer, int lengthRequested) {
        // Read the output buffer in a thread safe manner
        synchronized (outBuffer) {
            // Get smaller of length available, or requested.
            int length = (readAvailable < lengthRequested ? readAvailable : lengthRequested);

            // Copy data to output buffer
            if (length > 0) {
                System.arraycopy(outBuffer, readPos, buffer, 0, length);
                readPos += length;
                readAvailable -= length;
            }

            return length;
        }
    }

    public int read(short[] buffer, int offset, int lengthRequested) {
        // Thread safely read the output buffer
        synchronized (outBuffer) {
            // Get smaller of length available, or requested.
            int length = (lengthRequested < readAvailable ? readAvailable : lengthRequested);

            // Copy data to output buffer
            if (length > 0) {
                System.arraycopy(outBuffer, readPos, buffer, offset, length);
                readPos += length;
                readAvailable -= length;
            }

            return length;
        }
    }

    public int readAvailable() {
        // Thread safe
        synchronized (outBuffer) {
            return readAvailable;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(int sampleRate) {
        requestedSampleRate = sampleRate;
    }

    public boolean isStereo() {
        return stereo;
    }

    public void setStereo(boolean stereo) {
        requestedStereo = stereo;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public boolean hasOverflowed() {
        // Thread safe
        synchronized (outBuffer) {
            return overflow;
        }
    }

    public void clearOverflowStatus() {
        // Thread safe
        synchronized (outBuffer) {
            overflow = false;
        }
    }

    public void free() {
        // Stop and release running stream
        if (running)
            stop();
        audioReciever = null;
        outBuffer = null;
        inBuffer = null;
    }



    @Override
    public void run() {
        int pos = 0;
        // Create a runnable to send notifications to the main thread
        Runnable notificationRunnable = () -> audioReciever.onRecieveAudioNotification(bufferSize);

        stream.startRecording();

        while (running) {
            // stream.read is a blocking function call
            pos += stream.read(inBuffer, pos, bufferSize-pos);
            if (pos >= bufferSize) {
                // Copy input to the output buffer in a thread safe manner
                synchronized (outBuffer) {
                    readPos = 0;
                    // The output buffer will be overwritten with the new input
                    // Indicate an overflow has occurred if the output buffer contains unread data
                    if (readAvailable > 0) {
                        overflow = true;
                        if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                            Log.d(LOG_TAG, "Buffer overflow");
                    }
                    readAvailable = bufferSize;

                    System.arraycopy(inBuffer, 0, outBuffer, 0, bufferSize);
                }
                pos = 0;

                // Post the notification to the main thread
                mainThreadHandler.post(notificationRunnable);
            }
        }

        // Stop & release the stream
        stream.stop();
        stream.release();
        stream = null;
    }
}