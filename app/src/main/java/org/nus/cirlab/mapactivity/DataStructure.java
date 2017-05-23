package org.nus.cirlab.mapactivity;

import android.graphics.Point;


import com.google.android.gms.maps.model.LatLng;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

public class DataStructure {
	

	
	public static class Fingerprint implements Serializable
	{
		public String mMac;
		public Integer mRSSI;
		public Integer mFrequency;

		public Fingerprint(String m, int r, int f) {
			mMac = m;
			mRSSI = r;
			mFrequency = f;
		}

	}
	
	public static class FingerprintwithDuplicate {
		public String mMac;
		public ArrayList<Integer> mRSSI;

		public FingerprintwithDuplicate(String m, int r) {
			mMac = m;
			mRSSI = new ArrayList<Integer>();
			mRSSI.add(r);
		}


		public int getMeanRssi() {
			int mean = 0;
			for (int r : mRSSI) {
				mean += r;
			}
			return mean / mRSSI.size();
		}

		public double getStdDevRssi() {
			int mean = getMeanRssi();
			double stdDev = 0;
			for (int r : mRSSI) {
				stdDev += r - mean;
			}
			return stdDev / mRSSI.size();
		}
	}
	
	
	public static class GaussianParameter
	{
//		public String mac;
		public Integer mean;
		public Double stdev;

		public GaussianParameter( int r, double var) {
			mean = r;
			stdev = var;
		}
	
	}
	
	//Hash map of point to vector of fingerprint
	public static class RadioMap
	{
		//public Vector<LocFP> mLocFPList;
		public HashMap<LatLng,Vector<Fingerprint>> mLocFingerPrints;

		RadioMap(HashMap<LatLng,Vector<Fingerprint>> f)
		{
			this.mLocFingerPrints = f;
		}
	}
	

	
	public static class StepInfo implements Serializable
	{
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		public Vector<Fingerprint> mFingerprints;
		public double mPosX;
		public double mPosY;

		StepInfo( Vector<Fingerprint> f, double x, double y)
		{
			mFingerprints = f;
			mPosX = x;
			mPosY = y;
		}
		
		public Object deepCopy() throws IOException, ClassNotFoundException {
	        ByteArrayOutputStream bos = new ByteArrayOutputStream();
	        ObjectOutputStream oos = new ObjectOutputStream(bos);
	        oos.writeObject(this);
	        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
	        ObjectInputStream ois = new ObjectInputStream(bis);
	        return ois.readObject();
	    }
	}
	
}
