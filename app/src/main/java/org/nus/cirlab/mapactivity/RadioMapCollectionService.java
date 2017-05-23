package org.nus.cirlab.mapactivity;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;

import com.google.android.gms.maps.model.LatLng;

import org.nus.cirlab.mapactivity.DataStructure.Fingerprint;
import org.nus.cirlab.mapactivity.DataStructure.RadioMap;
import org.nus.cirlab.mapactivity.DataStructure.StepInfo;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Vector;


public class RadioMapCollectionService extends Service implements SensorEventListener {

	// Step Detection Thresholds and Variables
	double mUpperThresh = 2.5;// 1.2;// 2;//2.5;
	double mLowerThresh = -1.5;// -0.5;// -1.2;//-1.5;
	int mMaxDistThresh = 150;// 600;// 250;//150;
	int mMinDistThresh = 15;// 5;// 10;//15;
	int mCurrentStepCount = 0;
	int mMinStepDistThresh = 15;// 5;// 10;//15;
	int mMaxStepDistThresh = 150;// 600;// 300;//150;
	int mMaxStillDistThresh = 600;// 2000;// 600;
	float mLastUpperPeak = -1;
	float mLastLowerPeak = -1;
	long mLastUpperPeakIndex = -1;
	long mLastLowerPeakIndex = -1;
	long mLastStepIndex = -1;
	long mSampleCount = 0;
	float mAzimuth = 0;
	float l = 0.5f;

	int port = 8080;
	String colorCode = "black";
	private WifiManager mWifiManager = null;
	private BroadcastReceiver mWifiReceiver = null;
	private Boolean mIsCollectingFP = false;
	private Boolean mIsCollectionStarted = false;
	private SensorManager mSensorManager = null;
	private RadioMap mRadioMap = new RadioMap(null);

	private Vector<StepInfo> mSendSteps = new Vector<StepInfo>();
	private Vector<StepInfo> mSteps = null;
	private Vector<StepInfo> mBackupSteps = null;
	private Vector<StepInfo> mNotConfirmedMapping = null;
	private Vector<StepInfo> mMappedSteps = new Vector<StepInfo>();
	private String MacAddr = null;
	private boolean startCountingStep = false;
	private boolean mIsWalking = false;
	private boolean mIsStepUpdated = false;
	private double mLocalizationError =-1;

	// Phone orientation
	private float[] mOrientVals = new float[3];
	private float mAngle = 0;
	private int mLastX;
	private int mLastY;
	private float[] mRotationMatrix = new float[16];
	private float[] mLinearVector = new float[4];
	private float[] mWorldAcce = new float[4];
	private float[] mRotationVector = new float[4];
	private float[] mInverseRotationMatrix = new float[16];
//	private float[] mMag = new float[3];



	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	public void onSensorChanged(SensorEvent event) {

		switch (event.sensor.getType()) {
			case Sensor.TYPE_ROTATION_VECTOR:
				// Calculate new rotation matrix
				SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);

				mRotationVector[0] = event.values[0];
				mRotationVector[1] = event.values[1];
				mRotationVector[2] = event.values[2];

				SensorManager.getOrientation(mRotationMatrix, mOrientVals);
				mAngle = (float) normalizeAngle(mOrientVals[0]);
				mAzimuth = (float) Math.toDegrees(mAngle);
				break;

//			case Sensor.TYPE_MAGNETIC_FIELD:
//				mMag[0] = event.values[0];
//				mMag[1] = event.values[1];
//				mMag[2] = event.values[2];
//				break;

			case Sensor.TYPE_LINEAR_ACCELERATION:
				// Update rotation matrix, inverted version
				mLinearVector[0] = event.values[0];
				mLinearVector[1] = event.values[1];
				mLinearVector[2] = event.values[2];

				android.opengl.Matrix.invertM(mInverseRotationMatrix, 0, mRotationMatrix, 0);
				android.opengl.Matrix.multiplyMV(mWorldAcce, 0, mInverseRotationMatrix, 0, mLinearVector, 0);

				// Update walking state and step count
				if (startCountingStep)
					updateStep();

				break;
		}
	}

	public void updateStep() {
		// Increase current sample count
		mSampleCount++;

		if (mSampleCount < 0) {
			mLastUpperPeak = -1;
			mLastLowerPeak = -1;
			mLastUpperPeakIndex = -1;
			mLastLowerPeakIndex = -1;
			mLastStepIndex = -1;
			mSampleCount = 0;
		}

		// If the user is standing still for too much time, reset the walking
		// state
		if (mSampleCount - mLastStepIndex > mMaxStillDistThresh) {
			mIsWalking = false;
		}

		// Detect steps based on zAcc
		if (mWorldAcce[2] > mUpperThresh) {
			mLastUpperPeak = mWorldAcce[2];
			mLastUpperPeakIndex = mSampleCount;

			if (mLastLowerPeakIndex != -1 && mLastUpperPeakIndex - mLastLowerPeakIndex < mMaxDistThresh
					&& mLastUpperPeakIndex - mLastLowerPeakIndex > mMinDistThresh
					&& mSampleCount - mLastStepIndex > mMinStepDistThresh) {
				// In the walking state, new step detected
				if (mIsWalking && startCountingStep) {
					// Toast.makeText(getBaseContext(),"Step:"+mCurrentStepCount,
					// Toast.LENGTH_SHORT).show();
					mIsStepUpdated = true;

					mCurrentStepCount++;
					mLastStepIndex = mSampleCount;
					// Reset last lower peak for future steps
					mLastLowerPeakIndex = -1;

				} else {
					// Not in the walking state, transit to the walking state if
					// one candidate step detected
					if (mSampleCount - mLastStepIndex < mMaxStepDistThresh) {
						mIsWalking = true;
					}
					mLastStepIndex = mSampleCount;
				}
			}
		} else if (mWorldAcce[2] < mLowerThresh) {
			if (mWorldAcce[2] < mLastLowerPeak || mSampleCount - mLastLowerPeakIndex > mMaxDistThresh) {
				mLastLowerPeak = mWorldAcce[2];
				mLastLowerPeakIndex = mSampleCount;
			}
		}
	}

	public float normalizeAngle(float angle) {
		angle = (float) (angle % (2 * Math.PI));
		return (float) (angle < 0 ? angle + 2 * Math.PI : angle);
	}

	@Override
	public IBinder onBind(Intent intent) {
		IBinder result = null;
		if (null == result) {
			result = new MyBinder();
		}
		return result;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stopCollectingFingerprints();
		stopCollection();
	}

	public void clear() {
		mSteps = null;
		mBackupSteps = null;
		mNotConfirmedMapping = null;
		mMappedSteps = new Vector<StepInfo>();
	}

	public void startCollectingFingerprints() {
		if (mIsCollectingFP)
			return;
		// By default use wifi only
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		if (mWifiManager != null)
			MacAddr = mWifiManager.getConnectionInfo().getMacAddress();
		if (!mWifiManager.isWifiEnabled())
			mWifiManager.setWifiEnabled(true);

		new EnableWifiTask().execute(null, null, null);
	}

	public void stopCollectingFingerprints() {
		if (!mIsCollectingFP)
			return;

		this.unregisterReceiver(mWifiReceiver);
		mIsCollectingFP = false;

	}

	public void startCollection() {
		if (mIsCollectionStarted)
			return;

		mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
				SensorManager.SENSOR_DELAY_FASTEST);
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
				SensorManager.SENSOR_DELAY_FASTEST);


		mIsCollectionStarted = true;

		new Thread(new Runnable() {
			public void run() {
				try {
					while (mIsCollectionStarted) {
						if (mIsStepUpdated && startCountingStep) {
							Vector<Fingerprint> tmp = getFingerprint();

							if (mSteps == null || mSteps.size() == 0) {
								mLastX = 0;
								mLastY = 0;
								mSteps = new Vector<>();
								mSteps.add(new StepInfo(tmp, mLastX, mLastY));
								mSendSteps.add(new StepInfo(tmp, mLastX, mLastY));
							} else {
								mLastX += 10.0 * Math.cos(((450 - mAzimuth) % 360) * 1.0 / 180 * Math.PI);
								mLastY -= 10.0 * Math.sin(((450 - mAzimuth) % 360) * 1.0 / 180 * Math.PI);
								mSteps.add(new StepInfo(tmp, mLastX, mLastY));
								mSendSteps.add(new StepInfo( tmp, mLastX, mLastY));

							}
							mIsStepUpdated = false;
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();

	}

	public void stopCollection() {
		if (!mIsCollectionStarted)
			return;

		mIsCollectionStarted = false;
		mSensorManager.unregisterListener(this);
	}

	public Vector<Fingerprint> getFingerprint() {
		if (!mIsCollectingFP) {
			return null;
		}
		Vector<Fingerprint> currentFP = new Vector<>();
		List<ScanResult> result = mWifiManager.getScanResults();
		for (ScanResult r : result) {
			if (r.frequency > 0 && r.level!=0) {
				currentFP.add(new Fingerprint( r.BSSID, Math.abs(r.level), r.frequency));
			}
		}

		return currentFP;
	}

	public LatLng getLocation(Vector<Fingerprint> fp) {
		if (mRadioMap == null || fp == null)
			return null;
		return WeightedLocalization(fp, mRadioGuassianMap.keySet());
	}

	public LatLng WeightedLocalization(Vector<Fingerprint> fp, Set<LatLng> CandidateSet) {

		LatLng Key = null;
		double sum;
		double maxScore = 0;

		for (LatLng k : CandidateSet) {
			int number = 0;
			sum = 0;
			for (Fingerprint f2 : fp) {
				if (mRadioGuassianMap.get(k).containsKey(f2.mMac)) {
					number++;
					double diff = 1;
					double impactFactor = 1.0 / mRadioGuassianMap.get(k).get(f2.mMac);
					if (mRadioGuassianMap.get(k).get(f2.mMac) != f2.mRSSI) {
						diff = Math.abs(mRadioGuassianMap.get(k).get(f2.mMac) - f2.mRSSI);
						sum += impactFactor * (1.0 / diff);
					} else {
						sum += impactFactor * (1.0);
					}
				}
			}

			if (number > fp.size() / 2 && sum > maxScore) {
				maxScore = sum;
				Key = new LatLng(k.latitude, k.longitude);
			}
		}
		return Key;
	}

	public RadioMap appendRadioMapFromMapping() {

		HashMap<LatLng, Vector<Fingerprint>> ID2StepInfo = new HashMap<>();

		LatLng tmpKey;
		for (StepInfo s : mMappedSteps) {
			tmpKey = new LatLng( s.mPosX , s.mPosY);
			if (!ID2StepInfo.containsKey(tmpKey)) {
				Vector<Fingerprint> tmpInfo = new Vector<>();
				for (Fingerprint f : s.mFingerprints) {
					tmpInfo.add(f);
				}
				ID2StepInfo.put(tmpKey, tmpInfo);
			} else {
				for (Fingerprint f : s.mFingerprints) {
					ID2StepInfo.get(tmpKey).add(f);
				}
			}
		}
		mRadioMap.mLocFingerPrints = ID2StepInfo;
		return mRadioMap;
	}

	public Vector<StepInfo> mapCurrentTrajectory(LatLng startLoc, LatLng endLoc) {
		try {
			if (mSteps == null)
				return null;

			if (mBackupSteps == null || mBackupSteps.size() == 0) {

				mBackupSteps = new Vector<>();
				for (StepInfo s : mSteps)
					mBackupSteps.add((StepInfo) s.deepCopy());

				mNotConfirmedMapping = (Vector<StepInfo>) mSteps.clone();
				mNotConfirmedMapping = PiLocHelper.mapTrajectory(startLoc, endLoc,
						mNotConfirmedMapping);
				mSteps.clear();
			} else {
				mNotConfirmedMapping = new Vector<>();
				for (StepInfo s : mBackupSteps)
					mNotConfirmedMapping.add((StepInfo) s.deepCopy());

				mNotConfirmedMapping = PiLocHelper.mapTrajectory(startLoc, endLoc,
						mNotConfirmedMapping);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return mNotConfirmedMapping;
	}
	
	public void confirmCurrentMapping() {
		if (mNotConfirmedMapping == null)
			return;

		mMappedSteps.addAll(mNotConfirmedMapping);
		mBackupSteps.clear();
	}

	public void removeCurrentMapping() {
		mSteps.clear();
		mBackupSteps.clear();
		mNotConfirmedMapping.clear();
	}

	public void removeCollectedDataInTheMemory() {
		mMappedSteps.clear();
	}

	public boolean saveRadiomap(String filePath) {
		try {
			if (mRadioMap == null)
				return false;

//			String path = Environment.getExternalStorageDirectory().getAbsolutePath();
			File folder = new File(getExternalCacheDir()  + "/PiLoc/");
			if (!folder.exists()) {
				folder.mkdirs();
			}

			File f = new File(getExternalCacheDir()  + "/PiLoc/" + filePath);
			FileOutputStream fos = new FileOutputStream(f);
			for (LatLng p : mRadioMap.mLocFingerPrints.keySet()) {
				String writeString = p.latitude + " " + p.longitude;
				for (Fingerprint fp : mRadioMap.mLocFingerPrints.get(p)) {
					writeString += " " + fp.mMac + " " + fp.mRSSI+ " "+ fp.mFrequency;
				}
				writeString += "\n";
				fos.write(writeString.getBytes());
			}
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public boolean uploadRadioMap(String serverIP, String remoteID, String floorID) { //key function
		try {
			confirmCurrentMapping();

			if (mMappedSteps == null || mMappedSteps.size() == 0)
				return false;

			// String sendString = ;
			StringBuilder builder = new StringBuilder();
			builder.append(remoteID + "#" + floorID + "#");

//			for (LatLng p : this.mRadioMap.mLocFingerPrints.keySet()) {
//				builder.append(p.latitude + " " + p.longitude);
//				for (Fingerprint fp : mRadioMap.mLocFingerPrints.get(p)) {
//					builder.append(" " + fp.mMac + " " + fp.mRSSI+" "+fp.mFrequency);
//				}
//				builder.append("\n");
//			}

			for (StepInfo sp : this.mMappedSteps) {
				builder.append(sp.mPosX + " " + sp.mPosY);
				for (Fingerprint fp : sp.mFingerprints) {
					builder.append(" " + fp.mMac + " " + fp.mRSSI+" "+fp.mFrequency);
				}
				builder.append("\n");
			}

			String sendString = builder.toString();

			String requestURL = "http://" + serverIP + ":" + port + "/UploadRadiomap";

			String BOUNDARY = "---------";
			URL url = new URL(requestURL);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setUseCaches(false);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("connection", "Keep-Alive");
			conn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)");
			conn.setRequestProperty("Charsert", "UTF-8");
			conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

			OutputStream out = new DataOutputStream(conn.getOutputStream());
			byte[] end_data = ("\r\n--" + BOUNDARY + "--\r\n").getBytes();

			StringBuilder sb = new StringBuilder();
			sb.append("--");
			sb.append(BOUNDARY);
			sb.append("\r\n");
			sb.append("Content-Type:application/octet-stream\r\n\r\n");

			out.write(sendString.getBytes(), 0, sendString.getBytes().length);
			out.flush();
			out.close();

			mMappedSteps.clear();
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.equals("mapping data uploaded successfully")) {
					// uploadAllSteps(serverIP, remoteID);
					return true;
				} else
					return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		return false;

	}

	public boolean uploadRadioMapConfig(String serverIP, String remoteID,  String floorID, ArrayList<LatLng> mNodeConfigMap) {
		try {
			if (mNodeConfigMap == null || mNodeConfigMap.size() == 0)
				return false;

			// String sendString = ;
			StringBuilder builder = new StringBuilder();
			builder.append(remoteID + "#" + floorID + "#");

			for (LatLng p : mNodeConfigMap) {
				builder.append(p.latitude + " " + p.longitude);
				builder.append("\n");
			}
			String sendString = builder.toString();
			String requestURL = "http://" + serverIP + ":" + port;
			requestURL += "/UploadRadiomapConfig";


			String BOUNDARY = "---------";
			URL url = new URL(requestURL);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setUseCaches(false);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("connection", "Keep-Alive");
			conn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)");
			conn.setRequestProperty("Charsert", "UTF-8");
			conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

			OutputStream out = new DataOutputStream(conn.getOutputStream());
			byte[] end_data = ("\r\n--" + BOUNDARY + "--\r\n").getBytes();

			StringBuilder sb = new StringBuilder();
			sb.append("--");
			sb.append(BOUNDARY);
			sb.append("\r\n");
			sb.append("Content-Type:application/octet-stream\r\n\r\n");

			out.write(sendString.getBytes(), 0, sendString.getBytes().length);
			out.flush();
			out.close();

			mMappedSteps.clear();
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line = null;
			while ((line = reader.readLine()) != null) {
				if (line.equals("config data uploaded successfully")) {
					return true;
				} else
					return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		return false;

	}

	public RadioMap getRadioMap(String serverIP, String remoteID, String floorLevel) {
		try {
			try {
//				String path = Environment.getExternalStorageDirectory().getAbsolutePath();
				File folder = new File(getExternalCacheDir()  + "/PiLoc/"+remoteID+"/"+floorLevel);
				if (!folder.exists()) {
					folder.mkdirs();
				}
				File filename = new File(getExternalCacheDir()  + "/PiLoc/"+remoteID+"/"+floorLevel+"/radiomap.rm");
				FileOutputStream fos = new FileOutputStream(filename);

				remoteID = remoteID.replace(" ", "-");
				String request = "http://" + serverIP + ":" + port + "/Download?id=" + remoteID +"&level="+floorLevel+"&type=gp";
				URL rmUrl = new URL(request);
				HttpURLConnection urlConn = (HttpURLConnection) rmUrl.openConnection();
				urlConn.setDoInput(true);
				urlConn.connect();
				BufferedReader in = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));

				String readLine = "";
				Vector<String> result = new Vector<String>();
				while ((readLine = in.readLine()) != null) {
					result.add(readLine);
					fos.write((readLine+"\n").getBytes());
				}
				fos.close();
				in.close();

//				loadRadioMapFromString(result);
				loadRadioGaussianMapFromString(result);

			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		mMappedSteps.clear();

		return mRadioMap;
	}

	public RadioMap loadRadioMap( String remoteID, String floorLevel) {
		try {
			try {
//				String path = Environment.getExternalStorageDirectory().getAbsolutePath();
				File filename = new File(getExternalCacheDir()  + "/PiLoc/"+remoteID+"/"+floorLevel+"/radiomap.rm");
				BufferedReader in = new BufferedReader(new FileReader(filename));

				String readLine = "";
				Vector<String> result = new Vector<String>();
				while ((readLine = in.readLine()) != null) {
					result.add(readLine);
				}
				in.close();

//				loadRadioMapFromString(result);
				loadRadioGaussianMapFromString(result);

			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		mMappedSteps.clear();

		return mRadioMap;
	}

	public ArrayList<LatLng> loadTrace( String remoteID, String floorLevel, String filename) {
		ArrayList<LatLng> nodeList = new ArrayList<>();
		try {
			try {
//				String path = Environment.getExternalStorageDirectory().getAbsolutePath();
				File file = new File(getExternalCacheDir()  + "/PiLoc/"+remoteID+"/"+floorLevel+"/"+filename);
				BufferedReader in = new BufferedReader(new FileReader(file));

				String readLine ;
				Vector<String> result = new Vector<>();
				while ((readLine = in.readLine()) != null) {
					double lat = Double.parseDouble(readLine.split(" ")[0]);
					double lng = Double.parseDouble(readLine.split(" ")[1]);
					nodeList.add(new LatLng(lat, lng));
				}
				in.close();
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return nodeList;
	}

	private HashMap<LatLng, HashMap<String, Integer>> mRadioGuassianMap = new HashMap<>();

	public double getLocalizationError(){
		return mLocalizationError;
	}


	public boolean saveNewlyCollectedTrace(String FloorID, String floorLevel) {

		confirmCurrentMapping();

		if (mMappedSteps == null || mMappedSteps.size() == 0)
			return false;

		try {
//			String path = Environment.getExternalStorageDirectory().getCanonicalPath();
			File folder = new File(getExternalCacheDir() + "/PiLoc/"+FloorID+"/"+floorLevel+"/");
			boolean success = true;
			if (!folder.exists()) {
				success = folder.mkdirs();
			}

			SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MMdd_HHmm");
			String Timestamp = sdf.format(new Date());

			File f = new File(folder+"/"+Timestamp+".md");
			FileOutputStream fos = new FileOutputStream(f);

			String writeString = "";
			for (StepInfo s : this.mMappedSteps) {
				writeString += s.mPosX + " " + s.mPosY;
				for (Fingerprint fp : s.mFingerprints) {
					writeString += " " + fp.mMac + " " + fp.mRSSI+" "+fp.mFrequency;
					// fp.mType;
				}
				writeString += "\n";
			}
			fos.write(writeString.getBytes());
			fos.flush();
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public boolean uploadTraceFile(String serverIP, String FloorID, String floorLevel, String filename) { //key function
		try {

//			String path = Environment.getExternalStorageDirectory().getAbsolutePath();
			File file = new File(getExternalCacheDir()  + "/PiLoc/"+FloorID+"/"+floorLevel+"/"+filename);

			BufferedReader in = new BufferedReader(new FileReader(file));

			// String sendString = ;
			StringBuilder builder = new StringBuilder();
			builder.append(FloorID + "#" + floorLevel + "#");
			String line_str;
			while((line_str = in.readLine())!=null){
				builder.append(line_str+"\n");
			}
			in.close();

			String sendString = builder.toString();

			String requestURL = "http://" + serverIP + ":" + port + "/UploadRadiomap";

			String BOUNDARY = "---------";
			URL url = new URL(requestURL);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setUseCaches(false);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("connection", "Keep-Alive");
			conn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)");
			conn.setRequestProperty("Charsert", "UTF-8");
			conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

			OutputStream out = new DataOutputStream(conn.getOutputStream());
			byte[] end_data = ("\r\n--" + BOUNDARY + "--\r\n").getBytes();

			StringBuilder sb = new StringBuilder();
			sb.append("--");
			sb.append(BOUNDARY);
			sb.append("\r\n");
			sb.append("Content-Type:application/octet-stream\r\n\r\n");

			out.write(sendString.getBytes(), 0, sendString.getBytes().length);
			out.flush();
			out.close();

			mMappedSteps.clear();
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.equals("mapping data uploaded successfully")) {
					// uploadAllSteps(serverIP, remoteID);
					return true;
				} else
					return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		return false;

	}

	public RadioMap loadRadioGaussianMapFromString(Vector<String> mapStrings) {
		try {
			HashMap<LatLng, Vector<Fingerprint>> locMap = new HashMap<>();
			mRadioGuassianMap = new HashMap<>();

			for (String line : mapStrings) {
				HashMap<String, Integer> gpHash = new HashMap<>();
				String[] tokens = line.split(" ");
				if(tokens.length == 3){
					mLocalizationError = Double.parseDouble(tokens[2]);
				}else {
					LatLng loc = new LatLng(Double.parseDouble(tokens[0]), Double.parseDouble(tokens[1]));
					Vector<Fingerprint> fp = new Vector<>();
					for (int i = 0; i < (tokens.length - 2) / 2; i++) {
						String mac = tokens[i * 2 + 2];
						String[] element = tokens[i * 2 + 3].split(",");
						if(element.length==4){
							int rssi = Integer.parseInt(element[0]);
							fp.add(new Fingerprint(mac, rssi, 0));
							gpHash.put(mac, rssi);
						}

					}

					locMap.put(loc, fp);
					mRadioGuassianMap.put(loc, gpHash);
				}

			}
			mRadioMap.mLocFingerPrints = locMap;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return mRadioMap;
	}

//	public RadioMap loadRadioMapFromString(Vector<String> mapStrings) {
//		try {
//			HashMap<LatLng, Vector<Fingerprint>> locMap = new HashMap<>();
//			mRadioGuassianMap = new HashMap<>();
//
//			for (String line : mapStrings) {
//				HashMap<String, Integer> gpHash = new HashMap<>();
//				String[] tokens = line.split(" ");
//				LatLng loc = new LatLng(Double.parseDouble(tokens[0]), Double.parseDouble(tokens[1]));
//				Vector<Fingerprint> fp = new Vector<>();
//				for (int i = 0; i < (tokens.length - 2) / 2; i++) {
//					String mac = tokens[i * 2 + 2];
//					int rssi = Integer.parseInt(tokens[i * 2 + 3]);
//					fp.add(new Fingerprint(mac, rssi, 0));
//					gpHash.put(mac, rssi);
//				}
//
//				locMap.put(loc, fp);
//				mRadioGuassianMap.put(loc, gpHash);
//			}
//			mRadioMap.mLocFingerPrints = locMap;
//		} catch (Exception e) {
//			e.printStackTrace();
//			return null;
//		}
//		return mRadioMap;
//	}

	public Vector<String> getCurrentFloorIDList(String serverIP, Vector<Fingerprint> fp) {
		ArrayList<String> compressed = new ArrayList<>();

		for(Fingerprint f : fp){
			String mac = f.mMac.substring(0,16);
			if(!compressed.contains(mac)){
				compressed.add(mac);
			}
		}
//		compressed.add("a8:9d:21:44:05:a");


		Vector<String> IDList = new Vector<>();

		try {
			String request = "http://" + serverIP + ":" + port + "/QueryFp?fingerprint=";

			for (String mac : compressed) {
				request += mac + ",";
			}
			request.substring(0, request.length() - 1);

			URL rmUrl = new URL(request);
			HttpURLConnection urlConn = (HttpURLConnection) rmUrl.openConnection();
			urlConn.setDoInput(true);
			urlConn.connect();

			BufferedReader in = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));

			String readLine = "";
			while ((readLine = in.readLine()) != null) {
				String[] tokens = readLine.split("#");
				for (int i = 0; i < tokens.length; i++) {
					IDList.add(tokens[i]);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		return IDList;
	}

	public void setStartCountingStep(boolean startCountingStep) {
		this.startCountingStep = startCountingStep;
	}

	public class MyBinder extends Binder {
		public RadioMapCollectionService getService() {
			// startCollectingFingerprints();
			return RadioMapCollectionService.this;
		}
	}

	private class EnableWifiTask extends AsyncTask<String, Void, String> {
		protected String doInBackground(String... url) {

			while (!mWifiManager.isWifiEnabled()) {
			} // wait for wifi by default

			IntentFilter wifiIntent = new IntentFilter();
			wifiIntent.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
			mWifiReceiver = new BroadcastReceiver() {
				public void onReceive(Context c, Intent i) {
					mWifiManager.startScan();
				}
			};
			registerReceiver(mWifiReceiver, wifiIntent);
			mWifiManager.startScan();
			mIsCollectingFP = true;

			return null;
		}
	}
}
