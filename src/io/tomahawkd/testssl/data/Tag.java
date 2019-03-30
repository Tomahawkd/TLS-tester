package io.tomahawkd.testssl.data;

import io.tomahawkd.testssl.FindingParser;
import io.tomahawkd.testssl.data.parser.CipherInfo;
import io.tomahawkd.testssl.data.parser.CommonParser;

import java.util.HashMap;
import java.util.Map;

public class Tag<Result> {

	private String id;
	private String description;
	private SectionType type;
	private FindingParser<Result> parser;

	private Tag(String id, String description, SectionType type, FindingParser<Result> parser) {
		this.id = id;
		this.description = description;
		this.type = type;
		this.parser = parser;
	}

	public String getId() {
		return id;
	}

	public String getDescription() {
		return toString();
	}

	public SectionType getType() {
		return type;
	}

	public Result parseData(String finding) {
		return parser.parse(finding);
	}

	public Result parseCipher(String id, String finding) {
		return parser.parse(id + CipherInfo.splitSign + finding);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof Tag &&
				((Tag) obj).id.equals(this.id) &&
				((Tag) obj).description.equals(this.description) &&
				((Tag) obj).type.equals(this.type);
	}

	private static final Map<String, Tag> tagMap = new HashMap<>();

	public static Tag<?> getTag(String id) {
		return tagMap.getOrDefault(id, constructTag(id));
	}

	private static Tag<?> constructTag(String id) {
		if (id.startsWith("cipherorder_")) {
			var template = tagMap.get("cipherorder_");
			var tag = new Tag<>(id, template.description, template.type, CommonParser::parseCipherInfo);
			tagMap.put(id, tag);
			return tag;
		} else if (id.startsWith("cipher_x")) return tagMap.get("cipher_x");
		var template = tagMap.get(null);
		return new Tag<>(id, template.description, template.getType(), CommonParser::returnSelf);
	}

	@Override
	public String toString() {
		return description;
	}

	static {
		// Unknown
		tagMap.put(null, new Tag<>("null", "Unknown", SectionType.UNKNOWN, CommonParser::returnSelf));

		// Misc
		tagMap.put("scanTime", new Tag<>("scanTime", "Scanning time", SectionType.MISC, CommonParser::parseInt));

		// Service
		tagMap.put("service", new Tag<>("service", "Service detected", SectionType.COMMON, CommonParser::returnSelf));

		// Secure Layer Protocols
		tagMap.put("SSLv2", new Tag<>("SSLv2", "SSLv2 is offered", SectionType.PROTOCOLS, CommonParser::isOffered));
		tagMap.put("SSLv3", new Tag<>("SSLv3", "SSLv3 is offered", SectionType.PROTOCOLS, CommonParser::isOffered));
		tagMap.put("TLS1", new Tag<>("TLS1", "TLS1 is offered", SectionType.PROTOCOLS, CommonParser::isOffered));
		tagMap.put("TLS1_1", new Tag<>("TLS1_1", "TLS1.1 is offered", SectionType.PROTOCOLS, CommonParser::isOffered));
		tagMap.put("TLS1_2", new Tag<>("TLS1_2", "TLS1.2 is offered", SectionType.PROTOCOLS, CommonParser::isOffered));
		tagMap.put("TLS1_3", new Tag<>("TLS1_3", "TLS1.3 is offered", SectionType.PROTOCOLS, CommonParser::isOffered));
		tagMap.put("NPN", new Tag<>("NPN", "NPN/SPDY", SectionType.PROTOCOLS, CommonParser::isOffered));
		tagMap.put("ALPN_HTTP2", new Tag<>("ALPN_HTTP2", "ALPN_HTTP2", SectionType.PROTOCOLS, CommonParser::returnSelf));
		tagMap.put("ALPN", new Tag<>("ALPN", "ALPN/HTTP2", SectionType.PROTOCOLS, CommonParser::returnSelf));

		// Cipher
		tagMap.put("cipherlist_NULL", new Tag<>("cipherlist_NULL", "Null ciphers(no encryption) is offered", SectionType.CIPHER, CommonParser::isOffered));
		tagMap.put("cipherlist_aNULL", new Tag<>("cipherlist_aNULL", "Anonymous NULL Ciphers (no authentication) is offered", SectionType.CIPHER, CommonParser::isOffered));
		tagMap.put("cipherlist_EXPORT", new Tag<>("cipherlist_EXPORT", "Export ciphers (w/o ADH+NULL) is offered", SectionType.CIPHER, CommonParser::isOffered));
		tagMap.put("cipherlist_LOW", new Tag<>("cipherlist_LOW", "LOW: 64 Bit + DES, RC[2,4] (w/o export) is offered", SectionType.CIPHER, CommonParser::isOffered));
		tagMap.put("cipherlist_3DES_IDEA", new Tag<>("cipherlist_3DES_IDEA", "Triple DES Ciphers / IDEA is offered", SectionType.CIPHER, CommonParser::isOffered));
		tagMap.put("cipherlist_AVERAGE", new Tag<>("cipherlist_AVERAGE", "Average: SEED + 128+256 Bit CBC ciphers is offered", SectionType.CIPHER, CommonParser::isOffered));
		tagMap.put("cipherlist_STRONG", new Tag<>("cipherlist_STRONG", "Strong encryption (AEAD ciphers) is offered", SectionType.CIPHER, CommonParser::isOffered));

		// Perfect Forward Secrecy
		tagMap.put("PFS", new Tag<>("PFS", "PFS is offered", SectionType.PFS, CommonParser::isOffered));
		tagMap.put("PFS_ciphers", new Tag<>("PFS_ciphers", "PFS ciphers", SectionType.PFS, CommonParser::returnSelf));
		tagMap.put("PFS_ECDHE_curves", new Tag<>("PFS_ECDHE_curves", "Elliptic curves offered", SectionType.PFS, CommonParser::returnSelf));

		// Server Preference
		tagMap.put("cipher_order", new Tag<>("cipher_order", "Has server cipher order", SectionType.PREFERENCES, CommonParser::returnSelf));
		tagMap.put("protocol_negotiated", new Tag<>("protocol_negotiated", "Negotiated protocol", SectionType.PREFERENCES, CommonParser::returnSelf));
		tagMap.put("cipher_negotiated", new Tag<>("cipher_negotiated", "Negotiated cipher", SectionType.PREFERENCES, CommonParser::returnSelf));
		tagMap.put("cipherorder_", new Tag<>("cipherorder_", "cipher order", SectionType.CIPHER_ORDER, CommonParser::parseCipherInfo));

		// Server Defaults
		tagMap.put("TLS_extensions", new Tag<>("TLS_extensions", "TLS extensions (standard)", SectionType.SERVER_INFO, CommonParser::returnSelf));
		tagMap.put("TLS_session_ticket", new Tag<>("TLS_session_ticket", "Session Ticket RFC 5077 hint", SectionType.SERVER_INFO, CommonParser::returnSelf));
		tagMap.put("SSL_sessionID_support", new Tag<>("SSL_sessionID_support", "SSL Session ID support", SectionType.SERVER_INFO, CommonParser::isTrue));
		tagMap.put("sessionresumption_ticket", new Tag<>("sessionresumption_ticket", "Session Resumption of Ticket", SectionType.SERVER_INFO, CommonParser::isSupported));
		tagMap.put("sessionresumption_ID", new Tag<>("sessionresumption_ID", "Session Resumption of ID", SectionType.SERVER_INFO, CommonParser::isSupported));
		tagMap.put("TLS_timestamp", new Tag<>("TLS_timestamp", "TLS clock skew", SectionType.SERVER_INFO, CommonParser::returnSelf));
		tagMap.put("cert_numbers", new Tag<>("cert_numbers", "Cert numbers", SectionType.CERT, CommonParser::parseInt));
		tagMap.put("cert_signatureAlgorithm", new Tag<>("cert_signatureAlgorithm", "Signature Algorithm", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("cert_keySize", new Tag<>("cert_keySize", "Server key size", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("cert_keyUsage", new Tag<>("cert_keyUsage", "Server key usage", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("cert_extKeyUsage", new Tag<>("cert_extKeyUsage", "Server extended key usage", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("cert_serialNumber", new Tag<>("cert_serialNumber", "Serial number", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("cert_fingerprintSHA1", new Tag<>("cert_fingerprintSHA1", "Fingerprint SHA1", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("cert_fingerprintSHA256", new Tag<>("cert_fingerprintSHA256", "Fingerprint SHA256", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("cert", new Tag<>("cert", "Cert", SectionType.CERT, CommonParser::parseCert));
		tagMap.put("cert_commonName", new Tag<>("cert_commonName", "Common Name", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("cert_commonName_wo_SNI", new Tag<>("cert_commonName_wo_SNI", "cert_commonName_wo_SNI", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("cert_subjectAltName", new Tag<>("cert_subjectAltName", "Subject Alt Name", SectionType.CERT, CommonParser::parseList));
		tagMap.put("cert_caIssuers", new Tag<>("cert_caIssuers", "CA Issuers", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("cert_trust", new Tag<>("cert_trust", "Trust (hostname)", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("cert_chain_of_trust", new Tag<>("cert_chain_of_trust", "Chain of trust", SectionType.CERT, CommonParser::isPassed));
		tagMap.put("cert_certificatePolicies_EV", new Tag<>("cert_certificatePolicies_EV", "EV cert (experimental)", SectionType.CERT, CommonParser::isTrue));
		tagMap.put("cert_eTLS", new Tag<>("cert_eTLS", "eTLS (visibility info)", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("cert_expiration_status", new Tag<>("cert_expiration_status", "Certificate Validity (UTC) days", SectionType.CERT, CommonParser::parseTime));
		tagMap.put("cert_notBefore", new Tag<>("cert_notBefore", "Not valid before", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("cert_notAfter", new Tag<>("cert_notAfter", "Not valid after", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("certs_countServer", new Tag<>("certs_countServer", "Number of certificates provided", SectionType.CERT, CommonParser::parseInt));
		tagMap.put("certs_list_ordering_problem", new Tag<>("certs_list_ordering_problem", "List ordering problem", SectionType.CERT, CommonParser::isTrue));
		tagMap.put("cert_crlDistributionPoints", new Tag<>("cert_crlDistributionPoints", "CRL Distribution Points", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("cert_ocspURL", new Tag<>("cert_ocspURL", "OCSP URI", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("OCSP_stapling", new Tag<>("OCSP_stapling", "OCSP stapling", SectionType.CERT, CommonParser::isOffered));
		tagMap.put("cert_mustStapleExtension", new Tag<>("cert_mustStapleExtension", "OCSP must staple extension", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("DNS_CAArecord", new Tag<>("DNS_CAArecord", "DNS CAA RR (experimental)", SectionType.CERT, CommonParser::returnSelf));
		tagMap.put("certificate_transparency", new Tag<>("certificate_transparency", "Certificate Transparency", SectionType.CERT, CommonParser::isTrue));

		// HTTP Header
		tagMap.put("HTTP_status_code", new Tag<>("HTTP_status_code", "HTTP status code", SectionType.HTTP_INFO, CommonParser::returnSelf));
		tagMap.put("HTTP_clock_skew", new Tag<>("HTTP_clock_skew", "HTTP clock skew diff from local time", SectionType.HTTP_INFO, CommonParser::parseTime));
		tagMap.put("HSTS", new Tag<>("HSTS", "Strict Transport Security", SectionType.HTTP_INFO, CommonParser::isOffered));
		tagMap.put("HPKP",new Tag<>("HPKP","Public Key Pinning", SectionType.HTTP_INFO, CommonParser::returnSelf));
		tagMap.put("banner_server", new Tag<>("banner_server", "Server banner", SectionType.HTTP_INFO, CommonParser::returnSelf));
		tagMap.put("banner_application", new Tag<>("banner_application", "Application banner", SectionType.HTTP_INFO, CommonParser::returnSelf));
		tagMap.put("cookie_count", new Tag<>("cookie_count", "Cookie count", SectionType.HTTP_INFO, CommonParser::parseCount));
		tagMap.put("security_headers", new Tag<>("security_headers", "security_headers", SectionType.HTTP_INFO, CommonParser::returnSelf));
		tagMap.put("cookie_secure", new Tag<>("cookie_secure", "Cookie secure", SectionType.HTTP_INFO, CommonParser::parsePercentage));
		tagMap.put("cookie_httponly", new Tag<>("cookie_httponly", "Cookie httponly", SectionType.HTTP_INFO, CommonParser::parsePercentage));
		tagMap.put("X-UA-Compatible", new Tag<>("X-UA-Compatible", "Security headers", SectionType.HTTP_INFO, CommonParser::returnSelf));
		tagMap.put("banner_reverseproxy", new Tag<>("banner_reverseproxy", "Reverse Proxy banner", SectionType.HTTP_INFO, CommonParser::returnSelf));

		// Vulnerabilities
		tagMap.put("heartbleed", new Tag<>("heartbleed", "Heartbleed (CVE-2014-0160)", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("CCS", new Tag<>("CCS", "CCS (CVE-2014-0224)", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("ticketbleed", new Tag<>("ticketbleed", "Ticketbleed (CVE-2016-9244)", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("ROBOT", new Tag<>("ROBOT", "ROBOT", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("secure_renego", new Tag<>("secure_renego", "Secure Renegotiation (CVE-2009-3555)", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("secure_client_renego", new Tag<>("secure_client_renego", "Secure Client-Initiated Renegotiation", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("CRIME_TLS", new Tag<>("CRIME_TLS", "CRIME, TLS (CVE-2012-4929)", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("BREACH", new Tag<>("BREACH", "BREACH (CVE-2013-3587)", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("POODLE_SSL", new Tag<>("POODLE_SSL", "POODLE, SSL (CVE-2014-3566)", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("fallback_SCSV", new Tag<>("fallback_SCSV", "TLS_FALLBACK_SCSV (RFC 7507)", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("SWEET32", new Tag<>("SWEET32", "SWEET32 (CVE-2016-2183, CVE-2016-6329)", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("FREAK", new Tag<>("FREAK", "FREAK (CVE-2015-0204)", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("DROWN", new Tag<>("DROWN", "DROWN (CVE-2016-0800, CVE-2016-0703)", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("LOGJAM", new Tag<>("LOGJAM", "LOGJAM (CVE-2015-4000)", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("LOGJAM-common_primes", new Tag<>("LOGJAM-common_primes", "LOGJAM-common_primes", SectionType.VULNERABILITIES, CommonParser::returnSelf));
		tagMap.put("BEAST_CBC_TLS1", new Tag<>("BEAST_CBC_TLS1", "BEAST (CVE-2011-3389) TLS1", SectionType.VULNERABILITIES, CommonParser::returnSelf));
		tagMap.put("BEAST", new Tag<>("BEAST", "BEAST (CVE-2011-3389)", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("LUCKY13", new Tag<>("LUCKY13", "LUCKY13 (CVE-2013-0169)", SectionType.VULNERABILITIES, CommonParser::isVulnerable));
		tagMap.put("RC4", new Tag<>("RC4", "RC4 (CVE-2013-2566, CVE-2015-2808)", SectionType.VULNERABILITIES, CommonParser::isVulnerable));

		tagMap.put("cipher_x", new Tag<>("cipher_x", "Cipher suite", SectionType.CIPHER_SUITE, CommonParser::parseCipherSuite));
		tagMap.put("clientsimulation-android_422", new Tag<>("clientsimulation-android_422", "Android 4.2.2", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-android_442", new Tag<>("clientsimulation-android_442", "Android 4.4.2", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-android_500", new Tag<>("clientsimulation-android_500", "Android 5.0.0", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-android_60", new Tag<>("clientsimulation-android_60", "Android 6.0", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-android_70", new Tag<>("clientsimulation-android_70", "Android 7.0", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-chrome_65_win7", new Tag<>("clientsimulation-chrome_65_win7", "Chrome 65 Win 7", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-chrome_70_win10", new Tag<>("clientsimulation-chrome_70_win10", "Chrome 70 Win 10", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-firefox_59_win7", new Tag<>("clientsimulation-firefox_59_win7", "Firefox 59 Win 7", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-firefox_62_win7", new Tag<>("clientsimulation-firefox_62_win7", "Firefox 62 Win 7", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-ie_6_xp", new Tag<>("clientsimulation-ie_6_xp", "IE 6 XP", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-ie_7_vista", new Tag<>("clientsimulation-ie_7_vista", "IE 7 Vista", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-ie_8_win7", new Tag<>("clientsimulation-ie_8_win7", "IE 8 Win 7", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-ie_8_xp", new Tag<>("clientsimulation-ie_8_xp", "IE 8 XP", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-ie_11_win7", new Tag<>("clientsimulation-ie_11_win7", "IE 11 Win 7", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-ie_11_win81", new Tag<>("clientsimulation-ie_11_win81", "IE 11 Win 8.1", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-ie_11_win10", new Tag<>("clientsimulation-ie_11_win10", "IE 11 Win 10", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-edge_13_win10", new Tag<>("clientsimulation-edge_13_win10", "Edge 13 Win 10", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-edge_13_winphone10", new Tag<>("clientsimulation-edge_13_winphone10", "Edge 13 Win Phone 10", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-edge_15_win10", new Tag<>("clientsimulation-edge_15_win10", "Edge 15 Win 10", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-opera_17_win7", new Tag<>("clientsimulation-opera_17_win7", "Opera 17 Win 7", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-safari_9_ios9", new Tag<>("clientsimulation-safari_9_ios9", "Safari 9 iOS 9", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-safari_9_osx1011", new Tag<>("clientsimulation-safari_9_osx1011", "Safari 9 OS X 10.11", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-safari_10_osx1012", new Tag<>("clientsimulation-safari_10_osx1012", "Safari 10 OS X 10.12", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-apple_ats_9_ios9", new Tag<>("clientsimulation-apple_ats_9_ios9", "Apple ATS 9 iOS 9", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-tor_1709_win7", new Tag<>("clientsimulation-tor_1709_win7", "Tor 17.0.9 Win 7", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-java_6u45", new Tag<>("clientsimulation-java_6u45", "Java 6u45", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-java_7u25", new Tag<>("clientsimulation-java_7u25", "Java 7u25", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-java_8u161", new Tag<>("clientsimulation-java_8u161", "Java 8u161", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-java_904", new Tag<>("clientsimulation-java_904", "Java 9.0.4", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-openssl_101l", new Tag<>("clientsimulation-openssl_101l", "OpenSSL 1.0.1l", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
		tagMap.put("clientsimulation-openssl_102e", new Tag<>("clientsimulation-openssl_102e", "OpenSSL 1.0.2e", SectionType.CLIENT_SIMULATION, CommonParser::returnSelf));
	}
}
