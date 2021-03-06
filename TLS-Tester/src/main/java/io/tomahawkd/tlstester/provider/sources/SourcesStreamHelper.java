package io.tomahawkd.tlstester.provider.sources;

import io.tomahawkd.tlstester.data.TargetInfoFactory;
import io.tomahawkd.tlstester.netservice.CensysQueriesHelper;
import io.tomahawkd.tlstester.provider.TargetStorage;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.stream.Stream;

public final class SourcesStreamHelper {

	public static Stream<InetSocketAddress> process(Stream<String> dataStream) {
		return dataStream.distinct()
				.map(SourcesStreamHelper::parse).filter(Objects::nonNull);
	}

	public static InetSocketAddress parse(String target) {
		String[] l = target.split(":");
		try {
			int port = Integer.parseInt(l[1]);
			if (port < 0 || port > 0xFFFF) return null;
			return new InetSocketAddress(l[0], port);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static void addTo(TargetStorage storage,
	                         Stream<InetSocketAddress> dataStream) {
		dataStream.map(s -> TargetInfoFactory
						.postTestBuild(s, CensysQueriesHelper.CENSYS_CALLBACK))
				.forEach(storage::add);
	}

	public static void processTo(TargetStorage storage, Stream<String> dataStream) {
		process(dataStream)
				.map(s -> TargetInfoFactory
						.postTestBuild(s, CensysQueriesHelper.CENSYS_CALLBACK))
				.forEach(storage::add);
	}
}
