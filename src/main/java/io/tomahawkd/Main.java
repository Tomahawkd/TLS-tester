package io.tomahawkd;

import de.rub.nds.tlsattacker.core.exceptions.TransportHandlerConnectException;
import io.tomahawkd.common.FileHelper;
import io.tomahawkd.common.log.Logger;
import io.tomahawkd.common.provider.FileTargetProvider;
import io.tomahawkd.common.provider.TargetProvider;
import io.tomahawkd.detect.Analyzer;
import io.tomahawkd.exception.NoSSLConnectionException;
import io.tomahawkd.testssl.ExecutionHelper;
import io.tomahawkd.testssl.data.TargetSegmentMap;
import io.tomahawkd.testssl.data.exception.FatalTagFoundException;
import io.tomahawkd.testssl.data.parser.CommonParser;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.Security;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Main {

	private static final Logger logger = Logger.getLogger(Main.class);

	static {
		Security.addProvider(new BouncyCastleProvider());
	}

	private static void parseArgs(String[] args) {

		for (String arg : args) {
			if (arg.startsWith("--config=")) {
				try {
					Config.INSTANCE.loadFromFile(arg.split("=")[1]);
				} catch (IOException e) {
					logger.warn("Config load failed, use default");
				}

			}
		}
	}

	public static void main(String[] args) {

		parseArgs(args);

		try {

			int threadCount = Config.INSTANCE.get().getThreadCount();
			ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadCount);

//			IpProvider provider = new DefaultIpProvider(ShodanExplorer.explore("has_ssl: true", 80));
			TargetProvider<String> provider = FileTargetProvider.getDefault("./temp/test2.txt");

			while (provider.hasMoreData()) {

				String target = provider.getNextTarget();

				try {
					executor.execute(() -> {
						try {

							logger.info("Start testing host " + target);
							TargetSegmentMap t = CommonParser.parseFile(ExecutionHelper.runTest(target));
							t.forEach((ip, seg) -> Analyzer.analyze(seg));

						} catch (FatalTagFoundException e) {
							logger.critical(e.getMessage());
							logger.critical("Skip test host " + target);
						} catch (TransportHandlerConnectException e) {
							if (e.getCause() instanceof SocketTimeoutException)
								logger.critical("Connecting to host " + target + " timed out, skipping.");
							else logger.critical(e.getMessage());
						} catch (NoSSLConnectionException e) {
							logger.critical(e.getMessage());
							logger.critical("Skip test host " + target);

							Config.INSTANCE.getRecorder().addNonSSLRecord(target);
						} catch (Exception e) {
							logger.critical("Unhandled Exception, skipping");
							logger.critical(e.getMessage());
						}
					});
				} catch (RejectedExecutionException e) {
					logger.critical("Analysis to IP " + target + " is rejected");
				}
			}

			executor.shutdown();
			executor.awaitTermination(Config.INSTANCE.get().getExecutionPoolTimeout(), TimeUnit.DAYS);
			Analyzer.postAnalyze();

			Config.INSTANCE.printConfig();
		} catch (Exception e) {
			logger.fatal("Unhandled Exception");
			e.printStackTrace();
		}
	}
}
