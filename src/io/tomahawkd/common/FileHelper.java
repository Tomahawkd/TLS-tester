package io.tomahawkd.common;

import io.tomahawkd.common.log.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class FileHelper {

	private static final Logger logger = Logger.getLogger(FileHelper.class);

	public static final String TEMP = "./temp/";

	static {
		try {
			if (!isDirExist(TEMP)) createDir(TEMP);
		} catch (IOException e) {
			logger.fatal("Temp directory cannot be created.");
		}
	}

	public static String readFile(String path) throws IOException {

		logger.info("Reading file " + path);

		Path p = Paths.get(path);

		File file = p.toFile();
		if (!file.exists()) {
			logger.fatal("File Not Found.");
			throw new FileNotFoundException("File Not Found.");
		}
		if (!file.canRead()) {
			logger.fatal("File Cannot be read.");
			throw new FileSystemException("File Cannot be read.");
		}

		try {
			return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
		} catch (IOException e) {
			logger.fatal(e.getMessage());
			throw new IOException(e.getMessage());
		}
	}

	public static boolean isDirExist(String path) {
		File file = new File(path);
		return file.exists() && file.isDirectory();
	}

	public static void writeFile(String path, String data, boolean overwrite) throws IOException {

		logger.info("Writing file " + path);

		Path p = Paths.get(path);
		File file = p.toFile();

		if (file.exists()) {
			if (overwrite) {
				logger.info("Overwriting file");
				if (!file.delete()) {
					logger.fatal("File cannot be deleted.");
					throw new FileSystemException("File cannot be deleted.");
				}
			} else {
				logger.info("Appending file");
				Files.write(p, data.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
			}
		}

		if (!file.exists()) {
			Files.write(p, data.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
		}

		logger.info("Writing finished");
	}

	public static void createDir(String path) throws IOException {

		logger.info("Creating directory " + path);
		File file = new File(path);
		if (file.exists()) {
			logger.fatal("Directory already exist");
			throw new FileAlreadyExistsException("Directory already exist");
		}
		if (!file.mkdir()) {
			logger.fatal("Directory cannot be created");
			throw new FileSystemException("Directory cannot be created");
		}
		logger.info("Directory created");
	}

	public static boolean isFileExist(String path) {
		File file = new File(path);
		return file.exists() && file.isFile();
	}

	public static void deleteFile(String path) throws IOException {

		logger.info("Deleting file " + path);
		if (!isFileExist(path)) {
			logger.warn("File not exist, returning");
			return;
		}
		File file = new File(path);
		if (!file.delete()) {
			logger.fatal("File cannot be deleted.");
			throw new FileSystemException("File cannot be deleted.");
		}
	}

	public static class Cache {

		private static final Logger logger = Logger.getLogger(Cache.class);

		private static int EXPIRED_TIME = 1000 * 60 * 60 * 24 * 7;

		public static String getContentIfValidOrDefault(String file, ThrowableSupplier<String> onInvalid)
				throws Exception {

			String content = getIfValidOrDefault(file, FileHelper::readFile, onInvalid);

			// this is for cache
			if (!isTempFileNotExpired(file)) {
				logger.info("Cache expired, writing new one");
				writeFile(file, content, true);
			}
			return content;
		}

		public static String getIfValidOrDefault(
				String file, ThrowableFunction<String, String> onValid,
				ThrowableSupplier<String> onInvalid) throws Exception {

			return getIfValidOrDefault(file, Cache::isTempFileNotExpired, onValid, onInvalid);
		}

		public static String getIfValidOrDefault(
				String file,
				ThrowableFunction<String, Boolean> isValid,
				ThrowableFunction<String, String> onValid,
				ThrowableSupplier<String> onInvalid) throws Exception {

			if (FileHelper.isFileExist(file)) {
				if (isValid.apply(file)) {
					logger.info("Cache " + file + " is valid, applying valid function");
					return onValid.apply(file);
				} else FileHelper.deleteFile(file);
			}

			logger.info("Cache " + file + " is not valid, applying invalid function");
			return onInvalid.get();
		}


		public static boolean isTempFileNotExpired(String path) {

			// file must be exist
			if (!isFileExist(path)) {
				logger.warn("File not exist");
				return false;
			}

			File file = new File(path);
			return EXPIRED_TIME < 0 || System.currentTimeMillis() - file.lastModified() < EXPIRED_TIME;
		}

		/**
		 * @param expiredDay Calculated by days. if expired day less than 0, it will be infinite.
		 */
		public static void setExpiredTime(int expiredDay) {
			if (expiredDay < 0) {
				logger.info("Expired day set to infinite");
				EXPIRED_TIME = -1;
			}
			logger.info("Expired day set to " + expiredDay);
			EXPIRED_TIME = expiredDay * 1000 * 60 * 60 * 24;
		}
	}
}
