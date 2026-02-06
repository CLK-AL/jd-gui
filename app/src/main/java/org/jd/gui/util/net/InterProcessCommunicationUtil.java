/*
 * Copyright (c) 2008-2022 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.net;

import org.jd.gui.util.exception.ExceptionUtil;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

public class InterProcessCommunicationUtil {
	protected static final int PORT = 2015_6;

	public static void listen(final Consumer<String[]> consumer)
					throws
					Exception {
		final ServerSocket listener = new ServerSocket(PORT);

		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				while (true) {
					try (Socket socket = listener.accept();
					     ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
						// Security: Filter deserialization to only allow String[] to prevent RCE attacks
						ois.setObjectInputFilter(info -> {
							if (info.serialClass() == null) return ObjectInputFilter.Status.UNDECIDED;
							if (info.serialClass() == String[].class || info.serialClass() == String.class) {
								return ObjectInputFilter.Status.ALLOWED;
							}
							return ObjectInputFilter.Status.REJECTED;
						});
						// Receive args from another JD-GUI instance
						String[] args = (String[]) ois.readObject();
						consumer.accept(args);
					} catch (IOException | ClassNotFoundException e) {
						assert ExceptionUtil.printStackTrace(e);
					}
				}
			}
		};

		new Thread(runnable).start();
	}

	public static void send(String[] args) {
		try (Socket socket = new Socket(InetAddress.getLocalHost(),
		                                PORT);
		     ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {
			// Send args to the main JD-GUI instance
			oos.writeObject(args);
		} catch (IOException e) {
			assert ExceptionUtil.printStackTrace(e);
		}
	}
}
