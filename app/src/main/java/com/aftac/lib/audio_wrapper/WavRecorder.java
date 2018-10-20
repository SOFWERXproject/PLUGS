package com.aftac.lib.audio_wrapper;

import android.util.Log;

import com.aftac.lib.file_io_wrapper.BinaryFileOutputStream;

import java.io.File;
import java.io.IOException;


public class WavRecorder implements AudioInputStream.AudioStreamReciever {
    private static final String LOG_TAG = "WavRecorder";

    private AudioInputStream audioStream;
    private BinaryFileOutputStream outFile;
    private Object fileLock = new Object();

    private boolean stereo = false;
    private boolean recording = false;
    private int sampleRate = 0;
    private int recordedSize = 0;
    private int bufferSize = 4096;
    private short[] buffer = new short[bufferSize];

    public WavRecorder() {
    }

    public boolean isRecording() {
        return (recording && audioStream.isRunning());
    }

    public boolean startRecording(String filename, int sampleRate, boolean stereo) throws IOException {
        Log.d(LOG_TAG, "Trying to record to: " + filename);
        if (recording) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                Log.d(LOG_TAG, "Can't start recording while already recording");
            return false;
        }

        if (!createAudioStream(sampleRate, stereo)) {
            //if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                Log.d(LOG_TAG, "Failed to create audio stream with requested settings");
            return false;
        }

        this.sampleRate = sampleRate;
        this.stereo = stereo;

        // Open output file
        try {
            outFile = new BinaryFileOutputStream(filename,
                                                 BinaryFileOutputStream.Endianness.BIG_ENDIAN);
            outFile.setLength(0); // Empty the file
        } catch (IOException exception) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                Log.d(LOG_TAG, "Failed to open output file");
            exception.printStackTrace();
            throw exception;
        }
        if (outFile == null) {
            Log.d(LOG_TAG, "Failed to open output file");
            return false;
        }

        // Write header
        try {
            writeHeader();
        } catch (IOException exception) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                Log.d(LOG_TAG, "Failed to write file header");

            // Try to close and delete the file
            try {
                outFile.close();
                File file = new File(filename);
                file.delete();
            } catch (IOException exception2) { exception2.printStackTrace(); }

            outFile = null;
            exception.printStackTrace();
            throw exception;
        }

        // Try to start the audio stream
        if (!audioStream.start()) {
            try {
                outFile.close();
                File file = new File(filename);
                file.delete();
            } catch (IOException exception) {
                if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                    Log.d(LOG_TAG, "Failed to start audio stream.");
                exception.printStackTrace();
                throw exception;
            }
            Log.d(LOG_TAG, "Failed to start audio stream.");
            return false;
        }

        recording = true;
        return true;
    }

    public void stopRecording() throws IOException {
        if (recording) {
            audioStream.stop();
            synchronized (fileLock) {
                try {
                    outFile.close();
                    outFile = null;
                } catch (IOException exception) {
                    if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                        Log.d(LOG_TAG, "Failed to close output file");
                    exception.printStackTrace();
                    throw exception;
                }
            }
            recording = false;
        }
    }

    @Override
    public void onRecieveAudioNotification(int amount) {
        int size;
        // Continue reading while audio data is available, or an overflow occurs...
        synchronized (fileLock) {
            do {
                size = audioStream.read(buffer, bufferSize);

                // Try to write the data to file
                if (outFile != null) {
                    try {
                        outFile.write(buffer, 0, size);
                        recordedSize += size << 1;

                        // Update the file header so that hopefully the file is still in a valid
                        // state if something bad happens while recording
                        outFile.seek(4);  // RIFF block size
                        outFile.writeInt(36 + recordedSize);
                        outFile.seek(40); // Data block size
                        outFile.writeInt(recordedSize);
                        outFile.seek(outFile.getLength());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } while (audioStream.readAvailable() > 0 && !audioStream.hasOverflowed());
        }
        //Overflow?  Alert the user.
        if (audioStream.hasOverflowed()) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                Log.d(LOG_TAG, "Audio input buffer has overflowed");
            }
            audioStream.clearOverflowStatus();
        }
    }

    private boolean createAudioStream(int sampleRate, boolean stereo) {
        if (!AudioInputStream.sampleRateSupported(sampleRate, stereo))
            return false;

        Log.v(LOG_TAG, "Supported");

        if (audioStream == null) {
            audioStream = new AudioInputStream(sampleRate, stereo, this, bufferSize);
        } else if (audioStream.getSampleRate() != sampleRate || audioStream.isStereo() == stereo) {
            audioStream.free(); audioStream = null;
            audioStream = new AudioInputStream(sampleRate, stereo, this, bufferSize);
        }

        if (audioStream.getSampleRate() != sampleRate || audioStream.isStereo() != stereo) {
            audioStream.free(); audioStream = null;
            Log.v(LOG_TAG, "Doesn't match settings");
            return false;
        } else
            return true;
    }

    private void writeHeader() throws IOException {
        int channels = (stereo ? 2 : 1);
        int bytesPerSample = channels * 2; // 16-bit encoding = 2 bytes
        int byteRate = sampleRate * bytesPerSample;

        try {
            // Write wav file header
            outFile.setLength(0);
            outFile.writeBytes("RIFF");
            outFile.writeInt(0);      // RIFF block size (will be set when file is closed)
            outFile.writeBytes("WAVE");
            outFile.writeBytes("fmt "); // Format block
            outFile.writeInt(16);     // Format block size
            outFile.writeShort(1);    // PCM encoding
            outFile.writeShort(channels);   // Channels (stereo/mono)
            outFile.writeInt(sampleRate);
            outFile.writeInt(byteRate);
            outFile.writeShort(bytesPerSample);
            outFile.writeShort(16);   // 16-bit PCM encoding
            outFile.writeBytes("data"); // Data block
            outFile.writeInt(0);      // Data block size (Will be set when file is closed)
        } catch (IOException exception) {
            throw exception;
        }
    }
}