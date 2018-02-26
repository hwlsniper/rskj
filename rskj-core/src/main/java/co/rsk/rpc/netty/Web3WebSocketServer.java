/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.rpc.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;

import javax.annotation.Nullable;
import java.net.InetAddress;

public class Web3WebSocketServer {

    private static final int SOCKET_TIMEOUT_SECONDS = 15;

    private final InetAddress host;
    private final int port;
    private final JsonRpcWeb3ServerHandler web3ServerHandler;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private @Nullable ChannelFuture webSocketChannel;

    public Web3WebSocketServer(InetAddress host,
                               int port,
                               JsonRpcWeb3ServerHandler web3ServerHandler) {
        this.host = host;
        this.port = port;
        this.web3ServerHandler = web3ServerHandler;
        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();
    }

    public void start() throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new HttpServerCodec());
                    p.addLast(new HttpObjectAggregator(1024 * 1024 * 5));
                    p.addLast(new WebSocketServerProtocolHandler("/websocket"));
                    p.addLast(web3ServerHandler);
                    p.addLast(new Web3ResultWebSocketResponseHandler());

                    p.addLast(new IdleStateHandler(SOCKET_TIMEOUT_SECONDS, 0, 0));
                    p.addLast(new Web3IdleStateHandler());
                }
            });
        webSocketChannel = b.bind(host, port);
        webSocketChannel.sync();
    }

    public void stop() throws InterruptedException {
        if (webSocketChannel != null) {
            webSocketChannel.channel().close().sync();
        }
        this.bossGroup.shutdownGracefully();
        this.workerGroup.shutdownGracefully();
    }
}
