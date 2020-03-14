package io.tomahawkd.tlstester.testssl;

import de.rub.nds.tlsattacker.core.exceptions.TransportHandlerConnectException;
import io.tomahawkd.tlstester.ArgParser;
import io.tomahawkd.tlstester.common.FileHelper;
import io.tomahawkd.tlstester.common.log.Logger;
import io.tomahawkd.tlstester.exception.NoSSLConnectionException;
import io.tomahawkd.tlstester.tlsattacker.ConnectionTester;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

public class TestsslExecutor {

	private static final Logger logger = Logger.getLogger(TestsslExecutor.class);

	private static final String testssl =
			ArgParser.INSTANCE.get().getTestsslPath() + "/testssl.sh --jsonfile=";
	private static final String path = "./temp/testssl/";
	private static final String extension = ".txt";

	// Return file path
	public static String runTest(String host) throws Exception {

		logger.info("Running testssl on " + host);
		try {
			boolean isSSL = new ConnectionTester(host)
					.execute()
					.isServerHelloReceived();

			if (isSSL) {

				if (!FileHelper.isDirExist(path)) FileHelper.createDir(path);

				String file = path + host + extension;

				return FileHelper.Cache.getIfValidOrDefault(file, f -> {
					String fl = FileHelper.readFile(f);
					try {
						JSONArray arr = (JSONArray) new JSONObject("{\"list\": " + fl + "}").get("list");
						for (Object object : arr) {
							if (((String) ((JSONObject) object).get("severity")).trim().equals("FATAL") ||
									((String) ((JSONObject) object).get("finding")).trim().equals("Scan interrupted"))
								return false;
						}

						return arr.length() > 1 && FileHelper.Cache.isTempFileNotExpired(f);
					} catch (JSONException e) {
						return false;
					}

				}, f -> f, () -> {
					run(testssl + file + " " + host);
					return file;
				});

			} else {
				logger.warn("host " + host + " do not have ssl connection, skipping.");
				throw new NoSSLConnectionException();
			}
		} catch (TransportHandlerConnectException e) {
			if (e.getCause() instanceof SocketTimeoutException)
				logger.warn("Connecting to host " + host + " timed out, skipping.");
			else logger.warn(e.getMessage());
			throw new NoSSLConnectionException(e.getMessage());
		}
	}

	private static void run(String command)
			throws IOException, InterruptedException {

		logger.info("Running command " + command);

		Process pro = Runtime.getRuntime().exec(command);
		StringBuilder sb = new StringBuilder();

		new Thread(() -> {
			try {
				InputStream in = pro.getInputStream();
				OutputStream out = pro.getOutputStream();
				int charNum;
				while (pro.isAlive()) {
					if (in.available() > 0) {
						while ((charNum = in.read()) != -1) {
							sb.append((char) charNum);
						}
					} else {
						if (sb.length() > 50 && sb.substring(sb.length() - 50)
								.contains("Really proceed ? (\"yes\" to continue) -->")) {
							out.write("yes".getBytes());
						} else {
							Thread.sleep(1000);
						}
					}
				}

			} catch (IOException | InterruptedException e) {
				logger.critical("Error occurs while reading");
			}
		}).start();

		boolean status;
		try {
			status = pro.waitFor(30, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			logger.fatal(e.getMessage());
			throw new InterruptedException(e.getMessage());
		}

		if (!status) {
			pro.destroy();
			logger.critical("Time limit exceeded, force terminated");
			throw new IOException("Time limit exceeded, force terminated");
		} else {
			int exit = pro.exitValue();
			if (exit != 0) logger.warn("Exit code is " + exit);
		}

		logger.debug("\n" + sb.toString());
	}
}