package com.assistant.main.helpers;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

public class Beeper
{
    Boolean isOn = false;
    Boolean stop = false;
    int audioStream = AudioManager.STREAM_ALARM;
    int volume = 150;
    int tone = ToneGenerator.TONE_PROP_BEEP;
    ToneGenerator toneG = new ToneGenerator(audioStream, volume);

    public void setStop(Boolean value){
        stop = value;
    }
    public Boolean getStop(){
        return stop;
    }
    public int getTone(){
        return tone;
    }
    public void setTone(int value){
        tone = value;
    }

    public void stopBeeper(int delay, Boolean stp){
        setStop(stp);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                toneG.stopTone();
                isOn = false;
                setStop(stp);
                //toneG.release();
            }
        }, delay);
    }
    public void beep(int duration, int delay, int tone)
    {
        if(isOn || getStop()) return;
        toneG.stopTone();
        isOn = true;
        toneG.startTone(tone, duration);
        stopBeeper(duration+delay, getStop());
    }

    public void beepSlow(){
        setStop(false);
        beep(500, 200, getTone());
    }

    public void beepMedium(){
        setStop(false);
        beep(250, 150, getTone());
    }

    public void beepHigh(){
        setStop(false);
        beep(100, 100, getTone());
    }

    public void beepFast(){
        setStop(false);
        beep(50, 50 , getTone());
    }


}