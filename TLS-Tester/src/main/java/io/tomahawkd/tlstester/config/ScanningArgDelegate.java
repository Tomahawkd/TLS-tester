package io.tomahawkd.tlstester.config;

import com.beust.jcommander.Parameter;

import java.util.ArrayList;
import java.util.List;

public class ScanningArgDelegate extends AbstractArgDelegate {

	@Parameter(required = true,
			description = "<Type>::<Target String> " +
					"\nAvailable format: " +
					"shodan[::<start>-<end>]::<query>, " +
					"file::<path>, " +
					"ips::<ip>[;<ip>], " +
					"socket::[<ip>[:<port>]]")
	@SuppressWarnings("all")
	private List<String> providersList = new ArrayList<>();

	@Parameter(names = {"-e", "--enable_cert"},
			description = "enable searching and testing other host has same cert. " +
					"It will be a long tour.")
	private boolean otherSiteCert = false;

	@Parameter(names = {"-t", "--thread"}, description = "Total thread to be activated.")
	private Integer threadCount = 5;

	public boolean checkOtherSiteCert() {
		return otherSiteCert;
	}

	public Integer getThreadCount() {
		return threadCount;
	}

	public List<String> getProviderSources() {
		return providersList;
	}
}
