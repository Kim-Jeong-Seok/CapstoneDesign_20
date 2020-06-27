package com.example.captest;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.kyleduo.switchbutton.SwitchButton;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import app.akexorcist.bluetotohspp.library.BluetoothSPP;
import app.akexorcist.bluetotohspp.library.BluetoothState;
import app.akexorcist.bluetotohspp.library.DeviceList;

public class home extends AppCompatActivity {
    String key = "공공데이터 미세먼지 API KEY";
    String data;
    TextView dust_result;
    ImageButton dust_imgBtn;

    static final int REQUEST_ENABLE_BT = 10;
    UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    BluetoothAdapter mBluetoothAdapter;
    Button btn_window_open;
    Button btn_window_close;
    Button btn_blind_on;
    Button btn_blind_off;
    Switch switch_auto;
    Switch switch_outdoor;

    Set<BluetoothDevice> mDevices;
    InputStream mInputStream = null;
    OutputStream mOutputStream = null;
    int mPairedDeviceCount = 0;
    BluetoothDevice mRemoteDevice;
    BluetoothSocket mSocket = null;
    String mStrDelimiter = "\n";
    Thread mWorkerThread = null;
    byte[] readBuffer;
    int readBufferPosition;

    private BluetoothSPP bt;

    //자동,수동모드 버튼
    Button btn_manual;
    Button btn_auto;
    //자동모드 시 창문 열림,닫힘을 제어할 토글 변수
    private int mBtnClickToggle;
    //타이머 구현에 필요한 변수들
    static int counter = 0;
    Timer timer = new Timer();
    static TimerTask tt;

    public void mOnClick(View v) {
        switch (v.getId()) {
            case R.id.dust_imgBtn:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        data = getXmlData();
                        // 아래 메소드를 호출해 XML data를 파싱해서 String 객체 얻어오기
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                dust_result.setText(data); // TextView에 문자열 data 출력
                            }
                        });
                    }
                }).start();
                break;
        }
    }

    String getXmlData() {
        StringBuffer buffer = new StringBuffer();
        String queryUrl = "http://openapi.airkorea.or.kr/openapi/services/rest/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty?stationName=서석동&dataTerm=month&pageNo=1&numOfRows=1&ServiceKey=" + key + "&ver=1.3";

        try {
            URL url = new URL(queryUrl); //문자열로 된 요청 url을 URL 객체로 생성
            InputStream is = url.openStream(); // url위치로 인풋스트림 연결

            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xpp = factory.newPullParser();
            // inputstream 으로부터 xml 입력받기
            xpp.setInput(new InputStreamReader(is, "UTF-8"));

            String tag;

            xpp.next();
            int eventType = xpp.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:
                        buffer.append("파싱 시작 \n\n");
                        break;

                    case XmlPullParser.START_TAG:
                        tag = xpp.getName(); // 태그 이름 얻어오기

                        if (tag.equals("item")) ; // 첫번째 검색결과
                         else if (tag.equals("pm10Value")) {
                            buffer.append("현재 미세먼지 농도 :");
                            xpp.next();
                            buffer.append(xpp.getText());
                            buffer.append("㎍/m³\n");
                        }
                        break;

                    case XmlPullParser.TEXT:
                        break;

                    case XmlPullParser.END_TAG:

                        tag = xpp.getName(); //태그 이름 얻어오기
                        if (tag.equals("pm10Value")) buffer.append("\n"); //첫번째 값 끊기
                        break;
                }
                eventType = xpp.next();
            }
        } catch (Exception e) {

        }
        return buffer.toString();
    }


    public void onDestroy() {
        try {
            this.mWorkerThread.interrupt();
            this.mInputStream.close();
            this.mOutputStream.close();
            this.mSocket.close();
        } catch (Exception e) {
        }
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        dust_result = (TextView) findViewById(R.id.dust_result);
        this.btn_window_open = (Button) findViewById(R.id.btn_window_open);
        this.btn_window_close = (Button) findViewById(R.id.btn_window_close);
        this.btn_blind_on = (Button) findViewById(R.id.btn_blind_on);
        this.btn_blind_off = (Button) findViewById(R.id.btn_blind_off);

        //타이머 초기 설정
        tt = timerTaskMaker();
        final Timer timer = new Timer();
        //활성화 버튼
        btn_manual = findViewById(R.id.btn_manual);
        btn_manual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tt.cancel();
                mBtnClickToggle = 0;
                Toast.makeText(getApplicationContext(), "수동모드로 전환합니다.", Toast.LENGTH_SHORT).show();
            }
        });
        btn_auto = findViewById(R.id.btn_auto);
        btn_auto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick (View v) {
                tt = timerTaskMaker();
                timer.schedule(tt, 0, 5000);
                Toast.makeText(getApplicationContext(), "자동모드로 전환합니다.", Toast.LENGTH_SHORT).show();
                mBtnClickToggle = 1;
            }
        });

        // 창문 열림
        this.btn_window_open.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(mBtnClickToggle == 1) {
                    Toast.makeText(getApplicationContext(), "자동모드가 활성화되어있어서 작동이 제한됩니다!", Toast.LENGTH_SHORT).show();
                } else {
                home.this.sendData("a");
                Toast.makeText(getApplicationContext(), "창문을 여는 중입니다.", Toast.LENGTH_SHORT).show();}
            }
        });

        // 창문 닫힘
        this.btn_window_close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(mBtnClickToggle == 1) {
                    Toast.makeText(getApplicationContext(), "자동모드가 활성화되어있어서 작동이 제한됩니다!", Toast.LENGTH_SHORT).show();
                } else {
                home.this.sendData("b");
                Toast.makeText(getApplicationContext(), "창문을 닫는 중입니다.", Toast.LENGTH_SHORT).show(); }
            }
        });

        // 블라인드 On
        this.btn_blind_on.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(mBtnClickToggle == 1) {
                    Toast.makeText(getApplicationContext(), "자동모드가 활성화되어있어서 작동이 제한됩니다!", Toast.LENGTH_SHORT).show();
                } else {
                home.this.sendData("d");
                Toast.makeText(getApplicationContext(), "블라인드가 작동중입니다.", Toast.LENGTH_SHORT).show(); }
            }
        });

        // 블라인드 Off
        this.btn_blind_off.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(mBtnClickToggle == 1) {
                    Toast.makeText(getApplicationContext(), "자동모드가 활성화되어있어서 작동이 제한됩니다!", Toast.LENGTH_SHORT).show();
                } else {
                home.this.sendData("e");
                Toast.makeText(getApplicationContext(), "블라인드 작동을 중지했습니다.", Toast.LENGTH_SHORT).show(); }
            }
        });
        checkBluetooth();

    }

    //일정 주기로 데이터 전송하는 TimerTask,
    //TimerTask 재생성 메소드
    public TimerTask timerTaskMaker() {
        TimerTask tempTask = new TimerTask() {
            @Override
            public void run() {
                home.this.sendData("c");
                counter++;
            }
        };
        return tempTask;
    }

    public BluetoothDevice getDeviceFromBondedList(String name) {
        for (BluetoothDevice device : this.mDevices) {
            if (name.equals(device.getName())) {
                return device;
            }
        }
        return null;
    }

    public void sendData(String msg) {
        try {
            this.mOutputStream.write((String.valueOf(msg) + this.mStrDelimiter).getBytes());
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "데이터 전송 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    public void connectToSelectedDevice(String selectedDeviceName) {
        this.mRemoteDevice = getDeviceFromBondedList(selectedDeviceName);
        try {
            this.mSocket = this.mRemoteDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"));
            this.mSocket.connect();
            this.mOutputStream = this.mSocket.getOutputStream();
            this.mInputStream = this.mSocket.getInputStream();
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "블루투스 연결 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    public void selectDevice() {
        this.mDevices = this.mBluetoothAdapter.getBondedDevices();
        this.mPairedDeviceCount = this.mDevices.size();
        if (this.mPairedDeviceCount == 0) {
            Toast.makeText(getApplicationContext(), "페어링된 장치가 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("블루투스 장치 선택");
        List<String> listItems = new ArrayList<>();
        for (BluetoothDevice device : this.mDevices) {
            listItems.add(device.getName());
        }
        listItems.add("취소");
        final CharSequence[] items = (CharSequence[]) listItems.toArray(new CharSequence[listItems.size()]);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                if (item == home.this.mPairedDeviceCount) {
                    Toast.makeText(home.this.getApplicationContext(), "연결할 장치를 선택하지 않았습니다.", Toast.LENGTH_SHORT).show();
                    home.this.finish();
                    return;
                }
                home.this.connectToSelectedDevice(items[item].toString());
            }
        });
        builder.setCancelable(false);
        builder.create().show();
    }

    public void checkBluetooth() {
        this.mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (this.mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "기기가 블루투스를 지원하지 않습니다.", Toast.LENGTH_SHORT).show();
            finish();
        } else if (!this.mBluetoothAdapter.isEnabled()) {
            Toast.makeText(getApplicationContext(), "현재 블루투스가 비활성 상태입니다.", Toast.LENGTH_SHORT).show();
            startActivityForResult(new Intent("android.bluetooth.adapter.action.REQUEST_ENABLE"), 10);
        } else {
            selectDevice();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case 10:
                if (resultCode != -1) {
                    if (resultCode == 0) {
                        Toast.makeText(getApplicationContext(), "블루투스를 사용할 수 없어 프로그램을 종료합니다.", Toast.LENGTH_SHORT).show();
                        finish();
                        break;
                    }
                } else {
                    selectDevice();
                    break;
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}