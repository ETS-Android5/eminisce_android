/*
THIS CODE IS MEANT FOR READING FROM RFID SCANNER

We can communicate with the RFID scanner via a socket connection in local wifi
USB communication is possible but out of scope because we can't buy an industrial RFID scanner anyway
 */

package com.eminiscegroup.eminisce.rfid;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class RFIDListener {

    public interface RFIDCallback{
        void onRFIDReceive(String msg);
        void onRFIDConnected(String address);
    }

    ServerSocket serverSocket;
    private BufferedReader input;
    private RFIDCallback callback;

    public RFIDListener(RFIDCallback callback){ this.callback = callback; }

    public void startListening() {
        Thread socketServerThread = new Thread(new SocketServerThread());
        socketServerThread.start();
    }

    private class SocketServerThread extends Thread {

        static final int SocketServerPORT = 6942;

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(SocketServerPORT);
                // We want to accept many RFID scanners
                while (true) {
                    Socket socket = serverSocket.accept();
                    callback.onRFIDConnected(socket.getRemoteSocketAddress().toString());
                    input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    new Thread(new SocketInputListener(socket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    class SocketInputListener implements Runnable {
        Socket socket;

        public SocketInputListener(Socket socket) {this.socket = socket;}

        @Override
        public void run() {
            while (true) {
                try {
                    DataInputStream din = new DataInputStream(socket.getInputStream());
                    String message = din.readUTF();
                    if (message != null && !message.isEmpty()) {
                        callback.onRFIDReceive(message);
                    } else {
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

