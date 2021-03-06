package cz.nakoncisveta.eyetracksample.eyedetect;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


public class FdActivity extends Activity implements CvCameraViewListener2 {

    private static final String    TAG                 = "OCVSample::Activity";
    private static final Scalar    FACE_RECT_COLOR     = new Scalar(0, 255, 0, 255);
    public static final int        JAVA_DETECTOR       = 0;
    private static final int TM_SQDIFF = 0;
    private static final int TM_SQDIFF_NORMED = 1;
    private static final int TM_CCOEFF = 2;
    private static final int TM_CCOEFF_NORMED = 3;
    private static final int TM_CCORR = 4;
    private static final int TM_CCORR_NORMED = 5;


    private double[] lastX = new double[2];
    private double[] lastY = new double[2];
    private double[]  lastV = new double[2];
    private long[] lastT = new long[2];
    private static final double accelerationScaler = 500;
    private static final double accelerationLowerActivationThreshold  = 12;
    private static final double accelerationUpperActivationThreshold = 100;
    private static final long blinkActivationResetTime = 1000;
    private long remainActivatedUntil = 0;
    private static final long badDataPointDistanceThreshold = 100;
    private int isRightEye;
    private static final int rollingAverageSize = 5;
    private double[] rollingAverage = new double[rollingAverageSize];
    private int rollingAverageIndex = 0;

    private static final int rollingVarianceSize = 10 ;
    private double[] rollingVariance = new double[rollingVarianceSize];
    private int rollingVarianceIndex = 0;

    private static final int longTermAverageSize = 100;
    private double[] longTermAverage = new double[longTermAverageSize];
    private int longTermAverageIndex = 0;

    private long lastBlinkTime = 0;
    private long displayBlinkTextUntil = -1;
    private static final double blinkDetectionThreshold = 125;

    private double bpm = 0;
    private long lastResetTime = 0;



    private int learn_frames = 0;
    private Mat teplateR;
    private Mat teplateL;
    int method = 0;

    // matrix for zooming
    private Mat mZoomWindow;
    private Mat mZoomWindow2;

    private MenuItem               mItemFace50;
    private MenuItem               mItemFace40;
    private MenuItem               mItemFace30;
    private MenuItem               mItemFace20;
   // private MenuItem               mItemType;

    private Mat                    mRgba;
    private Mat                    mGray;
    private File                   mCascadeFile;
    private File                   mCascadeFileEye;
    private CascadeClassifier      mJavaDetector;
    private CascadeClassifier      mJavaDetectorEye;


    private int                    mDetectorType       = JAVA_DETECTOR;
    private String[]               mDetectorName;

    private float                  mRelativeFaceSize   = 0.2f;
    private int mAbsoluteFaceSize = 0;

    private CameraBridgeViewBase   mOpenCvCameraView;
    private SeekBar mMethodSeekbar;
    private TextView mValue;

    double xCenter = -1;
    double yCenter = -1;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");


                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        // load cascade file from application resources
                        InputStream ise = getResources().openRawResource(R.raw.haarcascade_eye);
                        File cascadeDirEye = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFileEye = new File(cascadeDirEye, "haarcascade_eye.xml");
                        FileOutputStream ose = new FileOutputStream(mCascadeFileEye);

                        while ((bytesRead = ise.read(buffer)) != -1) {
                            ose.write(buffer, 0, bytesRead);
                        }
                        ise.close();
                        ose.close();

                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

                        mJavaDetectorEye = new CascadeClassifier(mCascadeFileEye.getAbsolutePath());
                        if (mJavaDetectorEye.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier for eye");
                            mJavaDetectorEye = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFileEye.getAbsolutePath());

                        cascadeDir.delete();
                        cascadeDirEye.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }
                    mOpenCvCameraView.enableFpsMeter();
                    mOpenCvCameraView.setCameraIndex(1);
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public FdActivity() {
        mDetectorName = new String[2];
        mDetectorName[JAVA_DETECTOR] = "Java";

        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.face_detect_surface_view);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
        mZoomWindow.release();
        mZoomWindow2.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        long currentTime = System.currentTimeMillis();

        //if(currentTime - lastResetTime > 10000)
        //    learn_frames = 0;

        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }

        }

        if (mZoomWindow == null || mZoomWindow2 == null)
            CreateAuxiliaryMats();

        MatOfRect faces = new MatOfRect();

        if (mDetectorType == JAVA_DETECTOR) {
            if (mJavaDetector != null)
                mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        }
        else {
            Log.e(TAG, "Detection method is not selected!");
        }

        Rect[] facesArray = faces.toArray();
        for (int i = 0; i < facesArray.length; i++)
        {	Imgproc.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(),
                FACE_RECT_COLOR, 3);
            xCenter = (facesArray[i].x + facesArray[i].width + facesArray[i].x) / 2;
            yCenter = (facesArray[i].y + facesArray[i].y + facesArray[i].height) / 2;
            Point center = new Point(xCenter, yCenter);

            Imgproc.circle(mRgba, center, 10, new Scalar(255, 0, 0, 255), 3);

            Imgproc.putText(mRgba, "[" + center.x + "," + center.y + "]",
                    new Point(center.x + 20, center.y + 20),
                    Core.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(255, 255, 255,
                            255));

            Rect r = facesArray[i];
            // compute the eye area
            Rect eyearea = new Rect(r.x + r.width / 8,
                    (int) (r.y + (r.height / 4.5)), r.width - 2 * r.width / 8,
                    (int) (r.height / 3.0));
            // split it
            Rect eyearea_right = new Rect(r.x + r.width / 16,
                    (int) (r.y + (r.height / 4.5)),
                    (r.width - 2 * r.width / 16) / 2, (int) (r.height / 3.0));
            Rect eyearea_left = new Rect(r.x + r.width / 16
                    + (r.width - 2 * r.width / 16) / 2,
                    (int) (r.y + (r.height / 4.5)),
                    (r.width - 2 * r.width / 16) / 2, (int) (r.height / 3.0));


            // draw the area - mGray is working grayscale mat, if you want to
            // see area in rgb preview, change mGray to mRgba
            Imgproc.rectangle(mRgba, eyearea_left.tl(), eyearea_left.br(),
                    new Scalar(255, 0, 0, 255), 2);
            Imgproc.rectangle(mRgba, eyearea_right.tl(), eyearea_right.br(),
                    new Scalar(255, 0, 0, 255), 2);

            if (learn_frames < 5) {
                teplateR = get_template(mJavaDetectorEye, eyearea_right, 24);
                teplateL = get_template(mJavaDetectorEye, eyearea_left, 24);
                learn_frames++;
                lastResetTime = currentTime;
            } else {
                // Learning finished, use the new templates for template
                // matching
                isRightEye = 1;
                if(!match_eye(eyearea_right, teplateR, method))
                    continue;
                isRightEye = 0;
                if(!match_eye(eyearea_left, teplateL, method))
                    continue;

                Rect rightEyeBox = new Rect((int)lastX[1] + eyearea_right.x - eyearea_right.width/10,
                        (int)lastY[1] + eyearea_right.y,
                        eyearea_right.width/4,
                        eyearea_right.height/4);
                Imgproc.rectangle(mRgba, rightEyeBox.tl(), rightEyeBox.br(),
                        new Scalar(255, 0, 0, 255), 2);

                Rect leftEyeBox = new Rect((int)lastX[0] + eyearea_left.x - eyearea_left.width/10,
                        (int)lastY[0] + eyearea_left.y,
                        eyearea_left.width/4,
                        eyearea_left.height/4);
                Imgproc.rectangle(mRgba, leftEyeBox.tl(), leftEyeBox.br(),
                        new Scalar(255, 0, 0, 255), 2);

                int left = rightEyeBox.x;
                int right = rightEyeBox.x + rightEyeBox.width;
                int top = rightEyeBox.y;
                int bottom = rightEyeBox.y + rightEyeBox.height;

                long intensity = 0;



                for(int col = left; i < right; i++)
                {
                    for(int row = top; i < bottom; i++) {

                        double pixel[];
                        pixel = mGray.get(row, col);
                        intensity += pixel[0];
                    }
                }

                if(rollingVarianceIndex >= rollingVarianceSize-1)
                    rollingVarianceIndex = 0;
                else
                    rollingVarianceIndex++;

                rollingVariance[rollingVarianceIndex] = intensity;

                double mean = 0;
                for(int index = 0; index < rollingVarianceSize; index++)
                {
                    mean += rollingVariance[index];
                }
                mean = mean / rollingVarianceSize;

                double variance = 0;
                for(int index = 0; index < rollingVarianceSize; index++)
                {
                    variance += Math.pow(rollingVariance[index] - mean, 2);
                }
                variance = variance / rollingVarianceSize;
                double standardDev = Math.sqrt(variance);

                if(rollingAverageIndex >= rollingAverageSize-1)
                    rollingAverageIndex = 0;
                else
                    rollingAverageIndex++;

                rollingAverage[rollingAverageIndex] = standardDev;

                double sum = 0;
                for(int index = 0; index < rollingAverageSize; index++)
                {
                    sum += rollingAverage[index];
                }
                double smoothedStdDev = sum / rollingAverageSize;

                if(longTermAverageIndex >= longTermAverageSize-1)
                    longTermAverageIndex = 0;
                else
                    longTermAverageIndex++;

                longTermAverage[longTermAverageIndex] = smoothedStdDev;

                double longTermAverageSum = 0;
                for(int index = 0; index < longTermAverageSize; index++)
                {
                    longTermAverageSum += longTermAverage[index];
                }
                double average = longTermAverageSum / longTermAverageSize;

                double deviationFromAverage = (smoothedStdDev / average)*100;




                if(deviationFromAverage > blinkDetectionThreshold && currentTime > displayBlinkTextUntil)
                {
                    long timeBetweenBlink = currentTime - lastBlinkTime;
                    bpm = 60000 / timeBetweenBlink;
                    lastBlinkTime = currentTime;
                    displayBlinkTextUntil = currentTime + 1000;
                }

                Imgproc.circle(mRgba, new Point(200,200), (int)blinkDetectionThreshold, new Scalar(0,255,0,255), 5);
                Imgproc.circle(mRgba, new Point(200,200), (int)(deviationFromAverage), new Scalar(255,0,0,255), 5);




            }
            int centerx = mGray.width() / 2;
            if(displayBlinkTextUntil > currentTime)
            {
                Imgproc.putText(mRgba, "Blink Detected",
                        new Point(centerx, 50),
                        Core.FONT_HERSHEY_SIMPLEX, 1.5, new Scalar(0, 255, 0,
                                255),5);
            }

            if (bpm > 0)
            {
                String displayText = new String();
                displayText = "Blink Rate: ";
                displayText += String.valueOf(bpm);

                Imgproc.putText(mRgba, displayText,
                        new Point(centerx, mGray.height() - 50),
                        Core.FONT_HERSHEY_SIMPLEX, 1.5, new Scalar(0, 255, 0,
                                255), 5);
            }


            // cut eye areas and put them to zoom windows
            //Imgproc.resize(mRgba.submat(eyearea_left), mZoomWindow2,
            //        mZoomWindow2.size());
            //Imgproc.resize(mRgba.submat(eyearea_right), mZoomWindow,
            //        mZoomWindow.size());


        }

        return mRgba;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemFace50 = menu.add("Face size 50%");
        mItemFace40 = menu.add("Face size 40%");
        mItemFace30 = menu.add("Face size 30%");
        mItemFace20 = menu.add("Face size 20%");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        if (item == mItemFace50)
            setMinFaceSize(0.5f);
        else if (item == mItemFace40)
            setMinFaceSize(0.4f);
        else if (item == mItemFace30)
            setMinFaceSize(0.3f);
        else if (item == mItemFace20)
            setMinFaceSize(0.2f);

        return true;
    }

    private void setMinFaceSize(float faceSize) {
        mRelativeFaceSize = faceSize;
        mAbsoluteFaceSize = 0;
    }

    private void CreateAuxiliaryMats() {
        if (mGray.empty())
            return;

        int rows = mGray.rows();
        int cols = mGray.cols();

        if (mZoomWindow == null) {
            mZoomWindow = mRgba.submat(rows / 2 + rows / 10, rows, cols / 2
                    + cols / 10, cols);
            mZoomWindow2 = mRgba.submat(0, rows / 2 - rows / 10, cols / 2
                    + cols / 10, cols);
        }

    }

    private boolean match_eye(Rect area, Mat mTemplate, int type) {
        Point matchLoc;
        Mat mROI = mGray.submat(area);
        int result_cols = mROI.cols() - mTemplate.cols() + 1;
        int result_rows = mROI.rows() - mTemplate.rows() + 1;
        // Check for bad template size
        if (mTemplate.cols() == 0 || mTemplate.rows() == 0) {
            return false;
        }
        Mat mResult = new Mat(result_cols, result_rows, CvType.CV_8U);

        switch (type) {
            case TM_SQDIFF:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_SQDIFF);
                break;
            case TM_SQDIFF_NORMED:
                Imgproc.matchTemplate(mROI, mTemplate, mResult,
                        Imgproc.TM_SQDIFF_NORMED);
                break;
            case TM_CCOEFF:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_CCOEFF);
                break;
            case TM_CCOEFF_NORMED:
                Imgproc.matchTemplate(mROI, mTemplate, mResult,
                        Imgproc.TM_CCOEFF_NORMED);
                break;
            case TM_CCORR:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_CCORR);
                break;
            case TM_CCORR_NORMED:
                Imgproc.matchTemplate(mROI, mTemplate, mResult,
                        Imgproc.TM_CCORR_NORMED);
                break;
        }

        Core.MinMaxLocResult mmres = Core.minMaxLoc(mResult);
        // there is difference in matching methods - best match is max/min value
        if (type == TM_SQDIFF || type == TM_SQDIFF_NORMED) {
            matchLoc = mmres.minLoc;
        } else {
            matchLoc = mmres.maxLoc;
        }

        Point matchLoc_tx = new Point(matchLoc.x + area.x, matchLoc.y + area.y);
        Point matchLoc_ty = new Point(matchLoc.x + mTemplate.cols() + area.x,
                matchLoc.y + mTemplate.rows() + area.y);

        boolean activated = false;
        boolean bad_data = false;
        double thisA = 0;
        double filteredA = 0;
        long thisT = System.currentTimeMillis();
        //Skip processing the first data point
        if( remainActivatedUntil > thisT )
            activated = true;
        else if (lastX[isRightEye] >= 0 && lastY[isRightEye] >= 0)
        {
            long dT = thisT - lastT[isRightEye];
            double thisD = Math.sqrt((Math.pow(lastX[isRightEye] - matchLoc.x, 2) + Math.pow(lastY[isRightEye] - matchLoc.y, 2)));

            if (thisD < badDataPointDistanceThreshold)
            {
                double thisV = thisD / dT;


                //only process if we have a previous V
                if (lastV[isRightEye] >= 0) {
                    thisA = Math.abs((lastV[isRightEye] - thisV) / dT);
                    thisA = thisA * accelerationScaler;
                    //thisA = thisA / (area.y * area.x);

                    filteredA = thisA;
                    if (filteredA > accelerationLowerActivationThreshold &&
                        filteredA < accelerationUpperActivationThreshold    )
                    {

                        System.out.println("Activated!");
                        activated = true;
                        remainActivatedUntil = thisT + blinkActivationResetTime;
                    }
                }
                lastV[isRightEye] = thisV;
            }
            else
                bad_data = true;
        }
        if(!bad_data) {
            lastT[isRightEye] = thisT;
            lastX[isRightEye] = matchLoc.x;
            lastY[isRightEye] = matchLoc.y;
        }
        Scalar color;
        if(activated)
            color = new Scalar(0, 255, 0, 255);
        else
            color = new Scalar(255, 0, 0, 255);


        Imgproc.rectangle(mRgba, matchLoc_tx, matchLoc_ty, color);
        //Imgproc.circle(mRgba, new Point(200,200), (int)accelerationUpperActivationThreshold*4, color, 5);
        //Imgproc.circle(mRgba, new Point(200,200), (int)accelerationLowerActivationThreshold*4, color, 5);
        //Imgproc.circle(mRgba, new Point(200,200), (int)filteredA*4, color, 5);
        Rect rec = new Rect(matchLoc_tx,matchLoc_ty);

        return !bad_data;
    }

    private Mat get_template(CascadeClassifier clasificator, Rect area, int size) {
        Mat template = new Mat();
        Mat mROI = mGray.submat(area);
        MatOfRect eyes = new MatOfRect();
        Point iris = new Point();
        Rect eye_template = new Rect();
        clasificator.detectMultiScale(mROI, eyes, 1.15, 2,
                Objdetect.CASCADE_FIND_BIGGEST_OBJECT
                        | Objdetect.CASCADE_SCALE_IMAGE, new Size(30, 30),
                new Size());

        Rect[] eyesArray = eyes.toArray();
        for (int i = 0; i < eyesArray.length;) {
            Rect e = eyesArray[i];
            e.x = area.x + e.x;
            e.y = area.y + e.y;




            Rect eye_only_rectangle = new Rect((int) e.tl().x,
                    (int) (e.tl().y + e.height * 0.4), (int) e.width,
                    (int) (e.height * 0.6));
            mROI = mGray.submat(eye_only_rectangle);
            Mat vyrez = mRgba.submat(eye_only_rectangle);


            Core.MinMaxLocResult mmG = Core.minMaxLoc(mROI);

            Imgproc.circle(vyrez, mmG.minLoc, 2, new Scalar(255, 255, 255, 255), 2);
            iris.x = mmG.minLoc.x + eye_only_rectangle.x;
            iris.y = mmG.minLoc.y + eye_only_rectangle.y;
            eye_template = new Rect((int) iris.x - size / 2, (int) iris.y
                    - size / 2, size, size);
            Imgproc.rectangle(mRgba, eye_template.tl(), eye_template.br(),
                    new Scalar(255,0,0,255), 2);
            template = (mGray.submat(eye_template)).clone();
            return template;
        }
        return template;
    }

    public void onRecreateClick(View v)
    {
        learn_frames = 0;
    }

}
