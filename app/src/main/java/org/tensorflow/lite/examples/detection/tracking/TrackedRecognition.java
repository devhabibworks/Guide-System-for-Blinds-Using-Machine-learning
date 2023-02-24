package org.tensorflow.lite.examples.detection.tracking;

import android.graphics.RectF;
import android.util.Log;

import com.habib.finalproject.other.Constants;

public class TrackedRecognition {
    RectF location;
    float detectionConfidence;
    int color;
    private String title;
    void  setTitle(String value){
        Log.e("trackingValue"  , "value : "  + value);


        String translationArabic = Constants.objectLabelsWithTranslationMap.get(value);
        Log.e("trackingValue"  , "translationArabic : "  + translationArabic);

        this.title = (translationArabic == null) ? value :translationArabic;

    }

    public String getTitle() {
        return title;
    }
}

