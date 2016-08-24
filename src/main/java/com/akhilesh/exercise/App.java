package com.akhilesh.exercise;

import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class App extends Thread {

	private ServerSocketChannel serverSocketChannel;
	private Selector selector;
	private ByteBuffer buffer = ByteBuffer.allocate(128); 
	private static final int port = 8080;

	private final ByteBuffer banner = ByteBuffer.wrap("Chat with me!\n".getBytes());

	//ugliest possible room to user mapping store. It should be db or at least a persistent distributed cache ideally. 
	private Map<String, List<SelectionKey>> userToRoom = new ConcurrentHashMap<>();


	public App() throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.socket().bind(new InetSocketAddress(port));
		serverSocketChannel.configureBlocking(false);
		selector = Selector.open();

		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
	}

	@Override public void run() {
		try {
			Iterator<SelectionKey> iter;
			SelectionKey key;
			System.out.println("Chat Server Ready on port: " + this.port);
			while(serverSocketChannel.isOpen()) {
				selector.select();
				iter=this.selector.selectedKeys().iterator();
				while(iter.hasNext()) {
					key = iter.next();
					iter.remove();

					if(key.isAcceptable()) 
						accept(key);
					if(key.isReadable()) 
						read(key);
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}


	private void accept(SelectionKey key) throws Exception {
		SocketChannel socketChannel = ((ServerSocketChannel) key.channel()).accept();
		String attachement = getAddress(socketChannel);
		socketChannel.configureBlocking(false);
		socketChannel.register(selector, SelectionKey.OP_READ ,attachement  );
		socketChannel.write(banner);
		banner.rewind();
		System.out.println("User joined connection from: "+ attachement);
	}

	private void read(SelectionKey key) throws Exception {
		SocketChannel socketChannel = (SocketChannel) key.channel();
		StringBuilder sb = new StringBuilder();

		buffer.clear();
		int read = 0;
		while( (read = socketChannel.read(buffer)) > 0 ) {
			buffer.flip();
			byte[] bytes = new byte[buffer.limit()];
			buffer.get(bytes);
			sb.append(new String(bytes));
			buffer.clear();
		}
		String message;
		if(read<0) {
			message = key.attachment()+" Disconnected from chat.";
			socketChannel.close();
		}
		else {
			message = sb.toString();
			processMesage(key, message);

		}
	}

	private void processMesage(SelectionKey key, String message) throws IOException {
		if( message == null ||  message.equals("") || message.length() < 4)
			return; 
		String command = message.substring(0,4);
		if( command.equals("JOIN")) {
			String roomName = message.substring(5);
			roomName = roomName.substring(0,roomName.length()-2);
			System.out.println("Adding user to room:" + command +":" + roomName); 
			List<SelectionKey> selectionKeys = userToRoom.get(roomName );
			if( selectionKeys == null) {
				selectionKeys = new ArrayList<> ();
				selectionKeys.add(key);
				userToRoom.put(roomName,selectionKeys);
			} else {
				selectionKeys.add(key);
			}

		} else if( command.equals("SEND")) {

			String roomName = message.substring(5, message.indexOf(" ", 5));
			System.out.println("roomName in send:" + roomName);
			message = message.substring(4 + roomName.length() + 1);   //Extract message by ignoring the first 4 bytes + length of roomname + 1 for space
			String sender = getAddress(key);
			broadcast(sender, message, roomName );
		} else if ( command.equals("QUIT")) {
			SocketChannel socketChannel = (SocketChannel) key.channel();
			socketChannel.close();
		}

	}
	private void broadcast(String sender, String message, String room) {
		ByteBuffer messageBytes=ByteBuffer.wrap((sender +":"+ message).getBytes());
		List<SelectionKey> selectionKeys = userToRoom.get(room);
		if( selectionKeys == null) 
			return;
		List<Integer> invalidChannels = new ArrayList<>();
		for(int i =0; i < selectionKeys.size() ; i++) {
			SelectionKey key  = selectionKeys.get(i);
			SocketChannel socketChannel=(SocketChannel) key.channel();
			try {
				socketChannel.write(  messageBytes);
			}catch(Exception ex) {
				invalidChannels.add(i);
			}
			messageBytes.rewind();
		}
		for(Integer invalidIndex : invalidChannels) {
			SelectionKey selectionKey  = selectionKeys.get(invalidIndex);
			selectionKey.cancel();
			selectionKeys.remove(invalidIndex);
		}

	}

	public static void main(String[] args) throws IOException {
		App server = new App();
		(new Thread(server)).start();
	}

	private String getAddress( SelectionKey  selectionKey) {
		return getAddress( (SocketChannel) selectionKey.channel());
	}


	private String getAddress(SocketChannel socketChannel) {
		return   socketChannel.socket().getInetAddress().toString() + socketChannel.socket().getPort() ;
	}
}