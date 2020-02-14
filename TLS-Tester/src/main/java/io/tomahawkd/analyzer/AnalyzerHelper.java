package io.tomahawkd.analyzer;

import io.tomahawkd.ArgParser;
import io.tomahawkd.censys.exception.CensysException;
import io.tomahawkd.common.TriFunction;
import io.tomahawkd.common.log.Logger;
import io.tomahawkd.data.TargetInfo;
import io.tomahawkd.netservice.CensysQueriesHelper;
import io.tomahawkd.testssl.data.SectionType;
import io.tomahawkd.testssl.data.Segment;
import io.tomahawkd.testssl.data.SegmentMap;
import io.tomahawkd.testssl.data.exception.FatalTagFoundException;
import io.tomahawkd.testssl.data.parser.CipherInfo;
import io.tomahawkd.testssl.data.parser.CipherSuite;
import io.tomahawkd.testssl.data.parser.OfferedResult;
import io.tomahawkd.tlsattacker.DrownTester;
import io.tomahawkd.tlsattacker.HeartBleedTester;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

public class AnalyzerHelper {

	private static final Logger logger = Logger.getLogger(AnalyzerHelper.class);

	private static final Cache cache = new Cache();

	static boolean isVulnerableTo(SegmentMap target, String tag) {
		Segment segment = target.get(tag);
		if (segment == null) {
			logger.fatal("No vulnerability tag");
			throw new IllegalArgumentException("No vulnerability tag");
		}
		if (segment.getTag().getType() != SectionType.VULNERABILITIES) {
			logger.fatal("Not a vulnerability tag");
			throw new IllegalArgumentException("Not a vulnerability tag");
		}

		boolean result = ((OfferedResult) segment.getResult()).isResult();
		try {
			if (tag.equals(VulnerabilityTags.DROWN)) result |=
					new DrownTester().test(target.getIp());
			else if (tag.equals(VulnerabilityTags.HEARTBLEED)) result |=
					new HeartBleedTester().test(target.getIp());
		} catch (Exception e) {
			logger.warn("Further " + tag + " test failed, return original result");
		}

		cache.put(tag, target.getIp(), result);
		return result;
	}

	static boolean isOtherWhoUseSameCertVulnerableTo(SegmentMap target, String vulnerability) {
		return isOtherWhoUseSameCertVulnerableTo(target, vulnerability,
				t -> isVulnerableTo(t, vulnerability));
	}

	static boolean isOtherWhoUseSameCertVulnerableTo(SegmentMap target,
	                                                 String vulnerability,
	                                                 Function<SegmentMap, Boolean> detect) {

		if (!ArgParser.INSTANCE.get().checkOtherSiteCert()) {
			logger.info("Testing on other server which use the same cert is ignored");
			return false;
		}

		if (vulnerability == null || vulnerability.isEmpty()) {
			logger.critical("Vulnerability tag not initialized");
			return false;
		}

		String hash = (String) target.get("cert_fingerprintSHA256").getResult();

		try {
			AtomicBoolean isVul = new AtomicBoolean(false);
			List<String> list = CensysQueriesHelper.searchIpWithHashSHA256(hash);
			list.forEach(ip -> isVul.set(cache.getOrDefault(vulnerability, ip, () -> {

				try {
					TargetInfo info = new TargetInfo(ip);
					info.collectInfo();
					if (!info.isHasSSL()) return false;

					boolean r = detect.apply(info.getTargetData());
					cache.put(vulnerability, ip, r);

					if (r) return true;

				} catch (FatalTagFoundException e) {
					logger.critical(e.getMessage());
					logger.critical("Skipping test host " + ip);
				} catch (Exception ex) {
					logger.fatal(ex.getMessage());
					throw new IllegalArgumentException(ex.getMessage());
				}

				return false;
			})));

			return isVul.get();
		} catch (CensysException e) {
			logger.critical(e.getMessage());
			logger.critical("Error on query censys, assuming false");
			return false;
		} catch (Exception e) {
			logger.fatal(e.getMessage());
			throw new IllegalArgumentException(e.getMessage());
		}
	}

	static CipherInfo getHighestSupportedCipherSuite(SegmentMap target) {
		List<Segment> list = target.getByType(SectionType.CIPHER_ORDER);
		CipherInfo max = null;
		for (Segment seg : list) {
			CipherInfo info = (CipherInfo) seg.getResult();
			if (max == null) max = info;
			else if (info.compare(max) > 0) max = info;
		}

		if (max == null) {
			logger.fatal("No cipher got from segment");
			throw new IllegalArgumentException("No cipher got from segment");
		}

		return max;
	}

	static boolean downgradeIsPossibleToAVersionOf(SegmentMap target,
	                                               CipherInfo.SSLVersion version,
	                                               TriFunction<CipherInfo.SSLVersion,
			                                               CipherSuite, SegmentMap,
			                                               Boolean> factor) {
		boolean result = false;
		List<Segment> list = target.getByType(SectionType.CIPHER_ORDER);

		outer:
		for (Segment seg : list) {
			CipherInfo info = (CipherInfo) seg.getResult();
			if (info.getSslVersion().getLevel() >= CipherInfo.SSLVersion.TLS1.getLevel()) {
				for (CipherSuite suite : info.getCipher().getList()) {
					if (factor.apply(info.getSslVersion(), suite, target)) {
						result = true;
						break outer;
					}
				}
			}
		}

		return result;
	}

	static class Cache {

		private Map<String, Map<String, Boolean>> cache = new HashMap<>();

		synchronized boolean containsKey(String vulnerability, String ip) {
			return cache.containsKey(vulnerability) && cache.get(vulnerability).containsKey(ip);
		}

		// Be aware that the default value will be in put in to the map if absent
		synchronized boolean getOrDefault(String vulnerability, String ip,
		                                  Supplier<Boolean> defaultValue) {
			return cache.computeIfAbsent(vulnerability, vul -> {
				logger.debug("Vulnerability tag" + vul + "not found");
				return new HashMap<>();
			}).computeIfAbsent(ip, s -> {
				logger.debug("Ip " + s + " not matched in cache");
				return defaultValue.get();
			});
		}

		synchronized boolean getOrDefault(String vulnerability, String ip,
		                                  boolean isVulnerable) {
			return getOrDefault(vulnerability, ip, () -> isVulnerable);
		}

		synchronized void put(String vulnerability, String ip, boolean isVulnerable) {
			cache.computeIfAbsent(vulnerability, vul -> {
				logger.debug("Vulnerability tag" + vul + "not found");
				return new HashMap<>();
			}).put(ip, isVulnerable);
		}
	}

}