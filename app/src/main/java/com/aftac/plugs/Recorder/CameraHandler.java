package com.aftac.plugs.Recorder;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.CameraProfile;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import com.aftac.plugs.Gps.GpsService;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

public class CameraHandler {
    private static final String LOG_TAG = CameraHandler.class.getSimpleName();
    private static final Camera camera = Camera.open();
    private static MediaRecorder recorder = new MediaRecorder();
    private static boolean isRecording = false;
    private static SurfaceTexture surface;

    public static void init() {
        int maxHeight = 0, maxWidth = 0;
        Camera.Parameters params = camera.getParameters();
        List<Camera.Size> sizes = params.getSupportedPictureSizes();
        for (Camera.Size size : sizes) {
            Log.v(LOG_TAG, size.height + "x" + size.width);
            if (size.height > maxHeight) {
                maxHeight = size.height;
                maxWidth = size.width;
            }
        }
        params.setPictureSize(maxWidth, maxHeight);

        //maxHeight = 0; maxWidth = 0;
        sizes = params.getSupportedPreviewSizes();
        for (Camera.Size size : sizes) {
            Log.v(LOG_TAG, size.height + "x" + size.width);
            if (size.height < maxHeight) {
                maxHeight = size.height;
                maxWidth = size.width;
            }
        }
        params.setPreviewSize(maxWidth, maxHeight);
        surface = new SurfaceTexture(1);
        try { camera.setPreviewTexture(surface); }
        catch (Exception e) { e.printStackTrace(); }

        try { camera.setPreviewTexture(surface); }
        catch (Exception e) { e.printStackTrace(); }
        camera.setParameters(params);


        //params.setPreviewSize()

    }

    public static void takePicture(){
        try {
            camera.startPreview();

            camera.startPreview();
            camera.takePicture(null, null, pictureCallback);

        }
        catch (Exception e) { e.printStackTrace(); }
    }

    public static boolean isRecording() { return isRecording; }

    public static void startRecording() {
        if (isRecording) return;
        camera.unlock();

        recorder.setCamera(camera);
        recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        recorder.setAudioSamplingRate(48000);
        recorder.setAudioEncodingBitRate(12200);
        recorder.setVideoEncodingBitRate(3000000);
        //recorder.setVideoSize(1280, 720);
        //recorder.setVideoFrameRate(24);

        recorder.setProfile(CamcorderProfile.get(CameraProfile.QUALITY_HIGH));
        try {
            recorder.setOutputFile(RecordingManager.getFile(MEDIA_TYPE_VIDEO).toString());
            recorder.prepare();
            recorder.start();
            isRecording = true;
        } catch (Exception e) {
            e.printStackTrace();
            recorder.reset();
            camera.lock();
        }
    }

    public static void stopRecording() {
        if (isRecording) {
            recorder.stop();
            isRecording = false;
            recorder.reset();
            camera.lock();
            isRecording = false;
        }
    }

    private static Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            File pictureFile = RecordingManager.getFile(MEDIA_TYPE_IMAGE);
            camera.stopPreview();

            if (pictureFile == null) {
                Log.v(LOG_TAG, "Error creating image file.");
                return;
            }

            try {
                FileOutputStream outStream = new FileOutputStream(pictureFile);
                outStream.write(data);
                outStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };


}
