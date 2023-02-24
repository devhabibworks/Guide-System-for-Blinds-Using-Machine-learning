package com.habib.finalproject;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;
import com.habib.finalproject.other.Tools;
import com.habib.finalproject.ui.colors.ColorsFragment;
import com.habib.finalproject.ui.objects.ObjectFragment;
import com.habib.finalproject.ui.trains.TrainFragment;
import com.habib.finalproject.ui.waking_helpers.WakingHelperFragment;
import java.util.Locale;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import me.ibrahimsn.lib.SmoothBottomBar;
public class MainActivity extends AppCompatActivity {
    private static TextToSpeech mTextToSpeech;
    SmoothBottomBar bottomBar;
    public static void stopTextToSpeach() {
        mTextToSpeech.stop();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textStuff();
        ininitalVaribable();

    }
    private void  ininitalVaribable (){

        checkPermission();

        bottomBar = findViewById(R.id.bottomBar);

        WakingHelperFragment helperFragment = new WakingHelperFragment();
        ObjectFragment objectFragment = new ObjectFragment();
        ColorsFragment colorsFragment = new ColorsFragment();
        TrainFragment trainFragment  = new TrainFragment();
        createFragment(helperFragment);



        bottomBar.setOnItemSelected(new Function1<Integer, Unit>() {
            @Override
            public Unit invoke(Integer integer) {

                switch (integer) {
                    case 0:
                        Tools.vibrate(MainActivity.this);

                        createFragment(helperFragment);
                        convertTextToSpeech( "المرشد" +  "قم بتوجيه جوالك سيتم تنبيهك باالأشياء الخطره حال التعرف عليها.");



                        break;
                    case 1:

                        Tools.vibrate(MainActivity.this);
                        convertTextToSpeech("التعرف على الأشياء" +  "قم بتوجيه جوالك وانقر الشاشة.");
                        createFragment(objectFragment);

                        break;
                    case 2:

                        Tools.vibrate(MainActivity.this);
                        createFragment(colorsFragment);
                        convertTextToSpeech("التعرف على الألوان." +  "قم بتوجيه جوالك وانقر الشاشة.");


                        break;
                    case 3:
                        Tools.vibrate(MainActivity.this);
                        MainActivity.convertTextToSpeech(  "شاشة التدريب ، " +  "اطلب منه البقاء أمام الكاميرا والمس الشاشة.");
                        createFragment(trainFragment);

                        break;


                }
                return  null;

            }
        });
    }
    private void createFragment(Fragment fragment){
        getSupportFragmentManager().
                beginTransaction()
                .replace(R.id.placeholder, fragment)
                .commit();
    }
    private void welcome() {

        convertTextToSpeech( "يا مرحبا انت بشاشة المرشد" +  "قم بتوجيه جوالك سيتم تنبيهك باالأشياء الخطره حال التعرف عليها.");
    }
    public static void convertTextToSpeech(String str) {
        if(mTextToSpeech.isSpeaking()) mTextToSpeech.stop();

        mTextToSpeech.setPitch((float) 1.2);
        mTextToSpeech.setSpeechRate((float) 0.8); // speech rate means how speed rate in playing
        mTextToSpeech.speak(str, TextToSpeech.QUEUE_FLUSH, null);



    }
    public static boolean isSpeeching(){
      return   mTextToSpeech.isSpeaking();

    }
    private void checkPermission() {

        if(
                ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED  ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED  ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED  ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED



        ){

            ActivityCompat.requestPermissions(this,new String[] {
                    Manifest.permission.RECORD_AUDIO ,
                    Manifest.permission.CAMERA ,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE ,
                    Manifest.permission.READ_EXTERNAL_STORAGE  ,
                    Manifest.permission.VIBRATE

            },1);

        }

    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    }
    void textStuff(){
        mTextToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = mTextToSpeech.setLanguage(new Locale("ar")); // ar = arabic

                    // welcome message
                    welcome();



                } else {
                    Toast.makeText(MainActivity.this, "TextToSpeech Failed", Toast.LENGTH_SHORT).show();


                }
            }
        });
    }
    @Override
    protected void onDestroy() {

        // destroy the objects when exist the app so they won't use the memory
        if(mTextToSpeech!= null)
        {
            mTextToSpeech.stop();
            mTextToSpeech.shutdown();
        }
        super.onDestroy();
     

      
    }




}
