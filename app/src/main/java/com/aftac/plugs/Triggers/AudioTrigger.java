package com.aftac.plugs.Triggers;

import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import com.aftac.lib.audio_wrapper.AudioInputStream;
import com.aftac.plugs.Gps.GpsService;
import com.aftac.plugs.Queue.QueueTrigger;
import com.aftac.plugs.Sensors.PlugsSensorEvent;

import java.nio.ByteBuffer;

public class AudioTrigger extends PlugsTrigger {
    private static final int SETTLE_SAMPLE_NUM = 44100;
    private static final int POLL_INTERVAL = 20;

    AudioInputStream audioStream;
    short[] audioBuffer;
    boolean processing = false;

    HandlerThread workThread;
    Handler workHandler;

    MediaRecorder audioRecorder;

    int settleCounter = SETTLE_SAMPLE_NUM;
    float volume = 1;
    float sensitivity;
    boolean listening = false;


    public AudioTrigger() {
        /*audioRecorder = new MediaRecorder();
        audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
        audioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        audioRecorder.setOutputFile("/dev/null");
        try {
            audioRecorder.prepare();
        } catch (Exception e) {
            e.printStackTrace();
        }*/


        workThread = new HandlerThread(getClass().getName(), Thread.NORM_PRIORITY);
        workThread.start();
        workHandler = new Handler(workThread.getLooper());
        audioStream = new AudioInputStream(11025, false, audioReceiver);
        audioBuffer = new short[1024];
    }

    public void setSensitivity(float sensitivity) {
        this.sensitivity = sensitivity;
    }

    public void enable() {
        audioStream.start();
        //try {
        //    audioRecorder.prepare();
        listening = true;
        //audioRecorder.start();
        //workHandler.post(()->checkAudioLevel());
        //} catch (Exception e) {
        //    e.printStackTrace();
        //}
    }

    public void disable() {
        audioStream.stop();
        listening = false;
        //audioRecorder.stop();
    }

    public void detach() {
        audioStream.free();
        //audioRecorder.reset();
        //audioRecorder.release();
    }

    private void checkAudioLevel() {
        float value = audioRecorder.getMaxAmplitude();
        if (value > 0) value = (float)(20.0f * Math.log10(value / 2700.0f));
        volume = value * 0.001f + volume * 0.999f;
        //Log.v("AudioTrigger", "Volume: " + value + "db ");

        if (listening) {
            if (settleCounter == 0 && volume > sensitivity) {
                long gpsTimestamp    = GpsService.getUtcTime();
                long systemTimestamp = System.currentTimeMillis();
                ByteBuffer data = ByteBuffer.wrap(new byte[Float.BYTES]);
                data.asFloatBuffer().put(volume);

                doTrigger(new QueueTrigger(gpsTimestamp, systemTimestamp, 0,
                        0, data));
                settleCounter = SETTLE_SAMPLE_NUM;
            }
            workHandler.postDelayed(this::checkAudioLevel, POLL_INTERVAL);
        }
    }

    private void processAudio() {
        long adjustment = Math.round(1000f / 11025f * audioStream.readAvailable());
        long gpsTimestamp    = GpsService.getUtcTime()    - adjustment;
        long systemTimestamp = System.currentTimeMillis() - adjustment;
        boolean tripped = false;
        int tripPos = 0;
        float value, maxValue = Float.NEGATIVE_INFINITY;
        //Log.v("AudioTrigger", "Processing audio");
        while (audioStream.readAvailable() > 0) {
            int read = audioStream.read(audioBuffer, audioBuffer.length);
            for (int i = 0; i < read; i++) {
                if (audioBuffer[i] == 0) continue;
                value = (float)(20.0f * Math.log10(audioBuffer[i] / 2700.0f));
                if (!tripped && value > sensitivity) {
                    tripPos += i;
                    tripped = true;
                    //break;
                }
                if (value > maxValue)
                    maxValue = value;
            }
            if (settleCounter > 0) settleCounter -= read;
            if (settleCounter < 0) settleCounter = 0;
            if (!tripped)
                tripPos += read;
        }

        volume = maxValue;

        //Log.v("AudioTrigger", "Volume: " + volume);

        if (tripped && settleCounter == 0) {// > sensitivity) {
            adjustment = Math.round(1000f / 11025f * tripPos);
            gpsTimestamp    += adjustment;
            systemTimestamp += adjustment;
            ByteBuffer data = ByteBuffer.wrap(new byte[Float.BYTES]);
            data.asFloatBuffer().put(volume);

            doTrigger(new QueueTrigger(gpsTimestamp, systemTimestamp, 0,
                    0, data));
            settleCounter = SETTLE_SAMPLE_NUM;
        }
    }

    AudioInputStream.AudioStreamReciever audioReceiver = new AudioInputStream.AudioStreamReciever() {
        @Override
        public void onRecieveAudioNotification(int amount) {
            workHandler.post(()->processAudio());
        }
    };
}
