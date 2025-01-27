package com.assistant.main.llm;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;

import androidx.lifecycle.MutableLiveData;

import com.google.mediapipe.tasks.core.OutputHandler;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Arrays;

public class LLM {

    private static final String MODEL_PATH = "/data/local/tmp/llm/gemma2b.bin";
    private static LLM instance;
    private static LlmInference llmInference;
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    private LLM(Context context, OutputHandler.ProgressListener listener) {
        LlmInferenceOptions options = LlmInferenceOptions.builder()
                .setModelPath(MODEL_PATH)
                .setMaxTokens(512)
                .setTopK(50)
                .setRandomSeed(1)
                .setTemperature(0.1f)
                .setResultListener((partialResult, done) -> {
                    Pair<String, Boolean> result = new Pair<>(partialResult, done);
                    propertyChangeSupport.firePropertyChange("partialResult", null, result);
                    if(listener != null){
                        listener.run(partialResult, done);
                    }
                })
                .build();

        llmInference = LlmInference.createFromOptions(context, options);
    }

    public static LLM getInstance(Context context, OutputHandler.ProgressListener listener) {
        if (instance == null) {
            instance = new LLM(context, listener);
        }
        return instance;
    }

    public void generateResponse(String prompt) {
        llmInference.generateResponseAsync(prompt);
    }

    public void stop() {
        try {
            //llmInference.close();
            instance = null;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public void addPartialResultListener(PropertyChangeListener listener) {
        removePartialResultListener();
        propertyChangeSupport.addPropertyChangeListener("partialResult", listener);
    }

    public void removePartialResultListener() {
        PropertyChangeListener[] listeners = propertyChangeSupport.getPropertyChangeListeners("partialResult");
        for (PropertyChangeListener l : listeners) {
            propertyChangeSupport.removePropertyChangeListener("partialResult", l);
        }
    }
}
