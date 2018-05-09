package com.example.android.a2hop;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    Button listen, send, listDevices;
    ListView listView;
    TextView msg_box, status;
    EditText writeMsg;
    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] btArray;
    SendReceiveBlu sendReceiveBlu;

    Button attachment;
    private static final int SELECT_PICTURE = 1;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;

    int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final String APP_NAME = "BluTransfer";
    private static final UUID MY_UUID = UUID.fromString("9c2e399e-6c1c-4f22-9f53-a3c58944881c");



    //--------------------------------------------------------------------------------------------

    Button btnOnOff, btnDiscover, btnSend;
    ListView wifiListView;
    TextView connectionStatus;
    EditText wifiWriteMsg;

    WifiManager wifiManager;
    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;

    BroadcastReceiver mReceiver;
    IntentFilter mIntentFilter;

    List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    String[] deviceNameArray;
    WifiP2pDevice[] deviceArray;
    FileServerAsyncTask fileServerAsyncTask;
    FileClientAsyncTask fileClientAsyncTask;

    String wifiStatus = "";
    static final int MESSAGE_READ = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        attachment = (Button)findViewById(R.id.attach);
        msg_box=(TextView)findViewById(R.id.readMsg);
        btnSend = (Button) findViewById(R.id.sendButton);
        writeMsg = (EditText) findViewById(R.id.writeMsg);
        findViewByIds();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        }

        implementListeners();
//-------------------------------------------------------------------------------------------------
        initialWork();
        exqListener();

        forNameSake();

        registerReceiver(mReceiver, mIntentFilter);
        }
    public void forNameSake()
    {
        attachment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                i.setType("*/*");  //types
                startActivityForResult(Intent.createChooser(i, "Select Media"), SELECT_PICTURE);
            }
        });
    }
    private String getPath(Uri uri) {
        String[] projection = new String[]{MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String returnPath = cursor.getString(column_index);
        cursor.close();
        return returnPath;
    }
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        Log.d("file", "onActivityResult: "+resultCode);

        final int PERMISSION_REQUEST_CODE = 1;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {

            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_DENIED || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_DENIED) {


                String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE};

                requestPermissions(permissions, PERMISSION_REQUEST_CODE);

            }
        }
        Log.d("file", "onActivityResult: "+resultCode);
        if (resultCode == RESULT_OK) {
            Log.d("resultCode == RESULT_OK", "onActivityResult: ");
            if (requestCode == SELECT_PICTURE) {
                Log.d("requestCode == Select_picture", "onActivityResult: ");
                Uri selectedImageUri = data.getData();
                //selectClient sendImage = new selectClient(selectedImageUri);
                //sendImage.execute((Void) null);
                Log.d("tag", "onActivityResult: "+selectedImageUri);
                File f = new File(getPath(selectedImageUri));
                Log.d("file", "onActivityResult: "+f);
                if(wifiStatus=="Client")
                    fileClientAsyncTask.sendReceiveWifi.attach(f);
                else if(wifiStatus=="Host")
                    fileServerAsyncTask.sendReceiveWifi.attach(f);
            }
            }
        }

    private void findViewByIds() {
        listen = (Button) findViewById(R.id.listen);

        listView = (ListView) findViewById(R.id.listview);

        status = (TextView) findViewById(R.id.status);
        listDevices = (Button) findViewById(R.id.listDevices);
    }

    private void implementListeners() {
        listDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Set<BluetoothDevice> bt = bluetoothAdapter.getBondedDevices();
                String[] strings = new String[bt.size()];
                btArray = new BluetoothDevice[bt.size()];
                int i = 0;
                if (bt.size() > 0) {
                    for (BluetoothDevice device : bt) {
                        btArray[i] = device;
                        strings[i] = device.getName();
                        i++;
                    }
                    ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, strings);
                    listView.setAdapter(arrayAdapter);
                }
            }
        });
        listen.setOnClickListener(new View.OnClickListener()

        {
            @Override
            public void onClick(View view) {
                ServerClass serverClass = new ServerClass();
                serverClass.start();
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener()

        {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                ClientClass clientClass = new ClientClass(btArray[i]);
                clientClass.start();
                status.setText("Connecting");
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener()

        {
            @Override
            public void onClick(View view) {
                String string = String.valueOf(writeMsg.getText());
                Log.d("Debug", "onClick: " + string);
                sendReceiveBlu.write(string.getBytes());
                try {
                    if (wifiStatus == "Host") {
                        Log.d("Debug", "onClick: " + string);
                        fileServerAsyncTask.sendReceiveWifi.write(string.getBytes());
                    } else if (wifiStatus == "Client") {
                        Log.d("Debug", "onClick: " + string);
                        fileClientAsyncTask.sendReceiveWifi.write(string.getBytes());
                    }
                }
                catch (Exception e)
                {
                    Log.d("blusenderr", "errrrrr");
                    e.printStackTrace();
                }

            }
        });
    }
    Handler handlerBlu = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what){
                case STATE_LISTENING:
                    status.setText("Listening");
                    break;
                case STATE_CONNECTING:
                    status.setText("Connecting");
                    break;
                case  STATE_CONNECTED:
                    status.setText("Connected");
                    break;
                case STATE_CONNECTION_FAILED:
                    status.setText("Connection Failed");
                    break;
                case STATE_MESSAGE_RECEIVED:
                    byte[] readBuff = (byte[]) msg.obj;
                    String tempMsg = new String(readBuff,0,msg.arg1);
                    msg_box.setText(tempMsg);
                    try{
                        if (wifiStatus == "Host") {
                            Log.d("Debug", "onClick: " + msg);
                            fileServerAsyncTask.sendReceiveWifi.write(tempMsg.getBytes());
                        } else if (wifiStatus == "Client") {
                            Log.d("Debug", "onClick: " + msg);
                            fileClientAsyncTask.sendReceiveWifi.write(tempMsg.getBytes());
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                    break;
            }
            return true;
        }
    });
    private class ServerClass extends Thread{
        private BluetoothServerSocket serverSocket;

        public ServerClass(){
            try {
                serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(APP_NAME,MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run(){
            BluetoothSocket socket = null;
            while (socket == null){
                try {
                    Message message = Message.obtain();
                    message.what=STATE_CONNECTING;
                    handlerBlu.sendMessage(message);
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    Message message = Message.obtain();
                    message.what=STATE_CONNECTION_FAILED;
                    handlerBlu.sendMessage(message);
                }

                if(socket != null){
                    Message message = Message.obtain();
                    message.what=STATE_CONNECTED;
                    handlerBlu.sendMessage(message);
                    sendReceiveBlu = new SendReceiveBlu(socket);
                    sendReceiveBlu.start();
                    break;
                }
            }
        }
    }
    private class ClientClass extends Thread{
        private BluetoothDevice device;
        private BluetoothSocket socket;

        public  ClientClass(BluetoothDevice device1){
            device = device1;
            try {
                socket = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run(){
            try {
                socket.connect();
                Message message = Message.obtain();
                message.what = STATE_CONNECTED;
                handlerBlu.sendMessage(message);
                sendReceiveBlu =  new SendReceiveBlu(socket);
                sendReceiveBlu.start();

            } catch (IOException e) {
                e.printStackTrace();
                Message message = Message.obtain();
                message.what = STATE_CONNECTION_FAILED;
                handlerBlu.sendMessage(message);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("DESDfGG","onDestroy");
        try {

            sendReceiveBlu.bluetoothSocket.close();
            Log.d("DESDfGG","onDestroy successful");
        } catch (Exception e) {
            Log.d("vvnvjvjg","jgkg");
            e.printStackTrace();
        }
        try{
            unregisterReceiver(mReceiver);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private class SendReceiveBlu extends Thread{
        private  final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        byte[] buffer;

        public SendReceiveBlu(BluetoothSocket socket){
            bluetoothSocket = socket;
            InputStream tempIn = null;
            OutputStream tempOut = null;

            try {
                tempIn = bluetoothSocket.getInputStream();
                tempOut = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream = tempIn;
            outputStream =tempOut;

        }
        public void run(){
//            Thread thread = new Thread(new Runnable() {
//                @Override
//                public void run() {
            try{
                buffer = new byte[1024];
                int bytes;


                while (true) {
                    try {
                        Log.d("hfdj", "khkh");
                        bytes = inputStream.read(buffer);
                        handlerBlu.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }

            }
            catch(Exception e)
            {
                Log.d("lfldkhhroihwoihf", "run: ");
                e.printStackTrace();
            }
        }
//            });

//        }

        public  void write(byte[] bytes){

            try {
                Log.d("ta", "write: "+bytes);
                outputStream.write(bytes);
            } catch (Exception e) {
                Log.d("ta", "write:errr ");
                e.printStackTrace();
            }
        }
    }
    //---------------------------------------------------------------------------------------------
    private void exqListener() {
        btnOnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (wifiManager.isWifiEnabled()) {
                    wifiManager.setWifiEnabled(false);
                    btnOnOff.setText("Wifi On");
                } else {
                    wifiManager.setWifiEnabled(true);
                    btnOnOff.setText("Wifi Off");
                }
            }
        });
        btnDiscover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        connectionStatus.setText("Finding peers...");
                    }

                    @Override
                    public void onFailure(int reason) {
                        connectionStatus.setText("Finding peers failed...");
                    }
                });
            }
        });

        wifiListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final WifiP2pDevice device = deviceArray[position];
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;

                mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(getApplicationContext(), "Connecting to " + device.deviceName, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reason) {
                        Toast.makeText(getApplicationContext(), "Not Connected", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Log.d("Debug", "Im here nigga");
                String msg = writeMsg.getText().toString();
                if (wifiStatus == "Host") {
                    Log.d("Debug", "onClick: " + msg);
                    fileServerAsyncTask.sendReceiveWifi.write(msg.getBytes());
                } else if (wifiStatus == "Client") {
                    Log.d("Debug", "onClick: " + msg);
                    fileClientAsyncTask.sendReceiveWifi.write(msg.getBytes());
                }
                try{
                    sendReceiveBlu.write(msg.getBytes());
                }
                catch (Exception e)
                {
                    Log.d("sendwifierr", "onClick: errrrrrr");
                    e.printStackTrace();
                }
            }
        });
    }
    private void initialWork() {
        btnOnOff = (Button) findViewById(R.id.onOff);
        btnDiscover = (Button) findViewById(R.id.discover);

        wifiListView = (ListView) findViewById(R.id.peerListView);

        connectionStatus = (TextView) findViewById(R.id.connectionStatus);


        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);

        mReceiver = new WifiDirectBroadcastReceiver(mManager, mChannel, this);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        disconnect();

    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_READ:
                    byte[] readBuff = (byte[]) msg.obj;
                    String tempMsg = new String(readBuff, 0, msg.arg1);
                    msg_box.setText(tempMsg);
                    try{
                        sendReceiveBlu.write(tempMsg.getBytes());
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                    break;
            }
            return false;
        }
    });
    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            if (!peerList.getDeviceList().equals(peers)) {
                peers.clear();
                peers.addAll(peerList.getDeviceList());

                deviceNameArray = new String[peerList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[peerList.getDeviceList().size()];
                int i = 0;
                Log.d("debug", String.valueOf(peerList.getDeviceList().size()));
                for (WifiP2pDevice device : peerList.getDeviceList()) {
                    deviceNameArray[i] = device.deviceName;
                    deviceArray[i] = device;
                    i++;
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, deviceNameArray);
                wifiListView.setAdapter(adapter);
            }

            if (peers.size() == 0) {
                Toast.makeText(getApplicationContext(), "No Peers found", Toast.LENGTH_SHORT).show();

            }
        }
    };


    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            final InetAddress groupOwnerAddress = info.groupOwnerAddress;
            if (info.groupFormed && info.isGroupOwner) {
                connectionStatus.setText("Host");
                wifiStatus = "Host";
                Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
                fileServerAsyncTask = (FileServerAsyncTask) new FileServerAsyncTask(MainActivity.this, MainActivity.this.findViewById(R.id.writeMsg));
                fileServerAsyncTask.execute();

            } else if (info.groupFormed) {
                connectionStatus.setText("Client");
                wifiStatus = "Client";
                Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
                fileClientAsyncTask = (FileClientAsyncTask) new FileClientAsyncTask(groupOwnerAddress, 8080);
                fileClientAsyncTask.execute();


            }
        }
    };

    public void disconnect() {
        if (mManager != null && mChannel != null) {
            Log.d("Debug", "inside disconnect");
            mManager.requestGroupInfo(mChannel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (group != null && mManager != null && mChannel != null) {
                        mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {

                            @Override
                            public void onSuccess() {
                                Log.d("Debug", "removeGroup onSuccess -");
                            }

                            @Override
                            public void onFailure(int reason) {
                                Log.d("Debug", "removeGroup onFailure -" + reason);
                            }
                        });
                    }
                }
            });
        }
    }

    public class FileServerAsyncTask extends AsyncTask {

        private Context context;
        private TextView statusText;
        SendReceiveWifi sendReceiveWifi;
        MainActivity mActivity;
        ServerSocket serverSocket;
        Socket client;

        public FileServerAsyncTask(Context context, View statusText) {
            this.context = context;
            this.statusText = (TextView) statusText;
            Log.d("Server side", "value assigned");
        }

        @Override
        protected Object doInBackground(Object[] objects) {
            Log.d("Server side", "backgr");

            try {
                // Log.d("Debug","inside try");
                serverSocket = new ServerSocket(8080);
                Log.d("Server side", "Server: Socket opened");
                client = serverSocket.accept();
                sendReceiveWifi = new SendReceiveWifi(client,getApplicationContext());
                sendReceiveWifi.run();
                Log.d("Server side", "connection established(server)");
                //serverSocket.close();
            } catch (IOException e) {
                Log.d("Server side", "connection  not established");
                e.printStackTrace();
            }
            Log.d("Server side", "out");
            return null;
        }

        protected void close() {
            serverSocket.isClosed();
            serverSocket = null;
        }
    }

    public class FileClientAsyncTask extends AsyncTask {
        InetAddress destAddress;
        int destPort;
        String response;
        SendReceiveWifi sendReceiveWifi;

        public FileClientAsyncTask(InetAddress destAddress, int destPort) {
            this.destAddress = destAddress;
            this.destPort = destPort;
            Log.d("Client side", "values assigned");
        }

        @Override
        protected Object doInBackground(Object[] objects) {
            Socket socket = null;
            try {
                socket = new Socket(destAddress, destPort);
                socket.setSoTimeout(60000);
                Log.d("Client side", "connection Established");
                sendReceiveWifi = new SendReceiveWifi(socket,getApplicationContext());
                sendReceiveWifi.run();

            } catch (Exception e) {
                e.printStackTrace();
                response = "Exception " + e.toString();

            }
            return null;
        }

    }
    public class SendReceiveWifi extends Thread {
        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;
        private Context context;
        private BufferedInputStream bufferedInputStream;
        private BufferedOutputStream bufferedOutputStream;
        byte[] buffer = new byte[40000000];




        public SendReceiveWifi(Socket skt,Context context)
        {
            socket = skt;
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                this.context=context;
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }
        @Override
        public void run() {
            super.run();
            final File f = new File(Environment.getExternalStorageDirectory() + "/"
                    + context.getPackageName() + "/wifip2pshared-"
                    + "random.txt");
            File dirs = new File(f.getParent());
            if (!dirs.exists())
                dirs.mkdirs();
            try {
                f.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
            /*FileWriter filewriter = null;
            BufferedWriter out = null;
            try {
                filewriter = new FileWriter(f);
                out = new BufferedWriter(filewriter);
            } catch (IOException e) {
                e.printStackTrace();
            }

            final BufferedWriter finalOut = out;*/
            Thread thread = new Thread(new Runnable() {


                @Override
                public void run() {
                    try  {
                        //Your code goes here

                        int bytes;

                        while(socket!=null)
                        {
                            try{
                               // if(buffer!=null)
                                    bytes = inputStream.read(buffer);
                                //else
                                //    bytes=0;
                                Log.d("sendre", "run: "+bytes);
                                if(bytes > 0)
                                {
                                    //Toast.makeText(getApplicationContext(),bytes,Toast.LENGTH_LONG).show();
                                    Log.d("buffer",buffer.toString());
                                    if(buffer == null)
                                        Log.d("FUDGE","Object not created");
                                    //finalOut.write(buffer.toString());
                                    handler.obtainMessage(MESSAGE_READ,bytes,-1,buffer).sendToTarget();
                                }
                               /* else if(bytes>1024) {
                                    String res = filereceive();
                                    if (res != null) {
                                        Intent intent = new Intent();
                                        intent.setAction(android.content.Intent.ACTION_VIEW);
                                        intent.setDataAndType(Uri.parse("file://" + res), "image/*");
                                        context.startActivity(intent);
                                    }
                                }
                                buffer=null;
                                break;
                                */
                            }
                            catch (Exception e){
                                Log.d("sendre", "e=mc^2 ");
                                e.printStackTrace();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            thread.start();

        }
        /*public String filereceive()
        {
            int bytes;
            try{
                final File f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".jpg");

                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();
                Log.d("buffer", "filereceive: "+buffer.toString());
                inputStream = socket.getInputStream();
                while ((bytes = inputStream.read()) > 0)
                    outputStream.write(buffer, 0, bytes);
                outputStream = null;
                inputStream = null;
                return f.getAbsolutePath();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            return null;
        }*/

        public void write(final byte[] bytes)
        {
            Thread thread = new Thread(new Runnable() {

                @Override
                public void run() {
                    try  {
                        Log.d("Debug", "write: "+bytes.toString()+outputStream);

                        try {
                            // buffer=bytes;
                            outputStream.write(bytes);
                        }
                        catch(Exception e)
                        {
                            Log.d("Sendre","Excpt");
                            e.printStackTrace();
                        }
                        Log.d("Debug", "write: "+outputStream);
                        //Your code goes here
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            thread.start();

        }
        public void attach(final File f){
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try{
                        int len;
                        OutputStream outputStream = socket.getOutputStream();
                        Log.d("TAG", "run: "+buffer);
                        ContentResolver cr = context.getContentResolver();
                        inputStream = null;
                        inputStream = cr.openInputStream(Uri.parse("file://"+String.valueOf(f)));
                        while ((len = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, len);
                        }
                        Log.d("TAG", "run: "+buffer);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            thread.start();
        }
    }

}

