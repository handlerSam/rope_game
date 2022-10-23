package com.example.cardgame;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {
    private static Handler handler;
    public static final int MAPSIZE = 15;
    TextView playerIPText;
    EditText playerIPInput;
    Button searchIP;
    Button connect;
    Button surrender;
    Button restart;
    TextView playerTurn;
    static String lastResult = "";
    //getId(context, "map" + x + "_" + y)
    private ServerSocket severSocket;
    private Socket socket;
    String sendMassage = "";
    boolean flag = true;
    ImageView[][] map = new ImageView[MAPSIZE][MAPSIZE];
    int[][] mapInfo = new int[MAPSIZE][MAPSIZE];
    int[][] mapConnect = new int[MAPSIZE][MAPSIZE];//当格为我方则为1，当格为对手则为2
    int lastX = -1;
    int lastY = -1;
    int stage = 0;//0未开始游戏 1等待对手走 2轮到我方走 3我方胜利 4对手胜利
    boolean me = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findID();
        resetMap();
        setClick();
        funThread();
    }

    @Override
    protected void onDestroy() {
        flag = false;
        super.onDestroy();
    }

    public void findID() {
        playerIPText = findViewById(R.id.playerIPText);
        playerIPInput = findViewById(R.id.playerIPInput);
        searchIP = findViewById(R.id.searchIP);
        connect = findViewById(R.id.connect);
        surrender = findViewById(R.id.surrender);
        restart = findViewById(R.id.restart);
        playerTurn = findViewById(R.id.playerTurn);
        for (int i = 0; i < MAPSIZE; i++) {
            for (int j = 0; j < MAPSIZE; j++) {
                map[i][j] = findViewById(getId(MainActivity.this, "map" + i + "_" + j));
            }
        }
    }

    public void setClick() {
        searchIP.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playerIPText.setText("玩家IP:" + getLocalIpAddress());
            }
        });
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMassage = "checkConnection";
                if (isIPAddress(playerIPInput.getText().toString())) {
                    getServerMessage();
                    Toast.makeText(MainActivity.this, "正在连接对方...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "输入的IP格式错误", Toast.LENGTH_SHORT).show();
                }
            }
        });
        for (int i = 0; i < MAPSIZE; i++) {
            for (int j = 0; j < MAPSIZE; j++) {
                final int x = i;
                final int y = j;
                map[i][j].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (stage == 2) {
                            if (me ? (y != 0 && y != (MAPSIZE - 1)) : (x != 0 && x != (MAPSIZE - 1))) {//确保是我方能下的格子
                                if (mapInfo[x][y] > 0) {
                                    if (lastX != x || lastY != y) {
                                        if (lastX != -1 && lastY != -1) {
                                            map[lastX][lastY].setImageResource(R.drawable.pic00000);
                                        }
                                        lastX = -1;
                                        lastY = -1;
                                    }
                                } else {//空格子
                                    if (lastX != x || lastY != y) {
                                        if (lastX != -1 && lastY != -1) {
                                            map[lastX][lastY].setImageResource(R.drawable.pic00000);
                                        }
                                        lastX = x;
                                        lastY = y;
                                        sendMassage = "touch_" + y + "_" + x;
                                        map[lastX][lastY].setImageResource(R.drawable.picchoose);
                                    } else {
                                        sendMassage = "connect_" + y + "_" + x;
                                        stage = 1;
                                        playerTurn.setText("轮到对手");
                                        updateMap();
                                        connectRope(x, y, me);
                                        if (testVictory(true)) {
                                            AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
                                            dialog.setTitle("游戏结束");//标题
                                            dialog.setMessage("我方胜利！");//正文
                                            dialog.setCancelable(true);
                                            dialog.show();
                                            stage = 3;
                                            playerTurn.setText("游戏结束，我方胜利");
                                        }
                                        lastX = -1;
                                        lastY = -1;
                                    }
                                }
                            }
                        }
                    }
                });
            }
        }
        surrender.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
                dialog.setTitle("确认");//标题
                dialog.setMessage("是否向对方投降？");//正文
                dialog.setCancelable(true);
                dialog.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //错误逻辑
                    }
                });
                dialog.setPositiveButton("是", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //正确逻辑
                        Toast.makeText(MainActivity.this, "已投降", Toast.LENGTH_SHORT).show();
                        sendMassage = "surrender";
                        stage = 4;
                        playerTurn.setText("游戏结束，敌方胜利");
                    }
                });

                dialog.show();
            }
        });
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
                dialog.setTitle("确认");//标题
                dialog.setMessage("是否向对方发送重新开始申请？");//正文
                dialog.setCancelable(true);
                dialog.setPositiveButton("是", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //正确逻辑
                        Toast.makeText(MainActivity.this, "申请已发送", Toast.LENGTH_SHORT).show();
                        sendMassage = "apply_restart";
                    }
                });
                dialog.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //错误逻辑
                    }
                });
                dialog.show();
            }
        });
    }

    private void funThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (severSocket == null) {
                        severSocket = new ServerSocket(2345);
                    }
                    while (flag) {
                        Socket socket = severSocket.accept();
                        String serverMessage = sendMassage;
                        OutputStream os = socket.getOutputStream();
                        os.write(serverMessage.getBytes("utf-8"));
                        os.flush();
                        socket.close();
                    }
                    Log.d("Sam", "服务器关闭");
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d("Sam", "服务器异常" + e.getMessage());
                }
            }
        }).start();
    }

    private String getLocalIpAddress() {
        String ipAddress = "";
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        ipAddress += inetAddress.getHostAddress() + ",";
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("Sam", ex.toString());
        }
        return ipAddress;
    }

    public int getId(Context context, String resName) {
        return context.getResources().getIdentifier(resName, "id", context.getPackageName());
    }

    public int getDrawable(String resName) {
        return MainActivity.this.getResources().getIdentifier(resName, "drawable", MainActivity.this.getPackageName());
    }

    public boolean isIPAddress(String string) {
        String[] splitAddress = string.split("\\.");
        if (splitAddress.length == 4) {
            for (int i = 0; i <= 3; i++) {
                int temp = Integer.parseInt(splitAddress[i]);
                if (temp <= 0 || temp >= 255) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private void getServerMessage() {
        handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                // TODO 自动生成的方法存根
                if (msg.what == 1) {
                    String result = (String) msg.obj;
                    if (!result.equals(lastResult)) {
                        lastResult = result;
                        Log.i("Sam", "receive:" + (String) msg.obj);
                        if (lastResult.startsWith("touch")) {
                            //String[] temp = lastResult.split("_");
                            //updateMap();
                            //map[Integer.parseInt(temp[1])][Integer.parseInt(temp[2])].setImageResource(R.drawable.picchoose);
                        } else if (lastResult.startsWith("connect")) {
                            String[] temp = lastResult.split("_");
                            updateMap();
                            connectRope(Integer.parseInt(temp[1]), Integer.parseInt(temp[2]), false);
                            stage = 2;
                            playerTurn.setText("轮到我方");
                            if (testVictory(false)) {
                                AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
                                dialog.setTitle("游戏结束");//标题
                                dialog.setMessage("对手胜利！");//正文
                                dialog.setCancelable(true);
                                dialog.show();
                                stage = 4;
                                playerTurn.setText("游戏结束，对手胜利");
                            }
                        } else {
                            switch (lastResult) {
                                case "checkConnection":
                                    sendMassage = "ConfirmConnection";
                                    break;
                                case "ConfirmConnection":
                                    Toast.makeText(MainActivity.this, "已连接", Toast.LENGTH_SHORT).show();
                                    sendMassage = "reconfirmConnection";
                                    break;
                                case "reconfirmConnection":
                                    Toast.makeText(MainActivity.this, "已连接", Toast.LENGTH_SHORT).show();
                                    sendMassage = "";
                                    break;
                                case "apply_restart":
                                    AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
                                    dialog.setTitle("对方的请求");//标题
                                    dialog.setMessage("对方请求重新开始游戏，是否接受？");//正文
                                    dialog.setCancelable(false);
                                    dialog.setPositiveButton("是", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            //正确逻辑
                                            if (flipACoin()) {
                                                sendMassage = "apply_restart_confirm_first";
                                                stage = 2;
                                                playerTurn.setText("轮到我方");
                                                Toast.makeText(MainActivity.this, "我方先手", Toast.LENGTH_SHORT).show();
                                            } else {
                                                sendMassage = "apply_restart_confirm_second";
                                                stage = 1;
                                                playerTurn.setText("轮到对手");
                                                Toast.makeText(MainActivity.this, "对方先手", Toast.LENGTH_SHORT).show();
                                            }
                                            resetMap();
                                        }
                                    });
                                    dialog.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            //错误逻辑
                                            sendMassage = "apply_restart_reject";
                                        }
                                    });
                                    dialog.show();
                                    break;
                                case "apply_restart_confirm_first":
                                    Toast.makeText(MainActivity.this, "对方同意重新开始游戏,对方先手", Toast.LENGTH_SHORT).show();
                                    resetMap();
                                    stage = 1;
                                    playerTurn.setText("轮到对手");
                                    break;
                                case "apply_restart_confirm_second":
                                    Toast.makeText(MainActivity.this, "对方同意重新开始游戏,我方先手", Toast.LENGTH_SHORT).show();
                                    resetMap();
                                    stage = 2;
                                    playerTurn.setText("轮到我方");
                                    break;
                                case "apply_restart_reject":
                                    Toast.makeText(MainActivity.this, "对方拒绝重新开始游戏", Toast.LENGTH_SHORT).show();
                                    break;
                                case "surrender":
                                    stage = 3;
                                    playerTurn.setText("游戏结束，我方胜利");
                                    Toast.makeText(MainActivity.this, "对手已投降", Toast.LENGTH_SHORT).show();
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                }
                return false;
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (flag) {
                        Thread.sleep(500);
                        socket = new Socket(playerIPInput.getText().toString(), 2345);///连接的是服务器地址，端口号相同
                        InputStream is = socket.getInputStream();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        int i = -1;
                        while ((i = is.read()) != -1) {
                            baos.write(i);
                        }
                        //Log.i("-------------客户端result-------------------", result);
                        is.close();
                        socket.close();
                        //messageTextView.setText(result);
                        Message msg = new Message();
                        msg.what = 1;
                        msg.obj = baos.toString();
                        handler.sendMessage(msg);
                    }

                } catch (UnknownHostException e) {
                    // TODO 自动生成的 catch 块
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO 自动生成的 catch 块
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    // TODO 自动生成的 catch 块
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void resetMap() {
        for (int i = 0; i < MAPSIZE; i++) {
            for (int j = 0; j < MAPSIZE; j++) {
                if ((i % 2 == 0) && (j % 2 == 1)) {
                    map[i][j].setImageResource(R.drawable.pic10000);
                    mapInfo[i][j] = 10000;
                    mapConnect[i][j] = 1;
                } else if ((i % 2 == 1) && (j % 2 == 0)) {
                    map[i][j].setImageResource(R.drawable.pic20000);
                    mapInfo[i][j] = 20000;
                    mapConnect[i][j] = 2;
                } else {
                    map[i][j].setImageResource(R.drawable.pic00000);
                    mapInfo[i][j] = 0;
                    mapConnect[i][j] = 0;
                }
            }
        }
        for (int i = 0; i < MAPSIZE; i++) {
            for (int j = 0; j < MAPSIZE; j++) {
                if ((i == 0 || i == (MAPSIZE - 1)) && (j > 0 && j < (MAPSIZE - 1)) && (mapInfo[i][j] == 0)) {
                    connectRope(i, j, true);
                } else if ((j == 0 || j == (MAPSIZE - 1)) && (i > 0 && i < (MAPSIZE - 1)) && (mapInfo[i][j] == 0)) {
                    connectRope(i, j, false);
                }
            }
        }
        lastX = -1;
        lastY = -1;
        updateMap();
    }

    public void updateMap() {
        for (int i = 0; i < MAPSIZE; i++) {
            for (int j = 0; j < MAPSIZE; j++) {
                map[i][j].setImageResource(getDrawable(getMapPic(mapInfo[i][j])));
            }
        }
        if (stage == 1) {
            playerTurn.setText("轮到敌方");
        } else if (stage == 2) {
            playerTurn.setText("轮到我方");
        } else if (stage == 3) {
            playerTurn.setText("游戏结束，我方胜利");
        } else if (stage == 4) {
            playerTurn.setText("游戏结束，敌方胜利");
        } else if (stage == 0) {
            playerTurn.setText("");
        }
    }

    public void connectRope(int x, int y, boolean isMe) {
        if (isMe) {
            if ((x % 2 == 0)) {
                mapInfo[x][y - 1] += 100;
                mapInfo[x][y + 1] += 1;
                mapInfo[x][y] = 101;
                map[x][y - 1].setImageResource(getDrawable(getFakePic2(mapInfo[x][y - 1], 2)));
                map[x][y + 1].setImageResource(getDrawable(getFakePic2(mapInfo[x][y + 1], 4)));
                map[x][y].setImageResource(getDrawable(getFakePic(mapInfo[x][y])));
                mapConnect[x][y] = 1;
            } else if ((x % 2 == 1)) {
                mapInfo[x - 1][y] += 10;
                mapInfo[x + 1][y] += 1000;
                mapInfo[x][y] = 1010;
                map[x - 1][y].setImageResource(getDrawable(getFakePic2(mapInfo[x - 1][y], 3)));
                map[x + 1][y].setImageResource(getDrawable(getFakePic2(mapInfo[x + 1][y], 1)));
                map[x][y].setImageResource(getDrawable(getFakePic(mapInfo[x][y])));
                mapConnect[x][y] = 1;
            }
        } else {
            if ((y % 2 == 0)) {
                mapInfo[x - 1][y] += 20;
                mapInfo[x + 1][y] += 2000;
                mapInfo[x][y] = 2020;
                map[x - 1][y].setImageResource(getDrawable(getFakePic2(mapInfo[x - 1][y], 3)));
                map[x + 1][y].setImageResource(getDrawable(getFakePic2(mapInfo[x + 1][y], 1)));
                map[x][y].setImageResource(getDrawable(getFakePic(mapInfo[x][y])));
                mapConnect[x][y] = 2;
            } else if ((y % 2 == 1)) {
                mapInfo[x][y - 1] += 200;
                mapInfo[x][y + 1] += 2;
                mapInfo[x][y] = 202;
                map[x][y - 1].setImageResource(getDrawable(getFakePic2(mapInfo[x][y - 1], 2)));
                map[x][y + 1].setImageResource(getDrawable(getFakePic2(mapInfo[x][y + 1], 4)));
                map[x][y].setImageResource(getDrawable(getFakePic(mapInfo[x][y])));
                mapConnect[x][y] = 2;
            }
        }
    }

    public String getMapPic(int number) {
        String res = String.valueOf(number);
        while (res.length() < 5) {
            res = "0" + res;
        }
        Log.d("Sam", "pic" + res);
        return "pic" + res;
    }

    public String getFakePic(int number) {
        String res = String.valueOf(number);
        while (res.length() < 5) {
            res = "0" + res;
        }
        String new_res = "" + res.charAt(0);
        for (int i = 1; i < 5; i++) {
            if (res.charAt(i) != '0') {
                new_res += "3";
            } else {
                new_res += res.charAt(i);
            }
        }
        Log.d("Sam", "fake" + new_res);
        return "pic" + new_res;
    }

    public String getFakePic2(int number, int position) {
        String res = String.valueOf(number);
        while (res.length() < 5) {
            res = "0" + res;
        }
        String new_res = "" + res.charAt(0);
        for (int i = 1; i < 5; i++) {
            if (i == position && res.charAt(i) != '0') {
                new_res += "3";
            } else {
                new_res += res.charAt(i);
            }
        }
        Log.d("Sam", "fake" + new_res);
        return "pic" + new_res;
    }

    public boolean testVictory(boolean isMe) {
        final int mark = isMe ? 1 : 2;
        boolean[][] tempMap = new boolean[MAPSIZE][MAPSIZE];
        for (int i = 0; i < MAPSIZE; i++) {
            for (int j = 0; j < MAPSIZE; j++) {
                tempMap[i][j] = false;
            }
        }
        ArrayList<Integer> listx = new ArrayList<>();
        ArrayList<Integer> listy = new ArrayList<>();
        if (isMe) {
            listx.add(0);
            listy.add(1);
        } else {
            listy.add(0);
            listx.add(1);
        }
        while (!listx.isEmpty()) {
            int x = listx.get(0);
            int y = listy.get(0);
            tempMap[x][y] = true;
            listx.remove(0);
            listy.remove(0);
            if (x >= 1) {
                if ((mapConnect[x - 1][y] == mark) && (!tempMap[x - 1][y])) {
                    tempMap[x - 1][y] = true;
                    listx.add(x - 1);
                    listy.add(y);
                }
            }
            if (x < (MAPSIZE - 1)) {
                if ((mapConnect[x + 1][y] == mark) && (!tempMap[x + 1][y])) {
                    tempMap[x + 1][y] = true;
                    listx.add(x + 1);
                    listy.add(y);
                }
            }
            if (y >= 1) {
                if ((mapConnect[x][y - 1] == mark) && (!tempMap[x][y - 1])) {
                    tempMap[x][y - 1] = true;
                    listx.add(x);
                    listy.add(y - 1);
                }
            }
            if (y < (MAPSIZE - 1)) {
                if ((mapConnect[x][y + 1] == mark) && (!tempMap[x][y + 1])) {
                    tempMap[x][y + 1] = true;
                    listx.add(x);
                    listy.add(y + 1);
                }
            }
        }
        return isMe ? (tempMap[MAPSIZE - 1][MAPSIZE - 2]) : (tempMap[MAPSIZE - 2][MAPSIZE - 1]);
    }

    public boolean flipACoin() {
        return Math.random() >= 0.5d;
    }
}