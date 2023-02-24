package org.tensorflow.lite.examples.detection;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import com.habib.finalproject.R;
import com.habib.finalproject.other.Tools;
import java.io.IOException;
import java.util.List;
import org.tensorflow.lite.examples.detection.customview.AutoFitTextureView;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;


public class LegacyCameraConnectionFragment extends Fragment {
  private static final Logger LOGGER = new Logger();
  /** Conversion from screen rotation to JPEG orientation. */
  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }

  private Camera camera;
  private Camera.PreviewCallback imageListener;
  private Size desiredSize;
  /** The layout identifier to inflate for this Fragment. */
  private int layout;
  /** An {@link AutoFitTextureView} for camera preview. */
  private AutoFitTextureView textureView;
  /**
   * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a {@link
   * TextureView}.
   */
  private final TextureView.SurfaceTextureListener surfaceTextureListener =
      new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(
            final SurfaceTexture texture, final int width, final int height) {

          int index = getCameraId();
          camera = Camera.open(index);



          try {
            Camera.Parameters parameters = camera.getParameters();
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes != null
                && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
              parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            List<Camera.Size> cameraSizes = parameters.getSupportedPreviewSizes();
            Size[] sizes = new Size[cameraSizes.size()];
            int i = 0;
            for (Camera.Size size : cameraSizes) {
              sizes[i++] = new Size(size.width, size.height);
            }
            Size previewSize =
                CameraConnectionFragment.chooseOptimalSize(
                    sizes, desiredSize.getWidth(), desiredSize.getHeight());
            parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
              camera.setDisplayOrientation(Tools.isEmulator() ? 0 : 90);
            camera.setParameters(parameters);
            camera.setPreviewTexture(texture);
          } catch (IOException exception) {
            camera.release();
          }

          camera.setPreviewCallbackWithBuffer(imageListener);
          Camera.Size s = camera.getParameters().getPreviewSize();
          camera.addCallbackBuffer(new byte[ImageUtils.getYUVByteSize(s.height, s.width)]);

          textureView.setAspectRatio(s.height, s.width);

          camera.startPreview();


        }




        @Override
        public void onSurfaceTextureSizeChanged(
            final SurfaceTexture texture, final int width, final int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
          return true;
        }

        @Override
        public void onSurfaceTextureUpdated(final SurfaceTexture texture) {}
      };
  /** An additional thread for running tasks that shouldn't block the UI. */
  private HandlerThread backgroundThread;
  public void flashLightOn() {

    try {
      if (getActivity().getPackageManager().hasSystemFeature(
              PackageManager.FEATURE_CAMERA_FLASH)) {
        Camera.Parameters p = camera.getParameters();
        p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        camera.setParameters(p);
        camera.startPreview();
      }
    } catch (Exception e) {
      e.printStackTrace();
      Toast.makeText(getActivity() , "Exception flashLightOn()",
              Toast.LENGTH_SHORT).show();
    }
  }
  public void flashLightOff() {

    try {
      if (getActivity().getPackageManager().hasSystemFeature(
              PackageManager.FEATURE_CAMERA_FLASH)) {
        Camera.Parameters p = camera.getParameters();
        p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        camera.setParameters(p);
        camera.startPreview();
      }
    } catch (Exception e) {
      e.printStackTrace();
      Toast.makeText(getActivity() , "Exception flashLightOn()",
              Toast.LENGTH_SHORT).show();
    }
  }

  public LegacyCameraConnectionFragment(
      final Camera.PreviewCallback imageListener, final int layout, final Size desiredSize) {
    this.imageListener = imageListener;
    this.layout = layout;
    this.desiredSize = desiredSize;
  }

  @Override
  public View onCreateView(
      final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
   View  root =   inflater.inflate(layout, container, false);

   return  root;
  }

  @Override
  public void onViewCreated(final View view, final Bundle savedInstanceState) {
    textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
  }

  @Override
  public void onActivityCreated(final Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
  }


  @Override
  public void onResume() {
    super.onResume();
    startBackgroundThread();
    // When the screen is turned off and turned back on, the SurfaceTexture is already
    // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
    // a camera and start preview from here (otherwise, we wait until the surface is ready in
    // the SurfaceTextureListener).

    if (textureView.isAvailable()) {
      camera.startPreview();
    } else {
      textureView.setSurfaceTextureListener(surfaceTextureListener);
    }


  }

  @Override
  public void onPause() {
    stopCamera();
    stopBackgroundThread();
    super.onPause();
  }

  /** Starts a background thread and its {@link Handler}. */
  private void startBackgroundThread() {
    backgroundThread = new HandlerThread("CameraBackground");
    backgroundThread.start();
  }

  /** Stops the background thread and its {@link Handler}. */
  private void stopBackgroundThread() {
    backgroundThread.quitSafely();
    try {
      backgroundThread.join();
      backgroundThread = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }
  }

  public void stopCamera() {
    if (camera != null) {
      camera.stopPreview();
      camera.setPreviewCallback(null);
      camera.release();
      camera = null;
    }
  }
  public void openCamera(){
    int index = getCameraId();
    camera = Camera.open(index);


    try {
      Camera.Parameters parameters = camera.getParameters();
      List<String> focusModes = parameters.getSupportedFocusModes();
      if (focusModes != null
              && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      }
      List<Camera.Size> cameraSizes = parameters.getSupportedPreviewSizes();
      Size[] sizes = new Size[cameraSizes.size()];
      int i = 0;
      for (Camera.Size size : cameraSizes) {
        sizes[i++] = new Size(size.width, size.height);
      }
      Size previewSize =
              CameraConnectionFragment.chooseOptimalSize(
                      sizes, desiredSize.getWidth(), desiredSize.getHeight());
      parameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
      camera.setDisplayOrientation(Tools.isEmulator() ? 0 : 90);
      camera.setParameters(parameters);
    } catch (Exception exception) {
      camera.release();
    }

    camera.setPreviewCallbackWithBuffer(imageListener);
    Camera.Size s = camera.getParameters().getPreviewSize();
    camera.addCallbackBuffer(new byte[ImageUtils.getYUVByteSize(s.height, s.width)]);

    textureView.setAspectRatio(s.height, s.width);

    camera.startPreview();

  }

  public static  int getCameraId() {
    CameraInfo ci = new CameraInfo();
    for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
      Camera.getCameraInfo(i, ci);
      if (ci.facing == CameraInfo.CAMERA_FACING_BACK) return i;
    }
    return -1; // No camera found
  }


  public  void setCameraDisplayOrientation(Activity activity)
  {
    android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
    android.hardware.Camera.getCameraInfo(getCameraId(), info);
    int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
    Log.e("Rotationvalue" , "degree :" + rotation);
    int degrees = 0;
    switch (rotation)
    {
      case Surface.ROTATION_0:
        degrees = 0;
        break;
      case Surface.ROTATION_90:
        degrees = 90;
        break;
      case Surface.ROTATION_180:
        degrees = 180;
        break;
      case Surface.ROTATION_270:
        degrees = 270;
        break;
    }
    degrees = 90;

    int result;
    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
    {
      result = (info.orientation + degrees) % 360;
      result = (360 - result) % 360; // compensate the mirror
    }
    else
    { // back-facing
      result = (info.orientation - degrees + 360) % 360;
    }
    camera.setDisplayOrientation(90);
  }
}
