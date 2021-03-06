package edu.cloudy.nlp.ranking;

import edu.cloudy.nlp.WCVDocument;
import edu.cloudy.nlp.Word;

import java.util.List;

/**
 * @author spupyrev
 * Aug 18, 2013
 */
public class TFRankingAlgo implements RankingAlgo
{
    @Override
    public void buildWeights(WCVDocument wordifier)
    {
        List<Word> words = wordifier.getWords();

        int maxCount = -1;
        for (Word w : words)
            maxCount = Math.max(maxCount, w.getCoordinates().size());

        for (Word w : words)
        {
            w.weight = (double)w.getCoordinates().size() / (double)maxCount;
        }
    }
}
