package de.tinloaf.cloudy.algos;

import de.tinloaf.cloudy.algos.mds.DistanceScaling;
import de.tinloaf.cloudy.algos.overlaps.ForceDirectedOverlapRemoval;
import de.tinloaf.cloudy.text.Word;
import de.tinloaf.cloudy.utils.BoundingBoxGenerator;
import de.tinloaf.cloudy.utils.SWCRectangle;
import de.tinloaf.cloudy.utils.WordPair;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author spupyrev
 * May 12, 2013
 * 
 * 1. compute mds on dissimilarity matrix
 * 2. remove overlaps
 * 
 * (the algorithm is not supposed to compact the drawing!)
 */
public class MDSAlgo implements LayoutAlgo
{
    private List<Word> words;
    private Map<WordPair, Double> similarity;

    private BoundingBoxGenerator bbGenerator;

    @Override
    public void setConstraints(BoundingBoxGenerator bbGenerator)
    {
        this.bbGenerator = bbGenerator;
    }

    @Override
    public void setData(List<Word> words, Map<WordPair, Double> similarity)
    {
        this.words = words;
        this.similarity = similarity;
    }

    @Override
    public SWCRectangle getWordRectangle(Word w)
    {
        return wordPositions.get(w);
    }

    private static double SCALING = 2.0;

    private Map<Word, SWCRectangle> wordPositions = null;

    @Override
    public void run()
    {
        //maps words to their bounding rectangle
        wordPositions = computeBoundingBoxes();

        //mds placement
        computeInitialPlacement();

        //force-directed overlap removal
        new ForceDirectedOverlapRemoval<SWCRectangle>().run(words, wordPositions);
    }

    private Map<Word, SWCRectangle> computeBoundingBoxes()
    {
        Map<Word, SWCRectangle> result = new HashMap<Word, SWCRectangle>();

        // Get the bounding box for each of the words
        for (Word w : words)
            result.put(w, bbGenerator.getBoundingBox(w, w.weight));

        return result;
    }

    private void computeInitialPlacement()
    {
        double[][] outputMDS = runMDS();

        // set coordinates
        for (int i = 0; i < words.size(); i++)
        {
            SWCRectangle rect = wordPositions.get(words.get(i));
            double x = outputMDS[0][i];
            double y = outputMDS[1][i];
            x -= rect.getWidth() / 2.;
            y -= rect.getHeight() / 2.;
            rect.setRect(x, y, rect.getWidth(), rect.getHeight());
        }

        // Perturb coincident word positions
        double EPS = 0.1;
        boolean progress = false;
        while (progress)
        {
            progress = false;
            for (int i = 0; i < words.size(); i++)
                for (int j = i + 1; j < words.size(); j++)
                {
                    SWCRectangle r1 = wordPositions.get(words.get(i));
                    SWCRectangle r2 = wordPositions.get(words.get(j));

                    if ((Math.abs(r1.getX() - r2.getX())) < EPS && (Math.abs(r1.getY() - r2.getY()) < EPS))
                    {
                        Random r = new Random();
                        r1.setRect(r1.getX() + r.nextDouble(), r1.getY() + r.nextDouble(), r1.getWidth(), r1.getHeight());
                        progress = true;
                    }
                }
        }
    }

    private double[][] runMDS()
    {
        double maxWordSize = 0;
        for (Word w : wordPositions.keySet())
            maxWordSize = Math.max(maxWordSize, wordPositions.get(w).getWidth());

        Map<Word, Integer> wordIndex = new HashMap<Word, Integer>();
        for (int i = 0; i < words.size(); i++)
            wordIndex.put(words.get(i), i);

        double[][] desiredDistance = new double[words.size()][words.size()];
        for (int i = 0; i < words.size(); i++)
        {
            Arrays.fill(desiredDistance[i], 10 * maxWordSize);
            desiredDistance[i][i] = 0;
        }

        for (WordPair wp : similarity.keySet())
        {
            assert (0 <= similarity.get(wp) && similarity.get(wp) <= 1.0);

            int i1 = wordIndex.get(wp.getFirst());
            int i2 = wordIndex.get(wp.getSecond());
            if (i1 == i2)
                continue;

            desiredDistance[i1][i2] = desiredDistance[i2][i1] = (1 - similarity.get(wp)) * maxWordSize * SCALING;
            //double s = 0.1;
            //desiredDistance[i1][i2] = desiredDistance[i2][i1] = -Math.log((1 - s) * similarity.get(wp) + s) * maxWordSize;// * SCALING;
        }

        //aply MDS
        double[][] outputMDS = new DistanceScaling().mds(desiredDistance, 2);

        //debug output
        for (int i = 0; i < desiredDistance[0].length; i++)
        {
            assert (!Double.isNaN(outputMDS[0][i]));
            assert (!Double.isNaN(outputMDS[1][i]));
            //Logger.printf("(%4f, %4f)\n", outputMDS[0][i], outputMDS[1][i]);
        }

        return outputMDS;
    }

}
