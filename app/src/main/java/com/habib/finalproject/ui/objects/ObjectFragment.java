package com.habib.finalproject.ui.objects;
import static org.opencv.objdetect.Objdetect.CASCADE_SCALE_IMAGE;
import android.graphics.BitmapFactory;
import android.media.ImageReader;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Size;
import android.view.View;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.habib.finalproject.other.Constants;
import com.habib.finalproject.model.FaceObject;
import com.habib.finalproject.other.FileUtils;
import com.habib.finalproject.MainActivity;
import com.habib.finalproject.R;
import com.habib.finalproject.other.Storage;
import com.habib.finalproject.other.Tools;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.face.FaceRecognizer;
import org.opencv.face.LBPHFaceRecognizer;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.tensorflow.lite.examples.detection.DetectedObject;
import org.tensorflow.lite.examples.detection.ObjectLocation;
import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;
import org.tensorflow.lite.examples.detection.CameraFragment;


public class ObjectFragment extends  CameraFragment implements ImageReader.OnImageAvailableListener {


    // variables
    private String speechStatement = ""; // the final output speech will be stored her .
    //tenser flow
    List<DetectedObject> detectedObjects = new ArrayList<>(); // detected object will be store her
    private Classifier tensorflowClassifier; // used to TenserFlowClasseifer the objects and get there locations
    private MultiBoxTracker multiBoxTracker;// used to draw a rectangle around the detected objects
    //opencv
    private LinkedHashMap<Integer,String> sortMap = new LinkedHashMap<Integer,String>();// used for labels
    Storage localSharedPref; // used to reach the localSharedPref storage to get the stored names
    private ArrayList<Mat> images=new ArrayList<Mat>(); // will store the image Matrix
    private FaceRecognizer openCVFaceRecognizer; // used to Recognizer the faces that already trained on TrainFragment on  lbph_train_data.xml
    private Mat gray;// gray matrix
    private CascadeClassifier openCVFaceClassifier; // used to detect the faces of persons that recgnized by  tensorflowClassifier

    //  String cascadeName = "lbpcascade_frontalface_improved.xml";
    String cascadeName = "haarcascade_frontalface_alt.xml"; // pretrained models used to openCVFaceRecognizer the faces
    private MatOfRect faces; // used to store the faces detected by openCVFaceClassifier
   String TAGCV = "OPENCVTAG";
    private ArrayList<String> names=new ArrayList<String>();// used to store the names of faces that already trained before
    private int label[] = new int[20]; //???
    private double confidence[] = new double[20];  //???
    // constants
    private static final int INPUT_SIZE = 300;  // input image size depend on the input image size that trained on model
    private static final boolean MAINTAIN_ASPECT = false; // maintain aspect of the image when do cropping
    private static final float MINIMUM_CONFIDENCE = 0.5f;  // Minimum detection confidence to track a detection.
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static String TAG = "detection";
    // Views
    FloatingActionButton floatingFlash  ; // to enable or disable the flash
    private boolean isFlashOn = false;

    OverlayView overlayObjectTracker;  // view located over all views used for tracking
    // camera
    private Integer sensorOrientation; // the camera oriantation
    private long lastProcessingTimeMs; // used to calculate the time of processing
    private long timestamp = 0;        //
    private boolean computingDetection = false; // flag used wither to go to the next image or not

    private Bitmap rgbCameraOriginalFrameBitmap = null; // that frame that created by the Camera
    private Bitmap croppedFrameBitmap = null;  // tha 300 * 300 bitmap which we will use it as input which is cropped from  rgbCameraOriginalFrameBitmap

    private Bitmap tempBitMap = null;        // for testing

    private Matrix originalFrameToCropFrameTransform; // this for transform the Bitmap Image from Size(640, 480) -> Size(300, 300)  to be used for tenser flow object Detection
    private Matrix cropFrameToOriginalFrameTransform; // this for transform the Bitmap Image from Size(300, 300) ->  Size(640, 480) to be used for tracking and draw  a rectangle around the objects
    private BaseLoaderCallback callbackLoader = new BaseLoaderCallback(getActivity()) {
        @Override
        public void onManagerConnected(int status) {
            switch(status) {
                case BaseLoaderCallback.SUCCESS:
                    faces = new MatOfRect();

                    openCVFaceRecognizer = LBPHFaceRecognizer.create(3, 8, 8, 8, 200);  /* openCVFaceRecognizer */
                    localSharedPref = new Storage(getActivity());
                    loadOldNamesAndImages();
                    openCVFaceClassifier = FileUtils.loadXMLS(getActivity(), cascadeName);     //  cascadeName = "lbpcascade_frontalface_improved.xml";


                    if(!names.isEmpty()){
                        if(loadDataFacesModel()){ // load the pretrained faces model(lbph_train_data.xml) if not found return false
                            setlabel(); // so the final result is sortMap : {0=بكر, 2=محسن, 1=محمد, 3=عبدالحبيب} as example
                        }
                    }

                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    }; // OpenCv callback used to inilize the opencv and call it's variable it useually called onResuem Live Cycle



    private void loadOldNamesAndImages() {
        names.clear();
        images.clear();
        names = localSharedPref.getListString("names"); // get the old value it used the sharedprefrence


        if (!names.isEmpty()) {  // means there is an old faces are already saved

            for (String imageName : names) {
                try {
                    File tempFile = new File(Tools.getStoragePath(), imageName + ".png");// get the images by name  and stored in the File tempFile
                    Bitmap bm = loadFromFile(tempFile); // convert the file image to Bitmap
                    Mat tempMat = new Mat(); // used to hold the Image As Map
                    Utils.bitmapToMat(bm, tempMat); // convert the Bitmap to Mat
                    Imgproc.cvtColor(tempMat, tempMat, Imgproc.COLOR_BGR2GRAY); // convert the image to the gray Scale // Iam not sure if this step is that important
                    images.add(tempMat); // old stored image will be her List<Mat>

                }
                catch (Exception e){
                    Toast.makeText(getActivity(), "Can't Reach The Image", Toast.LENGTH_SHORT).show();

                }

            }
        }

    }


    public Bitmap loadFromFile(File filename) {
        try {
            //File f = new File(filename);
            if (!filename.exists()) { return null; }
            Bitmap tmp = BitmapFactory.decodeFile(filename.getAbsolutePath());
            return tmp;
        } catch (Exception e) {
            return null;
        }
    }

    private void setlabel() {
        Log.e("setLabel2" , " setLabel names :" + names);
        // example names :[بكر, بكر, بكر, بكر, بكر, بكر, بكر, محسن, محسن, محمد, محمد, محمد, عبدالحبيب]
        // example images :[png.بكر, بكر.png, بكر, بكر, بكر, بكر, بكر, محسن, محسن, محمد, محمد.png, محمد.png , عبدالحبيب]  just  the names + .png  (:

        Log.e("setLabel2" , " setLabel images Size :" + images.size());
        // labels from the model =  1 1 1 1 1 1 1 3 3 2 2 2 4

        int nameAndLabelIndex=0;



        for(Mat image:images){

            int label =openCVFaceRecognizer.predict_label(image); // in our example it could be 1 , 2 , 3 , 4
            sortMap.put(label-1,names.get(nameAndLabelIndex)); // at the end sortMap : {0=بكر, 2=محسن, 1=محمد, 3=عبدالحبيب}


            //imagesLabels.add(l-1,names.get(iy));
            nameAndLabelIndex++;
        }

        Log.e(TAGCV , "sortMapFromOldModel : " +  sortMap.toString());
    }


    private boolean loadDataFacesModel() {
        String filename = FileUtils.loadTrained(); // load the trained model witch it's path is FinalProject/lbph_trained_data.xml
        Log.e("OldModel"  , "model fileName" + filename);
        if(filename == null)
            return false;
        else
        {
            openCVFaceRecognizer.read(filename); // read the old names
            return true;
        }
    }


    private FaceObject recognizeImage(Mat matPerson) {
        String labelName=null;
        Rect rect_crop_face = null; // used to get face rectangle dimintion
          //get the detected face that already detected by objectDetection then FaceDetection
        // I used the for because it could be more than face
        for (Rect face : faces.toArray()) {
            rect_crop_face = new Rect(face.x, face.y, face.width, face.height);
        }




        Mat croppedMatFace = new Mat(matPerson, rect_crop_face); // so we can use it as input for the predict function to recognize the face
        //javadoc: FaceRecognizer::predict(src, label, confidence)
        //predict(Mat src, int[] label, double[] confidence)
        // to do thing her we need to do some kind of grey skills her
        openCVFaceRecognizer.predict(croppedMatFace, label, confidence);



        //(testing...) the next for just for showing the result
        String labelString = "";
        String confidentString =  "";

        for (int j = 0; j < label.length; j++) {
            labelString +=   (label[j] +  " : ");
            confidentString +=  (confidence[j] + " : " );
        }
        // result example

        //   label      =  2 : 0 : 0 : 0 : 0 : 0 : 0 : 0 : 0 : 0 : 0 : 0 : 0 : 0 : 0 : 0 : 0 : 0 : 0 : 0 :
        //   confidence =  138.11522958030642 : 0.0 : 0.0 : 0.0 : 0.0 : 0.0 : 0.0 : 0.0 : 0.0 : 0.0 : 0.0 : 0.0 : 0.0 : 0.0 : 0.0 : 0.0 : 0.0 : 0.0 : 0.0 : 0.0 :

        Log.e("recognizeImage" , "labelName = " + labelName);
        Log.e("recognizeImage" , "label = " + labelString);
        Log.e("recognizeImage" , "confidence = " + confidentString);



        Log.e(TAGCV , "labels = " + labelString);
        Log.e(TAGCV , "confidences = " + confidentString);

        FaceObject faceObject = null;
        if (label[0] != -1 && (int) confidence[0] < 169) { // why 169
            // sortMap : {0=بكر, 2=محسن, 1=محمد, 3=عبدالحبيب}
            // label from the model 1 1 1 1 1 1 1 3 3 2 2 2 4
            // name                      confidence
            faceObject = new FaceObject(sortMap.get(label[0]-1) ,(int) confidence[0] );


            //example : faceObject.getName()  =  محمد
            //example : faceObject.getConfidence() = 138
        }


        return faceObject;
    }





    public ObjectFragment() {
        super(R.layout.fragment_object);
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {

        multiBoxTracker = new MultiBoxTracker(getActivity()); //used to track the detected object and to draw a rectangle around them

        try {
            tensorflowClassifier =
                    TFLiteObjectDetectionAPIModel.create(
                            getActivity().getAssets(),
                            "object_model_v1.tflite",     //  =object_model_v1.tflite
                            "file:///android_asset/object_labels_v1.txt",    //  =file:///android_asset/labelmap.txt
                            INPUT_SIZE,     //  = 300
                            true);  //  = true if UNIT.INT or false if UNIT.FLOAT32  in this example model is QUANTIZED



        } catch (final IOException e) {
            e.printStackTrace();

            Toast.makeText(
                    getActivity(), "Classifier Problem", Toast.LENGTH_SHORT).show();


        }

        // get the width and height of the preview image in the camera
        previewWidth = size.getWidth(); // the Width of the preview in camera
        previewHeight = size.getHeight();// the Height of the preview in camera
        sensorOrientation = rotation - getScreenOrientation(); // used to get the orientation of the screen usually will be 90
        //    sensorOrientation = 0; // used to get the orientation of the screen usually will be 90
        // note rotation is important to used it later when cropping

        Log.i(TAG,
                "Camera orientation relative to screen canvas: " +  sensorOrientation     + " , rotation : " + rotation + "  screenOriantation : " + getScreenOrientation());
        Log.i(TAG,"Initializing at size  width  = " + previewWidth + "  height  = "+ previewHeight);


        rgbCameraOriginalFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888); // create empty bitmap of size 640 * 480
        croppedFrameBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888); // create empty bitmap of size 300 * 300 which is the imput Size


        // used to transform the bitmap when we crop it from the original frame
        // original frame -> crop frame
        // 640 * 480 -> 300 * 300
        originalFrameToCropFrameTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth,
                        previewHeight,
                        INPUT_SIZE,
                        INPUT_SIZE,
                        sensorOrientation, //
                        MAINTAIN_ASPECT);

        // used to transform the bitmap when we get original frame scale from the croped scale
        // crop frame -> original  frame
        //   300 * 300 ->  640 * 480
        // simply we invert the  originalFrameToCropFrameTransform
        cropFrameToOriginalFrameTransform = new Matrix();

    /*If getActivity() matrix can be inverted, return true and if inverse is not null,
    set inverse to be the inverse of getActivity() matrix. If getActivity() matrix cannot be inverted, ignore inverse and return false.
    * */
        // q whey it need to invert the  cropFrameToOriginalFrameTransform
        originalFrameToCropFrameTransform.invert(cropFrameToOriginalFrameTransform);


        overlayObjectTracker =   getActivity().findViewById(R.id.tracking_overlay); // this View came with tenser flow  tools
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
                if(isFlashOn) {
                    floatingFlash.setImageResource(R.drawable.ic_baseline_flash_off_24);

                    flashLightOff();
                    isFlashOn = !isFlashOn;
                }
                else {
                    floatingFlash.setImageResource(R.drawable.ic_baseline_flash_on_24);
                    flashLightOn();//////////////////////////
                    isFlashOn =  !isFlashOn;
                }


            }
        });
    }







    @Override
    protected void processImage() {

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
        cropTheImage();// from 640 * 489 -> 300 * 300

        readyForNextImage(); // after we finish fill the bitmap with it's pixels then tell the system we are ready for the next Image





// it simply use the threads  Handler to process the image
        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {

                        final long startTime = SystemClock.uptimeMillis(); // get the current time
                        // tensorflowClassifier used to detect objects  and Persons
                        final List<Classifier.Recognition> results =
                                tensorflowClassifier.recognizeImage(croppedFrameBitmap);  // Classifier the image
                        // Bitmap  copyBitmap =Bitmap.createBitmap(croppedFrameBitmap);
                        Bitmap copyBitmap=croppedFrameBitmap.copy(Bitmap.Config.ARGB_8888,true);

//                        final Canvas canvas = new Canvas(cropCopyBitmap);
//                        final Paint paint = new Paint();
//                        paint.setColor(Color.RED);
//                        paint.setStyle(Paint.Style.STROKE);
//                        paint.setStrokeWidth(2.0f);
//                        cropCopyBitmap = Bitmap.createBitmap(croppedFrameBitmap);
//                        final Canvas canvas = new Canvas(cropCopyBitmap);
//                        final Paint paint = new Paint();
//                        paint.setColor(Color.RED);
//                        paint.setStyle(Paint.Style.STROKE);
//                        paint.setStrokeWidth(2.0f);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime; // time after processing ...
                        detectedObjects.clear(); // clear the old objects
                        Log.e("trackingtime" ,"processing time : " +  lastProcessingTimeMs);


                        final List<Classifier.Recognition> classifierRecognitionObjects =           // used to store all the detected object
                                new LinkedList<Classifier.Recognition>();

                        for (Classifier.Recognition object : results) {
                            RectF location = object.getLocation(); //
                            Log.e("location_tracking" , "orignal location : " +  location.toString());


                            if (location != null && object.getConfidence() >= MINIMUM_CONFIDENCE) {
                                Classifier.Recognition   modifiedObject   =  filterBaseOnLocationAndTitle(object , location , copyBitmap);
                                //canvas.drawRect(location, paint);
                                if(modifiedObject.getPersonName() != null){
                                    object.setPersonKnown(modifiedObject.getPersonName());
                                }
                                Log.e("location_tracking" , "after processing  location : " +  object.getLocation().toString());

                                cropFrameToOriginalFrameTransform.mapRect(location); // return the old 640 * 480 scale from the 300 * 300 scale
                                Log.e("location_tracking" , "after transform  location : " +  location.toString());

                                object.setLocation(location); //  set the transform Location to the object
                                classifierRecognitionObjects.add(object);//  add the detected object to  classifierRecognitionObjects which well use later






                            }
                        }
                        //Toast.makeText(CameraActivity.getActivity(), results+""+left+","+middle+","+right, Toast.LENGTH_LONG).show();
                        String finalResult = getFinalResult();
                        if(finalResult != null){ // if there is no value in L or R or M

                            speechStatement=finalResult;
                        }
                        else{
                            speechStatement="";
                        }



                        multiBoxTracker.trackResults(classifierRecognitionObjects, currTimestamp);
                        overlayObjectTracker.postInvalidate();

                        computingDetection = false;


                    }
                });



        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Toast.makeText(getActivity(),"Output_speech= "+speechStatement,Toast.LENGTH_LONG).show();

                Log.e(TAG, "output _speech "  + speechStatement);
                MainActivity.convertTextToSpeech(speechStatement);

            }
        });
    }

    private Classifier.Recognition filterBaseOnLocationAndTitle(Classifier.Recognition object , RectF location , Bitmap copyBitmap) {
        object = checkIfPerson(object , location ,copyBitmap);


        if(location.left<INPUT_SIZE/2){
            if(location.right<(INPUT_SIZE/2)-10 ){
                //  location.left < 150 and location.right < 140

                addDetectedObject(new DetectedObject( object.getTitle(),ObjectLocation.LEFT , object.getPersonName() ));

            }
            else{
                //  location.left < 150 and location.right > 140

                addDetectedObject(new DetectedObject( object.getTitle(),ObjectLocation.MEDIUM , object.getPersonName()));

            }
        }
        else if(location.right>INPUT_SIZE/2){
            if(location.left>(INPUT_SIZE/2)-10){
                //  location.right > 150 and location.left > 140
                // re.add((String) object.getTitle()+"_R"); // means the object in the right
                addDetectedObject(new DetectedObject( object.getTitle(),ObjectLocation.RIGHT ,  object.getPersonName()));

            }
            else
            {
                //  location.right > 150 and location.left < 140
                //  re.add((String) object.getTitle()+"_M"); // means the object in the medium
                addDetectedObject(new DetectedObject( object.getTitle(),ObjectLocation.MEDIUM , object.getPersonName()));

            }

        }

        return object;


    }

    private Classifier.Recognition  checkIfPerson(Classifier.Recognition object , RectF location , Bitmap copyBitm) {
        if (object.getTitle().equals("person")){

            Log.e("faceRecgnization" , "it's a person");
            // Log.e("faceRecgnization" , "location : " + location.toString());
            //        RectF( left      , top       , right       , bottom     );
            //example : RectF(141.08357  , 87.37195  , 607.00964   , 385.72845  );

            if(location.left>=0 && location.top>=0 &&
                    location.width()>=0 && location.height()>=0 // this true condditions
                    && (location.width()+location.left)<=INPUT_SIZE &&
                    (location.top+location.height())<=INPUT_SIZE)
            {
                Log.e("faceRecgnization" , "pass the location test");
                Bitmap personCropBitmap=Bitmap.createBitmap(
                        copyBitm,
                        (int)location.left,
                        (int)location.top,
                        (int)location.width(),
                        (int)location.height());
                tempBitMap=personCropBitmap.copy(Bitmap.Config.ARGB_8888,true);
                gray= new Mat();
                Utils.bitmapToMat(tempBitMap,gray);

                faces  =  CascadeRecGray(gray);
                if(!faces.empty()) {
                    if(faces.toArray().length > 1) {
                        Toast.makeText(getActivity(), "Mutliple Faces Are not allowed", Toast.LENGTH_SHORT).show();
                        Log.e("faceRecgnization" , "more than one face");

                    }
                    else {
                        if(gray.total() == 0) {
                            Log.i(TAG, "Empty gray image");
                            Log.e("faceRecgnization" , "empty gray");
                            return object;
                        }
                        Log.i(TAG, "4");
                        if( !names.isEmpty()){
                            FaceObject faceObject=recognizeImage(gray);

                            if(faceObject!=null){
                                //   personName.add(temp+"_"+itr);
                                Log.e("faceRecgnization" , "name is :" + faceObject.toString());


                                object.setPersonKnown(faceObject.getName());

                                // you can also deal with   faceObject.getConfident()

                             //   if(!MainActivity.isSpeeching())       MainActivity.convertTextToSpeech(faceObject.getName());
                                return  object;

                            }
                        }



                    }
                }

                else {
                    //  Toast.makeText(getActivity(), "لم يتم التعرف علي الشخص", Toast.LENGTH_SHORT).show();
                    Log.e("faceRecgnization" , "no faces");
                }
            }

        }




        return  object;

    }

    private MatOfRect CascadeRecGray(Mat mConvertedGray) {


        //  if(!Tools.isEmulator())  Core.flip(mConvertedGray.t() , mConvertedGray , 1 ); // emulator

        Imgproc.cvtColor(mConvertedGray, mConvertedGray, Imgproc.COLOR_RGBA2GRAY);
        //   openCVFaceClassifier.detectMultiScale(gray,faces,1.1,3,0|CASCADE_SCALE_IMAGE, new org.opencv.core.Size(30,30));


        //////


        faces = new MatOfRect();
        // original frame is - 90 degree so we have to rotate is to 90 to get propare face for detection
        //  if(!Tools.isEmulator())  Core.flip(mConvertedGray.t() , mConvertedGray , 1 ); // emulator

        // Mat mConvertedGray = new Mat();
        //   Imgproc.cvtColor(rgba , mConvertedGray , Imgproc.COLOR_RGBA2GRAY);


        if(openCVFaceClassifier != null){
            //                                 input   output                                // minimum size of output
            // openCVFaceClassifier.detectMultiScale(mConvertedGray , faces , 1.1 , 2  ,  2  ,new org.opencv.core.Size(30,30) );
            openCVFaceClassifier.detectMultiScale(mConvertedGray,faces,1.1,3,0|CASCADE_SCALE_IMAGE, new org.opencv.core.Size(30,30));

        }
        // loop throw all faces
        Rect[] facesArray = faces.toArray();
//        for (int i = 0; i < facesArray.length ; i++) {
//
//            // draw face on original frame mRgba
//            Imgproc.rectangle(gray , facesArray[i].tl() , facesArray[i].br() , new Scalar(0, 255 , 0,255) , 2);
//
//        }
//
//
//        // rotate back original frame to -90 degree
//        if(!Tools.isEmulator())   Core.flip(gray.t(),gray , 0); // emulator
//        //Imgproc.cvtColor(mRgba , mRgba , Imgproc.COLOR_BGR2RGBA);

        return faces;

        // all done
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
        // srs bitmap                   transfomration matrix
        canvas.drawBitmap(rgbCameraOriginalFrameBitmap, originalFrameToCropFrameTransform, null);

        // For examining the actual TF input.

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

        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "System library loaded successfully");
            callbackLoader.onManagerConnected(BaseLoaderCallback.SUCCESS);

        } else {

            Log.i(TAG, "unable to load the system library");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, getActivity(), callbackLoader);

        }

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
                Log.e("personName" , "peronnName" + object.getPersonName()  + "label : " + object.getName());


                if(object.getPersonName() != null){

                    finalResult +=  " , " + object.getPersonName() + " , ";


                }
                else {
                    finalResult += object.getCounter() + " " + Constants.objectLabelsWithTranslationMap.get(object.getName()) + " , ";
                }

            }
        }
        if(!mediumObjects.isEmpty()){

            finalResult += "في المنتصف هناك " ;
            for (DetectedObject object:mediumObjects) {
                Log.e("personName" , "peronnName" + object.getPersonName()  + "label : " + object.getName());
                if(object.getPersonName() != null){
                    finalResult +=  " , " + object.getPersonName() + " , ";

                }
                else {
                    finalResult += object.getCounter() + " " + Constants.objectLabelsWithTranslationMap.get(object.getName()) + " , ";
                }

            }


        }
        if(!rightObjects.isEmpty()){

            finalResult += "في اليمين هناك " ;
            for (DetectedObject object:rightObjects) {
                Log.e("personName" , "peronnName" + object.getPersonName()  + "label : " + object.getName());
                if(object.getPersonName() != null){
                    finalResult +=  " , " + object.getPersonName() + " , ";

                }
                else {
                    finalResult += object.getCounter() + " " + Constants.objectLabelsWithTranslationMap.get(object.getName()) + " , ";
                }

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


//    @Override
//    protected void setUseNNAPI(final boolean isChecked) {
//        runInBackground(() -> tensorflowClassifier.setUseNNAPI(isChecked));
//    }

    @Override
    protected void setNumThreads(final int numberOfThreads) {
        runInBackground(() -> tensorflowClassifier.setNumThreads(numberOfThreads));
    }

}






















