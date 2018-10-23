package com.aftac.plugs.Recorder;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.aftac.lib.audio_wrapper.WavRecorder;
import com.aftac.plugs.Gps.GpsService;
import com.aftac.plugs.Queue.Queue;

import java.io.File;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

public class RecordingManager {
    public static final int COMMAND_TAKE_PICTURE = 1;
    public static final int COMMAND_RECORD_VIDEO = 2;
    public static final int COMMAND_RECORD_AUDIO = 3;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final WavRecorder audioRecorder = new WavRecorder();

    private static int extendVideo = 0;
    private static int extendAudio = 0;

    // TODO: Send recorded data to base station

    @Queue.Command(COMMAND_TAKE_PICTURE)
    public static void takePicture() {
        CameraHandler.takePicture();
    }

    @Queue.Command(COMMAND_RECORD_VIDEO)
    public static void recordVideo() {
        if (!CameraHandler.isRecording())
            CameraHandler.startRecording();
        else
            extendVideo++;
        handler.postDelayed(() -> {
            if (--extendVideo < 0 )
                CameraHandler.stopRecording();
        }, 10000);
    }

    @Queue.Command(COMMAND_RECORD_AUDIO)
    public static void recordAudio() {
        String filename = "";
        try {
            if (!audioRecorder.isRecording()) {
                File file = getFile(MEDIA_TYPE_AUDIO);
                Log.v("RecordingManager", file.toString());
                audioRecorder.startRecording(file.toString(), 44100, false);
                Log.v("RecordingManager", "Recording");
            } else
                extendAudio++;
            handler.postDelayed(() -> {
                if (--extendAudio < 0) return;
                {
                    try { audioRecorder.stopRecording(); }
                    catch (Exception e) { e.printStackTrace(); }
                }
            }, 10000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static File getFile(int type) {
        File mediaDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        if (!mediaDir.exists())
            mediaDir.mkdirs();

        String filename = mediaDir.getPath() + File.separator;
        String timestamp = GpsService.getFormattedUtcTime("yyyyMMdd_HHmmss");

        if (type == MEDIA_TYPE_IMAGE)
            filename += "IMG_" + timestamp + ".jpg";
        else if (type == MEDIA_TYPE_VIDEO)
            filename += "VID_" + timestamp + ".mp4";
        else if (type == MEDIA_TYPE_AUDIO)
            filename += "WAV_" + timestamp + ".wav";
        else
            return null;

        return new File(filename);
    }
}
