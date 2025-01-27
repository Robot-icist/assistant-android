package com.assistant.main;

import android.util.Log;

import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_face.FaceRecognizer;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.CV_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.equalizeHist;
import static org.bytedeco.opencv.global.opencv_imgproc.putText;
import static org.bytedeco.opencv.global.opencv_imgproc.rectangle;

/**
 * The class below takes two arguments: The path to the directory containing the training
 * faces and the path to the image you want to classify. Not that all images has to be of
 * the same size and that the faces already has to be cropped out of their original images
 * (Take a look here http://fivedots.coe.psu.ac.th/~ad/jg/nui07/index.html if you haven't
 * done the face detection yet).
 *
 * For the simplicity of this post, the class also requires that the training images have
 * filename format: <label>-rest_of_filename.png. For example:
 *
 * 1-jon_doe_1.png
 * 1-jon_doe_2.png
 * 2-jane_doe_1.png
 * 2-jane_doe_2.png
 * ...and so on.
 *
 * Source: http://pcbje.com/2012/12/doing-face-recognition-with-javacv/
 *
 * @author Petter Christian Bjelland
 */
public class OpenCVFaceRecognizer {

    private static OpenCVFaceRecognizer Instance;
    private static FaceRecognizer faceRecognizer;
    private static CascadeClassifier face_cascade;

    private static String lastFile = null;

    private static HashMap<Integer,String> names = null;

    private OpenCVFaceRecognizer(){
        Instance = this;
        //faceRecognizer = FisherFaceRecognizer.create();
        //faceRecognizer = EigenFaceRecognizer.create();
        faceRecognizer = LBPHFaceRecognizer.create();
    }

    public static OpenCVFaceRecognizer GetInstance(){
        if(Instance != null) return Instance;
        return new OpenCVFaceRecognizer();
    }

    public String Recognize(File picture){
        Mat testImage = imread(picture == null ? lastFile : picture.getAbsolutePath(), IMREAD_GRAYSCALE);
        return Recognize(testImage);
    }

    public String Recognize(Mat face){
            IntPointer label = new IntPointer(1);
            DoublePointer confidence = new DoublePointer(1);
            faceRecognizer.predict(face, label, confidence);
            long c = label.capacity();
            int predictedLabel = label.get(0);
            String predictedName = names.get(predictedLabel);
            double predictedConfidence = confidence.get(0);
            System.out.println("Predicted label: " + predictedLabel);
            System.out.println("Predicted name: " + predictedName);
            System.out.println("Predicted confidence: " + predictedConfidence);
            return predictedName + " " + predictedConfidence;
    }

    public void Train(File trainingDir) {
        try{
            FilenameFilter imgFilter = new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    name = name.toLowerCase();
                    return name.endsWith(".jpg") || name.endsWith(".pgm") || name.endsWith(".png");
                }
            };

            File[] imageFiles = trainingDir.listFiles(imgFilter);

            MatVector images = new MatVector(imageFiles.length);

            Mat labels = new Mat(imageFiles.length, 1, CV_32SC1);
            IntBuffer labelsBuf = labels.createBuffer();
            names = new HashMap<>();
            int counter = 0;
            for (File image : imageFiles) {
                Mat img = imread(image.getAbsolutePath(), IMREAD_GRAYSCALE);
                lastFile = image.getAbsolutePath();
                int label = Integer.parseInt(image.getName().split("\\-")[0]);
                String name = image.getName().split("\\-")[1];

                images.put(counter, img);

                labelsBuf.put(counter, label);

                names.put(label, name);

                counter++;
            }

            if(imageFiles.length > 0)
                faceRecognizer.train(images, labels);
        }catch (Exception e){
            Log.e("FaceRecognizerException Train", e.getMessage());
        }
    }
    public ArrayList<Mat> DetectFaces(String photoToDetect){
        try {
            face_cascade = new CascadeClassifier(Tasks.SetupTask.CascadeFile.getAbsolutePath());
            Mat img = imread(photoToDetect);
            ;
            int height = img.rows();
            int width = img.cols();
            Mat matGray = new Mat(height, width, CV_8UC1);
            //Mat matGray = new Mat();
            // Convert the current frame to grayscale:
            //cvtColor(img, matGray, COLOR_BGRA2GRAY);
            cvtColor(img, matGray, CV_BGR2GRAY);
            equalizeHist(matGray, matGray);

            RectVector faces = new RectVector();
            // Find the faces in the frame:
            face_cascade.detectMultiScale(matGray, faces);
            ArrayList<Mat> detected = new ArrayList<Mat>();
            for (int i = 0; i < faces.size(); i++) {
                Rect face_i = faces.get(i);
                Mat face = new Mat(matGray, face_i);
                detected.add(face);
            }
            return detected;
        }catch(Exception e){Log.e("FaceRecognizerException DetectFaces", e.getMessage());}
        return null;
    }

    public void DetectFaces(File photoToDetect){
        face_cascade = new CascadeClassifier(Tasks.SetupTask.CascadeFile.getAbsolutePath());

        Mat img = imread(photoToDetect.getAbsolutePath());
        int height = img.rows();
        int width = img.cols();
        Mat matGray = new Mat(height, width, CV_8UC1);
        //Mat matGray = new Mat();
        // Convert the current frame to grayscale:
        //cvtColor(img, matGray, COLOR_BGRA2GRAY);
        cvtColor(img, matGray, CV_BGR2GRAY);
        equalizeHist(matGray, matGray);

        RectVector faces = new RectVector();
        // Find the faces in the frame:
        face_cascade.detectMultiScale(matGray, faces);

        for (int i = 0; i < faces.size(); i++) {
            Rect face_i = faces.get(i);
            Mat face = new Mat(matGray, face_i);
            // If fisher face recognizer is used, the face need to be
            // resized.
            // resize(face, face_resized, new Size(im_width, im_height),
            // 1.0, 1.0, INTER_CUBIC);

            // Now perform the prediction, see how easy that is:
            /*IntPointer label = new IntPointer(1);
            DoublePointer confidence = new DoublePointer(1);
            faceRecognizer.predict(face, label, confidence);
            int prediction = label.get(0);

            // And finally write all we've found out to the original image!
            // First of all draw a green rectangle around the detected face:
            rectangle(img, face_i, new Scalar(0, 255, 0, 1));

            // Create the text we will annotate the box with:
            String box_text = "Prediction = " + prediction;
            // Calculate the position for annotated text (make sure we don't
            // put illegal values in there):
            int pos_x = Math.max(face_i.tl().x() - 10, 0);
            int pos_y = Math.max(face_i.tl().y() - 10, 0);
            // And now put it into the image:
            putText(img, box_text, new Point(pos_x, pos_y),
                    FONT_HERSHEY_PLAIN, 1.0, new Scalar(0, 255, 0, 2.0));
            */
            save(photoToDetect, face);
        }
    }

    private void save(File file, Mat image) {
        imwrite(file.getAbsolutePath(), image);
    }
}