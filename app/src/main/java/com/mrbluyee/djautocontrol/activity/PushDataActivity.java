package com.mrbluyee.djautocontrol.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.TextView;

import com.mrbluyee.djautocontrol.R;
import com.mrbluyee.djautocontrol.application.DJSDKApplication;
import com.mrbluyee.djautocontrol.application.WebRequestApplication;
import com.mrbluyee.djautocontrol.utils.ChargeStationInfo;
import com.mrbluyee.djautocontrol.utils.DroneStatusInfo;

import dji.common.battery.BatteryState;
import dji.common.flightcontroller.FlightControllerState;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;


public class PushDataActivity extends Activity {

    private TextView t,a,b,c,d,m,n,o,p;
    private BaseProduct mproduct = null;
    private FlightController mFlightController = null;
    private DroneStatusInfo DS = null;
    private static final String TAG = PushDataActivity.class.getName();
    private boolean webpostflag = false;
    private MyHandler myHandler;
    private WebRequestApplication webrequest = new WebRequestApplication();
    private String station_id = "112130";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dronestatus);
        DS = new DroneStatusInfo();
        DS.setDrone_id(this.getString(R.string.drone_id));
        initUI();
        initFlightController();
        myHandler = new MyHandler();
        IntentFilter filter = new IntentFilter();
        filter.addAction(DJSDKApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);
    }

    class MyHandler extends Handler {
        public MyHandler() {
        }
        public MyHandler(Looper L) {
            super(L);
        }
        // 子类必须重写此方法，接受数据
        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            Log.d(TAG,"handleMessage");
            super.handleMessage(msg);
            // 此处可以更新UI
            Bundle b = msg.getData();
            String drone_id = b.getString("uavid");
            if(drone_id != null) {
                if (drone_id.equals(DS.getDrone_id())) {
                    webpostflag = true;
                } else {
                    webpostflag = false;
                }
            }
        }
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        initFlightController();
    }

    @Override
    protected void onDestroy(){
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (mproduct != null && mproduct.isConnected()) {
                DS.setConnect_status(1);
            } else {
                DS.setConnect_status(0);
            }
            updateUI();
            webrequest.Get_chargesite_info(myHandler,station_id);
            if (webpostflag) {
                postDroneData();
            }
        }
    };

    private void initUI(){
        t=(TextView) findViewById(R.id.textt);
        a=(TextView) findViewById(R.id.texta);
        b=(TextView) findViewById(R.id.textb);
        c=(TextView) findViewById(R.id.textc);
        d=(TextView) findViewById(R.id.textd);
        m=(TextView) findViewById(R.id.textm);
        n=(TextView) findViewById(R.id.textn);
        o=(TextView) findViewById(R.id.texto);
        p=(TextView) findViewById(R.id.textp);
        t.setText("\n无人机实时状态参数显示\n");
    }

    private void updateUI(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                a.setText("电池电量： "+DS.getCharge()+"%\n");
                b.setText("当前电压： "+DS.getVoltage()+"mV\n");
                c.setText("当前电流： "+DS.getCurrent()+"mA\n");
                d.setText("电池温度： "+DS.getTemperature()+"摄氏度\n");
                m.setText("充电状态： "+(DS.getCharge_status()==1?"充电中":"放电中")+"\n");
                n.setText("经度："+DS.getLongitude()+"\n");
                o.setText("纬度："+DS.getLatitude()+"\n");
                p.setText("连接状态："+(DS.getConnect_status()==1?"连接中":"连接断开！"));
            }
        });
    }

    private void initFlightController() {
        mproduct = DJSDKApplication.getProductInstance();
        if (mproduct != null && mproduct.isConnected()) {
            if (mproduct instanceof Aircraft) {
                mFlightController = ((Aircraft)mproduct).getFlightController();
                mproduct.getBattery().setStateCallback(new BatteryState.Callback() {
                    @Override
                    public void onUpdate(BatteryState djiBatteryState) {
                        DS.setCharge(djiBatteryState.getChargeRemainingInPercent());
                        DS.setVoltage(djiBatteryState.getVoltage());
                        DS.setCurrent(djiBatteryState.getCurrent());
                        DS.setTemperature(djiBatteryState.getTemperature());
                        DS.setCharge_status(djiBatteryState.getCurrent() > 0 ? 1 : 0);
                        if (mproduct.isConnected()) {
                            DS.setConnect_status(1);
                        } else {
                            DS.setConnect_status(0);
                        }
                        updateUI();
                        webrequest.Get_chargesite_info(myHandler,station_id);
                        if (webpostflag) {
                            postDroneData();
                        }
                    }
                });
            }
        }
        if (mFlightController != null) {
            mFlightController.setStateCallback(
                    new FlightControllerState.Callback() {
                        @Override
                        public void onUpdate(FlightControllerState
                                                     djiFlightControllerCurrentState) {
                            DS.setLatitude(djiFlightControllerCurrentState.getAircraftLocation().getLatitude());
                            DS.setLongitude(djiFlightControllerCurrentState.getAircraftLocation().getLongitude());
                            updateUI();
                            webrequest.Get_chargesite_info(myHandler,station_id);
                            if (webpostflag) {
                                postDroneData();
                            }
                        }
                    });
        }
    }

    public void postDroneData(){
        String postdata = "uavid="+
                DS.getDrone_id()+
                "&power="+
                DS.getCharge()+
                "&temporary="+
                DS.getTemperature()+
                "&current="+
                DS.getCurrent()+
                "&linkstatus="+
                DS.getConnect_status()+
                "&stationid=112130";
        webrequest.Post_drone_info(postdata);
    }
}
