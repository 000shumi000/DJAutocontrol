package com.mrbluyee.djautocontrol.application;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;


import com.mrbluyee.djautocontrol.R;

import com.mrbluyee.djautocontrol.utils.ChargeStationInfo;
import com.scwang.smartrefresh.layout.api.RefreshLayout;
import com.scwang.smartrefresh.layout.listener.OnLoadmoreListener;
import com.scwang.smartrefresh.layout.listener.OnRefreshListener;


import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by rsj on 2018/1/12.
 */

public class StationStatusActivity extends AppCompatActivity {

    public String stationid;
    public String longitude;
    public String latitude;
    private TextView btn_set_target_station;
    public TextView text1;
    public TextView text2;
    public TextView text3;
    public TextView text4;
    public TextView textid;
    public TextView textcontrol;
    public TextView textlon;
    public TextView textlat;
    private WebRequestApplication webrequest = new WebRequestApplication();
    public MyHandler myHandler;
    private int SET_STATION_AS_TARGET = 2;
    private Timer mtimer = null;
    private TimerTask autofreshTask = null;
    private ChargeStationInfo chargeStationInfo = new ChargeStationInfo();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stationstatus);
        textid=(TextView)findViewById(R.id.textid);
        text1=(TextView)findViewById(R.id.text1);
        text2=(TextView)findViewById(R.id.text2);
        text3=(TextView)findViewById(R.id.text3);
        text4=(TextView)findViewById(R.id.text4);
        textlon=(TextView)findViewById(R.id.textlon);
        textlat=(TextView)findViewById(R.id.textlat);
        textcontrol=(TextView)findViewById(R.id.textcontrol);
        btn_set_target_station=(TextView)findViewById(R.id.set_target_station);
        Bundle bundle = this.getIntent().getExtras();
        stationid=bundle.getString("stationid");
        longitude=bundle.getString("longitude");
        latitude=bundle.getString("latitude");
        myHandler = new MyHandler();

        webrequest.Get_chargesite_info(myHandler,stationid);

        RefreshLayout refreshLayout = findViewById(R.id.refreshLayout);
        refreshLayout.setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh(RefreshLayout refreshlayout) {
                Toast.makeText(getApplicationContext(), "刷新成功", Toast.LENGTH_SHORT).show();
                text1.setText("充电站负载状态：");
                text2.setText("无人机电量：");
                text3.setText("更新时间:  ");
                text4.setText("无人机ID：");
                webrequest.Get_chargesite_info(myHandler,stationid);
                RefreshLayout refreshLayout = findViewById(R.id.refreshLayout);
                refreshlayout.finishRefresh(50/*,false*/);//传入false表示刷新失败
            }
        });
        refreshLayout.setOnLoadmoreListener(new OnLoadmoreListener() {
            @Override
            public void onLoadmore(RefreshLayout refreshlayout) {
                refreshlayout.finishLoadmore(50/*,false*/);//传入false表示加载失败
            }
        });
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        startTimer();

        //状态栏透明和间距处理
        StatusBarUtil.immersive(this);
        StatusBarUtil.setPaddingSmart(this, toolbar);
        StatusBarUtil.setPaddingSmart(this, findViewById(R.id.profile));
        StatusBarUtil.setPaddingSmart(this, findViewById(R.id.blurview));
        btn_set_target_station.setOnClickListener(new View.OnClickListener()//侦听登录点击事件
        {
            public void onClick(View v) {//设置目标站点
                Log.d("btn_set_target", "button pressed");
                if(chargeStationInfo.getStationStatus().equals("0")) {
                    //充电站空闲
                    Intent intent = new Intent();
                    intent.putExtra("station_id", stationid);
                    setResult(SET_STATION_AS_TARGET, intent);
                    stopTimer();
                    finish();
                }else{
                    Toast.makeText(getApplicationContext(),"station is not available",Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void startTimer(){
        if(mtimer == null){
            mtimer = new Timer();
        }
        if(autofreshTask == null){
            autofreshTask = new TimerTask() {
                @Override
                public void run() {
                    webrequest.Get_chargesite_info(myHandler,stationid);
                }
            };
        }
        if((mtimer != null)&&(autofreshTask != null)){
            mtimer.scheduleAtFixedRate(autofreshTask,1000,1000);
        }
    }

    private void stopTimer(){
        if (mtimer != null) {
            mtimer.cancel();
            mtimer = null;
        }
        if(autofreshTask != null){
            autofreshTask.cancel();
            autofreshTask = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
    }

    class MyHandler extends Handler {
        public MyHandler() {
        }
        public MyHandler(Looper L) {
            super(L);
        }

        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            super.handleMessage(msg);
            Bundle b = msg.getData();
            chargeStationInfo = webrequest.chargeSiteInfoHandler(b);
            if (chargeStationInfo.getStationId()!=null && chargeStationInfo.getStationId() != "0") {
                textid.setText("充电站ID：" + chargeStationInfo.getStationId());
                text3.setText("更新时间：" + chargeStationInfo.getStation_update_time());
                if (chargeStationInfo.getDroneId().equals("00000000")){
                    text4.setText("无人机ID：");
                }else {
                    text4.setText("无人机ID：" + chargeStationInfo.getDroneId());
                }
                text2.setText("无人机电量：" + chargeStationInfo.getDronePow());
                if (chargeStationInfo.getStationStatus().equals("0")) {
                    text1.setText("充电站负载状态： 空闲");
                    text2.setText("无人机电量：");
                }
                else if (chargeStationInfo.getStationStatus().equals("1"))
                    text1.setText("充电站负载状态： 正在充电");
                else if (chargeStationInfo.getStationStatus().equals("2"))
                    text1.setText("充电站负载状态： 已充满");
                textlon.setText("经度：" + longitude);
                textlat.setText("纬度：" + latitude);
                if(chargeStationInfo.getStationcontrol().equals("0")) {
                    textcontrol.setText("充电站状态: 关闭");
                }else{
                    textcontrol.setText("充电站状态: 开启");
                }
            }
            if(b.getString("IntentErr")!=null && b.getString("IntentErr").equals("1"))
            {
                Toast.makeText(getApplicationContext(),"网络错误，请重新连结",Toast.LENGTH_LONG).show();
            }
        }
    }
}
