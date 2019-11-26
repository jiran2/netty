package io.netty.example.bio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author jiran
 * @date 2019年11月26日
 */
public class BioServer {
    public static void main(String[] args) {
        PrintWriter pw = null;
        BufferedReader br = null;
        Socket accept = null;
        try {
            ServerSocket serverSocket = new ServerSocket(8787);
            accept = serverSocket.accept();
            while (true) {

                //输入流
                br = new BufferedReader(new InputStreamReader(accept.getInputStream()));
                String readLine = br.readLine();
                System.out.println("Server：" + readLine);

                //输出流
                pw = new PrintWriter(accept.getOutputStream(), true);
                pw.println("你好客户端，欢迎连接");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
