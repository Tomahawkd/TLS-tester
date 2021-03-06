package io.tomahawkd.tlstester.data.testssl.parser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class CommonParser {

	private static final Logger logger = LogManager.getLogger(CommonParser.class);

	public static String returnSelf(String finding) {
		return finding;
	}

	public static CipherInfo parseCipherInfo(String finding) {
		String[] findings = finding.split(CipherInfo.splitSign);
		return new CipherInfo(findings[0].split("cipherorder_")[1], parseListWith(findings[1], " "));
	}

	public static CipherInfo parseCipherInfoForNoList(String finding) {
		int startIndex = finding.indexOf(" at ") + " at ".length();
		int endIndex = finding.indexOf(" ", startIndex);
		if (startIndex < 0 || endIndex < 0) {
			logger.warn("No cipher found from " + finding);
			return new CipherInfo("-1", new NameList(new ArrayList<>()));
		}
		String version = finding.substring(startIndex, endIndex).replace(".", "_");
		String cipher = finding.split(" ")[0].trim().split(CipherInfo.splitSign)[1].trim();
		List<String> arr = new ArrayList<>();
		arr.add(cipher);
		return new CipherInfo(version, new NameList(arr));
	}

	private static OfferedResult parseIsString(String finding, String target) {
		if (finding.equalsIgnoreCase(target)) {
			return new OfferedResult(true, null);
		} else if (finding.startsWith(target)) {
			String info = finding.split(target)[1].trim();
			return new OfferedResult(true, info);
		} else if (finding.equals("not " + target)) {
			return new OfferedResult(false, null);
		} else if (finding.startsWith("not " + target)) {
			String info = finding.split("not " + target)[1].trim();
			return new OfferedResult(false, info);
		} else if (finding.contains("not " + target)) {
			return new OfferedResult(false, finding);
		} else if (finding.contains(target)) {
			return new OfferedResult(true, finding);
		} else {
			return new OfferedResult();
		}
	}

	public static OfferedResult isSupported(String finding) {
		return parseIsString(finding, "supported");
	}

	public static OfferedResult isVulnerable(String finding) {
		OfferedResult firstResult = parseIsString(finding, "vulnerable");
		OfferedResult secondResult = parseIsString(finding, "VULNERABLE");
		String info = "";
		if (OfferedResult.INVALID.equals(firstResult.getInfo())) {
			info = secondResult.getInfo();
		} else if (OfferedResult.INVALID.equals(secondResult.getInfo())) {
			info = firstResult.getInfo();
		} else {
			// this situation should not show up
			info = finding;
		}
		return new OfferedResult(firstResult.isResult() || secondResult.isResult(), info);
	}

	public static OfferedResult isOffered(String finding) {
		return parseIsString(finding, "offered");
	}

	public static OfferedResult isTrue(String finding) {
		if (finding.equals("yes")) return new OfferedResult(true, null);
		else if (finding.startsWith("yes")) return new OfferedResult(true, finding.split("yes")[1]);
		else if (finding.equals("no")) return new OfferedResult(false, null);
		else if (finding.startsWith("no")) return new OfferedResult(false, finding.split("no")[1]);
		else return new OfferedResult(false, finding);
	}

	public static OfferedResult isOk(String finding) {
		return parseIsString(finding, "Ok");
	}

	public static boolean isPassed(String finding) {
		return finding.startsWith("passed");
	}

	public static int parseInt(String finding) {
		return CommonParser.parseInt(finding, 10);
	}

	static int parseInt(String finding, int radix) {
		try {
			return Integer.parseInt(finding, radix);
		} catch (NumberFormatException e) {
			logger.error("Exception during parsing int with value \"" + finding + "\"");
			return -1;
		}
	}

	public static X509Certificate parseCert(String finding) {
		X509Certificate certificate = null;
		CertificateFactory cf;
		try {
			if (finding != null && !finding.trim().isEmpty()) {

				finding = finding.replace("-----BEGIN CERTIFICATE-----", "")
						.replace("-----END CERTIFICATE-----", "").trim();

				finding = "-----BEGIN CERTIFICATE-----\n" + finding + "-----END CERTIFICATE-----";

				cf = CertificateFactory.getInstance("X509");
				certificate = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(finding.getBytes()));
			}
		} catch (CertificateException e) {
			logger.error("Exception during parsing cert with value \"" +
					finding + "\"" + "with message " + "\"" + e.getMessage() + "\"");
		}
		return certificate;
	}

	public static String checkSerial(String finding) {
		if (finding.equals("00")) return "";
		else return finding;
	}

	public static int parseTime(String finding) {
		if (finding.startsWith("expires"))
			return CommonParser.parseInt(finding.substring(finding.indexOf("(") + 1, finding.indexOf(")")));
		return CommonParser.parseInt(finding.split(" ")[0]);
	}

	public static CipherSuite parseCipher(String finding) {

		if (finding.contains(",")) finding = finding.split(",")[0].trim();
		if (finding.contains(" ")) finding = finding.split(" ")[0].trim();

		CipherSuite cipher = PreservedCipherList.getFromName(finding);
		if (cipher == null) logger.error("Cipher " + finding + " not found");
		return cipher;
	}

	public static CipherSuiteSet parseCipherSuite(String finding) {
		String[] data = finding.split(" ");
		List<String> sliced = new ArrayList<>();
		CipherSuiteSet cipherSuiteList = new CipherSuiteSet();
		for (String datum : data) {
			if (!datum.equals("")) sliced.add(datum);
		}
		try {
			String hex = sliced.get(0);
			String name = sliced.get(1);
			StringBuilder keyExchange = new StringBuilder();
			for (int i = 2; i < sliced.size() - 3; i++) {
				keyExchange.append(sliced.get(i)).append(" ");
			}
			String enc = sliced.get(sliced.size() - 3);
			String bits = sliced.get(sliced.size() - 2);
			String rfc = sliced.get(sliced.size() - 1);
			cipherSuiteList.add(new CipherSuite(hex, name, keyExchange.toString().trim(), enc, bits, rfc));
		} catch (IndexOutOfBoundsException e) {
			logger.error("Exception during parsing string with value \"" + finding + "\"");
		}

		return cipherSuiteList;
	}

	public static class NameList {

		private List<String> list;

		public NameList(List<String> list) {
			this.list = list;
		}

		public List<String> getList() {
			return list;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			list.forEach(e -> builder.append(e).append(" "));
			return builder.toString().trim();
		}
	}

	private static NameList parseListWith(String finding, String regex) {
		ArrayList<String> list = new ArrayList<>();
		String[] r = finding.split(regex);
		for (String s : r) {
			if (!s.trim().isEmpty()) list.add(s);
		}
		return new NameList(list);
	}

	public static NameList parseList(String finding) {
		return parseListWith(finding, "\n");
	}

	public static CountableResult parseCount(String finding) {
		String[] list = finding.split(" ", 4);
		if (list.length == 3) return new CountableResult(CommonParser.parseInt(list[0]), list[2], null);
		else return new CountableResult(CommonParser.parseInt(list[0]), list[1], list[2]);
	}

	public static List<String> parseHost(String hosts) {
		ArrayList<String> list = new ArrayList<>();
		String[] l = hosts.split("\n");
		for (String host : l) {
			if (host.startsWith("#")) continue;
			if (!host.trim().isEmpty()) list.add(host);
		}
		return list;
	}
}