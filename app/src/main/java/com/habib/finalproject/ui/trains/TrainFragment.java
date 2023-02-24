package com.habib.finalproject.ui.trains;
import static org.opencv.objdetect.Objdetect.CASCADE_SCALE_IMAGE;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.os.CountDownTimer;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.habib.finalproject.other.FileUtils;
import com.habib.finalproject.MainActivity;
import com.habib.finalproject.R;
import com.habib.finalproject.other.Storage;
import com.habib.finalproject.other.Tools;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.face.FaceRecognizer;
import org.opencv.face.LBPHFaceRecognizer;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;


 public class TrainFragment extends Fragment    implements CameraBridgeViewBase.CvCameraViewListener2 {
   
    public TrainFragment(){} // empty constructor
     
   //Views  
   FloatingActionButton floatingAddPerson  ;
   FloatingActionButton floatingShowAllPersons ;
   FloatingActionButton floatingDeletePerson ;
   FloatingActionButton floatingDeleteAllPersons;
   JavaCameraView openCVCamera; //CameraView that used to get the frames
   Button btnScreen;
   
    // constants
    private static String TAG = "OpenCVDetectFace";
    private static String TAGButton = "OpenCVDetectFaceButton";


    private static final int PERMS_REQUEST_CODE = 123; // for permission
   // variables
    private Mat rgba, gray; // red green blue Matrix , gray matrix image
    private  MatOfRect detectFaces ;
    private ArrayList<Mat> images = new ArrayList<Mat>(); // the matrix of the image that stored on FinalProject
    private ArrayList<String> names = new ArrayList<String>(); // names in english
    private File xmFile , tempImgFile;
    private boolean delName = false; // delete the name or not
    int clickNumberDeleteAll = 0;
    Dialog dialogName; // small popup windows used to get the names when user is offline

   // objects
    private CascadeClassifier openCVFaceClassifier; // used to detect the faces
    private CascadeClassifier openCVFaceClassifier2; // used to detect the faces when clicking the screen 
    private FaceRecognizer openCVFaceRecognizer; // used to train the object to recognize the faces
    private Storage localSharedPref; // object used to reach the localSharedPref storage
    private SpeechRecognizer speechRecognizer; // used to reconize the Speech
     
    // just pre trained models that used to track the faces
    //String cascadeName = "lbpcascade_frontalface_improved.xml";
    String cascadeName = "haarcascade_frontalface_alt.xml";



     // load and  init the opencv variable
     private BaseLoaderCallback callbackLoader = new BaseLoaderCallback(getActivity()) {
         @Override
         public void onManagerConnected(int status) {

             switch (status) {
                 case BaseLoaderCallback.SUCCESS: // in case that opencv worked

                     if(!openCVCamera.isActivated())  openCVCamera.enableView(); // enable the camera
                    loadOldNamesAndImages();
                       Log.i(TAG, "Images " + images.size());
                     Toast.makeText(getActivity(), "name= " + names + " image= " + images.size(), Toast.LENGTH_LONG).show();
                    break;
                 default:
                     super.onManagerConnected(status);
                     break;
             }

             super.onManagerConnected(status);
         }

     };

     private void loadOldNamesAndImages() {
         Map<String , Integer> imageCounter = new HashMap<>(); // used to get the images from folders
         names = localSharedPref.getListString("names"); // get the old value it used the sharedprefrence

    if (!names.isEmpty()) {  // means there is an old faces are already saved
             images.clear();
     for (String imageName : names) {
                 if(imageCounter.get(imageName) != null){
                     int oldCounter = imageCounter.get(imageName);
                     imageCounter.put(imageName , ++oldCounter);
                 }
                 else{
                     imageCounter.put(imageName,0);
                 }

                 File tempFile = new File(Tools.getStoragePath(),  imageName + "/"+imageName +imageCounter.get(imageName) + ".png");// get the images by name  and stored in the File tempFile
                 Bitmap bm = loadFromFile(tempFile); // convert the file image to Bitmap
                 Mat tempMat = new Mat(); // used to hold the Image As Map
                 Utils.bitmapToMat(bm, tempMat); // convert the Bitmap to Mat
                 Imgproc.cvtColor(tempMat, tempMat, Imgproc.COLOR_RGB2GRAY); // convert the image to the gray Scale // Iam not sure if this step is that important
                 images.add(tempMat); // old stored image will be her List<Mat>

             }
         }

     }
     
   // first methode to be called in fragment
     @Nullable
     @Override
     public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

         View root = inflater.inflate(R.layout.fragment_train, container , false);

         init(root);
         return  root;

     }

     private void init(View root) {
      // link the views
         floatingAddPerson =root.findViewById(R.id.floatAddPerson);
         floatingShowAllPersons = root.findViewById(R.id.floatShowAllPersons);
         floatingDeletePerson = root.findViewById(R.id.floatDeleteperson);
         floatingDeleteAllPersons = root.findViewById(R.id.floatDeleteAllPersons);
         openCVCamera =root.findViewById(R.id.java_camera_view);
         btnScreen =root. findViewById(R.id.take_picture_button);
         dialogName = new Dialog(getActivity(), R.style.PauseDialog);
          // define the path of images and model if not found create new one
         // FinalProject is the path where we will save the image and the stored xml faces
        

     // permission stuff
         if(hasPermission()){

             initializeCamera(openCVCamera, CameraBridgeViewBase.CAMERA_ID_BACK);
         }
         else {
             requestPems();
         }


         localSharedPref = new Storage(getActivity()); // storage variable using sharedprefrence


         buttonStuff();
         floatingButtons();
         
         
     }

     private void floatingButtons() {
         floatingAddPerson.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {

                 Tools.vibrate(getActivity());

                 delName = false;
                 MainActivity.convertTextToSpeech("إضافة وجه ،" +  "اطلب منه البقاء أمام الكاميرا والمس الشاشة.");

}
         });
         
         
         floatingShowAllPersons.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {

                 Tools.vibrate(getActivity());
                 names=localSharedPref.getListString("names");
                 if(!names.isEmpty()){
                     Set<String> uniqueLabelsSet = new HashSet<>(names); // Get all unique labels with out repeat
                     String []  uniqueLabels = uniqueLabelsSet.toArray(new String[uniqueLabelsSet.size()]); // Convert the Set to String array, so we can read the values from the indices
                     List<String> namesUniqeList = Arrays.asList(uniqueLabels);

                     MainActivity.convertTextToSpeech(" الأسماء "+namesUniqeList);
                     Toast.makeText(getActivity(), namesUniqeList.toString(), Toast.LENGTH_SHORT).show();
                     Log.e(TAG, "names = " + namesUniqeList);
                 }else{
                     MainActivity.convertTextToSpeech("لا بوجد أسماء إلى ألاّن.");
                 }



             }
         });
         
         floatingDeletePerson.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {

                 Tools.vibrate(getActivity());

                 delName = true;

                 if(Tools.isOnline(getActivity()) && !Tools.isEmulator()) {
                     MainActivity.convertTextToSpeech("انطق الأسم المراد حذفه.");
                     person_input_speak_arabic(); // this function only working on the present of intent
                      }
                 else {
                     MainActivity.convertTextToSpeech("أكتب الأسم المراد حذفه. استعن بصديق ! , او قم بالإتصال بالإنترنت لتفعيل الآوامر الصوتيه.");
                     showDialogText();
                 }



             }
         });
         floatingDeleteAllPersons.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {


                 Tools.vibrate(getActivity());
                 MainActivity.stopTextToSpeach();
                 clickNumberDeleteAll++;

                 Handler handler = new Handler();
                 handler.postDelayed(new Runnable() {
                     @Override
                     public  void run() {
                         if (clickNumberDeleteAll == 1) {
                             MainActivity.convertTextToSpeech("اضغط مرتين لحذف كل الوجوه");

                         } else if (clickNumberDeleteAll == 2) {

                             try{
                                 
                                 Boolean isDeleted=Tools.deleteDirectory(Tools.getStoragePath());
                                if(isDeleted) {
                                    Toast.makeText(getActivity(), "file Found and deleted", Toast.LENGTH_LONG).show();
                                    localSharedPref.remove("names");
                                    names=new ArrayList<String>();
                                    images.clear();
                                    MainActivity.convertTextToSpeech("تم حذف أسماء الجميع وصورهم");
                                }
                               else
                                   Toast.makeText(getActivity(),"Can't Delete Files",Toast.LENGTH_LONG).show();


                             }catch (Exception e){
                                 Toast.makeText(getActivity(),"Error: "+e.getMessage(),Toast.LENGTH_LONG).show();
                             }

                             // if the file not exist then we will create it
                             
                         }
                         clickNumberDeleteAll = 0;



                     }
                 }, 500);







                 
             }
         });
     }

     private void showDialogText() {
         dialogName.setContentView(R.layout.popup_window);


         EditText editName = dialogName.findViewById(R.id.editName);
         TextView textOK = dialogName.findViewById(R.id.textOK);


         textOK.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {
                 Tools.vibrate(getActivity());
                 String name = editName.getText().toString();
                 if(!name.isEmpty()){
                     if (delName) { // delete flag if delName = true then delete we are deleting name else we are adding name

                         if (isNameExistOnLables(name)) {// means found the name that the user has said
                             trainFacesModelAndStoreIt(); // that not contain the deleted Image and name
                             MainActivity.convertTextToSpeech(name + "تم حذف ألاسم وصورتة");
                             Toast.makeText(getActivity(), "name : " + names.toString() + "images : " + images.size(), Toast.LENGTH_SHORT).show();

                         } else {

                             MainActivity.convertTextToSpeech(" اّسف " + name + "الأسم غير موجود");

                         }

                     } else {
                         addLabel(name);
                         MainActivity.convertTextToSpeech("جديد" + name + "تم إضافة أسم و صورة");
                         trainFacesModelAndStoreIt();  // train the new  image and label with the old names and images

                     }

                     if(dialogName.isShowing()) dialogName.dismiss();


                 }
                 else {
                     MainActivity.convertTextToSpeech("رجاء أضف الأسم");
                 }

             }
         });




         dialogName.show();
         dialogName.setCancelable(false);


         Window window = dialogName.getWindow();
         window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);// to make the dialog take the hole screen width




     }

     // on create the first function in the activity
    @Override
   public   void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


    }
    /**
     * This method is invoked when camera preview has started. After this method is invoked
     * the frames will start to be delivered to client via the onCameraFrame() callback.
     *
     * @param width  -  the width of the frames that will be delivered
     * @param height - the height of the frames that will be delivered
     */
    @Override
 public void onCameraViewStarted(int width, int height) {
        // initilize the object
        rgba = new Mat(); // used to store the rgba Matrix image
        gray = new Mat(); // used to store the gray scale image
        // openCVFaceClassifier used to detect faces
        openCVFaceClassifier = FileUtils.loadXMLS(getActivity(), cascadeName); // initialize the object that will used to detect the faces
        openCVFaceClassifier2 = FileUtils.loadXMLS(getActivity(), cascadeName); // initialize the object that will used to detect the faces that used for training
        // cascadeName could be  lbpcascade_frontalface_improved.xml or  haarcascade_frontalface_alt.xml
        // this is a pretrained models that used to detect faces

    }

    /**
     * This method is invoked when camera preview has been stopped for some reason.
     * No frames will be delivered via onCameraFrame() callback after this method is called.
     */

    @Override
  public    void onCameraViewStopped() {
        rgba.release();
        gray.release();

    }


    /**
     * This method is invoked when delivery of the frame needs to be done.
     * The returned values - is a modified frame which needs to be displayed on the screen.
     * TODO: pass the parameters specifying the format of the frame (BPP, YUV or RGB and etc)
     */
    @Override
   public  Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        Mat mGrayTmp = inputFrame.gray();
        Mat rgba   = inputFrame.rgba();
         gray = mGrayTmp;
      Imgproc.resize(gray, gray, new Size(200,200.0f/ ((float)gray.width()/ (float)gray.height())));
//    Imgproc.cvtColor(rgba , gray  , Imgproc.COLOR_RGBA2GRAY);

       return rgba; // just return the original frame from the camera with out any detection
     //  return  CascadeRec(rgba); //if you want to track the faces while training
    }
    void buttonStuff(){
 btnScreen.setOnClickListener(new View.OnClickListener() {
            @Override
          public    void onClick(View v) {
                delName = false;
                Log.e(TAGButton , "button clicked");
             MainActivity.stopTextToSpeach();
                if (!delName) {   // this btnScreen only used to detect faces
            Log.e(TAGButton  , "NOT DELETE");
                Mat  cachedMat = CascadeRecGray(gray); // detect faces
                 if (!detectFaces.empty()) {
                        if (detectFaces.toArray().length > 1) // means there is more than one face
                          {
                            MainActivity.convertTextToSpeech("يجب أن يكون شخص واحد فقط امام ألكاميرا حاول مره أخرى");
                            Log.e(TAGButton, "more than one person");
                           }
                        else { // means detected face and the only one face
                            if (gray.total() == 0) {
                                MainActivity.convertTextToSpeech("لا يوجد وجه");
                                Log.e(TAGButton , "there is no face");
                            }
                            else {
                                cropImageFace(cachedMat , detectFaces); // corp the detected face so you can use it later for training also to save it


                                Toast.makeText(getActivity(), "Face Detected", Toast.LENGTH_SHORT).show();
                                if(Tools.isOnline(getActivity()) && !Tools.isEmulator()) { // because Emulator not supporting Arabic TextToSpeech

                                    MainActivity.convertTextToSpeech("تم التقاط الصورة ، قل الاسم الآن");
                                    final Handler handler = new Handler();

                                    handler.postDelayed(new Runnable() {
                                        @Override
                                        public    void run() {

                                            person_input_speak_arabic();

                                        }
                                    }, 3000);

                                }
                                else {
                                    MainActivity.convertTextToSpeech("تم التقاط الصورة ، أضف الآسم الآن");
                                    showDialogText();

                                }

                            }
                        }

                    }  //end of  !detectFaces.empty()
                    else {
                        Log.e(TAGButton , "NO Face");
                        MainActivity.convertTextToSpeech("اّسف لا يوجد وجه! حاول مرة اخرى.");
                    }


                }

            }
        });

    }

    private Mat CascadeRec(Mat mRgba) {
        // original frame is - 90 degree so we have to rotate is to 90 to get propare face for detection
        if(!Tools.isEmulator())  Core.flip(mRgba.t() , mRgba , 1 ); // emulator

        Mat mRgb = new Mat();
        Imgproc.cvtColor(mRgba , mRgb , Imgproc.COLOR_RGBA2RGB);     // convert it into RGB from RGBA

        MatOfRect faces = new MatOfRect();

        if(openCVFaceClassifier != null){
            //                          input   output                                   // minimum size of output
            openCVFaceClassifier.detectMultiScale(mRgb , faces ,  1.1 , 2  ,  2  ,new Size(30,30) );
       }
        // loop throw all faces
        Rect[] facesArray = faces.toArray();
        for (int i = 0; i < facesArray.length ; i++) {
              // draw face on original frame mRgba
            Imgproc.rectangle(mRgba , facesArray[i].tl() , facesArray[i].br() , new Scalar(0, 255 , 0,255) , 2);

        }


        // rotate back original frame to -90 degree so you can show the changes
        if(!Tools.isEmulator())   Core.flip(mRgba.t(),mRgba , 0); // emulator
     //  Imgproc.cvtColor(mRgba , mRgba , Imgproc.COLOR_BGR2RGBA);

        return mRgba;

        // all done
    }
    // I think it's
     // just for testing
     private Mat CascadeRec2(Mat mRgba) {
         // original frame is - 90 degree so we have to rotate is to 90 to get propare face for detection
         if(!Tools.isEmulator())  Core.flip(mRgba.t() , mRgba , 1 ); // emulator
         //Imgproc.cvtColor(mRgba , mRgba , Imgproc.COLOR_BGR2GRAY);
         // convert it into RGB
         Mat mConvertedGray = new Mat();
        Imgproc.cvtColor(mRgba , mConvertedGray , Imgproc.COLOR_RGBA2GRAY);

         MatOfRect faces = new MatOfRect();
         if(openCVFaceClassifier != null){
             //                                 input   output                                // minimum size of output
             openCVFaceClassifier.detectMultiScale(mConvertedGray , faces ,  1.1 , 2  ,  2  ,new Size(30,30) );


         }
         // loop throw all faces
         Rect[] facesArray = faces.toArray();
         for (int i = 0; i < facesArray.length ; i++) {

             // draw face on original frame mRgba
             Imgproc.rectangle(mConvertedGray , facesArray[i].tl() , facesArray[i].br() , new Scalar(0, 255 , 0,255) , 2);

         }


         // rotate back original frame to -90 degree
         if(!Tools.isEmulator())   Core.flip(mConvertedGray.t(),mConvertedGray , 0); // emulator
      //   Imgproc.cvtColor(mRgba , mRgba , Imgproc.COLOR_BGR2GRAY);

         return mConvertedGray;

         // all done
     }



     private Mat CascadeRecGray(Mat mConvertedGray) {
           Mat detecMat = mConvertedGray.clone();
         //  Imgproc.cvtColor(mConvertedGray  , detecMat , 0);
          // detecMat = mConvertedGray.clone();
       //  detecMat.copyTo(mConvertedGray);


       //  detecMat =    mConvertedGray;
        detectFaces = new MatOfRect();
        // original frame is - 90 degree so we have to rotate is to 90 to get propare face for detection
        if(!Tools.isEmulator())  Core.flip(detecMat.t() , detecMat , 1 ); // emulator

         if(openCVFaceClassifier2 != null){
            //                                 input   output                                // minimum size of output
            openCVFaceClassifier2.detectMultiScale(detecMat,detectFaces,1.1,3,0|CASCADE_SCALE_IMAGE, new org.opencv.core.Size(30,30));
 }
        return detecMat;

        // all done
    }



    private void initializeCamera(JavaCameraView javaCameraView, int activeCamera){
        //    javaCameraView.setCameraPermissionGranted();   // this line used in the new version of opencv
        javaCameraView.setCameraIndex(activeCamera);
        javaCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        javaCameraView.setCvCameraViewListener(this);
        javaCameraView.enableView();
    }





    // train function and save the results
    private void  trainFacesModelAndStoreIt() {
        // check if there is an image and the size of images equal the size of labels or names
        Toast.makeText(getActivity(),
                "ImagesNO :" + images.size() +
                     "NamesNO: "+ names.size()
                , Toast.LENGTH_SHORT).show();

        if (!images.isEmpty() && !names.isEmpty() && images.size() == names.size()) {
            // names = ["علي" , "محمد" , "سعيد", "محمد"]
            //images = [imageMat1 , imageMat2 , imageMat3 , imageMat4];

            Log.i(TAG, "step one");
            // step one
            // move all the matrix in images to imagesMatrix so we can do processing on them ..
            List<Mat> imagesMatrix = new ArrayList<>(images);
            // imagesMatrix = [imageMat1 , imageMat2 , imageMat3 , imageMat4];


            Log.i(TAG, "step two");
            // step two
            // get the unique names
            Set<String> uniqueLabelsSet = new HashSet<>(names); // Get all unique labels with out repeat
            String []  uniqueLabels = uniqueLabelsSet.toArray(new String[uniqueLabelsSet.size()]); // Convert the Set to String array, so we can read the values from the indices
             // names = ["علي" , "محمد" , "سعيد", "محمد"]
            // uniqueLabels =  ["علي" , "محمد" , "سعيد"]
            Log.i(TAG, "step two => names :" + names);
            Log.i(TAG, "step two => labelset :" + uniqueLabelsSet);


            Log.i(TAG, "step three");
            //step three create an array of integers of size uniqueLabels and fill it with increament values from 1 to n in our example n = 3
            int[] classesNumbers = new int[uniqueLabels.length];
            // create the incrementing list for each uniqe label starting at 1
            // fill classNumbers with integer values from 1 to uniqueLabels length
            //classesNumbers  =  [1 , 2 , 3]
            for (int i = 0; i < classesNumbers.length; i++)  classesNumbers[i] = i + 1; // labels




            Log.i(TAG, "step four");
            // step 4 order the indexes to match the image and labels
            int[] classes = new int[names.size()];// in our example classes size is 4
            //[ 0,0 ,0 ,0 ] empty array of int in our example it will be 4
            for (int i = 0; i < names.size(); i++) {  // example names = =  ["علي" , "محمد" , "سعيد", "محمد"]
                String label = names.get(i);          // example label = "محمد"

                for (int j = 0; j < uniqueLabels.length; j++) { // uniqueLabels =  ["علي" , "محمد" , "سعيد"]
                    if (label.equals(uniqueLabels[j])) {
                        classes[i] = classesNumbers[j]; //classesNumbers  =  [1 , 2 , 3]
                        break;

                    }
                }
            }
            // classes = [1  , 2 , 1 , 3] // means that the first and third image for the same label



            Log.i(TAG, "five");
            // step five   create vectorClasses Matrix  and fill it with the classes
            // rows         columns          type int
           // classes = [2  , 3 , 0 , 1]
                                       // rows = 4     column = 1    , type int
            Mat vectorClasses = new Mat(classes.length, 1 ,     CvType.CV_32SC1);// cv_325 == int    this matrix will hold the integer values
            vectorClasses.put(0, 0, classes);
            // names = =  ["علي" , "محمد" , "سعيد", "محمد"]
            // imagesMatrix = [imageMat1 , imageMat2 , imageMat3 , imageMat4];
            // vectorClasses  =  [ 1 , means  محمد
            //                     2 , means  سعيد
            //                     1 , means  محمد
            //                     3   means   علي
            //                     ]   the order of labels and images
            //   Log.e(TAG , "step five , vectorClasses = " + vectorClasses.toString());


            Log.i(TAG, "6");
            // step 6 create openCVFaceRecognizer object
            //
            // C++: static Ptr_LBPHFaceRecognizer create(int radius = 1, int neighbors = 8, int grid_x = 8, int grid_y = 8, double threshold = DBL_MAX)
            //javadoc: LBPHFaceRecognizer::create(radius, neighbors, grid_x, grid_y, threshold)
            openCVFaceRecognizer = LBPHFaceRecognizer.create(3, 8, 8, 8, 200);  /* openCVFaceRecognizer */

           Log.i(TAG, "7");
            // step 7 train the date and save the data (images and labels and model)
            // names = =  ["علي" , "محمد" , "سعيد", "محمد"]
            // imagesMatrix = [imageMat1 , imageMat2 , imageMat3 , imageMat4];
            // vectorClasses  =  [ 1 , means  محمد
            //                     2 , means  سعيد
            //                     1 , means  محمد
            //                     3   means   علي
            //                     ]   the order of labels and images

            trainAndSaveData(imagesMatrix, vectorClasses);


        }


    }

    private void addLabel(String value) {
        names.add(value);
        Log.i(TAG, "Label : " + value);
    }



     void trainAndSaveData(List<Mat> imageMatrix, Mat vectorClass) {

         // step 7 train the date and save the data
        // names = =  ["علي" , "محمد" , "سعيد", "محمد"]
         // imagesMatrix = [imageMat1 , imageMat2 , imageMat3 , imageMat4];
         // vectorClasses  =  [ 1 , means  محمد
         //                     2 , means  سعيد
         //                     1 , means  محمد
         //                     3   means   علي
         //                     ]   the order of labels and images

        String filename = "lbph_train_data.xml"; // the trained data will be stored her   , this file will be stored on device

         //  Tools.getStoragePath()  = internalMemory/FinalProject/
         // xfFile =     internalMemory/FinalProject/lbph_train_data.xml
        xmFile = new File(Tools.getStoragePath(), filename);
        openCVFaceRecognizer.train(imageMatrix, vectorClass);  // training by finding the future of the images
       //  openCVFaceRecognizer.write(xmFile.toString());
        openCVFaceRecognizer.save(xmFile.toString());  /* save the reconized model on    internalMemory/FinalProject/lbph_train_data.xml*/
         saveImagesAndlabels2();
         saveImagesAndlabels();
    }


    private void saveImagesAndlabels() {
        int i = 0; // just for indexing ..
        // save the images
        // first save the image as Bitmap then save the text file
        for (Mat mat : images) {
            Bitmap bm = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, bm);
            //  Tools.getStoragePath()  = internalMemory/FinalProject/
            tempImgFile = new File(Tools.getStoragePath(), names.get(i) + ".png");
            //tempImgFile  = internalMemory/FinalProject/name.png
            saveToFile(bm , tempImgFile);
            i = i + 1;
        }
        // save labels
        localSharedPref.putListString("names", names);
    }


     private void saveImagesAndlabels2() {
         int i = 0; // just for indexing ..
         Map<String , Integer> imageCounter = new HashMap<>();
         // save the images
         // first save the image as Bitmap then save the text file
         for (Mat mat : images) {
             Bitmap bm = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
             Utils.matToBitmap(mat, bm);
             //  Tools.getStoragePath()  = internalMemory/FinalProject/
        File file = tempImgFile = new File(Tools.getStoragePath(),names.get(i) );
           if(!file.exists()) file.mkdir();
             if(imageCounter.get(names.get(i)) != null){
                 int oldCounter = imageCounter.get(names.get(i));
                 imageCounter.put(names.get(i) , ++oldCounter);
             }
             else{
                 imageCounter.put(names.get(i),0);
             }

             tempImgFile = new File(Tools.getStoragePath(),names.get(i) + "/" + names.get(i) +imageCounter.get(names.get(i))+ ".png");
             //tempImgFile  = internalMemory/FinalProject/name.png
             saveToFile(bm , tempImgFile);
             i = i + 1;
         }
         // save labels
         localSharedPref.putListString("names", names);
     }





     void saveToFile(Bitmap bmp , File filePath) {

         try {
             FileOutputStream out = new FileOutputStream(filePath);
             bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
             out.flush();
             out.close();


         } catch (Exception e) {
             // Error While Saving file
             Log.e(TAG , "Error while saveing image " + e.getMessage());
             Toast.makeText(getActivity(), "Error Saving `image " + e.getMessage(), Toast.LENGTH_SHORT).show();

         }
     }


     void cropImageFace(Mat originalMat , MatOfRect detectFaces ) {

       try {
           Mat croppedMat = null ;
           for ( Rect face: detectFaces.toArray()  ) {
               Rect rect_crop = new Rect(face.x, face.y, face.width, face.width); //  crop the image to the size of the face
               croppedMat = new Mat(originalMat, rect_crop);

           }


           images.add(croppedMat);

       }


       catch (Exception e){
           Log.e(TAG , "Error While cropping face " + e.getMessage() );

       }
    }

     Bitmap loadFromFile(File filename) {
        try {
            if (!filename.exists()) {
                return null;
            }
            Bitmap tmp = BitmapFactory.decodeFile(filename.getAbsolutePath());
            return tmp;
        } catch (Exception e) {
            return null;
        }
    }
    private void person_input_speak_arabic() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getActivity());
        final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar"); // the recognized language is arabic
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getActivity().getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"ar"});
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar");
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000);
        intent.putExtra("android.intent.extra.durationLimit", 3); // three seconds

        speechRecognizer.setRecognitionListener(new TrainFragment.SpeechListener());
        speechRecognizer.startListening(intent);
        new CountDownTimer(4000, 1000) {


            @Override
        public      void onTick(long millisUntilFinished) {


            }

            @Override
         public     void onFinish() {
                speechRecognizer.stopListening();

            }
        }.start();
    }

    class SpeechListener implements RecognitionListener {

        @Override
    public      void onReadyForSpeech(Bundle params) {
            Toast.makeText(getActivity(), "تكلم....", Toast.LENGTH_SHORT).show();

        }

        @Override
  public        void onBeginningOfSpeech() {

        }

        @Override
    public      void onRmsChanged(float rmsdB) {

        }

        @Override
    public      void onBufferReceived(byte[] buffer) {

        }

        @Override
   public       void onEndOfSpeech() {
            Toast.makeText(getActivity(), "توقف عن الكلام.....", Toast.LENGTH_SHORT).show();

        }

        @Override
      public    void onError(int error) {
            if (error == 7) {
                MainActivity.convertTextToSpeech("اّسف لا نسمعك بوضوح !");
            } else if (error == 5) {

                MainActivity.convertTextToSpeech("جهازك لايدعم اللغه العربية");
            }


        }

        @Override
        public void onResults(Bundle results) {
            Log.e(TAG , "processing result of voice");
            ArrayList<String> maches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String sentence = maches.get(0).trim(); // get the first sentence because it usually it return multible values and remove all the spaces

            if (maches != null) {
                if (delName) { // delete flag if delName = true then delete we are deleting name else we are adding name

                    if (isNameExistOnLables(sentence)) {// means found the name that the user has said
                        trainFacesModelAndStoreIt(); // that not contain the deleted Image and name
                        MainActivity.convertTextToSpeech(sentence + "تم حذف ألاسم وصورتة");
                        Toast.makeText(getActivity(), "name : " + names.toString() + "images : " + images.size(), Toast.LENGTH_SHORT).show();

                    } else {

                        MainActivity.convertTextToSpeech(" اّسف " + sentence + "الأسم غير موجود");

                    }

                } else {
                    addLabel(sentence);
                    MainActivity.convertTextToSpeech("جديد" + sentence + "تم إضافة أسم و صورة");
                    trainFacesModelAndStoreIt();  // train the new  image and label with the old names and images

                }
            }


        }

        @Override
    public      void onPartialResults(Bundle partialResults) {

        }

        @Override
    public      void onEvent(int eventType, Bundle params) {

        }
    }

    private boolean isNameExistOnLables(String sentence) {

        // search for the name in labels if exist we will retrain our model
        boolean found = false;
        // used to store the remain images temprarly
        ArrayList<String> tempNames = new ArrayList<>() ;
        ArrayList<Mat>   tempImages =  new ArrayList<>() ;

        for (int i = 0 ; i< names.size(); i++) {
            if (!names.get(i).equals(sentence)) {
              tempNames.add(names.get(i));
              tempImages.add(images.get(i));
            }
            else {
                found = true;
            }
        }

    names.clear();
    images.clear();
    names = tempNames;
    images = tempImages;

        // delete the files from storage and save the new label
        Tools.deleteDirectory(new File(Tools.getStoragePath().getAbsolutePath() + "/" + sentence));
        File imagePath = new File(Tools.getStoragePath().getAbsolutePath() + "/" + sentence +".png");
        if(imagePath.exists()) imagePath.delete();
        localSharedPref.putListString( "names" , names);

        Log.e("deletname" , "names " +  names.toString());
        return  found;
    }


    // handle permission
   private boolean hasPermission() {
        int res = 0;
        // String array of permission
        String[] permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};

        for (String perms : permissions) {
            res = getActivity(). checkCallingOrSelfPermission(perms);
            if (!(res == PackageManager.PERMISSION_GRANTED)) {
                return false;
            }
        }
        return true;
    }

    private void requestPems() {
        // String array of permission
        String[] permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, PERMS_REQUEST_CODE);
        }
    }

    @Override
 public     void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean allowed = true;
        switch (requestCode) {
            case PERMS_REQUEST_CODE:
                for (int res : grantResults) {
                    // if user granted all permissions.
                    allowed = allowed && (res == PackageManager.PERMISSION_GRANTED);
                }
                break;
            default:
                // if user not granted permissions.
                allowed = false;
                break;
        }
        if (allowed) {
            //user granted all permissions we can perform our task.
            Log.i(TAG, "Permission has been added");
        } else {
            // we will give warning to user that they haven't granted permissions.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) || shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                        shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    Toast.makeText(getActivity(), "Permission Denied.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // handle life cycle of the activity
    @Override
    public void onPause() {
        super.onPause();
        if (openCVCamera != null) {
            openCVCamera.disableView();
        }
    }



    @Override
    public void onStop() {
        super.onStop();
      //  trainFacesModelAndStoreIt();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (openCVCamera != null) {
            openCVCamera.disableView();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "System library loaded successfully");
            callbackLoader.onManagerConnected(BaseLoaderCallback.SUCCESS);

        } else {

            Log.i(TAG, "unable to load the system library");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, getActivity(), callbackLoader);

        }


    }


}

