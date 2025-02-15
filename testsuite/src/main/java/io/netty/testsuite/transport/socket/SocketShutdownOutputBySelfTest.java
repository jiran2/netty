/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import org.junit.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

public class SocketShutdownOutputBySelfTest extends AbstractClientSocketTest {

    @Test(timeout = 30000)
    public void testShutdownOutput() throws Throwable {
        run();
    }

    public void testShutdownOutput(Bootstrap cb) throws Throwable {
        TestHandler h = new TestHandler();
        ServerSocket ss = new ServerSocket();
        Socket s = null;
        SocketChannel ch = null;
        try {
            ss.bind(newSocketAddress());
            ch = (SocketChannel) cb.handler(h).connect(ss.getLocalSocketAddress()).sync().channel();
            assertTrue(ch.isActive());
            assertFalse(ch.isOutputShutdown());

            s = ss.accept();
            ch.writeAndFlush(Unpooled.wrappedBuffer(new byte[] { 1 })).sync();
            assertEquals(1, s.getInputStream().read());

            assertTrue(h.ch.isOpen());
            assertTrue(h.ch.isActive());
            assertFalse(h.ch.isInputShutdown());
            assertFalse(h.ch.isOutputShutdown());

            // Make the connection half-closed and ensure read() returns -1.
            ch.shutdownOutput().sync();
            assertEquals(-1, s.getInputStream().read());

            assertTrue(h.ch.isOpen());
            assertTrue(h.ch.isActive());
            assertFalse(h.ch.isInputShutdown());
            assertTrue(h.ch.isOutputShutdown());

            // If half-closed, the peer should be able to write something.
            s.getOutputStream().write(new byte[] { 1 });
            assertEquals(1, (int) h.queue.take());
        } finally {
            if (s != null) {
                s.close();
            }
            if (ch != null) {
                ch.close();
            }
            ss.close();
        }
    }

    @Test(timeout = 30000)
    public void testShutdownOutputAfterClosed() throws Throwable {
        run();
    }

    public void testShutdownOutputAfterClosed(Bootstrap cb) throws Throwable {
        TestHandler h = new TestHandler();
        ServerSocket ss = new ServerSocket();
        Socket s = null;
        try {
            ss.bind(newSocketAddress());
            SocketChannel ch = (SocketChannel) cb.handler(h).connect(ss.getLocalSocketAddress()).sync().channel();
            assertTrue(ch.isActive());
            s = ss.accept();

            ch.close().syncUninterruptibly();
            try {
                ch.shutdownInput().syncUninterruptibly();
                fail();
            } catch (Throwable cause) {
                checkThrowable(cause);
            }
            try {
                ch.shutdownOutput().syncUninterruptibly();
                fail();
            } catch (Throwable cause) {
                checkThrowable(cause);
            }
        } finally {
            if (s != null) {
                s.close();
            }
            ss.close();
        }
    }

    @Test(timeout = 30000)
    public void testWriteAfterShutdownOutputNoWritabilityChange() throws Throwable {
        run();
    }

    public void testWriteAfterShutdownOutputNoWritabilityChange(Bootstrap cb) throws Throwable {
        final TestHandler h = new TestHandler();
        ServerSocket ss = new ServerSocket();
        Socket s = null;
        SocketChannel ch = null;
        try {
            ss.bind(newSocketAddress());
            cb.option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(2, 4));
            ch = (SocketChannel) cb.handler(h).connect(ss.getLocalSocketAddress()).sync().channel();
            assumeFalse(ch instanceof OioSocketChannel);
            assertTrue(ch.isActive());
            assertFalse(ch.isOutputShutdown());

            s = ss.accept();

            byte[] expectedBytes = new byte[]{ 1, 2, 3, 4, 5, 6 };
            ChannelFuture writeFuture = ch.write(Unpooled.wrappedBuffer(expectedBytes));
            h.assertWritability(false);
            ch.flush();
            writeFuture.sync();
            h.assertWritability(true);
            for (int i = 0; i < expectedBytes.length; ++i) {
                assertEquals(expectedBytes[i], s.getInputStream().read());
            }

            assertTrue(h.ch.isOpen());
            assertTrue(h.ch.isActive());
            assertFalse(h.ch.isInputShutdown());
            assertFalse(h.ch.isOutputShutdown());

            // Make the connection half-closed and ensure read() returns -1.
            ch.shutdownOutput().sync();
            assertEquals(-1, s.getInputStream().read());

            assertTrue(h.ch.isOpen());
            assertTrue(h.ch.isActive());
            assertFalse(h.ch.isInputShutdown());
            assertTrue(h.ch.isOutputShutdown());

            try {
                // If half-closed, the local endpoint shouldn't be able to write
                ch.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{ 2 })).sync();
                fail();
            } catch (Throwable cause) {
                checkThrowable(cause);
            }
            assertNull(h.writabilityQueue.poll());
        } finally {
            if (s != null) {
                s.close();
            }
            if (ch != null) {
                ch.close();
            }
            ss.close();
        }
    }

    @Test(timeout = 30000)
    public void testShutdownOutputSoLingerNoAssertError() throws Throwable {
        run();
    }

    public void testShutdownOutputSoLingerNoAssertError(Bootstrap cb) throws Throwable {
        testShutdownSoLingerNoAssertError0(cb, true);
    }

    @Test(timeout = 30000)
    public void testShutdownSoLingerNoAssertError() throws Throwable {
        run();
    }

    public void testShutdownSoLingerNoAssertError(Bootstrap cb) throws Throwable {
        testShutdownSoLingerNoAssertError0(cb, false);
    }

    private void testShutdownSoLingerNoAssertError0(Bootstrap cb, boolean output) throws Throwable {
        ServerSocket ss = new ServerSocket();
        Socket s = null;

        ChannelFuture cf = null;
        try {
            ss.bind(newSocketAddress());
            cf = cb.option(ChannelOption.SO_LINGER, 1).handler(new ChannelInboundHandlerAdapter())
                    .connect(ss.getLocalSocketAddress()).sync();
            s = ss.accept();

            cf.sync();

            if (output) {
                ((SocketChannel) cf.channel()).shutdownOutput().sync();
            } else {
                ((SocketChannel) cf.channel()).shutdown().sync();
            }
        } finally {
            if (s != null) {
                s.close();
            }
            if (cf != null) {
                cf.channel().close();
            }
            ss.close();
        }
    }
    private static void checkThrowable(Throwable cause) throws Throwable {
        // Depending on OIO / NIO both are ok
        if (!(cause instanceof ClosedChannelException) && !(cause instanceof SocketException)) {
            throw cause;
        }
    }

    private static final class TestHandler extends SimpleChannelInboundHandler<ByteBuf> {
        volatile SocketChannel ch;
        final BlockingQueue<Byte> queue = new LinkedBlockingQueue<Byte>();
        final BlockingDeque<Boolean> writabilityQueue = new LinkedBlockingDeque<Boolean>();

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            writabilityQueue.add(ctx.channel().isWritable());
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ch = (SocketChannel) ctx.channel();
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            queue.offer(msg.readByte());
        }

        private void drainWritabilityQueue() throws InterruptedException {
            while ((writabilityQueue.poll(100, TimeUnit.MILLISECONDS)) != null) {
                // Just drain the queue.
            }
        }

        void assertWritability(boolean isWritable) throws InterruptedException {
            try {
                Boolean writability = writabilityQueue.takeLast();
                assertEquals(isWritable, writability);
                drainWritabilityQueue();
            } catch (Throwable c) {
                c.printStackTrace();
            }
        }
    }
}
