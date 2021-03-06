package edu.webapp.server;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

import edu.webapp.client.WordCloudService;
import edu.webapp.server.utils.RandomTwitterTrendExtractor;
import edu.webapp.server.utils.RandomWikiUrlExtractor;
import edu.webapp.server.utils.RandomYoutubeUrlExtractor;
import edu.webapp.shared.WCSetting;
import edu.webapp.shared.WordCloud;

/**
 * The server side implementation of the RPC service.
 */
@SuppressWarnings("serial")
public class WordCloudServiceImpl extends RemoteServiceServlet implements WordCloudService
{

	public WordCloud buildWordCloud(String input, WCSetting setting) throws IllegalArgumentException
	{
		String ip = getThreadLocalRequest().getRemoteAddr();
		WordCloud newcloud = WordCloudGenerator.createWordCloud(input, setting, ip);
		return newcloud;
	}

	public String getRandomWikiUrl()
	{
		return RandomWikiUrlExtractor.getRandomWikiPage();
	}

	public String getRandomTwitterUrl()
	{
		return RandomTwitterTrendExtractor.getRandomTrend();
	}

	public String getRandomYoutubeUrl()
	{
		return RandomYoutubeUrlExtractor.getRandomUrl();
	}
}
