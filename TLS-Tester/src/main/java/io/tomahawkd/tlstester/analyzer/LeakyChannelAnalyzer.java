package io.tomahawkd.tlstester.analyzer;

import de.rub.nds.tlsattacker.core.workflow.action.MessageAction;
import io.tomahawkd.tlstester.InternalNamespaces;
import io.tomahawkd.tlstester.data.DataHelper;
import io.tomahawkd.tlstester.data.TargetInfo;
import io.tomahawkd.tlstester.data.TreeCode;
import io.tomahawkd.tlstester.data.testssl.SegmentMap;
import io.tomahawkd.tlstester.data.testssl.parser.CipherInfo;
import io.tomahawkd.tlstester.data.testssl.parser.CipherSuite;
import io.tomahawkd.tlstester.tlsattacker.KeyExchangeTester;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

@Record(column = InternalNamespaces.Analyzers.LEAKY,
		resultLength = LeakyChannelAnalyzer.TREE_LENGTH,
		posMap = {
				@PosMap(src = LeakyChannelAnalyzer.RSA_DECRYPTION_HOST,
						dst = LeakyChannelAnalyzer.RSA_DECRYPTION_OTHER)
		})
public class LeakyChannelAnalyzer implements Analyzer {

	private static final Logger logger = LogManager.getLogger(LeakyChannelAnalyzer.class);

	public static final int RSA_KEY_EXCHANGE_OFFLINE = 0;
	public static final int RSA_KEY_EXCHANGE_USED = 1;
	public static final int RSA_KEY_EXCHANGE_PREFERRED = 2;
	public static final int RSA_KEY_EXCHANGE_DOWNGRADE = 3;
	public static final int RSA_DECRYPTION = 4;
	public static final int RSA_DECRYPTION_HOST = 5;
	public static final int RSA_DECRYPTION_OTHER = 6;
	public static final int TREE_LENGTH = 7;

	@Override
	public boolean getResult(TreeCode code) {
		return code.get(RSA_KEY_EXCHANGE_OFFLINE);
	}

	@Override
	public TreeCode updateResult(TreeCode code) {
		code.set(code.get(RSA_KEY_EXCHANGE_PREFERRED) || code.get(RSA_KEY_EXCHANGE_DOWNGRADE),
				RSA_KEY_EXCHANGE_USED);

		code.set(code.get(RSA_DECRYPTION_HOST) || code.get(RSA_DECRYPTION_OTHER), RSA_DECRYPTION);

		code.set(code.get(RSA_KEY_EXCHANGE_USED) && code.get(RSA_DECRYPTION), RSA_KEY_EXCHANGE_OFFLINE);
		return code;
	}

	@Override
	public String getResultDescription(TreeCode code) {

		return "GOAL Learn the session keys (allows decryption)\n" +
				"-----------------------------------------------\n" +
				"| 1 Decrypt RSA key exchange offline: " + code.get(RSA_KEY_EXCHANGE_OFFLINE) + "\n" +
				"\t& 1 RSA key exchange is used: " + code.get(RSA_KEY_EXCHANGE_USED) + "\n" +
				"\t\t| 1 RSA key exchange is preferred in the highest supported version of TLS: "
				+ code.get(RSA_KEY_EXCHANGE_PREFERRED) + "\n" +
				"\t\t| 2 Downgrade is possible to a version of TLS where RSA key exchange is preferred: "
				+ code.get(RSA_KEY_EXCHANGE_DOWNGRADE) + "\n" +
				"\t& 2 RSA decryption oracle (DROWN or Strong Bleichenbacher’s oracle) is available on: " +
				code.get(RSA_DECRYPTION) + "\n" +
				"\t\t| 1 This host: " + code.get(RSA_DECRYPTION_HOST) + "\n" +
				"\t\t| 2 Another host with the same certificate\n" +
				"\t\t| 3 Another host with the same public RSA key: " + code.get(RSA_DECRYPTION_OTHER) + "\n";
	}

	@Override
	public void analyze(TargetInfo info, TreeCode code) {

		logger.info("Start test leaky channel on " + info.getHost());

		boolean rsaUsed = isRSAUsed(info, code);
		code.set(rsaUsed, RSA_KEY_EXCHANGE_USED);

		boolean isVul = isHostRSAVulnerable(info);
		code.set(isVul, RSA_DECRYPTION_HOST);

		// ignoring test other host which has same cert
		// this operation is complete after.
		code.set(false, RSA_DECRYPTION_OTHER);

		code.set(isVul, RSA_DECRYPTION);

		boolean res = rsaUsed && isVul;
		code.set(res, RSA_KEY_EXCHANGE_OFFLINE);
	}

	@Override
	public void postAnalyze(TargetInfo info, TreeCode code) {
		logger.debug("Result: " + code);
		String result = "\n" + getResultDescription(code);
		if (getResult(code)) logger.warn(result);
		else logger.info(result);
	}

	private boolean isRSAUsed(TargetInfo info, TreeCode code) {

		SegmentMap target = DataHelper.getTargetData(info);
		CipherSuite cipher = (CipherSuite) target.get("cipher_negotiated").getResult();

		boolean preferred = false;
		if (cipher != null) preferred = cipher.getKeyExchange().contains("RSA");
		else logger.error("cipher not found, assuming false");
		code.set(preferred, RSA_KEY_EXCHANGE_PREFERRED);

		boolean isPossible = false;
		if (cipher != null) {
			isPossible = AnalyzerHelper.downgradeIsPossibleToAVersionOf(target,
					CipherInfo.SSLVersion.TLS1,
					(version, suite, segmentMap) -> {
						if (suite.getKeyExchange().contains("RSA")) {
							if (de.rub.nds.tlsattacker.core.constants.
									CipherSuite.getCipherSuite(suite.getHexCode()) == null) {
								logger.error("cipher isn't support by tls attacker, returning false");
								return false;
							}
							List<MessageAction> result = new KeyExchangeTester(info)
									.setCipherSuite(de.rub.nds.tlsattacker.core.constants.
											CipherSuite.getCipherSuite(suite.getHexCode()))
									.setNegotiateVersion(version).initRSA().execute();
							return result.get(result.size() - 1).getMessages().size() > 1;

						} else return false;
					});
		} else {
			logger.error("cipher not found, assuming false");
		}
		code.set(isPossible, RSA_KEY_EXCHANGE_DOWNGRADE);

		return preferred || isPossible;
	}

	private static boolean isHostRSAVulnerable(TargetInfo target) {
		return AnalyzerHelper.isVulnerableTo(target, VulnerabilityTags.ROBOT) ||
				AnalyzerHelper.isVulnerableTo(target, VulnerabilityTags.DROWN);
	}
}
