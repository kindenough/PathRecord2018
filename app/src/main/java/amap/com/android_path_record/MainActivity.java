package amap.com.android_path_record;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationClientOption.AMapLocationMode;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapUtils;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.trace.LBSTraceClient;
import com.amap.api.trace.TraceListener;
import com.amap.api.trace.TraceLocation;
import com.amap.api.trace.TraceOverlay;

import org.kymjs.kjframe.KJHttp;
import org.kymjs.kjframe.http.HttpCallBack;
import org.kymjs.kjframe.http.HttpParams;
import org.kymjs.kjframe.http.Request;
import org.kymjs.kjframe.utils.KJLoger;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import amap.com.database.DbAdapter;
import amap.com.record.PathRecord;
import amap.com.recorduitl.Util;


public class MainActivity extends Activity implements LocationSource,
		AMapLocationListener, TraceListener {
	private final static int CALLTRACE = 0;
	private MapView mMapView;
	private AMap mAMap;
	private OnLocationChangedListener mListener;
	private AMapLocationClient mLocationClient;
	private AMapLocationClientOption mLocationOption;
	private PolylineOptions mPolyoptions, tracePolytion;
	private Polyline mpolyline;
	private PathRecord record;
	private long mStartTime;
	private long mEndTime;
	private ToggleButton btn;
	private ToggleButton zoombtn;
	private ToggleButton shareBtn;
	private DbAdapter DbHepler;
	private DbAdapter DbHeplerGps;
	private List<TraceLocation> mTracelocationlist = new ArrayList<TraceLocation>();
	private List<TraceOverlay> mOverlayList = new ArrayList<TraceOverlay>();
	private List<AMapLocation> recordList = new ArrayList<AMapLocation>();
	private int tracesize = 30;
	private int mDistance = 0;
	private TraceOverlay mTraceoverlay;
	private TextView mResultShow;
	private Marker mlocMarker;

	private String newName = "record.db";
	//要上传的本地文件路径
	private String uploadFilePath = android.os.Environment
			.getExternalStorageDirectory().getAbsolutePath() + "/recordPath/record.db";
	//上传到服务器的指定位置
	private String actionUrl = "http://wncg.kingtopinfo.com/upload/upload.ashx";

	private LocationManager mgr=null;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.basicmap_activity);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		mMapView = (MapView) findViewById(R.id.map);
		mMapView.onCreate(savedInstanceState);// 此方法必须重写
		init();
		initpolyline();

		//步骤 1: 获取系统的定位管理器
		mgr = (LocationManager)getSystemService(LOCATION_SERVICE);
		mgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000/*毫秒，为测试方便*/, 1/*米*/, onLocationChange/*位置监听器*/);
		DbHeplerGps = new DbAdapter(this);
	}
	//步骤3：位置监听器LocationListener 的设置，当位置发生变化是触发onLocationChanged( )
	LocationListener onLocationChange = new LocationListener(){
		public void onLocationChanged(Location amapLocation) {
			//addText("Location Changed : (" + location.getLongitude()+","+location.getLatitude()+")");

			String angle = String.valueOf(amapLocation.getBearing());
			String y = String.valueOf(amapLocation.getLatitude());
			String x = String.valueOf(amapLocation.getLongitude());
			String speed = String.valueOf(amapLocation.getSpeed());
			String ltime = String.valueOf(amapLocation.getTime());
			String dtime = getcueDate(System.currentTimeMillis());
			String acc = String.valueOf(amapLocation.getAccuracy());

			DbHeplerGps.open();
			DbHeplerGps.createPositionApi(x,y,ltime,dtime,speed,angle,acc);
			DbHeplerGps.close();
		}
		public void onProviderDisabled(String arg0) {
			//addText("onProviderDisabled");
		}
		public void onProviderEnabled(String arg0) {
			//addText("onProviderEnabled");
		}
		public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
			//addText("onStatusChanged");
		}
	};

	//上传按钮事件
    public void nClick_BtnUpload(View view) {
		uploadFile();
        //Toast.makeText(getApplicationContext(), "upload clicked", Toast.LENGTH_LONG).show();
    }
 /**
	 * 初始化AMap对象
	 */
	private void init() {
		if (mAMap == null) {
			mAMap = mMapView.getMap();
			setUpMap();
		}

        shareBtn = (ToggleButton) findViewById(R.id.shareBtn);
		zoombtn = (ToggleButton) findViewById(R.id.zoombtn);
		btn = (ToggleButton) findViewById(R.id.locationbtn);
		btn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (btn.isChecked()) {
					mAMap.clear(true);
					if (record != null) {
						record = null;
					}
					record = new PathRecord();
					mStartTime = System.currentTimeMillis();
					record.setDate(getcueDate(mStartTime));
					mResultShow.setText("总距离");
				} else {
					mEndTime = System.currentTimeMillis();
					mOverlayList.add(mTraceoverlay);
					DecimalFormat decimalFormat = new DecimalFormat("0.0");
					mResultShow.setText(
							decimalFormat.format(getTotalDistance() / 1000d) + "KM");
					LBSTraceClient mTraceClient = new LBSTraceClient(getApplicationContext());
					mTraceClient.queryProcessedTrace(2, Util.parseTraceLocationList(record.getPathline()) , LBSTraceClient.TYPE_AMAP, MainActivity.this);
					saveRecord(record.getPathline(), record.getDate());
				}
			}
		});
		mResultShow = (TextView) findViewById(R.id.show_all_dis);

		mTraceoverlay = new TraceOverlay(mAMap);
	}

	protected void savePosition(AMapLocation amapLocation)
	{

		String angle = String.valueOf(amapLocation.getBearing());
		String y = String.valueOf(amapLocation.getLatitude());
		String x = String.valueOf(amapLocation.getLongitude());
		String speed = String.valueOf(amapLocation.getSpeed());
		String ltime = String.valueOf(amapLocation.getTime());
		String dtime = getcueDate(System.currentTimeMillis());

		DbHepler = new DbAdapter(this);
		DbHepler.open();
		DbHepler.createPosition(x,y,ltime,dtime,speed,angle,amapLocation.getProvider());
		DbHepler.close();
	}

	protected void saveRecord(List<AMapLocation> list, String time) {
		if (list != null && list.size() > 0) {
			DbHepler = new DbAdapter(this);
			DbHepler.open();
			String duration = getDuration();
			float distance = getDistance(list);
			String average = getAverage(distance);
			String pathlineSring = mStartTime + "," + mEndTime;//getPathLineString(list);
			AMapLocation firstLocaiton = list.get(0);
			AMapLocation lastLocaiton = list.get(list.size() - 1);
			String stratpoint = amapLocationToString(firstLocaiton);
			String endpoint = amapLocationToString(lastLocaiton);
			DbHepler.createrecord(String.valueOf(distance), duration, average,
					pathlineSring, stratpoint, endpoint, time);
			DbHepler.close();
		} else {
			Toast.makeText(MainActivity.this, "没有记录到路径", Toast.LENGTH_SHORT)
					.show();
		}
	}

	private String getDuration() {
		return String.valueOf((mEndTime - mStartTime) / 1000f);
	}

	private String getAverage(float distance) {
		return String.valueOf(distance / (float) (mEndTime - mStartTime));
	}

	private float getDistance(List<AMapLocation> list) {
		float distance = 0;
		if (list == null || list.size() == 0) {
			return distance;
		}
		for (int i = 0; i < list.size() - 1; i++) {
			AMapLocation firstpoint = list.get(i);
			AMapLocation secondpoint = list.get(i + 1);
			LatLng firstLatLng = new LatLng(firstpoint.getLatitude(),
					firstpoint.getLongitude());
			LatLng secondLatLng = new LatLng(secondpoint.getLatitude(),
					secondpoint.getLongitude());
			double betweenDis = AMapUtils.calculateLineDistance(firstLatLng,
					secondLatLng);
			distance = (float) (distance + betweenDis);
		}
		return distance;
	}

	private String getPathLineString(List<AMapLocation> list) {
		if (list == null || list.size() == 0) {
			return "";
		}
		StringBuffer pathline = new StringBuffer();
		for (int i = 0; i < list.size(); i++) {
			AMapLocation location = list.get(i);
			String locString = amapLocationToString(location);
			pathline.append(locString).append(";");
		}
		String pathLineString = pathline.toString();
		pathLineString = pathLineString.substring(0,
				pathLineString.length() - 1);
		return pathLineString;
	}

	private String amapLocationToString(AMapLocation location) {
		StringBuffer locString = new StringBuffer();
		locString.append(location.getLatitude()).append(",");
		locString.append(location.getLongitude()).append(",");
		locString.append(location.getProvider()).append(",");
		locString.append(location.getTime()).append(",");
		locString.append(location.getSpeed()).append(",");
		locString.append(location.getBearing());
		return locString.toString();
	}

	private void initpolyline() {
		mPolyoptions = new PolylineOptions();
		mPolyoptions.width(10f);
		mPolyoptions.color(Color.GRAY);
		tracePolytion = new PolylineOptions();
		tracePolytion.width(40);
		tracePolytion.setCustomTexture(BitmapDescriptorFactory.fromResource(R.drawable.grasp_trace_line));
	}

	/**
	 * 设置一些amap的属性
	 */
	private void setUpMap() {
		mAMap.setLocationSource(this);// 设置定位监听
		mAMap.getUiSettings().setMyLocationButtonEnabled(true);// 设置默认定位按钮是否显示
		mAMap.setMyLocationEnabled(true);// 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false
		// 设置定位的类型为定位模式 ，可以由定位、跟随或地图根据面向方向旋转几种
		mAMap.setMyLocationType(AMap.LOCATION_TYPE_LOCATE);
	}

	/**
	 * 方法必须重写
	 */
	@Override
	protected void onResume() {
		super.onResume();
		mMapView.onResume();
	}

	/**
	 * 方法必须重写
	 */
	@Override
	protected void onPause() {
		super.onPause();
		mMapView.onPause();
	}

	/**
	 * 方法必须重写
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		mMapView.onSaveInstanceState(outState);
	}

	/**
	 * 方法必须重写
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mMapView.onDestroy();
	}

	@Override
	public void activate(OnLocationChangedListener listener) {
		mListener = listener;
		startlocation();
	}

	@Override
	public void deactivate() {
		mListener = null;
		if (mLocationClient != null) {
			mLocationClient.stopLocation();
			mLocationClient.onDestroy();

		}
		mLocationClient = null;
	}

	/**
	 * 定位结果回调
	 * @param amapLocation 位置信息类
     */
	@Override
	public void onLocationChanged(AMapLocation amapLocation) {
		if (mListener != null && amapLocation != null) {
			if (amapLocation != null && amapLocation.getErrorCode() == 0) {
				mListener.onLocationChanged(amapLocation);// 显示系统小蓝点
				LatLng mylocation = new LatLng(amapLocation.getLatitude(),
						amapLocation.getLongitude());
				if( zoombtn.isChecked() ) {
					mAMap.moveCamera(CameraUpdateFactory.changeLatLng(mylocation));
				}
				if (btn.isChecked()) {
					record.addpoint(amapLocation);
					mPolyoptions.add(mylocation);
					mTracelocationlist.add(Util.parseTraceLocation(amapLocation));
					redrawline();
					if (mTracelocationlist.size() > tracesize - 1) {
						trace();
					}
					savePosition(amapLocation);
				}
				//共享位置
				if(shareBtn.isChecked()){

					//网络请求
					KJHttp kjh = new KJHttp();
					HttpParams params = new HttpParams();
					params.put("tel", "360");
					//params.put("rec", "");
					params.put("x", Double.toString(amapLocation.getLongitude()));
					params.put("y", Double.toString(amapLocation.getLatitude()));
					params.put("speed", Float.toString(amapLocation.getSpeed()));
					params.put("time", Util.formatUTC(System.currentTimeMillis(),
							"yyyy-MM-dd HH:mm:ss"));
					params.put("accuracy", amapLocation.getAccuracy() + "米");
					params.put("deviceid", "360");
					params.put("angle", Float.toString(amapLocation.getBearing()));
					params.put("teamid", "1");
					kjh.post("http://wncg.kingtopinfo.com/mymvc4/position2/postposition", params,
							new HttpCallBack() {
								@Override
								public void onPreStart() {
									super.onPreStart();
									KJLoger.debug("即将开始http请求");
									// et.setText("即将开始http请求");
								}

								@Override
								public void onSuccess(String t) {
									super.onSuccess(t);
//									ViewInject.longToast("请求成功");
									KJLoger.debug("请求成功:" + t.toString());
									// et.setText("请求成功:" + t.toString());
								}

								@Override
								public void onFailure(int errorNo, String strMsg) {
									super.onFailure(errorNo, strMsg);
									KJLoger.debug("出现异常:" + strMsg);
									// et.setText("出现异常:" + strMsg);
								}

								@Override
								public void onFinish() {
									super.onFinish();
									KJLoger.debug("请求完成，不管成功还是失败");
								}
							});
				}
			} else {
				String errText = "定位失败," + amapLocation.getErrorCode() + ": "
						+ amapLocation.getErrorInfo();
				Log.e("AmapErr", errText);
			}
		}
	}

	/**
	 * 开始定位。
	 */
	private void startlocation() {
		if (mLocationClient == null) {
			mLocationClient = new AMapLocationClient(this);
			mLocationOption = new AMapLocationClientOption();
			// 设置定位监听
			mLocationClient.setLocationListener(this);
			// 设置为高精度定位模式
			mLocationOption.setLocationMode(AMapLocationMode.Hight_Accuracy);

			mLocationOption.setInterval(2000);

			// 设置定位参数
			mLocationClient.setLocationOption(mLocationOption);
			// 此方法为每隔固定时间会发起一次定位请求，为了减少电量消耗或网络流量消耗，
			// 注意设置合适的定位时间的间隔（最小间隔支持为2000ms），并且在合适时间调用stopLocation()方法来取消定位请求
			// 在定位结束后，在合适的生命周期调用onDestroy()方法
			// 在单次定位情况下，定位无论成功与否，都无需调用stopLocation()方法移除请求，定位sdk内部会移除
			mLocationClient.startLocation();

		}
	}

	/**
	 * 实时轨迹画线
	 */
	private void redrawline() {
		if (mPolyoptions.getPoints().size() > 1) {
			if (mpolyline != null) {
				mpolyline.setPoints(mPolyoptions.getPoints());
			} else {
				mpolyline = mAMap.addPolyline(mPolyoptions);
			}
		}
//		if (mpolyline != null) {
//			mpolyline.remove();
//		}
//		mPolyoptions.visible(true);
//		mpolyline = mAMap.addPolyline(mPolyoptions);
//			PolylineOptions newpoly = new PolylineOptions();
//			mpolyline = mAMap.addPolyline(newpoly.addAll(mPolyoptions.getPoints()));
//		}
	}

	@SuppressLint("SimpleDateFormat")
	private String getcueDate(long time) {
		SimpleDateFormat formatter = new SimpleDateFormat(
				"yyyy-MM-dd  HH:mm:ss ");
		Date curDate = new Date(time);
		String date = formatter.format(curDate);
		return date;
	}

	public void record(View view) {
		Intent intent = new Intent(MainActivity.this, RecordActivity.class);
		startActivity(intent);
	}

	private void trace() {
		List<TraceLocation> locationList = new ArrayList<>(mTracelocationlist);
		LBSTraceClient mTraceClient = new LBSTraceClient(getApplicationContext());
		mTraceClient.queryProcessedTrace(1, locationList, LBSTraceClient.TYPE_AMAP, this);
		TraceLocation lastlocation = mTracelocationlist.get(mTracelocationlist.size()-1);
		mTracelocationlist.clear();
		mTracelocationlist.add(lastlocation);
	}

	/**
	 * 轨迹纠偏失败回调。
	 * @param i
	 * @param s
     */
	@Override
	public void onRequestFailed(int i, String s) {
		mOverlayList.add(mTraceoverlay);
		mTraceoverlay = new TraceOverlay(mAMap);
	}

	@Override
	public void onTraceProcessing(int i, int i1, List<LatLng> list) {

	}

	/**
	 * 轨迹纠偏成功回调。
	 * @param lineID 纠偏的线路ID
	 * @param linepoints 纠偏结果
	 * @param distance 总距离
	 * @param waitingtime 等待时间
     */
	@Override
	public void onFinished(int lineID, List<LatLng> linepoints, int distance, int waitingtime) {
		if (lineID == 1) {
			if (linepoints != null && linepoints.size()>0) {
				mTraceoverlay.add(linepoints);
				mDistance += distance;
				mTraceoverlay.setDistance(mTraceoverlay.getDistance()+distance);
				if (mlocMarker == null) {
					mlocMarker = mAMap.addMarker(new MarkerOptions().position(linepoints.get(linepoints.size() - 1))
							.icon(BitmapDescriptorFactory
									.fromResource(R.drawable.point))
							.title("距离：" + mDistance+"米"));
					mlocMarker.showInfoWindow();
				} else {
					mlocMarker.setTitle("距离：" + mDistance+"米");
					Toast.makeText(MainActivity.this, "距离"+mDistance, Toast.LENGTH_SHORT).show();
					mlocMarker.setPosition(linepoints.get(linepoints.size() - 1));
					mlocMarker.showInfoWindow();
				}
			}
		} else if (lineID == 2) {
			if (linepoints != null && linepoints.size()>0) {
				mAMap.addPolyline(new PolylineOptions()
						.color(Color.RED)
						.width(40).addAll(linepoints));
			}
		}

	}

	/**
	 * 最后获取总距离
	 * @return
     */
	private int getTotalDistance() {
		int distance = 0;
		for (TraceOverlay to : mOverlayList) {
			distance = distance + to.getDistance();
		}
		return distance;
	}

    /* 上传文件至Server的方法 */
    private void uploadFile() {
        String end = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        try {
            URL url = new URL(actionUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            /* 允许Input、Output，不使用Cache */
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setUseCaches(false);

            // 设置http连接属性
            con.setRequestMethod("POST");
            con.setRequestProperty("Connection", "Keep-Alive");
            con.setRequestProperty("Charset", "UTF-8");
            con.setRequestProperty("Content-Type",
                    "multipart/form-data;boundary=" + boundary);

            DataOutputStream ds = new DataOutputStream(con.getOutputStream());
            ds.writeBytes(twoHyphens + boundary + end);
            ds.writeBytes("Content-Disposition: form-data; "
                    + "name=\"file1\";filename=\"" + newName + "\"" + end);
            ds.writeBytes(end);

            // 取得文件的FileInputStream
            FileInputStream fStream = new FileInputStream(uploadFilePath);
            /* 设置每次写入1024bytes */
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            int length = -1;
            /* 从文件读取数据至缓冲区 */
            while ((length = fStream.read(buffer)) != -1) {
                /* 将资料写入DataOutputStream中 */
                ds.write(buffer, 0, length);
            }
            ds.writeBytes(end);
            ds.writeBytes(twoHyphens + boundary + twoHyphens + end);

            fStream.close();
            ds.flush();
            /* 取得Response内容 */
            InputStream is = con.getInputStream();
            int ch;
            StringBuffer b = new StringBuffer();
            while ((ch = is.read()) != -1) {
                b.append((char) ch);
            }
            /* 将Response显示于Dialog */
            //showDialog("上传成功" + b.toString().trim());
            Toast.makeText(MainActivity.this, "上传成功", Toast.LENGTH_SHORT).show();
            /* 关闭DataOutputStream */
            ds.close();
        } catch (Exception e) {
            //showDialog("上传失败" + e);
            Toast.makeText(MainActivity.this, "上传失败:"+uploadFilePath+e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

//	private void uploadFile2()
//	{
//        OkHttpClient client = new OkHttpClient();//创建OkHttpClient对象。
//        MediaType fileType = MediaType.parse("File/*");//数据类型为json格式，
//        File file = new File("path");//file对象.
//        RequestBody body = RequestBody.create(fileType , file );
//        Request request = new Request.Builder()
//                .url("http://www.baidu.com")
//                .post(body)
//                .build();
//        client.newCall(request).enqueue(new Callback() {。。。});//此处省略回调方法。
//        okhttp3.re
//	}
}
