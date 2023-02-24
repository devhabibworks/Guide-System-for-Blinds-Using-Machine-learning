//Color new code
        package com.habib.finalproject.ui.colors;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Toast;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.habib.finalproject.other.Constants;
import com.habib.finalproject.MainActivity;
import com.habib.finalproject.R;
import com.habib.finalproject.ml.ColorModel2;
import org.tensorflow.lite.examples.detection.CameraFragment;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
public class ColorsFragment  extends CameraFragment implements ImageReader.OnImageAvailableListener {
    ColorModel2 colorModel;// color model
    List<Category> categories = new ArrayList<>(); // detected object will be store her
    FloatingActionButton floatingFlash  ;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(480, 640);
    private boolean computingDetection = false; // flag used wither to go to the next image or not
    private Bitmap rgbCameraOriginalFrameBitmap = null; // that frame that detected by the Camera
    private  static final float MAINTAIN_SCORE = 0.4f;
    private static String TAG = "color_classifer";
    private String speechStatement = "";
    boolean isFlashOn = false;


    public ColorsFragment(){
        super(R.layout.fragment_colors);
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        isFlashOn = false;

        try {
            colorModel = ColorModel2.newInstance(getActivity()); // new model inilization


        } catch (final IOException e) {
            e.printStackTrace();

            Toast.makeText(
                    getActivity(), "Classifier Problem", Toast.LENGTH_SHORT).show();


        }

        // get the width and height of the preview image in the camera
        previewWidth = size.getWidth(); // the Width of the preview in camera
        previewHeight = size.getHeight();// the Height of the preview in camera

        rgbCameraOriginalFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888); // create empty bitmap of size 640 * 480




// control the flash
        floatingFlash  = getActivity().findViewById(R.id.floatingflash);


        floatingFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //  flashLightOn();
                if(isFlashOn) {
                    floatingFlash.setImageResource(R.drawable.ic_baseline_flash_off_24);


                    flashLightOff();
                    isFlashOn = !isFlashOn;
                }
                else {
                    floatingFlash.setImageResource(R.drawable.ic_baseline_flash_on_24);
                    flashLightOn();
                    isFlashOn = !isFlashOn;
                }

            }
        });
    }






    @Override
    protected void processImage() {

        // No mutex needed as getActivity() method is not reentrant.
        if (computingDetection) { // to make sure what we process the next image after the previus  completed proccessing
            readyForNextImage(); // get the next image
            return;
        }
        computingDetection = true; // now we are prepare to get the next image

        fillBitMapFromCameraFrame(); // get the frame from camera and put it into the frameBitmap
        readyForNextImage(); // after we finish fill the bitmap with it's pixels then tell the system we are ready for the next Image

// it simply use the threads  Handler to process the image
        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {

                        // Creates inputs for reference.
                        TensorImage image = TensorImage.fromBitmap(rgbCameraOriginalFrameBitmap);
                        ColorModel2.Outputs results = colorModel.process(image);
                        categories = results.getProbabilityAsCategoryList();

                        computingDetection = false;


                    }
                });



        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {


                String[] result = getFinalResult();
                if(result != null){
                    speechStatement = result[0];
                    Log.e(TAG, "الألوان : "  + result[1]);
                    Toast.makeText(getActivity(),result[1],Toast.LENGTH_LONG).show();
                    MainActivity.convertTextToSpeech(speechStatement);

                }
                else {


                    Toast.makeText(getActivity(),"لا يمكن تحديد اللون",Toast.LENGTH_LONG).show();
                    MainActivity.convertTextToSpeech("لا يمكن تحديد اللون");



                }


            }
        });
    }
    void  fillBitMapFromCameraFrame(){
          /*Replace pixels in the bitmap with the colors in the array. Each element in the array is a packed int representing
        a non-premultiplied ARGB Color in the sRGB color space.
                Params:
        pixels – The colors to write to the bitmap
        offset – The index of the first color to read from pixels[]
        stride – The number of colors in pixels[] to skip between rows.
                  Normally getActivity() value will be the same as the width of the bitmap, but it can be larger (or negative).
        x – The x coordinate of the first pixel to write to in the bitmap.
        y – The y coordinate of the first pixel to write to in the bitmap.
        width – The number of colors to copy from pixels[] per row
        height – The number of rows to write to the bitmap*/

        rgbCameraOriginalFrameBitmap.setPixels(   // frame from the camera
                getRgbBytes(),
                0,
                previewWidth,
                0,
                0,
                previewWidth,
                previewHeight
        ); // fill the empty Bitmap with pixels from the camera  of size 640 * 480
    }
    @Override
    public synchronized void onResume() {
        super.onResume();

        rgbBytes = null;  // so the it will call the  onPreviewSizeChosen again to inititalize the tracker


        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    String[]  getFinalResult(){

        int i =0;
        String finalResult = "";
        String details = "";
        int countOfColors = 0;
        Log.e("color_categories" , categories.toString());

        for (Category category: categories) {
            if(category.getScore() > MAINTAIN_SCORE){

                finalResult += Constants.colorsLabelsWithTranslationMap.get(category.getLabel().toLowerCase()) + " , ";
                details += Constants.colorsLabelsWithTranslationMap.get(category.getLabel().toLowerCase())+ " score :" +category.getScore() + " , ";

                countOfColors++;
            }

            Log.e(TAG , "index : "+ i + " value : " +  category.toString());
            i++;


        }
        if(countOfColors > 1){
            finalResult = " الألوان الموجوده هي ال" + finalResult;

        }
        else if(countOfColors == 1 && !finalResult.isEmpty()){
            finalResult = " اللون هو ال" + finalResult;

        }


        return finalResult.isEmpty() ? null : new String[]{finalResult , details};
    }

    @Override
    protected int getLayoutId() {
 return R.layout.camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    protected void setNumThreads(int numThreads) {

    }


}





