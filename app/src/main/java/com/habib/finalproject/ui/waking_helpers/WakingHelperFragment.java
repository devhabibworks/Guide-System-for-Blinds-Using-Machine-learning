package com.habib.finalproject.ui.waking_helpers;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Toast;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
//import com.habib.finalproject.ml.SlipperyModel6;
import com.habib.finalproject.ml.SlipperyModel6;
import com.habib.finalproject.other.Constants;
import com.habib.finalproject.MainActivity;
import com.habib.finalproject.R;
import com.habib.finalproject.other.Tools;

import org.tensorflow.lite.examples.detection.CameraFragment;
import org.tensorflow.lite.examples.detection.DetectedObject;
import org.tensorflow.lite.examples.detection.ObjectLocation;
import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;
import org.tensorflow.lite.support.image.TensorImage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class WakingHelperFragment extends CameraFragment implements ImageReader.OnImageAvailableListener {
    SlipperyModel6 slipperyModel;
    List<DetectedObject> detectedObjects = new ArrayList<>(); // detected object will be store her
    FloatingActionButton floatingFlash  ;
    private static final int INPUT_SIZE = 320;
    private static final boolean MAINTAIN_ASPECT = false; // maintain aspect of the image when do cropping
    private static final float MINIMUM_CONFIDENCE = 0.5f;  // Minimum detection confidence to track a detection.
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean SAVE_PREVIEW_BITMAP = false; // save the image with is detected for testing purpose
    private Integer sensorOrientation; // the camera oriantation
    private long lastProcessingTimeMs; // used to calculate the time of processing
    private long timestamp = 0;        //
    private  long timeToSpeechIfExistObject;
    OverlayView overlayObjectTracker;  // view locat over all views used for tracking
    private boolean computingDetection = false; // flag used wither to go to the next image or not
    private Bitmap rgbCameraOriginalFrameBitmap = null; // that frame that detected by the Camera
    private Bitmap croppedFrameBitmap = null;  // tha 320 * 320 bitmap which we will use it as input
    private Matrix originalFrameToCropFrameTransform;
    private Matrix cropFrameToOriginalFrameTransform;
    private MultiBoxTracker multiBoxTracker;
    private static String TAG = "detection";
    private String speechStatement = "";

    public WakingHelperFragment(){
        super(R.layout.fragment_walking_helper);
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        Log.e("lifecycleCamera" , "onPreviewSizeChosen");



         timeToSpeechIfExistObject = SystemClock.uptimeMillis();
    multiBoxTracker = new MultiBoxTracker(getActivity()); //used to track the detected object and to draw a rectangle around them

        try {
            slipperyModel = SlipperyModel6.newInstance(getActivity()); // new model inilization


        } catch (final IOException e) {
            e.printStackTrace();

            Toast.makeText(
                    getActivity(), "model error", Toast.LENGTH_SHORT).show();


        }

        // get the width and height of the preview image in the camera
        previewWidth = size.getWidth(); // the Width of the preview in camera
        previewHeight = size.getHeight();// the Height of the preview in camera
        sensorOrientation = rotation - getScreenOrientation(); // used to get the orientation of the screen usually will be 90




        Log.i(TAG,"Camera orientation relative to screen canvas: " +  sensorOrientation     + " , rotation : " + rotation + "  screenOriantation : " + getScreenOrientation());
        Log.i(TAG,"Initializing at size  width  = " + previewWidth + "  height  = "+ previewHeight);


        rgbCameraOriginalFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888); // create empty bitmap of size 640 * 480
        croppedFrameBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888); // create empty bitmap of size 320 * 320 which is the imput Size


        // used to transform the bitmap when we crop it from the original frame
        // original frame -> crop frame
        // 640 * 480 -> 320 * 320
        originalFrameToCropFrameTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth,
                        previewHeight,
                        INPUT_SIZE,
                        INPUT_SIZE,
                        sensorOrientation,
                        MAINTAIN_ASPECT);

        // used to transform the bitmap when we get original frame scale from the croped scale
        // crop frame -> original  frame
        //   320 * 320 ->  640 * 480
        // simply we invert the  originalFrameToCropFrameTransform
        cropFrameToOriginalFrameTransform = new Matrix();

    /*If getActivity() matrix can be inverted, return true and if inverse is not null,
    set inverse to be the inverse of getActivity() matrix. If getActivity() matrix cannot be inverted, ignore inverse and return false.
    * */
        // q whey it need to invert the  cropFrameToOriginalFrameTransform
        originalFrameToCropFrameTransform.invert(cropFrameToOriginalFrameTransform);


        overlayObjectTracker =   getActivity().findViewById(R.id.tracking_overlay);
        // overlay create a layer above the screen used to draw  a rectangle

        overlayObjectTracker.addCallback(
                new OverlayView.DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        multiBoxTracker.draw(canvas);  // MultiBoxTracker
                        if (isDebug()) {
                            multiBoxTracker.drawDebug(canvas);
                        }
                    }
                });

        multiBoxTracker.setFrameConfiguration(
                previewWidth, //640
                previewHeight, // 480
                sensorOrientation // 90
        ); // the frame that the multiBoxTracker will work on



// control the flash
        floatingFlash  = getActivity().findViewById(R.id.floatingflash);


        floatingFlash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //  flashLightOn();
                if(Constants.isFlashOn) {
                    floatingFlash.setImageResource(R.drawable.ic_baseline_flash_off_24);

                    flashLightOff();
                }
                else {
                    floatingFlash.setImageResource(R.drawable.ic_baseline_flash_on_24);
                    flashLightOn();
                }

            }
        });
    }






    @Override
    protected void processImage() {
        Log.e("lifecycleCamera" , "processImage");
      timeToSpeechIfExistObject ++;
        final long currTimestamp = ++timestamp;
        overlayObjectTracker.postInvalidate(); // update the UI

        // No mutex needed as getActivity() method is not reentrant.
        if (computingDetection) { // to make sure what we process the next image after the previus  completed proccessing
            readyForNextImage(); // get the next image
            return;
        }

        computingDetection = true; // now we are prepare to get the next image


        Log.e(TAG,"Preparing image " + currTimestamp + " for detection in bg thread.");

        fillBitMapFromCameraFrame(); // get the frame from camera and put it into the frameBitmap
        readyForNextImage(); // after we finish fill the bitmap with it's pixels then tell the system we are ready for the next Image

        cropTheImage();// from 640 * 489 -> 320 * 320




// it simply use the threads  Handler to process the image
        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {

                        final long startTime = SystemClock.uptimeMillis(); // get the current time
                        TensorImage image = TensorImage.fromBitmap(croppedFrameBitmap);

                        SlipperyModel6.Outputs results = slipperyModel.process(image);

//                        // Gets result from DetectionResult.
//                        SlipperyModel6.DetectionResult detectionResult = results.getDetectionResultList().get(0);
//
//                            float locationresult = detectionResult.getScoreAsFloat();
//                            RectF category = detectionResult.getLocationAsRectF();
//                            String score = detectionResult.getCategoryAsString();
//
//
//                            Log.e("newModel" , "location : "  + locationresult);
//                            Log.e("newModel" , "category : "  + category);
//                            Log.e("newModel" , "score : "  + score);

                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime; // time after processing ...
                        Log.e("calculateTime" , "processing time = " +  lastProcessingTimeMs + " ms");


                        detectedObjects.clear();


                        final List<Classifier.Recognition> classifierRecognitionObjects =           // used to store all the detected object to draw arectangle on them
                                new LinkedList<Classifier.Recognition>();

                        for (final SlipperyModel6.DetectionResult object : results.getDetectionResultList()) {
                            final RectF location = object.getLocationAsRectF(); //


                            if (location != null && object.getScoreAsFloat() >= MINIMUM_CONFIDENCE) {


                            float locationresult = object.getScoreAsFloat();
                            RectF category = object.getLocationAsRectF();
                            String score = object.getCategoryAsString();


                            Log.e("sliperyModel" , "location : "  + locationresult);
                            Log.e("sliperyModel" , "category : "  + category);
                            Log.e("sliperyModel" , "score : "  + score);


                                cropFrameToOriginalFrameTransform.mapRect(location); // return the old 640 * 480 scale from the 320 * 320 scale

                                Classifier.Recognition mapResult = new Classifier.Recognition(object.getCategoryAsString() , object.getScoreAsFloat() ,  location  );

                               // mappedRecognitions.add(mapResult);//  add the detected result to  mappedRecognitions which well use later

                                classifierRecognitionObjects.add(mapResult);//  add the detected object to  classifierRecognitionObjects which well use later


                                if(location.left<INPUT_SIZE/2){
                                    if(location.right<(INPUT_SIZE/2)-10){
                                        //  location.left < 150 and location.right < 140

                                        //  re.add((String) object.getTitle()+"_L"); // means the object detected in left
                                        addDetectedObject(new DetectedObject( object.getCategoryAsString(), ObjectLocation.LEFT));
                                        continue;
                                    }
                                    else{
                                        //  location.left < 150 and location.right > 140
                                        // re.add((String) object.getTitle()+"_M"); // means the objected detected in medium
                                        addDetectedObject(new DetectedObject( object.getCategoryAsString(),ObjectLocation.MEDIUM));
                                        continue;
                                    }
                                }


                                else if(location.right>INPUT_SIZE/2){
                                    if(location.left>(INPUT_SIZE/2)-10){
                                        //  location.right > 150 and location.left > 140
                                        // re.add((String) object.getTitle()+"_R"); // means the object in the right
                                        addDetectedObject(new DetectedObject( object.getCategoryAsString(),ObjectLocation.RIGHT));
                                        continue;
                                    }
                                    else
                                    {
                                        //  location.right > 150 and location.left < 140
                                        //  re.add((String) object.getTitle()+"_M"); // means the object in the medium
                                        addDetectedObject(new DetectedObject( object.getCategoryAsString(),ObjectLocation.MEDIUM));
                                        continue;
                                    }

                                }





                            }
                        }

                        multiBoxTracker.trackResults(classifierRecognitionObjects, currTimestamp);
                        overlayObjectTracker.postInvalidate();

                        computingDetection = false;


                    }
                });
        Log.e("calculateTime2" , "speech value : " + timeToSpeechIfExistObject);
   if(SystemClock.uptimeMillis() -  timeToSpeechIfExistObject > 5000){
       Log.e("calculateTime2" , "speech");
       timeToSpeechIfExistObject = SystemClock.uptimeMillis();
       String speechStatement = getFinalResult();
       if(speechStatement != null){
           Log.e("calculateTime2", "output "  + speechStatement);
           Tools.vibrate(getActivity());
           MainActivity.convertTextToSpeech(speechStatement);
       }
   }


        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Toast.makeText(getActivity(),""+speechStatement,Toast.LENGTH_LONG).show();

                Log.e(TAG, "  "  + speechStatement);
                MainActivity.convertTextToSpeech(speechStatement);

            }
        });
    }
    void  cropTheImage(){
        // the following code use to crop the  rgbCameraOriginalFrameBitmap to  croppedFrameBitmap using  originalFrameToCropFrameTransform matrix

        final Canvas canvas = new Canvas(croppedFrameBitmap); // used as use canvas as medium

   /* Draw the bitmap using the specified matrix.
       Params:
    bitmap – The bitmap to draw
    matrix – The matrix used to transform the bitmap when it is drawn
    paint – May be null. The paint used to draw the bitmap

    */

        canvas.drawBitmap(rgbCameraOriginalFrameBitmap, originalFrameToCropFrameTransform, null);

        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) { // the default value is false
            ImageUtils.saveBitmap(croppedFrameBitmap);
        }
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
        Log.e("liveCyclerTracker" , "onResume");


        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());







    }


    void addDetectedObject(DetectedObject object ){

        // this function used to add the object to the detected objects if already exist it will be increase the counter by 1
        boolean isAlreadyFound = false;
        for (int j = 0; j <detectedObjects.size() ; j++) {
            DetectedObject detectedObject = detectedObjects.get(j);
            if(detectedObject.isEqual(object)){
                detectedObjects.get(j).incCounter();
                isAlreadyFound = true;
                break;
            }

        }

        if(!isAlreadyFound){
            detectedObjects.add(object);
        }




    }
    String  getFinalResult(){
        String finalResult = "";
        List<DetectedObject> leftObjects = getObjectByLocation(ObjectLocation.LEFT);
        List<DetectedObject> rightObjects = getObjectByLocation(ObjectLocation.RIGHT);
        List<DetectedObject>  mediumObjects = getObjectByLocation(ObjectLocation.MEDIUM);

        if(!leftObjects.isEmpty()){

            finalResult = "في اليساد هناك " ;
            for (DetectedObject object:leftObjects) {
                finalResult += object.getCounter() + " " + Constants.objectLabelsWithTranslationMap.get(object.getName()) + " , ";

            }
        }
        if(!mediumObjects.isEmpty()){

            finalResult += "في المنتصف هناك " ;
            for (DetectedObject object:mediumObjects) {
                finalResult += object.getCounter() + " " + Constants.objectLabelsWithTranslationMap.get(object.getName()) + " , ";

            }


        }
        if(!rightObjects.isEmpty()){

            finalResult += "في اليمين هناك " ;
            for (DetectedObject object:rightObjects) {
                finalResult += object.getCounter() + " " + Constants.objectLabelsWithTranslationMap.get(object.getName())+ " , ";

            }


        }




        return finalResult.isEmpty() ? null : finalResult;
    }

    List<DetectedObject>   getObjectByLocation(ObjectLocation location){
        List<DetectedObject> objects = new ArrayList<>();
        for (DetectedObject object:detectedObjects) {
            if(object.getLocation() == location)
                objects.add(object);

        }

        return objects;

    }
    @Override
    protected int getLayoutId() {

        Log.e("liveCyclerTracker" , "getLayoutId");
        return R.layout.camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

//
//    @Override
//    protected void setUseNNAPI(final boolean isChecked) {
//        runInBackground(() -> tensorflowClassifier.setUseNNAPI(isChecked));
//    }

    @Override
    protected void setNumThreads(final int numberOfThreads) {

    }

}






