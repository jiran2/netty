package io.netty.example.bio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * @author jiran
 * @date 2019年11月26日
 */
public class NioClient implements Runnable {

    private volatile boolean stop;
    private SocketChannel socketChannel;
    private Selector selector;

    public NioClient() {
        try {
            //底层由Windows实现，整个计算机唯一，所有的Channel都注册在同一个Selector上面
            //public AbstractSelector openSelector() throws IOException {
            //        return new WindowsSelectorImpl(this);
            //    }
            selector = Selector.open();
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        new Thread(new NioClient()).start();
    }

    @Override
    public void run() {
        try {
            doConnect();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        while (!stop) {
            try {
                selector.select(1000);
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                SelectionKey selectionKey;
                while (iterator.hasNext()) {
                    selectionKey = iterator.next();
                    iterator.remove();
                    try {
                        handlerInput(selectionKey);
                    } catch (IOException e) {
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
                System.exit(1);
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

    private void doConnect() throws IOException {
        //当连接时会用当前的SocketChannel向Selector注册OP_ACCEPT事件
        boolean connect = socketChannel.connect(new InetSocketAddress("127.0.0.1", 8787));
        if (connect) {
            socketChannel.register(selector, SelectionKey.OP_READ);
            doWrite(socketChannel);
        } else {
            socketChannel.register(selector, SelectionKey.OP_CONNECT);
        }
    }

    private void handlerInput(SelectionKey selectionKey) throws IOException {
        if (selectionKey.isValid()) {
            SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
            if (selectionKey.isConnectable()) {
                if (socketChannel.finishConnect()) {
                    socketChannel.register(selector, SelectionKey.OP_READ);
                    doWrite(socketChannel);
                } else {
                    System.exit(1);
                }
            }
            if (selectionKey.isReadable()) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                int readByte = socketChannel.read(byteBuffer);
                if (readByte > 0) {
                    byteBuffer.flip();
                    byte[] bytes = new byte[byteBuffer.remaining()];
                    byteBuffer.get(bytes);
                    String responseBody = new String(bytes, "UTF-8");
                    System.out.println(responseBody);
                    this.stop = true;
                } else if (readByte < 0) {
                    selectionKey.cancel();
                    socketChannel.close();
                } else {

                }
            }
        }
    }

    private static void doWrite(SocketChannel socketChannel) throws IOException {
        String writeBody = "我是客户端，我连接服务端";
        byte[] writeBytes = writeBody.getBytes();
        ByteBuffer byteBuffer = ByteBuffer.allocate(writeBytes.length);
        byteBuffer.put(writeBytes);
        byteBuffer.flip();
        socketChannel.write(byteBuffer);
        if (!byteBuffer.hasRemaining()) {
            System.out.println("Send order 2 server succeed.");
        }
    }
}
