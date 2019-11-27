package io.netty.example.bio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * @author jiran
 * @date 2019年11月26日
 */
public class NioServer implements Runnable {
    Selector selector = null;
    ServerSocketChannel serverSocketChannel = null;
    private volatile boolean stop;

    public NioServer() {
        try {
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.socket().bind(new InetSocketAddress(8787), 1024);
            //将ServerSocketChannel注册到Selector上，监听ACCEPT事件
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("NioServer start ...");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public void run() {
        while (!stop) {
            try {
                //循环查找有没有准备好的SelectionKey
                selector.select(1000);
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                SelectionKey selectionKey = null;
                while (iterator.hasNext()) {
                    selectionKey = iterator.next();
                    iterator.remove();
                    try {
                        handlerInput(selectionKey);
                    } catch (Exception e) {
                        if (selectionKey != null) {
                            selectionKey.cancel();
                            if (selectionKey.channel() != null) {
                                selectionKey.channel().close();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (selector != null) {
            try {
                selector.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        new Thread(new NioServer()).start();
    }

    private void handlerInput(SelectionKey selectionKey) throws IOException {
        if (selectionKey.isValid()) {
            //
            if (selectionKey.isAcceptable()) {
                ServerSocketChannel serverSocketChannel = (ServerSocketChannel) selectionKey.channel();
                SocketChannel accept = serverSocketChannel.accept();
                accept.configureBlocking(false);
                accept.register(selector, SelectionKey.OP_READ);
            }
            if (selectionKey.isReadable()) {
                SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                int readByte = socketChannel.read(byteBuffer);
                if (readByte > 0) {
                    byteBuffer.flip();
                    byte[] bytes = new byte[byteBuffer.remaining()];
                    byteBuffer.get(bytes);
                    String responseBody = new String(bytes, "UTF-8");
                    System.out.println(responseBody);
                    doWrite(socketChannel);
                }
            }
        }
    }

    private static void doWrite(SocketChannel channel) throws IOException {
        String response = "我是服务端，欢迎客户端连接";
        byte[] bytes = response.getBytes();
        ByteBuffer responseBuffer = ByteBuffer.allocate(bytes.length);
        responseBuffer.put(bytes);
        responseBuffer.flip();
        channel.write(responseBuffer);
    }
}
