package io.netty.example.jiran.bio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * @author jiran
 * @date 2019年11月26日
 */
public class BioClient {
    public static void main(String[] args) {

        Socket socket = null;
        BufferedReader br = null;
        PrintWriter pw = null;
        try {
            while (true) {
                socket = new Socket("127.0.0.1", 8787);
                while (true) {
                    //输出流
                    pw = new PrintWriter(socket.getOutputStream(), true);
                    pw.println("你好服务端，我连接到你了");

                    //输入流
                    br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String readLine = br.readLine();
                    System.out.println(readLine);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
