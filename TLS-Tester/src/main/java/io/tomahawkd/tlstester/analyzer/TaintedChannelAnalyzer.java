package io.tomahawkd.tlstester.analyzer;

import de.rub.nds.tlsattacker.core.constants.CertificateKeyType;
import de.rub.nds.tlsattacker.core.protocol.message.*;
import de.rub.nds.tlsattacker.core.workflow.action.MessageAction;
import io.tomahawkd.tlstester.InternalNamespaces;
import io.tomahawkd.tlstester.data.DataHelper;
import io.tomahawkd.tlstester.data.TargetInfo;
import io.tomahawkd.tlstester.data.TreeCode;
import io.tomahawkd.tlstester.data.testssl.SectionType;
import io.tomahawkd.tlstester.data.testssl.Segment;
import io.tomahawkd.tlstester.data.testssl.SegmentMap;
import io.tomahawkd.tlstester.data.testssl.parser.CipherInfo;
import io.tomahawkd.tlstester.data.testssl.parser.CipherSuite;
import io.tomahawkd.tlstester.data.testssl.parser.OfferedResult;
import io.tomahawkd.tlstester.tlsattacker.ConnectionTester;
import io.tomahawkd.tlstester.tlsattacker.KeyExchangeTester;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
@Record(column = InternalNamespaces.Analyzers.TAINTED,
		resultLength = TaintedChannelAnalyzer.TREE_LENGTH,
		map = {
				@StatisticMapping(column = "overall", position = {
						TaintedChannelAnalyzer.FORCE_RSA_KEY_EXCHANGE,
						TaintedChannelAnalyzer.LEARN_LONG_LIVE_SESSION,
						TaintedChannelAnalyzer.FORGE_RSA_SIGN,
						TaintedChannelAnalyzer.HEARTBLEED
				}),
				@StatisticMapping(column = "force_rsa", position = TaintedChannelAnalyzer.FORCE_RSA_KEY_EXCHANGE),
				@StatisticMapping(column = "learn_session", position = TaintedChannelAnalyzer.LEARN_LONG_LIVE_SESSION),
				@StatisticMapping(column = "forge_sign", position = TaintedChannelAnalyzer.FORGE_RSA_SIGN),
				@StatisticMapping(column = "heartbleed", position = TaintedChannelAnalyzer.HEARTBLEED)
		},
		posMap = {
				@PosMap(src = TaintedChannelAnalyzer.RSA_DECRYPTION_HOST,
						dst = TaintedChannelAnalyzer.RSA_DECRYPTION_OTHER),
				@PosMap(src = TaintedChannelAnalyzer.RSA_SIGN_HOST,
						dst = TaintedChannelAnalyzer.RSA_SIGN_OTHER)
		},
		depMap = @DependencyMap(dep = LeakyChannelAnalyzer.class,
				pos = TaintedChannelAnalyzer.LEARN_SESSION_KEY))
public class TaintedChannelAnalyzer implements Analyzer {

	private static final Logger logger = LogManager.getLogger(TaintedChannelAnalyzer.class);

	public static final int FORCE_RSA_KEY_EXCHANGE = 0;
	public static final int RSA_KEY_EXCHANGE_SUPPORTED = 1;
	public static final int RSA_DECRYPTION = 2;
	public static final int RSA_DECRYPTION_HOST = 3;
	public static final int RSA_DECRYPTION_OTHER = 4;
	public static final int LEARN_LONG_LIVE_SESSION = 5;
	public static final int LEARN_SESSION_KEY = 6;
	public static final int CLIENT_RESUMES_SESSION = 7;
	public static final int RESUMPTION_WITH_TICKETS = 8;
	public static final int RESUMPTION_WITH_IDS = 9;
	public static final int FORGE_RSA_SIGN = 10;
	public static final int RSA_SIGN = 11;
	public static final int RSA_SIGN_HOST = 12;
	public static final int RSA_SIGN_OTHER = 13;
	public static final int SAME_RSA_KEY_AND_SIGN = 14;
	public static final int HEARTBLEED = 15;
	public static final int TREE_LENGTH = 16;

	@Override
	public boolean getResult(TreeCode code) {
		return code.get(FORCE_RSA_KEY_EXCHANGE) ||
				code.get(LEARN_LONG_LIVE_SESSION) ||
				code.get(FORGE_RSA_SIGN) ||
				code.get(HEARTBLEED);
	}

	@Override
	public TreeCode updateResult(TreeCode code) {
		code.set(code.get(RSA_DECRYPTION_HOST) || code.get(RSA_DECRYPTION_OTHER), RSA_DECRYPTION);
		code.set(code.get(RSA_KEY_EXCHANGE_SUPPORTED) && code.get(RSA_DECRYPTION), FORCE_RSA_KEY_EXCHANGE);

		code.set(code.get(RESUMPTION_WITH_TICKETS) || code.get(RESUMPTION_WITH_IDS), CLIENT_RESUMES_SESSION);
		code.set(code.get(LEARN_SESSION_KEY) && code.get(CLIENT_RESUMES_SESSION), LEARN_LONG_LIVE_SESSION);

		code.set(code.get(RSA_SIGN_HOST) || code.get(RSA_SIGN_OTHER), RSA_SIGN);
		code.set(code.get(RSA_SIGN) && code.get(SAME_RSA_KEY_AND_SIGN), FORGE_RSA_SIGN);

		return code;
	}

	@Override
	public String getResultDescription(TreeCode code) {

		return "GOAL Potential MITM (decryption and modification)\n" +
				"-----------------------------------------------\n" +
				"| 1 Force RSA key exchange by modifying Client Hello and decrypt it before the handshake times out: "
				+ code.get(FORCE_RSA_KEY_EXCHANGE) + "\n" +
				"\t& 1 RSA key exchange support in any TLS version: " + code.get(RSA_KEY_EXCHANGE_SUPPORTED) + "\n" +
				"\t& 2 Fast RSA decryption oracle (Special DROWN or Strong Bleichenbacher’s oracle) available on: "
				+ code.get(RSA_DECRYPTION) + "\n" +
				"\t\t| 1 This host: " + code.get(RSA_DECRYPTION_HOST) + "\n" +
				"\t\t| 2 Another host with the same certificate\n" +
				"\t\t| 3 Another host with the same public RSA key: " + code.get(RSA_DECRYPTION_OTHER) + "\n" +
				"| 2 Learn the session keys of a long lived session: " + code.get(LEARN_LONG_LIVE_SESSION) + "\n" +
				"\t& 1 Learn the session keys (Figure 2): " + code.get(LEARN_SESSION_KEY) + "\n" +
				"\t& 2 Client resumes the session: " + code.get(CLIENT_RESUMES_SESSION) + "\n" +
				"\t\t| 1 Session resumption with tickets: " + code.get(RESUMPTION_WITH_TICKETS) + "\n" +
				"\t\t| 2 Session resumption with session IDs: " + code.get(RESUMPTION_WITH_IDS) + "\n" +
				"| 3 Forge an RSA signature in the key establishment: " + code.get(FORGE_RSA_SIGN) + "\n" +
				"\t& 1 Fast RSA signature oracle (Strong Bleichenbacher’s oracle) is available on: "
				+ code.get(RSA_SIGN) + "\n" +
				"\t\t| 1 This host: " + code.get(RSA_SIGN_HOST) + "\n" +
				"\t\t| 2 Another host with the same certificate\n" +
				"\t\t| 3 Another host with the same public RSA key\n" +
				"\t\t| 4 A host with a certificate where the Subject Alternative Names (SAN) match this host: "
				+ code.get(RSA_SIGN_OTHER) + "\n" +
				"\t& 2 The same RSA key is used for RSA key exchange and RSA signature in ECDHE key establishment: "
				+ code.get(SAME_RSA_KEY_AND_SIGN) + "\n" +
				"| 4 Private key leak due to the Heartbleed bug: " + code.get(HEARTBLEED) + "\n";
	}

	@Override
	public void analyze(TargetInfo info, TreeCode code) {

		logger.info("Start test tainted channel on " + info.getHost());

		boolean force = canForceRSAKeyExchangeAndDecrypt(info, code);
		code.set(force, FORCE_RSA_KEY_EXCHANGE);

		boolean learn = canLearnTheSessionKeysOfLongLivedSession(DataHelper.getTargetData(info), code);
		code.set(learn, LEARN_LONG_LIVE_SESSION);

		boolean forge = canForgeRSASignatureInTheKeyEstablishment(info, code);
		code.set(forge, FORGE_RSA_SIGN);

		boolean heartbleed = AnalyzerHelper
				.isVulnerableTo(info, VulnerabilityTags.HEARTBLEED);
		code.set(heartbleed, HEARTBLEED);
	}

	@Override
	public void preAnalyze(TargetInfo info,
	                       Map<Class<? extends Analyzer>, TreeCode>
			                       dependencyResults, TreeCode code) {
		TreeCode c = dependencyResults.get(LeakyChannelAnalyzer.class);
		code.set(c.get(LeakyChannelAnalyzer.RSA_KEY_EXCHANGE_OFFLINE), LEARN_SESSION_KEY);
	}

	@Override
	public void postAnalyze(TargetInfo info, TreeCode code) {
		logger.debug("Result: " + code);
		String result = "\n" + getResultDescription(code);
		if (getResult(code)) logger.warn(result);
		else logger.info(result);
	}

	private boolean canForceRSAKeyExchangeAndDecrypt(TargetInfo info, TreeCode code) {

		logger.info("Start test force RSA exchange on " + info.getIp());

		SegmentMap target = DataHelper.getTargetData(info);
		boolean isSupported = isRSAUsedInAnyVersion(target);
		code.set(isSupported, RSA_KEY_EXCHANGE_SUPPORTED);

		boolean isHost = isHostRSAVulnerable(info);
		code.set(isHost, RSA_DECRYPTION_HOST);

		// ignoring test other host which has same cert
		// this operation is complete after.
		code.set(false, RSA_DECRYPTION_OTHER);

		code.set(isHost, RSA_DECRYPTION);

		return isSupported && isHost;
	}

	private boolean canLearnTheSessionKeysOfLongLivedSession(SegmentMap target, TreeCode code) {

		logger.info("Start test learn session key on " + target.getIp());

		boolean ticket = ((OfferedResult) target.get("sessionresumption_ticket").getResult()).isResult();
		code.set(ticket, RESUMPTION_WITH_TICKETS);

		boolean id = ((OfferedResult) target.get("sessionresumption_ID").getResult()).isResult();

		// what we can get from tls attacker is session id
		boolean isResumed = false;
		if (id) {
			CipherSuite cipher = (CipherSuite) target.get("cipher_negotiated").getResult();

			if (cipher != null && de.rub.nds.tlsattacker.core.constants.
					CipherSuite.getCipherSuite(cipher.getHexCode()) != null) {

				logger.info("Testing is session resuming using last ticket");

				List<ProtocolMessage> r = new ConnectionTester(target.getIp())
						.setCipherSuite(de.rub.nds.tlsattacker.core.constants.
								CipherSuite.getCipherSuite(cipher.getHexCode()))
						.execute().getHandShakeMessages();

				if (!r.isEmpty()) {
					List<ProtocolMessage> result = new ConnectionTester(target.getIp())
							.setCipherSuite(de.rub.nds.tlsattacker.core.constants.
									CipherSuite.getCipherSuite(cipher.getHexCode()))
							.execute(((ServerHelloMessage) r.get(0)).getSessionId()).getHandShakeMessages();

					if (result.size() > 1) isResumed = true;
				} else logger.error("did not receive server hello message");

			} else logger.error("Null pointer of cipher found");
		}

		boolean idResume = id && isResumed;
		code.set(idResume, RESUMPTION_WITH_IDS);

		boolean isResumption = ticket || idResume;
		code.set(isResumption, CLIENT_RESUMES_SESSION);

		return code.get(LEARN_SESSION_KEY) && isResumption;
	}

	private boolean canForgeRSASignatureInTheKeyEstablishment(TargetInfo info, TreeCode code) {

		logger.info("Start test forge RSA signature on " + info.getIp());

		SegmentMap target = DataHelper.getTargetData(info);
		boolean thisRobot = AnalyzerHelper.isVulnerableTo(info, VulnerabilityTags.ROBOT);
		code.set(thisRobot, RSA_SIGN_HOST);

		// ignoring test other host which has same cert
		// this operation is complete after.
		code.set(false, RSA_SIGN_OTHER);

		code.set(thisRobot, RSA_SIGN);

		ArrayList<CertificateMessage> rsa = new ArrayList<>();
		ArrayList<CertificateMessage> ecdhe = new ArrayList<>();

		logger.info("Testing RSA key duplication");

		List<Segment> list = target.getByType(SectionType.CIPHER_ORDER);
		for (Segment current : list) {

			((CipherInfo) current.getResult()).getCipher().getList().forEach(e -> {
				try {
					if (de.rub.nds.tlsattacker.core.constants.
							CipherSuite.getCipherSuite(e.getHexCode()) == null) return;
					if (e.getKeyExchange().contains("RSA")) {
						List<MessageAction> r = new KeyExchangeTester(info)
								.setCipherSuite(de.rub.nds.tlsattacker.core.constants.
										CipherSuite.getCipherSuite(e.getHexCode()))
								.initRSA().execute();

						CertificateMessage message = (CertificateMessage) r.get(1).getMessages().get(1);
						if (message.getCertificateKeyPair().getCertPublicKeyType().equals(CertificateKeyType.RSA))
							rsa.add(message);
					} else if (e.getKeyExchange().contains("ECDH") &&
							(e.getName().contains("RSA") || e.getRfcName().contains("RSA"))) {
						List<MessageAction> ec = new KeyExchangeTester(info)
								.setCipherSuite(de.rub.nds.tlsattacker.core.constants.
										CipherSuite.getCipherSuite(e.getHexCode()))
								.initECDHE().execute();

						CertificateMessage message = (CertificateMessage) ec.get(1).getMessages().get(1);
						if (message.getCertificateKeyPair().getCertPublicKeyType().equals(CertificateKeyType.RSA))
							ecdhe.add(message);
					}
				} catch (IndexOutOfBoundsException | ClassCastException exception) {
					logger.warn("Certificate message not received, skipping ciphersuite " + e.getHexCode());
				}
			});
		}

		boolean isSame = false;

		if (rsa.isEmpty() || ecdhe.isEmpty()) {
			logger.warn((rsa.isEmpty() ? "RSA" : "ECDH") + " Certificate Message is empty, " +
					"whose ciphersuite is may unsupported");
		} else {
			logger.info(
					String.format("Test %d RSA Certificate Message and %d ECDH Certificate Message.",
							rsa.size(), ecdhe.size()));

			for (CertificateMessage r : rsa) {
				for (CertificateMessage e : ecdhe) {
					if (e.getCertificateKeyPair().getPublicKey()
							.equals(r.getCertificateKeyPair().getPublicKey())) {
						isSame = true;
						break;
					}
				}
			}
		}
		code.set(isSame, SAME_RSA_KEY_AND_SIGN);

		return thisRobot && isSame;
	}

	private static boolean isRSAUsedInAnyVersion(SegmentMap target) {
		List<Segment> list = target.getByType(SectionType.CIPHER_ORDER);
		int count = 0;
		for (Segment current : list) {

			for (CipherSuite e : ((CipherInfo) current.getResult()).getCipher().getList()) {
				if (e.getKeyExchange().contains("RSA")) {
					count++;
					break;
				}
			}
		}

		return count > 0;
	}

	private static boolean isHostRSAVulnerable(TargetInfo target) {
		return AnalyzerHelper.isVulnerableTo(target, VulnerabilityTags.ROBOT) ||
				AnalyzerHelper.isVulnerableTo(target, VulnerabilityTags.DROWN);
	}
}
