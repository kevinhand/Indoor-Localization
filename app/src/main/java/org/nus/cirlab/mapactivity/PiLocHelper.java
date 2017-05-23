package org.nus.cirlab.mapactivity;

import com.google.android.gms.maps.model.LatLng;

import org.nus.cirlab.mapactivity.DataStructure.StepInfo;

import java.util.Vector;

public class PiLocHelper {


    public static Vector<StepInfo> mapTrajectory(LatLng startP, LatLng endP, Vector<StepInfo> steps) {
        if (steps == null || steps.size() == 0)
            return null;

        Vector<StepInfo> returnSteps = new Vector<>();

        StepInfo lastPoint = steps.lastElement();
        double lambda = Math.sqrt(((endP.latitude - startP.latitude) * (endP.latitude - startP.latitude)
                + (endP.longitude - startP.longitude) * (endP.longitude - startP.longitude)))
                / Math.sqrt((lastPoint.mPosX * lastPoint.mPosX + lastPoint.mPosY * lastPoint.mPosY));
        double rotateAngle = getRotateAngle(lastPoint.mPosX, lastPoint.mPosY,
                (endP.latitude - startP.latitude), (endP.longitude - startP.longitude));

        for (StepInfo step : steps) {
            double tempX = step.mPosX;
            double tempY = step.mPosY;
            step.mPosX = Math.cos(rotateAngle) * tempX - Math.sin(rotateAngle) * tempY;
            step.mPosY = Math.cos(rotateAngle) * tempY + Math.sin(rotateAngle) * tempX;

            double a = step.mPosX * lambda + startP.latitude;
            double b = startP.longitude + step.mPosY * lambda;

            step.mPosX =  a;
            step.mPosY =  b;
            returnSteps.add(step);
        }

        return returnSteps;
    }

    public static double getRotateAngle(double x1, double y1, double x2, double y2) {
        double epsilon = 1.0e-6;
        double nyPI = Math.PI;
        double dist, dot, rotateAngle;

        dist = Math.sqrt(x1 * x1 + y1 * y1);
        x1 /= dist;
        y1 /= dist;
        dist = Math.sqrt(x2 * x2 + y2 * y2);
        x2 /= dist;
        y2 /= dist;
        dot = x1 * x2 + y1 * y2;
        if (Math.abs(dot - 1.0) <= epsilon)
            rotateAngle = 0.0;
        else if (Math.abs(dot + 1.0) <= epsilon)
            rotateAngle = nyPI;
        else {
            double cross;

            rotateAngle = Math.acos(dot);
            cross = x1 * y2 - x2 * y1;
            if (cross < 0) {
                rotateAngle = 2 * nyPI - rotateAngle;
            }
        }
        return rotateAngle;
    }
}
